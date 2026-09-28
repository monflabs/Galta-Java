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


/**
 * Model data accessor. 
 */
public interface ModelAccessor {

	public static final Object UNHANDLED = new Object();
	
	// Member value access
	public Object getMember(Object instance, String member) throws ModelException;
	public boolean putMember(Object instance, String member, Object value) throws ModelException;
	
	// Member indexed value access
	public Object getMember(Object instance, int index) throws ModelException;
	public boolean putMember(Object instance, int index, Object value) throws ModelException;
	
	// Object construction
	public Object constructObject(String type, Object[] parameters) throws ModelException;
	public Object constructArray(String type, int size) throws ModelException;

	// Method call
	public Object call(Object instance, String methodName, Object[] parameters) throws ModelException;
}