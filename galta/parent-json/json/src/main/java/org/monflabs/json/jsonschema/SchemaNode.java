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
package org.monflabs.json.jsonschema;

import java.sql.JDBCType;
import java.util.Map;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.wrappers.JsonObjectWrapperImpl;
import org.monflabs.json.wrappers.WrappedMap;
import org.monflabs.util.StringUtil;

/**
 * 
 * @author priand
 *
 */
public class SchemaNode extends JsonObjectWrapperImpl {
	
	// Extended attributes
	//   https://json-schema.org/draft/2019-09/json-schema-core.html#rfc.section.6.5
	public static final String FORMAT_EX = "mf-format-ex";

	// Used by gnerators to provide more type information
	public static final String JDBC_EXTENSIONS = "mf-jdbc";
	public static final String JDBC_TYPE = "jdbc-type";

	public static final String SALESFORCE_EXTENSIONS = "mf-salesforce";

	public SchemaNode(JsonObject value) {
		super(value);
	}
	
	public String getId() {
		return wrapped().getString("$id",null);
	}

	public String getSchema() {
		return wrapped().getString("$schema",null);
	}
	
	public String getType() {
		// "type" may be an array (e.g. ["string","null"]): report the main type then
		Object type = wrapped().get("type");
		if(type instanceof JsonArray) {
			return getMainType();
		}
		return type instanceof String s ? s : null;
	}
	
	public boolean isNull() {
		String type = getMainType();
		return type!=null && type.equals("null");
	}
	
	public boolean isBoolean() {
		String type = getMainType();
		return type!=null && type.equals("boolean");
	}
	
	public boolean isNumber() {
		String type = getMainType();
		return type!=null && type.equals("number");
	}
	
	public boolean isString() {
		String type = getMainType();
		return type!=null && type.equals("string");
	}
	
	/**
	 * True if the type is "object", or if the schema has no type but describes
	 * object properties ("properties", "additionalProperties", "patternProperties").
	 * An ambiguous type (["string","object"]) is not an object.
	 */
	public boolean isObject() {
		if(!wrapped().containsKey("type")) {
			JsonObject o = wrapped();
			return o.containsKey("properties") || o.containsKey("additionalProperties") || o.containsKey("patternProperties");
		}
		return "object".equals(getMainType());
	}
	
	/**
	 * True if the type is "array", or if the schema has no type but describes
	 * array items ("items", "prefixItems").
	 */
	public boolean isArray() {
		if(!wrapped().containsKey("type")) {
			JsonObject o = wrapped();
			return o.containsKey("items") || o.containsKey("prefixItems");
		}
		return "array".equals(getMainType());
	}
	
	public String getTitle() {
		return wrapped().getString("title",null);
	}

	public String getDescription() {
		return wrapped().getString("description",null);
	}
	
	public Map<String,SchemaNode> getProperties() {
		JsonObject props = wrapped().getObject("properties",null);
		if(props!=null) {
			return new WrappedMap<SchemaNode>(props, (o) -> {
				return schema((JsonObject)o);
			});
		}
		return null;
	}
	
	protected SchemaNode schema(JsonObject o) {
		return new SchemaNode(o);
	}

	// A malformed type entry (not a string) is no type, rather than a ClassCastException
	private static String asTypeName(Object o) {
		return o instanceof String s ? s : null;
	}

	public String getMainType() {
		Object type = wrapped().get("type");
		if(type instanceof JsonArray a) {
			switch(a.size()) {
				case 0 ->  { return null; }
				case 1 ->  { return asTypeName(a.get(0)); }
				case 2 ->  {
					if("null".equals(a.get(0)))  {
						return asTypeName(a.get(1));
					}
					if("null".equals(a.get(1)))  {
						return asTypeName(a.get(0));
					}
					return null;
				}
				default -> { return null; }
			}
		} else {
			return type instanceof String s ? s : null;
		}
	}
	public boolean isNullable() {
		Object type = wrapped().get("type");
		if(type instanceof JsonArray a) {
			return a.contains("null");
		} else {
			return type!=null && type.equals("null");
		}
	}
	
	
	@SuppressWarnings("incomplete-switch")
	public SchemaType schemaType() {
		JsonObject scObject = wrapped();
		
		// Convert the JSON schema type
		String type = getMainType();
		if(type==null) {
			throw new JsonException(null, "Missing or ambiguous JSON schema type, {0}", scObject.stringify(true));
		}
		switch(type) {
			case "string" -> {
				String format = scObject.getString("format",null);
				if(format!=null) {
					switch(format) {
						case "date" -> {
							return SchemaType.LOCALDATE; 
						}
						case "time" -> {
							if(StringUtil.equals(formatEx(scObject),"time-tz")) {
								return SchemaType.OFFSETTIME;
							}
// Should not be needed!							
//							if(jdbcType(scObject)==JDBCType.TIME_WITH_TIMEZONE) {
//								return JDBCType.TIME_WITH_TIMEZONE;
//							}
							return SchemaType.LOCALTIME;
						}
						case "date-time" -> {
							if(StringUtil.equals(formatEx(scObject),"date-time-tz")) {
								return SchemaType.OFFSETDATETIME;
							}
// Should not be needed!							
//							if(jdbcType(scObject)==JDBCType.TIMESTAMP_WITH_TIMEZONE) {
//								return JDBCType.TIMESTAMP_WITH_TIMEZONE;
//							}
							return SchemaType.LOCALDATETIME; 
						}
					}
				}
				return SchemaType.STRING;
			}
// PHIL: not sure about these types - have to be verified
			case "integer" -> {
				JDBCType jt = jdbcType(scObject);
				if(jt!=null) {
					switch(jt) {
						case TINYINT -> { return SchemaType.BYTE; }
						case SMALLINT -> { return SchemaType.SHORT; }
						case INTEGER -> { return SchemaType.INTEGER; }
						case BIGINT -> { return SchemaType.LONG; } // a JDBC BIGINT is a 64-bit integer
					}
				}
				return SchemaType.INTEGER;
			}
			case "number" -> {
				JDBCType jt = jdbcType(scObject);
				if(jt!=null) {
					switch(jt) {
						case REAL -> { return SchemaType.FLOAT; }
						case FLOAT -> { return SchemaType.DOUBLE; }
						case DOUBLE -> { return SchemaType.DOUBLE; }
						case NUMERIC -> { return SchemaType.BIGDECIMAL; }
						case DECIMAL -> { return SchemaType.BIGDECIMAL; }
					}
				}
				return SchemaType.NUMBER;
			}
			case "boolean" -> {
				return SchemaType.BOOLEAN;
			}
			case "object" -> {
				return SchemaType.OBJECT;
			}
			case "array" -> {
				return SchemaType.ARRAY;
			}
		}
		throw new JsonException(null, "Cannot convert JSON type {0} to a schema type, {1}", type, scObject.stringify(true));
	}
	
	//
	// Json type to SQL
	//
	@SuppressWarnings("incomplete-switch")
	public JDBCType jdbcType() {
		JsonObject scObject = wrapped();
		
		// Convert the JSON schema type
		String type = getMainType();
		if(type==null) {
			throw new JsonException(null, "Missing or ambiguous JSON schema type, {0}", scObject.stringify(true));
		}
		switch(type) {
			case "string" -> {
				String format = scObject.getString("format",null);
				if(format!=null) {
					switch(format) {
						case "date" -> {
							return JDBCType.DATE; 
						}
						case "time" -> {
							if(StringUtil.equals(formatEx(scObject),"time-tz")) {
								return JDBCType.TIME_WITH_TIMEZONE;
							}
// Should not be needed!							
//							if(jdbcType(scObject)==JDBCType.TIME_WITH_TIMEZONE) {
//								return JDBCType.TIME_WITH_TIMEZONE;
//							}
							return JDBCType.TIME;
						}
						case "date-time" -> {
							if(StringUtil.equals(formatEx(scObject),"date-time-tz")) {
								return JDBCType.TIMESTAMP_WITH_TIMEZONE;
							}
// Should not be needed!							
//							if(jdbcType(scObject)==JDBCType.TIMESTAMP_WITH_TIMEZONE) {
//								return JDBCType.TIMESTAMP_WITH_TIMEZONE;
//							}
							return JDBCType.TIMESTAMP; 
						}
					}
				}
				return JDBCType.VARCHAR;
			}
			case "integer" -> {
				JDBCType jt = jdbcType(scObject);
				if(jt!=null) {
					switch(jt) {
						case TINYINT, SMALLINT, INTEGER, BIGINT 
							-> { return jt; }
					}
				}
				return JDBCType.INTEGER;
			}
			case "number" -> {
				JDBCType jt = jdbcType(scObject);
				if(jt!=null) {
					switch(jt) {
						case REAL, FLOAT, DOUBLE, NUMERIC, DECIMAL 
							-> { return jt; }
					}
				}
				return JDBCType.DOUBLE;
			}
			case "boolean" -> {
				return JDBCType.BOOLEAN;
			}
		}
		throw new JsonException(null, "Cannot convert JSON type {0} to JDBC, {1}", type, scObject.stringify(true));
	}
	
	
	//
	// Extended formats
	//
	private static String formatEx(JsonObject schemaNode) {
		return schemaNode.getString(SchemaNode.FORMAT_EX,null);
	}
	private static JDBCType jdbcType(JsonObject schemaNode) {
//	      "mf-jdbc":{
//	          "jdbc-type":"TIMESTAMP_WITH_TIMEZONE",
//	          "DATA_TYPE":2014,
//	          "TYPE_NAME":"timestamptz",
//	          "COLUMN_SIZE":35,
//	          "DECIMAL_DIGITSD":6,
//	          "NUM_PREC_RADIX":10,
//	          "NULLABLE":true
//	        }
		JsonObject typeJdbc = schemaNode.getObject(SchemaNode.JDBC_EXTENSIONS,null);
		if(typeJdbc!=null) {
			String jdbcType = typeJdbc.getString(SchemaNode.JDBC_TYPE,null);
			if(jdbcType!=null) {
				try {
					return JDBCType.valueOf(jdbcType);
				} catch(IllegalArgumentException ex) {
					throw new JsonException(ex, "Invalid JDBC type {0} in {1}", jdbcType, SchemaNode.JDBC_EXTENSIONS);
				}
			}
		}
		return null;
	}
}
