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
package org.monflabs.galtajs.rt.transpiler;

import java.util.function.Supplier;

import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext.VAR_TYPE;

/**
 * Variable object.
 */
public class JSVar implements VarAccessor {
	
	public static void initVars(Object[] a, int start) {
		Object undefined = RuntimeUtil.UNDEFINED;
		for(int i=start, len=a.length; i<len; i++) {
			a[i] = undefined;
		}
	}


	
	private VAR_TYPE type;
	private String name;
	
	// Public for direct access from the compiled code
	public Object value; 
	
	public JSVar() {
		this.value = RuntimeUtil.UNDEFINED;
	}
	public JSVar(Object value) {
		this.value = RuntimeUtil.UNDEFINED;
	}
	public JSVar(Object[] array, int index) {
		this.value = index<array.length ? array[index] : RuntimeUtil.UNDEFINED;
	}
	public JSVar(Object[] array, int index, Object defaultValue) {
		this.value = index<array.length ? array[index] : defaultValue;
	}
	public JSVar(Object[] array, int index, Supplier<Object> defaultValue) {
		this.value = index<array.length ? array[index] : (defaultValue!=null ? defaultValue.get() : RuntimeUtil.UNDEFINED);
	}
	
	
	//
	// TODO: change this!
	//
	public JSVar(JSTranspiledRuntimeContext context, VAR_TYPE type, String name) {
		this.type = type;
		this.name = name;
		this.value = RuntimeUtil.UNDEFINED;
	}
	public JSVar(JSTranspiledRuntimeContext context, VAR_TYPE type, String name, Object value) {
		this.type = type;
		this.name = name;
		this.value = value;
	}
	
	@Override
	public VAR_TYPE getType() {
		return type;
	}

	@Override
	public String getKey() {
		return name;
	}

	@Override
	public Object getValue() {
		return value;
	}

	@Override
	public Object setValue(Object value) {
		return this.value = value;
	}
	
	// Variable declaration
	@SuppressWarnings("unchecked")
	public <T> T initDecl(VAR_TYPE type, T value) {
		this.type = type;
		this.value = value;
		return (T)this;
	}
	
	
	@Override
	public String toString() {
		Object value = getValue();
		return value!=null ? value.toString() : null;
	}
}
