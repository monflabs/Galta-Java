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
package org.monflabs.json.serialization.classes;

import org.monflabs.json.serialization.ClassAdapter;

/**
 * Class adapter bound to a fixed set of generic parameters.
 * 
 * This is used when a parameterized type appears as a type argument, like the
 * inner Map in List&lt;Map&lt;String,Foo&gt;&gt;: the raw adapter is called with
 * the bound parameters, whatever the caller passes.
 * 
 * @author priand
 *
 */
public class ParameterizedClassAdapter implements ClassAdapter {
	
	private ClassAdapter rawAdapter;
	private ClassAdapter[] params;
	
	public ParameterizedClassAdapter(ClassAdapter rawAdapter, ClassAdapter[] params) {
		this.rawAdapter = rawAdapter;
		this.params = params;
	}
	
	public ClassAdapter getRawAdapter() {
		return rawAdapter;
	}
	
	public ClassAdapter[] getParams() {
		return params;
	}

	@Override
	public Class<?> getAdaptedClazz() {
		return rawAdapter.getAdaptedClazz();
	}

	@Override
	public Object serialize(Object value, ClassAdapter[] genericParams) {
		return rawAdapter.serialize(value, params);
	}

	@Override
	public Object deserialize(Object jsonValue, ClassAdapter[] genericParams) {
		return rawAdapter.deserialize(jsonValue, params);
	}
}
