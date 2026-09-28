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
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;

import org.monflabs.json.serialization.ClassAdapter;
import org.monflabs.json.serialization.JsonRegistry;
import org.monflabs.json.serialization.classes.ParameterizedClassAdapter;

/**
 * Resolves a reflected {@link Type} into a {@link ClassAdapter}.
 * 
 * The resolution happens in the context of an adapted class (the class the
 * enclosing {@link org.monflabs.json.serialization.classes.SimpleClassAdapter}
 * was built for):
 * <ul>
 *   <li>a plain class resolves to its registered adapter; a raw generic class
 *       (List, Map...) gets Object adapters for its parameters</li>
 *   <li>a parameterized type resolves recursively, binding the type arguments</li>
 *   <li>a wildcard resolves to its upper bound</li>
 *   <li>a type variable of the adapted class is looked up, at runtime, in the
 *       generic parameters passed to the field adapter</li>
 *   <li>a type variable of a superclass is resolved through the generic
 *       superclass chain (class Sub extends Base&lt;String&gt;)</li>
 *   <li>anything else (unbound type variable...) resolves to the Object adapter</li>
 * </ul>
 * 
 * @author priand
 *
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
		private ClassAdapter objectAdapter;
		private ClassAdapter staticAdapter;
		
		private ResolvedType(ClassAdapter adapter, ResolvedType[] params) {
			this.adapter = adapter;
			this.typeIndex = -1;
			this.params = params;
			if(params==null) {
				this.staticAdapter = adapter;
			} else {
				boolean isStatic = true;
				for(int i=0; i<params.length; i++) {
					if(!params[i].isStatic()) {
						isStatic = false;
						break;
					}
				}
				if(isStatic) {
					this.staticAdapter = new ParameterizedClassAdapter(adapter, resolveParams(null));
				}
			}
		}
		private ResolvedType(int typeIndex, ClassAdapter objectAdapter) {
			this.typeIndex = typeIndex;
			this.objectAdapter = objectAdapter;
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
				return objectAdapter;
			}
			return new ParameterizedClassAdapter(adapter, resolveParams(genericParams));
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
	 * Resolve a type in the context of an adapted class.
	 */
	public static ResolvedType resolve(Type type, Class<?> context, JsonRegistry registry) {
		if(type instanceof Class<?> c) {
			TypeVariable<?>[] tv = c.getTypeParameters();
			if(tv.length>0) {
				// Raw use of a generic class: the parameters are unknown
				ResolvedType[] params = new ResolvedType[tv.length];
				ResolvedType object = new ResolvedType(objectAdapter(registry), null);
				for(int i=0; i<tv.length; i++) {
					params[i] = object;
				}
				return new ResolvedType(registry.findAdapter(c), params);
			}
			return new ResolvedType(registry.findAdapter(c), null);
		}
		if(type instanceof ParameterizedType pt) {
			Class<?> raw = erase(pt.getRawType());
			ResolvedType[] params = resolve(pt.getActualTypeArguments(), context, registry);
			return new ResolvedType(registry.findAdapter(raw), params);
		}
		if(type instanceof WildcardType wt) {
			Type[] upper = wt.getUpperBounds();
			return resolve(upper.length>0 ? upper[0] : Object.class, context, registry);
		}
		if(type instanceof TypeVariable<?> tv) {
			if(tv.getGenericDeclaration() instanceof Class<?> dc) {
				int index = indexOf(dc, tv);
				if(index>=0) {
					if(dc==context) {
						return new ResolvedType(index, objectAdapter(registry));
					}
					// Look for the binding in the generic superclass chain of the context
					for(Class<?> c=context; c!=null && c!=dc; c=c.getSuperclass()) {
						Type gs = c.getGenericSuperclass();
						if(gs instanceof ParameterizedType ps && ps.getRawType()==dc) {
							return resolve(ps.getActualTypeArguments()[index], context, registry);
						}
					}
				}
			}
			return new ResolvedType(objectAdapter(registry), null);
		}
		if(type instanceof GenericArrayType) {
			return new ResolvedType(registry.findAdapter(erase(type)), null);
		}
		return new ResolvedType(objectAdapter(registry), null);
	}
	
	private static int indexOf(Class<?> clazz, TypeVariable<?> tv) {
		TypeVariable<?>[] tvs = clazz.getTypeParameters();
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
