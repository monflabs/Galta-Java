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
package org.monflabs.json.jsonpath;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonObject;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.TypeRef;
import com.jayway.jsonpath.spi.mapper.MappingException;
import com.jayway.jsonpath.spi.mapper.MappingProvider;

/**
 * Maps the values read by Jayway to the requested type.
 * <p>
 * JSON containers are returned as is when a Galta container type is requested, or converted
 * to <code>List</code>/<code>Map</code> (key order preserved) for <code>Object</code>,
 * <code>List</code> and <code>Map</code>. Numbers are converted between numeric types, but a
 * conversion that would lose information (a fraction, a value out of range, or digits that a
 * double or a float cannot hold) throws a {@link MappingException}, as does any other
 * incompatible conversion. Any value maps to
 * <code>String</code> through its string form.
 */
public class MonfLabsMappingProvider implements MappingProvider {
	
    @SuppressWarnings("unchecked")
	@Override
    public <T> T map(Object source, Class<T> targetType, Configuration configuration) {
        if(source == null){
            return null;
        }
        if(targetType.equals(Object.class) || targetType.equals(List.class) || targetType.equals(Map.class)){
            Object o = mapToObject(source);
            if(targetType!=Object.class && !targetType.isInstance(o)) {
            	throw incompatible(source, targetType);
            }
            return (T)o;
        }
        if(targetType.isInstance(source)) {
        	return (T)source;
        }
        Class<?> t = boxed(targetType);
        if(t.isInstance(source)) {
        	return (T)source;
        }
        if(t==String.class) {
        	return (T)source.toString();
        }
        if(source instanceof Number n) {
        	Object r = convertNumber(n, t);
        	if(r!=null) {
        		return (T)r;
        	}
        }
        if(t==Boolean.class && source instanceof String s) {
        	if(s.equals("true")) return (T)Boolean.TRUE;
        	if(s.equals("false")) return (T)Boolean.FALSE;
        }
        if(source instanceof String s && Number.class.isAssignableFrom(t)) {
        	try {
        		Object r = convertNumber(new BigDecimal(s.trim()), t);
        		if(r!=null) {
        			return (T)r;
        		}
        	} catch(NumberFormatException ex) {
        		// fall through
        	}
        }
        throw incompatible(source, targetType);
    }

    @SuppressWarnings("unchecked")
	@Override
    public <T> T map(Object source, TypeRef<T> targetType, Configuration configuration) {
    	Type type = targetType.getType();
    	Class<?> raw = null;
    	if(type instanceof Class<?> c) {
    		raw = c;
    	} else if(type instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> c) {
    		// The element types are not converted: only the container is mapped
    		raw = c;
    	}
    	if(raw==null) {
    		throw new MappingException("Unsupported TypeRef "+type);
    	}
    	return (T)map(source, raw, configuration);
    }

    private static MappingException incompatible(Object source, Class<?> targetType) {
    	return new MappingException("Cannot convert a "+source.getClass().getName()+" to "+targetType.getName());
    }
    
    private static Class<?> boxed(Class<?> c) {
    	if(!c.isPrimitive()) return c;
    	if(c==int.class) return Integer.class;
    	if(c==long.class) return Long.class;
    	if(c==double.class) return Double.class;
    	if(c==float.class) return Float.class;
    	if(c==short.class) return Short.class;
    	if(c==byte.class) return Byte.class;
    	if(c==boolean.class) return Boolean.class;
    	if(c==char.class) return Character.class;
    	return c;
    }
    
    /**
     * Exact conversion of a number, or a MappingException if information would be lost.
     * Returns null if the target is not a supported numeric type.
     */
    private static Object convertNumber(Number n, Class<?> t) {
    	if(t==Double.class || t==Float.class) {
    		return toFloatingExact(n, t==Float.class);
    	}
    	if(t==BigDecimal.class) {
    		return toBigDecimal(n);
    	}
    	if(t==Integer.class || t==Long.class || t==Short.class || t==Byte.class || t==BigInteger.class) {
    		BigInteger bi;
    		try {
    			bi = toBigDecimal(n).toBigIntegerExact();
    		} catch(ArithmeticException ex) {
    			throw new MappingException("Cannot convert "+n+" to "+t.getName()+" without losing its fraction");
    		}
    		if(t==BigInteger.class) {
    			return bi;
    		}
    		try {
	    		if(t==Long.class) return bi.longValueExact();
	    		if(t==Integer.class) return bi.intValueExact();
	    		if(t==Short.class) return bi.shortValueExact();
	    		return bi.byteValueExact();
    		} catch(ArithmeticException ex) {
    			throw new MappingException("Value "+n+" is out of range for "+t.getName());
    		}
    	}
    	return null;
    }
    /**
     * A double or a float with the same (decimal) value: 0.1 converts, as its shortest
     * representation is 0.1, but 9007199254740993 (2^53+1) or 1e300 as a float do not.
     */
    private static Object toFloatingExact(Number n, boolean toFloat) {
    	if(n instanceof Double || n instanceof Float) {
    		double d = n.doubleValue();
    		if(Double.isNaN(d) || Double.isInfinite(d)) {
    			return toFloat ? (Object)(float)d : (Object)d;
    		}
    	}
    	BigDecimal exact = toBigDecimal(n);
    	if(toFloat) {
    		float f = n.floatValue();
    		if(Float.isInfinite(f) || new BigDecimal(Float.toString(f)).compareTo(exact)!=0) {
    			throw new MappingException("Cannot convert "+n+" to a float without losing precision");
    		}
    		return f;
    	}
    	double d = n.doubleValue();
    	if(Double.isInfinite(d) || BigDecimal.valueOf(d).compareTo(exact)!=0) {
    		throw new MappingException("Cannot convert "+n+" to a double without losing precision");
    	}
    	return d;
    }
    private static BigDecimal toBigDecimal(Number n) {
    	if(n instanceof BigDecimal bd) return bd;
    	if(n instanceof BigInteger bi) return new BigDecimal(bi);
    	if(n instanceof Double || n instanceof Float) {
    		double d = n.doubleValue();
    		if(Double.isNaN(d) || Double.isInfinite(d)) {
    			throw new MappingException("Cannot convert "+d+" to a decimal number");
    		}
    		return BigDecimal.valueOf(d);
    	}
    	return BigDecimal.valueOf(n.longValue());
    }

    //
    // This can optimized to avoid temporary list an Map creations
    //
    private Object mapToObject(Object source){
        if(source instanceof JsonArray array){
        	List<Object> mapped = new ArrayList<Object>(array.size());
            for (int i = 0; i < array.size(); i++){
                mapped.add(mapToObject(array.get(i)));
            }
            return mapped;
        }
        else if (source instanceof JsonObject obj){
        	// Keep the JSON key order
            Map<String, Object> mapped = new LinkedHashMap<String, Object>();
            for (String key : obj.keySet()) {
                mapped.put(key, mapToObject(obj.get(key)));
            }
            return mapped;
        } else {
            return source;
        }
    }
}
