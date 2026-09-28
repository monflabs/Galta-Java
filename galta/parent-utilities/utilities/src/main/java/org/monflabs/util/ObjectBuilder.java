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

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

import org.monflabs.util.builder.Required;

/**
 * Base class for builders.
 * <p>
 * {@link #build()} always calls {@link #validate()}. The {@link Required} field annotations are a
 * development-time check only: they are validated when {@link DebugMode#isDebugMode()} is true
 * (a debugger is attached) and are <b>not</b> checked in production. A builder that must reject
 * missing values in production has to check them in {@link #validate()}.
 */ 
public abstract class ObjectBuilder<T> {
	

//	// Convenience for the fluid patter
//	public ObjectBuilder<T> configure(Consumer<ObjectBuilder<T>> configurator) {
//		configurator.accept(this);
//		return this;
//	}

	
	protected void validate() {
	}

	public final T build() {
		if(DebugMode.isDebugMode()) {
			_validateAnnotations();
		}
		validate();
		return _build();
	}

	protected void _validateAnnotations() {
		try {
			for( Class<?> c = getClass(); c!=Object.class; c=c.getSuperclass() ) {
				Field[] fields = c.getDeclaredFields();
				for(int i=0; i<fields.length; i++) {
					Field f = fields[i];
					Annotation[] annotations = f.getAnnotations();
					if(annotations.length>0) {
						f.setAccessible(true);
						Object v = f.get(this);
						for(Annotation a: annotations) {
							if(a.annotationType()==Required.class) {
								if(v==null) {
									throw new ObjectBuilderException(null, "Field {0} is required", f.getName());
								}
							}
						}
					}
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