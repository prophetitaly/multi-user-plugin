// SPDX-FileCopyrightText: 2021 Andreas Bauer
//
// SPDX-License-Identifier: MIT

package plugin;

import static java.lang.Long.parseLong;
import static plugin.JSONStateParser.appStateAsJSONObject;
import static plugin.JSONStateParser.parseCompleteAppState;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

import javax.swing.JFileChooser;
import javax.swing.plaf.nimbus.State;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import scout.*;

public class MultiUser {

    private static final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat dfFiles = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
    private static final String DATA_FILEPATH = "data";
    private static final String MODEL_FILENAME = "shared-state.json";
    private static final String PRODUCT_PROPERTIES_FILE = "product.properties";

    protected static final String META_DATA_DIFF = "multi-user-diff-widgets";
    protected static final String DELETED_AT = "multi-user-merge-deleted-at";

    private static AppState stateFromSessionStart = null;
    private static String sharedModelFolder = null;

    // crowdsourcing variables
    private static String startingHomeLocator = null;
    private static String hoverButtonText = null;
    private static AppState stateMicroTask = null;
    private static String microTaskWidget = null;
    private static String microTaskState = null;



    //end crowdsourcing variables


    protected enum DiffType {
        CREATED, DELETED, CHANGED, NO_CHANGES
    }

    public MultiUser() {
        this(false);
    }

    public MultiUser(boolean skipInit) {
        if (skipInit) {
            return;
        }

        List<String> products = getFolders(DATA_FILEPATH);
        StateController.setProducts(products);

        sharedModelFolder = StateController.getSystemProperty("multiUserPlugin.sharedModelFolder", DATA_FILEPATH);
    }

    private void startSession(String product, String productVersion, String testerName, String productView, String homeLocator, int productViewWidth, int productViewHeight, boolean isHeadlessBrowser) {
        // Happens after loadSession
        System.out.println("Start session -- MultiUser");

        StateController.setProduct(product);
        StateController.setProductVersion(productVersion);
        StateController.setTesterName(testerName);
        StateController.setProductView(productView);
        StateController.setHomeLocator(homeLocator);
        StateController.setProductViewHeight(productViewHeight);
        StateController.setProductViewWidth(productViewWidth);
        StateController.setHeadlessBrowser(isHeadlessBrowser);

        if (microTaskWidget != null && microTaskState != null) {
            AppState stateFromSessionStartCopy = deepCopy(stateFromSessionStart);
            StateController.setStateTree(stateFromSessionStartCopy);
            AppState test = stateFromSessionStartCopy.findState(microTaskState);
//            AppState targetState = StateController.getStateTree().findStateFromBookmark("Test");

//        StateController.setNavigationTargetState(test);
//        System.out.println(test);

//        StateController.setNavigationTargetState(targetState);

//        StateController.setRoute(StateController.Route.NAVIGATING);
//        StateController.setMode(StateController.Mode.AUTO);
//        PluginController.updateState();

            Widget widget;
            try {
                widget = stateFromSessionStartCopy.getAllIncludingChildWidgets().stream().filter(w -> w.getId().equals(microTaskWidget)).findFirst().get();
            } catch (Exception e) {
                return;
            }

            String url = (String) widget.getMetadata("href");
            if (url == null) {
                return;
            }
            StateController.setCurrentState(test);
            Action goTo = new Action();
            goTo.setComment("MultiUser: GoTo");
            goTo.putMetadata("url", url);
            PluginController.performAction(goTo);

            // modified startSession() without currentState = stateTree;
            StateController.setAutoStopSession(false);
            StateController.startNewPath(StateController.getProductVersion(), StateController.getTesterName());
            StateController.getCurrentState().addIteration();
            StateController.setSessionState(StateController.SessionState.INIT);
            StateController.setCurrentSubstate(StateController.CurrentSubstate.CHANGE);
            PluginController.startSession();
            StateController.setCurrentSubstate(StateController.CurrentSubstate.CAPTURE);
        }
        else {
            StateController.startSession();
        }
    }

    public void stopSession() {
        microTaskWidget = null;
        microTaskState = null;
    }

    public void enablePlugin() {
        sharedModelFolder = chooseFolderWithDialog();
        StateController.setSystemProperty("multiUserPlugin.sharedModelFolder", sharedModelFolder);
    }

    protected String chooseFolderWithDialog() {
        File currentWorkingDir = new File(".");

        JFileChooser chooser = new JFileChooser();
        chooser.setCurrentDirectory(currentWorkingDir);
        chooser.setDialogTitle("[Multi-User-Plugin] Select shared model folder.");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);

        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
            return currentWorkingDir.getAbsolutePath();
        }

        return chooser.getSelectedFile().getAbsolutePath();
    }

    protected void checkOrCreateProductFolder(String product) {
        String filePath = sharedModelFolder + "/" + product;
        File file = new File(filePath);
        if (!file.exists()) {
            file.mkdirs();
        }
    }

    /**
     * Load state tree for for the current product or create a new home state if not found.
     *
     * @return A state tree
     */
    public AppState loadState() {
        System.out.println("Load state tree");
        stateFromSessionStart = null;
        String product = StateController.getProduct();
        String filePath = getFilePathForProduct(product);
        checkOrCreateProductFolder(product);

        Properties properties = loadProductProperties(product, filePath);
        StateController.setProductProperties(properties);

        String sharedModelFilePath = sharedModelFolder + "/" + product + "/" + MODEL_FILENAME;
        JSONObject jsonModel = loadJSONModel(sharedModelFilePath);

        if (jsonModel == null) {
            AppState emptyState = new AppState("0", "Home");
            saveStateModel(sharedModelFilePath, emptyState);
            return emptyState;
        }

        AppState state = parseCompleteAppState(jsonModel);
//        removeAllMarkedAsDeletedWidgets(state);

//        markAsDeletedWidgetsInGUI(state);

        stateFromSessionStart = deepCopy(state);

        startingHomeLocator = StateController.getHomeLocator();

        log("Elenco tutti i widget initial state" + state.getAllIncludingChildWidgets().stream()
                .filter(w -> w.getWidgetVisibility() == Widget.WidgetVisibility.VISIBLE).collect(Collectors.toList()));

        return state;
    }

    protected void removeAllMarkedAsDeletedWidgets(AppState state) {
        Iterator<Widget> iter = state.getAllIncludingChildWidgets().iterator();
        while (iter.hasNext()) {
            Widget widget = iter.next();
            if (!isMarkedAsDeleted(widget)) {
                continue;
            }
            AppState widgetState = state.findState(widget);
            if (widgetState == null) {
                continue;
            }

            log("Remove as deleted marked widget with id " + widget.getId() + " from state with id " + widgetState.getId());
            widgetState.removeWidget(widget);
        }
    }

    protected void markAsDeletedWidgetsInGUI(AppState state, Graphics2D g2) {
        Iterator<Widget> iter = state.getVisibleWidgets().iterator();
        while (iter.hasNext()) {
            Widget widget = iter.next();
            if (!isMarkedAsDeleted(widget)) {
                continue;
            }
//            AppState widgetState = state.findState(widget);
//            if (widgetState == null) {
//                continue;
//            }

//          Create a graphic representation of the deleted widget
            Rectangle rect = widget.getLocationArea();
            if (rect != null) {
                drawRect(g2, rect);
            }
        }
    }

    public void paintCaptureForeground(Graphics g) {
        if (!StateController.isOngoingSession())
            return;

        Graphics2D g2 = (Graphics2D) g;

        markAsDeletedWidgetsInGUI(StateController.getCurrentState(), g2);
    }

    private void drawRect(Graphics2D g2, Rectangle rect) {
        int x = StateController.getScaledX((int) rect.getX() + 1);
        int y = StateController.getScaledY((int) rect.getY() + 1);
        int width = StateController.getScaledX((int) rect.getWidth() - 2);
        int height = StateController.getScaledY((int) rect.getHeight() - 2);

        g2.setStroke(new BasicStroke(3));
        g2.setColor(Color.red);
//        g2.fillRect(x, y, width, height);
//        g2.setColor(Color.black);
        g2.drawRect(x, y, width, height);

//        Composite c_old = g2.getComposite();
//        Composite c = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .4f);
//        g2.setComposite(c);
//
//        double angle = Math.toDegrees(Math.atan2(height, width));
//        double ipotenuse = Math.sqrt(width * width + height * height);
//        Font fontForCalculation = new Font(null, Font.PLAIN, 20);
//        AffineTransform affineTransform = new AffineTransform();
//        FontRenderContext frc = new FontRenderContext(affineTransform, true, true);
//        float textWidth = (float) (fontForCalculation.getStringBounds("DELETED", frc).getWidth());
//        float textHeight = (float) (fontForCalculation.getStringBounds("DELETED", frc).getHeight());
//        double upperWidth = ipotenuse * Math.cos(Math.toRadians(angle));
//        int fontSize = (int) ((upperWidth / (textWidth)) * 20.0f);
//
//        Font font = new Font(null, Font.PLAIN, fontSize);
//        AffineTransform affineTransform1 = new AffineTransform();
//        float textHeight1 = (float) (fontForCalculation.getStringBounds("DELETED", frc).getHeight());
//        double newAngle = Math.toDegrees(Math.atan2(height - Math.min(textHeight*Math.sin(Math.toRadians(90-angle)), textHeight1*Math.sin(Math.toRadians(90-angle))), width - 2));
//        affineTransform1.rotate(Math.toRadians(newAngle), 0, 0);
//        Font rotatedFont = font.deriveFont(affineTransform1);
//
//        FontRenderContext frc1 = new FontRenderContext(affineTransform, true, true);
//        AffineTransform affineTransform2 = new AffineTransform();
//        float textWidth2 = (float) (rotatedFont.getStringBounds("DELETED", frc1).getWidth());
//        float textHeight2 = (float) (rotatedFont.getStringBounds("DELETED", frc1).getHeight());
//        int fontSize1 = (int) ((upperWidth / (textWidth2)) * fontSize);
//        Font font1 = new Font(null, Font.PLAIN, fontSize1);
//        affineTransform2.rotate(Math.toRadians(newAngle), 0, 0);
//        Font rotatedFont1 = font1.deriveFont(affineTransform2);
//
//        g2.setFont(rotatedFont1);
//        g2.drawString("DELETED", x + 10, (int) (y + Math.min(textHeight*Math.sin(Math.toRadians(90-angle)), textHeight1*Math.sin(Math.toRadians(90-angle)))));
//        g2.setComposite(c_old);
    }

    private String getFilePathForProduct(String product) {
        if (product.isEmpty()) {
            return DATA_FILEPATH;
        }

        return DATA_FILEPATH + "/" + product;
    }

    private Properties loadProductProperties(String product, String projectRootPath) {
        try {
            Properties productProperties = new Properties();
            String filePath = projectRootPath + "/" + PRODUCT_PROPERTIES_FILE;
            FileInputStream in = new FileInputStream(filePath);
            productProperties.load(in);
            in.close();
            return productProperties;
        } catch (Exception e) {
            return new Properties();
        }
    }

    private JSONObject loadJSONModel(String filePath) {
        JSONParser jsonParser = new JSONParser();
        JSONObject jsonState = null;
        try {
            FileReader reader = new FileReader(filePath);
            jsonState = (JSONObject) jsonParser.parse(reader);
            reader.close();
        } catch (FileNotFoundException nfe) {
            log("State model file not found at location '" + filePath + "'. Start with empty model.");
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        return jsonState;
    }

    /**
     * Save the state tree for the current product.
     *
     * @return true if done
     */
    public Boolean saveState() {
        String product = StateController.getProduct();

        String productFilePath = getFilePathForProduct(product);

        createFolderIfNotExist(productFilePath);

        String sharedModelFilePath = sharedModelFolder + "/" + StateController.getProduct() + "/" + MODEL_FILENAME;

        AppState sessionState = StateController.getStateTree();
//        log("Elenco tutti i widget initial state" + stateFromSessionStart.getAllIncludingChildWidgets().stream()
//                .filter(w -> w.getWidgetVisibility() == Widget.WidgetVisibility.VISIBLE).collect(Collectors.toList()));
//        log("Elenco tutti i widget session state" + stateFromSessionStart.getAllIncludingChildWidgets().stream()
//                .filter(w -> w.getWidgetVisibility() == Widget.WidgetVisibility.VISIBLE).collect(Collectors.toList()));
        annotateDiffsInStates(stateFromSessionStart, sessionState);

        JSONObject jsonSharedModel = loadJSONModel(sharedModelFilePath);
        AppState currentSharedState = parseCompleteAppState(jsonSharedModel);
        AppState mergedSharedModel = mergeStateChanges(currentSharedState, sessionState);

        if (!saveStateModel(sharedModelFilePath, mergedSharedModel)) {
            return false;
        }

        String sessionModelFilePath = productFilePath + "/" + "session-state-" + dfFiles.format(new Date()) + ".json";
        if (!saveStateModel(sessionModelFilePath, sessionState)) {
            return false;
        }

        String propertiesFilePath = productFilePath + "/" + PRODUCT_PROPERTIES_FILE;
        saveProductProperties(propertiesFilePath);

        // Update products
        StateController.setProducts(getFolders(DATA_FILEPATH));

        return true;
    }

    protected void createFolderIfNotExist(String filePath) {
        File file = new File(filePath);
        file.mkdirs();
    }

    protected boolean saveProductProperties(String filePath) {
        try {
            FileWriter fileWriter = new FileWriter(filePath);
            StateController.getProductProperties().store(fileWriter, null);
            fileWriter.close();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private boolean saveStateModel(String filePath, AppState appState) {
        String jsonState = "";
        try {
            jsonState = appStateAsJSONObject(appState).toJSONString();

        } catch (Exception e) {
            log("Error while parsing app state as JSON object: " + e.getMessage());
            e.printStackTrace();
            return false;
        }

        try {
            FileWriter fileWriter = new FileWriter(filePath);
            PrintWriter printWriter = new PrintWriter(fileWriter);
            printWriter.print(jsonState);
            printWriter.close();
            fileWriter.close();
        } catch (Exception e) {
            log("Unable to save state model as file: " + e.getMessage());
            e.printStackTrace();
            return false;
        }

        log("Save state model file: " + filePath);
        return true;
    }

    private List<String> getFolders(String dirPath) {
        try {
            return Files.list(Paths.get(dirPath))
                    .filter(path -> Files.isDirectory(path))
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return new LinkedList<>();
        }
    }

    protected boolean isSameWidget(Widget widget, Widget other) {
        if (widget == null || other == null) {
            return false;
        }

        boolean isSameSubType = widget.getWidgetSubtype().equals(other.getWidgetSubtype());
        boolean isSameVisibility = widget.getWidgetVisibility().equals(other.getWidgetVisibility());

        boolean isSameHref = hasEqualMetaData("href", widget, other);
        boolean isSameXpath = hasEqualMetaData("xpath", widget, other);
        boolean isSameText = hasEqualMetaData("text", widget, other);
        boolean isSameTag = hasEqualMetaData("tag", widget, other);
        boolean isSameClass = hasEqualMetaData("class", widget, other);

        return isSameSubType
                && isSameVisibility
                && isSameHref
                && isSameXpath
                && isSameText
                && isSameTag
                && isSameClass;
    }

    protected String chooseStrValue(String value, String otherValue) {
        if (value == null && otherValue == null) {
            return null;
        }

        if (isNotEmpty(value) && isNotEmpty(otherValue)) {
            return value + " | " + otherValue;
        }

        if (isNotEmpty(otherValue)) {
            return otherValue;
        }

        return value;
    }

    private boolean isNotEmpty(String text) {
        return text != null && !text.isEmpty();
    }

    protected void mergeWidgetChanges(Widget widget, Widget changed) {
        log("Merge changes from widgets with ID '" + widget.getId() + "' into '" + changed.getId() + "'");
        changed.getMetadataKeys().forEach(key -> widget.putMetadata(key, changed.getMetadata(key)));

        widget.setWidgetType(changed.getWidgetType());

        String reported = chooseStrValue(widget.getReportedText(), changed.getReportedText());
        widget.setReportedText(reported);

        widget.setReportedDate(changed.getReportedDate());
        widget.setResolvedDate(changed.getResolvedDate());
        widget.setResolvedText(changed.getResolvedText());
    }

    /**
     * Add annotations as meta-data to track the changes of
     * widgets in each state after a session.
     * These annotations support the merging process with the shared state model.
     *
     * @param before app state from session start
     * @param after  changed app state that shall be annotated
     */
    protected void annotateDiffsInStates(AppState before, AppState after) {
        if (before == null && after == null) {
            return;
        }

        List<Widget> remainingBeforeWidgets = new LinkedList<>();
        List<Widget> afterWidgets = new LinkedList<>();

        if (before != null) {
            remainingBeforeWidgets = new LinkedList<>(before.getVisibleWidgets());
//            log("before state id" + before.getId());
//            log("before widgets" + before.getVisibleWidgets());
        }
//        else{
//            log("Before is null");
//            if(after != null){
//                log("After is not null");
//                log(after.getId());
//            }
//        }

        if (after != null) {
            afterWidgets = new LinkedList<>(after.getVisibleWidgets());
//            log("after state id" + after.getId());
//            log("after widgets" + after.getVisibleWidgets());
        }

        // A map for annotating difference composed of WidgetID and DiffType
        Map<String, DiffType> widgetDiff = new HashMap<>();

        for (Widget afterWidget : afterWidgets) {
            int foundIndex = indexOfSameWidget(afterWidget, remainingBeforeWidgets);
            boolean isPresent = foundIndex >= 0;

            DiffType diffType = DiffType.CREATED;
            AppState nextStateFromWidgetBefore = null;
            if (isPresent) {
                diffType = DiffType.NO_CHANGES;
                nextStateFromWidgetBefore = remainingBeforeWidgets.get(foundIndex).getNextState();
                remainingBeforeWidgets.remove(foundIndex);
            }
//            else {
//                // Posso assegnare punti qui per nuovo widget trovato
//            }

            widgetDiff.put(afterWidget.getId(), diffType);

            //the home state should not appear as a new state
            if (nextStateFromWidgetBefore != null && nextStateFromWidgetBefore.isHome()) {
                nextStateFromWidgetBefore = null;
            }
            AppState nextStateFromWidgetAfter = afterWidget.getNextState();
            if (nextStateFromWidgetAfter != null && nextStateFromWidgetAfter.isHome()) {
                nextStateFromWidgetAfter = null;
            }
            annotateDiffsInStates(nextStateFromWidgetBefore, nextStateFromWidgetAfter);
        }

        remainingBeforeWidgets.forEach(deletedWidget -> widgetDiff.put(deletedWidget.getId(), DiffType.DELETED));

        after.putMetadata(META_DATA_DIFF, widgetDiff);
    }

    /**
     * Merges changes of the session app state into the app state from the shared model.
     * The method {@link #annotateDiffsInStates(AppState, AppState)} must be called on
     * the session state before merging.
     *
     * @param sharedState  app state from the shared model
     * @param sessionState app state from the current session with changes
     * @return a copy of the shared state with changes merged from the session state.
     */
    protected AppState mergeStateChanges(AppState sharedState, AppState sessionState) {
        AppState result = deepCopy(sharedState);

        doMergeStateChangesIntoShared(result, sessionState);

        result.getVisibleStates().forEach(s -> s.removeMetadata(META_DATA_DIFF));

        return result;
    }

    private void doMergeStateChangesIntoShared(AppState sharedState, AppState sessionState) {
        if (sharedState == null && sessionState == null) {
            return;
        }

        if (sessionState == null) {
            return;
        }

        if (sharedState == null) {
            log("Unable to merge session state into NULL shared state. Caused by state with id: " + sessionState.getId());
            return;
        }

        sessionState.getMetadataKeys().stream()
                .filter(key -> sharedState.getMetadata(key) == null)
                .filter(key -> !key.equalsIgnoreCase(META_DATA_DIFF))
                .forEach(key -> sharedState.putMetadata(key, sessionState.getMetadata(key)));

        Map<String, DiffType> diffMap = getDiffMetaDataFromState(sessionState);
        if (diffMap.isEmpty()) {
            log("Session state with id " + sessionState.getId() + " doesn't have any diff annotations to proceed with merge.");
            return;
        }
        for (Entry<String, DiffType> diffItem : diffMap.entrySet()) {
            String widgetId = diffItem.getKey();

            switch (diffItem.getValue()) {
                case DELETED:
                    handleMergeDeletion(sharedState.getWidget(widgetId));
                    break;
                case CREATED:
                    handleMergeCreation(sharedState, sessionState, widgetId);
                    break;
                case NO_CHANGES:
                    handleMergeNoChange(sharedState, sessionState, widgetId);
                    break;
                default:
                    log("[Merge] DiffType '" + diffItem.getValue() + "' does not have a merging strategy");
                    break;
            }

        }
    }

    protected void handleMergeDeletion(Widget widget) {
        markAsDeleted(widget);

        AppState nextState = widget.getNextState();
        if (nextState == null) {
            return;
        }

        nextState.getAllIncludingChildWidgets().forEach(w -> markAsDeleted(w));
    }

    protected void handleMergeCreation(AppState sharedState, AppState sessionState, String widgetId) {
        Widget createdWidget = sessionState.getWidget(widgetId);
        int foundIndex = indexOfSameWidget(createdWidget, sharedState.getVisibleWidgets());
        boolean isPresentInSharedState = foundIndex >= 0;

        if (isPresentInSharedState) {
            Widget widgetFromShared = sharedState.getVisibleWidgets().get(foundIndex);
            Widget widgetFromSession = sessionState.getWidget(widgetId);
            mergeWidgetChanges(widgetFromShared, widgetFromSession);

            doMergeStateChangesIntoShared(widgetFromShared.getNextState(), widgetFromSession.getNextState());
            return;
        }

        sharedState.addWidget(createdWidget);
    }

    protected void handleMergeNoChange(AppState sharedState, AppState sessionState, String widgetId) {
        Widget originalWidget = sharedState.getWidget(widgetId);
        Widget otherWidget = sessionState.getWidget(widgetId);

        AppState nextStateFromShared = null;
        AppState nextStateFromSession = null;

        if (originalWidget != null) {
            nextStateFromShared = originalWidget.getNextState();
        }
        if (otherWidget != null) {
            nextStateFromSession = otherWidget.getNextState();
        }

        doMergeStateChangesIntoShared(nextStateFromShared, nextStateFromSession);
    }

    protected void handleMergeChange(AppState sharedState, AppState sessionState, String widgetId) {
        log("handleMergeChange() is not implemented yet");
    }

    @SuppressWarnings("unchecked")
    protected Map<String, DiffType> getDiffMetaDataFromState(AppState state) {
        try {
            Map<String, DiffType> diff = (Map<String, DiffType>) state.getMetadata(META_DATA_DIFF);
            return Optional.ofNullable(diff).orElseGet(() -> new HashMap<String, DiffType>());
        } catch (ClassCastException e) {
            log("Unable to cast meta-data object as Map<String, DiffType> in state with id " + state.getId());
            return new HashMap<>();
        }
    }

    protected int indexOfSameWidget(Widget widget, List<Widget> list) {
        for (int i = 0; i < list.size(); i++) {
            if (isSameWidget(widget, list.get(i))) {
                return i;
            }
        }

        return -1;
    }

    protected boolean hasEqualMetaData(String key, Widget widget, Widget other) {
        return String.valueOf(widget.getMetadata(key)).equals(String.valueOf(other.getMetadata(key)));
    }

    protected void markAsDeleted(Widget widget) {
        if (widget == null) {
            return;
        }

        widget.putMetadata(DELETED_AT, Instant.now().toEpochMilli());
    }

    protected boolean isMarkedAsDeleted(Widget widget) {
        try {
            Object epochMilli = widget.getMetadata(DELETED_AT);
            if (epochMilli == null) {
                return false;
            }
            return parseLong(epochMilli.toString()) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static void log(String message) {
        String now = df.format(new Date());
        System.out.printf("[%s] %s \n", now, message);
    }

    @SuppressWarnings("unchecked")
    protected <T extends Serializable> T deepCopy(T original) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(bos);
            out.writeObject(original);

            ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
            ObjectInputStream in = new ObjectInputStream(bis);
            return (T) in.readObject();
        } catch (Exception e) {
            log("Unable to create a deep copy of an object: " + e.getMessage());
            return null;
        }
    }

    // Begin of crowdsourcing plugin -- can be brought into a separate file

    public void performAction(Action action) {
        if (action.getComment().equals("MultiUser: Start")) {
            // Micro task selector will be launched here
            startCrowdsourcingSession();
        }
    }

    protected void startCrowdsourcingSession() {
        log("Starting crowdsourcing session");
        SelectProductDialog selectProductDialog = new SelectProductDialog(StateController.getParentFrame());
        if (selectProductDialog.showDialog()) {
            if (selectProductDialog.isCanceled()) {
                return;
            }
            StateController.setProduct(selectProductDialog.getProduct());
        }
        StartSessionDialog dialog = new StartSessionDialog(StateController.getParentFrame());
        if (dialog.showDialog()) {
            if (dialog.isCanceled()) {
                return;
            }
            microTaskWidget = "167579261770463";
            microTaskState = "16757926180282";
            startSession(selectProductDialog.getProduct(), dialog.getProductVersion(), dialog.getTesterName(),
                    dialog.getProductView(), dialog.getHomeLocator(), dialog.getProductWiewWidth(),
                    dialog.getProductWiewHeight(), dialog.isHeadlessBrowser());
//            timeHideEmptyBar = System.currentTimeMillis() + 30000;
        }
    }
}
