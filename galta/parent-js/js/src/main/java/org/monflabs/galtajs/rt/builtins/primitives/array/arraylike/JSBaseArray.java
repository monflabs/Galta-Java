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
package org.monflabs.galtajs.rt.builtins.primitives.array.arraylike;

import java.util.Comparator;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.jsonfactory.internal.JSArrayInternal;
import org.monflabs.galtajs.jsonfactory.internal.JSObjectInternal;
import org.monflabs.galtajs.rt.builtins.BuiltinUtil;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.array.BuiltinArrayPrototype;

public abstract class JSBaseArray implements JSArrayInternal {
	
	private JSEnvironment env;
	private JSObjectInternal members;
	
	protected JSBaseArray(JSEnvironment env) {
		this.env = env;
	}
	protected JSBaseArray(JSEnvironment env, JSObjectInternal members) {
		this.env = env;
		this.members = members;
	}

	@Override
	public JSEnvironment getEnvironment() {
		return env;
	}
	
	@Override
	public Object getPrototype() {
		return BuiltinArrayPrototype.get(getEnvironment());
	}
	
	@Override
	public PropertyDescriptor getOwnPropertyDescriptor(long index) {
		if(index>=0 && index<arrayLength()) {
			return PropertyDescriptor.DESC_PROP_ARRAYINDEX;
		}
		return null;
	}


	@Override
	public JSObjectInternal getMembers(boolean autoCreate) {
		if(autoCreate && members==null) {
			members = (JSObjectInternal)JSObject.create(getEnvironment());
		}
		return members;
	}	
	
	@Override
	public void arraySort(Comparator<? super Object> c, DESC_CHECK check) {
        Object[] a = this.toArray();
        BuiltinUtil.mergeSort(a,c);
        for(int i=0; i<a.length; i++) {
        	setOwnProperty(i,a[i]);
        }
	}
}
