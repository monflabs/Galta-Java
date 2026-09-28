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
package org.monflabs.tests;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.annotation.NonNull;


/**
 * Java Object Accessor.
 * 
 * This allows the private members (fields &amp; methods) to be accessed
 */
public class JavaAccessor {
	
	private Class<?> clazz;
	private Object object;
	
	public JavaAccessor(Class<?> clazz, Object object) {
		this.clazz = clazz;
		this.object = object;
	}
	
	@SuppressWarnings("unchecked")
	public <T> T getWrappedObject() {
		return (T)object;
	}
	
	@SuppressWarnings("unchecked")
	public <T> Class<T> getWrappedClass() {
		return (Class<T>)clazz;
	}


	public Object get(String field) {
		try {
			return findField(field).get(object);
		} catch(Exception ex) { throw new RuntimeException(ex); }
	}
	public boolean getBoolean(String field) {
		return toBoolean(get(field));
	}
	public char getChar(String field) {
		return toChar(get(field));
	}
	public byte getByte(String field) {
		return toByte(get(field));
	}
	public short getShort(String field) {
		return toShort(get(field));
	}
	public int getInt(String field) {
		return toInt(get(field));
	}
	public long getLong(String field) {
		return toLong(get(field));
	}
	public float getFloat(String field) {
		return toFloat(get(field));
	}
	public double getDouble(String field) {
		return toDouble(get(field));
	}

	
	@SuppressWarnings("unchecked")
	public <T> T call(String method, Object...params) {
		try {
			return (T)findMethod(method,params).invoke(object,params);
		} catch(Exception ex) { throw new RuntimeException(ex); }
	}
	public boolean callBoolean(String method, Object...params) {
		return toBoolean(call(method,params));
	}
	public char callChar(String method, Object...params) {
		return toChar(call(method,params));
	}
	public byte callByte(String method, Object...params) {
		return toByte(call(method,params));
	}
	public short callShort(String method, Object...params) {
		return toShort(call(method,params));
	}
	public int callInt(String method, Object...params) {
		return toInt(call(method,params));
	}
	public long callLong(String method, Object...params) {
		return toLong(call(method,params));
	}
	public float callFloat(String method, Object...params) {
		return toFloat(call(method,params));
	}
	public double callDouble(String method, Object...params) {
		return toDouble(call(method,params));
	}

	
	public Object newObject(Object...params) {
		try {
			return findConstructor(params).newInstance(params);
		} catch(Exception ex) { throw new RuntimeException(ex); }
	}

	
	private boolean toBoolean(Object v) {
		if(v instanceof Boolean b) {
			return b.booleanValue();
		}
		throw new RuntimeException(MessageFormat.format("Value is not a boolean but {0}", v!=null?v.getClass():"null"));
	}
	private char toChar(Object v) {
		if(v instanceof Character c) {
			return c.charValue();
		}
		throw new RuntimeException(MessageFormat.format("Value is not a char but {0}", v!=null?v.getClass():"null"));
	}
	private byte toByte(Object v) {
		if(v instanceof Byte b) {
			return b.byteValue();
		}
		throw new RuntimeException(MessageFormat.format("Value is not a byte but {0}", v!=null?v.getClass():"null"));
	}
	private short toShort(Object v) {
		if(v instanceof Short s) {
			return s.shortValue();
		}
		throw new RuntimeException(MessageFormat.format("Value is not a short but {0}", v!=null?v.getClass():"null"));
	}
	private int toInt(Object v) {
		if(v instanceof Integer i) {
			return i.intValue();
		}
		throw new RuntimeException(MessageFormat.format("Value is not an int but {0}", v!=null?v.getClass():"null"));
	}
	private long toLong(Object v) {
		if(v instanceof Long l) {
			return l.longValue();
		}
		throw new RuntimeException(MessageFormat.format("Value is not a long but {0}", v!=null?v.getClass():"null"));
	}
	private float toFloat(Object v) {
		if(v instanceof Float f) {
			return f.floatValue();
		}
		throw new RuntimeException(MessageFormat.format("Value is not a float but {0}", v!=null?v.getClass():"null"));
	}
	private double toDouble(Object v) {
		if(v instanceof Double d) {
			return d.doubleValue();
		}
		throw new RuntimeException(MessageFormat.format("Value is not a double but {0}", v!=null?v.getClass():"null"));
	}

	
	private Field findField(String field) {		
		ClassInfoCache ci = getClassInfoCache(clazz);
		FieldCache fc = ci.getField(field);
		if(fc!=null) {
			return fc.field;
		}
		throw new RuntimeException(MessageFormat.format("Cannot find field {0}",field));
	}

	private Method findMethod(String method, Object...params) {		
		ClassInfoCache ci = getClassInfoCache(clazz);
		MethodCache mc = ci.getMethod(method);
		if(mc!=null) {
			MethodCache m = (MethodCache)findCallable(ci, params, method, mc, false);
			if(m!=null) {
				return m.method;
			}
		}
		throw new RuntimeException(MessageFormat.format("Cannot find method {0}",method));
	}

	private Constructor<?> findConstructor(Object...params) {		
		ClassInfoCache ci = getClassInfoCache(clazz);
		ConstructorCache cc = ci.getConstructors();
		if(cc!=null) {
			ConstructorCache c = (ConstructorCache)findCallable(ci, params, "constructor", cc, false);
			if(c!=null) {
				return c.constructor;
			}
		}
		throw new RuntimeException("Cannot find constructor");
	}
	
	
	
	// A ClassValue does not prevent the classes (and their class loader) from being unloaded
	private static final ClassValue<ClassInfoCache> classCache = new ClassValue<>() {
		@Override
		protected ClassInfoCache computeValue(Class<?> type) {
			return new ClassInfoCache(type);
		}
	};

    private static ClassInfoCache getClassInfoCache(Class<?> c) {
        return classCache.get(c);
    }

    protected static abstract class MemberCache {
    }

    protected static abstract class CallableCache extends MemberCache {
        CallableCache next;
        Class<?>[] argClasses;
        CallableCache(Class<?>[] argClasses) {
            this.argClasses = argClasses;
        	// Transform the primitive types to their object ones
        	if(argClasses!=null) {
        		for(int i=0; i<argClasses.length; i++) {
        			if(argClasses[i].isPrimitive()) {
        				argClasses[i] = getObjectTypeFromPrimitive(argClasses[i]);
        			}
        		}
        	}
        }
        private Class<?> getObjectTypeFromPrimitive(Class<?> c) {
            // Transform a primitive to its Object based class
            if(c==Character.TYPE) {
                return Character.class;
            }
            if(c==Byte.TYPE) {
                return Byte.class;
            }
            if(c==Short.TYPE) {
                return Short.class;
            }
            if(c==Integer.TYPE) {
                return Integer.class;
            }
            if(c==Long.TYPE) {
                return Long.class;
            }
            if(c==Float.TYPE) {
                return Float.class;
            }
            if(c==Double.TYPE) {
                return Double.class;
            }
            if(c==Boolean.TYPE) {
                return Boolean.class;
            }
            return Void.class;
        }
    }

    protected static class FieldCache extends MemberCache {
        Field field;
        FieldCache(Field field) {
            this.field = field;
            field.setAccessible(true);
        }
        @Override
		public String toString() {
            return field.getName();
        }
    }
    
    private static class MethodCache extends CallableCache {
        Method  method;
        MethodCache(Method method) {
            super(method.getParameterTypes());
            this.method = method;
            method.setAccessible(true);
        }
        @Override
		public String toString() {
            return method.getName()+"()";
        }
    }

    private static class ConstructorCache extends CallableCache {
        Constructor<?> constructor;
        ConstructorCache(Constructor<?> constructor) {
            super(constructor.getParameterTypes());
            this.constructor = constructor;
            constructor.setAccessible(true);
        }
        @Override
		public String toString() {
            return "new "+constructor.getName()+"()";
        }
    }

    private static class ClassInfoCache {

        private Class<?> clazz;
        private volatile ConstructorCache constructors;
        // Concurrent: the accessors can be used from several threads
        private Map<String,FieldCache> fields;
        private Map<String,MethodCache> methods;

        ClassInfoCache(Class<?> clazz) {
            this.clazz = clazz;
            this.fields = new ConcurrentHashMap<String,FieldCache>();
            this.methods = new ConcurrentHashMap<String,MethodCache>();
        }

        ConstructorCache getConstructors() {
            if(constructors==null) {
            	synchronized(this) {
            		// Constructor: we do not look into the supper classes...
	                if(constructors==null) {
	                	// Build the whole list before publishing it
	                	ConstructorCache list = null;
		                Constructor<?>[] c=clazz.getDeclaredConstructors();
		                for(int i=0; i<c.length; i++) {
		                    ConstructorCache cc = new ConstructorCache(c[i]);
		                    cc.next = list;
		                    list = cc;
		                }
		                constructors = list;
	                }
            	}
            }
            return constructors;
        }
    
	    protected FieldCache getField(String name) {
	    	FieldCache cached = fields.get(name);
	        if(cached!=null) {
	            return cached;
	        }
	        cached = findField(name);
	        if(cached!=null) {
	        	fields.put(name,cached);
	        }
	    	return cached;
	    }
	
	    protected FieldCache findField(String name) {
        	for(Class<?> c=clazz; c!=null; c=c.getSuperclass()) {
            	Field[] f=c.getDeclaredFields();
        		for(int i=0; i<f.length; i++) {
    	            if(!f[i].getName().equals(name)) {
    	                continue;
    	            }
    				FieldCache fc = new FieldCache(f[i]);
    				return fc;
        		}
        	}
	        return null;
	    }

	    protected MethodCache getMethod(String name) {
	    	MethodCache cached = methods.get(name);
	        if(cached!=null) {
	            return cached;
	        }
	        cached = findMethod(name);
	        if(cached!=null) {
	        	methods.put(name,cached);
	        }
	    	return cached;
	    }
	    
	    protected MethodCache findMethod(String name) {
	    	// The order we discover the methods is significant
	    	// The top level classes should be checked first
	    	// We also do not add the duplicates from the parent classes because they have been overridden
	    	Set<String> methodKeys = new HashSet<>();
	        MethodCache first = null;
	        MethodCache last = null;
	        for(Class<?> c=clazz; c!=null; c=c.getSuperclass()) {
		        Method[] m=c.getDeclaredMethods();
		        for(int i=0; i<m.length; i++) {
		            if(!m[i].getName().equals(name)) {
		                continue;
		            }
		            String key = methodSignature(m[i].getParameterTypes());
		            if(methodKeys.contains(key)) {
		            	continue;
		            }
		            methodKeys.add(key);
		            MethodCache mc = new MethodCache(m[i]);
		            if(first==null) {
		            	first = mc;
		            }
		            if(last!=null) {
		            	last.next = mc;
		            }
		            last = mc;
		        }
	        }
	        return first;
	    }	 
    }
    
    private CallableCache findCallable(Object instance, @NonNull Object[] args, String name, CallableCache cache, boolean strictMatch) {
    	int argsLength = args.length;
        CallableCache found = null;
        Class<?>[] argsFound = null;
        boolean ambiguity = false;
loop:   for(CallableCache m=cache; m!=null; m=m.next) {
			if(m instanceof MethodCache mc) {
				Method md = mc.method;
	            if( instance==null && (md.getModifiers()&Modifier.STATIC)!=0 ) {
	                continue;
	            }
			}

			// Variable parameters are not yet handled
            Class<?>[] argClasses=m.argClasses;
            if(argClasses.length!=argsLength) {
                continue;
            }

            boolean exact = true;
            for(int j=0; j<argsLength; j++) {
            	Object param=args[j];
            	if(param==null) {
            		// null matches any (boxed) parameter type, but never exactly
            		exact = false;
            		continue;
            	}
                if(argClasses[j]==param.getClass()) {
                	// Exact class
                } else { 
                	exact = false;
	                if(!isAssignable(argClasses[j],param.getClass())) {
	                	continue loop;
	                }
                }
            }
            
            // If it matches precisely, then we're good
            if(exact) {
            	return m;
            }

            if(!ambiguity) {
	            // Compare this method to the other one found
	            // We try to keep the more specific here
	            if(found!=null) {
	                int r = compareArguments(argsFound,argClasses);
	                if(r==0) {
	                	// There is a potential ambiguity here that might be relieved by an exact match
	                	ambiguity = true;
	                }
	                if(r==1) {
	                    // Keep the old one as it is more specific
	                    continue loop;
	                }
	            }
	
	            // Set this method as the one to use
	            found = m;
	            argsFound = argClasses;
            }
        }
        
        if(ambiguity) {
            throw new RuntimeException(MessageFormat.format("Ambiguity when calling {0}{1}",name,methodSignature(argsFound)));
        }

        return found;
    }
    protected boolean isAssignable(Class<?> c1, Class<?> c2) {
    	// Directly assignable
        if(c1.isAssignableFrom(c2)) {
        	return true;
        }
// This can be enabled for scripting languages        
//        // String and Character can be exchanged
//        if( (c1==Character.class && c2==String.class) || (c2==Character.class && c1==String.class)) {
//        	return true;
//        }
//        // Numbers can be converted
//        if(Number.class.isAssignableFrom(c1) && Number.class.isAssignableFrom(c2)) {
//        	return true;
//        }
        // Ok, not compatible
        return false;
    }


    // According to the Java spec, we are looking for the most specific method
    // "The informal intuition is that one method is more specific than another if any invocation
    // handled by the first method could be passed on to the other one without a compile-time type error."
    // This method returns 3 values:
    //    0:  incompatible. This leads to an error
    //    1:  a1 is more specific than a2
    //    -1: a2 is more specific than a1
	protected final int compareArguments(Class<?>[] a1, Class<?>[] a2) {
        int result = 0;
        int length = a1.length;
        for(int i=0; i<length; i++ ) {
            Class<?> c1 = a1[i];
            Class<?> c2 = a2[i];
            if(c1!=c2) {
                if( c1.isAssignableFrom(c2) ) {
                    if(result==1) {
                        return 0;
                    }
                    result = -1;
                } if( c2.isAssignableFrom(c1) ) {
                    if(result==-1) {
                        return 0;
                    }
                    result = 1;
                }
            }
        }
        return result;
    }
	
	// Utility
    private static String methodSignature(Class<?>[] c) {
        StringBuilder b = new StringBuilder(64);
        b.append("(");
        if(c!=null) {
            for( int i=0; i<c.length; i++ ) {
                if(i>0) {
                    b.append(", ");
                }
                b.append(c[i].getName());
            }
        }
        b.append(")");
        return b.toString();
    }
}
