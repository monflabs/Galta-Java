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
package org.monflabs.json.serialization;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * A reference to a generic type, captured by an anonymous subclass (a "super type token").
 * <p>
 * <pre>
 * List&lt;Item&gt; items = registry.fromJson(new TypeRef&lt;List&lt;Item&gt;&gt;() {}, json);
 * </pre>
 *
 * @param <T> the referenced type
 */
public abstract class TypeRef<T> {

	private final Type type;

	protected TypeRef() {
		Type s = getClass().getGenericSuperclass();
		if(!(s instanceof ParameterizedType pt) || pt.getRawType()!=TypeRef.class) {
			throw new IllegalStateException("A TypeRef must be created as a direct anonymous subclass with a type argument: new TypeRef<List<Item>>() {}");
		}
		this.type = pt.getActualTypeArguments()[0];
	}

	/**
	 * The referenced type.
	 */
	public Type getType() {
		return type;
	}

	@Override
	public String toString() {
		return "TypeRef<"+type.getTypeName()+">";
	}
}
