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
package tests.suite;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.zip.CRC32;

import org.junit.Before;
import org.monflabs.filesystem.memory.MemoryFileSystem;
import org.monflabs.filesystem.path.PathFileSystem;
import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.js.debugger.ui.SwingDebugger;
import org.monflabs.galtajs.optimizer.ScriptOptimizer;
import org.monflabs.galtajs.rt.JSRuntimeInterruptException;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;
import org.monflabs.galtajs.rt.transpiler.JSTranspiledUnit;
import org.monflabs.galtajs.rt.transpiler.TranspiledGlobalRuntimeContext;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.JSTranspilerOptions;
import org.monflabs.javacompiler.JavaCompiler;
import org.monflabs.javacompiler.JavaCompilerFactory;
import org.monflabs.util.Console;
import org.monflabs.util.ConsoleColors;
import org.monflabs.util.FileUtil;
import org.monflabs.util.StreamUtil;
import org.monflabs.util.StringFormat;
import org.monflabs.util.StringUtil;
import org.monflabs.util.datetime.PeriodFormatter;
import org.monflabs.util.path.FilesUtil;
import org.monflabs.util.path.PathClassLoader;

import tests.BaseProjectTestCase;


public abstract class BaseTestSuiteTest extends BaseProjectTestCase {

	public static boolean OPTIMIZE_NODES = false;

	/**
	 * What the runner was doing for the current file when an exception was
	 * thrown - lets a subclass check WHEN an expected error happened (a
	 * test262 negative test with {@code phase: parse} must fail while the
	 * script is being created, never while it runs).
	 */
	public enum ExecPhase {
		/** Loading the harness/shell */
		SETUP,
		/** Parsing the file: env.createScript() and its early errors */
		PARSE,
		/** Transpiled mode only: generating and compiling the Java source */
		TRANSPILE,
		/** Running the script */
		EXECUTE
	}
	protected ExecPhase execPhase = ExecPhase.SETUP;
	
	private Charset WIN1252 = Charset.forName("windows-1252");

	private boolean debugger;

	public BaseTestSuiteTest() {
	}

	public BaseTestSuiteTest(boolean debugger) {
		this.debugger = debugger;
	}

	@Override
	protected boolean isJavaTranspiler() {
		if(_ALLTESTS) {
			return super.isJavaTranspiler();
		}
		return false;
	}
	protected boolean consoleShowPassed() {
		return false;
	}
	protected boolean isDebugger() {
		return debugger;
	}
	protected boolean consoleVerbose() {
		return false;
	}
	protected boolean filterFiles() {
		return true;
	}
	protected boolean filterErrors() {
		return true;
	}
	protected boolean filterFilesWithKnownErrors() {
		return false;
	}
	protected boolean stopOnFirstErrors() {
		return false;
	}

	protected String[] getFILTER() {
		return new String[0];
	}
	protected Object[] getFILTER_ERRORS() {
		return new Object[0];
	}
    protected String preprocessFile(Path file, String text) {
    	return text;
    }

	protected void initDebugger(SwingDebugger d) {
		//d.getDebuggerUI().addBreakpoint("15.4.4.5-3.js", 21);
	}


	// Checking tests files when they are external
    private static long crc32(String input) {
        CRC32 crc = new CRC32();
        crc.update(input.getBytes(StandardCharsets.UTF_8));
        return crc.getValue();
    }
    public static void checkFileCrc(String input, long value) {
    	long crc = crc32(input);
    	if(crc!=value) {
    		fail(StringFormat.format("Invalid checksum, source file has changed, checksum should be {0} instead of {1}",crc,value));
    	}
    }
	
	protected TimeZone defaultTZ;

	@Override
	@Before
    public void setUp() throws Exception {
		defaultTZ = TimeZone.getDefault();
		TimeZone PST = TimeZone.getTimeZone("PST");
		TimeZone.setDefault(PST);
		super.setUp();
	}

    public int exec(File rootDir, String file) throws Exception {
    	return exec(rootDir,file,true);
    }
    public int exec(File rootDir, String file, boolean recursive) throws Exception {
    	// We use a subpath system to ensure that the separator is always '/'
    	// Else, it complicates the filtering
    	PathFileSystem fs = PathFileSystem.newBuilder()
    			.root(rootDir.toPath())
    			.build();
    	
    	Path rootPath = FilesUtil.getRoot(fs);

    	int errorCount = 0;
    	
		Console.log("-----------------------------------------------------------------------");
		Console.outStream().println(StringFormat.format("Mode={0}", isJavaTranspiler() ? "TRANSPILER" : "INTERPRETER"));
		
		File targetFolder = new File(support.getProjectRoot(),"/src/test/java/compiled");
		FileUtil.emptyDirectory(targetFolder);

    	long startTs = System.currentTimeMillis();
        try {
	    	File logDir = new File(support.getTargetDirectory(),"jslogs");
	    	logDir.mkdirs();
	        	
        	Path f = StringUtil.isNotEmpty(file) ? rootPath.resolve(file) : rootPath;
        	if(Files.isDirectory(f)) {
       			errorCount += execFolder(rootPath,f,debugger,recursive);
        	} else {
                errorCount += execFile(rootPath,f,debugger);
        	}
        } catch(Throwable e) {
        	e.printStackTrace();
        } finally {
    		long endTs = System.currentTimeMillis();
        	if(consoleVerbose()) {
	    		Console.log("Total execution time: {0}", PeriodFormatter.formatPeriod(endTs-startTs));
	    		Console.log("");
	    		Console.log("Errors {0}", errorCount);
	    		Console.log("");
	    		Console.log("-----------------------------------------------------------------------");
        	}
        }
        return errorCount;
    }
	
    private int execFolder(Path rootDir, Path file, boolean debugger, boolean recursive) throws Exception {
    	int errorCount = 0;

    	Console.log("TestSuite: "+file.toString());
        long startTs = System.currentTimeMillis();
        try {
        	List<Path> files = StreamUtil.using( Files.list(file), (s) -> s.collect(Collectors.toList()) );
    		files.sort((f1,f2) -> f1.toString().compareToIgnoreCase(f2.toString()));
            for(Path f: files) {
	        	if(Files.isDirectory(f)) {
	        		if(recursive) {
	        			errorCount += execFolder(rootDir,f,debugger,recursive);
	        		}
	        	} else {
	        		errorCount += execFile(rootDir,f,debugger);
	        	}
	        }
        } finally {
    		long endTs = System.currentTimeMillis();
        	if(consoleVerbose()) {
        		Console.log("Suite execution time: {0}", PeriodFormatter.formatPeriod(endTs-startTs));
        	}
        }
        return errorCount;
    }
    
    private int execFile(Path rootDir, Path scriptFile, boolean debugger) throws Exception {
    	String parentPath = rootDir.toString();
    	String childPath = scriptFile.toString();
    	String relativePath = childPath.substring(parentPath.length());
    	// The script's own descriptor name (passed to env.createScript()
    	// below) needs to be its FULL path relative to rootDir, not just
    	// the bare filename - a relative `import`/`import(...)` specifier
    	// (e.g. `./sibling_FIXTURE.js`) is resolved via
    	// ModuleUtil.resolvePath(parentName, name), which derives "the
    	// importing file's own directory" purely from stripping the LAST
    	// slash-separated segment off parentName. A bare filename (no
    	// slashes at all) has no directory component to strip, so every
    	// relative import silently resolved to just the sibling's bare
    	// name with the real directory lost - "Cannot find module" for any
    	// module resolver rooted somewhere other than the current working
    	// directory (confirmed via test262's language/expressions/dynamic-import
    	// suite, ~350 files, once flags:[async] started actually running
    	// them). Normalized to forward slashes with no leading separator,
    	// matching what ModuleUtil.resolvePath()/JSPathModuleResolver
    	// (Path.resolve(name)) both expect.
    	String moduleName = relativePath.replace(java.io.File.separatorChar,'/');
    	if(moduleName.startsWith("/")) {
    		moduleName = moduleName.substring(1);
    	}

        if(!debugger) {
        	if(filterFiles()) {
        		String[] FILTER = getFILTER();
        		if(FILTER!=null) {
			        for(int fi=0; fi<FILTER.length; fi++) {
			    		if(relativePath.indexOf(FILTER[fi])>=0) {
			    			return 0;
			    		}
			    	}
        		}
        	}
        	if(filterFilesWithKnownErrors()) {
        		Object[] FILTER_ERRORS = getFILTER_ERRORS();
        		if(FILTER_ERRORS!=null) {
			        for(int fi=0; fi<FILTER_ERRORS.length; fi+=2) {
			    		if(relativePath.indexOf((String)FILTER_ERRORS[fi])>=0) {
			    			return 0;
			    		}
			    	}
        		}
        	}
        }

        if(!Files.exists(scriptFile)) {
            throw new IllegalArgumentException("File: '"+scriptFile.toString()+"' does not exist");
        }
        if(scriptFile.toString().endsWith(".js")) {
        	// Always renders this
			Console.log("  "+relativePath);
			
	        JSEnvironment.Builder envBuilder = createEnvironment();
	        if(OPTIMIZE_NODES) {
				ScriptOptimizer opt = ScriptOptimizer.newBuilder()
						.optimizers(ScriptOptimizer.DEFAULT_NODE_OPTIMIZER_NODES)
						.build();
				envBuilder.scriptOptimizer(opt);
	        }
	        
	        AtomicInteger errorCount = new AtomicInteger();

	        long startTs = System.currentTimeMillis();
	        long startExecTs = startTs;
        	try {
    			final String className = relativePath
    					.substring(0,relativePath.length()-3) // remove .js
    					.replace('/','_')
    					.replace('-','_')
    					.replace('.','_');
        		if(isJavaTranspiler()) {
        			JSTranspilerOptions options = JSTranspilerOptions.newBuilder()
        					.debugInformation(true)
        					.sourceMap(true)
        					.sourceCode(true)
        					.build();
        			JSEnvironment env = envBuilder.build();

        			// Deliberately NOT overriding createClassBody() to add a debug
        			// public-static-main() (createJavaStaticMain) here: the automated
        			// path below never calls main() - it constructs the compiled unit
        			// via reflection (PathClassLoader + getConstructor()) and calls
        			// runValue(ctx) directly - so a generated main() is dead code that
        			// still has to compile. It previously hardcoded a reference to
        			// tests.ecma.GlobalTestEnvironment, a class that doesn't exist in
        			// this module (or, by now, any module), which broke compilation -
        			// and therefore the ENTIRE remaining batch, since a thrown
        			// JavaCompilerException here isn't caught per-file, it unwinds all
        			// the way up to exec()'s outer catch - for every transpiled test in
        			// every module reusing this shared base class.
        			JSTranspiler transpiler = new JSTranspiler(env, options);

        			// The whole block below (parse, transpile, compile, construct,
        			// run) is one try/catch, not just runValue() at the end: a
        			// genuine parse-time SyntaxError (some test262 files are
        			// negative tests, others exercise not-yet-implemented syntax)
        			// or a transpiler/javac/reflection failure must be counted as
        			// THIS file's error via handleException, exactly like a
        			// runtime failure - not left to propagate uncaught past
        			// execFile/execFolder/exec's outer catch-and-print, which
        			// silently abandons every remaining file in the batch and
        			// discards the error count accumulated so far (the same
        			// failure mode as the two bugs fixed above, just one level
        			// earlier in this same method).
        			try {
			            // Load the test code
			            execPhase = ExecPhase.SETUP;
		            String shell = readShell(scriptFile.getParent(),null);
			            // MUST be read before getScriptFlags() - the latter reads
			            // currentMetadata, which loadScript()'s own preprocessFile()
			            // call populates as a side effect (parses the YAML front-
			            // matter, including `flags: [...]`) - calling
			            // getScriptFlags() any earlier sees stale/null metadata from
			            // whatever file ran before this one, silently missing
			            // SCRIPT_MODULE for a genuine flags:[module] test.
			            String scriptText = loadScript(scriptFile);
			            int scriptFlags = getScriptFlags(scriptFile);

	    				TranspiledGlobalRuntimeContext ctx = new TranspiledGlobalRuntimeContext(env,env.createProgramExecutor());
	    				ctx.setOutStream(new PrintStream(new ConsoleFilterOutputStream(Console.outStream(),errorCount)));
	    				try {
	    					if((scriptFlags & JSEnvironment.SCRIPT_MODULE)!=0 && StringUtil.isNotEmpty(shell)) {
	    						compileAndRunTranspiledUnit(env, transpiler, "shell_"+className, shell, "shell.js", JSEnvironment.SCRIPT_ADDTOCACHE, ctx);
	    						compileAndRunTranspiledUnit(env, transpiler, className, scriptText, moduleName, scriptFlags, ctx);
	    					} else {
	    						String testCode = combineShellAndScript(shell, scriptText);
	    						compileAndRunTranspiledUnit(env, transpiler, className, testCode, moduleName, scriptFlags, ctx);
	    					}
	    				} finally {
	    					ctx.getOutStream().close();
	    				}
    	        	} catch(JSRuntimeInterruptException e) {
    	        		// Ignore, this is is normal
    	        	} catch(Throwable t) {
    	        		handleException(relativePath, errorCount, t);
    				}
        		} else {
        	        if(debugger) { // only for interpreted mode
        	    		envBuilder.debug(true);
        	        }
        	        JSEnvironment env = envBuilder.build();

		        	InterpretedGlobalRuntimeContext jsContext = new InterpretedGlobalRuntimeContext(env,env.createProgramExecutor());
		        	execPhase = ExecPhase.SETUP;

		            // Initialize the context with the shell
		            loadShell(jsContext,scriptFile.getParent());
		        	String testCode = loadScript(scriptFile);


			        if(debugger) {
		                JSInterpretedUnit testScript = env.createScript(testCode,moduleName,getScriptFlags(scriptFile));
			    		SwingDebugger d = new SwingDebugger();
			    		initDebugger(d);
			    		d.setVisible(true);
			    		d.debugScript(testScript, () -> jsContext);
			        } else {
			        	startExecTs= System.currentTimeMillis();
	    	        	try {
	    	        		if(isInterpreterSaveClass()) {
		        				File ecmaSourceFile = new File(support.getProjectRoot(),"/src/test/java/interpreted/"+className+".js");
			        			ecmaSourceFile.getParentFile().mkdirs();
			        			support.saveFile(ecmaSourceFile, testCode);
	    	        		}
	    	        		execPhase = ExecPhase.PARSE;
	    	        		JSInterpretedUnit testScript = env.createScript(testCode,moduleName,getScriptFlags(scriptFile));
	    	        		jsContext.setOutStream(new PrintStream(new ConsoleFilterOutputStream(Console.outStream(),errorCount)));
	    	        		try {
	    	        			execPhase = ExecPhase.EXECUTE;
	    	        			testScript.executeWithContext(jsContext);
	    	        		} finally {
	    	        			jsContext.getOutStream().close();
	    	        		}
	    	        	} catch(JSRuntimeInterruptException e) {
	    	        		// Ignore, this is is normal
	    	        	} catch(Throwable t) {
	    	        		handleException(relativePath, errorCount, t);
	    	        	}
			        }
        		}
        	} finally {
        		// No-op for every existing subclass (default empty) - lets
        		// Test262BaseTest detect a flags:[async] file that never
        		// called $DONE at all (still a FAILURE, not silently
        		// ignored - see its own override).
        		afterExecute(relativePath, errorCount);

        		int errorExpected = 0;
        		int errorFound = errorCount.get();
        		
        		// Remove the error count for the files with known errors
        		if(filterErrors()) {
            		Object[] FILTER_ERRORS = getFILTER_ERRORS();
            		if(FILTER_ERRORS!=null) {
		    	        for(int fi=0; fi<FILTER_ERRORS.length; fi+=2) {
		    	    		if(relativePath.indexOf((String)FILTER_ERRORS[fi])>=0) {
		    	    			int expected = ((Number)FILTER_ERRORS[fi+1]).intValue();
		    	    			errorExpected += expected;
		    	    			errorCount.getAndAdd(-expected);
		    	    		}
		    	    	}
            		}
        		}
        		
        		if(errorFound<0) {
        			fail("INTERNAL ERROR: try to void more errors than FOUND");
        		}

        		if(errorFound!=errorExpected) {
       				Console.outStream().print(ConsoleColors.RED_BOLD_BRIGHT);
        			Console.log("       {0}, Errors: {1} total, {2} expected, {3} final", relativePath, errorFound, errorExpected, errorCount.get());
           			Console.outStream().print(ConsoleColors.RESET);
        			if(stopOnFirstErrors()) {
            			fail("Stopped as errors happened");
        			}
        		}
        		
        		if(!debugger) {
	        		long endTs = System.currentTimeMillis();
	            	if(consoleVerbose()) {
	            		Console.log("     Time: {0}, execution: {1}", PeriodFormatter.formatPeriod(endTs-startTs), PeriodFormatter.formatPeriod(endTs-startExecTs));
	            	}
        		}

        		errorCount.set(Math.abs(errorFound-errorExpected));
        	}
        	
        	return errorCount.get();
        }
        
        return 0;
    }

    private void compileAndRunTranspiledUnit(JSEnvironment env, JSTranspiler transpiler, String className,
            String code, String unitName, int scriptFlags, TranspiledGlobalRuntimeContext ctx) throws Exception {
        execPhase = ExecPhase.PARSE;
        JSInterpretedUnit unitAst = env.createScript(code,unitName,scriptFlags);
        execPhase = ExecPhase.TRANSPILE;
        String javaSource = transpiler.compile(className,"Object",unitAst);

        if(true) {
            String savedCode = StringUtil.replaceAll(javaSource,JSTranspiler.PACKAGE_COMMENT, "package compiled;");
            File ecmaSourceFile = new File(support.getProjectRoot(),"/src/test/java/compiled/js/"+className+".js");
            File ecmaGeneratedFile = new File(support.getProjectRoot(),"/src/test/java/compiled/"+className+".java");
            ecmaSourceFile.getParentFile().mkdirs();
            support.saveFile(ecmaSourceFile, code);
            ecmaGeneratedFile.getParentFile().mkdirs();
            support.saveFile(ecmaGeneratedFile, savedCode);
        }

        MemoryFileSystem memoryFs = MemoryFileSystem.newBuilder().build();
        Path srcFs = Files.createDirectory(memoryFs.getPath("src"));
        Path tgtFs = Files.createDirectory(memoryFs.getPath("tgt"));

        Files.writeString(srcFs.resolve(className+".java"),javaSource, StandardCharsets.UTF_8);

        try(JavaCompiler cp = JavaCompilerFactory.newBuilder()
                .classLoader(getClass().getClassLoader())
                .sourceFolder(srcFs,StandardCharsets.UTF_8)
                .targetFolder(tgtFs)
                .options(List.of("-Xdiags:verbose","-Xlint:unchecked"))
                .build()) {
            cp.compile(className);
        }

        assertTrue( Files.exists(tgtFs.resolve(className+".class")) );

        PathClassLoader cl = new PathClassLoader(getClass().getClassLoader(), tgtFs);
        Constructor<?> ctor = cl.loadClass(className).getConstructor(JSEnvironment.class, String.class);
        JSTranspiledUnit hw = (JSTranspiledUnit)ctor.newInstance(env, unitName);
        execPhase = ExecPhase.EXECUTE;
        hw.runValue(ctx);
    }

    protected void loadShell(InterpretedGlobalRuntimeContext jsContext, Path folder) throws Exception {
    	execPhase = ExecPhase.SETUP;
    	String shellCode = readShell(folder, "");
    	if(StringUtil.isNotEmpty(shellCode)) {
			JSInterpretedUnit shellScript = jsContext.getEnvironment().createScript(shellCode,"shell.js");
			shellScript.executeWithContext(jsContext);
    	}
    }
    protected abstract String readShell(Path folder, String shell) throws Exception;

    // Flags passed to env.createScript() for the file under test - default
    // preserves every existing suite's current behavior exactly
    // (SCRIPT_ADDTOCACHE only, no SCRIPT_MODULE). A subclass whose files can
    // be ES modules (Test262BaseTest, from its own already-parsed
    // flags:[module] metadata) overrides this so the file is genuinely
    // compiled AS a module (ASTProgram.isModule()==true) - needed for a
    // module-flagged test's OWN self-import (`import {x} from
    // './this-same-file.js'`, a common test262 idiom) to register itself in
    // the module resolver's cache and truly self-reference, rather than
    // independently re-parsing and re-executing a duplicate copy of the
    // same source (see InterpretedGlobalRuntimeContext.registerRootModule()'s
    // own doc comment).
    protected int getScriptFlags(Path scriptFile) {
    	return JSEnvironment.SCRIPT_ADDTOCACHE;
    }

    protected String loadScript(Path code) throws Exception {
        if(!Files.exists(code)) {
            throw new IllegalArgumentException("File: '"+code.getFileName().toString()+"' does not exist");
        }
        //return FilesUtil.readString(code,StandardCharsets.UTF_8);
        try {
        	String text = Files.readString(code);
        	return preprocessFile(code,text);
        } catch(MalformedInputException ex) {
            try {
            	return Files.readString(code,StandardCharsets.UTF_8);
            } catch(MalformedInputException ex2) {
            	return Files.readString(code,WIN1252); // Looks like some files are encoded as windows
            }
        }
    }

    // Combines the shell/harness text with the test's own (already-
    // preprocessed) script text into the ONE text transpiled-mode compiles
    // as a single Java class. Default behavior (plain concatenation)
    // preserves this method's previous inline shape for every subclass that
    // doesn't need anything smarter. A subclass whose preprocessFile()
    // embeds a leading Directive Prologue (e.g. a "use strict" prepended for
    // a strict-mode-only test) into `script` should override this to
    // re-anchor that directive at the very front of the COMBINED text - see
    // Test262BaseTest's override for why plain concatenation silently
    // defeats it. Interpreted-mode execution (loadShell()+executeWithContext,
    // just below) doesn't need this at all, since it runs shell and script
    // as genuinely separate script units rather than one combined text.
    protected String combineShellAndScript(String shell, String script) {
        return shell + "\n\n" + script;
    }

    protected void handleException(String relativePathStr, AtomicInteger errorCount, Throwable t) {
   		t.printStackTrace();
   		errorCount.getAndIncrement();
    }

    // Called once per file, always (success or handled exception), right
    // before errorCount is finalized for this file - a no-op by default.
    protected void afterExecute(String relativePathStr, AtomicInteger errorCount) {
    }

    
    public String getPASSED() {
    	return " PASSED!";
    }
    public String getFAILED() {
    	return " FAILED!";
    }
    public String getNO_TEST_EXIST() {
    	return " NO TESTS EXIST";
    }

	protected static int MAX_DISPLAY = 10;

	private class ConsoleFilterOutputStream extends OutputStream {
		
		
		private AtomicInteger errorCount;
		private PrintStream out;
	    private StringBuilder buffer;
	
	    public ConsoleFilterOutputStream(PrintStream out, AtomicInteger errorCount) {
	    	this.out = out;
	    	this.errorCount = errorCount;
	        this.buffer = new StringBuilder();
	    }
	    
	    @Override
		public void close() throws IOException {
	    	processBuffer();
	    }
	
	    private void processBuffer() throws IOException {
	        String line = buffer.toString();
	        if(line.startsWith(getFAILED())) {
	        	if(line.contains(getNO_TEST_EXIST())) {
	        		// Ignore, example ecma_2/replace-001,js
	        		return;
	        	}
	        	errorCount.incrementAndGet();
	        	if(errorCount.get()<=MAX_DISPLAY) {
		        	out.print(ConsoleColors.PURPLE);
		        	int l = buffer.length();
		        	if(errorCount.get()<MAX_DISPLAY) {
			        	for(int i=0; i<l; i++) {
			        		out.write((int)buffer.charAt(i));
			        	}
		        	} else {
		        		out.println("    ... more");
		        	}
		        	out.print(ConsoleColors.RESET);
	        	}
	        } else if(line.startsWith(getPASSED())) {
	        	if(consoleShowPassed()) {
		        	out.print(ConsoleColors.GREEN);
		        	int l = buffer.length();
		        	for(int i=0; i<l; i++) {
		        		out.write((int)buffer.charAt(i));
		        	}
		        	out.print(ConsoleColors.RESET);
	        	}
	        } else {
	        	if(consoleVerbose()) {
		        	int l = buffer.length();
		        	for(int i=0; i<l; i++) {
		        		out.write((int)buffer.charAt(i));
		        	}
	        	}
	        }
        	out.flush();
	    }
		
	    @Override
	    public void write(int b) throws IOException {
	        char c = (char) b;
            buffer.append(c);
	        if (c == '\n') {
	            processBuffer();
	            buffer.setLength(0);
	        }
	    }
	
	    @Override
	    public void write(byte[] b, int off, int len) throws IOException {
	        for (int i = off; i < off + len; i++) {
	            write(b[i]);
	        }
	    }
	}
}
