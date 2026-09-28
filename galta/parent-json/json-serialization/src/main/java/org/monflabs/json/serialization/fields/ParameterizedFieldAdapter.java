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
package org.monflabs.json.serialization.fields;

import java.lang.reflect.Type;

import org.monflabs.json.serialization.ClassAdapter;
import org.monflabs.json.serialization.FieldAdapter;
import org.monflabs.json.serialization.JsonRegistry;

public class ParameterizedFieldAdapter extends BaseFieldAdapter {
	
	private FieldAdapter rawAdapter;
	private Type[] params;
	private GenericTypeResolver.ResolvedType[] resolvedParams;
	private ClassAdapter[] paramAdapters;
	
	public ParameterizedFieldAdapter(FieldAdapter rawAdapter, Type[] params) {
		this.rawAdapter = rawAdapter;
		this.params = params;
	}
	
	@Override
	protected void _init(JsonRegistry registry, ClassAdapter parent) {
		if(rawAdapter instanceof BaseFieldAdapter fa) {
			fa.init(registry,parent);
		}
		Class<?> context = parent!=null ? parent.getAdaptedClazz() : null;
		resolvedParams = GenericTypeResolver.resolve(params, context, registry);
		boolean isStatic = true;
		for(int i=0; i<resolvedParams.length; i++) {
			if(!resolvedParams[i].isStatic()) {
				isStatic = false;
				break;
			}
		}
		if(isStatic) {
			// No type variable involved: resolve once for all
			paramAdapters = resolveParams(null);
		}
	}
	
	private ClassAdapter[] resolveParams(ClassAdapter[] genericParams) {
		if(paramAdapters!=null) {
			return paramAdapters;
		}
		ClassAdapter[] p = new ClassAdapter[resolvedParams.length];
		for(int i=0; i<resolvedParams.length; i++) {
			p[i] = resolvedParams[i].resolve(genericParams);
		}
		return p;
	}
	
	@Override
	public Object readProperty(Object _this, ClassAdapter[] genericParams) {
		return rawAdapter.readProperty(_this,resolveParams(genericParams));
	}
	
	@Override
	public void writeProperty(Object _this, Object jsonValue, ClassAdapter[] genericParams) {
		rawAdapter.writeProperty(_this,jsonValue,resolveParams(genericParams));
	}
}
