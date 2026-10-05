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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

import org.eclipse.jdt.annotation.NonNull;
import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonContainer;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.json.JsonUtil;
import org.monflabs.json.jsonpointer.JsonPointer;
import org.monflabs.json.util.Reducer;
import org.monflabs.util.StringFormat;
import org.monflabs.util.iterators.Iterators;

public class JsonValues implements Iterable<JsonValues> {

	// Immutable singletons
	public static final JsonValues EMPTY = new JsonValues();
	public static final JsonValues NULL = new JsonValues((Object) null);
	public static final JsonValues ZERO = new JsonValues((Object) 0);

	public static enum TYPE {
		EMPTY, VALUE, LIST,
	}

	public static JsonValues of() {
		return EMPTY;
	}

	public static JsonValues of(Object value) {
		//assert(!(value instanceof JsonValues));
		if (value == null) {
			return NULL;
		}
		return new JsonValues(value);
	}

	public static JsonValues of(Object value, Object... values) {
		//assert(!(value instanceof JsonValues));
		JsonValues r = new JsonValues(value);
		int length = values.length;
		for (int i = 0; i < length; i++) {
			//assert(!(values[i] instanceof JsonValues));
			r._add(values[i]);
		}
		return r;
	}

	public static JsonValues build(Consumer<Consumer<Object>> builder) {
		if(builder!=null) {
			JsonValues r = new JsonValues();
			builder.accept( (v) -> {
				r._add(v);
			});
			return r;
		}
		return EMPTY;
	}

	public static JsonValues flat(JsonArray a) {
		JsonValues r = new JsonValues();
		if (a != null) {
			int length = a.size();
			for (int i = 0; i < length; i++) {
				r._add(a.get(i));
			}
		}
		return r;
	}

	public static JsonValues parseAndFlat(String a) {
		return flat(JsonArray.parse(a));
	}

	public static JsonValues parse(String a) {
		return of(JsonFactory.get().parse(a));
	}

	JsonValues.TYPE type;
	Object value;
	List<JsonPointer> pointers;

	protected JsonValues() {
		this.type = TYPE.EMPTY;
	}

	protected JsonValues(Object value) {
		this.type = TYPE.VALUE;
		this.value = value;
	}

	protected JsonValues(List<?> value) {
		this.type = TYPE.LIST;
		this.value = value;
	}

	// Used by the JSON path engine
	protected JsonValues(Object value, JsonPointer pointer) {
		this.type = TYPE.VALUE;
		this.value = value;
		if (pointer != null) {
			this.pointers = new ArrayList<>();
			pointers.add(pointer);
		}
	}

	@Override
	public int hashCode() {
		if (value != null) {
			return value.hashCode();
		}
		return 0;
	}

	@Override
	public boolean equals(Object v) {
		if (v instanceof JsonValues jv) {
			if (type == TYPE.EMPTY) {
				return jv.isEmpty();
			}
			if (type == jv.type) {
				if (value == null) {
					return jv.value == null;
				}
				return value.equals(jv.value);
			}
		}
		return false;
	}

	public JsonArray toJsonArray() {
		JsonArray a = JsonArray.create();
		switch (type) {
			case EMPTY -> {
			}
			case VALUE -> {
				a.add(value);
			}
			case LIST -> {
				a.addAll((List<?>) value);
			}
		}
		return a;
	}

	public Object[] toArray() {
		Object[] a = new Object[_size()];
		for (int i = 0; i < a.length; i++) {
			a[i] = _get(i);
		}
		return a;
	}

	
	//
	// Update Methods.
	// These are packages protected for internal use. They should only be used
	// during
	// the construction of the value, not afterwards
	//

	// The shared singletons must never be mutated: doing so silently changed the
	// meaning of JsonValues.of(null)/of() everywhere
	private void checkMutable() {
		if (this == EMPTY || this == NULL || this == ZERO) {
			throw new IllegalStateException("A shared JsonValues singleton cannot be modified");
		}
	}

	void clear() {
		checkMutable();
		this.type = TYPE.EMPTY;
		this.value = null;
		this.pointers = null;
	}

	@SuppressWarnings("unchecked")
	void _add(Object v) {
		//assert(!(v instanceof JsonValues));
		checkMutable();
		switch (type) {
			case EMPTY -> {
				type = TYPE.VALUE;
				value = v;
			}
			case VALUE -> {
				type = TYPE.LIST;
				List<Object> l = new ArrayList<>();
				l.add(value);
				l.add(v);
				value = l;
			}
			case LIST -> {
				((List<Object>) value).add(v);
			}
		}
		if (pointers != null) {
			pointers.add(JsonPointer.EMPTY);
		}
	}

	@SuppressWarnings({ "unchecked" })
	void _add(Object v, JsonPointer p) {
		//assert(!(v instanceof JsonValues));
		if (p == null) {
			p = JsonPointer.EMPTY;
		}
		if (pointers == null) {
			pointers = new ArrayList<>();
			if (getType() != TYPE.EMPTY) {
				int sz = _size();
				for (int i = 0; i < sz; i++) {
					pointers.add(JsonPointer.EMPTY);
				}
			}
		}
		switch (getType()) {
			case EMPTY -> {
				type = TYPE.VALUE;
				value = v;
			}
			case VALUE -> {
				type = TYPE.LIST;
				List<Object> l = new ArrayList<>();
				l.add(value);
				l.add(v);
				value = l;
			}
			case LIST -> {
				((List<Object>) value).add(v);
			}
		}
		pointers.add(p);
	}
	
	void _addCollection(Collection<Object> c) {
		if (c != null && !c.isEmpty()) {
			for (Object v : c) {
				_add(v);
			}
		}
	}

	void _addJsonValues(JsonValues value) {
		if (value != null && !value.isEmpty()) {
			int sz = value._size();
			if (value.pointers != null) {
				for (int i = 0; i < sz; i++) {
					_add(value._get(i), value.getPointer(i));
				}
			} else {
				for (int i = 0; i < sz; i++) {
					_add(value._get(i));
				}
			}
		}
	}

	void add(Object value) {
		if(value instanceof JsonValues v) {
			_addJsonValues(v);
		} else {
			_add(value);
		}
	}
	
	

	//
	// Convert to a string
	//

	@Override
	public String toString() {
		return stringify(false);
	}

	public String stringify() {
		return stringify(true);
	}

	public String stringify(boolean compact) {
		switch (type) {
			case EMPTY -> {
				return "";
			}
			case VALUE -> {
				return JsonFactory.get().stringify(value,compact);
			}
			case LIST -> {
				return JsonFactory.get().stringify(toJsonArray(),compact);
			}
		}
		throw new IllegalStateException();
	}

	//
	// Check types
	//

	public JsonValues.TYPE getType() {
		return type;
	}

	public boolean isEmpty() {
		return type == TYPE.EMPTY;
	}

	public boolean isValue() {
		return type == TYPE.VALUE;
	}

	public boolean isList() {
		return type == TYPE.LIST;
	}


	//
	// Access to the raw values
	// 

	public int _size() {
		switch (type) {
			case EMPTY:
				return 0;
			case VALUE:
				return 1;
			case LIST:
				return ((List<?>) value).size();
			}
		throw new IllegalStateException();
	}	

	@SuppressWarnings("incomplete-switch")
	public Object _get(int index) {
		switch (type) {
			case VALUE -> {
				if (index == 0)
					return value;
			}
			case LIST -> {
				List<?> l = (List<?>) value;
				if (index >= 0 && index < l.size())
					return l.get(index);
			}
		}
		throw new IllegalArgumentException(StringFormat.format("Invalid index {0}, result size={1}", index, _size()));
	}
	@SuppressWarnings("unchecked")
	public Iterator<Object> rawIterator() {
		switch (type) {
			case EMPTY:
				return Iterators.empty();
			case VALUE:
				return Iterators.single(value);
			case LIST:
				return Iterators.readOnlyList((List<Object>) value);
		}
		throw new IllegalStateException();
	}

	@Override
	public void forEach(Consumer<? super JsonValues> action) {
		switch (type) {
			case EMPTY -> {
			}
			case VALUE -> {
				action.accept(this);
			}
			case LIST -> {
				@SuppressWarnings("unchecked")
				List<Object> list = (List<Object>) value;
				int count = list.size();
				for(int i=0; i<count; i++) {
					// Keep each value's pointer, so filter()/remove() results still have them
					if (pointers != null && i < pointers.size()) {
						action.accept(new JsonValues(list.get(i), pointers.get(i)));
					} else {
						action.accept(JsonValues.of(list.get(i)));
					}
				}
			}
		}
	}
	public void rawForEach(Consumer<Object> action) {
		switch (type) {
			case EMPTY -> {
			}
			case VALUE -> {
				action.accept(value);
			}
			case LIST -> {
				@SuppressWarnings("unchecked")
				List<Object> list = (List<Object>) value;
				int count = list.size();
				for(int i=0; i<count; i++) {
					action.accept(list.get(i));
				}
			}
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public Iterator<JsonValues> iterator() {
		switch (type) {
			case EMPTY -> {
				return Iterators.empty();
			}
			case VALUE -> {
				return Iterators.single(this);
			}
			case LIST -> {
				List<Object> list = (List<Object>) value;
				return new Iterator<JsonValues>() {
					private int current;
					@Override
					public boolean hasNext() {
						return current<list.size();
					}
					@Override
					public JsonValues next() {
						if (current<list.size()) {
							return JsonValues.of(list.get(current++));
						}
						throw new NoSuchElementException();
					}
					@Override
					public void remove() {
						throw new UnsupportedOperationException();
					}
				};
			}
		}
		throw new IllegalStateException();
	}
	
	@SuppressWarnings("unchecked")
	private JsonValues _getValue(int index) {
		switch (type) {
			case EMPTY, VALUE -> {
				return this;
			}
			case LIST -> {
				List<Object> list = (List<Object>) value;
				return JsonValues.of(list.get(index));
			}
		}
		throw new IllegalStateException();
	}


	//
	// Check for single value type
	//
	
	public boolean isNull() {
		return type == TYPE.VALUE && value == null;
	}

	public boolean isBoolean() {
		return type == TYPE.VALUE && (value instanceof Boolean);
	}

	public boolean isString() {
		return type == TYPE.VALUE && (value instanceof String);
	}

	public boolean isNumber() {
		return type == TYPE.VALUE && (value instanceof Number);
	}

	public boolean isContainer() {
		return type == TYPE.VALUE && (value instanceof JsonContainer);
	}

	public boolean isObject() {
		return type == TYPE.VALUE && (value instanceof JsonObject);
	}

	public boolean isArray() {
		return type == TYPE.VALUE && (value instanceof JsonArray);
	}

	private JsonException notSingleValue() {
		return new JsonException(null, "Value is not a single value, {0}", value);
	}

	//
	// Access to the ppinters
	//

	public JsonPointer getPointer(int index) {
		if (index >= 0 && index < _size()) {
			if (pointers != null) {
				return pointers.get(index);
			}
			return JsonPointer.EMPTY;
		}
		throw new IllegalArgumentException(StringFormat.format("Invalid index {0}, result size={1}", index, _size()));
	}

	public JsonPointer getPointer() {
		return getPointer(0);
	}

	public List<JsonPointer> getPointers() {
		return pointers;
	}


	
	//
	// Container Accessors
	//

	@SuppressWarnings("incomplete-switch")
	public JsonValues firstValue() {
		switch (type) {
			case VALUE -> {
				if (value instanceof JsonContainer c) {
					Object v = c.firstValueOrDefault(this); // We use this as anything else, just a marker!
					if(v!=this) {
						return of(v);
					}
				}
			}
			case LIST -> {
				JsonValues r = null;
				List<?> l = (List<?>)value;
				int sz = l.size();
				for (int i = 0; i < sz; i++) {
					Object value = l.get(i);
					if (value instanceof JsonContainer c) {
						Object v = c.firstValueOrDefault(this); // We use this as anything else, just a marker!
						if(v!=this) {
							if(r==null) {
								r = new JsonValues();
							}
							r._add(v);
						}
					}
				}
				return r!=null ? r : EMPTY;
			}
		}
		return EMPTY;
	}
	@SuppressWarnings("incomplete-switch")
	public JsonValues lastValue() {
		switch (type) {
			case VALUE -> {
				if (value instanceof JsonContainer c) {
					Object v = c.lastValueOrDefault(this); // We use this as anything else, just a marker!
					if(v!=this) {
						return of(v);
					}
				}
			}
			case LIST -> {
				JsonValues r = null;
				List<?> l = (List<?>)value;
				int sz = l.size();
				for (int i = 0; i < sz; i++) {
					Object value = l.get(i);
					if (value instanceof JsonContainer c) {
						Object v = c.lastValueOrDefault(this); // We use this as anything else, just a marker!
						if(v!=this) {
							if(r==null) {
								r = new JsonValues();
							}
							r._add(v);
						}
					}
				}
				return r!=null ? r : EMPTY;
			}
		}
		return EMPTY;
	}

	@SuppressWarnings("incomplete-switch")
	public JsonValues get(String name) {
		switch (type) {
			case VALUE -> {
				if (value instanceof JsonObject c) {
					if (c.containsKey(name)) {
						return of(c.get(name));
					}
				}
			}
			case LIST -> {
				JsonValues r = null;
				List<?> l = (List<?>)value;
				int sz = l.size();
				for (int i = 0; i < sz; i++) {
					Object value = l.get(i);
					if (value instanceof JsonObject c) {
						if (c.containsKey(name)) {
							if(r==null) {
								r = new JsonValues();
							}
							r._add(c.get(name));
						}
					}
				}
				return r!=null ? r : EMPTY;
			}
		}
		return EMPTY;
	}
	public JsonValues get(String... keys) {
		JsonValues r = new JsonValues();
		int sz = _size();
		for (int i = 0; i < sz; i++) {
			Object value = _get(i);
			if (value instanceof JsonObject o) {
				for (int k = 0; k < keys.length; k++) {
					String key = keys[k];
					if (o.containsKey(key)) {
						r._add(o.get(key));
					}
				}
			}
		}
		return r;
	}
	public JsonValues getAndFlat(String name) {
		return get(name).flat();
	}
	public JsonValues getAndFlat(String... names) {
		return get(names).flat();
	}

	@SuppressWarnings("incomplete-switch")
	public JsonValues get(int index) {
		switch (type) {
			case VALUE -> {
				if (value instanceof JsonArray c) {
					if(c.hasAt(index)) {
						return of(c.at(index));
					}
				}
			}
			case LIST -> {
				JsonValues r = null;
				List<?> l = (List<?>)value;
				int sz = l.size();
				for (int i = 0; i < sz; i++) {
					Object value = l.get(i);
					if (value instanceof JsonArray c) {
						if (c.hasAt(index)) { // negative indexes count from the end, as for a single array
							if(r==null) {
								r = new JsonValues();
							}
							r._add(c.at(index));
						}
					}
				}
				return r!=null ? r : EMPTY;
			}
		}
		return EMPTY;
	}
	public JsonValues get(int... indexes) {
		JsonValues r = new JsonValues();
		int sz = _size();
		for (int i = 0; i < sz; i++) {
			Object value = _get(i);
			if (value instanceof JsonArray a) {
				for (int k = 0; k < indexes.length; k++) {
					int idx = indexes[k];
					if(a.hasAt(idx)) {
						r._add(a.at(idx));
					}
				}
			}
		}
		return r;
	}
	public JsonValues getAndFlat(int index) {
		return get(index).flat();
	}
	public JsonValues getAndFlat(int... indexes) {
		return get(indexes).flat();
	}

	
	//
	// Apply path to the value
	//

	// The pointer of the value at index i, or its index in the list if there are no pointers
	private JsonPointer basePointer(int i) {
		if (pointers != null && i < pointers.size()) {
			return pointers.get(i);
		}
		return type == TYPE.LIST ? JsonPointer.EMPTY.getChild(i) : JsonPointer.EMPTY;
	}

	public JsonValues path(String path) {
		return path(JsonPathFactory.get().getJsonPath(path), false);
	}

	public JsonValues path(String path, boolean pointer) {
		return path(JsonPathFactory.get().getJsonPath(path), pointer);
	}

	public JsonValues path(JsonPath path) {
		return path(path, false);
	}

	public JsonValues path(JsonPath path, boolean pointer) {
		switch (type) {
			case EMPTY -> {
				return this;
			}
			case VALUE -> {
				return path.read(value(), pointer ? basePointer(0) : null);
			}
			case LIST -> {
				// The pointers are relative to each value's own pointer, or to its index
				// in the list, so values read from different elements stay distinct
				JsonValues r = new JsonValues();
				int sz = _size();
				for (int i = 0; i < sz; i++) {
					r._addJsonValues(path.read(_get(i), pointer ? basePointer(i) : null));
				}
				return r;
			}
		}
		throw new IllegalStateException();
	}

	
	//
	// Get the list of keys available in the current objects, if any
	// Can be used to browse the properties and get access the their values using get()
	//
	public Set<String> keySet() {
		switch (type) {
			case EMPTY -> {
				return Collections.emptySet();
			}
			case VALUE -> {
				if(value instanceof JsonObject o) {
					return o.keySet();
				}
				return Collections.emptySet();
			}
			case LIST -> {
				Set<String> keys = new HashSet<>();
				int sz = _size();
				for (int i = 0; i < sz; i++) {
					Object o = _get(i);
					if(o instanceof JsonObject jo) {
						keys.addAll(jo.keySet());
					}
				}
				return keys;
			}
		}
		throw new IllegalStateException();
	}
	
	
	/////////////////////////////////////////////////////////////////////////////////////////
	//
	// JsonPath/Stream like functions
	//
	/////////////////////////////////////////////////////////////////////////////////////////

	public JsonValues flat() {
		JsonValues r = new JsonValues();
		int sz = _size();
		for (int i = 0; i < sz; i++) {
			Object value = _get(i);
			if (value instanceof JsonContainer c) {
				r._addCollection(c.values());
			} else {
				r._add(value);
			}
		}
		return r;
	}
	/**
	 * Like {@link java.util.stream.Stream#flatMap}: each value is mapped, and the result
	 * of the mapper is flattened (the values of a JsonValues, the items of a JsonArray).
	 */
	public JsonValues flatMap(Function<JsonValues, Object> mapper) {
		JsonValues r = new JsonValues();
		int sz = _size();
		for (int i = 0; i < sz; i++) {
			Object res = mapper.apply(of(_get(i)));
			if (res instanceof JsonValues v) {
				r._addJsonValues(v);
			} else if (res instanceof JsonArray a) {
				int n = a.size();
				for (int k = 0; k < n; k++) {
					r._add(a.get(k));
				}
			} else {
				r._add(res);
			}
		}
		return r;
	}

	
	// An empty key in find is equivalent to $..* in json path
	
	public JsonValues find(String key) {
    	return find(key,true);
    }
	public JsonValues find(String key, boolean deep) {
		JsonValues r = new JsonValues();
		rawForEach( (v) -> {
			_find(r,v,key,deep);
		});
    	return r;
	}
	public void findAndSet(String key, Object v) {
		// The containers are the wrapped values, not this wrapper
		rawForEach( (value) -> {
			_findAndSet(value,key,v);
		});
	}
	
    private static void _find(JsonValues values, Object value, String key, boolean deep) {
    	if(value instanceof JsonObject o) {
    		if(key==null) {
        		for(Object v: o.values()) {
        			values._add(v);
        		}
    		} else if(o.has(key)) {
    			values._add(o.get(key));
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
        			values._add(a.get(i));
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

	public JsonValues find(int index) {
		return find(index, true);
	}

	public JsonValues find(int index, boolean deep) {
		JsonValues r = new JsonValues();
		// Iterating "this" yields JsonValues wrappers, never the raw containers
		rawForEach( (v) -> {
			_find(r, v, index, deep);
		});
		return r;
	}

	public void findAndSet(int index, Object v) {
		rawForEach( (value) -> {
			_findAndSet(value, index, v);
		});
	}

	private static void _find(JsonValues values, Object value, int index, boolean deep) {
		if (value instanceof JsonObject o) {
			for (Object v : o.values()) {
				_find(values, v, index, deep);
			}
		} else if (value instanceof JsonArray a) {
			int idx = index<0 ? index+a.size() : index; // negative: from the end, as in JSON Path
			if (a.has(idx)) {
				values._add(a.get(idx));
			}
			int count = a.size();
			for (int i = 0; i < count; i++) {
				if (deep || i != idx) {
					_find(values, a.get(i), index, deep);
				}
			}
		}
	}

	private static void _findAndSet(Object value, int index, Object v) {
		if (value instanceof JsonObject o) {
			for (Object val : o.values()) {
				_findAndSet(val, index, v);
			}
		} else if (value instanceof JsonArray a) {
			int idx = index<0 ? index+a.size() : index; // negative: from the end, as in JSON Path
			if (a.has(idx)) {
				a.set(idx, v);
			}
			int sz = a.size();
			for (int i = 0; i < sz; i++) {
				if (i != idx) {
					_findAndSet(a.get(i), index, v);
				}
			}
		}
	}

	/**
	 * The single value at the given position (a negative position counts from the end),
	 * or an empty result.
	 * Note that this is NOT JavaScript's slice(start), which returns all the values from
	 * start: use {@code slice(start, Integer.MAX_VALUE)} for that.
	 */
	public JsonValues slice(int start) {
		int s = start < 0 ? start + _size() : start;
		return slice(s, s+1, 1);
	}
	
	public JsonValues slice(int start, int end) {
		return slice(start, end, 1);
	}

	public JsonValues slice(Integer start, Integer end) {
		return slice(start, end, 1);
	}

	public JsonValues slice(Integer start, Integer end, int step) {
		// A negative step walks backwards, so its defaults are the other way round
		int s = start != null ? start : (step < 0 ? _size()-1 : 0);
		int e = end != null ? end : (step < 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE);
		return slice(s, e, step);
	}

	public JsonValues slice(int start, int end, int step) {
		if (step == 0) {
			throw new JsonException(null, "Slice step cannot be 0");
		}
		int size = _size();
		JsonValues a = new JsonValues();
		int nStart = start < 0 ? start + size : start;
		int nEnd = end < 0 ? end + size : end;
		if (step > 0) {
			int st = Math.max(0, Math.min(size, nStart));
			int ed = Math.max(0, Math.min(size, nEnd));
			// long: i+step must not overflow for a large step
			for (long i = st; i < ed; i += step) {
				a._add(_get((int)i));
			}
		} else {
			int st = Math.max(-1, Math.min(size-1, nStart));
			int ed = Math.max(-1, Math.min(size, nEnd));
			for (long i = st; i > ed; i += step) {
				a._add(_get((int)i));
			}
		}
		return a;
	}
	
	// Key wrapper giving a value the JSON equality (JsonUtil.eq/hashCode)
	private static final class JsonKey {
		private final Object value;
		private final int hash;
		JsonKey(Object value) {
			this.value = value;
			this.hash = JsonUtil.hashCode(value);
		}
		@Override
		public int hashCode() {
			return hash;
		}
		@Override
		public boolean equals(Object o) {
			return o instanceof JsonKey k && JsonUtil.eq(value, k.value);
		}
	}

	// Similar to sort but works on a copy
	public JsonValues sorted() {
		return sorted(true);
	}

	public JsonValues sorted(boolean asc) {
		return rawSorted(asc ? JsonUtil.jsonComparator : JsonUtil.jsonComparatorDesc);
	}

	@SuppressWarnings("incomplete-switch")
	public JsonValues rawSorted(Comparator<Object> comp) {
		JsonValues r = new JsonValues();
		switch (type) {
			case VALUE -> {
				r._add(_get(0));
			}
			case LIST -> {
				r._addJsonValues(this);
				List<?> l = (List<?>) r.value;
				l.sort(comp);
			}
		}
		return r;
	}

	@SuppressWarnings("incomplete-switch")
	public JsonValues sorted(Comparator<JsonValues> comp) {
		JsonValues r = new JsonValues();
		switch (type) {
			case VALUE -> {
				r._add(_get(0));
			}
			case LIST -> {
				r._addJsonValues(this);
				List<?> l = (List<?>) r.value;
				l.sort( (v1,v2) -> comp.compare(of(v1),of(v2)) );
			}
		}
		return r;
	}

	public JsonValues skip(int skip) {
		int sz = _size();
		if (skip > 0 && sz > 0) {
			JsonValues a = new JsonValues();
			for (int i = skip; i < sz; i++) {
				a._add(_get(i));
			}
			return a;
		}
		return this;
	}

	public JsonValues limit(int limit) {
		if (limit < 0) {
			// Same contract as Stream.limit()
			throw new IllegalArgumentException("Negative limit: "+limit);
		}
		int sz = _size();
		if (sz > limit) {
			JsonValues a = new JsonValues();
			int last = Math.min(limit, sz);
			for (int i = 0; i < last; i++) {
				a._add(_get(i));
			}
			return a;
		}
		return this;
	}

	public JsonValues skipLimit(int skip, int limit) {
		int sz = _size();
		int first = Math.max(0, skip);
		int last = (int)Math.min((long)first + limit, sz); // first+limit overflows for Integer.MAX_VALUE
		if (first > 0 || last < sz) {
			JsonValues a = new JsonValues();
			for (int i = first; i < last; i++) {
				a._add(_get(i));
			}
			return a;
		}
		return this;
	}

	public JsonValues filter(Predicate<JsonValues> predicate) {
		JsonValues r = new JsonValues();
		forEach( (value) -> {
			if (predicate.test(value)) {
				r._addJsonValues(value);
			}
		});
		return r;
	}

	public JsonValues remove(Predicate<JsonValues> predicate) {
		JsonValues r = new JsonValues();
		forEach( (value) -> {
			if (!predicate.test(value)) {
				r._addJsonValues(value);
			}
		});
		return r;
	}

	public JsonValues takeWhile(Predicate<JsonValues> predicate) {
		JsonValues r = new JsonValues();
		int sz = _size();
		for (int i = 0; i < sz; i++) {
			JsonValues value = _getValue(i);
			if (predicate.test(value)) {
				r._addJsonValues(value);
			} else {
				break;
			}
		}
		return r;
	}

	public JsonValues dropWhile(Predicate<JsonValues> predicate) {
		JsonValues r = new JsonValues();
		int sz = _size();
		boolean drop = true;
		for (int i = 0; i < sz; i++) {
			JsonValues value = _getValue(i);
			if (drop) {
				if (predicate.test(value)) {
					continue;
				}
				drop = false;
			}
			r._addJsonValues(value);
		}
		return r;
	}

	public JsonValues map(Function<JsonValues, Object> mapper) {
		JsonValues r = new JsonValues();
		int sz = _size();
		for (int i = 0; i < sz; i++) {
			JsonValues value = _getValue(i);
			Object res = mapper.apply(value);
			r.add(res);
		}
		return r;
	}

	public JsonValues peek(Consumer<JsonValues> action) {
		forEach(action);
		return this;
	}

	public <R> R process(Function<JsonValues, R> processsor) {
		return processsor.apply(this);
	}

	public JsonValues distinct() {
		JsonValues a = new JsonValues();
		int sz = _size();
		if (sz == 1) {
			a._add(_get(0));
		} else if (sz > 1) {
			// Distinct in the JSON sense (1, 1L and 1.0 are the same value), keeping the order
			Set<JsonKey> seen = new HashSet<>();
			for (int i = 0; i < sz; i++) {
				Object value = _get(i);
				if (seen.add(new JsonKey(value))) {
					a._add(value);
				}
			}
		}
		return a;
	}

	public JsonValues min() {
		int sz = _size();
		if (sz > 0) {
			Object v = _get(0);
			for (int i = 1; i < sz; i++) {
				Object value = _get(i);
				if (JsonUtil.compare(value, v) < 0) {
					v = value;
				}
			}
			return of(v);
		}
		return EMPTY;
	}

	public JsonValues min(Comparator<JsonValues> comp) {
		int sz = _size();
		if (sz > 0) {
			JsonValues v = _getValue(0);
			for (int i = 1; i < sz; i++) {
				JsonValues value = _getValue(i);
				if (comp.compare(value, v) < 0) {
					v = value;
				}
			}
			return v;
		}
		return EMPTY;
	}

	public JsonValues max() {
		int sz = _size();
		if (sz > 0) {
			Object v = _get(0);
			for (int i = 1; i < sz; i++) {
				Object value = _get(i);
				if (JsonUtil.compare(value, v) > 0) {
					v = value;
				}
			}
			return of(v);
		}
		return EMPTY;
	}

	public JsonValues max(Comparator<JsonValues> comp) {
		int sz = _size();
		if (sz > 0) {
			JsonValues v = _getValue(0);
			for (int i = 1; i < sz; i++) {
				JsonValues value = _getValue(i);
				if (comp.compare(value, v) > 0) {
					v = value;
				}
			}
			return v;
		}
		return EMPTY;
	}

	public boolean anyMatch(Predicate<JsonValues> predicate) {
		int sz = _size();
		for (int i = 0; i < sz; i++) {
			JsonValues value = _getValue(i);
			if (predicate.test(value)) {
				return true;
			}
		}
		return false;
	}

	public boolean allMatch(Predicate<JsonValues> predicate) {
		int sz = _size();
		for (int i = 0; i < sz; i++) {
			JsonValues value = _getValue(i);
			if (!predicate.test(value)) {
				return false;
			}
		}
		return true;
	}

	public boolean noneMatch(Predicate<JsonValues> predicate) {
		int sz = _size();
		for (int i = 0; i < sz; i++) {
			JsonValues value = _getValue(i);
			if (predicate.test(value)) {
				return false;
			}
		}
		return true;
	}

	//
	// Functions that return a value
	//
	
	public <U> U rawReduce(Reducer<U, Object> reducer) {
		return rawReduce(reducer.initialValue(), reducer.reducer());
	}
	public <U> U rawReduce(U identity, BiFunction<U, Object, U> accumulator) {
		int sz = _size();
		for (int i = 0; i < sz; i++) {
			Object value = _get(i);
			identity = accumulator.apply(identity, value);
		}
		return identity;
	}

	public <U> U reduce(Reducer<U, JsonValues> reducer) {
		return reduce(reducer.initialValue(), reducer.reducer());
	}
	public <U> U reduce(U identity, BiFunction<U, JsonValues, U> accumulator) {
		int sz = _size();
		for (int i = 0; i < sz; i++) {
			JsonValues value = _getValue(i);
			identity = accumulator.apply(identity, value);
		}
		return identity;
	}
	

	/////////////////////////////////////////////////////////////////////////////////////////
	// Access to single values
	/////////////////////////////////////////////////////////////////////////////////////////

	public Object value() {
		if (type == TYPE.VALUE) {
			return value;
		}
		throw notSingleValue();
	}

	public boolean booleanValue() {
		if (type == TYPE.VALUE) {
			return JsonUtil.checkBoolean(value);
		}
		throw notSingleValue();
	}

	public boolean booleanValue(boolean def) {
		if (type == TYPE.EMPTY) {
			return def;
		}
		return booleanValue();
	}

	public byte byteValue() {
		if (type == TYPE.VALUE) {
			return JsonUtil.checkByte(value);
		}
		throw notSingleValue();
	}

	public byte byteValue(byte def) {
		if (type == TYPE.EMPTY) {
			return def;
		}
		return byteValue();
	}

	public short shortValue() {
		if (type == TYPE.VALUE) {
			return JsonUtil.checkShort(value);
		}
		throw notSingleValue();
	}

	public short shortValue(short def) {
		if (type == TYPE.EMPTY) {
			return def;
		}
		return shortValue();
	}

	public int intValue() {
		if (type == TYPE.VALUE) {
			return JsonUtil.checkInt(value);
		}
		throw notSingleValue();
	}

	public int intValue(int def) {
		if (type == TYPE.EMPTY) {
			return def;
		}
		return intValue();
	}

	public long longValue() {
		if (type == TYPE.VALUE) {
			return JsonUtil.checkLong(value);
		}
		throw notSingleValue();
	}

	public long longValue(long def) {
		if (type == TYPE.EMPTY) {
			return def;
		}
		return longValue();
	}

	public float floatValue() {
		if (type == TYPE.VALUE) {
			return JsonUtil.checkFloat(value);
		}
		throw notSingleValue();
	}

	public float floatValue(float def) {
		if (type == TYPE.EMPTY) {
			return def;
		}
		return floatValue();
	}

	public double doubleValue() {
		if (type == TYPE.VALUE) {
			return JsonUtil.checkDouble(value);
		}
		throw notSingleValue();
	}

	public double doubleValue(double def) {
		if (type == TYPE.EMPTY) {
			return def;
		}
		return doubleValue();
	}

	public BigInteger bigIntegerValue() {
		if (type == TYPE.VALUE) {
			return JsonUtil.checkBigInteger(value);
		}
		throw notSingleValue();
	}

	public BigInteger bigIntegerValue(BigInteger def) {
		if (type == TYPE.EMPTY) {
			return def;
		}
		return bigIntegerValue();
	}

	public BigDecimal bigDecimalValue() {
		if (type == TYPE.VALUE) {
			return JsonUtil.checkBigDecimal(value);
		}
		throw notSingleValue();
	}

	public BigDecimal bigDecimalValue(BigDecimal def) {
		if (type == TYPE.EMPTY) {
			return def;
		}
		return bigDecimalValue();
	}

	public Number numberValue() {
		if (type == TYPE.VALUE) {
			return JsonUtil.checkNumber(value);
		}
		throw notSingleValue();
	}

	public Number numberValue(Number def) {
		if (type == TYPE.EMPTY) {
			return def;
		}
		return numberValue();
	}

	public String stringValue() {
		if (type == TYPE.VALUE) {
			return JsonUtil.checkString(value);
		}
		throw notSingleValue();
	}

	public String stringValue(String def) {
		if (type == TYPE.EMPTY) {
			return def;
		}
		return stringValue();
	}

	public JsonObject objectValue() {
		if (type == TYPE.VALUE) {
			return JsonUtil.checkObject(value);
		}
		throw notSingleValue();
	}

	public JsonObject objectValue(JsonObject def) {
		if (type == TYPE.EMPTY) {
			return def;
		}
		return objectValue();
	}

	public JsonArray arrayValue() {
		if (type == TYPE.VALUE) {
			return JsonUtil.checkArray(value);
		}
		throw notSingleValue();
	}

	public JsonArray arrayValue(JsonArray def) {
		if (type == TYPE.EMPTY) {
			return def;
		}
		return arrayValue();
	}

	public LocalDate localDateValue() {
		if (type == TYPE.VALUE) {
			return JsonUtil.checkLocalDate(value);
		}
		throw notSingleValue();
	}

	public LocalDate localDateValue(LocalDate def) {
		if (type == TYPE.EMPTY) {
			return def;
		}
		return localDateValue();
	}

	public LocalTime localTimeValue() {
		if (type == TYPE.VALUE) {
			return JsonUtil.checkLocalTime(value);
		}
		throw notSingleValue();
	}

	public LocalTime localTimeValue(LocalTime def) {
		if (type == TYPE.EMPTY) {
			return def;
		}
		return localTimeValue();
	}

	public LocalDateTime localDateTimeValue() {
		if (type == TYPE.VALUE) {
			return JsonUtil.checkLocalDateTime(value);
		}
		throw notSingleValue();
	}

	public LocalDateTime localDateTimeValue(LocalDateTime def) {
		if (type == TYPE.EMPTY) {
			return def;
		}
		return localDateTimeValue();
	}

	public OffsetTime offsetTimeValue() {
		if (type == TYPE.VALUE) {
			return JsonUtil.checkOffsetTime(value);
		}
		throw notSingleValue();
	}

	public OffsetTime offsetTimeValue(OffsetTime def) {
		if (type == TYPE.EMPTY) {
			return def;
		}
		return offsetTimeValue();
	}

	public OffsetDateTime offsetDateTimeValue() {
		if (type == TYPE.VALUE) {
			return JsonUtil.checkOffsetDateTime(value);
		}
		throw notSingleValue();
	}

	public OffsetDateTime offsetDateTimeValue(OffsetDateTime def) {
		if (type == TYPE.EMPTY) {
			return def;
		}
		return offsetDateTimeValue();
	}

	public ZonedDateTime zonedDateTimeValue() {
		if (type == TYPE.VALUE) {
			return JsonUtil.checkZonedDateTime(value);
		}
		throw notSingleValue();
	}

	public ZonedDateTime zonedDateTimeValue(ZonedDateTime def) {
		if (type == TYPE.EMPTY) {
			return def;
		}
		return zonedDateTimeValue();
	}


	public int asInt() {
		return JsonUtil.asInt(value());
	}
	public int asInt(int defaultValue) {
		if (type == TYPE.EMPTY) {
			return defaultValue;
		}
		return JsonUtil.asInt(value(),defaultValue);
	}
	
	public long asLong() {
		return JsonUtil.asLong(value());
	}
	public long asLong(long defaultValue) {
		if (type == TYPE.EMPTY) {
			return defaultValue;
		}
		return JsonUtil.asLong(value(),defaultValue);
	}
	
	public double asDouble() {
		return JsonUtil.asDouble(value());
	}
	public double asDouble(double defaultValue) {
		if (type == TYPE.EMPTY) {
			return defaultValue;
		}
		return JsonUtil.asDouble(value(),defaultValue);
	}
	
	public BigInteger asBigInteger() {
		return JsonUtil.asBigInteger(value());
	}
	public BigInteger asBigInteger(BigInteger defaultValue) {
		if (type == TYPE.EMPTY) {
			return defaultValue;
		}
		return JsonUtil.asBigInteger(value(),defaultValue);
	}
	
	public BigDecimal asBigDecimal() {
		return JsonUtil.asBigDecimal(value());
	}
	public BigDecimal asBigDecimal(BigDecimal defaultValue) {
		if (type == TYPE.EMPTY) {
			return defaultValue;
		}
		return JsonUtil.asBigDecimal(value(),defaultValue);
	}
	
	public boolean asBoolean() {
		return JsonUtil.asBoolean(value());
	}
	public boolean asBoolean(boolean defaultValue) {
		if (type == TYPE.EMPTY) {
			return defaultValue;
		}
		return JsonUtil.asBoolean(value(),defaultValue);
	}
	
	public String asString() {
		return JsonUtil.asString(value());
	}
	public String asString(String defaultValue) {
		if (type == TYPE.EMPTY) {
			return defaultValue;
		}
		return JsonUtil.asString(value(),defaultValue);
	}

	
	
	//
	// Comparators
	//

	public static final Comparator<JsonValues> JsonComparator = new Comparator<JsonValues>() {
		@Override
		public int compare(JsonValues o1, JsonValues o2) {
			return JsonUtil.compare(o1.value(), o2.value());
		}
	};

	public static final Comparator<JsonValues> JsonComparatorDesc = new Comparator<JsonValues>() {
		@Override
		public int compare(JsonValues o1, JsonValues o2) {
			return -JsonUtil.compare(o1.value(), o2.value());
		}
	};

	public int compareTo(JsonValues v) {
		return JsonUtil.compare(this.value(), v.value());
	}

	//
	// Handling streams
	//
//	public Stream<JsonValue> streamValues() {
//		return Stream.of(this);
//	}
//	// EQV to stream().flatMap(JsonStream.flatten())
//	public Stream<JsonValue> streamFlat() {
//		if(isArray() || isObject()) {
//			return allValues().stream();
//		}
//		return Stream.of(this);
//	}

	//
	// Single value comparators
	//

	public boolean eq(@NonNull String other) {
		if (type == TYPE.VALUE && value instanceof String v) {
			return v.equals(other);
		}
		return false;
	}

	public boolean eq(boolean other) {
		if (type == TYPE.VALUE && value instanceof Boolean v) {
			return v.equals(other);
		}
		return false;
	}

	public boolean eq(byte other) {
		return testNumber(other, EQ);
	}

	public boolean eq(short other) {
		return testNumber(other, EQ);
	}

	public boolean eq(int other) {
		return testNumber(other, EQ);
	}

	public boolean eq(long other) {
		return testNumber(other, EQ);
	}

	public boolean eq(float other) {
		return testNumber(other, EQ);
	}

	public boolean eq(double other) {
		return testNumber(other, EQ);
	}

	public boolean eq(BigInteger other) {
		return testNumber(other, EQ);
	}

	public boolean eq(BigDecimal other) {
		return testNumber(other, EQ);
	}

	public boolean eq(Object other) {
		if (other instanceof JsonValues ov) {
			if(ov.type != TYPE.VALUE) {
				return false;
			}
			other = ov.value;
		}
		if (type != TYPE.VALUE) {
			return false;
		}
		return JsonUtil.eq(value, other);
	}
	
	// Numbers compare exactly: 1.9 is not 1, and 1.5 is greater than 1 (no truncation).
	// NaN is not comparable: every comparison involving it is false (except ne)
	private static boolean comparable(Number v, Number other) {
		return other!=null && !isNaN(v) && !isNaN(other);
	}
	private static boolean isNaN(Number n) {
		return (n instanceof Double d && d.isNaN()) || (n instanceof Float f && f.isNaN());
	}

	// Comparison results
	private static final IntPredicate EQ = c -> c==0;
	private static final IntPredicate LT = c -> c<0;
	private static final IntPredicate LTE = c -> c<=0;
	private static final IntPredicate GT = c -> c>0;
	private static final IntPredicate GTE = c -> c>=0;
	
	/**
	 * Compares the single numeric value with a number, false if the value is not a number
	 * or one of them is NaN.
	 */
	private boolean testNumber(Number other, IntPredicate test) {
		if (type == TYPE.VALUE && value instanceof Number v) {
			return comparable(v, other) && test.test(JsonUtil.compareNumber(v, other));
		}
		return false;
	}
	/**
	 * Compares the single string value with a string, by Unicode code points (like the
	 * JSONPath filters, RFC 9535), false if the value is not a string.
	 */
	private boolean testString(String other, IntPredicate test) {
		if (type == TYPE.VALUE && value instanceof String v && other!=null) {
			return test.test(ExprBinaryOp.compareCodePoints(v, other));
		}
		return false;
	}

	public boolean ne(@NonNull String other) {
		return !eq(other);
	}

	public boolean ne(boolean other) {
		return !eq(other);
	}

	public boolean ne(byte other) {
		return !eq(other);
	}

	public boolean ne(short other) {
		return !eq(other);
	}

	public boolean ne(int other) {
		return !eq(other);
	}

	public boolean ne(long other) {
		return !eq(other);
	}

	public boolean ne(float other) {
		return !eq(other);
	}

	public boolean ne(double other) {
		return !eq(other);
	}

	public boolean ne(BigInteger other) {
		return !eq(other);
	}

	public boolean ne(BigDecimal other) {
		return !eq(other);
	}

	public boolean lt(String other) {
		return testString(other, LT);
	}

	public boolean lt(byte other) {
		return testNumber(other, LT);
	}

	public boolean lt(short other) {
		return testNumber(other, LT);
	}

	public boolean lt(int other) {
		return testNumber(other, LT);
	}

	public boolean lt(long other) {
		return testNumber(other, LT);
	}

	public boolean lt(float other) {
		return testNumber(other, LT);
	}

	public boolean lt(double other) {
		return testNumber(other, LT);
	}

	public boolean lt(BigInteger other) {
		return testNumber(other, LT);
	}

	public boolean lt(BigDecimal other) {
		return testNumber(other, LT);
	}

	public boolean lte(String other) {
		return testString(other, LTE);
	}

	public boolean lte(byte other) {
		return testNumber(other, LTE);
	}

	public boolean lte(short other) {
		return testNumber(other, LTE);
	}

	public boolean lte(int other) {
		return testNumber(other, LTE);
	}

	public boolean lte(long other) {
		return testNumber(other, LTE);
	}

	public boolean lte(float other) {
		return testNumber(other, LTE);
	}

	public boolean lte(double other) {
		return testNumber(other, LTE);
	}

	public boolean lte(BigInteger other) {
		return testNumber(other, LTE);
	}

	public boolean lte(BigDecimal other) {
		return testNumber(other, LTE);
	}

	public boolean gt(String other) {
		return testString(other, GT);
	}

	public boolean gt(byte other) {
		return testNumber(other, GT);
	}

	public boolean gt(short other) {
		return testNumber(other, GT);
	}

	public boolean gt(int other) {
		return testNumber(other, GT);
	}

	public boolean gt(long other) {
		return testNumber(other, GT);
	}

	public boolean gt(float other) {
		return testNumber(other, GT);
	}

	public boolean gt(double other) {
		return testNumber(other, GT);
	}

	public boolean gt(BigInteger other) {
		return testNumber(other, GT);
	}

	public boolean gt(BigDecimal other) {
		return testNumber(other, GT);
	}

	public boolean gte(String other) {
		return testString(other, GTE);
	}

	public boolean gte(byte other) {
		return testNumber(other, GTE);
	}

	public boolean gte(short other) {
		return testNumber(other, GTE);
	}

	public boolean gte(int other) {
		return testNumber(other, GTE);
	}

	public boolean gte(long other) {
		return testNumber(other, GTE);
	}

	public boolean gte(float other) {
		return testNumber(other, GTE);
	}

	public boolean gte(double other) {
		return testNumber(other, GTE);
	}

	public boolean gte(BigInteger other) {
		return testNumber(other, GTE);
	}

	public boolean gte(BigDecimal other) {
		return testNumber(other, GTE);
	}

	public boolean matches(String regExp) {
		if (type == TYPE.VALUE && value instanceof String v) {
			return v.matches(regExp);
		}
		return false;
	}

	// Generic in method
	public boolean in(Object... list) {
		if (type != TYPE.VALUE) {
			return false;
		}
		for (int i = 0; i < list.length; i++) {
			Object other = list[i];
			if (JsonUtil.eq(value, other)) {
				return true;
			}
		}
		return false;
	}

	// Optimized in methods for common cases, String & integers
	public boolean in(Integer... list) {
		if (type == TYPE.VALUE && value instanceof Number v) {
			// By value: 1.9 is not in (1), and a null element is skipped
			for (int i = 0; i < list.length; i++) {
				if (list[i]!=null && JsonUtil.eqNumber(v, list[i])) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean in(String... list) {
		if (type == TYPE.VALUE && value instanceof String v) {
			for (int i = 0; i < list.length; i++) {
				if (v.equals(list[i])) {
					return true;
				}
			}
		}
		return false;
	}
	
	

	/////////////////////////////////////////////////////////////////////////////////////////
	//
	// JsonObject methods
	//
	/////////////////////////////////////////////////////////////////////////////////////////
	
	public boolean has(String key) {
		if (type == TYPE.VALUE && value instanceof JsonObject o) {
			return o.has(key);
		}
		return false;
	}

	public boolean getBoolean(String key) {
		return objectValue().getBoolean(key);
	}
	public Number getNumber(String key) {
		return objectValue().getNumber(key);
	}
	public byte getByte(String key) {
		return objectValue().getByte(key);
	}
	public short getShort(String key) {
		return objectValue().getShort(key);
	}
	public int getInt(String key) {
		return objectValue().getInt(key);
	}
	public long getLong(String key) {
		return objectValue().getLong(key);
	}
	public float getFloat(String key) {
		return objectValue().getFloat(key);
	}
	public double getDouble(String key) {
		return objectValue().getDouble(key);
	}
	public BigInteger getBigInteger(String key) {
		return objectValue().getBigInteger(key);
	}
	public BigDecimal getBigDecimal(String key) {
		return objectValue().getBigDecimal(key);
	}
	public String getString(String key) {
		return objectValue().getString(key);
	}
	public JsonObject getObject(String key) {
		return objectValue().getObject(key);
	}
	public JsonArray getArray(String key) {
		return objectValue().getArray(key);
	}
	public LocalDate getLocalDate(String key) {
		return objectValue().getLocalDate(key);
	}
	public LocalTime getLocalTime(String key) {
		return objectValue().getLocalTime(key);
	}
	public LocalDateTime getLocalDateTime(String key) {
		return objectValue().getLocalDateTime(key);
	}
	public OffsetTime getOffsetTime(String key) {
		return objectValue().getOffsetTime(key);
	}
	public OffsetDateTime getOffsetDateTime(String key) {
		return objectValue().getOffsetDateTime(key);
	}
	public ZonedDateTime getZonedDateTime(String key) {
		return objectValue().getZonedDateTime(key);
	}
	
	public boolean getBoolean(String key, boolean defaultValue) {
		return objectValue().getBoolean(key, defaultValue);
	}
	public Number getNumber(String key, Number defaultValue) {
		return objectValue().getNumber(key, defaultValue);
	}
	public byte getByte(String key, byte defaultValue) {
		return objectValue().getByte(key, defaultValue);
	}
	public short getShort(String key, short defaultValue) {
		return objectValue().getShort(key, defaultValue);
	}
	public int getInt(String key, int defaultValue) {
		return objectValue().getInt(key, defaultValue);
	}
	public long getLong(String key, long defaultValue) {
		return objectValue().getLong(key, defaultValue);
	}
	public float getFloat(String key, float defaultValue) {
		return objectValue().getFloat(key, defaultValue);
	}
	public double getDouble(String key, double defaultValue) {
		return objectValue().getDouble(key, defaultValue);
	}
	public BigInteger getBigInteger(String key, BigInteger defaultValue) {
		return objectValue().getBigInteger(key, defaultValue);
	}
	public BigDecimal getBigDecimal(String key, BigDecimal defaultValue) {
		return objectValue().getBigDecimal(key, defaultValue);
	}
	public String getString(String key, String defaultValue) {
		return objectValue().getString(key, defaultValue);
	}
	public JsonObject getObject(String key, JsonObject defaultValue) {
		return objectValue().getObject(key, defaultValue);
	}
	public JsonArray getArray(String key, JsonArray defaultValue) {
		return objectValue().getArray(key, defaultValue);
	}
	public LocalDate getLocalDate(String key, LocalDate defaultValue) {
		return objectValue().getLocalDate(key, defaultValue);
	}
	public LocalTime getLocalTime(String key, LocalTime defaultValue) {
		return objectValue().getLocalTime(key, defaultValue);
	}
	public LocalDateTime getLocalDateTime(String key, LocalDateTime defaultValue) {
		return objectValue().getLocalDateTime(key, defaultValue);
	}
	public OffsetTime getOffsetTime(String key, OffsetTime defaultValue) {
		return objectValue().getOffsetTime(key, defaultValue);
	}
	public OffsetDateTime getOffsetDateTime(String key, OffsetDateTime defaultValue) {
		return objectValue().getOffsetDateTime(key, defaultValue);
	}
	public ZonedDateTime getZonedDateTime(String key, ZonedDateTime defaultValue) {
		return objectValue().getZonedDateTime(key, defaultValue);
	}
	
	

	
	/////////////////////////////////////////////////////////////////////////////////////////
	//
	// JsonArray methods
	//
	/////////////////////////////////////////////////////////////////////////////////////////
	
	public boolean has(int index) {
		if (type == TYPE.VALUE && value instanceof JsonArray a) {
			return a.has(index);
		}
		return false;
	}
	
	
	public boolean getBoolean(int index) {
		return arrayValue().getBoolean(index);
	}
	public Number getNumber(int index) {
		return arrayValue().getNumber(index);
	}
	public byte getByte(int index) {
		return arrayValue().getByte(index);
	}
	public short getShort(int index) {
		return arrayValue().getShort(index);
	}
	public int getInt(int index) {
		return arrayValue().getInt(index);
	}
	public long getLong(int index) {
		return arrayValue().getLong(index);
	}
	public float getFloat(int index) {
		return arrayValue().getFloat(index);
	}
	public double getDouble(int index) {
		return arrayValue().getDouble(index);
	}
	public BigInteger getBigInteger(int index) {
		return arrayValue().getBigInteger(index);
	}
	public BigDecimal getBigDecimal(int index) {
		return arrayValue().getBigDecimal(index);
	}
	public String getString(int index) {
		return arrayValue().getString(index);
	}
	public JsonObject getObject(int index) {
		return arrayValue().getObject(index);
	}
	public JsonArray getArray(int index) {
		return arrayValue().getArray(index);
	}
	public LocalDate getLocalDate(int index) {
		return arrayValue().getLocalDate(index);
	}
	public LocalTime getLocalTime(int index) {
		return arrayValue().getLocalTime(index);
	}
	public LocalDateTime getLocalDateTime(int index) {
		return arrayValue().getLocalDateTime(index);
	}
	public OffsetTime getOffsetTime(int index) {
		return arrayValue().getOffsetTime(index);
	}
	public OffsetDateTime getOffsetDateTime(int index) {
		return arrayValue().getOffsetDateTime(index);
	}
	public ZonedDateTime getZonedDateTime(int index) {
		return arrayValue().getZonedDateTime(index);
	}
	
	public boolean getBoolean(int index, boolean defaultValue) {
		return arrayValue().getBoolean(index, defaultValue);
	}
	public Number getNumber(int index, Number defaultValue) {
		return arrayValue().getNumber(index, defaultValue);
	}
	public byte getByte(int index, byte defaultValue) {
		return arrayValue().getByte(index, defaultValue);
	}
	public short getShort(int index, short defaultValue) {
		return arrayValue().getShort(index, defaultValue);
	}
	public int getInt(int index, int defaultValue) {
		return arrayValue().getInt(index, defaultValue);
	}
	public long getLong(int index, long defaultValue) {
		return arrayValue().getLong(index, defaultValue);
	}
	public float getFloat(int index, float defaultValue) {
		return arrayValue().getFloat(index, defaultValue);
	}
	public double getDouble(int index, double defaultValue) {
		return arrayValue().getDouble(index, defaultValue);
	}
	public BigInteger getBigInteger(int index, BigInteger defaultValue) {
		return arrayValue().getBigInteger(index, defaultValue);
	}
	public BigDecimal getBigDecimal(int index, BigDecimal defaultValue) {
		return arrayValue().getBigDecimal(index, defaultValue);
	}
	public String getString(int index, String defaultValue) {
		return arrayValue().getString(index, defaultValue);
	}
	public JsonObject getObject(int index, JsonObject defaultValue) {
		return arrayValue().getObject(index, defaultValue);
	}
	public JsonArray getArray(int index, JsonArray defaultValue) {
		return arrayValue().getArray(index, defaultValue);
	}
	public LocalDate getLocalDate(int index, LocalDate defaultValue) {
		return arrayValue().getLocalDate(index, defaultValue);
	}
	public LocalTime getLocalTime(int index, LocalTime defaultValue) {
		return arrayValue().getLocalTime(index, defaultValue);
	}
	public LocalDateTime getLocalDateTime(int index, LocalDateTime defaultValue) {
		return arrayValue().getLocalDateTime(index, defaultValue);
	}
	public OffsetTime getOffsetTime(int index, OffsetTime defaultValue) {
		return arrayValue().getOffsetTime(index, defaultValue);
	}
	public OffsetDateTime getOffsetDateTime(int index, OffsetDateTime defaultValue) {
		return arrayValue().getOffsetDateTime(index, defaultValue);
	}
	public ZonedDateTime getZonedDateTime(int index, ZonedDateTime defaultValue) {
		return arrayValue().getZonedDateTime(index, defaultValue);
	}	
}