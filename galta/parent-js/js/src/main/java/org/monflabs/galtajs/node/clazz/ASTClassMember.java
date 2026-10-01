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
package org.monflabs.galtajs.node.clazz;

import java.util.Collections;
import java.util.List;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.rt.builtins.standard.function.BuiltinClassConstructor;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.TranspilerJavaBuilder;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.util.JavaBuilder;
import org.monflabs.util.StringFormat;


/**
 * Class member.
 */
public abstract class ASTClassMember extends ASTNode {
	
	private String name;
	private ASTNode nameNode;
	private boolean isStatic;
	private boolean isPrivate;
	private ASTNode[] decorators;

	public ASTClassMember(Token t, Object name, boolean isStatic, boolean isPrivate) {
		this(t,name,isStatic,isPrivate,Collections.emptyList());
	}
	public ASTClassMember(Token t, Object name, boolean isStatic, boolean isPrivate, List<ASTNode> decorators) {
		super(t);
		if(name instanceof ASTNode n) {
			this.nameNode = assignParent(n);
		} else if(name instanceof String) {
			this.name = (String)name;
		} else {
			throw new IllegalArgumentException("Invalid name type: " + name.getClass().getName());
		}
		this.isStatic = isStatic;
		this.isPrivate = isPrivate;
		this.decorators = assignParent(decorators);
	}

	public String getName() {
		return name;
	}
	public ASTNode getNameNode() {
		return nameNode;
	}
	public ASTNode[] getDecorators() {
		return decorators;
	}

	// Shared child-slot base every subclass's own getChildCount()/getChild()/
	// _setChild() delegates to (via super.getChild(index-1), etc., after
	// accounting for its own extra child slots) - keeps decorator expressions
	// part of the AST tree (init()-time identifier resolution, private-name
	// reference checks like "@C.#$", yield/await handling) without every
	// subclass needing its own copy of this bookkeeping.
	// A computed key ([expr]) is a child as well, so identifier resolution,
	// "arguments"/parameter use tracking and private-name checks see it.
	private int ownChildCount() {
		return decorators.length+(nameNode!=null?1:0);
	}
	@Override
	public int getChildCount() {
		return super.getChildCount()+ownChildCount();
	}
	@Override
	public ASTNode getChild(int index) {
		if(index<decorators.length) {
			return decorators[index];
		}
		if(nameNode!=null && index==decorators.length) {
			return nameNode;
		}
		return super.getChild(index-ownChildCount());
	}
	@Override
	protected void _setChild(int index, ASTNode node) {
		if(index<decorators.length) {
			decorators[index] = node;
			return;
		}
		if(nameNode!=null && index==decorators.length) {
			nameNode = node;
			return;
		}
		super._setChild(index-ownChildCount(), node);
	}

	// Applies this member's (or, via ASTClassDecl, a class's own) decorator
	// list, in listed order, to currentValue - a deliberately simplified but
	// real subset of the 2023 decorators proposal's semantics (no `access`/
	// `metadata`, addInitializer() is a working no-op-registering stub): each
	// decorator is called with (currentValue, context) where context exposes
	// {kind, name, static, private, addInitializer}; if a decorator returns a
	// callable value, it REPLACES currentValue for the next decorator (and as
	// the final result) - matching the common real-world "wrap the value"
	// decorator pattern. A decorator returning anything else (including
	// undefined, the common case for a decorator that only calls
	// addInitializer or has side effects) leaves currentValue unchanged.
	protected static Object applyDecorators(JSInterpretedRuntimeContext context, ASTNode[] decorators, Object currentValue, String kind, Object name, boolean isStatic, boolean isPrivate) {
		if(decorators.length==0) {
			return currentValue;
		}
		JSEnvironment env = context.getEnvironment();
		for(ASTNode d: decorators) {
			Object decoratorFn = d.evaluateValue(context, new JSResult());
			JSObject ctx = JSObject.create(env);
			ctx.setOwnProperty("kind", kind);
			// A private member's name is its description ("#x")
			Object ctxName = name instanceof Symbol ? name
					: name instanceof org.monflabs.galtajs.rt.builtins.privatename.PrivateName pn ? pn.getDescription()
					: RuntimeUtil.toString(env, name);
			ctx.setOwnProperty("name", ctxName);
			ctx.setOwnProperty("static", isStatic);
			ctx.setOwnProperty("private", isPrivate);
			ctx.setOwnProperty("addInitializer", new BaseMethod(env, "addInitializer", 1) {
				@Override
				protected Object invoke(Object obj, Object[] args) {
					if(args.length==0 || !(args[0] instanceof Callable)) {
						throw RuntimeUtil.typeError("addInitializer argument must be a function");
					}
					return RuntimeUtil.UNDEFINED;
				}
			});
			Object result = RuntimeUtil.call(env, decoratorFn, RuntimeUtil.UNDEFINED, new Object[]{currentValue, ctx});
			if(result instanceof Callable) {
				currentValue = result;
			}
		}
		return currentValue;
	}
	
	public Object evaluateName(JSInterpretedRuntimeContext context) {
		Object result;
		if(nameNode != null) {
			Object nameObj = nameNode.evaluateValue(context, new JSResult());
			// ClassElementName's ToPropertyKey: an object whose
			// Symbol.toPrimitive/valueOf/toString returns a genuine Symbol
			// must use THAT Symbol as the key, not attempt to stringify it -
			// checking `instanceof Symbol` on the RAW pre-ToPrimitive value
			// (as this used to) misses that case entirely.
			result = RuntimeUtil.toPropertyKeyString(context.getEnvironment(), nameObj);
		} else {
			result = isPrivate ? "#" + name : name;
		}
		// A static class element (method, field, getter or setter) may not be
		// named "prototype" - checked here since it's the single place all
		// static/computed/literal names get resolved.
		if(isStatic && "prototype".equals(result)) {
			throw RuntimeUtil.typeError("Classes may not have a static property named 'prototype'");
		}
		return result;
	}

	// Private-name-aware variant: a private member's key is the PrivateName
	// token minted (once, on first request) by THIS evaluation of the
	// enclosing class - never the plain "#name" string (see PrivateName's
	// class doc). Private member names are never computed (grammar-enforced,
	// mutually exclusive with "#"), so this is the whole story for isPrivate().
	public Object evaluateName(JSInterpretedRuntimeContext context, BuiltinClassConstructor clazz) {
		if(isPrivate) {
			return clazz.getOrCreatePrivateName("#" + name);
		}
		return evaluateName(context);
	}
	
	public boolean isStatic() {
		return isStatic;
	}
	public boolean isPrivate() {
		return isPrivate;
	}
	public boolean isAutoGenerated() {
		return false;
	}

	@Override
	public STATEMENT_TYPE getStatementType() {
		return STATEMENT_TYPE.EXPRESSION;
	}
	
	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		return Signal.NONE;
	}

	public Object construct(JSInterpretedRuntimeContext context, Object instance, Object[] params) {
		return RuntimeUtil.UNDEFINED;
	}

	
	public void initClass(JSInterpretedRuntimeContext context, BuiltinClassConstructor clazz) {
	}
	public void initInstance(JSInterpretedRuntimeContext context, BuiltinClassConstructor clazz, Object instance) {
	}
	
	public void transpileInitClassStatement(JSTranspilerGeneratorContext _jsContext, TranspilerJavaBuilder b, String clazzVar, int memberIndex) {
    }
	// Second, deferred pass - runs strictly AFTER every class element's own
	// transpileInitClassStatement above has already run for the WHOLE class
	// body (every key computed, every method/getter/setter installed). Per
	// spec (ClassDefinitionEvaluation step 34) static field VALUES and
	// static BLOCK bodies share one ordered list and must run interleaved,
	// in source order, in this separate later pass - mirrors the
	// interpreter's identical split (ASTClassDecl.evaluate()'s
	// Initializer.initClass(), which calls ASTClassField.
	// initStaticFieldValue()/ASTClassStaticBlock.runStaticBlock() in a
	// second loop over the same element list). No-op for every other kind
	// of class element (methods, getters/setters, instance fields), which
	// have nothing left to do here - only ASTClassField (static branch) and
	// ASTClassStaticBlock override this.
	public void transpileInitStaticValueStatement(JSTranspilerGeneratorContext _jsContext, TranspilerJavaBuilder b, String clazzVar, int memberIndex) {
    }
	public void transpileInitInstanceStatement(JSTranspilerGeneratorContext _jsContext, TranspilerJavaBuilder b, String clazzVar, String instanceVar, int memberIndex) {
    }
	// Private-name-aware variant: mirrors evaluateName(context, clazz) above -
	// a private member's key is resolved through the BuiltinClassConstructor
	// being built (clazzVar - "_this" in initClass, "clazz" in initInstance),
	// never the plain "#name" string. Private member names are never computed
	// (grammar-enforced), so this is the whole story for isPrivate().
	public String transpileNameExpression(JSTranspilerGeneratorContext _jsContext, String clazzVar) {
		if(isPrivate) {
			return StringFormat.format("{0}.getOrCreatePrivateName({1})", clazzVar, JSTranspiler.literal("#" + getName()));
		}
		ASTNode namedNode = getNameNode();
		if(namedNode!=null) {
			// ClassElementName's ToPropertyKey - see evaluateName(context)'s
			// identical interpreted-mode conversion above. Missing here meant a
			// computed name evaluating to anything other than an
			// already-String/Symbol value (a boolean, number, or an object with
			// its own Symbol.toPrimitive) was passed straight through as the
			// raw value, later rejected by JSObjectImpl.litGetter/litSetter/
			// BuiltinClassConstructor's own key-type checks ("Key is not
			// valid"/"not a function") instead of being coerced first.
			String propertyKey = StringFormat.format("toPropertyKeyString({0},{1})", JSTranspiler.MAIN_ENVIRONMENT, JSTranspiler.asValue(_jsContext,namedNode));
			// A computed name can only resolve to "prototype" at runtime -
			// see RuntimeUtil.checkStaticElementName's own comment for why
			// this re-check is needed here (a literal `static prototype(){}`
			// is already an early SyntaxError shared by both execution modes).
			return isStatic ? StringFormat.format("checkStaticElementName({0})", propertyKey) : propertyKey;
		}
		return JSTranspiler.literal(getName());
    }

	public void decompile(JavaBuilder b) {
	}
}