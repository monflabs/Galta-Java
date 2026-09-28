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
package org.monflabs.galtajs.transpiler.context;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.monflabs.galtajs.JSContext;
import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.node.ASTVarContainer.VariableDef;
import org.monflabs.galtajs.rt.transpiler.JSTranspilerMap;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.JSTranspilerOptions;
import org.monflabs.galtajs.transpiler.TranspilerJavaBuilder;

/**
 * Transpiler context.
 */
public abstract class JSTranspilerGeneratorContext implements JSContext {
	
	private JSTranspilerGeneratorContext parent;
	private Map<String, VariableDef> variables = new LinkedHashMap<>();

	private int currentUniqueId;

	// Java variable name of the `List<DisposableResource>` the NEAREST
	// enclosing using/await-using disposal boundary (a Block or function body
	// that actually declares a using/await-using binding - see
	// ASTVariableDeclUsing's own checkNotAtTopLevelOfScript()) generated for
	// itself - null until a boundary container calls setDisposablesListVar().
	// Delegates up the CODEGEN context chain, same pattern as
	// getContextJavaName(); a using-declaration transpiling against a null
	// result means no boundary container has set one up (shouldn't happen -
	// every legal position for `using` has one).
	private String disposablesListVar;
	
	public JSTranspilerGeneratorContext() {
	}
	
	public JSTranspilerGeneratorContext(JSTranspilerGeneratorContext parent) {
		this.parent = parent;
	}
	
	public TranspilerGeneratorMainContext getMainContext() {
		return parent.getMainContext();
	}
	
	public JSTranspiler getTranspiler() {
		return parent.getTranspiler();
	}

	@Override
	public JSEnvironment getEnvironment() {
		return getTranspiler().getEnvironment();
	}

	public JSTranspilerOptions getOptions() {
		return getTranspiler().getTranspilerOptions();
	}
	
	public JSTranspilerGeneratorContext getParent() {
		return parent;
	}

	public TranspilerJavaBuilder createJavaBuilder() {
		return new TranspilerJavaBuilder(this);
	}
	
	public int generateUniqueId() {
		return getMainContext().generateUniqueId();
	}
	
	public String getModuleName() {
		return getMainContext().getModuleName();
	}

	public final String generateUniqueId(String prefix) {
		return prefix+generateUniqueId();
	}

	public int getCurrentValueId() {
		return currentUniqueId;
	}

	public String wrapValue(int id, Supplier<String> s) {
		int oldId = currentUniqueId;
		currentUniqueId = id;
		try {
			String code = s.get();
			return code;
		} finally {
			currentUniqueId = oldId;
		}
	}

	
	public int getDepth() {
		int depth = 0;
		for(JSTranspilerGeneratorContext c=this; c!=null; c=c.getParent()) {
			depth++;
		}
		return depth;
	}
	
	public ConstantPool getConstantPool() {
		return getParent().getConstantPool();
	}

	public Map<String, VariableDef> getVariables() {
		return variables;
	}
	public String getWithJavaName() {
		return null;
	}
	// True only for a TranspilerEvalShadowContext - see its own doc. Checked by
	// ASTIdentifier.getIdentifierReadAccessor()'s context-chain walk, the exact
	// same way it already checks getWithJavaName() on every context visited -
	// default false, so every OTHER context subclass (the overwhelming common
	// case) is completely unaffected.
	public boolean isEvalShadowBoundary() {
		return false;
	}
	// The Java variable name that generated code should use as "the current
	// context" when constructing a function-object literal or resolving a
	// private (#name) member - normally JSTranspiler.MAIN_CONTEXT ("_ctx"),
	// but a class body with private members overrides this (see
	// TranspilerGeneratorFunctionContext) to point at a per-class-evaluation
	// private-name-resolving scope instead. Delegates up the CODEGEN context
	// chain (not the runtime chain) by default.
	public String getContextJavaName() {
		return parent!=null ? parent.getContextJavaName() : JSTranspiler.MAIN_CONTEXT;
	}

	public void setDisposablesListVar(String javaVarName) {
		this.disposablesListVar = javaVarName;
	}
	public String getDisposablesListVar() {
		if(disposablesListVar!=null) {
			return disposablesListVar;
		}
		return parent!=null ? parent.getDisposablesListVar() : null;
	}
	
	public JSTranspilerMap getTranspilerMap() {
		return getParent().getTranspilerMap();
	}

	public VariableDef findVariable(String var) {
		VariableDef v = variables.get(var);
		if(v!=null) {
			return v;
		}
		if(parent!=null) {
			return parent.findVariable(var);
		}
		return null;
	}

	public VariableDef getOwnVariable(String var) {
		VariableDef v = variables.get(var);
		return v;
	}
	
	public void createVariable(VariableDef v) {
		createVariable(v,true);
	}
	public void createVariable(VariableDef v, boolean tranpilerDeclared) {
		v.setTranspilerDeclared(tranpilerDeclared);
		variables.put(v.getName(),  v);
	}
	public void removeVariable(VariableDef v) {
		variables.remove(v.getName());
	}
	
	public Map<String,VariableDef> getAllVariables(Map<String,VariableDef> vars, boolean deep) {
		for(Map.Entry<String, VariableDef> e: variables.entrySet()) {
			if(e.getValue().isTranspilerDeclared()) {
				if(!vars.containsKey(e.getKey())) {
					vars.put(e.getKey(), e.getValue());
				}
			}
		}
		if(deep) {
			if(getParent()!=null) {
				return getParent().getAllVariables(vars,deep);
			}
		}
		return vars;
	}
}
