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
package org.monflabs.galtajs.cdp.protocol;

import java.util.List;
import java.util.Map;

/**
 * Typed access to a request's parameters.
 */
final class Params {
	private final Map<String, Object> map;

	Params(final Object params) {
		this.map = params instanceof Map<?, ?> m ? castMap(m) : Map.of();
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> castMap(final Map<?, ?> m) {
		return (Map<String, Object>) m;
	}

	/** Whether the parameter is present with a non-null value. */
	boolean has(final String name) {
		return map.get(name) != null;
	}

	/** Whether the parameter is present at all, even as an explicit null. */
	boolean containsKey(final String name) {
		return map.containsKey(name);
	}

	Object raw(final String name) {
		return map.get(name);
	}

	String string(final String name) throws CdpError {
		final Object v = map.get(name);
		if (v == null) {
			throw CdpError.invalidParams(name + " is required");
		}
		return v.toString();
	}

	String string(final String name, final String fallback) {
		final Object v = map.get(name);
		return v == null ? fallback : v.toString();
	}

	int integer(final String name) throws CdpError {
		final Object v = map.get(name);
		if (!(v instanceof Number n)) {
			throw CdpError.invalidParams(name + " must be an integer");
		}
		return n.intValue();
	}

	int integer(final String name, final int fallback) {
		final Object v = map.get(name);
		return v instanceof Number n ? n.intValue() : fallback;
	}

	boolean bool(final String name, final boolean fallback) {
		final Object v = map.get(name);
		return v instanceof Boolean b ? b : fallback;
	}

	Params object(final String name) throws CdpError {
		final Object v = map.get(name);
		if (!(v instanceof Map)) {
			throw CdpError.invalidParams(name + " must be an object");
		}
		return new Params(v);
	}

	Params objectOrNull(final String name) {
		final Object v = map.get(name);
		return v instanceof Map ? new Params(v) : null;
	}

	List<?> list(final String name) {
		final Object v = map.get(name);
		return v instanceof List<?> l ? l : List.of();
	}
}
