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
package tests;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.monflabs.filesystem.memory.MemoryFileSystem;
import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSEnvironment.Builder;
import org.monflabs.galtajs.JSException;
import org.monflabs.galtajs.JSModule;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.node.ASTProgram;
import org.monflabs.galtajs.node.literal.ASTLiteral;
import org.monflabs.galtajs.optimizer.ScriptOptimizer;
import org.monflabs.galtajs.preprocessor.ScriptPreProcessor;
import org.monflabs.galtajs.rt.JSGlobalContext;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.standard.regexp.jdk.RegExpEngineJdkJavascript;
import org.monflabs.galtajs.rt.builtins.standard.regexp.joni.RegExpEngineJoni;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;
import org.monflabs.galtajs.rt.transpiler.JSTranspiledUnit;
import org.monflabs.galtajs.rt.transpiler.TranspiledGlobalRuntimeContext;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.JSTranspilerOptions;
import org.monflabs.galtajs.transpiler.TranspilerJavaBuilder;
import org.monflabs.galtajs.transpiler.context.TranspilerGeneratorMainContext;
import org.monflabs.javacompiler.JavaCompiler;
import org.monflabs.javacompiler.JavaCompilerFactory;
import org.monflabs.tests.__BaseTestCase;
import org.monflabs.util.Console;
import org.monflabs.util.IOStreamUtil;
import org.monflabs.util.Properties;
import org.monflabs.util.StringFormat;
import org.monflabs.util.StringUtil;
import org.monflabs.util.path.FilesUtil;
import org.monflabs.util.path.PathClassLoader;

import util.GlobalTestEnvironment;

/**
 * Abstract execution test.
 * 
 * @author Philippe Riand
 */
public abstract class BaseProjectTestCase extends __BaseTestCase {
	
	public static record ExecutionResult(JSGlobalContext context, Object value) {}; 

	public static boolean _VERBOSE = false;
	public static boolean _ALLTESTS = false;
	public static boolean _EXECUTE_OPTIMIZED = false;
	public static PrintStream _OPTIMIZER_TRACE_STREAM = null;
	public static boolean _EXECUTE_JAVATRANSPILER = false;
	public static boolean _EXECUTE_DECOMPILER = false;
	
	// Don't gegerate by default the Java class for the transpiler, unless we want to debug it
	public static boolean _TRANSPILER_SAVE_CLASS = false;
	public static boolean _INTERPRETER_SAVE_CLASS = false;

	public static final String JAVA_CLASSNAME = "TranspilerTestClass";
	
	private static final Properties EMPTY_PROPERTIES = new Properties();
	
	private JSEnvironment env;
	private Properties props = EMPTY_PROPERTIES;
	
	protected BaseProjectTestCase() {
	}
	
	protected boolean isVerbose() {
		return _VERBOSE;
	}
	protected boolean isJavaTranspiler() {
		return _EXECUTE_JAVATRANSPILER;
	}
	protected boolean isJavaDecompiler() {
		return _EXECUTE_DECOMPILER;
	}
	protected boolean isOptimized() {
		return _EXECUTE_OPTIMIZED;
	}
	protected boolean isDumpSource() {
		return isVerbose();
	}
	protected boolean isDumpDecompiledSource() {
		return isVerbose();
	}
	protected boolean isDumpAstTree() {
		return isVerbose();
	}
	
	protected boolean isTranspilerSaveClass() {
		return _TRANSPILER_SAVE_CLASS;
	}
	protected boolean isInterpreterSaveClass() {
		return _INTERPRETER_SAVE_CLASS;
	}
	
	protected Properties getProperties() {
		return props;
	}

	// Execute the JS file associated, by name, to the current class
	public ExecutionResult execute() throws Exception {
		return executeFile(null);
	}

	public ExecutionResult execute(Properties props) throws Exception {
		Properties oldProps = props;
		try {
			this.props = props;
			return executeFile(null);
		} finally {
			props = oldProps;
		}
	}

	// Execute the JS file used as a parameter
	public ExecutionResult executeFile(String fileName) throws Exception {
		String text = findSource(fileName);
		return execute(text, fileName);
	}
	
	// Execute a piece of code passed as parameter
	public Object executeCode(String text) throws Exception {
		return execute(text, "inlinecode.js").value();
	}


	//
	// Execution method
	// Uses the test mode flags (interpreted, transpiled, ...)
	//
	private ExecutionResult execute(String text, String fileName) throws Exception {
		JSEnvironment env = getEnvironment();

		if(isDumpSource()) {
			Console.outStream().println("-----------------------------------------------------------------------------------");
			Console.outStream().println(text);
			Console.outStream().println("");
		}
		String decompiled = null;
		try {
			Map<String,Object> directives = new HashMap<>();
			if(isJavaTranspiler()) {
				directives.put("INTERPRETER", true);
				directives.put("TRANSPILER", false);
			} else {
				directives.put("INTERPRETER", false);
				directives.put("TRANSPILER", true);
			}
			if(env.getRegexpEngineFactory()==RegExpEngineJdkJavascript.factory()) {
				directives.put("REGEXP_JDK", true);
				directives.put("REGEXP_JONI", false);
			} else if(env.getRegexpEngineFactory()==RegExpEngineJoni.factory()) {
				directives.put("REGEXP_JDK", false);
				directives.put("REGEXP_JONI", true);
			}
			text = ScriptPreProcessor.preprocess(text,directives);
			JSInterpretedUnit expr = env.createScript(text,fileName);
			
			if(isJavaDecompiler()) {
				// Decompile and recompile...
				decompiled = expr.getProgram().decompile();
				if(isDumpDecompiledSource()) {
					Console.outStream().println("// Decompiled source code");
					Console.outStream().println(decompiled);
					Console.outStream().println("");
				}
				expr = env.createScript(decompiled,fileName);
			}
			
			if(isJavaTranspiler()) {
				//new UnreachableCodeRemovalOptimizer(true).optimize(new JSOptimizerContext.MainOptimizerContext(env, null), expr.getProgram());
				return transpilerExecute(env,JAVA_CLASSNAME,fileName,expr);
			} else {
				return interpreterExecute(env,expr);
			}
		} catch(Exception e) {
			String path = findSourcePath(fileName);
			Console.outStream().println("-----------------------------------------------------------------------------------");
			Console.outStream().println(path);
			Console.outStream().println("");
			Console.outStream().println(JSException.extractSourceCode(new StringBuilder(),-1,text,-1,-1).toString());
			Console.outStream().println("");
			if(decompiled!=null) {
				Console.outStream().println("");
				Console.outStream().println("// Decompiled source code");
				Console.outStream().println(decompiled);
				Console.outStream().println("");
			}
			if(e instanceof JSException jse) {
				if(jse.getSourceNode()!=null) {
					//jse.getSourceNode().getTopNode().dump();
					Console.outStream().println("");
				}
				
			}
			throw e;
		}
	}

	protected String findSource(String name) throws Exception {
		if(StringUtil.isEmpty(name)) {
			name = getClass().getSimpleName()+"."+JSInterpretedUnit.DEFAULT_FILE_EXTENSION;
		} else if(name.startsWith("_")) {
			name = getClass().getSimpleName()+name+"."+JSInterpretedUnit.DEFAULT_FILE_EXTENSION;
		} else {
			name = name+"."+JSInterpretedUnit.DEFAULT_FILE_EXTENSION;
		}
		return loadSource(name);
	}
	protected String loadSource(String name) throws Exception {
		InputStream is = getClass().getResourceAsStream(name);
		if(is==null) {
			throw new IllegalStateException(MessageFormat.format("Cannot find source file {0}", name));
		}
		Reader r = new InputStreamReader(is,StandardCharsets.UTF_8);
		try {
			return IOStreamUtil.readString(r);
		} finally {
			IOStreamUtil.close(r);
		}
	}

	protected String findSourcePath(String name) throws Exception {
		// Looks like there is no way in Eclipse to highlight file in the console that is not a source file
		String ext = "java";
		//String ext = JSScript.DEFAULT_FILE_EXTENSION;
		if(StringUtil.isEmpty(name)) {
			name = getClass().getSimpleName()+"."+ext;
		} else if(name.startsWith("_")) {
			name = getClass().getSimpleName()+name+"."+ext;
		} else {
			name = name+"."+ext;
		}
		
		return "("+getClass().getName().replace('.', '/')+'/'+name+":1)";
	}


	//
	// Environment creation
	//

	public final JSEnvironment getEnvironment() {
		if(env==null) {
			JSEnvironment.Builder envBuilder = createEnvironment();
			if(isOptimized()) {
				ScriptOptimizer opt = ScriptOptimizer.newBuilder()
						.optimizers(ScriptOptimizer.DEFAULT_NODE_OPTIMIZER_NODES)
						.build();
				envBuilder.scriptOptimizer(opt);
			}
			env = envBuilder.build();
		}
		return env;
	}
	
	// We should change how this is initialized
	// The way the test does it is obsolotete
	protected JSEnvironment.Builder createEnvironment() {
		return GlobalTestEnvironment.newBuilder();
	}
	protected final JSEnvironment.Builder createEnvironment(Consumer<Builder> configurator) {
		JSEnvironment.Builder envBuilder = createEnvironment();
		if(configurator!=null) {
			envBuilder.configure(configurator);
		}
		return envBuilder;
	}
	protected Object getThis(JSEnvironment environment) {
		return RuntimeUtil.NOT_AVAILABLE;
	}

	
	//
	// Java Transpilation
	//

	public String transpileToJava(JSEnvironment env, String className, String fileName, JSInterpretedUnit script) throws Exception {
		return transpileToJava(env,className,fileName,script,null);
	}
	public String transpileModuleToJava(JSEnvironment env, String className, String fileName, JSInterpretedUnit script, String moduleName) throws Exception {
		return transpileToJava(env,className,fileName,script,moduleName);
	}

	public ExecutionResult transpilerExecute(JSEnvironment env, String className, String fileName, JSInterpretedUnit script) throws Exception {
		String javaCode = transpileToJava(env,className,fileName,script);
		//GlobalObject.log(JSException.extractSourceCode(new StringBuilder(),-1,javaCode,-1,-1).toString());
		return transpilerCompileAndExecute(className, script, javaCode, null);
	}
	
	public JSModule transpileToJavaModuleAndInit(JSEnvironment env, String className, String fileName, JSInterpretedUnit script, String moduleName) throws Exception {
		String javaCode = transpileModuleToJava(env,className,fileName,script,moduleName);
		return (JSModule)transpilerCompileAndExecute(className,script,javaCode,moduleName).value();
	}

	public ExecutionResult javaCompileAndInitModule(String className, JSInterpretedUnit script, String javaSource, String moduleName) throws Exception {
		return transpilerCompileAndExecute(className,script,javaSource,moduleName);
	}

	
	//
	// Extension points
	//
	protected void preExecute(JSGlobalContext ctx, JSInterpretedUnit expr) throws Exception {
		if(isDumpAstTree()) {
			Console.outStream().println(StringFormat.format("Mode={0}", isJavaTranspiler() ? "TRANSPILER" : "INTERPRETER"));
			expr.dump();
			Console.outStream().println("");
		}
	}
	protected void postExecute(JSGlobalContext ctx, JSInterpretedUnit expr) throws Exception {
	}
	
	
	//
	// Interpreter Execution
	//
	private ExecutionResult interpreterExecute(JSEnvironment env, JSInterpretedUnit expr) throws Exception {
		InterpretedGlobalRuntimeContext ctx = new InterpretedGlobalRuntimeContext(env,env.createProgramExecutor(),getThis(env));
		
		preExecute(ctx, expr);
		Object result = expr.executeWithContext(ctx);
		postExecute(ctx, expr);
		
		if(isVerbose()) {
			Console.outStream().println("="+ctx.getObjectString(result));
		}
		return new ExecutionResult(ctx, result);
	}

	
	//
	// Transpiler Execution
	//
	private String transpileToJava(JSEnvironment env, String className, String fileName, JSInterpretedUnit script, String moduleName) throws Exception {
		JSTranspiler transpiler = new JSTranspiler(env, getTranspilerOptions()) {
			{
				putProperty(PROP_UNITTESTS, true);
			}

			@Override
			protected void createClassHeader(TranspilerGeneratorMainContext ctx, TranspilerJavaBuilder b, String className, ASTProgram program) {
				if(fileName!=null) {
					b.comment("  {0}", fileName);
				}
				b.comment("  {0}", BaseProjectTestCase.this.getClass().getName());
			}

			// Add a main() method to debug
			@Override
			protected void createImports(TranspilerGeneratorMainContext ctx, TranspilerJavaBuilder b, String className, ASTProgram program) {
				b.println("import util.GlobalTestEnvironment;");
			}
			@Override
			protected void createConstants(TranspilerGeneratorMainContext ctx, TranspilerJavaBuilder b, String className, String resultClass, ASTProgram program) {
				if(fileName!=null) {
					b.println("  public String FILENAME = {0};", ASTLiteral.encodeString(fileName) );
				}
				b.println("  public String CLASSNAME = {0};", ASTLiteral.encodeString(BaseProjectTestCase.this.getClass().getName()));
			}
			@Override
			protected void createClassBody(TranspilerGeneratorMainContext ctx, TranspilerJavaBuilder b, String className, ASTProgram program) {
				String testClass = BaseProjectTestCase.this.getClass().getName();
				createJavaStaticMain(ctx,b,className,StringFormat.format("(new {0}()).getEnvironment()",testClass));
				//createJavaStaticMain(ctx,b,className,"GlobalTestEnvironment.create()");
			}
		};
		
		String javaCode = transpiler.compileResult(className,"Object",script.getProgram(),moduleName).getJavaCode();
		// Save it to the project for debugging purposes
		if(isTranspilerSaveClass()) {
			String newClassName = className.equals(JAVA_CLASSNAME) ? getClass().getName() : "Module_"+className;
			if(newClassName.startsWith("tests.galtajs.")) {
				newClassName = newClassName.substring("tests.galtajs.".length());
			}
			newClassName = StringUtil.replaceAll(newClassName, ".", "_");
			
			String packageName = "compiled";
			
			// Hard coded to ease the manual run and verification of tests
			if(newClassName.endsWith("SampleTestTranspiled")) {
				newClassName = "SampleTestTranspiled";
				packageName = "sample";
			}
			if(newClassName.endsWith("SampleGaltaTestTranspiled")) {
				newClassName = "SampleGaltaTestTranspiled";
				packageName = "sample";
			}
			
			File packageDir = new File(support.getTestJavaDirectory(),packageName);
			if(!packageDir.exists()) {
				packageDir.mkdirs();
			}
			File f = new File(packageDir,newClassName+".java");
			String savedCode = javaCode;
			savedCode = StringUtil.replaceFirst(savedCode, JSTranspiler.PACKAGE_COMMENT, "package "+packageName+";");
			savedCode = StringUtil.replaceFirst(savedCode, "class "+className, "class "+newClassName);
			savedCode = StringUtil.replaceFirst(savedCode, "public "+className, "public "+newClassName); // ctor 1
			savedCode = StringUtil.replaceFirst(savedCode, "public "+className, "public "+newClassName); // ctor 2
			savedCode = StringUtil.replaceFirst(savedCode, className+".class", newClassName+".class"); // super()
			savedCode = StringUtil.replaceFirst(savedCode, "JSScriptUnit o = new "+className+"(env", newClassName+" o = new "+newClassName+"(env");
			support.saveFile(f, savedCode);
		}
		
		return javaCode;
	}	
	private ExecutionResult transpilerCompileAndExecute(String className, JSInterpretedUnit script, String javaSource, String moduleName) throws Exception {
		FileSystem fs = MemoryFileSystem.newBuilder()
			.build();

		Path src = fs.getPath("src"); Files.createDirectory(src);
		Path tgt = fs.getPath("tgt"); Files.createDirectory(tgt);

		Path srcFile = src.resolve(className+".java");
		FilesUtil.writeString(srcFile, javaSource, StandardCharsets.UTF_8);

		try(JavaCompiler cp = JavaCompilerFactory.newBuilder()
				.classLoader(getClass().getClassLoader())
				.sourceFolder(src,StandardCharsets.UTF_8)
				.targetFolder(tgt)
				.options(List.of("-Xdiags:verbose","-Xlint:unchecked"))
				.build()) {
			cp.compile(className);
		}
		assertTrue(Files.exists( tgt.resolve(className+".class") ));

		PathClassLoader cl = new PathClassLoader(getClass().getClassLoader(), tgt);
		Constructor<?> ctor = cl.loadClass(className).getConstructor(JSEnvironment.class,String.class);
		
		if(StringUtil.isNotEmpty(moduleName) ) {
			// Module
			JSTranspiledUnit hw = (JSTranspiledUnit)ctor.newInstance(getEnvironment(),moduleName);
			TranspiledGlobalRuntimeContext ctx = new TranspiledGlobalRuntimeContext(getEnvironment(),env.createProgramExecutor(),hw);
			preExecute(ctx, script);
			JSTranspiledUnit unit = (JSTranspiledUnit)hw; 
			unit.initModule(ctx,false) ;
			postExecute(ctx, script);
			return new ExecutionResult(ctx, unit);
		} else {
			// Program
			JSTranspiledUnit hw = (JSTranspiledUnit)ctor.newInstance(getEnvironment(),moduleName);
			TranspiledGlobalRuntimeContext ctx = new TranspiledGlobalRuntimeContext(getEnvironment(),env.createProgramExecutor(),getThis(getEnvironment()));
			Object res = hw.runValue(ctx); 
			return new ExecutionResult(ctx, res);
		}
	}	
	
	protected JSTranspilerOptions getTranspilerOptions() {
		JSTranspilerOptions opt = JSTranspilerOptions.newBuilder()
				.debugInformation(true)
				.sourceInCode(true)
				.sourceMap(true)
				.sourceCode(true)
				.build();
		return opt;
	}

	
}
