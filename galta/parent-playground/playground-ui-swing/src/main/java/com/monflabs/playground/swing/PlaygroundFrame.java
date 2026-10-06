/*
 * Copyright (c) 2019-2026 Philippe Riand
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.monflabs.playground.swing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeSelectionModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import org.fife.rsta.ac.LanguageSupport;
import org.fife.rsta.ac.LanguageSupportFactory;
import org.fife.rsta.ac.java.JavaLanguageSupport;
import org.fife.rsta.ac.java.buildpath.LibraryInfo;
import org.fife.ui.rsyntaxtextarea.FileTypeUtil;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.monflabs.playground.ExecutionContext;
import org.monflabs.playground.ExecutionController;
import org.monflabs.playground.ExecutionController.Run;
import org.monflabs.playground.ExecutionEngineFactory;
import org.monflabs.playground.ExecutionResult;
import org.monflabs.playground.PlaygroundConfiguration;
import org.monflabs.playground.PlaygroundLayout;
import org.monflabs.playground.PlaygroundLayout.WINDOW;
import org.monflabs.playground.Snippet;
import org.monflabs.playground.SnippetStorage;
import org.monflabs.playground.SnippetTree;
import org.monflabs.ui.swing.ide.components.TextAreaOutputStream;
import org.monflabs.ui.swing.ide.frame.IDEFrame;
import org.monflabs.ui.swing.ide.syntax.SyntaxTextArea;
import org.monflabs.ui.swing.settings.ComponentStateManager;
import org.monflabs.ui.swing.settings.UiPersistentSettings;
import org.monflabs.ui.swing.util.SwingUtil;
import org.monflabs.util.BaseException;
import org.monflabs.util.Console;
import org.monflabs.util.PathUtil;
import org.monflabs.util.StringFormat;
import org.monflabs.util.StringUtil;
import org.monflabs.util.UserPath;
import org.monflabs.util.config.Config;
import org.monflabs.util.datetime.PeriodFormatter;
import org.monflabs.util.path.FilesUtil;

import com.monflabs.swing.components.MarkdownRenderer;
import com.monflabs.swing.rtsyntax.LibraryInfo2;


/**
 * The playground window: the snippet tree, the snippet's files in editor
 * tabs, and the console and result tabs.
 * <p>
 * <b>Construction</b>: the constructor builds the UI through
 * {@link #createUi()}, which calls the overridable {@link #initToolbarLeft(JToolBar)}
 * and {@link #initToolbarRight(JToolBar)} - they run before the subclass
 * constructor, so they must not rely on the subclass's field initializers.
 * The first snippet is loaded when the frame becomes displayable (shown or
 * packed), once the subclass is fully constructed.
 * <p>
 * <b>Executions</b> are run by an {@link ExecutionController}: debounced,
 * one at a time, the current one stopped when another starts or another
 * snippet is selected, and the results and console output of a superseded
 * run dropped. {@link #processExecutionResult(ExecutionResult)} is called on
 * the event dispatch thread for the current run only.
 * <p>
 * <b>Settings</b>: when a {@link UiPersistentSettings} store is set, the
 * toolbar options, the window bounds, the main divider and the last selected
 * snippet are persisted under {@code playground/}.
 *
 * @see <a href="https://github.com/bobbylight/RSyntaxTextArea">RSyntaxTextArea</a>
 * @see <a href="https://github.com/JFormDesigner/FlatLaf">FlatLaf</a>
 */
@SuppressWarnings("serial")
public class PlaygroundFrame extends IDEFrame {

	/**
	 * Execution option: whether the value of each top-level expression is
	 * logged to the console (the "Log Expression Values" checkbox).
	 */
	public static final String OPTION_LOG_STATEMENTS = "LogStatements";

	/**
	 * The settings folder of the playground in {@link UiPersistentSettings}.
	 */
	public static final String SETTINGS_PATH = "playground";

	private final ExecutionController controller = new ExecutionController(
			ctx -> PlaygroundConfiguration.get().createExecutionEngine(ctx),
			new ExecutionListener());
	// The options of the last request, captured on the event dispatch thread
	// (see collectExecutionOptions()): used outside of an execution thread
	private volatile Map<String,Object> executionOptions = Map.of();

    private ExecutionContext executionContext;
    private boolean dirty;
    private boolean initialSnippetLoaded;

    // The text editors of the current snippet, by file name, and the ones
    // modified since their text was last copied to the execution context -
    // copied when an execution starts or the snippet is saved, not on every
    // keystroke. Event dispatch thread only.
    private final Map<String,SyntaxTextArea> editors = new LinkedHashMap<>();
    private final Set<String> modifiedEditors = new LinkedHashSet<>();

    // Scratchpad: a personal, always-editable buffer backed by a real file
    // under the user's home folder (not part of the snippet library), saved
    // automatically and unconditionally - no dirty prompt, no explicit Save
    // needed. Debounced 1s after the last edit, and flushed immediately when
    // switching away from it or closing the window (canClose()), so nothing
    // is lost even if the debounce hasn't fired yet.
    // One folder per kind of main file (playground-scratchpad/js, .../jshell...): the
    // playgrounds of different languages don't share their scratchpad
    private static final Path SCRATCHPAD_ROOT = UserPath.getMonflabsFolder().resolve("playground-scratchpad");
    private Snippet scratchpad;
    private boolean scratchpadActive;
    private final Timer scratchpadSaveTimer = new Timer(1000, e -> saveSnippet());

    private ComponentStateManager stateManager;

	private JTextArea edConsole;
	private JTree snippetTree;
	private JPanel treePanel;
	private JCheckBox ckAutoExec;
	private JCheckBox ckLogStatement;
	private JCheckBox ckPreserveConsole;
	private JCheckBox ckWordWrap;

    private JTabbedPane primaryTabPane;
    private JTabbedPane secondaryTabPane;
    private JTabbedPane resultTabPane;
    private JPanel panel;
    private JPanel panel_1;

    private JToolBar toolBar;
    private JPanel toolBarInfoPanel;
    private JLabel lbExecution;
    private JButton btnExecute;
    private JButton btnStop;
    private JSplitPane mainSplitPane;
    private JSplitPane codeSplitPane;

    public PlaygroundFrame() {
    	scratchpadSaveTimer.setRepeats(false);
    	// copies the edited text to the execution context right before a run
    	controller.setBeforeRun(() -> onEdt(this::commitEditors));
    	createUi();
    }

    /**
     * Builds the UI - called by the constructor. See the class documentation
     * about the overridable methods it calls.
     */
    public void createUi() {
        getContentPane().setLayout(new BorderLayout(0, 0));

        // Main split panel
        mainSplitPane = new JSplitPane();
        mainSplitPane.setResizeWeight(0.5);
        getContentPane().add(mainSplitPane);


        // Left side, Primary & secondary
        primaryTabPane = new JTabbedPane(JTabbedPane.TOP);
        codeSplitPane = new JSplitPane();
        codeSplitPane.setResizeWeight(0.5);
        codeSplitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);
        mainSplitPane.setLeftComponent(codeSplitPane);

        codeSplitPane.setLeftComponent(primaryTabPane);

        secondaryTabPane = new JTabbedPane(JTabbedPane.TOP);
        codeSplitPane.setRightComponent(secondaryTabPane);

        // Right side
        resultTabPane = new JTabbedPane();
       	mainSplitPane.setRightComponent(resultTabPane);

       	panel = new JPanel();
       	resultTabPane.addTab("Console", null, panel, null);
       	panel.setLayout(new BorderLayout(0, 0));
       	JScrollPane scrollPane = new JScrollPane();
       	panel.add(scrollPane, BorderLayout.CENTER);
       	scrollPane.setViewportBorder(null);
       	edConsole = new JTextArea();
       	edConsole.setEditable(false);
       	scrollPane.setViewportView(edConsole);

       	panel_1 = new JPanel();
       	panel.add(panel_1, BorderLayout.SOUTH);
       	panel_1.setLayout(new BorderLayout(0, 0));

       	lbExecution = new JLabel("...");
       	lbExecution.setHorizontalAlignment(SwingConstants.LEFT);
       	panel_1.add(lbExecution);

        treePanel = new JPanel();
        getContentPane().add(treePanel, BorderLayout.WEST);
        treePanel.setLayout(new BorderLayout(0, 0));

        JScrollPane scrollPane_2 = new JScrollPane();
        treePanel.add(scrollPane_2, BorderLayout.CENTER);

        snippetTree = new JTree(buildSnippetTree());
        snippetTree.setShowsRootHandles(true);
        snippetTree.setRootVisible(false);
        scrollPane_2.setViewportView(snippetTree);

        toolBar = new JToolBar();
        JPanel toolBarPanel = new JPanel(new BorderLayout(0, 0));
        toolBarPanel.add(toolBar, BorderLayout.CENTER);
        toolBarInfoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        toolBarPanel.add(toolBarInfoPanel, BorderLayout.SOUTH);
        getContentPane().add(toolBarPanel, BorderLayout.NORTH);

        initToolbarLeft(toolBar);
        toolBar.add(Box.createHorizontalGlue());
        initToolbarRight(toolBar);

        init();
    }

    protected void initToolbarLeft(JToolBar toolBar) {
        btnExecute = new JButton("Execute");
        btnExecute.setToolTipText("Execute the current snippet ("+shortcutText(KeyEvent.VK_ENTER)+")");
        toolBar.add(btnExecute);
        btnExecute.addActionListener(e -> executeNow());

        btnStop = new JButton("Stop");
        btnStop.setToolTipText("Stop the current execution ("+shortcutText(KeyEvent.VK_PERIOD)+")");
        btnStop.setEnabled(false);
        toolBar.add(btnStop);
        btnStop.addActionListener(e -> interupt());

        ckAutoExec = new JCheckBox("Auto Execute");
        toolBar.add(ckAutoExec);
        ckAutoExec.setSelected(true);
    }
    protected void initToolbarRight(JToolBar toolBar) {
        ckLogStatement = new JCheckBox("Log Expression Values");
        ckLogStatement.setSelected(true);
        ckLogStatement.addActionListener(e -> executeNow());
        getToolbar().add(ckLogStatement);

        JButton btnClearConsole = new JButton("Clear");
        btnClearConsole.setToolTipText("Clear the console");
        btnClearConsole.addActionListener(e -> TextAreaOutputStream.clear(edConsole));
        toolBar.add(btnClearConsole);

        ckWordWrap = new JCheckBox("Word Wrap");
        ckWordWrap.setSelected(true);
        ckWordWrap.addActionListener( (e) ->
        	edConsole.setLineWrap(ckWordWrap.isSelected())
        );
        toolBar.add(ckWordWrap);

        ckPreserveConsole = new JCheckBox("Preserve Console");
        toolBar.add(ckPreserveConsole);
    }

    public ExecutionContext getExecutionContext() {
    	return executionContext;
    }
    public JTextArea getConsoleTextArea() {
    	return edConsole;
    }
    public JTabbedPane getResultTabPane() {
    	return resultTabPane;
    }

    public JToolBar getToolbar() {
    	return toolBar;
    }

    /**
     * The controller running the executions.
     */
    public ExecutionController getExecutionController() {
    	return controller;
    }

    /**
     * A row below the toolbar, empty by default, for a subclass to show
     * status that does not belong on the button toolbar itself - a full
     * URL, for instance, next to a hyperlink-styled button.
     */
    public JPanel getToolbarInfoPanel() {
    	return toolBarInfoPanel;
    }

    /**
     * The persisted state of the controls ({@link #SETTINGS_PATH}), for a
     * subclass to add its own: without a {@link UiPersistentSettings} store,
     * the controls just get their default values.
     */
    public ComponentStateManager getComponentStateManager() {
    	return stateManager;
    }

    private void init() {
    	stateManager = new ComponentStateManager(SETTINGS_PATH);
    	stateManager.add(ckAutoExec, "autoExecute", true, null);
    	stateManager.add(ckLogStatement, "logStatements", true, null);
    	stateManager.add(ckWordWrap, "wordWrap", true, null);
    	stateManager.add(ckPreserveConsole, "preserveConsole", false, null);
    	edConsole.setLineWrap(ckWordWrap.isSelected());

    	initTitle();
    	initKeyboardShortcuts();

        treePanel.setPreferredSize(new Dimension(SwingUtil.scale(250), SwingUtil.scale(250)));
        // Every selection change goes through setSelectionPaths() or addSelectionPaths()
        // (setSelectionPath() and addSelectionPath() call them): guard these two only, so
        // the user is asked once per change
        snippetTree.setSelectionModel(new DefaultTreeSelectionModel() {
            @Override
			public void setSelectionPaths(TreePath[] paths){
                if (isSelectionChangeAllowed(paths!=null && paths.length>0 ? paths[0] : null)) {
                    super.setSelectionPaths(paths);
                }
            }
            @Override
			public void addSelectionPaths(TreePath[] paths){
                if (isSelectionChangeAllowed(paths!=null && paths.length>0 ? paths[0] : null)) {
                    super.addSelectionPaths(paths);
                }
            }
            private boolean isSelectionChangeAllowed(TreePath path) {
            	// Clearing the selection, or re-selecting the current node, loads nothing
            	if(path==null || path.equals(getSelectionPath())) {
            		return true;
            	}
            	return canClose();
            }
        });
        snippetTree.addTreeSelectionListener(e -> {
        	Object node = snippetTree.getLastSelectedPathComponent();
        	if(node instanceof ScratchpadTreeNode) {
        		loadScratchpad();
        	} else if(node instanceof SnippetTreeNode stn) {
        		loadSnippet(stn.treeNode);
        	}
        	if(node instanceof DefaultMutableTreeNode n) {
        		saveSetting(u -> u.put(settingPath("lastSnippet"), nodeKey(n)));
        	}
        });
    }

    /**
     * Loads the first snippet (the last selected one when it was persisted)
     * once the frame is displayable, and restores the persisted bounds.
     */
    @Override
    public void addNotify() {
    	if(!initialSnippetLoaded) {
    		restoreBounds();
    	}
    	super.addNotify();
    	if(!initialSnippetLoaded) {
    		initialSnippetLoaded = true;
    		selectInitialNode();
    	}
    }

    /**
     * Stops the executions, releases the editors and persists the window
     * state. The frame cannot be used anymore.
     */
    @Override
    public void dispose() {
    	saveWindowSettings();
    	scratchpadSaveTimer.stop();
    	controller.close();
    	disposeEditors();
    	if(stateManager!=null) {
    		stateManager.unregister();
    	}
    	super.dispose();
    }

    //
    // Keyboard
    //
    private static int menuShortcutMask() {
    	try {
    		return Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
    	} catch(UnsupportedOperationException e) { // HeadlessException included
    		return InputEvent.CTRL_DOWN_MASK;
    	}
    }

    private static String shortcutText(int keyCode) {
    	return InputEvent.getModifiersExText(menuShortcutMask())+"+"+KeyEvent.getKeyText(keyCode);
    }

    /**
     * Execute (Cmd/Ctrl+Enter), Save (Cmd/Ctrl+S, the scratchpad only: it is
     * saved automatically anyway) and Stop (Cmd/Ctrl+.), in the whole window.
     */
    protected void initKeyboardShortcuts() {
    	bindShortcut("playground.execute", KeyEvent.VK_ENTER, this::executeNow);
    	bindShortcut("playground.save", KeyEvent.VK_S, () -> {
    		if(scratchpadActive) {
    			saveSnippet();
    		}
    	});
    	bindShortcut("playground.stop", KeyEvent.VK_PERIOD, this::interupt);
    }

    protected void bindShortcut(String name, int keyCode, Runnable action) {
    	KeyStroke ks = KeyStroke.getKeyStroke(keyCode, menuShortcutMask());
    	getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ks, name);
    	getRootPane().getActionMap().put(name, new AbstractAction(name) {
    		@Override
    		public void actionPerformed(ActionEvent e) {
    			action.run();
    		}
    	});
    }

    //
    // Settings
    //
    private static String settingPath(String name) {
    	return SETTINGS_PATH+"/"+name;
    }

    private static Config settings() {
    	return UiPersistentSettings.isAvailable() ? UiPersistentSettings.get() : null;
    }

    private static void saveSetting(java.util.function.Consumer<Config.Updater> update) {
    	Config c = settings();
    	if(c!=null && !c.isReadOnly()) {
    		try {
    			c.updateValues(update);
    		} catch(RuntimeException e) {
    			Console.log(e);
    		}
    	}
    }

    private void restoreBounds() {
    	Config c = settings();
    	if(c==null || !c.has(settingPath("window/width"))) {
    		return;
    	}
    	try {
    		Rectangle r = new Rectangle(c.getInt(settingPath("window/x"),0), c.getInt(settingPath("window/y"),0),
    				c.getInt(settingPath("window/width"),0), c.getInt(settingPath("window/height"),0));
    		// only when it is still on a screen (a display may have gone)
    		Rectangle screen = getGraphicsConfiguration()!=null ? getGraphicsConfiguration().getBounds() : null;
    		if(r.width>100 && r.height>100 && (screen==null || screen.intersects(r))) {
    			setBounds(r);
    		}
    	} catch(RuntimeException e) {
    		Console.log(e);
    	}
    }

    private void saveWindowSettings() {
    	if(!isDisplayable()) {
    		return;
    	}
    	Rectangle r = getBounds();
    	int divider = mainSplitPane.getDividerLocation();
    	int width = mainSplitPane.getWidth();
    	saveSetting(u -> {
    		u.put(settingPath("window/x"), r.x);
    		u.put(settingPath("window/y"), r.y);
    		u.put(settingPath("window/width"), r.width);
    		u.put(settingPath("window/height"), r.height);
    		if(width>0 && divider>0 && divider<width) {
    			u.put(settingPath("mainDivider"), (double)divider/width);
    		}
    	});
    }

    private double mainDividerProportion() {
    	Config c = settings();
    	double d = c!=null ? c.getDouble(settingPath("mainDivider"), 0.5) : 0.5;
    	return d>0.1 && d<0.9 ? d : 0.5;
    }

    // A stable key of a tree node: its labels from the root
    private static String nodeKey(DefaultMutableTreeNode node) {
    	StringBuilder b = new StringBuilder();
    	for(TreeNode n: node.getPath()) {
    		if(n.getParent()==null) {
    			continue;	// the root
    		}
    		if(b.length()>0) {
    			b.append('/');
    		}
    		b.append(n.toString());
    	}
    	return b.toString();
    }

    @Override
	public boolean canClose() {
		if(scratchpadActive) {
			// Saved automatically and unconditionally - no prompt, no
			// discarding: just flush whatever the debounce timer hasn't yet.
			if(dirty) {
				scratchpadSaveTimer.stop();
				return saveSnippet();
			}
			return true;
		}
		if(dirty) {
			// The snippets of the library are never saved: leaving one discards its changes
		    int res = JOptionPane.showConfirmDialog(this,
		    		"The snippet has been modified. Leave it and discard the changes?",
		    		"Discard the Changes",
		    		JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
		    return res == JOptionPane.YES_OPTION;
		}
    	return true;
    }

    private void initTitle() {
    	String title = PlaygroundConfiguration.get().getFrameTitle();
		if(executionContext!=null) {
    		Snippet s = executionContext.getSnippet();
    		if(s!=null) {
    			title += " - " + (dirty?"*":"") + s.getFolder().toString();
    		}
		}
    	setTitle(title);
    }

    /**
     * Selects the snippet loaded first: the last selected one when it was
     * persisted and still exists, the first one otherwise.
     */
    protected void selectInitialNode() {
    	DefaultMutableTreeNode tree = (DefaultMutableTreeNode)snippetTree.getModel().getRoot();
    	if(tree.getChildCount()==0) {
    		return;
    	}
    	DefaultMutableTreeNode node = (DefaultMutableTreeNode)tree.getChildAt(0);
    	Config c = settings();
    	String last = c!=null ? c.getString(settingPath("lastSnippet"), null) : null;
    	if(last!=null) {
    		var e = tree.depthFirstEnumeration();
    		while(e.hasMoreElements()) {
    			if(e.nextElement() instanceof DefaultMutableTreeNode n && last.equals(nodeKey(n)) && n!=tree) {
    				node = n;
    				break;
    			}
    		}
    	}
    	TreePath path = new TreePath(node.getPath());
    	snippetTree.getSelectionModel().setSelectionPath(path);
    	snippetTree.scrollPathToVisible(path);
    }

    private void loadSnippet(SnippetTree.Node node) {
    	leavingScratchpadIfNeeded();
    	scratchpadActive = false;
    	loadSnippetContent(node.getSnippet(), node.getChildren().length==0);
    }

    private void loadScratchpad() {
    	leavingScratchpadIfNeeded();
    	ensureScratchpadFile();
    	scratchpadActive = true;
    	loadSnippetContent(scratchpad, true);
    }

    // Flushes a pending edit before switching away from the scratchpad (to
    // a real snippet, or closing the window) - the debounce timer alone
    // isn't enough, since it may not have fired yet.
    private void leavingScratchpadIfNeeded() {
    	if(scratchpadActive) {
    		scratchpadSaveTimer.stop();
    		if(dirty) {
    			saveSnippet();
    		}
    	}
    }

    /**
     * The name of the scratchpad's main file: the engine's preferred main
     * file name ({@link ExecutionEngineFactory#getMainFileName()}).
     */
    protected String getScratchpadFileName() {
    	ExecutionEngineFactory f = PlaygroundConfiguration.get().getExecutionEngineFactory();
    	String name = f!=null ? f.getMainFileName() : null;
    	return name!=null ? name : "main.txt";
    }

    /**
     * The folder of the scratchpad: one per kind of main file, under the
     * Monflabs folder, so the playgrounds of different languages don't mix
     * their files.
     */
    protected Path getScratchpadFolder(String fileName) {
    	String ext = PathUtil.POSIX.getFileExtension(fileName);
    	return SCRATCHPAD_ROOT.resolve(StringUtil.isNotEmpty(ext) ? ext : "default");
    }

    /**
     * The content of a new scratchpad file: a line comment for the usual
     * C-like languages (and a "Hello, world!" for Java and JShell), nothing
     * otherwise.
     */
    protected String getScratchpadDefaultContent(String fileName) {
    	String ext = PathUtil.POSIX.getFileExtension(fileName);
    	if(ext.equals("java")) {
    		String className = PathUtil.POSIX.removeExtension(PathUtil.POSIX.getFileName(fileName));
    		return "// Scratchpad - saved automatically, not part of the snippet library\n"
    			+ "public class "+className+" {\n\n"
    			+ "\tpublic static void main(String[] args) {\n"
    			+ "\t\tSystem.out.println(\"Hello, world!\");\n"
    			+ "\t}\n"
    			+ "}\n";
    	}
    	if(ext.equals("jshell")) {
    		return "// Scratchpad - saved automatically, not part of the snippet library\n"
    			+ "System.out.println(\"Hello, world!\");\n";
    	}
    	return switch(ext) {
    		case "js", "mjs", "ts", "c", "cpp", "cs", "go", "kt", "scala", "swift" ->
    			"// Scratchpad - saved automatically, not part of the snippet library\n";
    		case "py", "sh", "rb" ->
    			"# Scratchpad - saved automatically, not part of the snippet library\n";
    		default -> "";
    	};
    }

    private void ensureScratchpadFile() {
    	if(scratchpad==null) {
    		String name = getScratchpadFileName();
    		Path folder = getScratchpadFolder(name);
    		try {
    			Files.createDirectories(folder);
    			Path main = folder.resolve(name);
    			if(!Files.exists(main)) {
    				// The scratchpad of an earlier version, shared by all the playgrounds
    				Path previous = SCRATCHPAD_ROOT.resolve(name);
    				if(Files.isRegularFile(previous)) {
    					Files.copy(previous, main);
    				} else {
    					Files.writeString(main, getScratchpadDefaultContent(name), StandardCharsets.UTF_8);
    				}
    			}
    		} catch(IOException ex) {
    			Console.log(ex);
    		}
    		scratchpad = new Snippet(folder);
    	}
    }

    private void loadSnippetContent(Snippet s, boolean leafNode) {
    	// Whatever runs belongs to the previous snippet
    	controller.cancel();
    	try {
	        executionContext = new PlaygroundExecutionContext(this, s) {
	        	@Override
				public boolean isLogStatements() {
	        		return Boolean.TRUE.equals(getExecutionOption(OPTION_LOG_STATEMENTS, Boolean.FALSE));
	        	}
	        };

	    	PlaygroundLayout layout = PlaygroundConfiguration.get().getLayout();

	    	List<String> main = new ArrayList<>();
	    	List<String> secondary = new ArrayList<>();

	    	if(leafNode) {
				try (Stream<Path> stream = Files.list(FilesUtil.getRoot((executionContext.getSnippetFs())))) {
				    stream
				    	.filter(Files::isRegularFile)
				    	.forEach( (p) -> {
				    		String name = p.getFileName().toString();
				    		WINDOW w = layout.getWindow(name);
				    		switch(w) {
				    			case MAIN -> 		main.add(name);
				    			case SECONDARY -> 	secondary.add(name);
				    			case DOC -> 		secondary.add(name);
				    			default ->			main.add(name);
				    		}
				    	});
				}
		    	layout.sortTabs(WINDOW.MAIN, main);
		    	layout.sortTabs(WINDOW.SECONDARY, secondary);
	    	} else {
	    		String readme = Snippet.FILEPATH_DOCUMENTATION;
	    		if(executionContext.getBytes(readme)==null) {
	    			executionContext.setContent(readme, "## "+s.getFolder().getFileName().toString());
	    		}
	    		main.add(readme);
	    	}

	    	// fill the tabs
	    	disposeEditors();
	    	fillTabbedPane(primaryTabPane, main);
	   		fillTabbedPane(secondaryTabPane, secondary);

	   		if(!secondary.isEmpty()) {
	   			codeSplitPane.setDividerLocation(0.5);
	   		} else {
	   			codeSplitPane.setDividerLocation(1.0);
	   		}

	    	dirty = false;
	    	initTitle();

	    	btnExecute.setEnabled(PlaygroundConfiguration.get().getExecutionEngineFactory().isExecutable(executionContext.getSnippet()));
	    	if(leafNode) {
	        	if(ckAutoExec.isSelected()) {
	        		executeNow();
	        	}
	   			mainSplitPane.setDividerLocation(mainDividerProportion());
	        } else {
	   			mainSplitPane.setDividerLocation(1.0);
	    	}
	    	focusFirstEditor();
    	} catch(IOException | RuntimeException ex) {
    		// Never leave the frame half loaded: an empty, clean state and the error in the console
    		Console.log(ex);
    		disposeEditors();
    		fillTabbedPane(primaryTabPane, List.of());
    		fillTabbedPane(secondaryTabPane, List.of());
    		dirty = false;
    		initTitle();
    		btnExecute.setEnabled(false);
    		edConsole.append(StringFormat.format("Cannot load the snippet {0}:\n{1}\n", s.getFolder(), BaseException.getStackAsString(ex)));
    	}
    }

    private void focusFirstEditor() {
    	Component c = primaryTabPane.getTabCount()>0 ? primaryTabPane.getComponentAt(0) : null;
    	SyntaxTextArea ta = c!=null ? findEditor(c) : null;
    	if(ta!=null) {
    		SwingUtilities.invokeLater(ta::requestFocusInWindow);
    	}
    }

    private static SyntaxTextArea findEditor(Component c) {
    	if(c instanceof SyntaxTextArea ta) {
    		return ta;
    	}
    	if(c instanceof java.awt.Container container) {
    		return SwingUtil.findFirstChildOfType(container, SyntaxTextArea.class);
    	}
    	return null;
    }

    /**
     * Releases the editors of the current snippet: unregistered from the
     * RSyntaxTextArea language supports (which otherwise keep every text area
     * ever registered).
     */
    private void disposeEditors() {
    	for(SyntaxTextArea ta: editors.values()) {
    		try {
    			LanguageSupportFactory.get().unregister(ta);
    		} catch(RuntimeException e) {
    			Console.log(e);
    		}
    	}
    	editors.clear();
    	modifiedEditors.clear();
    }

    protected void fillTabbedPane(JTabbedPane tabPane, List<String> files) {
    	tabPane.removeAll();
		tabPane.setVisible(!files.isEmpty());
    	for(String f: files) {
    		JComponent c;
    		try {
    			c = createTabbedPane(f);
    		} catch(RuntimeException e) {
    			// one bad file does not prevent the others from opening
    			Console.log(e);
    			c = createPlaceholder(StringFormat.format("{0} cannot be opened: {1}", f, e.getMessage()));
    		}
    		if(c!=null) {
    			tabPane.addTab(f, null, c, null);
    		}
    	}
    }

    protected JComponent createTabbedPane(String name) {
    	if(!executionContext.isTextFile(name)) {
    		byte[] b = executionContext.getBytes(name);
    		return createPlaceholder(StringFormat.format("Binary file, {0} bytes - not editable", b!=null ? b.length : 0));
    	}
    	// Should we better use MIME types?
    	String ext = PathUtil.POSIX.getFileExtension(name);
    	if(ext.equals("md")) {
        	return createMarkdownRenderer(name);
    	}
    	return createSyntaxArea(name);
    }

    /**
     * A non-editable tab showing a message (a binary file, an error).
     */
    protected JComponent createPlaceholder(String message) {
    	JLabel label = new JLabel(message, SwingConstants.CENTER);
    	label.setEnabled(false);
    	return label;
    }

    protected JComponent createMarkdownRenderer(String name) {
        MarkdownRenderer md = new MarkdownRenderer();
        Path physical = executionContext.getSnippetFs().getPhysicalFile(executionContext.getSnippetFs().getPath(name));
        md.setBaseFolder(physical!=null ? physical.getParent() : executionContext.getSnippet().getFolder());
		md.setMarkdown(executionContext.getContent(name));
		return md;
    }

    protected JComponent createSyntaxArea(String name) {
        SyntaxTextArea textArea = new SyntaxTextArea();
        RTextScrollPane scrollPane = new RTextScrollPane(textArea);
        scrollPane.setViewportBorder(null);
        scrollPane.setLineNumbersEnabled(true);

        setInitialText(textArea, executionContext.getContent(name));

        textArea.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void removeUpdate(DocumentEvent e) {
				textChanged(name);
			}
			@Override
			public void insertUpdate(DocumentEvent e) {
				textChanged(name);
			}
			@Override
			public void changedUpdate(DocumentEvent e) {
				// attribute changes only: the text is the same
			}
		});

        initSyntaxTextArea(textArea, name);
        editors.put(name, textArea);

        edConsole.setFont(textArea.getFont());

        return scrollPane;
    }

    /**
     * Sets the text of an editor as loaded content: not undoable (undo must
     * not empty the editor), caret at the start.
     */
    public static void setInitialText(SyntaxTextArea textArea, String text) {
		textArea.setText(text);
		textArea.discardAllEdits();
		textArea.setCaretPosition(0);
    }

    private void textChanged(String name) {
		if(!dirty) {
			dirty = true;
			initTitle();
		}
		// The text is copied to the execution context when it is needed
		// (execution, save), not on every keystroke
		modifiedEditors.add(name);
		autoExecute();
		if(scratchpadActive) {
			scratchpadSaveTimer.restart();
		}
    }

    /**
     * Copies the text of the modified editors to the execution context - event
     * dispatch thread only. Called before an execution starts and before a
     * save; a subclass reading the context's files outside of an execution
     * (e.g. to start a debugger) calls it first.
     */
    public void commitEditors() {
    	if(executionContext==null) {
    		return;
    	}
    	for(String name: modifiedEditors) {
    		SyntaxTextArea ta = editors.get(name);
    		if(ta!=null) {
    			executionContext.setContent(name, ta.getText());
    		}
    	}
    	modifiedEditors.clear();
    }


    protected void initSyntaxTextArea(SyntaxTextArea textArea, String name) {
    	if(name.endsWith(".js")) {
    		// Highlighting only: the RSTA JavaScript language support parses
    		// with Rhino, which flags the engine's extensions as errors
            textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
    	} else if(name.endsWith(".json")) {
            textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JSON);
    	} else if(name.endsWith(".java")) {
   			initJavaLanguage();
   	    	LanguageSupportFactory.get().register(textArea);
            textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
    	} else if(name.endsWith(".jshell")) {
   			initJavaLanguage();
   	    	LanguageSupportFactory.get().register(textArea);
            textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
    	} else if(name.endsWith(".xml")) {
            textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_XML);
    	} else if(name.endsWith(".html")) {
            textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_HTML);
    	} else if(name.endsWith(".css")) {
            textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_CSS);
    	} else if(name.endsWith(".yaml")) {
            textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_YAML);
    	} else {
	    	File file = new File(name);
	        textArea.setSyntaxEditingStyle(FileTypeUtil.get().guessContentType(file));
    	}
    }

    private boolean javaInitialized;

    protected void initJavaLanguage() {
		if(javaInitialized) {
			return;
		}
		javaInitialized = true;

		LanguageSupportFactory lsf = LanguageSupportFactory.get();
		LanguageSupport support = lsf.getSupportFor(SyntaxConstants.SYNTAX_STYLE_JAVA);
		JavaLanguageSupport jls = (JavaLanguageSupport)support;
		// Indexing the JDK classes reads every entry of every module: done on
		// a background thread, then registered on the event dispatch thread
		// (the jar manager is not thread-safe), where it is now cheap.
		Thread.ofVirtual().name("playground-java-completion").start(() -> {
			try {
				LibraryInfo info = LibraryInfo2.getMainJreJarInfo();
				if(info!=null) {
					info.createPackageMap();
					SwingUtilities.invokeLater(() -> {
						try {
							jls.getJarManager().addClassFileSource(info);
						} catch (IOException | RuntimeException e) {
							Console.log(e);
						}
					});
				}
			} catch (IOException | RuntimeException e) {
				// Code completion is a nicety: never fail the editor because of it
				Console.log(e);
			}
		});
    }


    //
    // Save the scratchpad
    //
    /**
     * Writes the scratchpad's modified text files back to its folder (see
     * {@link SnippetStorage}): atomically, keeping CRLF line breaks, skipping
     * the binary files. A file modified outside of the playground since it was
     * loaded is only overwritten once the user confirms. A failure is reported
     * to the user and leaves the snippet dirty.
     *
     * @return true when the snippet was saved (or there was nothing to save)
     */
    private boolean saveSnippet() {
    	// Only the scratchpad is saved: the snippets of the library are read-only
    	if(executionContext==null || !scratchpadActive) {
    		return true;
    	}
    	commitEditors();
    	Path f = executionContext.getSnippet().getFolder();
    	SnippetStorage storage = new SnippetStorage(executionContext);
    	try {
    		SnippetStorage.SaveResult r = storage.save(false);
    		if(r.hasConflicts()) {
    			StringBuilder files = new StringBuilder();
    			for(Path p: r.getConflicts()) {
    				files.append("\n  ").append(p.getFileName());
    			}
    			int res = JOptionPane.showConfirmDialog(this,
    					StringFormat.format("These files were modified outside of the playground since they were loaded:{0}\n\nOverwrite them?", files),
    					"Save Snippet", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
    			if(res!=JOptionPane.YES_OPTION) {
    				return false;
    			}
    			storage.save(true);
    		}
    	} catch(IOException | RuntimeException ex) {
    		Console.log(ex);
    		JOptionPane.showMessageDialog(this,
    				StringFormat.format("The snippet {0} cannot be saved:\n{1}", f, ex.getMessage()),
    				"Save Snippet", JOptionPane.ERROR_MESSAGE);
    		return false;
    	}
    	if(dirty) {
    		dirty = false;
    		initTitle();
    	}
    	return true;
    }


    //
    // Execution engine
    //

    public void autoExecute() {
    	if(ckAutoExec.isSelected()) {
    		executeAsync(500);
    	}
    }

	/**
	 * The value of an execution option: the options of the run executing on
	 * the calling thread, else of the last request. Safe to call from any
	 * thread.
	 *
	 * @see #collectExecutionOptions(Map)
	 */
	public Object getExecutionOption(String key, Object defaultValue) {
		Run run = ExecutionController.currentThreadRun();
		Map<String,Object> options = run!=null ? run.getOptions() : executionOptions;
		return options.getOrDefault(key, defaultValue);
	}

	/**
	 * A typed execution option (see {@link #getExecutionOption(String, Object)}):
	 * the default value when it is not set or has another type.
	 */
	public <T> T getExecutionOption(String key, Class<T> type, T defaultValue) {
		Object v = getExecutionOption(key, defaultValue);
		return type.isInstance(v) ? type.cast(v) : defaultValue;
	}

	/**
	 * Captures the execution options from the UI: called on the event dispatch
	 * thread when an execution is requested, so the execution thread never
	 * reads a Swing component. Subclasses add their own options.
	 */
	protected void collectExecutionOptions(Map<String,Object> options) {
		options.put(OPTION_LOG_STATEMENTS, ckLogStatement.isSelected());
	}

    /**
     * Called on the event dispatch thread with the result of the current
     * execution (null when it failed). To be overridden.
     */
    protected void processExecutionResult(ExecutionResult r) {
    }

    /**
     * Stops the current execution (the Stop button).
     */
    protected void interrupt() {
    	controller.stop();
    }

    /**
     * @deprecated misspelled: use {@link #interrupt()}. Still called by the
     * Stop button and its shortcut, so an existing override keeps working.
     */
    @Deprecated
    protected void interupt() {
    	interrupt();
    }

    public void executeNow() {
    	executeAsync(0);
    }

	/**
	 * Requests an execution of the current snippet after the delay (in
	 * milliseconds): a newer request made before it starts replaces it, and
	 * the execution in progress is stopped when it starts.
	 */
	public void executeAsync(int delay) {
    	if(executionContext==null || !PlaygroundConfiguration.get().getExecutionEngineFactory().isExecutable(executionContext.getSnippet())) {
    		return;
    	}
    	final Map<String,Object> options = new HashMap<>();
    	onEdt(() -> collectExecutionOptions(options));
    	executionOptions = Map.copyOf(options);
    	controller.request(executionContext, options, delay);
    }

	/**
	 * The controller's events, moved to the event dispatch thread.
	 */
	private final class ExecutionListener implements ExecutionController.Listener {
		@Override
		public void executionStarted(Run run) {
			// Waits: the console is cleared before the run prints anything
			onEdt( () -> {
				if(!run.isCurrent()) {
					return;
				}
				btnStop.setEnabled(true);
				lbExecution.setText("Executing...");
				if(!ckPreserveConsole.isSelected()) {
					edConsole.setText("");
				}
				if(run.getContext() instanceof PlaygroundExecutionContext pc) {
					String text = edConsole.getText();
					int lines = edConsole.getLineCount();
					// the run starts on the last line when it is empty
					pc.setConsoleBaseLine(text.isEmpty() || text.endsWith("\n") ? lines-1 : lines);
				}
			});
		}
		@Override
		public void executionFinished(Run run, ExecutionResult result, Throwable failure) {
			if(failure!=null) {
				reportException(run, failure);
			} else if(result!=null && result.getException()!=null) {
				reportException(run, result.getException());
			}
			long end = System.currentTimeMillis();
			String text = StringFormat.format("Execution time: {0}", PeriodFormatter.formatPeriod(end-run.getStartTime()));
			onEdtLater( () -> {
				if(!run.isCurrent() && !run.isAbandoned()) {
					return;	// superseded meanwhile: the newer run owns the UI
				}
				lbExecution.setText(text);
				btnStop.setEnabled(false);
				if(run.isCurrent()) {
					processExecutionResult(failure==null ? result : null);
				}
			});
		}
	}

	private void reportException(Run run, Throwable t) {
		if(isEngineKill(t)) {
			return;
		}
		PrintStream out = null;
		if(run.getContext() instanceof PlaygroundExecutionContext pc) {
			// an abandoned run is not current anymore, but its abandon is
			// still worth a line
			out = run.isAbandoned() ? pc.getRawConsole() : run.gate(pc.getRawConsole());
		}
		if(out==null) {
			Console.log(t);
			return;
		}
		if(isInterrupt(t)) {
			out.println("Execution stopped");
			out.flush();
			return;
		}
		boolean parser = t.getClass().getName().contains("ParseException");
		if(parser) {
			String msg = t.getLocalizedMessage();
			out.print(msg);
			if(msg==null || !msg.endsWith("\n")) {
				out.println();
			}
		} else {
			if(run.isAbandoned()) {
				out.println(t.getMessage());
			} else {
				Console.log(t);
				t.printStackTrace(out);
			}
		}
		out.flush();
	}

	private static boolean isInterrupt(Throwable t) {
		while(t!=null) {
			if(t instanceof InterruptedException || t.getClass().getName().endsWith("InterruptException")) {
				return true;
			}
			t = t.getCause()!=t ? t.getCause() : null;
		}
		return false;
	}

	/**
	 * Run the code on the event dispatch thread and wait for it.
	 */
	protected static void onEdt(Runnable r) {
		if(SwingUtilities.isEventDispatchThread()) {
			r.run();
			return;
		}
		try {
			SwingUtilities.invokeAndWait(r);
		} catch(InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch(InvocationTargetException e) {
			Console.log(e.getCause());
		}
	}

	/**
	 * Run the code on the event dispatch thread, without waiting.
	 */
	protected static void onEdtLater(Runnable r) {
		if(SwingUtilities.isEventDispatchThread()) {
			r.run();
		} else {
			SwingUtilities.invokeLater(r);
		}
	}

	/**
	 * Whether the failure is an engine reporting it was killed (GraalVM wraps
	 * its own kill as a message mentioning ThreadDeath) - not worth a stack
	 * trace. Thread.stop() no longer exists on Java 21, so a real ThreadDeath
	 * cannot be thrown anymore.
	 */
	private static boolean isEngineKill(Throwable t) {
    	while(t!=null) {
    		String m = t.getMessage();
    		if(m!=null && m.contains("ThreadDeath")) {
    			return true;
    		}
    		t = BaseException.getCause(t);
    	}
    	return false;
    }


    //
    // Tree nodes
    //
    private static class SnippetTreeNode extends DefaultMutableTreeNode {
    	SnippetTree.Node treeNode;
    	SnippetTreeNode(SnippetTree.Node treeNode) {
    		this(treeNode.getFolder().getFileName().toString(),treeNode);
    	}
    	SnippetTreeNode(String label, SnippetTree.Node treeNode) {
    		super(label);
    		this.treeNode = treeNode;
    	}
    }

    // A synthetic leaf, always the tree's first node - not backed by a
    // SnippetTree.Node (there's no on-disk library entry for it).
    private static class ScratchpadTreeNode extends DefaultMutableTreeNode {
    	ScratchpadTreeNode() {
    		super("Scratchpad");
    	}
    }

    public DefaultMutableTreeNode buildSnippetTree() {
    	if(PlaygroundConfiguration.get().getSnippetFactory()==null) { // design time
    		return new DefaultMutableTreeNode("Snippets");
    	}
    	SnippetTree tree = new SnippetTree(PlaygroundConfiguration.get().getSnippetFactory());

    	SnippetTreeNode root = new SnippetTreeNode("Root",tree.getRoot());
    	buildSnippetFolder(root);
    	root.insert(new ScratchpadTreeNode(), 0);
    	return root;
    }
    private void buildSnippetFolder(SnippetTreeNode node) {
    	SnippetTree.Node[] children = node.treeNode.getChildren();
    	if(children!=null) {
    		for(int i=0; i<children.length; i++) {
    			SnippetTreeNode treeNode = new SnippetTreeNode(children[i]);
    			node.add(treeNode);
    			buildSnippetFolder(treeNode);
    		}
    	}
    }
}
