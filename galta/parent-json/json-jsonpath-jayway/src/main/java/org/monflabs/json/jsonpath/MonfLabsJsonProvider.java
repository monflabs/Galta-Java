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

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Collections;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;

import com.jayway.jsonpath.InvalidJsonException;
import com.jayway.jsonpath.JsonPathException;
import com.jayway.jsonpath.spi.json.AbstractJsonProvider;

public class MonfLabsJsonProvider extends AbstractJsonProvider {
	
	private JsonFactory getFactory() {
		return JsonFactory.get();
	}

    @Override
    public Object parse(String json) throws InvalidJsonException {
        try {
        	return getFactory().parse(json);
        } catch (Exception e) {
            throw new InvalidJsonException(e);
        }
    }

    @Override
    public Object parse(InputStream jsonStream, String charset) throws InvalidJsonException {

        try {
        	return getFactory().parse(new InputStreamReader(jsonStream, charset));
        } catch (Exception e) {
            throw new InvalidJsonException(e);
        }
    }

    @Override
    public Object unwrap(Object obj) {
        return obj;
    }

    @Override
    public String toJson(Object obj) {
        // E4: obj.toString() NPEs on null and returns bare (unquoted) strings, which is not JSON.
        // The factory stringifier handles every JSON value, including top-level strings and null.
        return getFactory().stringify(obj);
    }

    @Override
    public Object createArray() {
        return getFactory().createArray();
    }

    @Override
    public Object createMap() {
        return getFactory().createObject();
    }

    @Override
    public boolean isArray(Object obj) {
        return obj instanceof JsonArray;
    }

    @Override
    public Object getArrayIndex(Object obj, int idx) {
        try {
        	JsonArray a = toJsonArray(obj); 
            return a.has(idx) ? a.get(idx) : null;
        } catch (Exception e) {
            throw new JsonPathException(e);
        }
    }

    @Override
    public void setArrayIndex(Object array, int index, Object newValue) {
        try {
            if (!isArray(array)) {
                throw new UnsupportedOperationException();
            } else {
                // E1: same contract as AbstractJsonProvider - replace the element at index,
                // and only append when index==size. It used to append unconditionally.
                setArrayValue(toJsonArray(array), index, createJsonElement(newValue));
            }
        } catch (Exception e) {
            throw new JsonPathException(e);
        }
    }

    @Override
    public Object getMapValue(Object obj, String key) {
        try {
            JsonObject jsonObject = toJsonObject(obj);
            // E3: an explicit JSON null is a present value, only a missing key is UNDEFINED.
            if (!jsonObject.containsKey(key)) {
                return UNDEFINED;
            }
            return unwrap(jsonObject.get(key));
        } catch (Exception e) {
            throw new JsonPathException(e);
        }
    }

    @Override
    public void setProperty(Object obj, Object key, Object value) {
        try {
            if (isMap(obj)) {
                toJsonObject(obj).putValue(key.toString(), createJsonElement(value));
            } else {
                JsonArray array = toJsonArray(obj);
                int index;
                if (key != null) {
                    index = key instanceof Integer k ? k : Integer.parseInt(key.toString());
                } else {
                    index = array.size();
                }
                // E2: a property set on an array replaces the element (it used to insert, shifting the tail).
                setArrayValue(array, index, createJsonElement(value));
            }
        } catch (Exception e) {
            throw new JsonPathException(e);
        }
    }

    @Override
    public void removeProperty(Object obj, Object key) {
        if (isMap(obj)) {
            toJsonObject(obj).remove(key.toString());
        }         else {
            JsonArray array = toJsonArray(obj);
            int index = key instanceof Integer k ? k : Integer.parseInt(key.toString());
            array.remove(index);
        }
    }

    @Override
    public boolean isMap(Object obj) {
        return obj instanceof JsonObject;
    }

    @Override
    public Collection<String> getPropertyKeys(Object obj) {
        JsonObject jsonObject = toJsonObject(obj);
        try {
            if(jsonObject.size()==0) {
                return Collections.emptyList();
            }
            return jsonObject.keySet();
        } catch (Exception e) {
            throw new JsonPathException(e);
        }
    }

    @Override
    public int length(Object obj) {
        if (isArray(obj)) {
            return toJsonArray(obj).size();
        } else if (isMap(obj)) {
            return toJsonObject(obj).size();
        } else {
            if (obj instanceof String s) {
                return s.length();
            }
        }
        throw new JsonPathException("length operation can not applied to " + (obj != null ? obj.getClass().getName()
                : "null"));
    }

    @Override
    public Iterable<?> toIterable(Object obj) {
        try {
            if (isArray(obj)) {
                JsonArray arr = toJsonArray(obj);
                return arr.values();
            } else {
                JsonObject jsonObject = toJsonObject(obj);
                return jsonObject.values();
            }
        } catch (Exception e) {
            throw new JsonPathException(e);
        }
    }

    private Object createJsonElement(Object o) {
        return o;
    }

    /**
     * Replaces the element at {@code index}, or appends when {@code index==size()},
     * mirroring {@code AbstractJsonProvider.setArrayIndex}.
     */
    private static void setArrayValue(JsonArray array, int index, Object value) {
        if (index == array.size()) {
            array.addValue(value);
        } else {
            array.setValue(index, value);
        }
    }

    private JsonArray toJsonArray(Object o) {
        return (JsonArray) o;
    }

    private JsonObject toJsonObject(Object o) {
        return (JsonObject) o;
    }

}

