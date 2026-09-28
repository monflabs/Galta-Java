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
package org.monflabs.galtajs.node;

import java.util.function.Function;

import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.util.StringFormat;


/**
 * import.meta (spec 16.2.1.7 ImportMeta / 13.3.12 Meta Properties) - the
 * current module's [[ImportMeta]] object. Mirrors ASTNewMember's own
 * MetaProperty pattern; kept out of PrimaryExpression() at the grammar
 * level for the same reason ImportCall is (so "new import.meta" stays a
 * SyntaxError while "import.meta.foo" works via ordinary member-access
 * chaining).
 */
public class ASTImportMeta extends ASTNode {

	public ASTImportMeta(Token t) {
		super(t);
	}

	// Spec 16.1.4/13.3.12.1 "It is a Syntax Error if the syntactic goal
	// symbol is not Module" - a STATIC property of the whole compilation
	// unit (the nearest ENCLOSING ASTProgram, regardless of how many
	// ordinary function bodies lie between - "goal-module-nested-
	// function.js" requires import.meta nested inside a function INSIDE a
	// module to stay valid), not a runtime/dynamic check. Function(),
	// GeneratorFunction(), AsyncFunction(), and AsyncGeneratorFunction()
	// each compile their body as a SEPARATE, always-non-module
	// ASTProgram/parse unit regardless of the calling context, so
	// "goal-*-params-or-body.js" (Function("import.meta") etc.) correctly
	// still reaches (and fails at) ITS OWN, non-module ASTProgram here.
	@Override
	protected void init(InitContext initContext) {
		super.init(initContext);
		ASTProgram program = findParentNodeByClass(ASTProgram.class);
		if(program==null || !program.isModule()) {
			throw RuntimeUtil.syntaxError("import.meta may only be used in a module");
		}
	}

	@Override
	public STATEMENT_TYPE getStatementType() {
		return STATEMENT_TYPE.EXPRESSION;
	}

	@Override
	public JSType getReturnedType() {
		return JSType.UNKNOWN;
	}

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			result.setValue(context.getMainContext().getScriptUnit().getImportMetaObject());
			return Signal.NONE;
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}
	}

	@Override
	public void evaluateAssign(JSInterpretedRuntimeContext context, Object rightValue, Function<Object, Object> assigner, JSResult result, Function<Object, Object> returnOriginalValue) {
		throw RuntimeUtil.syntaxError("Cannot assign to import.meta");
	}

	@Override
	public boolean evaluateDelete(JSInterpretedRuntimeContext context, JSResult result) {
		throw RuntimeUtil.syntaxError("Cannot delete import.meta");
	}

	// Mirrors evaluate()'s own `context.getMainContext().getScriptUnit().
	// getImportMetaObject()` - getMainContext() walks up to the outermost
	// runtime context regardless of how deeply nested inside ordinary
	// function bodies this reference is (same reasoning as init()'s own
	// nested-function doc comment above), getScriptUnit() gets the owning
	// module instance (a JSScriptUnit, which both JSInterpretedUnit and
	// JSTranspiledUnit extend via AbstractModule's shared
	// getImportMetaObject()), so this is the exact transpiled-codegen
	// equivalent, just expressed as a Java expression fragment instead of
	// a direct call - JSTranspiler.MAIN_CONTEXT ("_ctx") is the runtime
	// context parameter threaded through every generated method,
	// including nested function bodies.
	@Override
	public String transpileJavaExpression(JSTranspilerGeneratorContext jsContext) {
		return StringFormat.format("{0}.getMainContext().getScriptUnit().getImportMetaObject()", JSTranspiler.MAIN_CONTEXT);
	}

	@Override
	public String decompileExpression() {
		return "import.meta";
	}
}
