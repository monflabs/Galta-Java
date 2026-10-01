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
package org.monflabs.util;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.monflabs.util.builder.Required;

/**
 * Base class for builders.
 * <p>
 * {@link #build()} checks the fields annotated with {@link Required} (none may be null),
 * then calls {@link #validate()}, then {@link #_build()}. The {@link Required} check used to
 * run only with a debugger attached ({@link DebugMode}): it now always runs.
 */
public abstract class ObjectBuilder<T> {

	// The @Required fields of each builder class, its super classes included
	private static final ClassValue<Field[]> REQUIRED_FIELDS = new ClassValue<>() {
		@Override
		protected Field[] computeValue(Class<?> type) {
			List<Field> required = new ArrayList<>();
			for( Class<?> c = type; c!=null && c!=Object.class; c=c.getSuperclass() ) {
				for(Field f: c.getDeclaredFields()) {
					if(f.isAnnotationPresent(Required.class)) {
						f.setAccessible(true);
						required.add(f);
					}
				}
			}
			return required.toArray(new Field[required.size()]);
		}
	};

//	// Convenience for the fluid patter
//	public ObjectBuilder<T> configure(Consumer<ObjectBuilder<T>> configurator) {
//		configurator.accept(this);
//		return this;
//	}


	protected void validate() {
	}

	public final T build() {
		_validateAnnotations();
		validate();
		return _build();
	}

	/**
	 * Checks the fields annotated with {@link Required}.
	 * @throws ObjectBuilderException naming the first required field that is null
	 */
	protected void _validateAnnotations() {
		try {
			for(Field f: REQUIRED_FIELDS.get(getClass())) {
				if(f.get(this)==null) {
					throw new ObjectBuilderException(null, "Field {0} is required", f.getName());
				}
			}
		} catch(IllegalAccessException e) {
			throw new ObjectBuilderException(e, "Internal error while validating the builder annotations");
		}
	}

	protected abstract T _build();

	
	//
	// Validation helpers
	//
	protected ObjectBuilderException exception(String msg, Object... params) {
		return new ObjectBuilderException(null, msg,params);
	}
	protected void assertNotNull(Object o, String msg) {
		if(o==null) {
			throw exception("Object {0} cannot be null", msg); 
		}
	}
}