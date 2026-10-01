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

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

import org.eclipse.jdt.annotation.NonNull;
import org.monflabs.util.StringUtil;
import org.monflabs.util.TypeUtil;

/**
 * Java reflection helper.
 */
public class ClassMetadata {
	
	public static class AccessManager {
		
		public boolean canLoadClass(String className) {
			return true;
		}
		public boolean canCreateObject(Class<?> c) {
			return true;
		}
		public boolean canCreateArray(Class<?> c) {
			return true;
		}
		public boolean canProxy(Class<?> targetClass) {
			return true;
		}
		/**
		 * Whether the members (fields, methods, properties) of the given class can be
		 * used. Checked for every member lookup, both on instances and on the class
		 * itself (static members): denying {@code java.lang.Class} and the
		 * {@code java.lang.reflect} classes prevents reflection from reaching classes
		 * {@link #canLoadClass(String)} refuses.
		 */
		public boolean canAccessMember(Class<?> c) {
			return true;
		}

		public boolean acceptField(String name) {
			return true;
		}
		public boolean acceptMethod(String name) {
			return true;
		}
		public boolean acceptProperty(String name) {
			return true;
		}
	}

	
	private static final boolean USE_JAVABEAN_PROPERTIES = false;

	// Read from every Java-interop access, possibly from several threads at once.
	// A ClassValue rather than a Map keyed by Class: the cache entry lives with the
	// class, so a class (and its class loader - e.g. one compiled at runtime) can
	// still be unloaded instead of being pinned by this cache forever.
	private final ClassValue<ClassInfoCache> classCache = new ClassValue<>() {
		@Override
		protected ClassInfoCache computeValue(Class<?> type) {
			return new ClassInfoCache(type);
		}
	};
	private AccessManager accessManager;

	public ClassMetadata(AccessManager accessManager) {
		this.accessManager = accessManager;
	}

	public AccessManager getAccessManager() {
		return accessManager;
	}

	public static final Object convertObject(Object value, Class<?> targetClass) {
		return convertObject(value, targetClass, false);
	}
	public static final Object convertObjectPermissive(Object value, Class<?> targetClass) {
		return convertObject(value, targetClass, true);
	}
	public static final Object convertObject(Object value, Class<?> targetClass, boolean permissiveConverter) throws ModelException {
		if(value!=null && targetClass!=value.getClass()) {
			// Primitive conversion
			if(targetClass==Byte.TYPE || targetClass==Byte.class) {
				if(value instanceof Number number) {
					return Byte.valueOf(number.byteValue());
				}
				if(permissiveConverter) {
					if(value instanceof String s) {
						return Byte.parseByte(s);
					}
					if(value instanceof Boolean b) {
						return b ? (byte)1 : (byte)0;
					}
				}
			} else if(targetClass==Short.TYPE || targetClass==Short.class) {
				if(value instanceof Number number) {
					return Short.valueOf(number.shortValue());
				}
				if(permissiveConverter) {
					if(value instanceof String s) {
						return Short.parseShort(s);
					}
					if(value instanceof Boolean b) {
						return b ? (short)1 : (short)0;
					}
				}
			} else if(targetClass==Integer.TYPE || targetClass==Integer.class) {
				if(value instanceof Number number) {
					return Integer.valueOf(number.intValue());
				}
				if(permissiveConverter) {
					if(value instanceof String s) {
						return Integer.parseInt(s);
					}
					if(value instanceof Boolean b) {
						return b ? (int)1 : (int)0;
					}
				}
			} else if(targetClass==Long.TYPE || targetClass==Long.class) {
				if(value instanceof Number number) {
					return Long.valueOf(number.longValue());
				}
				if(permissiveConverter) {
					if(value instanceof String s) {
						return Long.parseLong(s);
					}
					if(value instanceof Boolean b) {
						return b ? (long)1 : (long)0;
					}
				}
			} else if(targetClass==Float.TYPE || targetClass==Float.class) {
				if(value instanceof Number number) {
					return Float.valueOf(number.floatValue());
				}
				if(permissiveConverter) {
					if(value instanceof String s) {
						return Float.parseFloat(s);
					}
					if(value instanceof Boolean b) {
						return b ? (float)1 : (float)0;
					}
				}
			} else if(targetClass==Double.TYPE || targetClass==Double.class) {
				if(value instanceof Number number) {
					return Double.valueOf(number.doubleValue());
				}
				if(permissiveConverter) {
					if(value instanceof String s) {
						return Double.parseDouble(s);
					}
					if(value instanceof Boolean b) {
						return b ? (double)1 : (double)0;
					}
				}
			} else if(targetClass==Boolean.TYPE || targetClass==Boolean.class) {
				if(value instanceof Boolean b) {
					return b;
				}
				if(permissiveConverter) {
					if(value instanceof Number number) {
						return number.intValue()!=0;
					}
					if(value instanceof String s) {
						return Boolean.parseBoolean(s);
					}
				}
			} else if(targetClass==Character.TYPE || targetClass==Character.class) {
				if(value instanceof Number number) {
					return Character.valueOf((char)number.intValue());
				}
				if(value instanceof String s) {
					if(s.length()==1) {
						return Character.valueOf(s.charAt(0));
					}
				}
			} else if(targetClass==String.class) {
				if(value instanceof String s) {
					return s;
				}
				if(permissiveConverter) {
					return value.toString();
				}
			} else if(targetClass==BigInteger.class) {
				if(value instanceof Number number) {
					return TypeUtil.toBigInteger(number);
				}
				if(permissiveConverter) {
					if(value instanceof String s) {
						return new BigInteger(s);
					}
					if(value instanceof Boolean b) {
						return b ? BigInteger.ONE : BigInteger.ZERO;
					}
				}
			} else if(targetClass==BigDecimal.class) {
				if(value instanceof Number number) {
					return TypeUtil.toBigDecimal(number);
				}
				if(permissiveConverter) {
					if(value instanceof String s) {
						return new BigDecimal(s);
					}
					if(value instanceof Boolean b) {
						return b ? BigDecimal.ONE : BigDecimal.ZERO;
					}
				}
			}
		}
		return value;
	}

	
	/////////////////////////////////////////////////////////////////////////////////////
	// Member access
	/////////////////////////////////////////////////////////////////////////////////////

	// Overload resolution, following Java's "most specific method" rule:
	// - an overload whose parameter classes are exactly the argument classes wins
	// - else, among the applicable overloads, the one more specific than all the others
	//   is selected, whatever the order the reflection API returned them in
	// - several maximally specific overloads with different signatures is an ambiguity
	// A null argument is applicable to any reference parameter but never to a primitive one,
	// and is never an exact match (so f(String) is preferred over f(Object) for null).
	// strictMatch is currently unused: conversions (e.g. Double -> int) are always allowed.
	private CallableCache findCallable(Boolean staticMethod, @NonNull Object[] args, CallableCache cache, boolean strictMatch) {
		int argsLength = args.length;
		List<CallableCache> candidates = null;
		loop: for (CallableCache m = cache; m != null; m = m.nextCallable) {
			if (staticMethod!=null && (staticMethod != m.isStatic())) {
				continue;
			}

			// TODO: Check for variable parameters!
			Class<?>[] argClasses = m.argClasses;
			if (argClasses.length != argsLength) {
				continue;
			}

			ASSIGNABLE exact = ASSIGNABLE.EXACT;
			for (int j = 0; j < argsLength; j++) {
				Object param = args[j];
				if (param == null) {
					// The parameter classes are boxed, so the primitive information is kept aside
					if (m.primitiveArgs[j]) {
						continue loop;
					}
					exact = ASSIGNABLE.POSSIBLE;
					continue;
				}
				ASSIGNABLE a = isAssignable(argClasses[j], param);
				if(a==ASSIGNABLE.NO) {
					continue loop;
				} else if(a==ASSIGNABLE.POSSIBLE) {
					exact = ASSIGNABLE.POSSIBLE;
				}
			}

			// If it matches precisely, then we're good
			if (exact==ASSIGNABLE.EXACT) {
				return m;
			}
			if (candidates == null) {
				candidates = new ArrayList<>();
			}
			candidates.add(m);
		}
		if (candidates == null) {
			return null;
		}
		if (candidates.size() == 1) {
			return candidates.get(0);
		}

		// Keep the maximally specific candidates (no other candidate is more specific)
		List<CallableCache> best = new ArrayList<>();
		for (CallableCache m : candidates) {
			boolean maximal = true;
			for (CallableCache o : candidates) {
				if (o != m && compareArguments(o.argClasses, m.argClasses) == 1) {
					maximal = false;
					break;
				}
			}
			// The same signature can be listed twice (e.g. a covariant bridge method): keep one
			if (maximal && best.stream().noneMatch(b -> java.util.Arrays.equals(b.argClasses, m.argClasses))) {
				best.add(m);
			}
		}
		if (best.size() > 1) {
			throw new ModelException(null, "Ambiguity between {0}{1} and {0}{2}", best.get(0).getName(),
					methodSignature(best.get(0).argClasses),methodSignature(best.get(1).argClasses));
		}
		return best.get(0);
	}
	

	// Should we create 3 states of assignable?
	//    NO, POSSIBLE, EXACT
	// ex: int-> double is possible, int-> is exact
	protected enum ASSIGNABLE {NO, POSSIBLE, EXACT};
	protected ASSIGNABLE isAssignable(Class<?> c1, Object p2) {
		if(p2==null) {
			// Null is not valid for primitives (findCallable() checks that itself, as the
			// parameter classes it passes are boxed), and it is never an exact match
			if (c1.isPrimitive()) {
				return ASSIGNABLE.NO;
			}
			return ASSIGNABLE.POSSIBLE;
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
		// Ok, not compatible
		return ASSIGNABLE.NO;
	}

	private static String methodSignature(Class<?>[] c) {
		StringBuilder b = new StringBuilder();
		b.append("(");
		if (c != null) {
			for (int i = 0; i < c.length; i++) {
				if (i > 0) {
					b.append(", ");
				}
				b.append(c[i].getName());
			}
		}
		b.append(")");
		return b.toString();
	}

	// According to the Java spec, we are looking for the most specific method
	// "The informal intuition is that one method is more specific than another if
	// any invocation
	// handled by the first method could be passed on to the other one without a
	// compile-time type error."
	// This method returns 3 values:
	// 0: incompatible. This leads to an error
	// 1: a1 is more specific than a2
	// -1: a2 is more specific than a1
	private static int compareArguments(Class<?>[] a1, Class<?>[] a2) {
		int result = 0;
		int length = a1.length;
		for (int i = 0; i < length; i++) {
			Class<?> c1 = a1[i];
			Class<?> c2 = a2[i];
			if (c1 != c2) {
				if (c1.isAssignableFrom(c2)) {
					if (result == 1) {
						return 0;
					}
					result = -1;
				}
				if (c2.isAssignableFrom(c1)) {
					if (result == -1) {
						return 0;
					}
					result = 1;
				}
			}
		}
		return result;
	}

	public static String getMethodSignature(String functionName, Object[] args) {
		StringBuilder b = new StringBuilder();
		if (!StringUtil.isEmpty(functionName)) {
			b.append(functionName);
		}
		b.append("(");
		if (args != null) {
			for (int i = 0; i < args.length; i++) {
				if (i > 0) {
					b.append(", ");
				}
				if (args[i] != null) {
					b.append(args[i].getClass().getName());
				} else {
					b.append("<null>");
				}
			}
		}
		b.append(")");
		return b.toString();
	}
	

	/////////////////////////////////////////////////////////////////////////////////////
	// Java access class
	/////////////////////////////////////////////////////////////////////////////////////

	public ClassInfoCache getClassInfoCache(Class<?> c) {
		return classCache.get(c);
	}

	public static abstract class MemberCache {
		MemberCache nextMember;
	}

	// The loaders whose classes are never unloaded: the bootstrap loader (null),
	// the platform loader and the system loader with its ancestors
	private static final Set<ClassLoader> PERMANENT_LOADERS = permanentLoaders();

	private static Set<ClassLoader> permanentLoaders() {
		Set<ClassLoader> set = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		for (ClassLoader l = ClassLoader.getSystemClassLoader(); l != null; l = l.getParent()) {
			set.add(l);
		}
		set.add(ClassLoader.getPlatformClassLoader());
		return set;
	}

	// A call shape: whether the call is static, and the class of each argument
	private static final class Shape {
		private final Boolean staticMethod;
		private final Object[] classes;
		private final int hash;

		Shape(Boolean staticMethod, Object[] classes) {
			this.staticMethod = staticMethod;
			this.classes = classes;
			this.hash = 31 * java.util.Objects.hashCode(staticMethod) + java.util.Arrays.hashCode(classes);
		}

		@Override
		public int hashCode() {
			return hash;
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Shape s
				&& hash == s.hash
				&& java.util.Objects.equals(staticMethod, s.staticMethod)
				&& java.util.Arrays.equals(classes, s.classes);
		}
	}

	// The last call shape of an overload group, with its resolution
	private static final class LastCall {
		private final Boolean staticMethod;
		// Classes (or NULL_ARG), or WeakReferences to the classes that must not be pinned
		private final Object[] refs;
		final Object result;

		LastCall(Boolean staticMethod, Object[] classes, boolean strong, Object result) {
			this.staticMethod = staticMethod;
			if (strong) {
				this.refs = classes;
			} else {
				Object[] r = new Object[classes.length];
				for (int i = 0; i < r.length; i++) {
					r[i] = classes[i] instanceof Class<?> c && !isBootstrap(c) ? new java.lang.ref.WeakReference<Class<?>>(c) : classes[i];
				}
				this.refs = r;
			}
			// The resolved member belongs to the owner: it never pins a foreign loader
			this.result = result;
		}

		private static boolean isBootstrap(Class<?> c) {
			return c.getClassLoader() == null;
		}

		boolean matches(Boolean staticMethod, Object[] args) {
			if (refs.length != args.length || !java.util.Objects.equals(this.staticMethod, staticMethod)) {
				return false;
			}
			for (int i = 0; i < args.length; i++) {
				Object a = args[i];
				Object r = refs[i];
				if (a == null) {
					if (r != CallableCache.NULL_ARG_MARKER) {
						return false;
					}
				} else {
					Class<?> c = a.getClass();
					if (r != c && !(r instanceof java.lang.ref.WeakReference<?> w && w.get() == c)) {
						return false;
					}
				}
			}
			return true;
		}
	}


	public abstract class CallableCache extends MemberCache {
		CallableCache nextCallable;
		Class<?>[] argClasses;
		// Which parameters were primitive before argClasses got boxed (a null can't be passed to them)
		boolean[] primitiveArgs;

		protected CallableCache(Class<?>[] argClasses) {
			this.argClasses = argClasses;
			this.primitiveArgs = new boolean[argClasses!=null ? argClasses.length : 0];
			// Transform the primitive types to their object ones
			if (argClasses != null) {
				for (int i = 0; i < argClasses.length; i++) {
					if (argClasses[i].isPrimitive()) {
						primitiveArgs[i] = true;
						argClasses[i] = getObjectTypeFromPrimitive(argClasses[i]);
					}
				}
			}
		}

		public CallableCache getNextCallable() {
			return nextCallable;
		}

		public Class<?>[] getArgClasses() {
			return argClasses;
		}

		public abstract boolean isStatic();

		public abstract String getName();
		
		// Resolved overload per distinct call SHAPE (staticMethod + each
		// argument's runtime class, null args using NULL_ARG as a stand-in
		// since isAssignable() treats a null argument specially and it has
		// no getClass() of its own) - overload resolution depends only on
		// this shape, never on the argument VALUES beyond their class/nullness,
		// so a shape already seen on this overload group resolves in O(1).
		//
		// This cache lives with the ClassInfoCache of a class, possibly for the
		// life of the JVM (a JDK class): a shape is only kept in the map when all
		// its classes come from a loader that can't be unloaded before the owner
		// of the method (the bootstrap/platform/system loaders, or the owner's own
		// loader and its ancestors). Classes from any other loader (e.g. one per
		// compiled script) would be pinned - with their whole loader - forever.
		// A ConcurrentHashMap can't hold a null value, so a resolved "no match" is
		// stored as NO_MATCH - distinguishable from "never resolved this shape"
		private final Map<Shape, Object> resolutionCache = new ConcurrentHashMap<>();
		// The last resolved shape, checked first without allocating anything: most
		// call sites always pass the same argument classes. It references classes
		// from other loaders only weakly.
		private volatile LastCall lastCall;
		private static final Object NULL_ARG = new Object();
		static final Object NULL_ARG_MARKER = NULL_ARG;
		private static final Object NO_MATCH = new Object();

		/**
		 * The class whose loader bounds the lifetime of this member, if known.
		 */
		protected Class<?> getOwnerClass() {
			return null;
		}

		// The methods should be added here!
		public CallableCache findCallable(Boolean staticMethod, @NonNull Object[] args, boolean strictMatch) {
			LastCall last = lastCall;
			if (last != null && last.matches(staticMethod, args)) {
				return last.result == NO_MATCH ? null : (CallableCache) last.result;
			}
			Class<?> owner = getOwnerClass();
			ClassLoader ownerLoader = owner != null ? owner.getClassLoader() : null;
			boolean cacheable = true;
			Object[] classes = new Object[args.length];
			for (int i = 0; i < args.length; i++) {
				Object a = args[i];
				if (a == null) {
					classes[i] = NULL_ARG;
				} else {
					Class<?> c = a.getClass();
					classes[i] = c;
					if (cacheable && !isPinnable(c, ownerLoader)) {
						cacheable = false;
					}
				}
			}
			Shape key = cacheable ? new Shape(staticMethod, classes) : null;
			Object result = key != null ? resolutionCache.get(key) : null;
			if (result == null) {
				// A plain get (not computeIfAbsent) so a genuinely ambiguous
				// shape's ModelException (thrown by the real resolution below,
				// not caught here) is never mistaken for "not yet cached" and
				// re-thrown fresh on every call instead of being cached as a
				// bogus result.
				CallableCache r = ClassMetadata.this.findCallable(staticMethod, args, this, strictMatch);
				result = r != null ? r : NO_MATCH;
				if (key != null) {
					resolutionCache.put(key, result);
				}
			}
			lastCall = new LastCall(staticMethod, classes, cacheable, result);
			return result == NO_MATCH ? null : (CallableCache) result;
		}

		private static boolean isPinnable(Class<?> c, ClassLoader ownerLoader) {
			ClassLoader l = c.getClassLoader();
			if (l == null || PERMANENT_LOADERS.contains(l)) {
				return true;
			}
			for (ClassLoader o = ownerLoader; o != null; o = o.getParent()) {
				if (o == l) {
					return true;
				}
			}
			return false;
		}

		private Class<?> getObjectTypeFromPrimitive(Class<?> c) {
			// Transform a primitive to its Object based class
			if (c == Character.TYPE) {
				return Character.class;
			}
			if (c == Byte.TYPE) {
				return Byte.class;
			}
			if (c == Short.TYPE) {
				return Short.class;
			}
			if (c == Integer.TYPE) {
				return Integer.class;
			}
			if (c == Long.TYPE) {
				return Long.class;
			}
			if (c == Float.TYPE) {
				return Float.class;
			}
			if (c == Double.TYPE) {
				return Double.class;
			}
			if (c == Boolean.TYPE) {
				return Boolean.class;
			}
			return Void.class;
		}
	}
	
	// JDK-4071957 : (reflect) Method.invoke access control does not understand inner class scoping
	// https://bugs.java.com/bugdatabase/view_bug.do?bug_id=4071957
	// A public method declared by a non-public class (e.g. size() of the List returned by
	// List.of()) cannot be invoked through its own Method object: the same method has to be
	// invoked through a public class or interface declaring it.
	
	/**
	 * Returns a Method that can be invoked from outside for the given public method: the method
	 * itself when its declaring class is public and exported, else the same method as declared by a
	 * public, exported super class or interface. As a last resort, the method is made accessible if
	 * possible, and returned as is.
	 */
	public static Method getAccessibleMethod(Method method) {
		if (isAccessibleClass(method.getDeclaringClass())) {
			return method;
		}
		if (!Modifier.isStatic(method.getModifiers())) {
			Method m = findPublicMethod(method.getDeclaringClass(), method.getName(), method.getParameterTypes(), new HashSet<>());
			if (m != null) {
				return m;
			}
		}
		method.trySetAccessible();
		return method;
	}
	private static boolean isAccessibleClass(Class<?> c) {
		for (Class<?> e = c; e != null; e = e.getEnclosingClass()) {
			if (!Modifier.isPublic(e.getModifiers())) {
				return false;
			}
		}
		return c.getModule().isExported(c.getPackageName());
	}
	private static Method findPublicMethod(Class<?> c, String name, Class<?>[] pTypes, Set<Class<?>> visited) {
		if (c == null || !visited.add(c)) {
			return null;
		}
		if (isAccessibleClass(c)) {
			try {
				return c.getMethod(name, pTypes);
			} catch (NoSuchMethodException e) {
				// Not declared by this class hierarchy branch
			}
		}
		Method m = findPublicMethod(c.getSuperclass(), name, pTypes, visited);
		if (m != null) {
			return m;
		}
		for (Class<?> i : c.getInterfaces()) {
			m = findPublicMethod(i, name, pTypes, visited);
			if (m != null) {
				return m;
			}
		}
		return null;
	}
	
	public class MethodCache extends CallableCache {
		Method method;
		volatile Method publicMethod;

		protected MethodCache(Method method) {
			super(method.getParameterTypes());
			this.method = method;
		}

		@Override
		protected Class<?> getOwnerClass() {
			return method.getDeclaringClass();
		}
		
		public Method getMethod() {
			return method;
		}

		/**
		 * The Method actually invoked: see {@link ClassMetadata#getAccessibleMethod(Method)}.
		 */
		public Method getPublicMethod() {
			Method m = publicMethod;
			if (m == null) {
				publicMethod = m = getAccessibleMethod(method);
			}
			return m;
		}

		/**
		 * Invokes the method, working around the non-public declaring class issue.
		 * The parameters must already be converted to the parameter types.
		 */
		public Object invoke(Object _this, Object[] parameters) throws InvocationTargetException, IllegalAccessException {
			return getPublicMethod().invoke(_this, parameters);
		}

		@Override
		public boolean isStatic() {
			int modifier = method.getModifiers();
			return (modifier & Modifier.STATIC) != 0;
		}

		@Override
		public String getName() {
			return method.getName();
		}
		
		@Override
		public String toString() {
			return MessageFormat.format("Method {0}", getName());
		}

		public Object call(BiFunction<Object,Class<?>,Object> convert, boolean staticMethod, Object _this, Object[] parameters) {
			MethodCache m = (MethodCache) findCallable(staticMethod, parameters, true);
			if (m != null) {
				// Convert the parameters
				// Example: Numbers, String<->Characters...
				// Into a copy: the caller's array is left untouched
				Object[] converted = parameters;
				if (parameters != null && parameters.length > 0) {
					Class<?>[] args = m.argClasses;
					converted = new Object[parameters.length];
					for (int i = 0; i < parameters.length; i++) {
						converted[i] = convert.apply(parameters[i], args[i]);
					}
				}
				try {
					return m.invoke(_this, converted);
				} catch (InvocationTargetException e) {
					Throwable te = e.getTargetException();
					throw ModelException.wrap(te);
				} catch (Exception e) {
					Class<?> c = m.method.getDeclaringClass();
					throw new ModelException(e, "Error while calling method {0} of class {1}",
							getMethodSignature(getName(), parameters), c.getName());
				}
			}
			Class<?> c = method.getDeclaringClass();
			throw new ModelException(null, "Cannot find public method {0} for class {1}",
					getMethodSignature(getName(), parameters), c.getName());
		}
	}

	public class ConstructorCache extends CallableCache {
		Constructor<?> constructor;

		protected ConstructorCache(Constructor<?> constructor) {
			super(constructor.getParameterTypes());
			this.constructor = constructor;
		}

		@Override
		protected Class<?> getOwnerClass() {
			return constructor.getDeclaringClass();
		}
		
		public Constructor<?> getConstructor() {
			return constructor;
		}
		
		@Override
		public boolean isStatic() {
			return true;
		}

		@Override
		public String getName() {
			return "new " + constructor.getName();
		}
	}
	
	
	public interface ValueAccessor {
		public Object get(Object instance);
		public void set( BiFunction<Object,Class<?>,Object> convert, Object instance, Object value);
		default boolean isStatic() { return false; }
	}

	public class FieldCache extends MemberCache implements ValueAccessor {
		Field field;

		protected FieldCache(Field field) {
			this.field = field;
			// A public field declared by a non-public class can't be accessed reflectively otherwise
			if (!isAccessibleClass(field.getDeclaringClass())) {
				field.trySetAccessible();
			}
		}
		
		public Field getField() {
			return field;
		}

		@Override
		public boolean isStatic() {
			int modifier = field.getModifiers();
			return (modifier & Modifier.STATIC) != 0;
		}

		@Override
		public Object get(Object instance) {
			if (instance == null != isStatic()) {
				if (isStatic()) {
					throw new ModelException(null,
							"Cannot access static field {0} of class {1} from an instance object", field.getName(),
							field.getDeclaringClass().getName());
				} else {
					throw new ModelException(null,
							"Cannot access instance field {0} of class {1} without an instance object", field.getName(),
							field.getDeclaringClass().getName());
				}
			}
			try {
				return field.get(instance);
			} catch (Exception e) {
				throw new ModelException(e, "Error while accessing field {0} of class {1}", field.getName(),
						field.getDeclaringClass().getName());
			}
		}

		@Override
		public void set(BiFunction<Object,Class<?>,Object> convert, Object instance, Object value) {
			if (instance == null != isStatic()) {
				if (isStatic()) {
					throw new ModelException(null,
							"Cannot access static field {0} of class {1} from an instance object", field.getName(),
							field.getDeclaringClass().getName());
				} else {
					throw new ModelException(null,
							"Cannot access instance field {0} of class {1} without an instance object", field.getName(),
							field.getDeclaringClass().getName());
				}
			}
			try {
				field.set(instance, convert.apply(value, field.getType()));
			} catch (Exception e) {
				throw new ModelException(e, "Error while setting field {0} of class {1}", field.getName(),
						field.getDeclaringClass().getName());
			}

		}
	}

	public class PropertyCache extends MemberCache implements ValueAccessor {
		String name;
		Method getter;
		Method setter;

		protected PropertyCache(String name, Method getter, Method setter) {
			this.name = name;
			this.getter = getter;
			this.setter = setter;
		}
		
		private Method accessibleGetter;
		private Method accessibleSetter;
		private Method accessibleGetter() {
			Method m = accessibleGetter;
			if (m == null) {
				accessibleGetter = m = getAccessibleMethod(getter);
			}
			return m;
		}
		private Method accessibleSetter() {
			Method m = accessibleSetter;
			if (m == null) {
				accessibleSetter = m = getAccessibleMethod(setter);
			}
			return m;
		}

		public String getName() {
			return name;
		}
		
		public Method getGetter() {
			return getter;
		}
		
		public Method getSetter() {
			return setter;
		}
		
		@Override
		public boolean isStatic() {
			return false;
		}

		@Override
		public Object get(Object instance) {
			if (instance == null) {
				throw new ModelException(null,
						"Cannot access instance property {0} of class {1} without an instance object", name,
						getter.getDeclaringClass().getName());
			}
			if (getter == null) {
				throw new ModelException(null, "Cannot read write-only property {0}", name);
			}
			try {
				return accessibleGetter().invoke(instance);
			} catch (Exception e) {
				throw new ModelException(e, "Error while accessing property {0} of class {1}", name,
						getter.getDeclaringClass().getName());
			}
		}

		@Override
		public void set(BiFunction<Object,Class<?>,Object> convert, Object instance, Object value) {
			if (instance == null) {
				throw new ModelException(null,
						"Cannot access instance property {0} of class {1} without an instance object", name,
						getter.getDeclaringClass().getName());
			}
			if (setter == null) {
				throw new ModelException(null, "Error while setting read-only property {0} of class {1}", name,
						getter.getDeclaringClass().getName());
			}
			try {
				accessibleSetter().invoke(instance, convert.apply(value, setter.getParameterTypes()[0]));
			} catch (Exception e) {
				throw new ModelException(e, "Error while setting property {0} of class {1}", name,
						getter.getDeclaringClass().getName());
			}

		}
	}

	protected class BeanPropertyCache extends MemberCache implements ValueAccessor {
		Class<?> clazz;
		PropertyDescriptor desc;
		protected BeanPropertyCache(Class<?> clazz, PropertyDescriptor desc) {
        	this.clazz = clazz;
            this.desc = desc;
        }
       	@Override
		public Object get(Object instance) {
            Method read = desc.getReadMethod();
            if(read==null) {
                throw new ModelException(null,"Java Bean property '{0}' does not have a read method",desc.getName());
            }
            try {
                return read.invoke(instance);
            } catch(Exception e) {
                throw new ModelException(null,"Error while accessing bean property {0} of class {1}",desc.getName(),clazz.getName());
            }
    	}
    	@Override
		public void set(BiFunction<Object,Class<?>,Object> convert, Object instance, Object value) {
            Method write = desc.getWriteMethod();
            if(write==null) {
                throw new ModelException(null,"Java Bean property '{0}' does not have a write method",desc.getName());
            }
            try {
                write.invoke(instance,convert.apply(value,desc.getPropertyType()));
            } catch(Exception e) {
                throw new ModelException(e,"Error while accessing bean property {0} of class {1}",desc.getName(),clazz.getName());
            }
    	}
	}
	
	
	//
	// Factories
	//
	protected MethodCache createMethodCache(Method method) {
		return new MethodCache(method);
	}
	protected ConstructorCache createConstructorCache(Constructor<?> constructor) {
		return new ConstructorCache(constructor);
	}
	protected FieldCache createFieldCache(Field field) {
		return new FieldCache(field);
	}
	protected PropertyCache createPropertyCache(String name, Method getter, Method setter) {
		return new PropertyCache(name,getter,setter);
	}
	protected BeanPropertyCache createBeanPropertyCache(Class<?> clazz, PropertyDescriptor desc) {
		return new BeanPropertyCache(clazz,desc);
	}



	private static class EmptyCache extends MemberCache {
		EmptyCache() {
		}
	}
	private static final EmptyCache emptyCache = new EmptyCache();

	
	public final class ClassInfoCache {

		private Class<?> clazz;
		private volatile ConstructorCache constructors;
		private Map<String, MemberCache> members;
		private Set<String> allMembers;
		private Set<String> valueAccessors;
		private Set<String> methods;
		
		ClassInfoCache(Class<?> clazz) {
			this.clazz = clazz;
			this.members = new ConcurrentHashMap<String, MemberCache>();
		}
		
		public Class<?> getNativeClass() {
			return clazz;
		}
		
		public synchronized Set<String> getAllMembers() {
			if(allMembers==null) {
				allMembers = findAllMembers(new HashSet<>(),clazz);
			}
			return allMembers;
			
		}
		public synchronized Set<String> getValueAccessors() {
			if(valueAccessors==null) {
				valueAccessors = findValueAccessors(new HashSet<>(),clazz);
			}
			return valueAccessors;
			
		}
		public synchronized Set<String> getMethods() {
			if(methods==null) {
				methods = findMethods(new HashSet<>(),clazz);
			}
			return methods;
			
		}

		public ConstructorCache getConstructors() {
			ConstructorCache result = constructors;
			if (result == null) {
				synchronized (this) {
					result = constructors;
					if (result == null) {
						// Build the whole chain before publishing it, so another thread
						// can never observe a partially built list
						Constructor<?>[] c = clazz.getConstructors();
						for (int i = 0; i < c.length; i++) {
							if ((c[i].getModifiers() & Modifier.PUBLIC) == 0) {
								continue;
							}
							ConstructorCache mc = createConstructorCache(c[i]);
							mc.nextCallable = result;
							result = mc;
						}
						constructors = result;
					}
				}
			}
			return result;
		}
		
		// Called on every single Java-interop property/method access
		// (JavaAccessor's get/set/call), so the cache-hit case (the
		// overwhelming majority of calls, once warm) must never block on a
		// lock - ConcurrentHashMap.get() is lock-free. A concurrent miss for
		// the same name racing findMembers() twice is harmless (idempotent
		// computation); putIfAbsent makes sure every caller still observes
		// the same winning MemberCache/emptyCache instance afterward.
		public MemberCache getMembers(String name) {
			MemberCache cached = members.get(name);
			if (cached != null) {
				return cached;
			}

			// If the member name is empty, it is because we are looking for a constructor
			if (name.length() == 0) {
				ConstructorCache first = null;
				return first;
			}

			MemberCache mc = findMembers(clazz, name);
			if (mc == null) {
				// Nothing corresponds here...
				mc = emptyCache;
			}
			MemberCache existing = members.putIfAbsent(name, mc);
			return existing!=null ? existing : mc;
		}
		
		public FieldCache getField(String name) {
			MemberCache mc = getMembers(name);
			while(mc!=null) {
				if(mc instanceof FieldCache f) {
					return f;
				}
				mc = mc.nextMember;
			}
			return null;
		}
		
		public PropertyCache getProperty(String name) {
			MemberCache mc = getMembers(name);
			while(mc!=null) {
				if(mc instanceof PropertyCache p) {
					return p;
				}
				mc = mc.nextMember;
			}
			return null;
		}

		public ValueAccessor getValueAccessor(String name) {
			MemberCache mc = getMembers(name);
			while(mc!=null) {
				if(mc instanceof ValueAccessor a) {
					return a;
				}
				mc = mc.nextMember;
			}
			return null;
		}

		public MethodCache getMethod(String name) {
			MemberCache mc = getMembers(name);
			while(mc!=null) {
				if(mc instanceof MethodCache m) {
					return m;
				}
				mc = mc.nextMember;
			}
			return null;
		}
	}

	protected MemberCache findMembers(Class<?> clazz, String name) {
		MemberCache member = null;

		// No member at all of a class the access manager doesn't expose - this is what
		// keeps reflection (java.lang.Class, java.lang.reflect.*) closed when denied,
		// whatever path led to an instance of that class (e.g. obj.getClass())
		if(accessManager!=null && !accessManager.canAccessMember(clazz)) {
			return null;
		}

		// Look if there is a field with that name
		if(accessManager==null || accessManager.acceptField(name)) {
			FieldCache fc = findField(clazz, name);
			if (fc != null) {
				fc.nextMember = member;
				member = fc;
			}
		}

		// Look for methods with that name
		if(accessManager==null || accessManager.acceptMethod(name)) {
			MethodCache mc = findMethod(clazz, name);
			if (mc != null) {
				mc.nextMember = member;
				member = mc;
			}
		}

		// Look if there is a property with that name
		// Properties have a high priority than fields
		if(accessManager==null || accessManager.acceptProperty(name)) {
			PropertyCache pc = findProperty(clazz, name);
			if (pc != null) {
				pc.nextMember = member;
				member = pc;
			}
			
	        // Look for a javabean property
			if(USE_JAVABEAN_PROPERTIES) {
		        BeanPropertyCache bc = findBeanProperty(clazz, name);
		        if(bc != null) {
					bc.nextMember = member;
					member = bc;
		        }
			}
		}

		return member;
	}
	

	protected Set<String> findAllMembers(Set<String> keys, Class<?> clazz) {
		findValueAccessors(keys,clazz);
		findMethods(keys,clazz);
		return keys;
	}
	@SuppressWarnings("unused")
	protected Set<String> findValueAccessors(Set<String> keys, Class<?> clazz) {
		// Fields
		Field[] allFields = clazz.getFields();
		for(Field f: allFields) {
			keys.add(f.getName());
		}
		// Properties
		if(false) {
			Method[] methods = clazz.getMethods();
			for (int i = 0; i < methods.length; i++) {
				Method m = methods[i];
				// Method should be public and not static
				if ((m.getModifiers() & (Modifier.PUBLIC | Modifier.STATIC)) != Modifier.PUBLIC) {
					continue;
				}
				if (!void.class.equals(m.getReturnType())) { // Else the getter is invalid...
					String mName = m.getName();
					if (mName.startsWith("get")) {
						keys.add(mName.substring(3));
					} else if (mName.startsWith("is")) {
						keys.add(mName.substring(2));
					}
				}
			}
		}
		return keys;
	}
	protected Set<String> findMethods(Set<String> keys, Class<?> clazz) {
		Method[] allMethods = clazz.getMethods();
		for(Method m: allMethods) {
			keys.add(m.getName());
		}
		return keys;
	}


	protected FieldCache findField(Class<?> clazz, String name) {
		try {
			Field f = clazz.getField(name);
			if (f != null && (f.getModifiers() & Modifier.PUBLIC) == Modifier.PUBLIC) {
				// Put it in the list
				FieldCache fc = createFieldCache(f);
				return fc;
			}
		} catch (NoSuchFieldException e) {
		}
		return null;
	}
	protected MethodCache findMethod(Class<?> clazz, String name) {
		MethodCache first = null;
		Method[] m = clazz.getMethods();
		for (int i = 0; i < m.length; i++) {
			if (!m[i].getName().equals(name)) {
				continue;
			}
			if ((m[i].getModifiers() & Modifier.PUBLIC) == 0) {
				continue;
			}
			MethodCache mc = createMethodCache(m[i]);
			mc.nextCallable = first;
			first = mc;
		}
		return first;
	}
	protected PropertyCache findProperty(Class<?> clazz, String name) {
		Method[] methods = clazz.getMethods();

		// 1- Find the getter, this is required
		Method getter = null;
		String nameGet = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
		String nameIs = "is" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
		for (int i = 0; i < methods.length; i++) {
			Method m = methods[i];
			// Method should be public and not static
			// A bridge method (e.g. the Object getV() generated for a covariant String getV())
			// has the erased return type: the setter would then be matched against Object
			if ((m.getModifiers() & (Modifier.PUBLIC | Modifier.STATIC)) != Modifier.PUBLIC || m.isBridge()) {
				continue;
			}
			String mName = m.getName();
			if (mName.equals(nameGet) || mName.equals(nameIs)) {
				if (m.getParameterTypes().length == 0) {
					if (!void.class.equals(m.getReturnType())) { // Else the getter is invalid...
						getter = m;
					}
					break;
				}
			}
		}
		if (getter == null) {
			return null;
		}

		// Find the setter
		Method setter = null;
		String nameSet = "set" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
		for (int i = 0; i < methods.length; i++) {
			Method m = methods[i];
			// Method should be public and not static
			if ((m.getModifiers() & (Modifier.PUBLIC | Modifier.STATIC)) != Modifier.PUBLIC || m.isBridge()) {
				continue;
			}
			String mName = m.getName();
			if (mName.equals(nameSet)) {
				Class<?>[] ptypes = m.getParameterTypes();
				if (ptypes.length == 1) {
					if (ptypes[0] == getter.getReturnType()) {
						setter = m;
						break;
					}
				}
			}
		}

		return createPropertyCache(name, getter, setter);
	}
	protected BeanPropertyCache findBeanProperty(Class<?> clazz, String name) {
        try {
            BeanInfo bi = Introspector.getBeanInfo(clazz);
            PropertyDescriptor[] desc =  bi.getPropertyDescriptors();
            if(desc!=null) {
                for(int i=0; i<desc.length; i++) {
                    if(desc[i].getName().equals(name)) {
                    	BeanPropertyCache pc = createBeanPropertyCache(clazz,desc[i]);
                        return pc;
                    }
                }
            }
        } catch(Exception ex) {}
        return null;
    }
}