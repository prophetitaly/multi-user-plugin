// SPDX-FileCopyrightText: 2021 Andreas Bauer
//
// SPDX-License-Identifier: MIT

package plugin;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import plugin.MultiUser.DiffType;
import scout.*;
import scout.Widget.WidgetStatus;
import scout.Widget.WidgetSubtype;
import scout.Widget.WidgetType;
import scout.Widget.WidgetVisibility;

public class JSONStateParser {

    private static final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");


    // ********************************************
    // * Parse Scout objects to JSONObjects       *
    // ********************************************

    @SuppressWarnings("unchecked")
    public static JSONObject appStateAsJSONObject(AppState state) {
        JSONObject json = new JSONObject();

        json.put("product", StateController.getProduct());
        json.put("last-updated-at-ms", Instant.now().toEpochMilli());

        List<JSONObject> paths = state.getPaths().stream()
                .map(p -> pathAsJSONObject(p))
                .collect(Collectors.toList());

        json.put("paths", paths);

        Map<String, Widget> allUsedWidgets = new HashMap<>();

        json.put("state", stateTreeAsJSONObject(state, allUsedWidgets));

        List<String> issues = state.getAllIssues().stream()
                .map(w -> w.getId())
                .collect(Collectors.toList());
        json.put("issues", issues);

        List<JSONObject> allUsedWidgetsAsJSON = allUsedWidgets.values().stream()
                .map(w -> widgetAsJSONObject(w))
                .collect(Collectors.toList());

        List<JSONObject> matchingWidgetsAsJSON = allUsedWidgets.values().stream()
                .filter(w -> w.hasMetadata("matching_widget"))
                .map(w -> (Widget) w.getMetadata("matching_widget"))
                .map(w -> widgetAsJSONObject(w))
                .collect(Collectors.toList());

        allUsedWidgetsAsJSON.addAll(matchingWidgetsAsJSON);
        json.put("all-widgets", allUsedWidgetsAsJSON);
        return json;
    }

    @SuppressWarnings("unchecked")
    public static JSONObject stateTreeAsJSONObject(AppState state, Map<String, Widget> allUsedWidgets) {
        JSONObject json = new JSONObject();
        json.put("state-id", state.getId());
        json.put("product-version", state.getProductVersions());
        json.put("bookmarks", state.getBookmark());

        List<Widget> visibleWidgets = state.getVisibleWidgets();

        json.put("visible-widgets", visibleWidgets.stream().map(w -> stateWidgetAsSimpleJSONObject(w, allUsedWidgets)).collect(Collectors.toList()));

        visibleWidgets.forEach(w -> allUsedWidgets.put(w.getId(), w));

        JSONObject jsonMetaData = new JSONObject();
        state.getMetadataKeys().stream()
                .filter(k -> k != "multi-user-diff-widgets")
                .forEach(k -> jsonMetaData.put(k, String.valueOf(state.getMetadata(k))));

        Map<String, DiffType> diff = (Map<String, DiffType>) state.getMetadata("multi-user-diff-widgets");
        if (diff != null) {
            Map<String, String> diffStr = new HashMap<>();
            diff.entrySet().forEach(e -> diffStr.put(e.getKey(), e.getValue().toString()));
            jsonMetaData.put("multi-user-diff-widgets", diffStr);
        }

        json.put("meta-data", jsonMetaData);

        return json;
    }

    @SuppressWarnings("unchecked")
    public static JSONObject pathAsJSONObject(Path path) {
        JSONObject json = new JSONObject();
        json.put("id", path.getId());
        json.put("product-version", path.getProductVersion());
        json.put("session-id", path.getSessionId());
        json.put("session-duration", path.getSessionDuration());
        json.put("created-at-ms", path.getCreatedDate().getTime());
        json.put("tester", path.getTester());

        List<String> widgetIDs = path.getWidgets().stream()
                .map(w -> w.getId())
                .collect(Collectors.toList());

        json.put("widgets", widgetIDs);
        return json;
    }

    @SuppressWarnings("unchecked")
    public static JSONObject widgetAsJSONObject(Widget widget) {
        JSONObject json = new JSONObject();
        json.put("id", widget.getId());
        json.put("text", widget.getText());
        json.put("weight", widget.getWeight());
        json.put("type", widget.getWidgetType().name());
        json.put("subtype", widget.getWidgetSubtype().toString());
        json.put("status", widget.getWidgetStatus().toString());

        if (widget.getCreatedDate() != null) {
            json.put("created-date-ms", widget.getCreatedDate().getTime());
        }
        if (widget.getResolvedDate() != null) {
            json.put("resolved-date-ms", widget.getResolvedDate().getTime());
        }
        if (widget.getReportedDate() != null) {
            json.put("reported-date-ms", widget.getReportedDate().getTime());
        }

        json.put("created-by", widget.getCreatedBy());
        json.put("created-by-plugin", widget.getCreatedByPlugin());
        json.put("comment", widget.getComment());
        json.put("reported-text", widget.getReportedText());
        json.put("reported-by", widget.getReportedBy());
        json.put("meta-data", metadataAsJSONObject(widget));
        json.put("visibility", widget.getWidgetVisibility().name());
        json.put("location", locationAreaAsJSONObject(widget.getLocationArea()));

        return json;
    }

    @SuppressWarnings("unchecked")
    public static JSONObject locationAreaAsJSONObject(java.awt.Rectangle locRec) {
        if (locRec == null) {
            return null;
        }

        JSONObject jsonLoc = new JSONObject();
        jsonLoc.put("x", String.valueOf(locRec.x));
        jsonLoc.put("y", String.valueOf(locRec.y));
        jsonLoc.put("width", String.valueOf(locRec.width));
        jsonLoc.put("height", String.valueOf(locRec.height));
        return jsonLoc;
    }

    @SuppressWarnings("unchecked")
    public static JSONObject metadataAsJSONObject(Widget widget) {
        JSONObject json = new JSONObject();
        if (widget.hasMetadata("matching_widget")) {
            String widgetId = ((Widget) widget.getMetadata("matching_widget")).getId();
            json.put("matching_widget", widgetId);
        }

        widget.getMetadataKeys().stream()
                .filter(k -> !k.equals("matching_widget"))
                .filter(k -> !k.equals("neighbors"))
                .forEach(k -> json.put(k, String.valueOf(widget.getMetadata(k))));

        return json;
    }

    @SuppressWarnings("unchecked")
    public static JSONObject stateWidgetAsSimpleJSONObject(Widget widget, Map<String, Widget> allUsedWidgets) {
        JSONObject json = new JSONObject();
        json.put("id", widget.getId());

        AppState nexState = widget.getNextState();
        if (nexState == null || nexState.isHome()) {
            json.put("next-state", null);
            return json;
        }

        json.put("next-state", stateTreeAsJSONObject(widget.getNextState(), allUsedWidgets));

        return json;
    }

    // ********************************************
    // * Parse JSONObjects to Scout objects       *
    // ********************************************

    public static AppState parseCompleteAppState(JSONObject jsonState) {
        List<Widget> allWidgets = new LinkedList<>();
        AppState appState = null;

        try {
            allWidgets = parseWidgets((JSONArray) jsonState.get("all-widgets"));
            appState = parseState((JSONObject) jsonState.get("state"), allWidgets);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return appState;
    }

    public static AppState parseState(JSONObject jsonState, List<Widget> allWidgets) {
        String id = (String) jsonState.get("state-id");
        String bookmark = (String) jsonState.get("bookmarks");
        JSONArray jsonWidgets = (JSONArray) jsonState.get("visible-widgets");

        List<Widget> visibleWidgets = new LinkedList<>();
        Iterator i = jsonWidgets.iterator();
        while (i.hasNext()) {
            JSONObject item = (JSONObject) i.next();
            String widgetID = (String) item.get("id");
            Widget widget = allWidgets.stream()
                    .filter(w -> w.getId().equals(widgetID))
                    .findFirst()
                    .orElse(null);

            JSONObject jsonMetaData = (JSONObject) item.get("meta-data");
            if (jsonMetaData != null) {
                String matchingWidgetId = String.valueOf(jsonMetaData.get("matching_widget"));
                if (matchingWidgetId != null && matchingWidgetId.length() > 0) {
                    Widget matchingWidget = allWidgets.stream()
                            .filter(w -> w.getId().equals(matchingWidgetId))
                            .findFirst()
                            .orElse(null);
                    widget.putMetadata("matching_widget", matchingWidget);
                }
            }

            JSONObject nextStatJsonObject = (JSONObject) item.get("next-state");
            if (nextStatJsonObject != null) {
                AppState nextState = parseState(nextStatJsonObject, allWidgets);
                widget.setNextState(nextState);
            }
            visibleWidgets.add(widget);
        }

        AppState state = new AppState(id, bookmark);
        state.addWidgets(visibleWidgets, WidgetVisibility.VISIBLE, null);

        return state;
    }

    public static List<Widget> parseWidgets(JSONArray jsonWidgets) {
        List<Widget> widgets = new ArrayList<>();
        Iterator i = jsonWidgets.iterator();

        while (i.hasNext()) {
            JSONObject jsonWidget = (JSONObject) i.next();
            Widget widget = parseWidget(jsonWidget);
            widgets.add(widget);
        }
        return widgets;
    }

    public static Widget parseWidget(JSONObject jsonWidget) {
        Widget widget = new Widget();
        widget.setId((String) jsonWidget.get("id"));
        widget.setText((String) jsonWidget.get("text"));
        widget.setCreatedBy((String) jsonWidget.get("created-by"));
        widget.setCreatedByPlugin((String) jsonWidget.get("created-by-plugin"));
        widget.setComment((String) jsonWidget.get("comment"));
        widget.setWidgetVisibility(WidgetVisibility.valueOf((String) jsonWidget.get("visibility")));

        WidgetStatus status = WidgetStatus.valueOf((String) jsonWidget.get("status"));
        widget.setWidgetStatus(status);

        Date created = parseDate(jsonWidget.get("created-date-ms"));
        widget.setCreatedDate(created);
        Date resolved = parseDate(jsonWidget.get("resolved-date-ms"));
        widget.setResolvedDate(resolved);
        Date reported = parseDate(jsonWidget.get("reported-date-ms"));
        widget.setReportedDate(reported);

        String weightStr = String.valueOf(jsonWidget.get("weight"));
        Double weight = Double.parseDouble(weightStr);
        widget.setWeight(weight);

        String type = (String) jsonWidget.get("type");
        widget.setWidgetType(WidgetType.valueOf(type));

        String subType = (String) jsonWidget.get("subtype");
        widget.setWidgetSubtype(WidgetSubtype.valueOf(subType));

        widget.setReportedText((String) jsonWidget.get("reported-text"));
        widget.setReportedBy((String) jsonWidget.get("reported-by"));

        JSONObject locRec = (JSONObject) jsonWidget.get("location");
        if (locRec != null) {
            int locX = Integer.parseInt((String) locRec.get("x"));
            int locY = Integer.parseInt((String) locRec.get("y"));
            int locWidth = Integer.parseInt((String) locRec.get("width"));
            int locHeight = Integer.parseInt((String) locRec.get("height"));
            widget.setLocationArea(new java.awt.Rectangle(locX, locY, locWidth, locHeight));
        }

        JSONObject jsonMetadata = (JSONObject) jsonWidget.get("meta-data");
        widget.putMetadata("type", (String) jsonMetadata.get("type"));
        widget.putMetadata("title", (String) jsonMetadata.get("title"));
        widget.putMetadata("xpath", (String) jsonMetadata.get("xpath"));
        widget.putMetadata("name", (String) jsonMetadata.get("name"));
        widget.putMetadata("href", (String) jsonMetadata.get("href"));
        widget.putMetadata("id", (String) jsonMetadata.get("id"));
        widget.putMetadata("text", (String) jsonMetadata.get("text"));
        widget.putMetadata("tag", (String) jsonMetadata.get("tag"));
        widget.putMetadata("class", (String) jsonMetadata.get("class"));
        if(jsonMetadata.get("multi-user-merge-deleted-at") != null) {
            widget.putMetadata("multi-user-merge-deleted-at", (String) jsonMetadata.get("multi-user-merge-deleted-at"));
        }

        return widget;
    }

    protected static Date parseDate(Object jsonMilliseconds) {
        try {
            return new Date((long) jsonMilliseconds);
        } catch (Exception e) {
            return null;
        }
    }

    private static void log(String message) {
        String now = df.format(new Date());
        System.out.printf("[%s] %s \n", now, message);
    }
}
