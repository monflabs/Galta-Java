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
package org.monflabs.json.impexp.util;

import java.text.MessageFormat;

import org.monflabs.json.impexp.JsonContent;
import org.monflabs.json.impexp.JsonKey;

public class StaticDeletedContent implements JsonContent {
	
	private JsonKey key;
	
	public StaticDeletedContent(JsonKey key) {
		this.key = key;
	}
	
	@Override
	public String toString() {
		return MessageFormat.format("{0} DELETED",key);
	}

	@Override
	public TYPE getType() {
		return TYPE.DELETION;
	}
	@Override
	public JsonKey getKey() {
		return key;
	}
	@Override
	public Object getJson() {
		return null;
	}
}
