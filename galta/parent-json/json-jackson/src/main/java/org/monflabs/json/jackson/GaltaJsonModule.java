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
package org.monflabs.json.jackson;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.IdentityHashMap;
import java.util.Map;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonContainer;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

/**
 * A Jackson module reading and writing Galta JSON values: register it in an ObjectMapper,
 * and {@link JsonObject}, {@link JsonArray} and {@link JsonContainer} become regular Jackson
 * types, as values, as fields of a Java object, or as the target of a conversion:
 * <pre>
 * ObjectMapper mapper = new ObjectMapper().registerModule(new GaltaJsonModule());
 * JsonObject o = mapper.readValue(text, JsonObject.class);       // parse with Jackson
 * JsonObject p = mapper.convertValue(person, JsonObject.class);  // a Java object as JSON values
 * Person q = mapper.convertValue(o, Person.class);               // and back
 * JsonNode n = mapper.valueToTree(o);                            // to the Jackson tree model
 * </pre>
 * The containers are created by the factory given to the module: with the factory of a
 * GaltaJS environment, they are JavaScript objects and arrays. The numbers follow the
 * rules of that factory, as when Galta parses the text (Integer when it fits, a BigDecimal
 * for a decimal a double can't hold...).
 * <p>
 * When a container is written, the factory of the container decides how each of its values
 * is written ({@link JsonFactory#exportValue(Object)}): a value JSON can't represent, like
 * JavaScript's undefined, is omitted from an object and null in an array. NaN and the
 * infinities are written as null, a Date as its ISO-8601 instant, as Galta's own
 * stringifier does, and the values Galta doesn't know are given to Jackson. A container
 * that contains itself is an error.
 */
public class GaltaJsonModule extends SimpleModule {

	private static final long serialVersionUID = 1L;

	private final transient JsonFactory factory;

	/**
	 * A module creating the containers with the default factory, {@link JsonFactory#get()}.
	 */
	public GaltaJsonModule() {
		this(JsonFactory.get());
	}

	/**
	 * A module creating the containers with the given factory.
	 */
	public GaltaJsonModule(JsonFactory factory) {
		super("GaltaJsonModule");
		this.factory = factory;
		addDeserializer(JsonContainer.class, new ContainerDeserializer<>(factory, JsonContainer.class));
		addDeserializer(JsonObject.class, new ContainerDeserializer<>(factory, JsonObject.class));
		addDeserializer(JsonArray.class, new ContainerDeserializer<>(factory, JsonArray.class));
		addSerializer(JsonContainer.class, new ContainerSerializer());
	}

	/**
	 * The factory creating the containers.
	 */
	public JsonFactory getFactory() {
		return factory;
	}


	//
	// Reading
	//

	private static final class ContainerDeserializer<T> extends StdDeserializer<T> {
		private static final long serialVersionUID = 1L;
		private final transient JsonFactory factory;

		ContainerDeserializer(JsonFactory factory, Class<T> type) {
			super(type);
			this.factory = factory;
		}

		@Override
		@SuppressWarnings("unchecked")
		public T deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
			JsonToken t = p.currentToken();
			Class<?> type = handledType();
			// FIELD_NAME: Jackson already consumed the START_OBJECT of the object
			boolean object = t==JsonToken.START_OBJECT || t==JsonToken.FIELD_NAME || t==JsonToken.END_OBJECT;
			boolean array = t==JsonToken.START_ARRAY;
			if((type==JsonObject.class && !object) || (type==JsonArray.class && !array) || (!object && !array)) {
				return (T)ctxt.handleUnexpectedToken(type, p);
			}
			if(t==JsonToken.START_OBJECT || t==JsonToken.START_ARRAY) {
				return (T)readValue(p, ctxt);
			}
			return (T)readFields(p, ctxt, factory.createObject());
		}

		private Object readValue(JsonParser p, DeserializationContext ctxt) throws IOException {
			switch(p.currentToken()) {
				case START_OBJECT:
					p.nextToken();
					return readFields(p, ctxt, factory.createObject());
				case START_ARRAY: {
					JsonArray a = factory.createArray();
					while(p.nextToken()!=JsonToken.END_ARRAY) {
						a.add(readValue(p, ctxt));
					}
					return a;
				}
				case VALUE_STRING:
					return p.getText();
				case VALUE_NUMBER_INT:
					if(p.getNumberType()==JsonParser.NumberType.INT && factory.defaultInteger()==JsonFactory.INTEGER.INT) {
						return Integer.valueOf(p.getIntValue());
					}
					// The rules of the factory, as when Galta parses the literal
					return factory.parseInteger(p.getText());
				case VALUE_NUMBER_FLOAT:
					if(p.getNumberType()==JsonParser.NumberType.BIG_DECIMAL) {
						// A BigDecimal value (from a Java object), kept as is
						return p.getDecimalValue();
					}
					// The original literal for a text: a decimal a double can't hold may be a
					// BigDecimal, as when Galta parses it
					return factory.parseDecimal(p.getText());
				case VALUE_TRUE:
					return Boolean.TRUE;
				case VALUE_FALSE:
					return Boolean.FALSE;
				case VALUE_NULL:
					return null;
				case VALUE_EMBEDDED_OBJECT:
					return p.getEmbeddedObject();
				default:
					return ctxt.handleUnexpectedToken(Object.class, p);
			}
		}

		// The fields of an object, from the current FIELD_NAME (or END_OBJECT) on
		private Object readFields(JsonParser p, DeserializationContext ctxt, JsonObject o) throws IOException {
			for(JsonToken t = p.currentToken(); t==JsonToken.FIELD_NAME; t = p.nextToken()) {
				String name = p.currentName();
				p.nextToken();
				o.put(name, readValue(p, ctxt));
			}
			return o;
		}
	}


	//
	// Writing
	//

	private static final class ContainerSerializer extends StdSerializer<JsonContainer> {
		private static final long serialVersionUID = 1L;

		ContainerSerializer() {
			super(JsonContainer.class);
		}

		@Override
		public void serialize(JsonContainer value, JsonGenerator gen, SerializerProvider provider) throws IOException {
			writeContainer(value, gen, provider, new IdentityHashMap<>());
		}

		private void writeContainer(JsonContainer c, JsonGenerator gen, SerializerProvider provider, Map<Object,Boolean> path) throws IOException {
			if(path.put(c, Boolean.TRUE)!=null) {
				throw JsonMappingException.from(gen, "Circular reference: a "+(c instanceof JsonArray ? "JSON array" : "JSON object")+" contains itself");
			}
			JsonFactory f = c.factory();
			if(c instanceof JsonArray a) {
				int size = a.size();
				gen.writeStartArray(a, size);
				for(int i=0; i<size; i++) {
					Object v = f.exportValue(a.get(i));
					if(v==JsonFactory.NO_VALUE) {
						gen.writeNull();
					} else {
						writeValue(v, gen, provider, path);
					}
				}
				gen.writeEndArray();
			} else {
				JsonObject o = (JsonObject)c;
				gen.writeStartObject(o);
				for(Map.Entry<String,Object> e: o.entrySet()) {
					Object v = f.exportValue(e.getValue());
					if(v==JsonFactory.NO_VALUE) {
						continue;
					}
					gen.writeFieldName(e.getKey());
					writeValue(v, gen, provider, path);
				}
				gen.writeEndObject();
			}
			path.remove(c);
		}

		private void writeValue(Object v, JsonGenerator gen, SerializerProvider provider, Map<Object,Boolean> path) throws IOException {
			if(v==null) {
				gen.writeNull();
			} else if(v instanceof String s) {
				gen.writeString(s);
			} else if(v instanceof Boolean b) {
				gen.writeBoolean(b);
			} else if(v instanceof Integer || v instanceof Short || v instanceof Byte) {
				gen.writeNumber(((Number)v).intValue());
			} else if(v instanceof Long l) {
				gen.writeNumber(l);
			} else if(v instanceof Double d) {
				if(d.isNaN() || d.isInfinite()) {
					gen.writeNull();
				} else {
					gen.writeNumber(d);
				}
			} else if(v instanceof Float f) {
				if(f.isNaN() || f.isInfinite()) {
					gen.writeNull();
				} else {
					gen.writeNumber(f);
				}
			} else if(v instanceof BigInteger b) {
				gen.writeNumber(b);
			} else if(v instanceof BigDecimal b) {
				gen.writeNumber(b);
			} else if(v instanceof JsonContainer c) {
				writeContainer(c, gen, provider, path);
			} else if(v instanceof java.util.Date d) {
				gen.writeString(d.toInstant().toString());
			} else if(v instanceof CharSequence s) {
				gen.writeString(s.toString());
			} else {
				// A value Galta doesn't know (a Java object...): Jackson writes it
				provider.defaultSerializeValue(v, gen);
			}
		}
	}
}
