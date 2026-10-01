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
package org.monflabs.galtajs.library.java;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;
import org.monflabs.galtajs.AbstractLibrary;
import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSEnvironment.Builder;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.jsonfactory.JSObject.DESC_CHECK;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.array.BuiltinArrayPrototype;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.rt.builtins.standard.function.BuiltinFunction;
import org.monflabs.galtajs.rt.builtins.standard.global.StandardObjects;
import org.monflabs.json.JsonUtil;
import org.monflabs.util.StringFormat;
import org.monflabs.util.iterators.Iterators;
import org.monflabs.util.model.ClassMetadata;
import org.monflabs.util.model.ClassMetadata.AccessManager;
import org.monflabs.util.model.ClassMetadata.ClassInfoCache;
import org.monflabs.util.model.ClassMetadata.ConstructorCache;
import org.monflabs.util.model.ClassMetadata.FieldCache;
import org.monflabs.util.model.ClassMetadata.PropertyCache;
import org.monflabs.util.model.ClassMetadata.MemberCache;
import org.monflabs.util.model.ClassMetadata.MethodCache;
import org.monflabs.util.model.ClassMetadata.ValueAccessor;

/**
 * Java library, based on the Java class metadata.
 */
public class JavaLibrary extends AbstractLibrary implements JSJavaLibrary {
	
	private class JavaClassMetadata extends ClassMetadata {
		private final class JSMethodCache extends MethodCache implements Callable {
			JSMethodCache(Method method) {
				super(method);
			}
			@Override
			public Object call(Object _this, Object[] parameters) {
				Object result = call( (v,c) -> JavaLibrary.this.convertObject(v, c), _this instanceof JavaClass, _this, parameters);
				// A reflective handle (Class.forName(), obj.getClass(), ...) must not give
				// access to a class the access manager refuses to load by name
				if(result instanceof Class<?> c) {
					AccessManager am = getAccessManager();
					if(am!=null && !am.canLoadClass(c.getName())) {
						throw RuntimeUtil.typeError("Java class '{0}' cannot be loaded", c.getName());
					}
				}
				return result;
			}
		}
		private JavaClassMetadata( AccessManager accessManager) {
			super(accessManager);
		}
		@Override
		protected MethodCache createMethodCache(Method method) {
			return new JSMethodCache(method);
		}
		// The library's options narrow what a script sees of a Java object
		@Override
		protected FieldCache findField(Class<?> clazz, String name) {
			return useFields ? super.findField(clazz, name) : null;
		}
		@Override
		protected MethodCache findMethod(Class<?> clazz, String name) {
			return useMethods ? super.findMethod(clazz, name) : null;
		}
		@Override
		protected PropertyCache findProperty(Class<?> clazz, String name) {
			return useProperties ? super.findProperty(clazz, name) : null;
		}
		@Override
		protected BeanPropertyCache findBeanProperty(Class<?> clazz, String name) {
			return useProperties ? super.findBeanProperty(clazz, name) : null;
		}
		@Override
		protected ASSIGNABLE isAssignable(Class<?> c1, Object p2) {
			if(p2==null) {
				// Null is nor valid for primitives
				// Else, we don't assume this is an exact match
				// Should handle UNDEFINED as well
				if (c1.isPrimitive()) {
					return ASSIGNABLE.NO;
				}
				return ASSIGNABLE.EXACT;
			}
			
			if(c1==Class.class) {
				if(p2 instanceof JavaClass || p2.getClass()==Class.class) {
					return ASSIGNABLE.EXACT;
				}
				return ASSIGNABLE.NO;
			}
			
			Class<?> c2 = p2.getClass();
			if(c1==c2) {
				return ASSIGNABLE.EXACT;
			}
			
			// Directly assignable
			if (c1.isAssignableFrom(c2)) {
				return ASSIGNABLE.POSSIBLE;
			}
			// String and Character can be exchanged
			if ((c1 == Character.class && c2 == String.class) || (c2 == Character.class && c1 == String.class)) {
				return ASSIGNABLE.POSSIBLE;
			}
			// Numbers can be converted
			if (Number.class.isAssignableFrom(c1) && Number.class.isAssignableFrom(c2)) {
				return ASSIGNABLE.POSSIBLE;
			}
			// If the parameter is a ScriptFuntion, then we assume it can be adapted to any interface
			if (BuiltinFunction.class.isAssignableFrom(c2)) {
				if(c1.isInterface()) {
					return ASSIGNABLE.POSSIBLE;
				}
			}
			// Ok, not compatible
			return ASSIGNABLE.NO;
		}

	}
		
	private HashMap<String, JavaClass> primitives = new HashMap<String, JavaClass>();
	
	private HashMap<Class<?>, JavaClass> javaClassCache = new HashMap<Class<?>, JavaClass>();

	private boolean useConstructors = true;
	private boolean useFields = true;
	private boolean useMethods = true;
	private boolean useProperties = true;
	
	private ClassMetadata classMetadata;

	public JavaLibrary() {
		this(null);
	}
	public JavaLibrary(AccessManager accessManager) {	
		this.classMetadata = new JavaClassMetadata(accessManager);
		
		// These are needed to create array and use instanceof
		primitives.put("char", getJavaClass(Character.TYPE));
		primitives.put("byte", getJavaClass(Byte.TYPE));
		primitives.put("short", getJavaClass(Short.TYPE));
		primitives.put("int", getJavaClass(Integer.TYPE));
		primitives.put("long", getJavaClass(Long.TYPE));
		primitives.put("float", getJavaClass(Float.TYPE));
		primitives.put("double", getJavaClass(Double.TYPE));
		primitives.put("boolean", getJavaClass(Boolean.TYPE));
	}
	
	@Override
	public void configureEnvironment(Builder builder) {
		builder.supportJavaNative(true);
	}

	@Override
	public void configureStandardObjects(JSEnvironment env, StandardObjects standardObjects) {
		standardObjects.setOwnProperty("Java", new Java(env,this));
	}
	
	public Map<String,JavaClass> getPrimitives() {
		return primitives;
	}

	public ClassMetadata getClassMetadata() {
		return classMetadata;
	}

	public AccessManager getAccessManager() {
		return classMetadata.getAccessManager();
	}

	public ClassLoader getClassLoader(JSEnvironment env) {
		return env.getClassLoader();
	}
	
	@Override
	public synchronized JavaClass getJavaClass(Class<?> clazz) {
		JavaClass jc = javaClassCache.get(clazz);
		if(jc==null) {
			ClassInfoCache ci = classMetadata.getClassInfoCache(clazz);
			jc = new JavaClassImpl(ci);
			javaClassCache.put(clazz, jc);
		}
		return jc;
	}

	@Override
	public JSAccessor createAccessor(JSEnvironment env, Class<?> clazz) {
		if(JavaClassImpl.class.isAssignableFrom(clazz)) {
			return new JavaConstructorAccessor(env);
		}

		if (!acceptClass(clazz)) {
			return null;
		}

		if (clazz.isArray()) {
			return new JavaArrayAccessor(env);
		}

		return new JavaAccessor(env);
	}
	
	
	// This is a requirement to distinguish between the class constructor and the class Object
	//    new MyClass() -> ctor
	//    o.getClass() -> class object
	// JavaClassImpl is the class to apply new to it.
	//    Methods called on it are static
	// Class<?> if for a class instance
	//    Methods called on it are instance ones
	private final class JavaClassImpl implements JavaClass {
		
		private ClassInfoCache ci;
		
		public JavaClassImpl(ClassInfoCache ci) {
			this.ci = ci;
		}

		public ClassInfoCache getClassInfoCache() {
			return ci;
		}
	
		@Override
		public Class<?> getNativeClass() {
			return ci.getNativeClass();
		}
		
		@Override
		public Constructor getSuperClass() {
			Class<?> c = getNativeClass();
			if(c.getSuperclass()!=null) {
				return getJavaClass(c.getSuperclass());
			}
			return null;
		}
		
		@Override
		public String getClassName() {
			Class<?> c = getNativeClass();
			return c.getName();
		}

		@Override
		public Object call(Object _this, @NonNull Object[] parameters) {
			// JavaClass(value): a Java cast, with the numeric conversions a JS
			// number needs to become a Java one
			Class<?> c = getNativeClass();
			Object o = parameters.length>0 ? parameters[0] : null;
			if(o==null || o==RuntimeUtil.UNDEFINED) {
				if(c.isPrimitive()) {
					throw RuntimeUtil.typeError("null or undefined cannot be cast to class {0}", c.getName());
				}
				return null;
			}
			Class<?> boxed = c.isPrimitive() ? java.lang.invoke.MethodType.methodType(c).wrap().returnType() : c;
			if(Number.class.isAssignableFrom(boxed)) {
				if(!(o instanceof Number n)) {
					throw RuntimeUtil.typeError("Value {0} is not a number", RuntimeUtil.objectTypeName(o));
				}
				if(boxed==Byte.class) {
					return JsonUtil.toByte(n);
				} else if(boxed==Short.class) {
					return JsonUtil.toShort(n);
				} else if(boxed==Integer.class) {
					return JsonUtil.toInt(n);
				} else if(boxed==Long.class) {
					return JsonUtil.toLong(n);
				} else if(boxed==Float.class) {
					return JsonUtil.toFloat(n);
				} else if(boxed==Double.class) {
					return JsonUtil.toDouble(n);
				} else if(boxed==BigInteger.class) {
					return JsonUtil.toBigInteger(n);
				} else if(boxed==BigDecimal.class) {
					return JsonUtil.toBigDecimal(n);
				}
			}
			if(boxed==Character.class && o instanceof CharSequence cs && cs.length()==1) {
				return cs.charAt(0);
			}
			if(!boxed.isInstance(o)) {
				throw RuntimeUtil.typeError("Java Object {0} cannot be cast to class {1}", RuntimeUtil.objectTypeName(o), c.getName());
			}
			return o;
		}

		@Override
		public Object constructObject(Object[] parameters, Constructor topConstructor) {
			Class<?> c = getNativeClass();
			if(!useConstructors) {
				throw RuntimeUtil.typeError("Java class '{0}' cannot be created", c);
			}
			AccessManager accessManager = getAccessManager(); 
			if(accessManager!=null && !accessManager.canCreateObject(c)) {
				throw RuntimeUtil.typeError("Java class '{0}' cannot be created", c);
			}

			// Find and call the proper ctor
			// Find the best constructor using 2 passes
			ConstructorCache cache = getClassInfoCache().getConstructors();
			ConstructorCache m = (ConstructorCache) cache.findCallable(true, parameters, true);
			if (m != null) {
				// Create the java object
				try {
					if (parameters != null && parameters.length > 0) {
						Class<?>[] args = m.getArgClasses();
						for (int i = 0; i < parameters.length; i++) {
							parameters[i] = convertObject(parameters[i], args[i]);
						}
					}
					Object o = m.getConstructor().newInstance(parameters);
					if(RuntimeUtil.isPrimitiveType(o)) {
						return RuntimeUtil.primitiveAsObject(JSEnvironment.getEnvironment(),o);
					}
					return o;
				} catch (Exception e) {
					throw RuntimeUtil.error(e, "Error while calling java constructor '{0}'",
							ClassMetadata.getMethodSignature(c.getName(), parameters));
				}
			}
			throw RuntimeUtil.error("Cannot find java public constructor '{0}'",
					ClassMetadata.getMethodSignature(c.getName(), parameters));
		}

		@Override
		public boolean canConstructArray() {
			return true;
		}

		@Override
		public final Object constructArray(int dimensions, long size) {
			Class<?> c = getNativeClass();
			AccessManager accessManager = getAccessManager(); 
			if(accessManager!=null && !accessManager.canCreateArray(c)) {
				throw RuntimeUtil.typeError("Java array '{0}' cannot be created", c);
			}
			return _constructArray(c, dimensions, size);
		}

		protected Object _constructArray(Class<?> c, int dimensions, long size) {
			if (dimensions > 0) {
				for (int i = 0; i < dimensions; i++) {
					c = getArrayClass(c);
				}
			}
			if(size>Integer.MAX_VALUE) {
            	throw RuntimeUtil.typeError("Invalid size {0}", size);
			}
			return Array.newInstance(c, (int)size);
		}

		private Class<?> getArrayClass(Class<?> c) {
			return Array.newInstance(c, 0).getClass();
		}

		@Override
		public String toString() {
			return StringFormat.format("JavaClass: {0}",getNativeClass());
		}
	}
	

	
	/////////////////////////////////////////////////////////////////////////////////////
	// Options
	/////////////////////////////////////////////////////////////////////////////////////

	public boolean isUseConstructors() {
		return useConstructors;
	}

	/**
	 * Whether scripts may construct Java objects ({@code new JavaClass(...)}).
	 * Like the other {@code setUse*} options, set it before the library is
	 * used: members already looked up are cached.
	 * @param useConstructors true to allow
	 */
	public void setUseConstructors(boolean useConstructors) {
		this.useConstructors = useConstructors;
	}

	public boolean isUseFields() {
		return useFields;
	}

	/**
	 * Whether scripts see the public fields of Java objects.
	 * @param useFields true to expose fields
	 */
	public void setUseFields(boolean useFields) {
		this.useFields = useFields;
	}

	public boolean isUseMethods() {
		return useMethods;
	}

	/**
	 * Whether scripts see the public methods of Java objects.
	 * @param useMethods true to expose methods
	 */
	public void setUseMethods(boolean useMethods) {
		this.useMethods = useMethods;
	}

	public boolean isUseProperties() {
		return useProperties;
	}

	/**
	 * Whether scripts see getter/setter pairs of Java objects as properties.
	 * @param useProperties true to expose properties
	 */
	public void setUseProperties(boolean useProperties) {
		this.useProperties = useProperties;
	}

	protected boolean acceptClass(Class<?> c) {
		return true;
	}
	protected String getFullClassName(String name) {
		// We load the default classes in java.lang
		return null;
	}


	/////////////////////////////////////////////////////////////////////////////////////
	// Class alias management
	/////////////////////////////////////////////////////////////////////////////////////

	@Override
	public JavaClass loadClass(JSEnvironment env, String className) {
		AccessManager accessManager = getAccessManager(); 
		if(accessManager!=null && !accessManager.canLoadClass(className)) {
			throw RuntimeUtil.typeError("Java class '{0}' cannot be loaded", className);
		}
		
		try {
			if(primitives.containsKey(className)) {
				return primitives.get(className);
			}
			Class<?> clazz = getClassLoader(env).loadClass(className);
			return getJavaClass(clazz);
		} catch (Exception e) {
			throw RuntimeUtil.error(e, "Error while loading Java class '{0}'", className);
		}
	}

	
	
	/////////////////////////////////////////////////////////////////////////////////////
	// Java Proxy
	/////////////////////////////////////////////////////////////////////////////////////

	public Object getProxy(Callable function, Class<?> targetClass) {
		AccessManager accessManager = getAccessManager(); 
		if(accessManager!=null && !accessManager.canProxy(targetClass)) {
			throw RuntimeUtil.typeError("Java class '{0}' cannot be proxied", targetClass.getName());
		}
		
		if (!targetClass.isInterface()) {
			throw RuntimeUtil.error("ScriptFunction must match an interface, while '{0}' is not",
					targetClass.getName());
		}
		return Proxy.newProxyInstance(targetClass.getClassLoader(), new Class[] { targetClass },
				new FunctionInvocationHandler(function));
	}

	protected static class FunctionInvocationHandler implements InvocationHandler {

		private Callable function;

		public FunctionInvocationHandler(Callable function) {
			this.function = function;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			// Object's own methods identify the proxy itself - they must not be
			// routed to the function (a proxy put in a HashMap called it, and
			// failed, for hashCode())
			if (method.getDeclaringClass() == Object.class) {
				switch (method.getName()) {
				case "hashCode":
					return System.identityHashCode(proxy);
				case "equals":
					return args != null && args.length == 1 && proxy == args[0];
				case "toString":
					return proxy.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(proxy));
				default:
					break;
				}
			}
			// invokeDefault() since Java 16
			if (method.isDefault()) {
				return InvocationHandler.invokeDefault(proxy, method, args);
			}
			// How to make sure that this is the only method to override
			// (check that it is a FunctionalInterface?)
			return function.call(null, args!=null ? args : RuntimeUtil.EMPTY_PARAMS);
		}
	}
	

	/////////////////////////////////////////////////////////////////////////////////////
	// Member access
	/////////////////////////////////////////////////////////////////////////////////////
	
	public static abstract class ObjectWrapper extends AbstractMap<String,Object> {
		private Set<String> keys;
		public ObjectWrapper() {
		}
		protected Set<String> getKeys() {
			if(keys==null) {
				keys = findKeys();
			}
			return keys;
		}
		protected abstract Set<String> findKeys();
	    @Override
		public int size() {
	    	return getKeys().size();
	    }
		@Override
		public Set<Entry<String, Object>> entrySet() {
			Set<String> keys = getKeys();
			return new AbstractSet<Map.Entry<String,Object>>() {
				@Override
				public Iterator<Entry<String, Object>> iterator() {
					return Iterators.map(keys.iterator(), (k) -> {
						return new Entry<String, Object>() {
							@Override
							public String getKey() {
								return k;
							}
							@Override
							public Object getValue() {
								return get(k);
							}
							@Override
							public Object setValue(Object value) {
								return put(k,value);
							}
						};
					});
				}
				@Override
				public int size() {
					return keys.size();
				}
	            @Override
	            public boolean isEmpty() {
					return keys.isEmpty();
	            }
	            @Override
	            public void clear() {
	            	throw new IllegalStateException("Cannot clear a Java object");
	            }
	            @Override
	            public boolean contains(Object key) {
	    	    	return keys.contains(key);
	            }
			};
		}
	}

	public static abstract class JavaLibraryAccessor extends JSAccessor {
		public JavaLibraryAccessor(JSEnvironment env) {
			super(env);
		}
	}

	// Java classes
	// Should it inherit from JavaAccessor
	// Should we still have JavaClass?
	protected class JavaConstructorAccessor extends JavaLibraryAccessor {

		public JavaConstructorAccessor(JSEnvironment env) {
			super(env);
		}

		@Override
		public String getClassName(Object _this) {
			return "constructor "+((JavaClassImpl)_this).getNativeClass().getName();
		}	

		@Override
		public PropertyDescriptor getOwnPropertyDescriptor(Object _this, String member) {
			// The members of the Java class itself, not of this JS wrapper
			if(!member.isEmpty() && member.charAt(0)=='$') {
				member = member.substring(1);
			}
			MemberCache m = ((JavaClassImpl)_this).getClassInfoCache().getMembers(member);
			if (m != null) {
				if (m instanceof MethodCache) {
					return PropertyDescriptor.DESC_JAVA_METHOD;
				} else {
					return PropertyDescriptor.DESC_JAVA_FIELD;
				}
			}
			return null;
		}
		
		@SuppressWarnings({ "rawtypes", "unchecked" })
		@Override
		public Iterator<Map.Entry<Object,Object>> ownPropertyEntries(Object _this, boolean strings, boolean symbols, boolean enumerableOnly) {
			// TODO>: Add access to the object dynamic properties??
			if(strings) { 
				ClassInfoCache ci =  ((JavaClassImpl)_this).getClassInfoCache();
				return (Iterator)(new ObjectWrapper() {
					@Override
					protected Set<String> findKeys() {
						return ci.getMethods();
					}
				    @Override
					public Object get(Object key) {
				    	return getOwnProperty(_this,(String)key,RuntimeUtil.UNDEFINED,_this);
				    }
				    @Override
				    public Object put(String key, Object value) {
				    	Object old = getOwnProperty(_this,(String)key,RuntimeUtil.UNDEFINED,_this);
				    	setProperty(_this,(String)key,value, null, DESC_CHECK.CHECK);
				    	return old;
				    }
				}).entrySet().iterator();
			}
			return Iterators.empty();
		}

		@Override
		public Object getOwnProperty(Object _this, String member, Object defaultValue, Object receiver) {
			if(!member.isEmpty() && member.charAt(0)=='$') {
				member = member.substring(1);
			}
			JavaClassImpl jc = (JavaClassImpl) _this;
			MemberCache m = jc.getClassInfoCache().getMembers(member);
			if (m != null) {
				// Value accessor
				if (m instanceof ValueAccessor acc) {
					return acc.get(null);
				}
				// If it is a method, then return a callable object
				if (m instanceof MethodCache mc) {
					return mc;
				}
			}

			return defaultValue;
			//return super._getOwnMember(env,base,  _this, member, defaultValue);
		}

//		@Override
//		public Object constructObject(Object _ctor, Object[] parameters) {
//			JavaClassImpl jc = (JavaClassImpl) _ctor;
//
//			AccessManager accessManager = getAccessManager(); 
//			if(accessManager!=null && !accessManager.canCreateObject(jc.getClass())) {
//				throw RuntimeUtil.typeError("Java class '{0}' cannot be created", jc.getClass());
//			}
//
//			// Find and call the proper ctor
//			// Find the best constructor using 2 passes
//			ConstructorCache cache = jc.getClassInfoCache().getConstructors();
//			ConstructorCache m = (ConstructorCache) cache.findCallable(true, parameters, true);
//			if (m != null) {
//				// Create the java object
//				try {
//					if (parameters != null && parameters.length > 0) {
//						Class<?>[] args = m.getArgClasses();
//						for (int i = 0; i < parameters.length; i++) {
//							parameters[i] = convertObject(parameters[i], args[i]);
//						}
//					}
//					return m.getConstructor().newInstance(parameters);
//				} catch (Exception e) {
//					throw JSRuntimeException.jsRuntimeException(e, "Error while calling java constructor '{0}'",
//							ClassMetadata.getMethodSignature(jc.getNativeClass().getName(), parameters));
//				}
//			}
//			throw JSRuntimeException.jsRuntimeException("Cannot find java public constructor '{0}'",
//					ClassMetadata.getMethodSignature(jc.getNativeClass().getName(), parameters));
//		}
//
//		@Override
//		public final Object constructArray(Object _ctor, int dimensions, long size) {
//			JavaClassImpl jc = (JavaClassImpl) _ctor;
//			
//			AccessManager accessManager = getAccessManager(); 
//			if(accessManager!=null && !accessManager.canCreateArray(jc.getClass())) {
//				throw RuntimeUtil.typeError("Java array '{0}' cannot be created", jc.getClass());
//			}
//			
//			Class<?> c = jc.getNativeClass();
//			return _constructArray(c, dimensions, size);
//		}
//
//		protected Object _constructArray(Class<?> c, int dimensions, long size) {
//			if (dimensions > 0) {
//				for (int i = 0; i < dimensions; i++) {
//					c = getArrayClass(c);
//				}
//			}
//			if(size>Integer.MAX_VALUE) {
//            	throw RuntimeUtil.typeError("Invalid size {0}", size);
//			}
//			return Array.newInstance(c, (int)size);
//		}
//
//		private Class<?> getArrayClass(Class<?> c) {
//			return Array.newInstance(c, 0).getClass();
//		}
	}
	
	public class JavaAccessor extends JavaLibraryAccessor {

		public JavaAccessor(JSEnvironment env) {
			super(env);
		}
		
		@Override
		public String getClassName(Object _this) {
			return _this.getClass().getName();
		}	

		//
		// TODO: FOR NOW
		@Override
		public PropertyDescriptor getOwnPropertyDescriptor(Object _this, String member) {
			MemberCache m = classMetadata.getClassInfoCache(_this.getClass()).getMembers(member);
			if (m != null) {
				if (m instanceof MethodCache) {
					return PropertyDescriptor.DESC_JAVA_METHOD;
				} else {
					return PropertyDescriptor.DESC_JAVA_FIELD;
				}
			}
			return null;
		}
		
		@SuppressWarnings({ "rawtypes", "unchecked" })
		@Override
		public Iterator<Map.Entry<Object,Object>> ownPropertyEntries(Object _this, boolean strings, boolean symbols, boolean enumerableOnly) {
			// Should we access the dynamic properties?
			if(strings) {
				Iterator indexesKeys = null;
				if(_this instanceof List l) {
					indexesKeys = Iterators.map(
							Iterators.intSequence(0,l.size()), 
							v-> newEntry(Integer.toString(v),l.get(v)));
				}
				// Should we simplify this as we don't need a 'map' anymore?
				ClassInfoCache ci =  classMetadata.getClassInfoCache(_this.getClass());
				boolean instanceReceiver = !(_this instanceof JavaClass);
				Iterator m = (new ObjectWrapper() {
					@Override
					protected Set<String> findKeys() {
						if(!enumerableOnly) {
							return ci.getAllMembers();
						} else {
							return ci.getValueAccessors();
						}
					}
				    @Override
					public Object get(Object key) {
				    	// Skip static ValueAccessors when enumerating an
				    	// INSTANCE receiver - the accessor would reject the
				    	// instance target and crash. Static access is still
				    	// available via the JavaClass receiver.
				    	if (instanceReceiver) {
				    		MemberCache mc = ci.getMembers((String)key);
				    		if (mc instanceof ValueAccessor va && va.isStatic()) return RuntimeUtil.UNDEFINED;
				    	}
				    	return getOwnProperty(_this,(String)key,RuntimeUtil.UNDEFINED,_this);
				    }
				    @Override
				    public Object put(String key, Object value) {
				    	Object old = getOwnProperty(_this,(String)key,RuntimeUtil.UNDEFINED,_this);
				    	setProperty(_this,(String)key,value, null,DESC_CHECK.CHECK);
				    	return old;
				    }
				}).entrySet().iterator();
				if(indexesKeys!=null) {
					return Iterators.concat(indexesKeys,m);
				}
				return m;
			}
			return Iterators.empty();
		}
		
		@Override
		public Object getOwnProperty(Object _this, String member, Object defaultValue, Object receiver) {
			if(!member.isEmpty() && member.charAt(0)=='$') {
				member = member.substring(1);
			}
			Class<?> clazz = _this instanceof JavaClass jc ? jc.getNativeClass() : _this.getClass();
			MemberCache m = classMetadata.getClassInfoCache(clazz).getMembers(member);
			if (m != null) {
				if (m instanceof ValueAccessor) {
					ValueAccessor acc = (ValueAccessor) m;
					return acc.get(_this);
				}
				// If it is a method, then return a callable object
				if (m instanceof MethodCache mc) {
					return mc;
				}
			}

			return defaultValue;
		}

		@Override
		public Object getOwnProperty(Object _this, long index, Object defaultValue, Object receiver) {
			if (_this instanceof List<?> list) {
				if(index>Integer.MAX_VALUE) {
	            	throw RuntimeUtil.typeError("Invalid index value {0}", index);
				}
				return list.get((int)index);
			}
			return defaultValue;
		}

		@Override
		public boolean setOwnProperty(Object _this, String member, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
			if (super.setOwnProperty(_this, member, value, desc, check, receiver)) {
				return true;
			}

			MemberCache m = classMetadata.getClassInfoCache(_this.getClass()).getMembers(member);
			if (m != null) {
				if (m instanceof ValueAccessor) {
					ValueAccessor acc = (ValueAccessor) m;
					acc.set( (v,c) -> convertObject(v, c), _this, value);
					return true;
				}
			}

			return false;
		}

		@SuppressWarnings("unchecked")
		@Override
		public boolean setOwnProperty(Object _this, long index, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
			if (_this instanceof List<?> list) {
				if(index>Integer.MAX_VALUE) {
	            	throw RuntimeUtil.typeError("Invalid index value {0}", index);
				}
				((List<Object>) list).set((int)index, value);
				return true;
			}
			return false;
		}
	}
	
	public class JavaArrayAccessor extends JavaLibraryAccessor {

		public JavaArrayAccessor(JSEnvironment env) {
			super(env);
		}

		@Override
		public Object getPrototype(Object _this) {
			return BuiltinArrayPrototype.get(getEnvironment());
		}
		
		@Override
		public String getClassName(Object _this) {
			// "int[]", "java.lang.String[]" - not the binary name "[I[]"
			return _this.getClass().getComponentType().getName()+"[]";
		}	

		@Override
		public PropertyDescriptor getOwnPropertyDescriptor(Object _this, String member) {
			if ("length".equals(member)) {
				// Non-enumerable, matching real JS Array/String "length" and
				// this class's own ownPropertyEntries()/getOwnPropertyDescriptors()
				// (both already exclude "length" from enumeration) - was
				// DESC_READONLY_PROP (enumerable:true), an inconsistency only
				// exposed once a caller (e.g. for-in's keyIterator) started
				// consulting this descriptor instead of just ownPropertyEntries().
				return PropertyDescriptor.DESC_READONLY_HIDDEN_PROP;
			}
			return null;
		}

		@Override
		public JSObject getOwnPropertyDescriptors(JSObject descriptors, Object _this) {
			super.getOwnPropertyDescriptors(descriptors,_this);
			descriptors.setOwnProperty("length",PropertyDescriptor.DESC_READONLY_HIDDEN_PROP);
			return descriptors;
		}

		@SuppressWarnings({ "unchecked", "rawtypes" })
		@Override
		public Iterator<Map.Entry<Object,Object>> ownPropertyEntries(Object _this, boolean strings, boolean symbols, boolean enumerableOnly) {
			if(strings) {
				return (Iterator)Iterators.concat(
					Iterators.map(Iterators.longSequence(0,Array.getLength(_this)), (v) -> {
						return newEntry(Long.toString(v),Array.get(_this,(int)v));
					}),
					!enumerableOnly ? Iterators.single(newEntry("length",Array.getLength(_this))) : null
				);
			}
			return Iterators.empty();
		}

		@Override
		public Object getOwnProperty(Object _this, String member, Object defaultValue, Object receiver) {
			if ("length".equals(member)) {
				return Array.getLength(_this);
			}
			long index = RuntimeUtil.memberIndex(member);
			if(index!=Long.MIN_VALUE) {
				return getOwnProperty(_this,index,defaultValue,_this);
			}
			return super.getOwnProperty(_this,member,defaultValue, receiver);
		}

		@Override
		public Object getOwnProperty(Object _this, long index, Object defaultValue, Object receiver) {
			if(index>Integer.MAX_VALUE) {
	        	throw RuntimeUtil.typeError("Invalid Index {0}", index);
			}
			int size = Array.getLength(_this);
			if (index >= 0 && index < size) {
				return Array.get(_this, (int)index);
			}
			return super.getOwnProperty(_this, index,defaultValue, receiver);
		}

		@Override
		public Object getOwnProperty(Object _this, Symbol symbol, Object defaultValue, Object receiver) {
			if(symbol==Symbol.IS_CONCAT_SPREDABLE) {
				return true;
			}
			return super.getOwnProperty(_this, symbol,defaultValue, receiver);
		}

		@Override
		public boolean setOwnProperty(Object _this, long index, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
			if(index>Integer.MAX_VALUE) {
	        	throw RuntimeUtil.typeError("Invalid Index {0}", index);
			}
			int size = Array.getLength(_this);
			if (index >= 0 && index < size) {
				Array.set(_this, (int)index, value);
				return true;
			}
			throw RuntimeUtil.error("Invalid Java Array index {0}, max is {1}", index, size);
		}
	}
	

	private final Object convertObject(Object value, Class<?> targetClass) {
		if(value instanceof JavaClass jc) {
			return jc.getNativeClass();
		}
		if(value instanceof Callable function) {
			return getProxy(function, targetClass);
		}
		return ClassMetadata.convertObject(value, targetClass);
	}

}