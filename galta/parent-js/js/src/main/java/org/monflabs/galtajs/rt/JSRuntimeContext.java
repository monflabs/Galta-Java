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
package org.monflabs.galtajs.rt;

import java.util.List;

import org.monflabs.galtajs.JSContext;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.privatename.PrivateName;
import org.monflabs.galtajs.rt.interpreter.AbstractRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext.VAR_TYPE;
import org.monflabs.galtajs.rt.interpreter.VariableMap;
import org.monflabs.galtajs.rt.transpiler.VarAccessor;
import org.monflabs.util.Console;

/**
 * Script runtime context.
 */
public interface JSRuntimeContext extends JSContext {
	
	public static JSRuntimeContext get() {
		return (JSRuntimeContext)JSContext.get();
	}
	public static JSRuntimeContext getUnchecked() {
		return (JSRuntimeContext)JSContext.getUnchecked();
	}
	
	// Check the context for debugging purposes
	public default void checkCurrent() {
		if(this!=get()) {
			JSRuntimeContext ctx = get();
			throw RuntimeUtil.error("JSRuntimeContext is not the current context", ctx.getClass());
		}
	}

	public JSRuntimeContext getParent();
	public JSUnitContext getMainContext();
	public JSRuntimeContext getVarDeclContext();

	public JSGlobalContext getGlobalContext();
	
	public JSFunctionContext getFunctionContext();
	
	public JSRuntimeContext getDebugCallParent();
	public JSBoundaryContext getDebugCallBoundaryContext();
	
	public Object getVariableValue(String varName, Object defaultValue);
	public VarAccessor getVariableEntry(String varName);

	// Checks ONLY this context's own bindings, never walking to the parent -
	// unlike getVariableEntry() (a full ancestor-chain walk). Needed anywhere
	// "does THIS exact scope already declare this name" must be answered
	// without a false-positive match on an unrelated ancestor binding of the
	// same name.
	public VarAccessor getLocalVariableEntry(String varName);

	// `using`/`await using` (explicit resource management): AddDisposableResource
	// registers a resolved (value, [Symbol.dispose]/[Symbol.asyncDispose])
	// pair on the CURRENT (innermost) scope, exactly like a lexical binding -
	// registerDisposableResource() is called once per `using` declaration at
	// evaluation time (see ASTVariableDeclUsing), and getOwnDisposableResources()
	// is read back by whatever construct owns this scope's disposal boundary
	// (ASTBlock, a function body, a C-style for loop's head, etc. - see
	// DisposeResourcesUtil) when it exits, in either direction (normal or
	// exceptional). Deliberately NOT inherited/walked like getVariableEntry() -
	// each scope disposes only its OWN resources.
	public void registerDisposableResource(DisposableResource resource);
	public List<DisposableResource> getOwnDisposableResources();

	// Lets a context expose bindings that live outside its own VariableMap
	// (e.g. InterpretedGlobalRuntimeContext surfacing globalThis-backed
	// var/function declarations) as if they were declared in this exact
	// scope, without walking up to the parent chain.
	public Object resolveOwnIdentifierValue(String varName, Object defaultValue);

	public VariableMap getVariableMap();
	public VariableMap getVariableMap(boolean autoCreate);
	public VarAccessor createVariable(String varName, Object value, VAR_TYPE type); // value===NOT_AVAILABLE means that the value should stay the same, or UNDEFINED when newly created
	// configurable: whether this specific binding should be deletable, as when a direct eval
	// hoists a var/function into an already-existing outer scope (EvalDeclarationInstantiation
	// creates such bindings as deletable). Contexts that don't track per-binding configurability
	// (i.e. everything except AbstractRuntimeContext's own VariableMap-backed bindings) can ignore it.
	public default VarAccessor createVariable(String varName, Object value, VAR_TYPE type, boolean configurable) {
		return createVariable(varName, value, type);
	}
	public void setVariable(String varName, Object value);
	public boolean hasVariable(String varName);
	public boolean deleteVariable(String varName);

	
	public Object getThis();
	public default void setThis(Object _this) {
		if(getParent()!=null) {
			getParent().setThis(_this);
			return;
		}
		throw RuntimeUtil.typeError("Internal error - cannot set This in context");
	}
	public Object getFilterContext();

	// Resolves a #name reference (spec: PrivateEnvironment.ResolveBinding) by
	// walking the ambient closure chain for the nearest enclosing class
	// evaluation that declares it - not a static, parse-time lookup, since a
	// class's PrivateName tokens only exist once that specific evaluation has
	// run. Only InterpretedClassPrivateScopeContext (one per class
	// evaluation) actually knows any names; every other context just
	// delegates outward, so a nested class correctly sees an outer class's
	// private names too. Returns null if no enclosing class declares `name`.
	public default PrivateName resolvePrivateName(String name) {
		JSRuntimeContext parent = getParent();
		return parent!=null ? parent.resolvePrivateName(name) : null;
	}

	// Collects every "#name" declared by ANY class evaluation currently
	// enclosing this context (walking the same ambient closure chain
	// resolvePrivateName() above does), for a direct eval's
	// AllPrivateNamesValid early-error check (spec: EvalDeclarationInstantiation's
	// privateIdentifiers, collected from the caller's PrivateEnvironment
	// chain - see StandardLibrary's eval case, PrivateNameValidator). Only
	// InterpretedClassPrivateScopeContext actually contributes names; every
	// other context just delegates outward, so an eval nested inside several
	// classes sees the UNION of all their declared names, matching spec's
	// "repeat while privateEnv is not null" accumulation (AllPrivateNamesValid
	// only cares about set membership, not which specific enclosing class
	// declared a given name).
	public default void collectEnclosingPrivateNames(java.util.Set<String> into) {
		JSRuntimeContext parent = getParent();
		if(parent!=null) {
			parent.collectEnclosingPrivateNames(into);
		}
	}

	// Should avoid calling this, only for some special cases...
	public boolean isCompiled();
	
	public Constructor getNewTarget();
	public void setNewTarget(Constructor newTarget);
	
	
	/////////////////////////////////////////////////////////////////////////
	// Dump helpers
	/////////////////////////////////////////////////////////////////////////


	public default String getObjectString(Object value) {
		if(value!=null) {
			String s = value.toString() + " [" + value.getClass().getSimpleName() +"]";
			return s;
		}
		return "<null>";
	}
	public default void dumpContext() {
		int i=0;
		for(JSRuntimeContext ctx=this; ctx!=null; ctx=ctx.getParent()) {
			Console.log("Context [{0}]: {1}", i++, ctx.getClass().getSimpleName());

			Object _this = ctx.getThis();
			if(_this!=null) {
				Console.log("    this={0}", DebugUtil.jsLiteral(getEnvironment(),_this,512));
			}
			
			if(ctx instanceof AbstractRuntimeContext ic) {
				if(ic.getVariableMap()!=null) {
					for(VarAccessor e: ic.getVariableMap().entries()) {
						Console.log("    {0}={1}", e.getKey(), DebugUtil.jsLiteral(getEnvironment(),e.getValue(),512));
					}
				}
			}
		}
	}
}
