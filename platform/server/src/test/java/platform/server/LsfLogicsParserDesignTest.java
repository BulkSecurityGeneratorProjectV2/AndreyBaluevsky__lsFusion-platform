package platform.server;

import org.apache.commons.io.FileUtils;
import org.junit.*;
import org.junit.rules.TestName;
import platform.server.data.sql.PostgreDataAdapter;
import platform.server.form.view.ComponentView;
import platform.server.form.view.ContainerView;
import platform.server.form.view.GroupObjectView;
import platform.server.form.view.PropertyDrawView;
import platform.server.logics.scripted.ScriptedBusinessLogics;
import platform.server.logics.scripted.ScriptingErrorLog;
import platform.server.logics.scripted.*;
import platform.server.logics.scripted.ScriptingFormEntity;
import platform.server.logics.linear.LP;
import platform.server.logics.scripted.ScriptingLogicsModule;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;

import static org.junit.Assert.*;

public class LsfLogicsParserDesignTest {
    @Rule
    public TestName name = new TestName();

    private File testScriptFile;

    private ScriptedBusinessLogics bl;

    private ScriptingLogicsModule LM;

    private ScriptingFormEntity entity;

    private ScriptedFormView design;

    private GroupObjectView sGroup;
    private GroupObjectView aGroup;

    @BeforeClass
    public static void setUpTests() throws Exception {
        Settings.instance = new Settings();
        FileUtils.cleanDirectory(new File("src/test/resources/testscripts"));
    }

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
//        if (testScriptFile != null && testScriptFile.exists()) {
//            testScriptFile.delete();
//        }

        testScriptFile = null;
        bl = null;
        design = null;
        aGroup = null;
        sGroup = null;
    }

    @Test
    public void testDefaultDesignCreation() throws Exception {
        createDesign("DESIGN storeArticle FROM DEFAULT;");

        assertNotNull(design.getComponentBySID("main"));

        assertNotNull(design.getComponentBySID("a.box"));
        assertNotNull(design.getComponentBySID("a.panel"));
        assertNotNull(design.getComponentBySID("a.grid"));
        assertNotNull(design.getComponentBySID("a.grid.box"));
        assertNotNull(design.getComponentBySID("a.showType"));
        assertNotNull(design.getComponentBySID("a.classChooser"));
        assertNotNull(design.getComponentBySID("a.controls"));
        assertNotNull(design.getComponentBySID("a.filters"));

        assertNotNull(design.getComponentBySID("functions.box"));

        assertNotNull(design.getComponentBySID("functions.print"));
        assertNotNull(design.getComponentBySID("functions.edit"));
        assertNotNull(design.getComponentBySID("functions.xls"));
        assertNotNull(design.getComponentBySID("functions.null"));
        assertNotNull(design.getComponentBySID("functions.apply"));
        assertNotNull(design.getComponentBySID("functions.cancel"));
        assertNotNull(design.getComponentBySID("functions.ok"));

        assertTrue(design.getComponentBySID("a.panel") instanceof ComponentView);
        assertFalse(design.getComponentBySID("a.grid") instanceof ContainerView);
    }

    @Test
    public void testSkipDefaultDesignCreation() throws Exception {
        createDesign("DESIGN storeArticle;", false);

        assertNotNull(design.getComponentBySID("main"));

        assertNotNull(design.getComponentBySID("a.grid"));
        assertNotNull(design.getComponentBySID("a.showType"));
        assertNotNull(design.getComponentBySID("a.classChooser"));

        assertNotNull(design.getComponentBySID("functions.print"));
        assertNotNull(design.getComponentBySID("functions.edit"));
        assertNotNull(design.getComponentBySID("functions.xls"));
        assertNotNull(design.getComponentBySID("functions.null"));
        assertNotNull(design.getComponentBySID("functions.apply"));
        assertNotNull(design.getComponentBySID("functions.cancel"));
        assertNotNull(design.getComponentBySID("functions.ok"));

        assertNull(design.getComponentBySID("functions.box", false));

        assertNull(design.getComponentBySID("a.box", false));
        assertNull(design.getComponentBySID("a.panel", false));
        assertNull(design.getComponentBySID("a.grid.box", false));
        assertNull(design.getComponentBySID("a.controls", false));
        assertNull(design.getComponentBySID("a.filters", false));
    }

    @Test
    public void testSetFormViewProperties() throws Exception {
        createDesign("DESIGN storeArticle FROM DEFAULT {\n" +
                     "    caption='some';\n" +
                     "    title='some2';\n" + //title <==> caption
                     "    overridePageWidth=12;\n" +
                     "}");

        assertEquals(design.caption, "some2");
        assertEquals((long) design.overridePageWidth, 12);
    }

    @Test
    public void testSetGroupViewProperties() throws Exception {
        createDesign("DESIGN storeArticle FROM DEFAULT {\n" +
                     "    GROUP(s) {\n" +
                     "        highlightColor = #321233;\n" +
                     "        tableRowsCount = 10;\n" +
                     "        needVerticalScroll = FALSE;\n" +
                     "    }" +
                     "}");

        assertEquals(sGroup.highlightColor, new Color(0x321233));
        assertEquals((long) sGroup.tableRowsCount, 10);
        assertEquals(sGroup.needVerticalScroll, false);
    }

    @Test(expected = RuntimeException.class)
    public void testSetUnknownProperties() throws Exception {
        createDesign("DESIGN storeArticle FROM DEFAULT {\n" +
                     "    caption23='some';\n" +
                     "}");
    }

    @Test
    public void testSetPropertyDrawViewProperties() throws Exception {
        createDesign("DESIGN storeArticle FROM DEFAULT {\n" +
                     "    PROPERTY(bar) {\n" +
                     "        autoHide = TRUE;\n" +
                     "        showTableFirst = TRUE;\n" +
                     "        editOnSingleClick = TRUE;\n" +
                     "        hide = TRUE;\n" +
                     "        regexp = '[\\d]+';\n" +
                     "        regexpMessage = 'regexpmsg';\n" +
                     "    }\n" +
                     "\n" +
                     "    PROPERTY(storeSizeName(s)) {\n" +
                     "        echoSymbols = FALSE;\n" +
                     "        highlightColor = #120BAC;\n" +
                     "    }\n" +
                     "\n" +
                     "    PROPERTY(name(a)) {\n" +
                     "        minimumCharWidth = 11;\n" +
                     "        maximumCharWidth = 12;\n" +
                     "        preferredCharWidth = 13;\n" +
                     "        showEditKey = TRUE;\n" +
                     "    }\n" +
                     "\n" +
                     "    PROPERTY(foo(s, a)) {\n" +
                     "        focusable = FALSE;\n" +
                     "        panelLabelAbove = TRUE;\n" +
                     "        caption = 'This is bar\\'s caption!';\n" +
                     "        clearText = TRUE;\n" +
                     "    }\n" +
                     "}");

        PropertyDrawView barView = design.get(entity.getPropertyDraw(findLPBySID("bar")));
        PropertyDrawView storeSizeView = design.get(entity.getPropertyDraw(findLPBySID("storeSizeName")));
        PropertyDrawView nameView = design.get(entity.getPropertyDraw(findLPBySID("name"), 1));
        PropertyDrawView fooView = design.get(entity.getPropertyDraw(findLPBySID("foo")));

        assertTrue(barView.autoHide);
        assertTrue(barView.showTableFirst);
        assertTrue(barView.editOnSingleClick);
        assertTrue(barView.hide);
        assertEquals(barView.regexp, "[\\d]+");

        assertEquals(storeSizeView.highlightColor, new Color(0x120BAC));

        assertEquals(nameView.getMinimumCharWidth(), 11);
        assertEquals(nameView.getMaximumCharWidth(), 12);
        assertTrue(nameView.showEditKey);

        assertEquals(fooView.caption, "This is bar's caption!");
    }

    @Test
    public void testSetPropertiesWithCustomConverters() throws Exception {
        createDesign("DESIGN storeArticle FROM DEFAULT {\n" +
                     "    PROPERTY(bar) {\n" +
                     "        editKey = 'alt shift X';\n" +
                     "        headerFont = 'Tahoma bold 15';\n" +
                     "        font = '12 Tahoma italic bold';\n" +
                     "    }\n" +
                     "}");

        PropertyDrawView barView = design.get(entity.getPropertyDraw(findLPBySID("bar")));

        assertEquals(barView.editKey, KeyStroke.getKeyStroke("alt shift X"));
        assertEquals(barView.editKey, KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.ALT_MASK | InputEvent.SHIFT_MASK));
        assertEquals(barView.design.headerFont, new Font("Tahoma", Font.BOLD, 15));
        assertEquals(barView.design.font, new Font("Tahoma", Font.BOLD | Font.ITALIC, 12));
    }

    @Test(expected = RuntimeException.class)
    public void testSetKeyStrokePropertyFails() throws Exception {
        createDesign("DESIGN storeArticle FROM DEFAULT {\n" +
                     "    PROPERTY(bar) {\n" +
                     "        editKey = 'alt shift Xdf';\n" +
                     "    }\n" +
                     "}");
    }

    @Test(expected = RuntimeException.class)
    public void testSetFontPropertyFails1() throws Exception {
        createDesign("DESIGN storeArticle FROM DEFAULT {\n" +
                     "    PROPERTY(bar) {\n" +
                     "        font = 'alt shift Xdf';\n" +
                     "    }\n" +
                     "}");
    }

    @Test(expected = RuntimeException.class)
    public void testSetFontPropertyFails2() throws Exception {
        createDesign("DESIGN storeArticle FROM DEFAULT {\n" +
                     "    PROPERTY(bar) {\n" +
                     "        font = '12 italic bold name1 name2';\n" +
                     "    }\n" +
                     "}");
    }

    @Test(expected = RuntimeException.class)
    public void testSetFontPropertyFails3() throws Exception {
        createDesign("DESIGN storeArticle FROM DEFAULT {\n" +
                     "    PROPERTY(bar) {\n" +
                     "        font = '-12 italic Tahoma';\n" +
                     "    }\n" +
                     "}");
    }

    private LP findLPBySID(String sid) throws ScriptingErrorLog.SemanticErrorException {
        return LM.findLPByCompoundName(sid);
    }

    private void createDesign(String additionalPart) throws Exception {
        createDesign(additionalPart, true);
    }

    private void createDesign(String additionalPart, boolean hasDefault) throws Exception {
        String fileContent = FileUtils.readFileToString(new File("src/test/resources/testscript.lsf"), "UTF-8") + additionalPart;
        testScriptFile = new File("src/test/resources/testscripts/" + name.getMethodName() + ".lsf");
        FileUtils.writeStringToFile(testScriptFile, fileContent, "UTF-8");

        bl = new ScriptedBusinessLogics("scriptedLogicsUnitTest",
                                        new PostgreDataAdapter("scripted_logic_unittest", "localhost", "postgres", "11111", true),
                                        1234,
                                        "SampleFeatures",
                                        testScriptFile.getAbsolutePath());
        bl.afterPropertiesSet();

        LM = (ScriptingLogicsModule)bl.findModule("SampleFeatures");
        assertNotNull(LM);

        entity = (ScriptingFormEntity) bl.LM.baseElement.getNavigatorElement("storeArticle");
        assertNotNull(entity);

        design = (ScriptedFormView) entity.richDesign;
        assertNotNull(design);

        if (hasDefault) {
            aGroup = design.getGroupObject("a");
            sGroup = design.getGroupObject("s");
        }
    }
}
