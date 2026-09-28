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
package org.monflabs.galtajs.rt.builtins.standard.set.setlike;

import java.util.Iterator;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;
import org.monflabs.galtajs.JSEnvironment;


//
// 
//
public class JSSetJavaSet extends JSBaseSet {

	public static JSSetJavaSet of(@NonNull JSEnvironment env, @NonNull Set<?> set) {
		return new JSSetJavaSet(env,set);
	}

	private Set<?> set;
	
	private JSSetJavaSet(@NonNull JSEnvironment env, @NonNull Set<?> set) {
		super(env);
		this.set = set;
	}
	
	@Override
	public long jsSize() {
		return set.size();
	}

	@Override
	public boolean jsHas(Object v) {
		return set.contains(v);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Iterator<Object> jsKeys() {
		return ((Set<Object>)set).iterator();
	}
}
