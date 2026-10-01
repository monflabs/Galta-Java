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
package org.monflabs.json.serialization;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;

/**
 * An error raised while serializing or deserializing a value nested in an object graph.
 * <p>
 * The message is the one of the original error, followed by the location of the value that
 * failed, as a path from the root value: <code>$.address.lines[2]</code> (a field or a map
 * key is <code>.name</code>, an array or collection item is <code>[index]</code>).
 * <p>
 * When the original error is a plain {@link JsonException}, it is replaced by this exception
 * with the same cause and stack trace, so {@link #getCause()} is unchanged. Any other
 * exception becomes the cause.
 */
@SuppressWarnings("serial")
public class SerializationException extends JsonException {

	private final String detail;
	private final String path;

	private SerializationException(Throwable cause, String detail, String path) {
		super(cause, "{0} (at ${1})", detail, path);
		this.detail = detail;
		this.path = path;
	}

	/**
	 * The error message, without the location.
	 */
	public String getDetail() {
		return detail;
	}

	/**
	 * The location of the value that failed, like <code>$.items[2].name</code>.
	 */
	public String getPath() {
		return "$"+path;
	}

	/**
	 * Add the location of a property (a field, a record component or a map key) to an error.
	 */
	public static RuntimeException atProperty(RuntimeException ex, String name) {
		return at(ex, isIdentifier(name) ? "."+name : "['"+name.replace("'", "\\'")+"']");
	}

	/**
	 * Add the location of an array or collection item to an error.
	 */
	public static RuntimeException atIndex(RuntimeException ex, int index) {
		return at(ex, "["+index+"]");
	}

	private static RuntimeException at(RuntimeException ex, String segment) {
		SerializationException e;
		if(ex instanceof SerializationException se) {
			e = new SerializationException(se.getCause(), se.detail, segment+se.path);
		} else if(ex.getClass()==JsonException.class) {
			// Replaced, with the same cause
			e = new SerializationException(ex.getCause(), ex.getMessage(), segment);
		} else {
			String m = ex.getMessage();
			e = new SerializationException(ex, ex instanceof JsonException && m!=null ? m : ex.toString(), segment);
			return e;
		}
		e.setStackTrace(ex.getStackTrace());
		for(Throwable s: ex.getSuppressed()) {
			e.addSuppressed(s);
		}
		return e;
	}

	private static boolean isIdentifier(String name) {
		if(name.isEmpty() || !Character.isJavaIdentifierStart(name.charAt(0))) {
			return false;
		}
		for(int i=1; i<name.length(); i++) {
			if(!Character.isJavaIdentifierPart(name.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * A description of the type of a JSON value, for the error messages: "a JSON string",
	 * "a JSON array"...
	 */
	public static String describe(Object jsonValue) {
		if(jsonValue==null) {
			return "null";
		}
		if(jsonValue instanceof JsonObject) {
			return "a JSON object";
		}
		if(jsonValue instanceof JsonArray) {
			return "a JSON array";
		}
		if(jsonValue instanceof String) {
			return "a JSON string";
		}
		if(jsonValue instanceof Number) {
			return "a JSON number";
		}
		if(jsonValue instanceof Boolean) {
			return "a JSON boolean";
		}
		return "a "+jsonValue.getClass().getName();
	}
}
