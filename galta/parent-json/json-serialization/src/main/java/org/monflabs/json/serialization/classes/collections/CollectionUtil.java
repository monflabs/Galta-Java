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
package org.monflabs.json.serialization.classes.collections;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.monflabs.json.serialization.ClassAdapter;

/**
 * Helpers shared by the collection adapters.
 */
final class CollectionUtil {
	
	private CollectionUtil() {
	}
	
	/**
	 * The generic parameters of a collection, defaulting to the object adapter when they are
	 * missing (a raw collection, or a collection serialized at the top level).
	 */
	static ClassAdapter[] params(ClassAdapter[] genericParams, int count, ClassAdapter objectAdapter) {
		ClassAdapter[] p = new ClassAdapter[count];
		boolean usable = genericParams!=null && genericParams.length==count;
		for(int i=0; i<count; i++) {
			p[i] = usable && genericParams[i]!=null ? genericParams[i] : objectAdapter;
		}
		return p;
	}
	
	static <T> T newInstance(Class<?> clazz) throws InstantiationException, IllegalAccessException {
		try {
			Constructor<?> c = clazz.getDeclaredConstructor();
			@SuppressWarnings("unchecked")
			T t = (T)c.newInstance();
			return t;
		} catch(NoSuchMethodException|InvocationTargetException ex) {
			InstantiationException e = new InstantiationException("Cannot instantiate "+clazz.getName());
			e.initCause(ex);
			throw e;
		}
	}
}
