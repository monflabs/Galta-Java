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
package org.monflabs.json;

import java.io.IOException;
import java.io.Reader;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import org.monflabs.json.jsonpath.JsonValues;
import org.monflabs.json.stringifier.JsonStringifier.StringSerializer;
import org.monflabs.util.iterators.Iterators;

/**
 * Json Container - Object or array.
 */
public interface JsonContainer extends Cloneable {
	
	// Just to make the method signature different
	public interface ObjectConsumer extends Consumer<JsonObject> {}
	public interface ArrayConsumer extends Consumer<JsonArray> {}
	
	
	public JsonFactory factory();
	
	public boolean isObject();
	public boolean isArray();
	
	public int size();
	public boolean isEmpty();
	public void clear();
	
	public Collection<Object> values();
	
	public Object toNativeJsonPrimitive();
	public JsonValues jsonValues();
	
	@SuppressWarnings("unchecked")
	public default <T> T firstValue() {
		if(isEmpty()) {
			throw new JsonException(null,"Collection is empty");
		}
		return (T)Iterators.first(values().iterator());
	}
	@SuppressWarnings("unchecked")
	public default <T> T firstValueOrDefault(T defaultValue) {
		if(isEmpty()) {
			return defaultValue;
		}
		return (T)Iterators.first(values().iterator());
	}
	
	@SuppressWarnings("unchecked")
	public default <T> T lastValue() {
		if(isEmpty()) {
			throw new JsonException(null,"Collection is empty");
		}
		return (T)Iterators.last(values().iterator());
	}
	@SuppressWarnings("unchecked")
	public default <T> T lastValueOrDefault(T defaultValue) {
		if(isEmpty()) {
			return defaultValue;
		}
		return (T)Iterators.last(values().iterator());
	}

	
	//
	// Shortcuts
	public static JsonContainer parse(String json) {
		return (JsonContainer)JsonFactory.get().parse(json);
	}
	public static JsonContainer parse(Reader json) {
		return (JsonContainer)JsonFactory.get().parse(json);
	}

	public default String stringify() {
		return stringify(true);
	}
	public default String stringify(boolean compact) {
		try {
			StringSerializer sg = new StringSerializer();
			sg.setCompact(compact);
			return sg.stringify(this);
		} catch(IOException e) {
			throw new JsonException(e);
		}
	}
	
	public JsonContainer clone();
	public default JsonContainer deepClone() {
		return (JsonContainer)factory().deepClone(this);
	}
	
	public default JsonContainer forEachValue(Consumer<JsonValues> action) {
		values().forEach( (v) -> action.accept(JsonValues.of(v)) );
		return this;
	}

	
	/////////////////////////////////////////////////////////////////
	//
	// JSON Reference implementation
	//
	/////////////////////////////////////////////////////////////////
	
	public default String getReference() {
		return null;
	}
	
	public default void setReference(String reference) {
		throw new JsonException(null,"Class {0} does not support references",getClass());
	}
	
	
	/////////////////////////////////////////////////////////////////
	//
	// Stream like functions
	//
	/////////////////////////////////////////////////////////////////

	// An empty key in find is equivalent to $..* in json path
	
	public default JsonArray find(String key) {
    	return find(key,true);
    }
	public default JsonArray find(String key, boolean deep) {
        JsonArray r = factory().createArray();
        _find(r,this,key,deep);
    	return r;
	}
	public default void findAndSet(String key, Object v) {
		_findAndSet(this,key,v);
	}
	
    private static void _find(List<Object> values, Object value, String key, boolean deep) {
    	if(value instanceof JsonObject o) {
    		if(key==null) {
        		for(Object v: o.values()) {
        			values.add(v);
        		}
    		} else if(o.has(key)) {
    			values.add(o.get(key));
    		}
			for(String k: o.keySet()) {
				if(deep || (key!=null && !k.equals(key))) {
					_find(values,o.get(k),key,deep);
				}
			}
    	} else if(value instanceof JsonArray a) {
    		if(key==null) {
    			int sz = a.size();
        		for(int i=0; i<sz; i++) {
        			values.add(a.get(i));
        		}
        		if(!deep) {
        			return;
        		}
    		}
			for(Object v: a.values()) {
				_find(values,v,key,deep);
			}
    	}
    }
    private static void _findAndSet(Object value, String key, Object v) {
    	if(value instanceof JsonObject o) {
    		if(key==null) {
        		for(String k: o.keySet()) {
        			o.put(k,v);
        		}
       			return;
    		} else if(o.has(key)) {
    			o.put(key,v);
    		}
			for(String k: o.keySet()) {
				if(!k.equals(key)) {
					_findAndSet(o.get(k),key,v);
				}
			}
    	} else if(value instanceof JsonArray a) {
			int sz = a.size();
    		if(key==null) {
        		for(int i=0; i<sz; i++) {
        			a.set(i, v);
        		}
        		return;
    		}
    		for(int i=0; i<sz; i++) {
				_findAndSet(a.get(i),key,v);
			}
    	}
    }
    
    
	//
	// Index member search
	//
	public default JsonArray find(int index) {
    	return find(index,true);
    }
	public default JsonArray find(int index, boolean deep) {
        JsonArray r = factory().createArray();
        _find(r,this,index,deep);
    	return r;
	}
	public default void findAndSet(int index, Object v) {
		_findAndSet(this,index,v);
	}
	
    private static void _find(List<Object> values, Object value, int index, boolean deep) {
    	if(value instanceof JsonObject o) {
			for(Object v: o.values()) {
				_find(values,v,index,deep);
			}
    	} else if(value instanceof JsonArray a) {
    		int idx = a.actualIndex(index);
    		if(a.has(idx)) {
    			values.add(a.get(idx));
    		}
    		int count = a.size();
			for(int i=0; i<count; i++) {
				if(deep || i!=idx) {
					_find(values,a.get(i),index,deep);
				}
			}
    	}
    }
    private static void _findAndSet(Object value, int index, Object v) {
    	if(value instanceof JsonObject o) {
			for(Object val: o.values()) {
				_findAndSet(val,index,v);
			}
    	} else if(value instanceof JsonArray a) {
    		int idx = a.actualIndex(index);
    		if(a.has(idx)) {
    			a.set(idx,v);
    		}
			int sz = a.size();
    		for(int i=0; i<sz; i++) {
				if(i!=idx) {
					_findAndSet(a.get(i),index,v);
				}
			}
    	}
    }

	
    public  JsonArray flat();
}
