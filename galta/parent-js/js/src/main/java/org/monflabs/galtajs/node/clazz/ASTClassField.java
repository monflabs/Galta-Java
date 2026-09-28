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

import org.monflabs.galtajs.jsonfactory.JSObject.DESC_CHECK;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.standard.function.BuiltinClassConstructor;
import org.monflabs.galtajs.rt.builtins.standard.function.BuiltinFunction;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.rt.transpiler.JSTranspiledFunctionRuntimeContext;
import org.monflabs.util.StringFormat;
import org.monflabs.util.StringUtil;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.TranspilerJavaBuilder;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.util.JavaBuilder;


/**
 * Class fields.
 */
public class ASTClassField extends ASTClassMember {

	private ASTNode body;
	// `accessor x = 1;` (decorators proposal auto-accessor): the field's value
	// lives in a class-private backing slot, exposed through a getter/setter
	// pair on the prototype (or the class itself, when static) - see
	// BuiltinClassConstructor.addClassAccessor()/setAccessorFieldValue().
	private final boolean accessor;

	public ASTClassField(Token t, Object name, ASTNode body, boolean isStatic, boolean isPrivate) {
		this(t,name,body,isStatic,isPrivate,Collections.emptyList());
	}
	public ASTClassField(Token t, Object name, ASTNode body, boolean isStatic, boolean isPrivate, List<ASTNode> decorators) {
		this(t,name,body,isStatic,isPrivate,decorators,false);
	}
	public ASTClassField(Token t, Object name, ASTNode body, boolean isStatic, boolean isPrivate, List<ASTNode> decorators, boolean accessor) {
		super(t,name,isStatic, isPrivate,decorators);
		this.body = assignParent(body);
		this.accessor = accessor;
	}

	public ASTNode getBody() {
		return body;
	}

	public boolean isAccessor() {
		return accessor;
	}
	
	@Override
	public int getChildCount() {
		return super.getChildCount()+1;
	}
	@Override
	public ASTNode getChild(int index) {
		switch(index) {
			case 0 ->	{ return body; }
			default ->	{ return super.getChild(index-1); }
		}
	}
	@Override
	protected void _setChild(int index, ASTNode node) {
		switch(index) {
			case 0 ->	{ this.body = node; }
			default ->  { super._setChild(index-1,node); }
		}
	}

	@Override
	public void initClass(JSInterpretedRuntimeContext context, BuiltinClassConstructor clazz) {
		Object name = evaluateName(context, clazz);
		// Field decorators receive `undefined` (not the field's own value -
		// which, for an instance field, doesn't even exist yet) and may
		// return an additional initializer function that per spec
		// participates in computing the field's actual value. Evaluating and
		// calling the decorators here (once, at class-definition time, for
		// every field kind - static, instance, private - matching
		// ClassFieldDefinitionEvaluation's timing) is implemented; chaining a
		// returned initializer into the field's later value computation is
		// deliberately NOT (no test262 coverage exercises it - every
		// decorated field in the suite has no initializer and no assertion
		// on the decorator's effect).
		if(getDecorators().length>0) {
			applyDecorators(context, getDecorators(), RuntimeUtil.UNDEFINED, accessor ? "accessor" : "field", name, isStatic(), isPrivate());
		}
		if(accessor) {
			// The getter/setter pair is a class element like any method -
			// defined here, at class-definition time; the backing slot is
			// filled per instance (initInstance) or once for the class
			// (initStaticFieldValue) via setAccessorFieldValue.
			//
			// For a PUBLIC name this defines the pair on the prototype (or on
			// the class, when static). For a PRIVATE one: a static accessor is
			// installed on the class here, while an INSTANCE accessor is only
			// created here (so the pair exists and has its [[HomeObject]]) and
			// registered per instance by initInstance() below - the same split
			// a private getter/setter already uses.
			clazz.addClassAccessor(name, isStatic());
		}
		// ClassFieldDefinitionEvaluation: EVERY field's NAME (static or
		// instance) is evaluated here, once, in source order, as part of
		// class definition's single element-by-element pass (step 28) -
		// but a STATIC field's VALUE initializer must NOT run inline here.
		// Per spec, static fields' values only run in a SEPARATE later
		// pass (step 34, DefineField) that happens strictly AFTER every
		// element in the class body - methods AND fields, static and
		// instance alike - has already had its key/name computed. Running
		// a static field's value eagerly, interleaved with later elements'
		// still-to-be-computed keys, lets its side effects corrupt a LATER
		// computed key (test262 intercalated-static-non-static-computed-
		// fields.js: `[i++]=i++; static [i++]=i++; [i++]=i++;` - the
		// static field's value initializer must not consume an `i`
		// increment before the third field's key is computed). See
		// ASTClassDecl.evaluate()'s Initializer.initClass(), which now runs
		// initStaticFieldValue() for every static field in a second pass
		// after this per-element loop completes. Private names don't need
		// caching here (never computed - grammar-enforced - and already
		// pre-minted upfront by ASTClassDecl for a different reason,
		// lexical visibility order); only a computed public name is cached
		// for later reuse (by initStaticFieldValue() or initInstance()).
		if(!isPrivate()) {
			clazz.setComputedFieldName(this, name);
		}
	}
	// Runs a STATIC field's value initializer and defines it on the class -
	// called by ASTClassDecl AFTER every class element's key has been
	// computed (see initClass()'s own comment above for why this must be
	// deferred rather than run inline in source-order position).
	public void initStaticFieldValue(JSInterpretedRuntimeContext context, BuiltinClassConstructor clazz) {
		Object name = isPrivate() ? evaluateName(context, clazz) : clazz.getComputedFieldName(this);
		ASTNode body = getBody();
		Object v = RuntimeUtil.UNDEFINED;
		if(body!=null ) {
			v = body.evaluateValue(context, new JSResult());
			maybeSetFunctionName(name, v);
		}
		if(accessor) {
			// The accessor pair (public or private) was installed on the class
			// by initClass(); this fills its backing slot.
			clazz.setAccessorFieldValue(clazz, name, v);
		} else {
			clazz.addClassStaticField(name, v);
		}
	}
	@Override
	public void initInstance(JSInterpretedRuntimeContext context, BuiltinClassConstructor clazz, Object instance) {
		if(!isStatic()) {
			Object name = isPrivate() ? evaluateName(context, clazz) : clazz.getComputedFieldName(this);
			ASTNode body = getBody();
			Object v = RuntimeUtil.UNDEFINED;
			if(body!=null ) {
				v = body.evaluateValue(context, new JSResult());
				maybeSetFunctionName(name, v);
			}
			if(accessor) {
				if(isPrivate()) {
					// A private instance accessor lives on the instance, not
					// the prototype - register it here, per instance, before
					// its backing slot is filled.
					clazz.addInstanceAutoAccessor(instance, name);
				}
				clazz.setAccessorFieldValue(instance, name, v);
			} else {
				clazz.addClassField(instance, name, v);
			}
		}
	}

	// NamedEvaluation for a field initializer (ClassFieldDefinitionEvaluation
	// step "If IsAnonymousFunctionDefinition(Initializer), perform
	// NamedEvaluation..."): an anonymous function/arrow/class expression
	// assigned directly as a field's initializer gets the field's own name
	// (including the "#" prefix for a private field, via PrivateName's own
	// toString() - see propertyKeyToFunctionName's doc) - mirrors
	// JSObjectImpl.litFunction()'s identical "only if genuinely still
	// anonymous" check for object-literal methods, since a private field's
	// value never goes through that shared helper (definePrivateElement is a
	// completely separate installation path, bypassing JSAccessor/
	// setOwnProperty entirely).
	private static void maybeSetFunctionName(Object name, Object value) {
		if(value instanceof BuiltinFunction fn) {
			Object fnName = fn.getProperty("name");
			if(RuntimeUtil.isNullOrUndefined(fnName) || StringUtil.isEmpty(fnName.toString())) {
				fn.setOwnProperty("name", PropertyDescriptor.propertyKeyToFunctionName(name), null, DESC_CHECK.NONE);
			}
		}
	}

	@Override
	public void transpileInitClassStatement(JSTranspilerGeneratorContext _jsContext, TranspilerJavaBuilder b, String clazzVar, int memberIndex) {
		if(!isStatic() && !isPrivate() && getNameNode()!=null) {
			// ClassFieldDefinitionEvaluation: an instance field's NAME
			// (unlike its value) is computed here, at class-definition
			// time, same as every other element - not deferred until
			// initInstance() (see ASTClassDecl's matching `instFieldKey_N`
			// field declaration and this class's own transpileInitInstanceStatement,
			// which reads this back instead of recomputing).
			b.println("instFieldKey_{0} = {1};", memberIndex, transpileNameExpression(_jsContext, clazzVar));
		}
		if(isStatic() && !isPrivate() && getNameNode()!=null) {
			// A STATIC field's NAME (like an instance field's) is computed
			// here, in this first pass, in source order - but unlike an
			// instance field, its VALUE must NOT run here (see
			// transpileInitStaticValueStatement below for why, and for the
			// history of a previous, reverted attempt at this same fix).
			// Cached into `statFieldKey_N` (mirrors `instFieldKey_N`
			// exactly) so the second pass reads back the ALREADY-computed
			// key instead of re-running a possibly side-effecting computed-
			// name expression a second time.
			b.println("statFieldKey_{0} = {1};", memberIndex, transpileNameExpression(_jsContext, clazzVar));
		}
		if(accessor) {
			// Mirrors initClass() above: the getter/setter pair is defined at
			// class-definition time, reading back the just-cached computed
			// key when there is one. A private INSTANCE accessor is only
			// created here and registered per instance below, exactly as in
			// interpreted mode.
			String name = getNameNode()!=null
					? StringFormat.format(isStatic() ? "statFieldKey_{0}" : "instFieldKey_{0}", memberIndex)
					: transpileNameExpression(_jsContext, clazzVar);
			b.println("{0}.addClassAccessor({1},{2});", clazzVar, name, isStatic());
		}
    }
	// Runs a STATIC field's value initializer and defines it on the class -
	// called by ASTClassDecl in a SEPARATE, deferred pass AFTER every class
	// element's key has already been computed by transpileInitClassStatement
	// above (see ASTClassMember.transpileInitStaticValueStatement's own doc
	// for the full spec citation and interpreter cross-reference). Moved
	// out of transpileInitClassStatement's old single-pass inline codegen,
	// which computed a static field's key AND value together - letting the
	// value initializer's side effects corrupt a LATER element's
	// still-to-be-computed key (test262 intercalated-static-non-static-
	// computed-fields.js: `[i++]=i++; static [i++]=i++; [i++]=i++;`). A
	// previous attempt at deferring ONLY this (leaving ASTClassStaticBlock
	// still running inline in the first pass) regressed ~65 files by
	// breaking static-field/static-block relative source ordering
	// (static-init-sequence.js et al.) - this time ASTClassStaticBlock's
	// own body is deferred to this exact same pass too (see its
	// transpileInitStaticValueStatement), so both kinds stay correctly
	// interleaved in source order, exactly mirroring the interpreter's
	// existing (already spec-correct) two-pass split.
	@Override
	public void transpileInitStaticValueStatement(JSTranspilerGeneratorContext _jsContext, TranspilerJavaBuilder b, String clazzVar, int memberIndex) {
		if(!isStatic()) {
			return;
		}
		// A computed public name was already evaluated once, in the first
		// pass (see transpileInitClassStatement above) - read back the
		// cached value rather than re-running the key expression. A
		// literal/private name has no such timing concern (no side
		// effects), so it's still computed inline here as before.
		String name = !isPrivate() && getNameNode()!=null ? StringFormat.format("statFieldKey_{0}", memberIndex) : transpileNameExpression(_jsContext, clazzVar);
		// See ASTClassStaticBlock's identical wrapping (and
		// TranspiledFieldInitializerRuntimeContext's own doc) for the
		// full rationale - a static field initializer's own
		// this/[[HomeObject]] must be the class itself, so
		// super.prop/eval("this...")/an arrow closure created inside
		// the initializer resolves correctly instead of leaking
		// whatever context lexically encloses the class declaration.
		String tmpCtx = _jsContext.generateUniqueId("fictx");
		b.println("{0} {1} = new TranspiledFieldInitializerRuntimeContext({2},{3},{3});", JSTranspiledFunctionRuntimeContext.class.getSimpleName(), tmpCtx, JSTranspiler.MAIN_CONTEXT, clazzVar);
		b.println("{");
		b.incIndent();
		b.println("{0} {1} = {2};", JSTranspiledFunctionRuntimeContext.class.getSimpleName(), JSTranspiler.MAIN_CONTEXT, tmpCtx);
		// Captured into a local before use (rather than embedding the
		// generated expression text twice) both because it may be
		// side-effecting and because NamedEvaluation (below) needs the
		// SAME already-evaluated value maybeSetFunctionName inspects,
		// not a freshly re-evaluated one.
		String valueVar = _jsContext.generateUniqueId("fval");
		String value = getBody()!=null ? JSTranspiler.asValue(_jsContext, getBody()) : "RuntimeUtil.UNDEFINED";
		b.println("Object {0} = {1};", valueVar, value);
		if(accessor) {
			b.println("{0}.setAccessorFieldValue({0},{1},{2});", clazzVar, name, valueVar);
		} else {
			b.println("{0}.addClassStaticField({1},{2});", clazzVar, name, valueVar);
		}
		// ClassFieldDefinitionEvaluation NamedEvaluation step - see
		// RuntimeUtil.maybeSetFunctionName's own doc. Missing entirely
		// from transpiled codegen until now (test262
		// static-field-anonymous-function-name.js).
		b.println("maybeSetFunctionName({0},{1});", name, valueVar);
		b.decIndent();
		b.println("}");
    }
	@Override
	public void transpileInitInstanceStatement(JSTranspilerGeneratorContext _jsContext, TranspilerJavaBuilder b, String clazzVar, String instanceVar, int memberIndex) {
		if(!isStatic()) {
			// A computed name was already evaluated once, at class-
			// definition time (see transpileInitClassStatement above) -
			// read back the cached value rather than re-running the key
			// expression (which could have side effects, or simply be
			// wrong per spec: ClassFieldDefinitionEvaluation computes a
			// field's name once, not once per `new`). A literal/private
			// name has no such timing concern, so it's still computed
			// inline here as before.
			String name = !isPrivate() && getNameNode()!=null ? StringFormat.format("instFieldKey_{0}",memberIndex) : transpileNameExpression(_jsContext, clazzVar);
			// [[HomeObject]] for an instance field initializer is the
			// class's prototype, `this` is the instance being initialized
			// - same as ASTClassDecl's interpreted-mode initInstance()
			// (Object proto = clazz.getProperty(Constructor.PROTOTYPE)).
			// See the static-field branch above for the full rationale.
			String tmpCtx = _jsContext.generateUniqueId("fictx");
			// The class's own PrivateEnvironment ("classScope_N" if this class
			// has private members, otherwise just whatever ambient context
			// _jsContext already resolves to) - captured BEFORE pushing
			// bodyContext below (which would otherwise shadow it with tmpCtx).
			// Threaded through as fictx's 4th constructor argument SOLELY for
			// resolvePrivateName() (a direct eval reading a private name from
			// inside this initializer's own body needs it) - see
			// TranspiledFieldInitializerRuntimeContext's own doc for why this
			// is deliberately narrower than the reparent-`parent` fix
			// attempted (and reverted) previously.
			String classScopeVar = _jsContext.getContextJavaName();
			b.println("{0} {1} = new TranspiledFieldInitializerRuntimeContext({2},{3}.getProperty(Constructor.PROTOTYPE),{4},{5});", JSTranspiledFunctionRuntimeContext.class.getSimpleName(), tmpCtx, JSTranspiler.MAIN_CONTEXT, clazzVar, instanceVar, classScopeVar);
			b.println("{");
			b.incIndent();
			b.println("{0} {1} = {2};", JSTranspiledFunctionRuntimeContext.class.getSimpleName(), JSTranspiler.MAIN_CONTEXT, tmpCtx);
			// Pins the body's OWN eval-bundling context (ASTCall.
			// transpileSpecialFunctions' jsContext.getContextJavaName()) to
			// this field's fictx variable - which has the CORRECT getThis()
			// (the real instance, not whatever classScope_N.getThis()
			// delegates to) - instead of the class-wide classScopeVar a
			// bare _jsContext would otherwise still report here (the fictx
			// Java-local `_ctx` rename above is invisible to _jsContext's own
			// tracking). Scoped to ONLY this field's body expression - method/
			// getter/setter/constructor bodies each already push their own
			// fresh context layer elsewhere and are untouched by this.
			JSTranspilerGeneratorContext bodyContext = new org.monflabs.galtajs.transpiler.context.TranspilerGeneratorFunctionContext(_jsContext, tmpCtx);
			String valueVar = _jsContext.generateUniqueId("fval");
			String value = getBody()!=null ? JSTranspiler.asValue(bodyContext, getBody()) : "RuntimeUtil.UNDEFINED";
			b.println("Object {0} = {1};", valueVar, value);
			if(accessor) {
				if(isPrivate()) {
					// See initInstance(): a private instance accessor is
					// registered on the instance, not the prototype.
					b.println("{0}.addInstanceAutoAccessor({1},{2});", clazzVar, instanceVar, name);
				}
				b.println("{0}.setAccessorFieldValue({1},{2},{3});", clazzVar, instanceVar, name, valueVar);
			} else {
				b.println("{0}.addClassField({1},{2},{3});", clazzVar, instanceVar, name, valueVar);
			}
			// ClassFieldDefinitionEvaluation NamedEvaluation step - see
			// RuntimeUtil.maybeSetFunctionName's own doc.
			b.println("maybeSetFunctionName({0},{1});", name, valueVar);
			b.decIndent();
			b.println("}");
		}
    }
	
	@Override
	public void decompile(JavaBuilder b) {
		if(isStatic()) {
			b.append("static ");
		}
		if(accessor) {
			b.append("accessor ");
		}
		ASTNode namedNode = getNameNode();
		if(namedNode!=null) {
			b.append('[');
			b.append(namedNode.decompileExpression());
			b.append(']');
		} else {
			if(isPrivate()) {
				b.append('#');
			}
			b.append(getName());
		}
		if(getBody()!=null) {
			b.append(" = ");
			b.append(getBody().decompileExpression());
		}
		b.append(";\n");
	}
}
