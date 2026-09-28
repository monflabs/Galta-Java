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
package tests.extras;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.json.JsonObject;
import org.monflabs.util.Console;
import org.monflabs.util.StringFormat;

import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interop.V8Host;
import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.javet.values.IV8Value;
import com.caoccao.javet.values.V8Value;
import com.caoccao.javet.values.primitive.V8ValueBigInteger;
import com.caoccao.javet.values.primitive.V8ValueBoolean;
import com.caoccao.javet.values.primitive.V8ValueLong;
import com.caoccao.javet.values.primitive.V8ValueNumber;
import com.caoccao.javet.values.primitive.V8ValueString;
import com.caoccao.javet.values.reference.IV8ValueArray;
import com.caoccao.javet.values.reference.V8ValueArray;
import com.caoccao.javet.values.reference.V8ValueObject;

import tests.javascript.JavaScriptStrictTestCase;
import util.GlobalTestEnvironment;

/**
 * @author Philippe Riand
 */
public class CheckPropertiesTest extends JavaScriptStrictTestCase {

	public static String BROWSE_PROPERTIES = 
"""
const obj = {0};

const props = {}; 

const stringProps = Object.getOwnPropertyNames(obj);
for (const prop of stringProps) {
  const key = prop;
  props[key] = Object.getOwnPropertyDescriptor(obj,prop);
  try {props[key].functionLength = typeof obj[prop]==='function' ? obj[prop].length : -1; } catch(e){}
}

const symbolProps = Object.getOwnPropertySymbols(obj);
for (const prop of symbolProps) {
  const key = '['+prop.toString()+']';
  props[key] = Object.getOwnPropertyDescriptor(obj,prop);
  try {props[key].functionLength = typeof obj[prop]==='function' ? obj[prop].length : -1; } catch(e){}
}

props;
""";
	public static String BROWSE_ONE_PROPERTIES = 
"""
const obj = {0};

const props = {}; 

const symbolProps = Object.getOwnPropertySymbols(obj);
for (const prop of symbolProps) {
  const key = '['+prop.toString()+']';
  props[key] = Object.getOwnPropertyDescriptor(obj,prop);
}

props;
""";
	@Override
	protected JSEnvironment.Builder createEnvironment() {
		JSEnvironment.Builder envBuilder = GlobalTestEnvironment.newBuilder()
				.strictMode(false);
		return envBuilder;
	}

	
	//
	// GraalVM
	//
	@SuppressWarnings("unused")
	private JsonObject readGraalVMDescriptors(String object) throws Exception {
        try (Context context = Context.create("js")) {
        	JsonObject map = JsonObject.create(); 
        	String code = StringFormat.format(BROWSE_PROPERTIES,object);
            Value result = context.eval("js", code);
            Set<String> props = result.getMemberKeys();
            for(String key: props) {
            	Value v = result.getMember(key);
            	JsonObject desc = JsonObject.of(
                		"writable", propBoolean(v,"writable"),
                		"configurable", propBoolean(v,"configurable"),
                		"enumerable", propBoolean(v,"enumerable"),
                		"functionLength", propInt(v,"functionLength")
                	);
                map.put(key, desc);
            }
            return map;
            //System.out.println("Result: " + result.asString());
        }	
    }
	private boolean propBoolean(Value v, String prop) {
		Value m = v.getMember(prop);
		return m!=null ? m.asBoolean() : false;
	}
	private int propInt(Value v, String prop) {
		Value m = v.getMember(prop);
		return m!=null ? m.asInt() : -1;
	}

	
	//
	// V8
	//
	private JsonObject readV8Descriptors(String object) throws Exception {
        try (V8Runtime v8Runtime = V8Host.getV8Instance().createV8Runtime()) {
        	JsonObject map = JsonObject.create(); 
        	String code = StringFormat.format(BROWSE_PROPERTIES,object);
        	try(V8ValueObject props = v8Runtime.getExecutor(code).execute()) {  
	        	try(IV8ValueArray nameArray = (IV8ValueArray)props.getOwnPropertyNames()) {
		        	int length = nameArray.getLength();
		        	for (int i = 0; i < length; i++) {
		        		String key = nameArray.get(i).asString();
		        		try(V8ValueObject v = props.getProperty(key)) {
			            	JsonObject desc = JsonObject.of(
			            		"writable", propBoolean(v,"writable"),
			            		"configurable", propBoolean(v,"configurable"),
			            		"enumerable", propBoolean(v,"enumerable"),
			            		"functionLength", propInt(v,"functionLength")
			            	);
			            	map.put(key, desc);
		        		}
		            }
		            return map;
	        	}
	        } catch(JavetException e) {
	            System.out.println("\n\n=== JavetException Details ===");
	            System.out.println("Message: " + e.getMessage());
	            System.out.println("Type: " + e.getClass().getSimpleName());
	            
	            Map<String, Object> params = e.getParameters();
	
	            System.out.println("");
	            if (e.getError() != null) {
	                System.out.println("JavaScript Error Type: " + e.getError().getType());
	                System.out.println("JavaScript Error Message: " + e.getError().getMessage(params));
	            }
	            
	            System.out.println("");
	            for(Entry<String, Object> param : params.entrySet()) {
					System.out.println("Parameter: " + param.getKey() + " = " + param.getValue());
	            }

	            System.out.println("");
	            try (BufferedReader reader = new BufferedReader(new StringReader(code))) {
	                String line;
	                for ( int i=1; (line = reader.readLine()) != null; i++) {
	                	System.out.printf("%4d: %s%n", i, line);
	                }
	            }	            
	            // throw e;
	            return JsonObject.create(); // Return empty object on error
			}
    	}
	}
	private boolean propBoolean(V8ValueObject v, String prop) throws Exception {
		IV8Value o =v.getProperty(prop);
		return !o.isNullOrUndefined() ? o.asBoolean() : false;
	}
	private int propInt(V8ValueObject v, String prop) throws Exception {
		IV8Value o =v.getProperty(prop);
		return !o.isNullOrUndefined() ? o.asInt() : -1;
	}

//	public void testJavetVersion() throws Exception {
//		 System.out.println("Javet version: " + V8Host.getV8Instance().getJavetVersion());
//		 System.out.println("V8 version: " + V8Host.getV8Instance().createV8Runtime().getVersion());
//	}
	
	//
	// GaltaJS
	//
	private JsonObject readGaltaJSDescriptors(String object) throws Exception {
		JSEnvironment env = getEnvironment();
    	String code = StringFormat.format(BROWSE_PROPERTIES,object);
    	JSObject result = env.evaluateExpression(code);

    	//return (JsonObject)result;
        
    	JsonObject map = JsonObject.create();
    	for(Iterator<String> it=result.ownPropertyKeys(true); it.hasNext(); ) {
    		String key = it.next();
        	JSObject v = (JSObject)result.getProperty(key);
        	JsonObject desc = JsonObject.of(
            		"writable", propBoolean(v,"writable"),
            		"configurable", propBoolean(v,"configurable"),
            		"enumerable", propBoolean(v,"enumerable"),
            		"functionLength", propInt(v,"functionLength")
            	);
            map.put(key, desc);
        }
        return map;

    }
	private boolean propBoolean(JSObject v, String prop) throws Exception {
		if(v.hasProperty(prop) && RuntimeUtil.isNotNullOrUndefined(v.getProperty(prop))) {
			return RuntimeUtil.toBoolean(getEnvironment(), v.getProperty(prop));
		}
		return false;
	}
	private int propInt(JSObject v, String prop) throws Exception {
		if(v.hasProperty(prop) && RuntimeUtil.isNotNullOrUndefined(v.getProperty(prop))) {
			return RuntimeUtil.toInt(getEnvironment(), v.getProperty(prop));
		}
		return -1;
	}
	
	
	//
	// List properties
	//
//	public void testOneProperties() throws Exception {
//        try (V8Runtime v8Runtime = V8Host.getV8Instance().createV8Runtime()) {
//        	String code = StringFormat.format(BROWSE_ONE_PROPERTIES,"Symbol.prototype");
//        	V8ValueObject props = v8Runtime.getExecutor(code).execute();
//        	Object v = convertV8Value(props);
//        	String s = JsonFactory.get().stringify(v,false);
//        	Console.log(s);
//	    }
//	}
//
//	public void testProperties() throws Exception {
//		Console.log("\n\nV8 Object");
//		dumpProperties(readV8Descriptors("Symbol.prototype"));
//		
//		Console.log("\n\nGaltaJS Objects");
//		dumpProperties(readGaltaJSDescriptors("String.prototype"));
//		
//		Console.log("\n\nGraalVM Objects");
//		dumpProperties(readGraalVMDescriptors("String.prototype"));
//	}		
//	private void dumpProperties(JsonObject map) throws Exception {
//		Object[] keys = map.keySet().toArray();
//		Arrays.sort(keys);
//		for(int i=0; i<keys.length; i++) {
//			JsonObject p = map.getObject((String)keys[i]);
//	    	Console.log("{0}={ writable: {1}, configurable: {2}, enumerable: {3}, functionLength={4} }",keys[i],p.getBoolean("writable",false),p.getBoolean("configurable",false),p.getBoolean("enumerable",false),p.getInt("functionLength",-1));
//		}
//	}		
	
	//
	// TESTS
	
	//
//	public void testCompareOneComponentProperties() throws Exception {
//		checkProperties("Symbol.prototype");
//	}
	
	public void testCompareProperties() throws Exception {
		// Just an individual test
		if(_ALLTESTS) {
			return;
		}
		
		checkCtorAndPrototype("Symbol");

		checkCtorAndPrototype("Number");
		checkCtorAndPrototype("Boolean");
		checkCtorAndPrototype("String");
		checkCtorAndPrototype("Object");
		checkCtorAndPrototype("Array");
		checkCtorAndPrototype("Symbol");
		
		checkCtorAndPrototype("BigInt");
		checkCtorAndPrototype("Date");
		checkCtorAndPrototype("Function");
		checkCtorAndPrototype("Map");
		checkCtorAndPrototype("Set");
		checkCtorAndPrototype("RegExp");
		checkCtorAndPrototype("WeakMap");
		checkCtorAndPrototype("WeakRef");
		checkCtorAndPrototype("WeakSet");
		
		checkCtorAndPrototype("ArrayBuffer");
		checkCtorAndPrototype("BigInt64Array");
		checkCtorAndPrototype("BigUint64Array");
		checkCtorAndPrototype("DataView");
		//checkProperties("Float16Array.prototype"); // Does not exist in V8
		checkCtorAndPrototype("Float32Array");
		checkCtorAndPrototype("Float64Array");
		checkCtorAndPrototype("Int16Array");
		checkCtorAndPrototype("Int32Array");
		checkCtorAndPrototype("Int8Array");
		checkCtorAndPrototype("SharedArrayBuffer");
		checkCtorAndPrototype("Uint16Array");
		checkCtorAndPrototype("Uint32Array");
		checkCtorAndPrototype("Uint8Array");
		checkCtorAndPrototype("Uint8ClampedArray");
		
		checkProperties("console");
		checkProperties("JSON");
		checkProperties("Math");
		checkProperties("Reflect");
		//checkProperties("Performance.prototype");
	}
	private void checkCtorAndPrototype(String object) throws Exception {
		checkProperties(object);
		checkProperties(object+".prototype");
	}
	private Differences checkProperties(String object) throws Exception {
		JsonObject js = readV8Descriptors(object);
		//JsonObject js = readGraalVMDescriptors(object);
		JsonObject ga = readGaltaJSDescriptors(object);

		Differences diff = new Differences(js, ga);
		
		for(Map.Entry<String,Object> e: js.entrySet()) {
			if(!ga.containsKey(e.getKey())) {
				// Property is missing in GaltaJS
				diff.extraNative.add(e.getKey());
			} else {
				JsonObject p_ga = ga.getObject(e.getKey());
				JsonObject p_v8 = (JsonObject)e.getValue();
				if(p_ga.getBoolean("writable",false)!=p_v8.getBoolean("writable",false) || p_ga.getBoolean("configurable",false)!=p_v8.getBoolean("configurable",false) || p_ga.getBoolean("enumerable",false)!=p_v8.getBoolean("enumerable",false)  || p_ga.getInt("functionLength",-1)!=p_v8.getInt("functionLength",-1)) {
					// Property is different
					diff.differences.add(e.getKey());
				}
			}
		}

		for(Map.Entry<String,Object> e: ga.entrySet()) {
			if(!js.containsKey(e.getKey())) {
				// Property is extra in GaltaJS
				diff.extraGalta.add(e.getKey());
			}
		}
		
		if(diff.hasDifferences()) {
			Console.log("==========================================================");
			Console.log("{0}\n", object);
			diff.dumpDifferences();
		}
		return diff;
	}
	
	private static class Differences {
		JsonObject nativeProps;
		JsonObject galtaProps;
		
		Set<String> extraNative = new HashSet<String>();
		Set<String> extraGalta = new HashSet<String>();
		Set<String> differences = new HashSet<String>();
		
		public Differences(JsonObject nativeProp, JsonObject galtaProps) {
			this.nativeProps = nativeProp;
			this.galtaProps = galtaProps;
		}
		
		public boolean hasDifferences() {
			return !extraNative.isEmpty() || !extraGalta.isEmpty() || !differences.isEmpty();
		}
		
		public void dumpDifferences() {
			if(!extraNative.isEmpty()) {
				Console.log("Extra Native Properties:");
				Object[] keys = extraNative.toArray();
				Arrays.sort(keys);
				for(int i=0; i<keys.length; i++) {
					Console.log("    - {0}", keys[i]);
				}
			}
			if(!extraGalta.isEmpty()) {
				Console.log("Extra GaltaJS Properties:");
				Object[] keys = extraGalta.toArray();
				Arrays.sort(keys);
				for(int i=0; i<keys.length; i++) {
					Console.log("    - {0}", keys[i]);
				}
			}
			if(!differences.isEmpty()) {
				Console.log("Property Differences:");
				Object[] keys = differences.toArray();
				Arrays.sort(keys);
				for(int i=0; i<keys.length; i++) {
					Console.log("    - {0}", keys[i]);
					JsonObject pn = nativeProps.getObject((String)keys[i]);
					JsonObject pg = galtaProps.getObject((String)keys[i]);
			    	Console.log("      Native: { writable: {0}, configurable: {1}, enumerable: {2}, functionLength={3} }",pn.getBoolean("writable",false),pn.getBoolean("configurable",false),pn.getBoolean("enumerable",false),pn.getInt("functionLength",-1));
			    	Console.log("      Galta:  { writable: {0}, configurable: {1}, enumerable: {2}, functionLength={3} }",pg.getBoolean("writable",false),pg.getBoolean("configurable",false),pg.getBoolean("enumerable",false),pg.getInt("functionLength",-1));
				}
			}
		}
	}
	
    public Object convertV8Value(V8Value value) throws Exception {
        if (value == null) {
            return null;
        } else if (value.isUndefined()) {
        	return RuntimeUtil.UNDEFINED;
        } else if(value instanceof V8ValueString v) { 
        	return v.asString();
        } else if(value instanceof V8ValueBoolean v) { 
        	return v.asBoolean();
        } else if(value instanceof V8ValueLong v) {
        	return v.asLong();
        } else if(value instanceof V8ValueBigInteger v) {
        	return v.toPrimitive();
        } else if(value instanceof V8ValueNumber v) { 
        	return v.asDouble();
        } else if(value instanceof V8ValueArray a) {
        	JSArray res = JSArray.create(getEnvironment());
        	a.forEach( (int index, V8Value itemValue) -> {
        		res.setOwnProperty(index,convertV8Value(itemValue));
        	});
        	return res;
        } else if(value instanceof V8ValueObject o) {
        	JSObject res = JSObject.create(getEnvironment());
            IV8ValueArray keys = o.getOwnPropertyNames();
            int length = keys.getLength();
            for (int i = 0; i < length; i++) {
                String key = keys.getString(i);
        		res.setOwnProperty(key,convertV8Value(o.get(key)));
        	};
        	return res;
        } else {
        	throw new RuntimeException("Invalid V8 type "+value.getClass());
        }
    }
}
