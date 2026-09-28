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

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;
import org.monflabs.util.model.ClassMetadata.ConstructorCache;
import org.monflabs.util.model.ClassMetadata.MemberCache;
import org.monflabs.util.model.ClassMetadata.MethodCache;
import org.monflabs.util.model.ClassMetadata.ValueAccessor;


/**
 * Basic Java library with support of collections (no reflection and no beans). 
 */
public class PojoAccessor extends JavaObjectAccessor {
	
	// This is temporary
	private static PojoAccessor instance = new PojoAccessor();
	public static PojoAccessor getDataAccessor() {
		return instance;
	}

	private ClassMetadata classMetaData;
	private boolean permissiveConverter;

	public PojoAccessor() {
		this(null);
	}
	public PojoAccessor(ClassMetadata classMetaData) {
		this(classMetaData,false);
	}
	public PojoAccessor(ClassMetadata classMetaData, boolean permissiveConverter) {
		this.classMetaData = classMetaData!=null ? classMetaData : new ClassMetadata(null);
		this.permissiveConverter = permissiveConverter;
	}

	protected boolean checkInstance(Object instance) throws ModelException {
		if(instance==null) {
			if(isUseExceptions()) {
				throw new ModelException(null,"Instance is null");
			}
			return false;
		}
		return checkClass(instance.getClass());
	}

	protected boolean checkClass(Class<?> c) throws ModelException {
		if(!acceptClass(c)) {
			if(isUseExceptions()) {
				throw new ModelException(null,"Class {0} cannot be accessed",c.getName());
			}
			return false;
		}
		return true;
	}

	////////////////////////////////////////////////////////////////////////////////////
	// Class alias management
	/////////////////////////////////////////////////////////////////////////////////////

	public ClassLoader getClassLoader() {
		return getClass().getClassLoader();
	}

	public Class<?> loadClass(String className) {
		// Try to load the class
		try {
			Class<?> c = getClassLoader().loadClass(className);
			return c;
		} catch(Exception e) {}
		return null;
	}

	public boolean acceptClass(Class<?> c) {
		return true;
	}

	public ClassMetadata getClassMetadata() {
		return classMetaData;
	}

	protected final Object convertObject(Object value, Class<?> targetClass) throws ModelException {
		return ClassMetadata.convertObject(value, targetClass, permissiveConverter);
	}


	/////////////////////////////////////////////////////////////////////////////////////
	// Member access
	/////////////////////////////////////////////////////////////////////////////////////

	@Override
	public Object getMember(Object instance, String member) throws ModelException {
		if(!checkInstance(instance)) {
			return UNHANDLED;
		}

		if(instance instanceof Map map) {
			if(map.containsKey(member)) {
				return map.get(member);
			}
		} else {
			Class<?> c = instance.getClass();
			ValueAccessor va = getClassMetadata().getClassInfoCache(c).getValueAccessor(member);
			if(va!=null) {
				return va.get(instance);
			}
		}
		
		if(isUseExceptions()) {
			throw newInvalidMember(instance, member);
		}
        return UNHANDLED;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public boolean putMember(Object instance, String member, Object value) throws ModelException {
		if(!checkInstance(instance)) {
			return false;
		}

		if(instance instanceof Map map) {
			((Map)map).put(member,value);
			return true;
		}
		
		Class<?> c = instance.getClass();
		ValueAccessor va = getClassMetadata().getClassInfoCache(c).getValueAccessor(member);
		if(va!=null) {
			va.set(PojoAccessor.this::convertObject,instance,value);
			return true;
		}

		if(isUseExceptions()) {
			throw newInvalidMember(instance, member);
		}
        return false;
	}


	/////////////////////////////////////////////////////////////////////////////////////
	// Indexed access
	/////////////////////////////////////////////////////////////////////////////////////

	@Override
	public Object getMember(Object instance, int index) throws ModelException {
		if(!checkInstance(instance)) {
			return UNHANDLED;
		}

		if(instance instanceof List l) {
			if(index>=0 && index<l.size()) {
				return l.get(index);
			}
		} else {
			if(instance.getClass().isArray()) {
				int size = Array.getLength(instance);
				if(index>=0 && index<size) {
					return Array.get(instance, index);
				}
			}
		}
		
		// We don't access the bean properties as of now

		if(isUseExceptions()) {
			throw newInvalidIndex(instance, index);
		}
        return UNHANDLED;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public boolean putMember(Object instance, int index, Object value) throws ModelException {
		if(!checkInstance(instance)) {
			return false;
		}
		
		if(instance instanceof List l) {
			if(index>=0 && index<l.size()) {
				((List)instance).set(index,value);
				return true;
			}
		}
		if(instance.getClass().isArray()) {
			int size = Array.getLength(instance);
			if(index>=0 && index<size) {
				Array.set(instance,index,convertObject(value,instance.getClass().getComponentType()));
				return true;
			}
		}
		
		// We don't access the bean properties as of now

		if(isUseExceptions()) {
			throw newInvalidIndex(instance, index);
		}
		return false;
	}


	/////////////////////////////////////////////////////////////////////////////////////
	// Object construction
	/////////////////////////////////////////////////////////////////////////////////////

	@SuppressWarnings("deprecation")
	@Override
	public Object constructObject(String type, Object[] parameters) throws ModelException {
		Class<?> c = loadClass(type);
		if(c==null) {
			if(isUseExceptions()) {
				throw new ModelException(null,"Cannot load class {0}",type);
			}
			return UNHANDLED;
		}
		if(!checkClass(c)) {
			return UNHANDLED;
		}
		// No paramaters
		if(parameters==null || parameters.length==0) {
			try {
				return c.newInstance();
			} catch(Exception ex) {
				if(isUseExceptions()) {
					throw newInvalidContructor(ex,c.getName());
				}
				return UNHANDLED;
			}
		}
        // Find the best constructor using 2 passes
        ConstructorCache cache = getClassMetadata().getClassInfoCache(c).getConstructors();
        ConstructorCache m = (ConstructorCache)cache.findCallable(null,parameters,true);
        if(m!=null) {
            // Create the java object
            try {
            	// As for call(): the constructor may have been accepted with conversions
            	// (e.g. Double -> int), so convert the arguments before invoking
            	Object[] converted = convertArguments(m.getArgClasses(), parameters);
                return m.constructor.newInstance(converted);
            } catch( Exception e ) {
				if(isUseExceptions()) {
	                throw new ModelException(e,"Error while calling java constructor '{0}'",ClassMetadata.getMethodSignature(c.getName(),parameters));
				}
				return UNHANDLED;
            }
        }
		if(isUseExceptions()) {
			throw newInvalidContructor(null,c.getName());
		}
		return UNHANDLED;
	}

	@Override
	public Object constructArray(String type, int size) throws ModelException {
		Class<?> c = loadClass(type);
		if(c==null) {
			if(isUseExceptions()) {
				throw new ModelException(null,"Cannot load class {0}",type);
			}
			return UNHANDLED;
		}
		if(!checkClass(c)) {
			return UNHANDLED;
		}
		try {
			return Array.newInstance(c, size);
		} catch(Exception ex) {
			if(isUseExceptions()) {
				throw newInvalidArray(ex,c.getName());
			}
			return UNHANDLED;
		}
	}
	
	
	/////////////////////////////////////////////////////////////////////////////////////
	// Method call
	/////////////////////////////////////////////////////////////////////////////////////
	
	@Override
	public Object call(Object instance, String methodName, @NonNull Object[] parameters) throws ModelException {
		if(!checkInstance(instance)) {
			return UNHANDLED;
		}
		
		//PHIL: this doesn't work yet for public methods...
		// Moreover, we should better distinguish the different cache as it is not like Javascript
		// Find the best method using 2 passes
		Class<?> c = instance.getClass();
		// getMethod() walks the member chain: a property or field with the same name
		// is listed first and used to hide the method
		MethodCache mc = getClassMetadata().getClassInfoCache(c).getMethod(methodName);
		if(mc!=null) {
			MethodCache m = (MethodCache)mc.findCallable(false,parameters,true);
	        if(m!=null) {
	            try {
	            	// The overload was accepted with conversions (e.g. Double -> int),
	            	// so convert the arguments before invoking - into a copy, the caller's
	            	// array is left untouched
	            	Object[] converted = convertArguments(m.argClasses, parameters);
	            	// Through the public declaring class/interface when the method is declared
	            	// by a non-public class (e.g. the List returned by List.of())
	                return m.invoke(instance, converted);
	            } catch( InvocationTargetException e ) {
	        		if(isUseExceptions()) {
	        			throwInvocationTargetException(e);
	        		}
        			return UNHANDLED;
	            } catch( Exception e ) {
        			if(isUseExceptions()) {
    	                throw new ModelException(e,"Error while calling method {0} of class {1}",ClassMetadata.getMethodSignature(methodName,parameters),c.getName());
        			}
        			return UNHANDLED;
	            }
	        }
		}
		if(isUseExceptions()) {
		    throw new ModelException(null,"Cannot find public method {0} for class {1}",ClassMetadata.getMethodSignature(methodName,parameters),c.getName());
		}
		return UNHANDLED;
	}
	private Object[] convertArguments(Class<?>[] argClasses, Object[] parameters) {
		if(argClasses==null || parameters.length!=argClasses.length) {
			return parameters;
		}
		Object[] converted = new Object[parameters.length];
		for(int i=0; i<parameters.length; i++) {
			converted[i] = convertObject(parameters[i], argClasses[i]);
		}
		return converted;
	}

	protected void throwInvocationTargetException(InvocationTargetException e) throws ModelException {
        throw new ModelException(e.getTargetException());
	}
}