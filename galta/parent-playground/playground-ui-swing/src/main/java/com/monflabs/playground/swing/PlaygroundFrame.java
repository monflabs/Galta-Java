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
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

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
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeSelectionModel;
import javax.swing.tree.TreePath;

import org.fife.rsta.ac.LanguageSupport;
import org.fife.rsta.ac.LanguageSupportFactory;
import org.fife.rsta.ac.java.JavaLanguageSupport;
import org.fife.ui.rsyntaxtextarea.FileTypeUtil;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.monflabs.playground.ExecutionContext;
import org.monflabs.playground.ExecutionEngine;
import org.monflabs.playground.ExecutionResult;
import org.monflabs.playground.MemoryFileSystemSnippet;
import org.monflabs.playground.PlaygroundConfiguration;
import org.monflabs.playground.PlaygroundLayout;
import org.monflabs.playground.PlaygroundLayout.WINDOW;
import org.monflabs.playground.Snippet;
import org.monflabs.playground.SnippetTree;
import org.monflabs.ui.swing.ide.components.TextAreaOutputStream;
import org.monflabs.ui.swing.ide.frame.IDEFrame;
import org.monflabs.ui.swing.ide.syntax.SyntaxTextArea;
import org.monflabs.util.BaseException;
import org.monflabs.util.Console;
import org.monflabs.util.PathUtil;
import org.monflabs.util.StringFormat;
import org.monflabs.util.StringUtil;
import org.monflabs.util.UserPath;
import org.monflabs.util.datetime.PeriodFormatter;
import org.monflabs.util.path.FilesUtil;

import com.monflabs.swing.components.MarkdownRenderer;
import com.monflabs.swing.rtsyntax.LibraryInfo2;


/**
 * https://github.com/bobbylight/RSyntaxTextArea
 * https://github.com/JFormDesigner/FlatLaf
 */
@SuppressWarnings("serial")
public class PlaygroundFrame extends IDEFrame {
	
	private ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
	private volatile Runnable stopScript;
	private final AtomicLong executionRequest = new AtomicLong();
	
    
    private ExecutionContext executionContext;
    private boolean dirty;

    // Scratchpad: a personal, always-editable buffer backed by a real file
    // under the user's home folder (not part of the snippet library), saved
    // automatically and unconditionally - no dirty prompt, no explicit Save
    // needed. Debounced 1s after the last edit, and flushed immediately when
    // switching away from it or closing the window (canClose()), so nothing
    // is lost even if the debounce hasn't fired yet.
    private static final Path SCRATCHPAD_FOLDER = UserPath.getMonflabsFolder().resolve("playground-scratchpad");
    private static final String SCRATCHPAD_DEFAULT_CONTENT = "// Scratchpad - saved automatically, not part of the snippet library\n";
    private Snippet scratchpad;
    private boolean scratchpadActive;
    private final Timer scratchpadSaveTimer = new Timer(1000, e -> saveSnippet());

	private JTextArea edConsole;
	private JTree snippetTree;
	private JPanel treePanel;
	private JCheckBox ckAutoExec;
	private JCheckBox ckLogStatement;
	private JCheckBox ckPreverveConsole;
	private JCheckBox ckWordWrap;

    private JTabbedPane primaryTabPane;
    private JTabbedPane secondaryTabPane;
    private JTabbedPane resultTabPane;
    private JPanel panel;
    private JPanel panel_1;
    
    private JToolBar toolBar;
    private JPanel toolBarInfoPanel;
    private JLabel lbExecution;
    private JButton btSaveSnippet;
    private JButton btnExecute;
    private JButton btnStop;
    private JSplitPane mainSplitPane;
    private JSplitPane codeSplitPane;

    public PlaygroundFrame() {
    	scratchpadSaveTimer.setRepeats(false);
    	createUi();
    }
    
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
        btSaveSnippet = new JButton("Save Snippet");
        btSaveSnippet.addActionListener(new ActionListener() {
        	@Override
			public void actionPerformed(ActionEvent e) {
        		saveSnippet();
        	}
        });
        toolBar.add(btSaveSnippet);
        
        btnExecute = new JButton("Execute");
        btnExecute.setToolTipText("Execute the current snippet");
        toolBar.add(btnExecute);
        btnExecute.addActionListener(new ActionListener() {
        	@Override
			public void actionPerformed(ActionEvent e) {
       			executeNow();
        	}
        });
        
        btnStop = new JButton("Stop");
        btnStop.setToolTipText("Stop the current execution");
        toolBar.add(btnStop);
        btnStop.addActionListener(new ActionListener() {
        	@Override
			public void actionPerformed(ActionEvent e) {
       			interupt();
        	}
        });
        
        ckAutoExec = new JCheckBox("Auto Execute");
        toolBar.add(ckAutoExec);
        ckAutoExec.setSelected(true);
    }
    protected void initToolbarRight(JToolBar toolBar) {
        ckLogStatement = new JCheckBox("Log Expression Values");
        ckLogStatement.setSelected(true);
        //ckLogStatement.setSelected(true);
        ckLogStatement.addActionListener(new ActionListener() {
        	@Override
			public void actionPerformed(ActionEvent e) {
       			executeNow();
        	}
        });
        getToolbar().add(ckLogStatement);
        
        JButton btnClearConsole = new JButton("Clear");
        btnClearConsole.setToolTipText("Clear the console");
        btnClearConsole.addActionListener(new ActionListener() {
        	@Override
			public void actionPerformed(ActionEvent e) {
        		TextAreaOutputStream.clear(edConsole);
        	}
        });
        toolBar.add(btnClearConsole);

        ckWordWrap = new JCheckBox("Word Wrap");
        ckWordWrap.setSelected(true);
        ckWordWrap.addActionListener( (e) -> 
        	edConsole.setLineWrap(ckWordWrap.isSelected()) 
        );
        toolBar.add(ckWordWrap);
        
        ckPreverveConsole = new JCheckBox("Preserve Console");
        toolBar.add(ckPreverveConsole);    	
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
     * A row below the toolbar, empty by default, for a subclass to show
     * status that does not belong on the button toolbar itself - a full
     * URL, for instance, next to a hyperlink-styled button.
     */
    public JPanel getToolbarInfoPanel() {
    	return toolBarInfoPanel;
    }

    private void init() {
    	btSaveSnippet.setVisible(PlaygroundConfiguration.get().isEditable());
        
    	edConsole.setLineWrap(ckWordWrap.isSelected()); 
    	
    	AtomicBoolean eventsEnabled = new AtomicBoolean(true);
    	
    	initTitle();
        
        treePanel.setPreferredSize(new Dimension(250, 250));
        // Keyboard navigation goes through setSelectionPaths(), not setSelectionPath():
        // guard every way the selection can change
        snippetTree.setSelectionModel(new DefaultTreeSelectionModel() {
            @Override
			public void setSelectionPath(TreePath path){
                if (isSelectionChangeAllowed(path)) {
                    super.setSelectionPath(path);
                }
            }
            @Override
			public void setSelectionPaths(TreePath[] paths){
                if (isSelectionChangeAllowed(paths!=null && paths.length>0 ? paths[0] : null)) {
                    super.setSelectionPaths(paths);
                }
            }
            @Override
			public void addSelectionPath(TreePath path){
                if (isSelectionChangeAllowed(path)) {
                    super.addSelectionPath(path);
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
        snippetTree.addTreeSelectionListener(new TreeSelectionListener() {
            @Override
			public void valueChanged(TreeSelectionEvent e) {
            	eventsEnabled.set(false);
            	try {
	            	Object node = snippetTree.getLastSelectedPathComponent();
	            	if(node instanceof ScratchpadTreeNode) {
	            		loadScratchpad();
	            	} else if(node instanceof SnippetTreeNode stn) {
	            		loadSnippet(stn.treeNode);
	            	}
            	} finally {
                	eventsEnabled.set(true);
            	}
            }
        });
        
        // Select the first snippet
        selectInitialNode();
    }

    @Override
	public boolean canClose() {
		if(scratchpadActive) {
			// Saved automatically and unconditionally - no prompt, no
			// discarding: just flush whatever the debounce timer hasn't yet.
			if(dirty) {
				scratchpadSaveTimer.stop();
				saveSnippet();
			}
			return true;
		}
		if(dirty) {
			if(PlaygroundConfiguration.get().isEditable()) {
    			// Ask for save?
    		    int res = JOptionPane.showConfirmDialog(this, 
    		    		 	"The snippet has been modified, do you want to save it?",
    		    		 	"Save Confirmation",
    		    		 	JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
	    	    if(res == JOptionPane.YES_OPTION) {
	    	    	saveSnippet();
	    	    	return true;
	    	    } else if (res == JOptionPane.NO_OPTION){
	    	    	return true;
	    	    } else {
	    	    	return false;
	    	    }
	    	} else {
    			// Ask for save?
    		    int res = JOptionPane.showConfirmDialog(this, 
    		    		 	"The snippet has been modified, do you want to discard the changes?",
    		    		 	"Discard Confirmation",
    		    		 	JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
	    	    if(res == JOptionPane.YES_OPTION) {
	    	    	return true;
	    	    } else {
	    	    	return false;
	    	    }
	    		
	    	}
		}
    	return true;
    }
    
    private void initTitle() {
    	String title = PlaygroundConfiguration.get().getFrameTitle();
    	if(PlaygroundConfiguration.get().isEditable()) {
    		title = "[Editable] "+ title;
    	}
		if(executionContext!=null) {
    		Snippet s = executionContext.getSnippet();
    		if(s!=null) {
    			title += " - " + (dirty?"*":"") + s.getFolder().toString();
    		}
		}
    	setTitle(title);
    }
    
    protected void selectInitialNode() {
    	DefaultMutableTreeNode tree = (DefaultMutableTreeNode)snippetTree.getModel().getRoot();
    	int count = tree.getChildCount();
    	if(count>0) {
    		DefaultMutableTreeNode node = (DefaultMutableTreeNode)tree.getChildAt(0);
    		snippetTree.getSelectionModel().setSelectionPath(new TreePath(node.getPath()));
    	}
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
    		saveSnippet();
    	}
    }

    private void ensureScratchpadFile() {
    	if(scratchpad==null) {
    		try {
    			Files.createDirectories(SCRATCHPAD_FOLDER);
    			Path main = SCRATCHPAD_FOLDER.resolve("main.js");
    			if(!Files.exists(main)) {
    				Files.writeString(main, SCRATCHPAD_DEFAULT_CONTENT, StandardCharsets.UTF_8);
    			}
    		} catch(IOException ex) {
    			Console.log(ex);
    		}
    		scratchpad = new Snippet(SCRATCHPAD_FOLDER);
    	}
    }

    private void loadSnippetContent(Snippet s, boolean leafNode) {
        executionContext = new PlaygroundExecutionContext(this, s) {
        	@Override
			public boolean isLogStatements() {
        		return ckLogStatement.isSelected();
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
			} catch(IOException ex) {
				Console.log(ex);
			}
	    	layout.sortTabs(WINDOW.MAIN, main);
	    	layout.sortTabs(WINDOW.SECONDARY, secondary);
    	} else {
    		Path file = executionContext.getSnippetFs().getPath("README.md");
    		if(!Files.exists(file)) {
    			try {
    				Files.writeString(file, "## "+s.getFolder().getFileName().toString(), StandardCharsets.UTF_8);
    			} catch(IOException ex) {
    				Console.log(ex);
    			}
    		}
    		main.add(file.getFileName().toString());
    	}
    	
    	// fill the tabs
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
   			mainSplitPane.setDividerLocation(0.5);
        } else {
   			mainSplitPane.setDividerLocation(1.0);
    	}
    }

    protected void fillTabbedPane(JTabbedPane tabPane, List<String> files) {
    	tabPane.removeAll();
		tabPane.setVisible(!files.isEmpty());
    	for(String f: files) {
    		JComponent c = createTabbedPane(f);
    		if(c!=null) {
    			tabPane.addTab(f, null, c, null);
    		}
    	}
    }
    
    protected JComponent createTabbedPane(String name) {
    	// Should we better use MIME types?
    	String ext = PathUtil.POSIX.getFileExtension(name);
    	if(ext.equals("md")) {
        	return createMarkdownRenderer(name);
    	}
    	return createSyntaxArea(name);
    }
    
    protected JComponent createMarkdownRenderer(String name) {
        MarkdownRenderer md = new MarkdownRenderer();
		md.setMarkdown(executionContext.getContent(name));
		return md;
    }
    
    protected JComponent createSyntaxArea(String name) {
        SyntaxTextArea textArea = new SyntaxTextArea();
        RTextScrollPane scrollPane = new RTextScrollPane(textArea);
        scrollPane.setViewportBorder(null);
        scrollPane.setLineNumbersEnabled(true);

		textArea.setText(executionContext.getContent(name));

        textArea.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void removeUpdate(DocumentEvent e) {
				if(!dirty) {
					dirty = true;
					initTitle();
				}
				executionContext.setContent(name, textArea.getText());
				autoExecute();
				if(scratchpadActive) {
					scratchpadSaveTimer.restart();
				}
			}
			@Override
			public void insertUpdate(DocumentEvent e) {
				if(!dirty) {
					dirty = true;
					initTitle();
				}
				executionContext.setContent(name, textArea.getText());
				autoExecute();
				if(scratchpadActive) {
					scratchpadSaveTimer.restart();
				}
			}
			@Override
			public void changedUpdate(DocumentEvent arg0) {
//	        	if(eventsEnabled.get()) {
//		        	executeDelay();
//	        	}
			}
		});
        
        initSyntaxTextArea(textArea, name);
        
        edConsole.setFont(textArea.getFont());
        
        return scrollPane;
    }

    
    protected void initSyntaxTextArea(SyntaxTextArea textArea, String name) {
    	LanguageSupportFactory.get().register(textArea);
    	
    	if(name.endsWith(".js")) {
            textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
    	} else if(name.endsWith(".json")) {
            textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JSON);
    	} else if(name.endsWith(".java")) {
   			initJavaLanguage();
            textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
    	} else if(name.endsWith(".jshell")) {
   			initJavaLanguage();
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
		try {
			//jls.getJarManager().addCurrentJreClassFileSource();
			var info = LibraryInfo2.getMainJreJarInfo();
			if(info!=null) {
				jls.getJarManager().addClassFileSource(info);
			}
		} catch (IOException | RuntimeException e) {
			// Code completion is a nicety: never fail the editor because of it
			Console.log(e);
		}
    }

    
    //
    // Save snippet (dev mode)
    //
    private void saveSnippet() {
    	Snippet s = executionContext.getSnippet();
    	Path f = s.getFolder();
    	if(!f.getFileSystem().isReadOnly()) {
			try (Stream<Path> stream = Files.list(FilesUtil.getRoot(executionContext.getSnippetFs()))) {
			    stream
			    	.filter(Files::isRegularFile)
			    	.forEach( (p) -> {
			    		saveFile(p);
			    	});
			} catch(IOException ex) {
			}
    	}
    	if(dirty) {
    		dirty = false;
    		initTitle();
    	}
    }
    
    private void saveFile(Path inMemoryFile) {
    	MemoryFileSystemSnippet fs = executionContext.getSnippetFs();
    	Path snippetFile = fs.getPhysicalFile(inMemoryFile);
    	if(snippetFile==null) {
    		// A file that only exists in memory, like the README.md generated for a folder:
    		// saved next to the snippet files
    		snippetFile = executionContext.getSnippet().getFolder().resolve(inMemoryFile.getFileName().toString());
    		fs.setPhysicalFile(inMemoryFile, snippetFile);
    	}
    	String original = Files.exists(snippetFile) ? StringUtil.normalizeLineBreaks(FilesUtil.readString(snippetFile,StandardCharsets.UTF_8)) : null;
    	String newContent = StringUtil.normalizeLineBreaks(FilesUtil.readString(inMemoryFile,StandardCharsets.UTF_8));
    	if(!StringUtil.equals(original,newContent)) {
    		FilesUtil.writeString(snippetFile, newContent, StandardCharsets.UTF_8);
    	}
    }
    
    
    //
    // Execution engine
    //
    
    public void autoExecute() {
    	if(ckAutoExec.isSelected()) {
    		executeAsync(500);
    	}
    }
    
	public Object getExecutionOption(String key, Object defaultValue) {
		return defaultValue;
	}

    // To be overridden
    protected void processExecutionResult(ExecutionResult r) {
    }
    
    protected void interupt() {
    	Runnable stop = stopScript;
    	if(stop!=null) {
    		try {
    			stop.run();
    		} catch(RuntimeException e) {
    			Console.log(e);
    		}
    	}
    }
    
    public void executeNow() {
    	executeAsync(0);    
    }
    
	public void executeAsync(int delay) {
    	if(!PlaygroundConfiguration.get().getExecutionEngineFactory().isExecutable(executionContext.getSnippet())) {
    		return;
    	}
    	// Debounce: a request is dropped when a newer one was made before it started
    	final long request = executionRequest.incrementAndGet();
    	final ExecutionContext context = executionContext;
    	executorService.schedule(() -> {
    		if(request!=executionRequest.get()) {
    			return;
    		}
    		runExecution(context);
    	}, delay, TimeUnit.MILLISECONDS);
    }
	
	private void runExecution(ExecutionContext context) {
		long start = System.currentTimeMillis();
		onEdt( () -> {
			btnExecute.setEnabled(false);
			btnStop.setEnabled(true);
			lbExecution.setText("Executing...");
			if(!ckPreverveConsole.isSelected()) {
				TextAreaOutputStream.clear(getConsoleTextArea());
			}
		});
		try {
			ExecutionEngine engine = PlaygroundConfiguration.get().createExecutionEngine(context);
			ExecutionResult r;
			if(engine.isSoftInterruptable()) {
				stopScript = engine::softInterrupt;
				r = executeEngine(engine);
			} else {
				// Run on a dedicated thread, so Stop can interrupt it without
				// blocking the executor. The FutureTask cannot miss the completion,
				// even when the execution ends before get() is called.
				FutureTask<ExecutionResult> task = new FutureTask<>(() -> executeEngine(engine));
				Thread thread = new Thread(task, "playground-execution");
				thread.setDaemon(true);
				stopScript = thread::interrupt;
				thread.start();
				r = task.get();
			}
			onEdt( () -> processExecutionResult(r) );
		} catch(InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch(Throwable t) {
			reportException(t);
		} finally {
			stopScript = null;
			long end = System.currentTimeMillis();
			String text = StringFormat.format("Execution time: {0}",PeriodFormatter.formatPeriod(end-start));
			onEdt( () -> {
				lbExecution.setText(text);
				btnExecute.setEnabled(true);
				btnStop.setEnabled(false);
			});
		}
	}
	
	/**
	 * Execute the engine, reporting the failures to the console.
	 * Returns null when the execution itself failed.
	 */
	private ExecutionResult executeEngine(ExecutionEngine engine) {
		try {
			ExecutionResult r = engine.execute();
			if(r!=null && r.getException()!=null) {
				reportException(r.getException());
			}
			return r;
		} catch(Throwable t) {
			reportException(t);
			return null;
		}
	}
	
	private void reportException(Throwable t) {
		if(isThreadDeath(t)) {
			return;
		}
		if(isInterrupt(t)) {
			onEdtLater( () -> getConsoleTextArea().append("Execution stopped\n") );
			return;
		}
		boolean parser = t.getClass().getName().contains("ParseException");
		if(parser) {
			String msg = t.getLocalizedMessage();
			onEdtLater( () -> getConsoleTextArea().append(msg) );
		} else {
			Console.log(t);
			t.printStackTrace(TextAreaOutputStream.getPrintStream(getConsoleTextArea()));
		}
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
    
	private boolean isThreadDeath(Throwable t) {
    	while(t!=null) {
    		if(t instanceof ThreadDeath) {
    			return true;
    		}
    		String m = t.getMessage();
    		if(m!=null) {
	    		if(m.contains("ThreadDeath")) { // Graalvm...
	    			return true;
	    		}
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
