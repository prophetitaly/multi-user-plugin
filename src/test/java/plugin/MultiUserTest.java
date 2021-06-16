// SPDX-FileCopyrightText: 2021 Andreas Bauer
//
// SPDX-License-Identifier: MIT

package plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.awt.Rectangle;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.Test;

import scout.AppState;
import scout.Widget;
import scout.Widget.WidgetStatus;
import scout.Widget.WidgetSubtype;
import scout.Widget.WidgetType;
import scout.Widget.WidgetVisibility;

public class MultiUserTest extends MultiUser {

    public MultiUserTest() {
        super(new ArrayList<String>());
    }

    @Test
    public void testisSameWidget() {
        Widget widget = createWidget("1234");

        Widget other = createWidget("5678");
        other.setCreatedBy("Second tester"); 

        assertTrue(isSameWidget(widget, other));
    }

    @Test
    public void testIsSameWidget_ShouldFail() {
        Widget widget = createWidget("1234");
        
        assertFalse(isSameWidget(widget, null));
        assertFalse(isSameWidget(widget, new Widget()));
        
        Widget other = null;
        other = createWidget("1234");
        other.setWidgetVisibility(WidgetVisibility.HIDDEN);
        assertFalse(isSameWidget(widget, other));

        other = createWidget("1234");
        other.setWidgetType(WidgetType.ISSUE);
        assertFalse(isSameWidget(widget, other));
        
        other = createWidget("1234");
        other.setWidgetSubtype(WidgetSubtype.RIGHT_CLICK_ACTION);
        assertFalse(isSameWidget(widget, other));
    }

    @Test
    public void testIsSameWidget_ShouldFailMetaData() {
        Widget widget = createWidget("1234");
        Widget other = null;

        other = createWidget("1234");
        other.putMetadata("xpath", "/html[1]/body[1]");
        assertFalse(isSameWidget(widget, other));
        
        other = createWidget("1234");
        other.putMetadata("href", "https://othersite.de/logout");
        assertFalse(isSameWidget(widget, other));
        
        other = createWidget("1234");
        other.putMetadata("text", "Logout");
        assertFalse(isSameWidget(widget, other));
        
        other = createWidget("1234");
        other.putMetadata("tag", "Button");
        assertFalse(isSameWidget(widget, other));
        
        other = createWidget("1234");
        other.putMetadata("class", "btn"); 
        assertFalse(isSameWidget(widget, other));
    }

    @Test
    public void testHasEqualMetaData() {
        Widget widget = createWidget("1");
        Widget other = createWidget("1");

        assertTrue(hasEqualMetaData("xpath", widget, other));
        assertTrue(hasEqualMetaData("href", widget, other));
        assertTrue(hasEqualMetaData("text", widget, other));
        assertTrue(hasEqualMetaData("tag", widget, other));
        assertTrue(hasEqualMetaData("class", widget, other));
        
        widget.putMetadata("answer", "42");
        other.putMetadata("answer", 42);
        assertTrue(hasEqualMetaData("answer", widget, other));
    }

    @Test
    public void testHasEqualMetaData_Fail() {
        Widget widget = createWidget("1");
        Widget other = createWidget("1");

        other.removeMetadata("text");

        assertFalse(hasEqualMetaData("text", widget, other));
        assertFalse(hasEqualMetaData("text", other, widget));
    }
    
    @Test
    public void testMergeAppState_Simple() {
        AppState firsState = new AppState("10","Home");
        firsState.addWidget(createWidget("1"));
        Widget w2 = createWidget("2");
        w2.putMetadata("href", "https://othersite.de/new");
        w2.putMetadata("xpath", "/html[1]/body[1]/div[1]");
        firsState.addWidget(w2);

        AppState secondState = new AppState("20","Home");
        secondState.addWidget(createWidget("3"));
        Widget w4 = createWidget("4");
        w4.putMetadata("href", "https://othersite.de/users");
        w4.putMetadata("xpath", "/html[1]/body[1]/div[1]/div[1]/div[1]");
        secondState.addWidget(w4);

        AppState result = mergeAppState(firsState, secondState);

        assertNotNull(result);
        assertEquals(3, result.getVisibleActions().size());
        
        assertNotNull(result.getWidget("1"));
        assertEquals(w2, result.getWidget("2"));
        assertEquals(w4, result.getWidget("4"));
        assertNull(result.getWidget("3"));
    }

   /**
    * Expected graph after merge:
    * 
    *    H
    *    | 
    *    L2
    *    |
    *    L3
    *    |
    *    L4
    *   / \
    * L50 L51
    *  |   |
    * L60 L61
    *  |   |
    * L70 L71 
    */ 
    @Test
    public void testMergeAppState_Scenario10() throws Exception {
        String filePath = JSONStateParser.class.getClassLoader().getResource("scenario_10/state.json").getPath();
        AppState state = loadJSONModel(filePath);
        String filePathOther = JSONStateParser.class.getClassLoader().getResource("scenario_10/state_other.json").getPath();
        AppState other = loadJSONModel(filePathOther);

        AppState result = mergeAppState(state, other);

        assertNotNull(result);
        assertEquals("0", result.getId());
        assertEquals("Home", result.getBookmark());
        assertEquals(1, result.getVisibleActions().size());
        
        Widget w1 = result.getVisibleActions().get(0);
        assertEquals("162124543220764", w1.getId());
        
        AppState level2 = w1.getNextState();
        assertNotNull(level2);
        assertEquals(1, level2.getVisibleActions().size());
        
        Widget w2 = level2.getVisibleActions().get(0);
        assertEquals("162124543930275", w2.getId());

        AppState level3 = w2.getNextState();
        assertNotNull(level3);
        assertEquals(1, level3.getVisibleActions().size());

        Widget w3 = level3.getVisibleActions().get(0);
        assertEquals("162124544379288", w3.getId());

        AppState level4 = w3.getNextState();
        assertNotNull(level4);
        assertEquals(1, level4.getVisibleActions().size());

        Widget w4 = level4.getVisibleActions().get(0);
        assertEquals("16212454465295", w4.getId());

        AppState level5 = w4.getNextState();
        assertNotNull(level5);
        assertEquals(2, level5.getVisibleActions().size());

        Widget w50 = level5.getVisibleActions().get(0);
        Widget w51 = level5.getVisibleActions().get(1);
        assertEquals("162124545582218", w50.getId());
        assertEquals("162124574889049", w51.getId());

        AppState level60 = w50.getNextState();
        assertNotNull(level60);
        assertEquals(1, level60.getVisibleActions().size());
        AppState level61 = w51.getNextState();
        assertNotNull(level61);
        assertEquals(1, level61.getVisibleActions().size());
        
        Widget w60 = level60.getVisibleActions().get(0);
        assertEquals("16212454633346", w60.getId());
        Widget w61 = level61.getVisibleActions().get(0);
        assertEquals("16212457535564", w61.getId());
        
        AppState level70 = w60.getNextState();
        assertNotNull(level70);
        assertEquals(1, level70.getVisibleActions().size());
        AppState level71 = w61.getNextState();
        assertNotNull(level71);
        assertEquals(1, level71.getVisibleActions().size());
        
        Widget w70 = level70.getVisibleActions().get(0);
        assertEquals("162124547047918", w70.getId());
        Widget w71 = level71.getVisibleActions().get(0);
        assertEquals("162124575555994", w71.getId());
    }

    @Test
    public void testMergeAppState_OneNullArg() {
        AppState firsState = new AppState("10","Home");
        firsState.addWidget(createWidget("1"));
        Widget w2 = createWidget("2");
        w2.putMetadata("href", "https://othersite.de/new");
        w2.putMetadata("xpath", "/html[1]/body[1]/div[1]");
        firsState.addWidget(w2); 

        AppState result = mergeAppState(firsState, null);

        assertNotNull(result);
        assertEquals(firsState, result);
        
        result = mergeAppState(null, firsState);
        
        assertNotNull(result);
        assertEquals(firsState, result);

    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testMergeAppState_Null () {
        mergeAppState(null, null);
    }

    @Test
    public void testChooseStrValue() {
        assertNull(chooseStrValue(null, null));
        assertEquals("val1", chooseStrValue("val1", null));
        assertEquals("val2", chooseStrValue(null, "val2"));
        assertEquals("val3", chooseStrValue("", "val3"));
        assertEquals("val4", chooseStrValue("val4", ""));
        assertEquals("val5", chooseStrValue("val5", "val6"));
    }

    @Test
    public void testMergeSameWidgets() {
        Widget widget = createWidget("1");
        widget.putMetadata("only-in-widget", "abc");
        widget.putMetadata("coverage", "100");
        widget.setNextState(new AppState("2212", "next-state"));
        
        Widget other = createWidget("2");
        other.putMetadata("only-in-other-widget", "def");
        other.putMetadata("coverage", "90");
        other.setNextState(new AppState("33433", "other-next-state")); 
        
        Widget result = mergeSameWidgets(widget, other);

        assertNotNull(result);
        assertTrue(isSameWidget(result, widget));
        assertEquals("1", result.getId());

        assertEquals("100", result.getMetadata("coverage"));
        assertEquals("abc", result.getMetadata("only-in-widget"));
        assertEquals("def", result.getMetadata("only-in-other-widget"));

        assertNull(result.getNextState());
    }

    @Test
    public void testDeepCopy() throws IOException, ClassNotFoundException, ParseException {
        Widget original = createWidget("1");
        
        Widget deepCopy = deepCopy(original);

        assertNotNull(deepCopy);
        assertEquals(0, original.compareTo(deepCopy));
    }

    @Test
    public void testGetAllVisibleActionsRecursive() {
        Widget w1 = createWidget("1");
        Widget w2 = createWidget("2");
        Widget w3 = createWidget("3");
        Widget w4 = createWidget("4");
        
        AppState s1 = new AppState("S1");
        AppState s2 = new AppState("S2");
        AppState s3 = new AppState("S3");
        
        w1.setNextState(s1);
        s1.addWidget(w2);
        w2.setNextState(s2);
        s2.addWidget(w3);
        w3.setNextState(s3);
        s3.addWidget(w4);

        List<Widget> result = getAllVisibleActionsRecursive(w1);

        assertEquals(3, result.size());
        assertTrue(result.contains(w2));
        assertTrue(result.contains(w3));
        assertTrue(result.contains(w4));         
    }

    @Test
    public void testGetAllVisibleActionsRecursive_Null() {
        List<Widget> result = getAllVisibleActionsRecursive(null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    } 

    private Widget createWidget(String id) {
        Widget widget = new Widget();
        widget.setId(id);
        widget.setWidgetStatus(WidgetStatus.LOCATED);
        widget.setCreatedBy("Mr. Tester"); 
        widget.setWidgetSubtype(WidgetSubtype.LEFT_CLICK_ACTION);
        widget.setWidgetVisibility(WidgetVisibility.VISIBLE);
        widget.setLocationArea(new Rectangle(970,117,14,36));
        widget.putMetadata("xpath", "/html[1]/body[1]/div[1]/div[1]/header[1]/div[1]/a[1]");
        widget.putMetadata("href", "https://mydomain.de/login");
        widget.putMetadata("text", "Login");
        widget.putMetadata("tag", "A");
        widget.putMetadata("class", "v-btn v-btn--flat v-btn--router v-btn--text theme--dark v-size--default"); 
        return widget;
    }

    private AppState loadJSONModel(String filePath) throws FileNotFoundException, IOException, ParseException {
        JSONParser jsonParser = new JSONParser();
        FileReader reader = new FileReader(filePath);			
        
        JSONObject jsonModel = (JSONObject) jsonParser.parse(reader);
        reader.close();
        
        return JSONStateParser.parseCompleteAppState(jsonModel);
    }
}
