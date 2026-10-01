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
package org.monflabs.json.serialization.fields;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.monflabs.json.serialization.ClassAdapter;
import org.monflabs.json.serialization.JsonRegistry;
import org.monflabs.json.serialization.classes.ParameterizedClassAdapter;
import org.monflabs.json.serialization.classes.SimpleClassAdapter;
import org.monflabs.json.serialization.classes.arrays.ObjectArrayClassAdapter;

/**
 * Resolution of the generic types (fields, record components, type arguments) to adapters.
 * <p>
 * The generic parameters of a class adapter are the type parameters of the class, followed
 * by the ones of its enclosing classes when it is an inner (non static) class, see
 * {@link #typeParameters(Class)}.
 */
public class GenericTypeResolver {

	/**
	 * A resolved type.
	 *
	 * It is either static (fully known at init time) or depends on the generic
	 * parameters passed at runtime to the enclosing class adapter.
	 */
	public static class ResolvedType {

		private ClassAdapter adapter;
		private int typeIndex;
		private ResolvedType[] params;
		// Array of a dynamic component
		private ResolvedType component;
		private ClassAdapter fallbackAdapter;
		private ClassAdapter staticAdapter;
		private JsonRegistry registry;
		// The last dynamic resolution: {genericParams, adapter}
		private volatile Object[] last;

		private ResolvedType(ClassAdapter adapter, ResolvedType[] params) {
			this.adapter = adapter;
			this.typeIndex = -1;
			this.params = params;
			if(params==null) {
				this.staticAdapter = adapter;
			} else if(allStatic(params)) {
				this.staticAdapter = new ParameterizedClassAdapter(adapter, resolveParams(null));
			}
		}
		private ResolvedType(int typeIndex, ClassAdapter fallbackAdapter) {
			this.typeIndex = typeIndex;
			this.fallbackAdapter = fallbackAdapter;
		}
		private ResolvedType(ResolvedType component, JsonRegistry registry) {
			this.typeIndex = -1;
			this.component = component;
			this.registry = registry;
			if(component.isStatic()) {
				this.staticAdapter = arrayAdapter(component.staticAdapter, registry);
			}
		}

		private static boolean allStatic(ResolvedType[] params) {
			for(int i=0; i<params.length; i++) {
				if(!params[i].isStatic()) {
					return false;
				}
			}
			return true;
		}

		public boolean isStatic() {
			return staticAdapter!=null;
		}

		/**
		 * Get the adapter for this type, given the generic parameters passed at
		 * runtime to the enclosing class adapter.
		 */
		public ClassAdapter resolve(ClassAdapter[] genericParams) {
			if(staticAdapter!=null) {
				return staticAdapter;
			}
			if(typeIndex>=0) {
				if(genericParams!=null && typeIndex<genericParams.length && genericParams[typeIndex]!=null) {
					return genericParams[typeIndex];
				}
				return fallbackAdapter;
			}
			// The same parameters are usually passed again and again (the adapters bound to
			// a field): the last resolution is reused
			Object[] l = last;
			if(l!=null && sameParams((ClassAdapter[])l[0], genericParams)) {
				return (ClassAdapter)l[1];
			}
			ClassAdapter r;
			if(component!=null) {
				r = arrayAdapter(component.resolve(genericParams), registry);
			} else {
				r = new ParameterizedClassAdapter(adapter, resolveParams(genericParams));
			}
			last = new Object[] { genericParams!=null ? genericParams.clone() : null, r };
			return r;
		}

		private ClassAdapter[] resolveParams(ClassAdapter[] genericParams) {
			ClassAdapter[] p = new ClassAdapter[params.length];
			for(int i=0; i<params.length; i++) {
				p[i] = params[i].resolve(genericParams);
			}
			return p;
		}
	}

	/**
	 * True if two sets of generic parameters hold the same adapters (by identity).
	 */
	static boolean sameParams(ClassAdapter[] a, ClassAdapter[] b) {
		if(a==b) {
			return true;
		}
		if(a==null || b==null || a.length!=b.length) {
			return false;
		}
		for(int i=0; i<a.length; i++) {
			if(a[i]!=b[i]) {
				return false;
			}
		}
		return true;
	}
	
	private static ClassAdapter arrayAdapter(ClassAdapter component, JsonRegistry registry) {
		Class<?> arrayClass = Array.newInstance(component.getAdaptedClazz(), 0).getClass();
		if(!(component instanceof ParameterizedClassAdapter)) {
			// The registry adapter, shared
			ClassAdapter a = registry.findAdapterOrNull(arrayClass);
			if(a!=null) {
				return a;
			}
		}
		ObjectArrayClassAdapter a = new ObjectArrayClassAdapter(arrayClass, component);
		a.init(registry);
		return a;
	}

	/**
	 * Resolve a set of types.
	 */
	public static ResolvedType[] resolve(Type[] types, Class<?> context, JsonRegistry registry) {
		ResolvedType[] r = new ResolvedType[types.length];
		for(int i=0; i<types.length; i++) {
			r[i] = resolve(types[i], context, registry);
		}
		return r;
	}

	/**
	 * Resolve a type in the context of an adapted class (null for a top-level type).
	 */
	public static ResolvedType resolve(Type type, Class<?> context, JsonRegistry registry) {
		if(type instanceof Class<?> c) {
			TypeVariable<?>[] tv = typeParameters(c);
			if(tv.length>0) {
				// Raw use of a generic class: the parameters are unknown, they resolve to
				// their bounds
				ResolvedType[] params = new ResolvedType[tv.length];
				for(int i=0; i<tv.length; i++) {
					params[i] = new ResolvedType(boundAdapter(tv[i], registry), null);
				}
				return new ResolvedType(registry.findAdapter(c), params);
			}
			return new ResolvedType(registry.findAdapter(c), null);
		}
		if(type instanceof ParameterizedType pt) {
			Class<?> raw = erase(pt.getRawType());
			ResolvedType[] params = resolve(typeArguments(pt), context, registry);
			return new ResolvedType(registry.findAdapter(raw), params);
		}
		if(type instanceof WildcardType wt) {
			Type[] upper = wt.getUpperBounds();
			return resolve(upper.length>0 ? upper[0] : Object.class, context, registry);
		}
		if(type instanceof TypeVariable<?> tv) {
			if(context!=null) {
				// A type parameter of the context class (or of its enclosing classes)
				int index = indexOf(typeParameters(context), tv);
				if(index>=0) {
					return new ResolvedType(index, boundAdapter(tv, registry));
				}
				// Look for the binding in the generic superclass chain of the context
				if(tv.getGenericDeclaration() instanceof Class<?> dc) {
					index = indexOf(dc.getTypeParameters(), tv);
					if(index>=0) {
						for(Class<?> c=context; c!=null && c!=dc; c=c.getSuperclass()) {
							Type gs = c.getGenericSuperclass();
							if(gs instanceof ParameterizedType ps && ps.getRawType()==dc) {
								return resolve(ps.getActualTypeArguments()[index], context, registry);
							}
						}
					}
				}
			}
			// Unbound: its bound
			return new ResolvedType(boundAdapter(tv, registry), null);
		}
		if(type instanceof GenericArrayType ga) {
			ResolvedType component = resolve(ga.getGenericComponentType(), context, registry);
			return new ResolvedType(component, registry);
		}
		return new ResolvedType(objectAdapter(registry), null);
	}

	/**
	 * The adapter of the bound of a type variable: <code>T extends Shape</code> uses the
	 * <code>Shape</code> adapter, an unbounded variable (or a bound without adapter, like
	 * <code>Comparable</code>) the <code>Object</code> one.
	 */
	private static ClassAdapter boundAdapter(TypeVariable<?> tv, JsonRegistry registry) {
		Type[] bounds = tv.getBounds();
		Class<?> b = bounds.length>0 ? erase(bounds[0]) : Object.class;
		if(b!=Object.class) {
			try {
				ClassAdapter a = registry.findAdapterOrNull(b);
				// A reflection adapter of an interface or an abstract class (created by a class
				// factory) cannot read anything back: the Object adapter is better
				if(a!=null && !(a instanceof SimpleClassAdapter<?> && (b.isInterface() || Modifier.isAbstract(b.getModifiers())))) {
					return a;
				}
			} catch(RuntimeException ex) {
				// A bound that cannot be adapted (Comparable for a class factory accepting
				// every class...): the values are kept as is
			}
		}
		return objectAdapter(registry);
	}

	/**
	 * The generic parameters of a class: its own type parameters, followed by the ones of its
	 * enclosing class if it is an inner (non static) class.
	 */
	public static TypeVariable<?>[] typeParameters(Class<?> clazz) {
		TypeVariable<?>[] own = clazz.getTypeParameters();
		Class<?> outer = clazz.getDeclaringClass();
		if(outer==null || Modifier.isStatic(clazz.getModifiers()) || clazz.isInterface() || clazz.isEnum() || clazz.isRecord()) {
			return own;
		}
		TypeVariable<?>[] o = typeParameters(outer);
		if(o.length==0) {
			return own;
		}
		TypeVariable<?>[] all = new TypeVariable<?>[own.length+o.length];
		System.arraycopy(own, 0, all, 0, own.length);
		System.arraycopy(o, 0, all, own.length, o.length);
		return all;
	}

	/**
	 * The type arguments of a parameterized type, followed by the ones of its owner when the
	 * type is an inner class (<code>Outer&lt;String&gt;.Inner</code>), matching
	 * {@link #typeParameters(Class)}.
	 */
	private static Type[] typeArguments(ParameterizedType pt) {
		Type[] own = pt.getActualTypeArguments();
		Class<?> raw = erase(pt.getRawType());
		if(pt.getOwnerType() instanceof ParameterizedType owner && typeParameters(raw).length>own.length) {
			Type[] o = typeArguments(owner);
			Type[] all = new Type[own.length+o.length];
			System.arraycopy(own, 0, all, 0, own.length);
			System.arraycopy(o, 0, all, own.length, o.length);
			return all;
		}
		return own;
	}

	/**
	 * The type arguments of a generic supertype (like <code>Map</code>) as seen from a class
	 * (like <code>class Props&lt;V&gt; extends HashMap&lt;String,V&gt;</code>): the result,
	 * <code>[String, V]</code>, is expressed with the type variables of the class. Returns
	 * the type parameters of the supertype if it is used raw, or null if the class does not
	 * extend it.
	 */
	public static Type[] typeArgumentsOf(Class<?> clazz, Class<?> target) {
		if(!target.isAssignableFrom(clazz)) {
			return null;
		}
		Type[] r = typeArgumentsOf((Type)clazz, target, new ArrayList<>());
		return r!=null ? r : target.getTypeParameters();
	}
	private static Type[] typeArgumentsOf(Type type, Class<?> target, List<Class<?>> visited) {
		Class<?> raw = erase(type);
		if(raw==target) {
			return type instanceof ParameterizedType pt ? pt.getActualTypeArguments() : target.getTypeParameters();
		}
		if(!target.isAssignableFrom(raw) || visited.contains(raw)) {
			return null;
		}
		visited.add(raw);
		List<Type> supers = new ArrayList<>();
		if(raw.getGenericSuperclass()!=null) {
			supers.add(raw.getGenericSuperclass());
		}
		Collections.addAll(supers, raw.getGenericInterfaces());
		for(Type s: supers) {
			Type[] r = typeArgumentsOf(s, target, visited);
			if(r!=null) {
				if(type instanceof ParameterizedType pt) {
					// Substitute the type variables of raw with the actual arguments
					TypeVariable<?>[] vars = raw.getTypeParameters();
					Type[] args = pt.getActualTypeArguments();
					Type[] sub = r.clone();
					for(int i=0; i<sub.length; i++) {
						int index = sub[i] instanceof TypeVariable<?> tv ? indexOf(vars, tv) : -1;
						if(index>=0 && index<args.length) {
							sub[i] = args[index];
						}
					}
					return sub;
				}
				// The class itself, or a raw supertype whose variables resolve to their bounds
				return r;
			}
		}
		return null;
	}

	private static int indexOf(TypeVariable<?>[] tvs, TypeVariable<?> tv) {
		for(int i=0; i<tvs.length; i++) {
			if(tvs[i].equals(tv)) {
				return i;
			}
		}
		return -1;
	}

	private static ClassAdapter objectAdapter(JsonRegistry registry) {
		return registry.findAdapter(Object.class);
	}

	/**
	 * Get the erasure of a type.
	 */
	public static Class<?> erase(Type type) {
		if(type instanceof Class<?> c) {
			return c;
		}
		if(type instanceof ParameterizedType pt) {
			return erase(pt.getRawType());
		}
		if(type instanceof GenericArrayType ga) {
			return Array.newInstance(erase(ga.getGenericComponentType()), 0).getClass();
		}
		if(type instanceof WildcardType wt) {
			Type[] upper = wt.getUpperBounds();
			return upper.length>0 ? erase(upper[0]) : Object.class;
		}
		if(type instanceof TypeVariable<?> tv) {
			Type[] bounds = tv.getBounds();
			return bounds.length>0 ? erase(bounds[0]) : Object.class;
		}
		return Object.class;
	}
}
