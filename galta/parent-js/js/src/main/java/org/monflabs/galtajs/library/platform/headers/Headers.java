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
package org.monflabs.galtajs.library.platform.headers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.NativeObject;

/**
 * WHATWG Headers-like object. Keys are stored lowercase (per spec), and
 * repeated same-name appends are joined with ", " on get (matching
 * fetch/Headers "get combined value" behavior).
 */
public class Headers extends NativeObject {

	public static final String CLASSNAME = "Headers";

	private final Map<String, List<String>> map = new LinkedHashMap<>();

	public Headers(JSEnvironment env) {
		super(env);
		installMethods(env);
	}

	@Override
	public String getClassName() {
		return CLASSNAME;
	}

	private void installMethods(JSEnvironment env) {
		setOwnMethod(new Method(env, "append", 2));
		setOwnMethod(new Method(env, "delete", 1));
		setOwnMethod(new Method(env, "get", 1));
		setOwnMethod(new Method(env, "has", 1));
		setOwnMethod(new Method(env, "set", 2));
		setOwnMethod(new Method(env, "keys", 0));
		setOwnMethod(new Method(env, "values", 0));
		setOwnMethod(new Method(env, "entries", 0));
		setOwnMethod(new Method(env, "forEach", 1));
	}

	public void append(String name, String value) {
		String k = normalizeName(name);
		String v = normalizeValue(value);
		map.computeIfAbsent(k, __ -> new ArrayList<>()).add(v);
	}

	public void set(String name, String value) {
		String k = normalizeName(name);
		String v = normalizeValue(value);
		List<String> list = new ArrayList<>(1);
		list.add(v);
		map.put(k, list);
	}

	public String get(String name) {
		List<String> list = map.get(normalizeName(name));
		if (list == null || list.isEmpty()) {
			return null;
		}
		return String.join(", ", list);
	}

	@Override
	public boolean has(String name) {
		return map.containsKey(normalizeName(name));
	}

	public void remove(String name) {
		map.remove(normalizeName(name));
	}

	public Iterator<Map.Entry<String, List<String>>> headerEntries() {
		return map.entrySet().iterator();
	}

	public Headers copy() {
		Headers h = new Headers(getEnvironment());
		for (Map.Entry<String, List<String>> e : map.entrySet()) {
			for (String v : e.getValue()) {
				h.append(e.getKey(), v);
			}
		}
		return h;
	}

	static String normalizeName(String name) {
		if (name == null) {
			throw RuntimeUtil.typeError("Headers: name is null");
		}
		String trimmed = name.trim();
		if (trimmed.isEmpty()) {
			throw RuntimeUtil.typeError("Headers: invalid name");
		}
		return trimmed.toLowerCase(Locale.ROOT);
	}

	static String normalizeValue(String value) {
		return value == null ? "" : value.trim();
	}

	private final class Method extends BaseMethod {
		private final String methodName;

		private Method(JSEnvironment env, String name, int length) {
			super(env, name, length);
			this.methodName = name;
		}

		@Override
		protected Object invoke(Object obj, Object[] args) {
			JSEnvironment env = getEnvironment();
			switch (methodName) {
				case "append": {
					String n = RuntimeUtil.toString(env, param(args, 0));
					String v = RuntimeUtil.toString(env, param(args, 1));
					Headers.this.append(n, v);
					return RuntimeUtil.UNDEFINED;
				}
				case "set": {
					String n = RuntimeUtil.toString(env, param(args, 0));
					String v = RuntimeUtil.toString(env, param(args, 1));
					Headers.this.set(n, v);
					return RuntimeUtil.UNDEFINED;
				}
				case "delete": {
					String n = RuntimeUtil.toString(env, param(args, 0));
					Headers.this.remove(n);
					return RuntimeUtil.UNDEFINED;
				}
				case "get": {
					String n = RuntimeUtil.toString(env, param(args, 0));
					String v = Headers.this.get(n);
					return v == null ? null : v;
				}
				case "has": {
					String n = RuntimeUtil.toString(env, param(args, 0));
					return Headers.this.has(n);
				}
				case "keys": {
					return arrayOf(map.keySet().toArray(new String[0]));
				}
				case "values": {
					List<String> out = new ArrayList<>();
					for (List<String> vs : map.values()) {
						out.add(String.join(", ", vs));
					}
					return arrayOf(out.toArray(new String[0]));
				}
				case "entries": {
					List<Object[]> out = new ArrayList<>();
					for (Map.Entry<String, List<String>> e : map.entrySet()) {
						out.add(new Object[] { e.getKey(), String.join(", ", e.getValue()) });
					}
					return arrayOfPairs(out);
				}
				case "forEach": {
					if (args.length == 0 || !(args[0] instanceof Callable cb)) {
						throw RuntimeUtil.typeError("Headers.forEach requires a callback");
					}
					Object thisArg = args.length > 1 ? args[1] : null;
					for (Map.Entry<String, List<String>> e : map.entrySet()) {
						String v = String.join(", ", e.getValue());
						cb.call(thisArg, new Object[] { v, e.getKey(), Headers.this });
					}
					return RuntimeUtil.UNDEFINED;
				}
				default:
					throw new IllegalStateException("Unknown Headers method: " + methodName);
			}
		}

		private Object arrayOf(String[] items) {
			return JSArray.of(getEnvironment(), (Object[]) items);
		}

		private Object arrayOfPairs(List<Object[]> pairs) {
			Object[] rows = new Object[pairs.size()];
			for (int i = 0; i < pairs.size(); i++) {
				rows[i] = JSArray.of(getEnvironment(), pairs.get(i));
			}
			return JSArray.of(getEnvironment(), rows);
		}
	}
}
