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

/**
 * A JSON value as a hash key, with the JSON equality: 1 and 1.0 are the same key.
 */
final class JsonValueKey {
	private final Object value;
	private final int hash;
	JsonValueKey(Object value) {
		this.value = value;
		this.hash = JsonUtil.hashCode(value);
	}
	@Override
	public int hashCode() {
		return hash;
	}
	@Override
	public boolean equals(Object o) {
		return o instanceof JsonValueKey k && hash==k.hash && JsonUtil.eq(value, k.value);
	}
}
