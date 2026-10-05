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
package org.monflabs.json.java;

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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.JsonUtil;
import org.monflabs.json.jsonpath.JsonValues;
import org.monflabs.json.util.Reducer;
import org.monflabs.util.StringUtil;


/**
 * Json Array implemented as an ArrayLisy.
 */
@SuppressWarnings("serial")
public class JsonArrayAsArrayList extends ArrayList<Object> implements JsonArray {

	private String reference;

	public JsonArrayAsArrayList() {
	}

	public JsonArrayAsArrayList(int initialCapacity) {
		super(initialCapacity);
	}

	@Override
	public JsonArray clone() {
		return (JsonArray)super.clone();
	}
	
	@Override
	public boolean equals(Object o) {
		if(this==o) {
			return true;
		}
		if(o instanceof JsonArray l) {
			return JsonUtil.equalsArray(this, l);
		}
		// Any other List: the List contract, so the equality is symmetric with the JDK lists
		if(o instanceof List<?>) {
			return super.equals(o);
		}
		return false;
	}

	@Override
	public int hashCode() {
		// Consistent with equals(): [1] and [1.0] are equal, so they must hash the same
		return JsonUtil.hashCode(this);
	}


	@Override
	public JsonValues jsonValues() {
		return JsonValues.of(this);
	}
	
	@Override
	public String getReference() {
		return reference;
	}
	@Override
	public  void setReference(String reference) {
		this.reference = reference;
	}
	
	
	@Override
	public String toString() {
		// Pretty, and a circular reference doesn't throw
		return factory().toDisplayString(this);
	}
	
	
	@Override
	public JavaJsonFactory factory() {
		return JavaJsonFactory.instance;
	}

	@Override
	public Collection<Object> values() {
		return this;
	}


	// The helpers of JsonArray (getInt(), add(String)...) go through the List methods,
	// so a subclass (Checked, the JavaScript arrays) sees every change. Every index follows
	// the List contract; the at*() methods of JsonArray count a negative one from the end.

    // Original methods
	protected final Object _get(int index) {
		return super.get(index);
	}
	protected final Object _set(int index, Object value) {
		return super.set(index,value);
    }
	protected final void _add(int index, Object value) {
		super.add(index,value);
    }
	protected final Object _remove(int index) {
		return super.remove(index);
    }
}
