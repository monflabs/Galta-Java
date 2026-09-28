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

import org.monflabs.galtajs.optimizer.JSOptimizerContext;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.node.unaryop.ASTUnaryOp;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.util.StringFormat;

/**
 * ImportCall: the dynamic `import(specifier)` expression (spec 13.3.10),
 * plus the related `import(specifier, attributes)` (import-attributes
 * proposal), `import.defer(specifier)` and `import.source(specifier)`
 * (deferred/source-phase import proposals) forms.
 * Unlike the static import declaration (ASTImport, control-flow node,
 * resolved eagerly at module-instantiation time), this evaluates in
 * ordinary expression position and returns a Promise - resolved to the
 * module's namespace object, or rejected if the specifier can't be
 * resolved/loaded. Per spec, the specifier (and, if present, the
 * attributes/options expression) is evaluated and coerced SYNCHRONOUSLY (so
 * a throwing specifier/options expression's exception propagates as a real
 * synchronous throw, not a rejection - confirmed via test262
 * import-attributes/2nd-param-evaluation-abrupt-throw.js's
 * `assert.throws(..., function() { ...; import('', throwError()); ...})`),
 * but the actual module job (here, GaltaJS's own already-synchronous
 * RuntimeUtil.importModule()) is deferred to a microtask - same "wrap
 * synchronous work in a queued microtask" pattern as
 * Response.resolved()/rejected() (there is no real async I/O anywhere in
 * this engine's module resolution to await on).
 *
 * `import.defer(specifier)` resolves to the SAME cached deferred namespace
 * object (module.[[DeferredNamespace]]) a static `import defer * as ns`
 * of the same specifier would get - see RuntimeUtil.dynamicImportDefer(),
 * which reuses importDeferredNamespace(), the exact synchronous/cached
 * mechanism the static form already uses. `import.source(specifier)`
 * resolves to the module's Module Source Object without evaluating it -
 * see RuntimeUtil.dynamicImportSource() and JSGlobalContext.importModuleSource().
 */
public class ASTImportCall extends ASTUnaryOp {

	public enum ImportCallMode { NORMAL, DEFER, SOURCE }

	private final ImportCallMode mode;
	// Nullable: the optional 2nd argument to import(specifier, attributes) -
	// the import-attributes proposal's options object (e.g. {with: {type:
	// 'json'}}). Never present for DEFER/SOURCE mode (import.defer(x)/
	// import.source(x) take exactly one argument, no options).
	private ASTNode attributesNode;

	public ASTImportCall(Token t, ASTNode specifier) {
		this(t, specifier, null, ImportCallMode.NORMAL);
	}

	public ASTImportCall(Token t, ASTNode specifier, ASTNode attributesNode, ImportCallMode mode) {
		super(t, specifier);
		this.attributesNode = attributesNode!=null ? assignParent(attributesNode) : null;
		this.mode = mode;
	}

	@Override
	public int getChildCount() {
		return super.getChildCount() + (attributesNode!=null ? 1 : 0);
	}
	@Override
	public ASTNode getChild(int index) {
		if(attributesNode!=null) {
			if(index==1) {
				return attributesNode;
			}
			return super.getChild(index>1 ? index-1 : index);
		}
		return super.getChild(index);
	}
	@Override
	protected void _setChild(int index, ASTNode node) {
		if(attributesNode!=null) {
			if(index==1) {
				this.attributesNode = node;
				return;
			}
			super._setChild(index>1 ? index-1 : index, node);
			return;
		}
		super._setChild(index, node);
	}

	@Override
	public boolean isConstant(JSOptimizerContext context) {
		// Never foldable - always produces a fresh Promise/module job.
		return false;
	}

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			JSResult specResult = new JSResult();
			getNode().evaluate(context, specResult);
			String specifier;
			try {
				specifier = RuntimeUtil.toString(context.getEnvironment(), specResult.getValue());
			} catch(Throwable ex) {
				// Unlike the specifier EXPRESSION's own evaluation just
				// above (step 2-3, genuinely a synchronous throw - see
				// this class's own doc comment), ToString(specifier) is
				// spec'd to run AFTER the promise capability already
				// exists (step 4/5) - IfAbruptRejectPromise means an
				// abrupt completion here must REJECT the returned
				// promise instead of propagating synchronously (test262
				// specifier-tostring-abrupt-rejects.js and its
				// import.defer/import.source siblings - a throwing
				// `toString()` inside `await import(obj)` was otherwise
				// propagating straight out of the enclosing async
				// function's own body, silently rejecting THAT promise
				// instead, which nothing in the test observes - a "$DONE
				// never called" failure, not a wrong-value one).
				org.monflabs.galtajs.rt.builtins.standard.promise.BuiltinPromise p =
						new org.monflabs.galtajs.rt.builtins.standard.promise.BuiltinPromise(context.getEnvironment());
				p.reject(org.monflabs.galtajs.rt.JSRuntimeException.exceptionObject(ex));
				result.setValue(p);
				return Signal.NONE;
			}

			// Evaluated synchronously, same as the specifier above - a
			// throwing attributes expression must propagate as a real
			// exception from THIS call, before the promise is ever created
			// (test262 2nd-param-evaluation-abrupt-{return,throw}.js,
			// 2nd-param-evaluation-sequence.js).
			Object attributesValue = null;
			if(attributesNode!=null) {
				JSResult attrResult = new JSResult();
				attributesNode.evaluate(context, attrResult);
				attributesValue = attrResult.getValue();
			}

			result.setValue(mode==ImportCallMode.DEFER
					? RuntimeUtil.dynamicImportDefer(context, specifier)
					: mode==ImportCallMode.SOURCE
					? RuntimeUtil.dynamicImportSource(context, specifier)
					: attributesNode!=null
						? RuntimeUtil.dynamicImport(context, specifier, attributesValue)
						: RuntimeUtil.dynamicImport(context, specifier));
			return Signal.NONE;
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}
	}

	@Override
	public JSType getReturnedType() {
		return JSType.UNKNOWN;
	}

	@Override
	public String transpileJavaExpression(JSTranspilerGeneratorContext jsContext) {
		String specifierValueExpr = JSTranspiler.asValue(jsContext,getNode());
		// No-attributes and DEFER forms: ToString(specifier) happens AFTER
		// the promise capability conceptually exists (IfAbruptRejectPromise,
		// spec step 6/7), so a throwing toString() must reject the promise,
		// not propagate as a real synchronous throw - dynamicImportChecked/
		// dynamicImportDeferChecked do that coercion themselves (see their
		// own doc comments in RuntimeUtil), since generated Java code has no
		// equivalent of evaluate()'s inline try/catch around a single
		// sub-expression.
		if(mode==ImportCallMode.DEFER) {
			return StringFormat.format("RuntimeUtil.dynamicImportDeferChecked({0},{1})", JSTranspiler.MAIN_CONTEXT, specifierValueExpr);
		}
		if(mode==ImportCallMode.SOURCE) {
			return StringFormat.format("RuntimeUtil.dynamicImportSourceChecked({0},{1})", JSTranspiler.MAIN_CONTEXT, specifierValueExpr);
		}
		if(attributesNode!=null) {
			// Mirrors evaluate() above: the specifier is converted to a
			// String first (checked - an abrupt ToString here must reject
			// the promise, not propagate as a real throw, same reasoning as
			// the no-attributes branch below - test262 dynamic-import/
			// import-attributes/2nd-param-trailing-comma-reject.js and
			// siblings), then the attributes expression is evaluated purely
			// for its side effect/exception (a genuine synchronous throw,
			// left alone) - the Supplier defers that evaluation until
			// dynamicImportChecked()'s own body has already confirmed
			// ToString(specifier) succeeded, since a plain Java argument
			// would otherwise be evaluated too early (before the checked
			// ToString runs at all).
			return StringFormat.format("RuntimeUtil.dynamicImportChecked({0},{1},() -> {2})", JSTranspiler.MAIN_CONTEXT, specifierValueExpr, JSTranspiler.asValue(jsContext,attributesNode));
		}
		return StringFormat.format("RuntimeUtil.dynamicImportChecked({0},{1})", JSTranspiler.MAIN_CONTEXT, specifierValueExpr);
	}

	@Override
	public String decompileExpression() {
		String prefix = switch(mode) {
			case DEFER -> "import.defer(";
			case SOURCE -> "import.source(";
			default -> "import(";
		};
		if(attributesNode!=null) {
			return StringFormat.format("{0}{1}, {2})", prefix, getNode().decompileExpression(), attributesNode.decompileExpression());
		}
		return StringFormat.format("{0}{1})", prefix, getNode().decompileExpression());
	}

	@Override
	protected String decompileOperator() {
		return "import"; // not used - decompileExpression() is overridden above
	}
}
