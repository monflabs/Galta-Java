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
package playground.impl;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.cdp.CdpServer;
import org.monflabs.galtajs.cdp.inprocess.InProcessCdpServer;
import org.monflabs.galtajs.debug.api.DebugOptions;
import org.monflabs.galtajs.debug.api.impl.DebuggerImpl;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.modules.JSPathModuleResolver;
import org.monflabs.galtajs.library.node.NodeModuleResolver;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.ASTProgram;
import org.monflabs.galtajs.rt.DebugUtil;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.JSTranspilerOptions;
import org.monflabs.js.debugger.ui.DebuggerPanel;
import org.monflabs.playground.ExecutionResult;
import org.monflabs.playground.PlaygroundConfiguration;
import org.monflabs.ui.swing.ide.IDEApplication;
import org.monflabs.ui.swing.ide.syntax.SyntaxTextArea;
import org.monflabs.util.BaseException;
import org.monflabs.util.Console;
import org.monflabs.util.StringFormat;
import org.monflabs.util.TextBuilder;

import com.monflabs.playground.galtajs.GaltaJSExecutionEngine;
import com.monflabs.playground.galtajs.GaltaJSExecutionResult;
import com.monflabs.playground.galtajs.SnippetEnvironment;
import com.monflabs.playground.swing.PlaygroundFrame;

/**
 *
 *
 */
@SuppressWarnings("serial")
public class GaltaJSPlaygroundFrame extends PlaygroundFrame {

	private JTree treeAstNodes;
	private JTextArea textAstNodes;
	private SyntaxTextArea transpiledCode;
	private SyntaxTextArea decompiledCode;
	
    private JButton btnDebug;
	private JButton btnExternalDebugger;
	private JCheckBox ckGaltaJS;
	private JCheckBox ckStrictMode;
	private JCheckBox ckOptimizer;

	// The one debugging session currently running, of either kind - starting
	// either button, closing the debug window, or selecting another snippet
	// (see canClose()) all funnel through cancelDebugSession() so exactly one
	// of these can ever be active.
	private DebuggerImpl activeDebugger;
	private AutoCloseable activeServer;
	private JFrame debugFrame;
	private DebuggerPanel debugPanel;

	private JLabel lbDebugServerInfo;
	private JButton btnOpenChromeInspect;

	public GaltaJSPlaygroundFrame() {
		treeAstNodes = new JTree();
		JScrollPane scrollPane1  = new JScrollPane();
		scrollPane1.setViewportView(treeAstNodes);

		textAstNodes = new JTextArea();
		JScrollPane scrollPane2  = new JScrollPane();
		scrollPane2.setViewportView(textAstNodes);

		JSplitPane splitter = new JSplitPane();
		splitter.setOrientation(JSplitPane.VERTICAL_SPLIT);
		// Should be realized in the screen to be effective...
		//splitter.setDividerLocation(0.7f);
		splitter.setTopComponent(scrollPane1);
		splitter.setBottomComponent(scrollPane2);
       	getResultTabPane().addTab("AST Tree", null, splitter, null);

       	transpiledCode = new SyntaxTextArea();
		transpiledCode.setEditable(false);
		transpiledCode.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
		RTextScrollPane scrollPane3  = new RTextScrollPane();
		scrollPane3.setViewportView(transpiledCode);
       	getResultTabPane().addTab("Java Transpiler", null, scrollPane3, null);

       	decompiledCode = new SyntaxTextArea();
       	decompiledCode.setEditable(false);
       	decompiledCode.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
		RTextScrollPane scrollPane4  = new RTextScrollPane();
		scrollPane4.setViewportView(decompiledCode);
       	getResultTabPane().addTab("Decompiled Nodes", null, scrollPane4, null);

       	processExecutionResult(null);
       	
       	treeAstNodes.addTreeSelectionListener(new TreeSelectionListener() {
            @Override
			public void valueChanged(TreeSelectionEvent e) {
            	ASTTreeNode node = (ASTTreeNode)treeAstNodes.getLastSelectedPathComponent();
            	if(node!=null) {
            		textAstNodes.setText(node.node!=null ? node.node.toString()+"\n\n"+getDescriptionString(node.node) : "");
            	} else {
            		textAstNodes.setText("");
            	}
            }
        });
	}
	
	@Override
	protected void initToolbarLeft(JToolBar toolBar) {
		super.initToolbarLeft(toolBar);

		// Right after Execute (index 0: Save Snippet, 1: Execute) - inserted,
		// not appended, so they land ahead of Stop/Auto Execute.
		btnDebug = new JButton("Debug");
		btnDebug.setToolTipText("Debug the current snippet in the built-in debugger panel, paused at its first "
				+ "statement. Cancels any running debug session and starts a fresh one, closing any open "
				+ "debugger window first.");
		btnDebug.addActionListener(e -> startDebugSession(true));
		toolBar.add(btnDebug, 2);

		btnExternalDebugger = new JButton("External Debugger");
		btnExternalDebugger.setToolTipText("Start a Chrome DevTools Protocol server on port " + DebugOptions.DEFAULT_PORT
				+ " - Node's own --inspect-brk default - running the current snippet paused at its first "
				+ "statement, for an external debugger (Chrome DevTools, VS Code, ...) to attach to. Cancels "
				+ "any running debug session and starts a fresh one.");
		btnExternalDebugger.addActionListener(e -> startDebugSession(false));
		toolBar.add(btnExternalDebugger, 3);

		lbDebugServerInfo = new JLabel(" ");
		getToolbarInfoPanel().add(lbDebugServerInfo);

		btnOpenChromeInspect = new JButton("<html><u>open chrome://inspect</u></html>");
		btnOpenChromeInspect.setBorderPainted(false);
		btnOpenChromeInspect.setContentAreaFilled(false);
		btnOpenChromeInspect.setFocusPainted(false);
		btnOpenChromeInspect.setForeground(new Color(0x2a, 0x6f, 0xc4));
		btnOpenChromeInspect.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btnOpenChromeInspect.setToolTipText("Launch Chrome on its inspect page; the playground appears under Remote Target");
		btnOpenChromeInspect.setVisible(false);
		btnOpenChromeInspect.addActionListener(e -> openChromeInspect());
		getToolbarInfoPanel().add(btnOpenChromeInspect);

        ckGaltaJS = new JCheckBox("GaltaJS Extension");
        ckGaltaJS.setSelected(true);
        ckGaltaJS.addActionListener(new ActionListener() {
        	@Override
			public void actionPerformed(ActionEvent e) {
       			executeNow();
        	}
        });
        getToolbar().add(ckGaltaJS);
		
        ckStrictMode = new JCheckBox("Strict Mode");
        ckStrictMode.setSelected(true);
        ckStrictMode.addActionListener(new ActionListener() {
        	@Override
			public void actionPerformed(ActionEvent e) {
       			executeNow();
        	}
        });
        getToolbar().add(ckStrictMode);
	}
	@Override
	protected void initToolbarRight(JToolBar toolBar) {
		super.initToolbarRight(toolBar);
		
        ckOptimizer = new JCheckBox("Optimize Code");
        ckOptimizer.addActionListener(new ActionListener() {
        	@Override
			public void actionPerformed(ActionEvent e) {
       			executeNow();
        	}
        });
        getToolbar().add(ckOptimizer);
	}
	
    @Override
	public Object getExecutionOption(String key, Object defaultValue) {
		if(GaltaJSExecutionEngine.OPTION_OPTIMIZE.equals(key)) {
			return (Boolean)ckOptimizer.isSelected();
		}
		if(GaltaJSExecutionEngine.OPTION_GALTAJS.equals(key)) {
			return (Boolean)ckGaltaJS.isSelected();
		}
		if(GaltaJSExecutionEngine.OPTION_STRICTMODE.equals(key)) {
			return (Boolean)ckStrictMode.isSelected();
		}
		return defaultValue;
	}
	
    @Override
	protected void processExecutionResult(ExecutionResult r) {
    	// We should do this only when the tab is selected
    	if(r instanceof GaltaJSExecutionResult jr) {
    		updateASTTree(jr.getScript());
    		updateJavaCode(jr.getScript());
    		updateDecompiler(jr.getScript());
    	} else {
    		buildASTTree(null);
    		transpiledCode.setText("// No generated code");
    		decompiledCode.setText("// No generated code");
    	}
    }
    
	/**
	 * Compiles the current snippet fresh and wraps it in a new
	 * {@link DebuggerImpl}, ready to {@link DebuggerImpl#start()} - shared by
	 * both kinds of debug session started by {@link #startDebugSession(boolean)}.
	 */
	private DebuggerImpl newDebugger() {
		GaltaJSExecutionEngine jsEngine = (GaltaJSExecutionEngine)PlaygroundConfiguration.get().getExecutionEngineFactory().createExecutionEngine(getExecutionContext());

		JSEnvironment env = SnippetEnvironment.newBuilder(ckGaltaJS.isSelected(),ckStrictMode.isSelected())
				.addModuleResolver(new JSPathModuleResolver(getExecutionContext().getSnippetFs()))
				.addModuleResolver(new NodeModuleResolver(getExecutionContext().getSnippetFs()))
				.debug(true)
				.build();
		String script = getExecutionContext().getContent(GaltaJSExecutionEngine.DEFAULT_JS);
		JSInterpretedUnit sc = env.createScript(script,GaltaJSExecutionEngine.DEFAULT_JS);

		return new DebuggerImpl(sc, () -> jsEngine.createProgramRuntimeContext(env));
	}

	/**
	 * Starts a fresh debug session for the current snippet, paused at its
	 * first statement - like a real {@code node --inspect-brk} launch.
	 * Cancels whatever session (of either kind) is already running first, so
	 * this is the only entry point either debugger button needs.
	 *
	 * @param internal true for the built-in Swing panel over the in-process
	 * (socket-free) CDP transport ("Debug"); false for a real Chrome DevTools
	 * Protocol server on GaltaJS's default debugger port, for an external
	 * client (Chrome DevTools, VS Code, ...) to attach to ("External Debugger")
	 */
	private void startDebugSession(boolean internal) {
		cancelDebugSession();

		DebuggerImpl debugger;
		try {
			debugger = newDebugger();
		} catch(Exception e) {
			JOptionPane.showMessageDialog(this, e.getMessage(), "Start the debugger", JOptionPane.ERROR_MESSAGE);
			return;
		}
		debugger.pauseOnStart();

		if(internal) {
			// no socket, no port: the engine and this panel run in the very
			// same JVM, so there is nothing to dial and nothing that can
			// fail binding
			InProcessCdpServer.Handle server = InProcessCdpServer.open(debugger, DebugOptions.parse("", false));

			DebuggerPanel panel = new DebuggerPanel(new Font(Font.MONOSPACED, Font.PLAIN, 12), isDarkTheme());
			JFrame frame = new JFrame("GaltaJS Debugger");
			frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			frame.setSize(1200, 850);
			frame.getContentPane().add(panel, BorderLayout.CENTER);

			activeDebugger = debugger;
			activeServer = server;
			debugFrame = frame;
			debugPanel = panel;

			AtomicBoolean started = new AtomicBoolean();
			// the script only starts once the panel is actually connected and
			// has enabled the Debugger domain - Debugger.enable does not
			// replay an already-in-progress pause, so starting any earlier
			// could pause the script before a client exists to be told about it
			panel.onConnectionChange((state, detail) -> {
				if(state==DebuggerPanel.ConnectionState.CONNECTED && started.compareAndSet(false, true)) {
					debugger.start();
				}
			});
			frame.addWindowListener(new WindowAdapter() {
			    @Override
			    public void windowClosed(WindowEvent e) {
			    	// only if this window's own session is still the active
			    	// one - avoids re-entering cancelDebugSession() when IT
			    	// is what disposed this window in the first place
			    	if(activeDebugger==debugger) {
			    		cancelDebugSession();
			    	}
			    }
			});

			frame.setVisible(true);
			panel.attach(server.clientChannel());
		} else {
			CdpServer.Handle server;
			try {
				server = CdpServer.open(debugger, DebugOptions.parse(Integer.toString(DebugOptions.DEFAULT_PORT), false));
			} catch(IOException e) {
				JOptionPane.showMessageDialog(this, "Cannot start the debugger server: " + e.getMessage(), "Start the debugger", JOptionPane.ERROR_MESSAGE);
				return;
			}
			activeDebugger = debugger;
			activeServer = server;

			debugger.start();
			lbDebugServerInfo.setText("Debugger listening on " + server.webSocketUrl() + ", paused at the first statement — ");
			btnOpenChromeInspect.setVisible(true);

			// A real Node/V8 inspector closes the connection when the
			// debugged process exits, which is how DevTools knows the run is
			// over. Without an equivalent here, a script resumed to
			// completion just left the server open with no signal at all -
			// the banner never went away even though the run had genuinely
			// finished. Watching the execution thread and cancelling the
			// session on completion (only if this hasn't since been
			// superseded by a newer session) fixes that.
			final DebuggerImpl startedDebugger = debugger;
			final Thread watcher = new Thread(() -> {
				try {
					startedDebugger.getExecutionThread().join();
				} catch(InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
				SwingUtilities.invokeLater(() -> {
					if(activeDebugger==startedDebugger) {
						cancelDebugSession();
					}
				});
			}, "galtajs-debug-session-watcher");
			watcher.setDaemon(true);
			watcher.start();
		}
	}

	/**
	 * Tears down whichever debug session (built-in panel or external CDP
	 * server) is currently running, if any - closing the debugger window
	 * first if one is open. Called before starting a fresh session, when the
	 * debugger window is closed, and when another snippet is selected (see
	 * {@link #canClose()}).
	 */
	private void cancelDebugSession() {
		DebuggerImpl debugger = activeDebugger;
		activeDebugger = null;
		AutoCloseable server = activeServer;
		activeServer = null;
		JFrame frame = debugFrame;
		debugFrame = null;
		DebuggerPanel panel = debugPanel;
		debugPanel = null;

		if(frame!=null) {
			frame.dispose();
		}
		if(panel!=null) {
			panel.close();
		}
		if(debugger!=null) {
			debugger.close();
		}
		if(server!=null) {
			try {
				server.close();
			} catch(Exception ignored) {
				// closing anyway
			}
		}
		lbDebugServerInfo.setText(" ");
		btnOpenChromeInspect.setVisible(false);
	}

    /**
     * Launches Chrome (or another Chromium) on its inspect page. chrome:// is
     * not an OS-registered scheme, so the browser is started with the URL as
     * an argument, per platform, first candidate that starts winning.
     */
    private void openChromeInspect() {
    	final String url = "chrome://inspect/#devices";
    	final String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    	final List<List<String>> candidates = new ArrayList<>();
    	if(os.contains("mac")) {
    		candidates.add(List.of("open", "-a", "Google Chrome", url));
    		candidates.add(List.of("open", "-a", "Chromium", url));
    	} else if(os.contains("win")) {
    		candidates.add(List.of("cmd", "/c", "start", "chrome", url));
    		candidates.add(List.of("cmd", "/c", "start", "msedge", "edge://inspect/#devices"));
    	} else {
    		candidates.add(List.of("google-chrome", url));
    		candidates.add(List.of("chromium", url));
    		candidates.add(List.of("chromium-browser", url));
    	}
    	for(List<String> command : candidates) {
    		try {
    			Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
    			if(!process.waitFor(2, TimeUnit.SECONDS) || process.exitValue()==0) {
    				return;   // still running, or done and content: the browser is on its way
    			}
    		} catch(IOException notThere) {
    			// try the next candidate
    		} catch(InterruptedException interrupted) {
    			Thread.currentThread().interrupt();
    			return;
    		}
    	}
    	JOptionPane.showMessageDialog(this,
    			"Could not launch Chrome. Open chrome://inspect in a Chromium browser yourself;\nthe playground appears under Remote Target.",
    			"Start the debugger server", JOptionPane.INFORMATION_MESSAGE);
    }

    // Called both when the window itself is closing and - by the base
    // class's snippet tree selection model - before switching to another
    // snippet, so cancelling the active debug session here covers both
    // "selecting another snippet cancels any existing debugging session"
    // and the window-close case in one place.
    @Override
    public boolean canClose() {
    	if(!super.canClose()) {
    		return false;
    	}
    	cancelDebugSession();
    	return true;
    }

    private boolean isDarkTheme() {
    	try {
    		return IDEApplication.get().getTheme().isDark();
    	} catch(Exception e) {
    		return false;
    	}
    }

    
	protected void updateASTTree(JSInterpretedUnit sc) {
		sc.getEnvironment().run(() -> {
	    	buildASTTree(sc.getProgram());
		});
    }
    
	
	//
	// Transpiler
	//
	protected void updateJavaCode(JSInterpretedUnit sc) {
		String code = transpiledToJava(sc);
		transpiledCode.setText(code);
    }
	public String transpiledToJava(JSInterpretedUnit script) {
		try {
			JSTranspilerOptions opt = JSTranspilerOptions.newBuilder()
					.sourceInCode(true)
					.build();
			JSTranspiler compiler = new JSTranspiler(script.getEnvironment(),opt);
			String javaCode = compiler.compile("Test","Object",script);
			return javaCode;
		} catch(Exception e) {
			Console.log(e);
			return StringFormat.format("//\n// TRANSPILER ERROR\n\n{0}", BaseException.getStackAsString(e) );
		}
	}

	
	//
	// Decompiler
	//
	protected void updateDecompiler(JSInterpretedUnit sc) {
		String code = decompileToJava(sc);
		decompiledCode.setText(code);
    }
	public String decompileToJava(JSInterpretedUnit script) {
		try {
			String javaCode = script.getProgram().decompile();
			return javaCode;
		} catch(Exception e) {
			Console.log(e);
			return StringFormat.format("//\n// DECOMPILER ERROR\n\n{0}", BaseException.getStackAsString(e) );
		}
	}

	
	//
	// AST Tree
	//
	private static class ASTTreeNode extends DefaultMutableTreeNode {
		
		private static String label(ASTNode node) {
			return node!=null ? node.toString() : "<null>";
		}

		ASTNode node;
		
		ASTTreeNode(String label) {
			super(label);
		}
		
		ASTTreeNode(ASTNode node) {
			super(label(node));
			this.node = node;
		}
	}
	public void buildASTTree(ASTProgram node) {
		ASTTreeNode root = node != null ? createTreeNode(node) : new ASTTreeNode("<empty>");
		treeAstNodes.setModel(new DefaultTreeModel(root));
		expandAllNodes(treeAstNodes);
	}

	private void expandAllNodes(JTree tree) {
		int j = tree.getRowCount();
		int i = 0;
		while (i < j) {
			tree.expandRow(i);
			i += 1;
			j = tree.getRowCount();
		}
	}

	private ASTTreeNode createTreeNode(ASTNode node) {
		ASTTreeNode treeNode;
		treeNode = new ASTTreeNode(node);
		addChildren(treeNode);
//		if (node instanceof ASTUnary) {
//			ASTUnary n = (ASTUnary) node;
//			treeNode = new ASTTreeNode(n, n.type.symbol());
//			addChild(treeNode, n.node);
//		} else {
//			throw new ExpressionException(null, "Internal error: invalid ASTNode " + node.getClass());
//		}
		return treeNode;
	}


//	private ASTTreeNode addContainer(ASTTreeNode parent, String label) {
//		String txt = label;
//		ASTTreeNode treeNode = new ASTTreeNode(txt);
//		parent.add(treeNode);
//		return treeNode;
//	}
//
//	private void addValue(ASTTreeNode parent, String label, Object value) {
//		String txt = label + "=" + (value != null ? value.toString() : "");
//		ASTTreeNode treeNode = new ASTTreeNode(txt);
//		parent.add(treeNode);
//	}

	private void addChild(ASTTreeNode parent, ASTNode child) {
		ASTTreeNode treeNode = createTreeNode(child);
		parent.add(treeNode);
	}

	private void addChildren(ASTTreeNode parent) {
		ASTNode node = parent.node;
		if(node!=null) {
			int sz = node.getChildCount();
			for(int i=0; i<sz; i++) {
				addChild(parent, node.getChild(i));
			}
		}
	}
	
	//
	// Description field
	//

	public String getDescriptionString(ASTNode node) {
		TextBuilder b = new TextBuilder();
		readObject(b,node);
		return b.toString();
	}
	public static void readObject(TextBuilder b, Object o) {
		List<Field> fields = new ArrayList<>();
		for( Class<?> c=o.getClass(); c!=null && acceptClass(c); c=c.getSuperclass()) {
			Field[] cfields = c.getDeclaredFields();
			for(int i=0; i<cfields.length; i++) {
				Field f = cfields[i];
				if(acceptField(f)) {
					f.setAccessible(true);
					fields.add(f);
				}
			}
		}
		fields.sort((f1,f2) -> f1.getName().compareToIgnoreCase(f2.getName()) );
		
		b.incIndent();
		for(Field f: fields) {
			try {
				Object v = f.get(o);
				b.print("{0}",f.getName());
				printValue(b,v);
			} catch (Exception e) {
				b.println("{0}={1}",f.getName(),e.toString() );
			}
		}
		b.decIndent();
	}
	public static void printValue(TextBuilder b, Object v) {
		if(v!=null) {
			Class<?> fc = v.getClass();
			if(fc==String.class || fc==Boolean.class || Number.class.isAssignableFrom(fc)) {
				b.println("={0}",DebugUtil.jsLiteral(SnippetEnvironment.staticValue, v, 128) );
			} else if(fc.isArray()) {
				b.println(", {0}",Array.getLength(v));
				b.incIndent();
				int c = Array.getLength(v);
				for(int i=0; i<c; i++) {
					b.print("[{0}]",i);
					printValue(b, Array.get(v, i));
				}
				b.decIndent();
			} else if(List.class.isAssignableFrom(fc)) {
				List<?> l = (List<?>)v;
				b.println(", {0}",l.size());
				b.incIndent();
				int c = l.size();
				for(int i=0; i<c; i++) {
					b.print("[{0}]",i);
					printValue(b, l.get(i));
				}
				b.decIndent();
			} else if(Map.class.isAssignableFrom(fc)) {
				Map<?,?> m = (Map<?,?>)v;
				b.println(", {0}",m.size());
				b.incIndent();
				for(Map.Entry<?,?> e: m.entrySet()) {
					b.print("{0}",e.getKey());
					printValue(b, e.getValue());
				}
				b.decIndent();
			} else {
				b.println(", {0}", fc.getName());
				readObject(b,v);
			}
		} else {
			b.println("=<null>");
		}
	}
	private static boolean acceptClass(Class<?> c) {
		if(c==ASTNode.class) {
			return false;
		}
		return true;
	}
	private static boolean acceptField(Field f) {
		if(Modifier.isStatic(f.getModifiers())) {
			return false;
		}
		// No child nodes as they are in the hierarchy
		if(ASTNode.class.isAssignableFrom(f.getType())) {
			return false;
		}
		if(ASTNode[].class.isAssignableFrom(f.getType())) {
			return false;
		}
		if(List.class.isAssignableFrom(f.getType())) {
		    if (f.getGenericType() instanceof ParameterizedType pt) {
		    	Class<?> gt = (Class<?>)pt.getActualTypeArguments()[0];
				if(ASTNode.class.isAssignableFrom(gt)) {
					return false;
				}
		    }
		}
		if(f.getName().contains("$")) { // No outer class fields
			return false;
		}
		return true;
	}

}