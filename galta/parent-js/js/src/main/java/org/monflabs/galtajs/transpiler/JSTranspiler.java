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
package org.monflabs.galtajs.transpiler;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSModule;
import org.monflabs.galtajs.StaticConfiguration;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.ASTProgram;
import org.monflabs.galtajs.node.ASTVarContainer.VariableDef;
import org.monflabs.galtajs.node.literal.ASTLiteral;
import org.monflabs.galtajs.rt.transpiler.JSTranspiledRuntimeContext;
import org.monflabs.galtajs.rt.transpiler.JSTranspiledUnit;
import org.monflabs.galtajs.rt.transpiler.JSTranspilerMap;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.transpiler.context.TranspilerCodeSplitter;
import org.monflabs.galtajs.transpiler.context.TranspilerGeneratorMainContext;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.util.StringFormat;
import org.monflabs.util.StringUtil;

/**
 * GaltaJS Transpiler.
 */
public class JSTranspiler {
	
	public static final class Result {
		private String javaScriptCode;
		private String javaCode;
		private JSTranspilerMap transpilerMap;
		private Result(String javaScriptCode, String javaCode, JSTranspilerMap transpilerMap) {
			this.javaScriptCode = javaScriptCode;
			this.javaCode = javaCode;
			this.transpilerMap = transpilerMap;
		}
		public String getJavaScriptCode() {
			return javaScriptCode;
		}
		public String getJavaCode() {
			return javaCode;
		}
		public JSTranspilerMap getTranspilerMap() {
			return transpilerMap;
		}
	}

	public static String MAIN_FUNCTION_IMPL = "_runValue";
	
	public static String MAIN_CONTEXT = "_ctx";
	public static String MAIN_ENVIRONMENT = "env";
	public static String FUNCTION_ARGUMENTS = "_args";
	public static String EXCEPTION_VAR = "_ex";
	public static String THIS_VAR = "_this";
	public static String CURRENT_VALUE = "_value"; // Filter current value in JsonPath
	public static String TEMP_VAR = "tmp.v";

	public static String PACKAGE_COMMENT = "// package XXX;";

	public static String PROP_UNITTESTS = "com.monflabs.unittests";

	public static String MODULE_INIT_ITEMS = "initModuleItems";
	
	
	private JSEnvironment env;
	private JSTranspilerOptions options;
	private Map<String,Object> properties = new HashMap<>();

	public JSTranspiler(JSEnvironment env, JSTranspilerOptions options) {
		this.env = env;
		this.options = options;
	}
	
	public JSEnvironment getEnvironment() {
		return env;
	}
	
	public JSTranspilerOptions getTranspilerOptions() {
		return options;
	}
	
	@SuppressWarnings("unchecked")
	public <T> T getProperty(String key) {
		return (T)properties.get(key);
	}
	
	@SuppressWarnings("unchecked")
	public <T> T getProperty(String key, T defaultValue) {
		if(!properties.containsKey(key)) {
			return defaultValue;
		}
		return (T)properties.get(key);
	}
	public void putProperty(String key, Object value) {
		properties.put(key,value);
	}
	
	
	public String compile(String fullClassName, String resultClass, JSInterpretedUnit script) {
		// Was hardcoded null - discarded script's own already-populated real
		// descriptor name for every caller (all test infrastructure - see
		// docs/GaltaJS/TranspiledModuleLiveBindingsDesignBrief.md's P4 own
		// prerequisite note), leaving jsContext.getModuleName() always null
		// for a test262-compiled module root. Needed for ASTProgram's own
		// self-reference detection (a re-export/import resolving to THIS
		// SAME module, by comparing ModuleUtil.resolvePath(moduleName,...)
		// against moduleName itself).
		if(script.getProgram().isModule() && env!=null) {
			// Link the module graph (resolve every import and re-export)
			// before any of it can run - see StaticModuleLinker
			org.monflabs.galtajs.modules.StaticModuleLinker.link(env, script.getDescriptor().getName(), script.getProgram());
		}
		return compileResult(fullClassName,resultClass,script.getProgram(),script.getDescriptor().getName()).getJavaCode();
	}
	
	public String compileModule(String fullClassName, JSInterpretedUnit script, String moduleName) {
		return compileResult(fullClassName,"Object",script.getProgram(),moduleName).getJavaCode();
	}

	public Result compileResult(String fullClassName, String resultClass, ASTProgram program, String moduleName) {
		// Should we run that systematically, with the risk of running it towice?
		//new UnreachableCodeRemovalOptimizer(true).optimize(new JSOptimizerContext.MainOptimizerContext(env, null), program);
		TranspilerGeneratorMainContext ctx = new TranspilerGeneratorMainContext(this, program.getSourceCode(),moduleName);
		return ctx.with( () -> {
			TranspilerJavaBuilder b = new TranspilerJavaBuilder(ctx);
			
			String packageName = null;
			String className = fullClassName;
			
			int pos = className.lastIndexOf('.');
			if(pos>=0) {
				packageName = fullClassName.substring(0,pos);
				className = fullClassName.substring(pos+1);
			}
			
			b.println("//");
			b.println("// Transpiled with GaltaJS Transpiler");
			b.println("// (c) 2018-2026 Monflabs");
			b.println("//");
			b.println("// Unit: {0}", commentSafe(moduleName));
			b.println("//");
			b.println();
			if(StringUtil.isNotEmpty(packageName)) {
				b.println("package {0};",packageName);
			} else {
				b.println("{0}",PACKAGE_COMMENT); // Could be replaced later if the package needs to be changed
			}
			b.println();
			b.println("import java.util.*;");
			b.println("import java.util.function.*;");
			b.println("import org.monflabs.galtajs.jsonfactory.*;");
			b.println("import java.math.*;");
			b.println("import org.monflabs.galtajs.modules.*;");
			b.println("import org.monflabs.galtajs.*;");
			b.println("import org.monflabs.galtajs.rt.*;");
			b.println("import org.monflabs.galtajs.rt.builtins.*;");
			b.println("import org.monflabs.galtajs.rt.builtins.standard.regexp.*;");
			b.println("import org.monflabs.galtajs.rt.builtins.standard.function.*;");
			b.println("import org.monflabs.galtajs.rt.builtins.standard.arguments.Arguments;");
			b.println("import org.monflabs.galtajs.rt.transpiler.*;");
			b.println("import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext.VAR_TYPE;");
			b.println("import org.monflabs.util.generators.*;");
			
			b.println("import static org.monflabs.galtajs.rt.RuntimeUtil.*;");
			
			createImports(ctx, b, className, program);
			b.println();
			
			createSourceComment(ctx, b, className, program);
			
			createClassHeader(ctx, b, className, program);

			b.println("@SuppressWarnings(\"unused\") // For unused imports");
			b.println("public class {0} extends {1}{2} {", 
					className,
					baseClass(ctx),
					implementsString(ctx, className, program));
			b.incIndent();
			createConstants(ctx, b, className, resultClass, program);
			b.println();
			b.println("public {0}(JSEnvironment env) {", className);
			b.incIndent();
			b.println("this(env,JSModule.DEFAULT_PROGRAM_NAME);");
			b.decIndent();
			b.println("}");
			b.println("public {0}(JSEnvironment env, String moduleName) {", className);
			b.incIndent();
			b.println("super(env,new Descriptor(moduleName));");
			b.decIndent();
			b.println("}");
			b.println("");

			createStrictModeOption(ctx, b, className, resultClass, program);
			createCommonJSOption(ctx, b, className, resultClass, program);
			createAsyncOption(ctx, b, className, resultClass, program);
			createIsModuleOption(ctx, b, className, resultClass, program);
			createCustomOptions(ctx, b, className, resultClass, program);
			createMainFunction(ctx, b, className, resultClass, program);
			createClassBody(ctx, b, className, program);
			
			createConstantPool(ctx,b);

			// Could be in a derived class...
			createTranspilerMap(ctx,b);
			b.println();
			createSourceCode(ctx,b);
			
			b.decIndent();
			b.println("}");

			String javaCode = b.toString();
			return new Result(ctx.getSourceCode(), javaCode, ctx.getTranspilerMap());
			
		});
	}

	private String implementsString(TranspilerGeneratorMainContext ctx, String className, ASTProgram program) {
		String[] impl = classImplements(ctx, className, program);
		if (impl != null && impl.length > 0) {
			StringBuilder b = new StringBuilder(64);
			b.append(" implements ");
			for (int i = 0; i < impl.length; i++) {
				if(i>0) {
					b.append(", ");
				}
				b.append(impl[i]);
			}
			return b.toString();
		}
		return "";
	}
	
	protected String baseClass(TranspilerGeneratorMainContext ctx) {
		return JSTranspiledUnit.class.getSimpleName();
	}

	protected String[] classImplements(TranspilerGeneratorMainContext ctx, String className, ASTProgram program) {
		return null;
	}

	protected void createClassHeader(TranspilerGeneratorMainContext ctx, TranspilerJavaBuilder b, String className, ASTProgram program) {
	}

	protected void createSourceComment(TranspilerGeneratorMainContext ctx, TranspilerJavaBuilder b, String className, ASTProgram program) {
		if(!options.isSourceInComments()) {
			return;
		}
		int max = options.getMaxSourceInComments();
		String source = StringUtil.normalizeLineBreaks(program.getSourceCode());
		String[] lines = StringUtil.splitString(source,'\n');
		for(int i=0; i<lines.length && i<max; i++) {
			b.print("// ");
			b.print(StringUtil.padLeft(Integer.toString(i),4,' '));
			b.print(": ");
			b.println(commentSafe(lines[i]));
		}
	}

	/**
	 * Text placed in a // comment: javac processes unicode escapes everywhere,
	 * comments included, so a JS string holding a backslash-u escape for a line
	 * feed would end the comment early, and a JS-only form (backslash-u followed
	 * by braces) would fail the compilation. A backslash is written as the unicode
	 * escape of a backslash, which javac does not process a second time.
	 */
	public static String commentSafe(String text) {
		if(text==null) {
			return null;
		}
		return text.replace("\\", "\\u005c");
	}
	
	protected void createImports(TranspilerGeneratorMainContext ctx, TranspilerJavaBuilder b, String className, ASTProgram program) {
	}

	public static void createConstantPool(JSTranspilerGeneratorContext ctx, TranspilerJavaBuilder b) {
		if(!ctx.getConstantPool().isEmpty()) {
			b.println("// Begin constant pool");
			for(Map.Entry<Object,String> e: ctx.getConstantPool().entrySet()) {
				String constantName = e.getValue();
				Object constantValue = e.getKey();
				// Unconditional, like ASTClassDecl.transpileClassElementLoop's
				// identical own use of a TranspilerCodeSplitter constant
				// directly (not gated on JSTranspilerOptions.getCodeSplitter(),
				// which is opt-in and NOT enabled by the test harness) - this
				// isn't a performance/size optimization to be toggled, it's
				// needed for correctness (avoiding a hard javac "code too
				// large" compile error) any time it applies.
				if(constantValue instanceof String[] a && a.length>TranspilerCodeSplitter.DEFAULT_ARRAY_SPLIT_MAX) {
					createSplitStringArrayConstant(b, constantName, a, TranspilerCodeSplitter.DEFAULT_ARRAY_SPLIT_MAX);
				} else {
					b.println("private static final {0} {1}={2};", constantType(constantValue), constantName, ASTLiteral.encodeJavaLiteral(constantValue));
				}
			}
			b.println("// End constant pool");
			b.println("");
		}
	}

	// A single flat `new String[] {...}` literal for a very large array (e.g.
	// ~8000 entries - language/identifiers/start-unicode-10.0.0.js's
	// top-level var-name list, ASTVarContainer.transpilerDeclareStatement's
	// initGlobalVariables(...) call) can alone approach/exceed the JVM's
	// 64KB per-method bytecode limit once combined with everything else
	// already emitted inside <clinit>. Unlike ASTLiteral.encodeJavaLiteral's
	// large-String handling (largeString(...), which merely splits one
	// expression into several smaller ones STILL inside the same <clinit>
	// method - fine for a String, since a chunk of char data costs far less
	// bytecode per byte than an array-literal element does), splitting an
	// array literal into several sub-expressions of the SAME enclosing
	// expression would not reduce <clinit>'s own bytecode size at all. Each
	// chunk is instead built in its OWN private static method (well under
	// the 64KB limit given a small enough chunk size), and the field is
	// initialized by merging the chunk methods' results at class-init time
	// (JSTranspiledUnit.mergeStringArrays).
	private static void createSplitStringArrayConstant(TranspilerJavaBuilder b, String constantName, String[] a, int chunkMax) {
		int chunkCount = 0;
		int i = 0;
		while(i<a.length) {
			int len = Math.min(a.length-i, chunkMax);
			b.println("private static String[] {0}_c{1}() {", constantName, chunkCount);
			b.incIndent();
			b.print("return new String[] {");
			for(int j=0; j<len; j++) {
				if(j>0) {
					b.append(",");
				}
				b.append(ASTLiteral.encodeString(a[i+j]));
			}
			b.println("};");
			b.decIndent();
			b.println("}");
			i += len;
			chunkCount++;
		}
		StringBuilder call = new StringBuilder("mergeStringArrays(");
		for(int c=0; c<chunkCount; c++) {
			if(c>0) {
				call.append(",");
			}
			call.append(constantName).append("_c").append(c).append("()");
		}
		call.append(")");
		b.println("private static final String[] {0}={1};", constantName, call.toString());
	}
	protected static String constantType(Object value) {
		if(value instanceof Boolean) {
			return "Boolean";
		} else if(value instanceof Byte) {
			return "Byte";
		} else if(value instanceof Short) {
			return "Short";
		} else if(value instanceof Integer) {
			return "Integer";
		} else if(value instanceof Long) {
			return "Long";
		} else if(value instanceof Float) {
			return "Float";
		} else if(value instanceof Double) {
			return "Double";
		} else if(value instanceof BigInteger) {
			return "BigInteger";
		} else if(value instanceof BigDecimal) {
			return "BigDecimal";
		} else if(value instanceof CharSequence) {
			return "String";
		} else if(value instanceof String[]) {
			return "String[]";
		} else if(value instanceof boolean[]) {
			return "boolean[]";
		}
		return "Object";
	}
	
	
	
	protected boolean isForceStrictMode() {
		return false;
	}

	protected void createConstants(TranspilerGeneratorMainContext ctx, TranspilerJavaBuilder b, String className, String resultClass, ASTProgram program) {
	}
	protected void createStrictModeOption(TranspilerGeneratorMainContext ctx, TranspilerJavaBuilder b, String className, String resultClass, ASTProgram program) {
		if(program.isForceStrictMode()) {
			b.println("@Override");
			b.println("public boolean isForceStrictMode() {");
			b.incIndent();
			b.println("return true;");
			b.decIndent();
			b.println("}");
		}
	}
	protected void createCommonJSOption(TranspilerGeneratorMainContext ctx, TranspilerJavaBuilder b, String className, String resultClass, ASTProgram program) {
		if(program.isCommonJS()) {
			b.println("@Override");
			b.println("public boolean isCommonJS() {");
			b.incIndent();
			b.println("return true;");
			b.decIndent();
			b.println("}");
		}
	}
	protected void createAsyncOption(TranspilerGeneratorMainContext ctx, TranspilerJavaBuilder b, String className, String resultClass, ASTProgram program) {
		if(program.isAsyncExecution()) {
			b.println("@Override");
			b.println("public boolean isAsyncExecution() {");
			b.incIndent();
			b.println("return true;");
			b.decIndent();
			b.println("}");
		}
	}
	protected void createCustomOptions(TranspilerGeneratorMainContext ctx, TranspilerJavaBuilder b, String className, String resultClass, ASTProgram program) {
	}
	// Bakes ASTProgram.isModule() as a literal override, same pattern as
	// isForceStrictMode()/isCommonJS()/isAsyncExecution() above - lets
	// JSTranspiledUnit.runValue() (a plain script/module-agnostic entry
	// point, unlike JSInterpretedUnit's own program.isModule() field check)
	// tell the two apart at runtime, needed for registerRootModule() - see
	// that call site's own doc comment.
	protected void createIsModuleOption(TranspilerGeneratorMainContext ctx, TranspilerJavaBuilder b, String className, String resultClass, ASTProgram program) {
		if(program.isModule()) {
			b.println("@Override");
			b.println("public boolean isModuleUnit() {");
			b.incIndent();
			b.println("return true;");
			b.decIndent();
			b.println("}");
		}
	}
	protected void createMainFunction(TranspilerGeneratorMainContext ctx, TranspilerJavaBuilder b, String className, String resultClass, ASTProgram program) {
		b.println("@Override");
		b.println("protected void {1}({2} {3}) {", resultClass, MAIN_FUNCTION_IMPL, JSTranspiledRuntimeContext.class.getSimpleName(), MAIN_CONTEXT);

		b.incIndent();
		//b.println("final var {0}={1};",MAIN_CONTEXT,GLOBAL_CONTEXT);
		if(StaticConfiguration.TRANSPILER_NO_CLOSURE) {
			b.println("final var tmp=new TempVar();",TEMP_VAR);
		}
		b.println("final var {0}={1}.getThis();",THIS_VAR,MAIN_CONTEXT);

		compileMainNode(ctx, b, className, program);

		b.decIndent();
		b.println("}");
	}

	protected void compileMainNode(TranspilerGeneratorMainContext ctx, TranspilerJavaBuilder b, String className, ASTProgram program) {
		program.transpileJavaStatement(ctx, b);
	}

	protected void createClassBody(TranspilerGeneratorMainContext ctx,TranspilerJavaBuilder b,  String className, ASTProgram program) {
	}

	protected void createJavaStaticMain(TranspilerGeneratorMainContext ctx, TranspilerJavaBuilder b, String className, String envAccessor) {
		b.println();
		b.println("public static void main(String[] args) {");
		b.incIndent();
		b.println("try {");
		b.incIndent();
		b.println("{0} env = {1};", JSEnvironment.class.getSimpleName(), envAccessor);
		b.println("JSScriptUnit o = new {0}(env,\"{1}\");", className, JSModule.DEFAULT_EXPRESSION_NAME);
		b.println("o.execute();");
		b.decIndent();
		b.println("} catch(Throwable t) {");
		b.incIndent();
		b.println("t.printStackTrace();");
		b.decIndent();
		b.println("}");
		b.decIndent();
		b.println("}");
		b.println();
	}

	protected void createTranspilerMap(TranspilerGeneratorMainContext ctx, TranspilerJavaBuilder b) {
		if(!options.isSourceMap()) {
			return;
		}
		b.println("@Override");
		b.println("protected JSTranspilerMap getTranspilerMap() {");
		b.incIndent();
		b.println("return TranspilerMap.MAP;");
		b.decIndent();
		b.println("};");	

		b.println("private static class TranspilerMap {");
		b.incIndent();
		b.println("static JSTranspilerMap MAP = JSTranspilerMap.deserialize({0});",ASTLiteral.encodeJavaLiteral(ctx.getTranspilerMap().serialize()));
		b.decIndent();
		b.println("};");	
	}

	protected void createSourceCode(TranspilerGeneratorMainContext ctx, TranspilerJavaBuilder b) {
		if(!options.isSourceCode()) {
			return;
		}
		b.println("@Override");
		b.println("protected String getSourceCode() {");
		b.incIndent();
		b.println("return SourceCode.CODE;");
		b.decIndent();
		b.println("};");	

		b.println("private static class SourceCode {");
		b.incIndent();
		b.println("static String CODE = {0};",ASTLiteral.encodeJavaLiteral(ctx.getSourceCode()));
		b.decIndent();
		b.println("};");	
	}

	//
	// Helpers that could use optimized types in the future
	//

	public static String asRawValue(JSTranspilerGeneratorContext ctx, ASTNode node) {
		return StringFormat.format("{0}", node.transpileJavaExpression(ctx));
	}

	public static String asResult(JSTranspilerGeneratorContext ctx, ASTNode node) {
		if(node.isSequence()) {
			// Already a sequence
			return StringFormat.format("{0}", node.transpileJavaExpression(ctx));
		}
		return StringFormat.format("asResult({0})", node.transpileJavaExpression(ctx));
	}
	public static String asResult(JSTranspilerGeneratorContext ctx, int valueId, ASTNode node) {
		return ctx.wrapValue(valueId, () -> {
			if(node.isSequence()) {
				// Already a sequence
				return StringFormat.format("{0}", node.transpileJavaExpression(ctx));
			}
			return StringFormat.format("asResult({0})", node.transpileJavaExpression(ctx));
		});
	}

	// The Java expression that yields the current `this` binding at a given
	// AST position. Everywhere EXCEPT a derived class constructor this is just
	// the immutable `_this` method parameter. Inside a derived constructor the
	// local `_this` is NOT a stable source: ASTSuperCtor emits `_this = superCtor(...)`,
	// reassigning that parameter, which (a) makes it non-effectively-final, so
	// any lambda in the same method that captures it - e.g. the executeIsolated/
	// resolveMethodTarget closures ASTCall emits for a plain `this.method()` -
	// fails to compile, and (b) is anyway not updated when super() is reached
	// through a nested arrow (RuntimeUtil.superCtor() walks up past arrow
	// contexts and calls setThis() on the constructor's JSFunctionContext, never
	// touching this outer method's local). Sourcing every read from
	// `_ctx.getThis()` instead - the single authoritative binding superCtor()
	// writes - keeps `_this` effectively final AND correct for the arrow-reached
	// super() case. checkThisBinding()/checkDerivedConstructorReturn() still gate
	// the TDZ (pre-super) read at their own call sites, exactly as before.
	public static String thisRef(ASTNode callerNode) {
		if(org.monflabs.galtajs.rt.builtins.standard.StandardLibrary.isCallerInDerivedClassConstructor(callerNode)) {
			return StringFormat.format("{0}.getThis()", MAIN_CONTEXT);
		}
		return THIS_VAR;
	}

	public static String asValue(JSTranspilerGeneratorContext ctx, Object nodeOrValue) {
		if(nodeOrValue instanceof ASTNode node) {
			return asValue(ctx,node);
		}
		return ASTLiteral.encodeLiteral(ctx,nodeOrValue);
	}
	public static String asValue(JSTranspilerGeneratorContext ctx, ASTNode node) {
		if(node.isSequence()) {
			return StringFormat.format("deref({0})", node.transpileJavaExpression(ctx));
		} else {
			return StringFormat.format("{0}", node.transpileJavaExpression(ctx));
		}
	}
	public static String asValue(JSTranspilerGeneratorContext ctx, int valueId, ASTNode node) {
		return ctx.wrapValue(valueId, () -> {
			if(node.isSequence()) {
				return StringFormat.format("deref({0})", node.transpileJavaExpression(ctx));
			} else {
				return StringFormat.format("{0}", node.transpileJavaExpression(ctx));
			}
		});
	}

	public static String asBoolean(JSTranspilerGeneratorContext ctx, ASTNode node) {
		if(node.isSequence()) {
			return StringFormat.format("toBoolean(deref({0}))", node.transpileJavaExpression(ctx));
		} else {
			// Optimization - we don't do it for sequences right now
			JSType type = node.getReturnedType();
			if(type==JSType.BOOLEAN) {
				return StringFormat.format("{0}", node.transpileJavaExpression(ctx));
			} else {
				return StringFormat.format("toBoolean({0})", node.transpileJavaExpression(ctx));
			}
		}
	}

	// Same as asBoolean(), but for use as an if/while/for-test condition:
	// a literal `false` test (e.g. `while (false) x;`, `if (false) x;`) is a
	// JLS compile-time constant expression when emitted as the raw Java
	// literal `false`, so javac's static reachability analysis (JLS 14.21)
	// flags the guarded statement as an "unreachable statement" compile
	// error, even though it's perfectly valid (if dead) JS.
	// Boolean.FALSE.booleanValue() is NOT a constant expression (only
	// primitive/String literals and certain operators on them are), so it
	// defeats that analysis while being semantically identical.
	public static String asBooleanCondition(JSTranspilerGeneratorContext ctx, ASTNode node) {
		String test = asBoolean(ctx, node);
		return test.equals("false") ? "Boolean.FALSE.booleanValue()" : test;
	}

	// fn(value), the value of a sequence being dereferenced first
	private static String convert(String fn, JSTranspilerGeneratorContext ctx, ASTNode node) {
		String value = node.transpileJavaExpression(ctx);
		return node.isSequence() ? fn+"(deref("+value+"))" : fn+"("+value+")";
	}

	public static String asNumber(JSTranspilerGeneratorContext ctx, ASTNode node) {
		return convert("toNumber", ctx, node);
	}

	public static String asString(JSTranspilerGeneratorContext ctx, ASTNode node) {
		return convert("toString", ctx, node);
	}
	public static String asString(JSTranspilerGeneratorContext ctx, ASTNode node, boolean nulls) {
		String value = node.transpileJavaExpression(ctx);
		return StringFormat.format(node.isSequence() ? "toString(deref({0}),{1})" : "toString({0},{1})", value, nulls);
	}

	public static String asInt32(JSTranspilerGeneratorContext ctx, ASTNode node) {
		return convert("toInt32", ctx, node);
	}

	public static String asCallable(JSTranspilerGeneratorContext ctx, ASTNode node) {
		return convert("toCallable", ctx, node);
	}

	public static String asFunction(JSTranspilerGeneratorContext ctx, ASTNode node) {
		return convert("toFunction", ctx, node);
	}

	// Wider than asFunction()'s toFunction() (BuiltinFunction) - NamedEvaluation
	// (litFunction()'s own JSObjectImpl signature) also applies to an anonymous
	// CLASS expression value (test262 fn-name-class.js: `{id: class {}}`), and
	// BuiltinClassConstructor is a BaseCallableObject but not a BuiltinFunction.
	public static String asBaseCallableObject(JSTranspilerGeneratorContext ctx, ASTNode node) {
		return convert("toBaseCallableObject", ctx, node);
	}

	public static String asVar(JSTranspilerGeneratorContext ctx, VariableDef var) {
		return var.getJavaVariableValue();
	}

	public static String literal(Object value) {
		return ASTLiteral.encodeJavaLiteral(value);
	}

	// javac resolves unicode escapes on the raw source text before tokenization, even
	// inside comments and string literals, and always requires exactly 4 hex digits
	// after every backslash-then-u - doubling the backslash does not help, since the
	// second backslash is itself immediately followed by a u. Any generated source
	// text that echoes arbitrary JS text verbatim (a raw source comment, or a JS
	// string whose raw content contains such a sequence) must first neutralize it by
	// encoding just the backslash via its own (already-valid, 4-hex-digit) unicode
	// escape, which resolves to a literal backslash without re-triggering the rule.
	private static final String BACKSLASH_UNICODE_ESCAPE = "\\" + "u005c";
	public static String escapeUnicodeMarkerForJavac(String s) {
		if(s.indexOf("\\u")<0) {
			return s;
		}
		StringBuilder b = new StringBuilder(s.length()+8);
		for(int i=0; i<s.length(); i++) {
			char c = s.charAt(i);
			if(c=='\\' && i+1<s.length() && s.charAt(i+1)=='u') {
				b.append(BACKSLASH_UNICODE_ESCAPE);
			} else {
				b.append(c);
			}
		}
		return b.toString();
	}


	/**
	 * Default conversion from a module name to a Java class name.
	 * <p>
	 * A trailing {@code .js} is dropped (the extension is optional in module
	 * names, so "a" and "a.js" are the same module), the path separators
	 * ({@code /} or {@code \\}) become package separators and the last segment
	 * is the class name. The encoding is otherwise injective, so two distinct
	 * module names never share a class (the class name is also the cache key
	 * of the compiled modules):
	 * <ul>
	 * <li>ASCII letters and digits are kept as is, except the first character
	 * of the class name: a lowercase letter is capitalized ("ab" -&gt; "Ab")</li>
	 * <li>{@code _} is the escape character: {@code __} is '_', {@code _d} is
	 * '-', {@code _o} is '.', {@code _s} is a space, {@code _xHHHH} is any
	 * other UTF-16 char, {@code _e} is an empty segment</li>
	 * <li>{@code _} followed by an uppercase letter or a digit is that
	 * character, kept literally where it would otherwise be ambiguous or
	 * invalid: an uppercase first class character ("Ab" -&gt; "_Ab") or a
	 * leading digit ("1a" -&gt; "_1a")</li>
	 * <li>a package segment that is a Java keyword has its first character
	 * escaped</li>
	 * </ul>
	 */
	public static String moduleNameToJavaClassName(String basePackage, String moduleName) {
		if(moduleName.endsWith(".js")) {
			moduleName = moduleName.substring(0, moduleName.length()-3);
		}

		StringBuilder b = new StringBuilder(moduleName.length()+16);
		if(StringUtil.isNotEmpty(basePackage)) {
			b.append(basePackage);
		}
		if(moduleName.isEmpty()) {
			return b.toString();
		}

		int len = moduleName.length();
		int start = 0;
		while(start<=len) {
			int end = start;
			while(end<len && moduleName.charAt(end)!='/' && moduleName.charAt(end)!='\\') {
				end++;
			}
			if(!b.isEmpty()) {
				b.append('.');
			}
			encodeModuleNameSegment(b, moduleName.substring(start, end), end>=len);
			start = end+1;
		}
		return b.toString();
	}
	private static void encodeModuleNameSegment(StringBuilder b, String segment, boolean className) {
		if(segment.isEmpty()) {
			b.append("_e");
			return;
		}
		boolean keyword = !className && javax.lang.model.SourceVersion.isKeyword(segment);
		for(int i=0; i<segment.length(); i++) {
			char c = segment.charAt(i);
			boolean first = i==0;
			if(c>='a' && c<='z') {
				if(first && className) {
					b.append((char)(c-'a'+'A'));
				} else if(first && keyword) {
					appendHexEscape(b, c);
				} else {
					b.append(c);
				}
			} else if(c>='A' && c<='Z') {
				if(first && className) {
					b.append('_');
				}
				b.append(c);
			} else if(c>='0' && c<='9') {
				if(first) {
					b.append('_');
				}
				b.append(c);
			} else if(c=='_') {
				b.append("__");
			} else if(c=='-') {
				b.append("_d");
			} else if(c=='.') {
				b.append("_o");
			} else if(c==' ') {
				b.append("_s");
			} else {
				appendHexEscape(b, c);
			}
		}
	}
	private static void appendHexEscape(StringBuilder b, char c) {
		b.append("_x");
		String hex = Integer.toHexString(c);
		for(int k=hex.length(); k<4; k++) {
			b.append('0');
		}
		b.append(hex);
	}

}