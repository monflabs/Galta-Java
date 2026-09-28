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
package org.monflabs.util.model;

import org.eclipse.jdt.annotation.NonNull;

/**
 * Basic Java library with support of collections (no reflection and no beans). 
 */
public class JavaObjectAccessor implements ModelAccessor {

	private boolean useExceptions;
	
	public JavaObjectAccessor() {
	}

	protected ModelException newInvalidMember(Object instance, String member) throws ModelException {
		return newInvalidMember(null, instance, member);
	}
	protected ModelException newInvalidMember(Throwable ex, Object instance, String member) throws ModelException {
		return new ModelException(ex,"Invalid member {0} for class {1}",member,instance.getClass().getName());
	}
	protected ModelException newInvalidIndex(Object instance, int index) throws ModelException {
		return newInvalidIndex(null, instance, index);
	}
	protected ModelException newInvalidIndex(Throwable ex, Object instance, int index) throws ModelException {
		return new ModelException(ex,"Invalid index {0} for class {1}",index,instance.getClass().getName());
	}
	protected ModelException newInvalidContructor(String type) throws ModelException {
		return newInvalidContructor(null,type);
	}
	protected ModelException newInvalidContructor(Throwable ex, String type) throws ModelException {
		throw new ModelException(ex,"Cannot create object of type {0}",type);
	}
	protected ModelException newInvalidArray(String type) throws ModelException {
		return newInvalidArray(null,type);
	}
	protected ModelException newInvalidArray(Throwable ex, String type) throws ModelException {
		throw new ModelException(ex,"Cannot create array of type {0}",type);
	}
	protected ModelException newInvalidCall(Object instance, String methodName) throws ModelException {
		return newInvalidCall(null, instance, methodName);
	}
	protected ModelException newInvalidCall(Throwable ex, Object instance, String methodName) throws ModelException {
		return new ModelException(ex,"Cannot call member {0} for class {1}",methodName,instance.getClass().getName());
	}

	
	public boolean isUseExceptions() {
		return useExceptions;
	}

	public void setUseExceptions(boolean useExceptions) {
		this.useExceptions = useExceptions;
	}

	@Override
	public Object getMember(Object instance, String member) throws ModelException {
		if(useExceptions) {
			throw newInvalidMember(instance, member);
		}
		return UNHANDLED;
	}
	
	@Override
	public boolean putMember(Object instance, String member, Object value) throws ModelException {
		if(useExceptions) {
			throw newInvalidMember(instance, member);
		}
		return false;
	}
	
	// Member indexed value access
	@Override
	public Object getMember(Object instance, int index) throws ModelException {
		if(useExceptions) {
			throw newInvalidIndex(instance, index);
		}
		return UNHANDLED;
	}
	
	@Override
	public boolean putMember(Object instance, int index, Object value) throws ModelException {
		if(useExceptions) {
			throw newInvalidIndex(instance, index);
		}
		return false;
	}
	
	// Object construction
	@Override
	public Object constructObject(String type, @NonNull Object[] parameters) throws ModelException {
		if(useExceptions) {
			throw newInvalidContructor(null,type);
		}
		return UNHANDLED;
	}
	@Override
	public Object constructArray(String type, int size) throws ModelException {
		if(useExceptions) {
			throw newInvalidArray(null,type);
		}
		return UNHANDLED;
	}

	// Method call
	@Override
	public Object call(Object instance, String methodName, @NonNull Object[] parameters) throws ModelException {
		if(useExceptions) {
			throw newInvalidCall(instance, methodName);
		}
		return UNHANDLED;
	}
}