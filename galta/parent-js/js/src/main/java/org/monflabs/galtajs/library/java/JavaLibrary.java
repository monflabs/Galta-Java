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
import java.lang.reflect.InvocationTargetException;
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
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.jsonfactory.JSObject.DESC_CHECK;
import org.monflabs.galtajs.rt.JSRuntimeException;
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
				Object result = call( (v,c) -> JavaLibrary.this.convertObject(v, c), _this instanceof JavaClass, _this, undefinedToNull(parameters));
				return checkClassAccess(result);
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
		// A method of that very name wins over a property derived from a getter (shutdown()
		// over isShutdown()): a script couldn't call it at all otherwise, while the property
		// stays readable through its getter
		@Override
		protected MemberCache findMembers(Class<?> clazz, String name) {
			MemberCache m = super.findMembers(clazz, name);
			for (MemberCache c = m; c != null; c = c.getNextMember()) {
				if (c instanceof MethodCache) {
					return c;
				}
			}
			return m;
		}
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
				// Null is not valid for primitives, and never an exact match.
				// (ClassMetadata.findCallable() handles a null argument itself, so this is
				// only reached by a direct call)
				if (c1.isPrimitive()) {
					return ASSIGNABLE.NO;
				}
				return ASSIGNABLE.POSSIBLE;
			}
			
			// A JavaClass is passed as its Class: to a Class parameter, or to one a Class is
			// assignable to (Type, Object...)
			if(p2 instanceof JavaClass) {
				return c1==Class.class ? ASSIGNABLE.EXACT : c1.isAssignableFrom(Class.class) ? ASSIGNABLE.POSSIBLE : ASSIGNABLE.NO;
			}
			
			Class<?> c2 = p2.getClass();
			if(c1==c2) {
				return ASSIGNABLE.EXACT;
			}
			
			// Directly assignable
			if (c1.isAssignableFrom(c2)) {
				return ASSIGNABLE.POSSIBLE;
			}
			// A Character can be passed as a String, and a one-character String as a Character
			if (c1 == String.class && c2 == Character.class) {
				return ASSIGNABLE.POSSIBLE;
			}
			if (c1 == Character.class && c2 == String.class) {
				return ((String)p2).length() == 1 ? ASSIGNABLE.POSSIBLE : ASSIGNABLE.NO;
			}
			// Numbers can be converted
			if (isNumericTarget(c1) && Number.class.isAssignableFrom(c2)) {
				return ASSIGNABLE.POSSIBLE;
			}
			// A function is adapted to a functional interface (a single abstract method)
			if (BuiltinFunction.class.isAssignableFrom(c2) && functionalMethod(c1)!=null) {
				return ASSIGNABLE.POSSIBLE;
			}
			// Ok, not compatible
			return ASSIGNABLE.NO;
		}
		// A function passed to two unrelated functional interfaces (Runnable/Callable,
		// ExecutorService.submit()): the one whose method returns a value is preferred, as a
		// function always returns one
		@Override
		protected int compareUnrelated(Class<?> c1, Class<?> c2, Object arg) {
			if (arg instanceof BuiltinFunction) {
				Method m1 = functionalMethod(c1);
				Method m2 = functionalMethod(c2);
				if (m1!=null && m2!=null) {
					boolean v1 = m1.getReturnType()!=Void.TYPE;
					boolean v2 = m2.getReturnType()!=Void.TYPE;
					if (v1!=v2) {
						return v1 ? 1 : -1;
					}
				}
			}
			return 0;
		}

	}
		
	private HashMap<String, JavaClass> primitives = new HashMap<String, JavaClass>();
	
	// Two caches, like ClassMetadata's own: the classes of the permanent loaders (JDK,
	// application) in a map owned by this library, the others in a ClassValue whose entry
	// lives with the class, so a class and its loader are not pinned by this library. A
	// ClassValue entry for a class that is never unloaded would pin this library (and its
	// environment) forever, as a JavaClass references it.
	private final java.util.concurrent.ConcurrentHashMap<Class<?>, JavaClass> permanentJavaClassCache = new java.util.concurrent.ConcurrentHashMap<>();
	private final ClassValue<JavaClass> javaClassCache = new ClassValue<>() {
		@Override
		protected JavaClass computeValue(Class<?> clazz) {
			return new JavaClassImpl(classMetadata.getClassInfoCache(clazz));
		}
	};

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
	public JavaClass getJavaClass(Class<?> clazz) {
		if(ClassMetadata.isPermanentClass(clazz)) {
			JavaClass jc = permanentJavaClassCache.get(clazz);
			return jc!=null ? jc : permanentJavaClassCache.computeIfAbsent(clazz, c -> new JavaClassImpl(classMetadata.getClassInfoCache(c)));
		}
		return javaClassCache.get(clazz);
	}

	// A reflective handle (Class.forName(), obj.getClass(), getInterfaces(), a
	// Class-typed field...) must not give access to a class the access manager
	// refuses to load by name
	private Object checkClassAccess(Object value) {
		AccessManager am = getAccessManager();
		if(am!=null) {
			if(value instanceof Class<?> c) {
				checkClassAccess(am, c);
			} else if(value instanceof Class<?>[] classes) {
				for(Class<?> c: classes) {
					checkClassAccess(am, c);
				}
			}
		}
		return value;
	}
	private static void checkClassAccess(AccessManager am, Class<?> c) {
		if(c!=null && !am.canLoadClass(c.getName())) {
			throw RuntimeUtil.typeError("Java class '{0}' cannot be loaded", c.getName());
		}
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

		return new JavaAccessor(env, clazz);
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
			// new (Java.type('int[]'))(n): an array of that size
			if(c.isArray()) {
				if(accessManager!=null && !accessManager.canCreateArray(c.getComponentType())) {
					throw RuntimeUtil.typeError("Java array '{0}' cannot be created", c.getComponentType());
				}
				long size = parameters.length>0 ? RuntimeUtil.toLength(JSEnvironment.getEnvironment(), parameters[0]) : 0;
				if(size>Integer.MAX_VALUE-8) {
					throw RuntimeUtil.rangeError("Invalid array length {0}", size);
				}
				return Array.newInstance(c.getComponentType(), (int)size);
			}
			if(accessManager!=null && !accessManager.canCreateObject(c)) {
				throw RuntimeUtil.typeError("Java class '{0}' cannot be created", c);
			}

			// Find and call the proper ctor
			// Find the best constructor using 2 passes
			ConstructorCache cache = getClassInfoCache().getConstructors();
			parameters = undefinedToNull(parameters);
			ConstructorCache m = cache!=null ? (ConstructorCache) cache.findCallable(true, parameters, true) : null;
			if (m != null) {
				// Convert the arguments into a copy (varargs collected into an array): the
				// caller's array is left untouched
				Object[] converted = m.convertArguments((v,t) -> convertObject(v, t), parameters);
				// Create the java object
				try {
					Object o = m.getConstructor().newInstance(converted);
					if(RuntimeUtil.isPrimitiveType(o)) {
						return RuntimeUtil.primitiveAsObject(JSEnvironment.getEnvironment(),o);
					}
					return o;
				} catch (InvocationTargetException e) {
					// The exception thrown by the constructor itself
					Throwable t = e.getCause()!=null ? e.getCause() : e;
					if(t instanceof JSRuntimeException jse) {
						throw jse;
					}
					throw RuntimeUtil.error(t, "Error while calling java constructor '{0}': {1}",
							ClassMetadata.getMethodSignature(c.getName(), parameters), t.toString());
				} catch (ReflectiveOperationException | IllegalArgumentException e) {
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
			// An array type: "int[]", "java.lang.String[][]"
			int dimensions = 0;
			String name = className;
			while(name.endsWith("[]")) {
				name = name.substring(0, name.length()-2).trim();
				dimensions++;
			}
			if(dimensions>0) {
				if(accessManager!=null && !accessManager.canLoadClass(name)) {
					throw RuntimeUtil.typeError("Java class '{0}' cannot be loaded", name);
				}
				Class<?> c = primitives.containsKey(name) ? primitives.get(name).getNativeClass() : getClassLoader(env).loadClass(name);
				for(int i=0; i<dimensions; i++) {
					c = c.arrayType();
				}
				return getJavaClass(c);
			}
			if(primitives.containsKey(className)) {
				return primitives.get(className);
			}
			Class<?> clazz = getClassLoader(env).loadClass(className);
			return getJavaClass(clazz);
		} catch (JSRuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw RuntimeUtil.error(e, "Error while loading Java class '{0}'", className);
		}
	}

	
	
	/////////////////////////////////////////////////////////////////////////////////////
	// Java.to() / Java.from()
	/////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Java.to(value, type): converts a script array, or any array-like value (a length and
	 * indexed elements), a Java array or a collection, to a new Java array of the given type,
	 * converting every element with the JavaScript conversions (ToNumber, ToString,
	 * ToBoolean) for the primitives, their wrappers and String. Nested arrays are converted
	 * for multi-dimensional types. A List, Collection, Deque or Set type gives a new
	 * ArrayList, ArrayDeque or LinkedHashSet. The type defaults to Object[].
	 */
	public Object toJava(JSEnvironment env, Object value, Class<?> type) {
		if(type==null) {
			type = Object[].class;
		}
		List<Object> elements = elements(env, value, "Java.to");
		AccessManager accessManager = getAccessManager();
		if(type.isArray()) {
			Class<?> component = type.getComponentType();
			if(accessManager!=null && !accessManager.canCreateArray(component)) {
				throw RuntimeUtil.typeError("Java array '{0}' cannot be created", component);
			}
			Object array = Array.newInstance(component, elements.size());
			for(int i=0; i<elements.size(); i++) {
				Array.set(array, i, toJavaElement(env, elements.get(i), component));
			}
			return array;
		}
		java.util.Collection<Object> collection;
		if(type.isAssignableFrom(java.util.ArrayList.class)) {
			collection = new java.util.ArrayList<>(elements.size());
		} else if(type.isAssignableFrom(java.util.ArrayDeque.class)) {
			collection = new java.util.ArrayDeque<>(elements.size());
		} else if(type.isAssignableFrom(java.util.LinkedHashSet.class)) {
			collection = new java.util.LinkedHashSet<>();
		} else {
			throw RuntimeUtil.typeError("Java.to() converts to an array, List, Collection, Deque or Set type, not {0}", type.getName());
		}
		if(accessManager!=null && !accessManager.canCreateObject(collection.getClass())) {
			throw RuntimeUtil.typeError("Java class '{0}' cannot be created", collection.getClass().getName());
		}
		for(Object e: elements) {
			collection.add(toJavaElement(env, e, Object.class));
		}
		return collection;
	}

	/**
	 * Java.from(value): a new script array with the elements of a Java array, a collection or
	 * any other Iterable.
	 */
	public Object fromJava(JSEnvironment env, Object value) {
		if(value!=null && (value.getClass().isArray() || value instanceof Iterable<?>)) {
			List<Object> elements = elements(env, value, "Java.from");
			Object[] values = new Object[elements.size()];
			for(int i=0; i<values.length; i++) {
				values[i] = checkClassAccess(elements.get(i));
			}
			return JSArray.of(env, values);
		}
		throw RuntimeUtil.typeError("Java.from() expects a Java array or collection, not {0}", RuntimeUtil.objectTypeName(value));
	}

	// The elements of an array-like value, as they are
	private List<Object> elements(JSEnvironment env, Object value, String function) {
		if(value!=null && value.getClass().isArray()) {
			int length = Array.getLength(value);
			List<Object> l = new java.util.ArrayList<>(length);
			for(int i=0; i<length; i++) {
				l.add(Array.get(value, i));
			}
			return l;
		}
		// A script array or object: its length and indexed properties, holes included
		if(value instanceof JSObject || value instanceof CharSequence) {
			JSAccessor acc = env.getAccessor(value);
			long length = RuntimeUtil.toLength(env, acc.getProperty(value, "length", RuntimeUtil.UNDEFINED));
			if(length>Integer.MAX_VALUE-8) {
				throw RuntimeUtil.rangeError("Invalid array length {0}", length);
			}
			List<Object> l = new java.util.ArrayList<>((int)length);
			for(long i=0; i<length; i++) {
				l.add(acc.getProperty(value, i, RuntimeUtil.UNDEFINED));
			}
			return l;
		}
		if(value instanceof Iterable<?> it) {
			List<Object> l = new java.util.ArrayList<>();
			for(Object o: it) {
				l.add(o);
			}
			return l;
		}
		throw RuntimeUtil.typeError("{0}() expects an array-like value, not {1}", function, RuntimeUtil.objectTypeName(value));
	}

	// An element converted to a component type, with the JavaScript conversions
	private Object toJavaElement(JSEnvironment env, Object v, Class<?> type) {
		boolean nullish = v==null || v==RuntimeUtil.UNDEFINED;
		if(type.isArray()) {
			return nullish ? null : toJava(env, v, type);
		}
		if(nullish && !type.isPrimitive()) {
			return null;
		}
		Class<?> boxed = type.isPrimitive() ? java.lang.invoke.MethodType.methodType(type).wrap().returnType() : type;
		if(boxed==Boolean.class) {
			return RuntimeUtil.toBoolean(env, v);
		}
		if(boxed==Character.class) {
			if(v instanceof CharSequence cs && cs.length()==1) {
				return cs.charAt(0);
			}
			if(v instanceof Character) {
				return v;
			}
			return (char)RuntimeUtil.toInt32(env, v);
		}
		if(boxed==String.class) {
			return v instanceof String ? v : RuntimeUtil.toString(env, v);
		}
		if(ClassMetadata.isNumericTarget(boxed)) {
			Number n = v instanceof Number num ? num : RuntimeUtil.toNumber(env, v);
			if(boxed==Integer.class) {
				return RuntimeUtil.toInt32(n);
			} else if(boxed==Long.class) {
				return n instanceof Double || n instanceof Float ? Long.valueOf((long)n.doubleValue()) : Long.valueOf(n.longValue());
			} else if(boxed==Double.class) {
				return n.doubleValue();
			} else if(boxed==Float.class) {
				return n.floatValue();
			} else if(boxed==Short.class) {
				return (short)RuntimeUtil.toInt32(n);
			} else if(boxed==Byte.class) {
				return (byte)RuntimeUtil.toInt32(n);
			}
			return ClassMetadata.convertObject(n, boxed);
		}
		Object o = convertObject(v, type);
		if(o!=null && !type.isInstance(o)) {
			throw RuntimeUtil.typeError("Java.to() cannot convert {0} to {1}", RuntimeUtil.objectTypeName(v), type.getName());
		}
		return o;
	}

	/////////////////////////////////////////////////////////////////////////////////////
	// Java Proxy
	/////////////////////////////////////////////////////////////////////////////////////

	// The single abstract method of a functional interface, or null for a class or another
	// interface. The abstract methods redeclaring a public method of Object don't count
	// (Comparator.equals()), and the overloads of a single name count as one (a generic
	// method redeclared with a more specific signature).
	private static final ClassValue<Method[]> FUNCTIONAL_METHOD = new ClassValue<>() {
		@Override
		protected Method[] computeValue(Class<?> c) {
			Method found = null;
			if (c.isInterface()) {
				for (Method m : c.getMethods()) {
					if (!java.lang.reflect.Modifier.isAbstract(m.getModifiers()) || isObjectMethod(m)) {
						continue;
					}
					if (found!=null && !found.getName().equals(m.getName())) {
						return new Method[] { null };
					}
					if (found==null || found.getReturnType()==Void.TYPE) {
						found = m;
					}
				}
			}
			return new Method[] { found };
		}
	};
	private static boolean isObjectMethod(Method m) {
		try {
			return java.lang.reflect.Modifier.isPublic(Object.class.getMethod(m.getName(), m.getParameterTypes()).getModifiers());
		} catch (NoSuchMethodException e) {
			return false;
		}
	}
	static Method functionalMethod(Class<?> c) {
		return FUNCTIONAL_METHOD.get(c)[0];
	}

	// undefined reaches Java as null: a copy of the arguments when any is undefined
	private static Object[] undefinedToNull(Object[] args) {
		Object[] r = args;
		if (args!=null) {
			for (int i = 0; i < args.length; i++) {
				if (args[i]==RuntimeUtil.UNDEFINED) {
					if (r==args) {
						r = args.clone();
					}
					r[i] = null;
				}
			}
		}
		return r;
	}

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
			Object result = function.call(null, args!=null ? args : RuntimeUtil.EMPTY_PARAMS);
			Class<?> type = method.getReturnType();
			if (type==Void.TYPE || result==RuntimeUtil.UNDEFINED) {
				return null;
			}
			// A JS number to the numeric type the method returns (an int for a Comparator)
			return ClassMetadata.convertObject(result, type);
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
					return checkClassAccess(acc.get(null));
				}
				// If it is a method, then return a callable object
				if (m instanceof MethodCache mc) {
					return mc;
				}
			}

			return defaultValue;
			//return super._getOwnMember(env,base,  _this, member, defaultValue);
		}
	}
	
	public class JavaAccessor extends JavaLibraryAccessor {

		// The class this accessor was created for (JSEnvironment keeps one accessor per
		// class) and its metadata, found once instead of on every member access
		private final Class<?> accessorClass;
		private volatile ClassInfoCache accessorClassInfo;

		public JavaAccessor(JSEnvironment env) {
			this(env, null);
		}
		public JavaAccessor(JSEnvironment env, Class<?> clazz) {
			super(env);
			this.accessorClass = clazz;
		}

		// The metadata of a class, without a lookup for this accessor's own class
		private ClassInfoCache classInfo(Class<?> clazz) {
			if(clazz==accessorClass) {
				ClassInfoCache ci = accessorClassInfo;
				if(ci==null) {
					accessorClassInfo = ci = classMetadata.getClassInfoCache(clazz);
				}
				return ci;
			}
			return classMetadata.getClassInfoCache(clazz);
		}
		
		@Override
		public String getClassName(Object _this) {
			return _this.getClass().getName();
		}	

		//
		// TODO: FOR NOW
		@Override
		public PropertyDescriptor getOwnPropertyDescriptor(Object _this, String member) {
			MemberCache m = classInfo(_this.getClass()).getMembers(member);
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
				ClassInfoCache ci =  classInfo(_this.getClass());
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
			MemberCache m = classInfo(clazz).getMembers(member);
			if (m != null) {
				if (m instanceof ValueAccessor acc) {
					return checkClassAccess(acc.get(_this));
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
				// Like a JS array: reading past the end gives undefined
				if(index>=0 && index<list.size()) {
					return list.get((int)index);
				}
				return RuntimeUtil.UNDEFINED;
			}
			return defaultValue;
		}

		@Override
		public boolean setOwnProperty(Object _this, String member, Object value, PropertyDescriptor desc, DESC_CHECK check, Object receiver) {
			if (super.setOwnProperty(_this, member, value, desc, check, receiver)) {
				return true;
			}

			MemberCache m = classInfo(_this.getClass()).getMembers(member);
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
				if(index>Integer.MAX_VALUE-8) {
					throw RuntimeUtil.rangeError("Invalid Java List index {0}", index);
				}
				// Past the end, like a JS array: the list grows (a Java list
				// has no holes, they are null - as in the JSArrayList wrapper)
				List<Object> l = (List<Object>) list;
				while(l.size()<index) {
					l.add(null);
				}
				if(index<l.size()) {
					l.set((int)index, value);
				} else {
					l.add(value);
				}
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
				// A JS number is a Double (or an Integer): convert it to the
				// component type (an int[] slot cannot take a Double as is)
				try {
					Array.set(_this, (int)index, convertObject(value, _this.getClass().getComponentType()));
				} catch(IllegalArgumentException | ClassCastException e) {
					throw RuntimeUtil.typeError("Cannot store {0} into a Java {1}", RuntimeUtil.objectTypeName(value), getClassName(_this));
				}
				return true;
			}
			throw RuntimeUtil.error("Invalid Java Array index {0}, max is {1}", index, size);
		}
	}
	

	private final Object convertObject(Object value, Class<?> targetClass) {
		if(value==RuntimeUtil.UNDEFINED) {
			return null;
		}
		if(value instanceof JavaClass jc) {
			return jc.getNativeClass();
		}
		// A function is proxied to the interface it is passed to - and passed as is to a
		// parameter it already is an instance of (Object...)
		if(value instanceof Callable function && targetClass!=null && targetClass.isInterface() && !targetClass.isInstance(value)) {
			return getProxy(function, targetClass);
		}
		return ClassMetadata.convertObject(value, targetClass);
	}

}