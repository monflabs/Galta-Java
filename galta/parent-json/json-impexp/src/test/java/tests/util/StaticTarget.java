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
package tests.util;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.json.impexp.JsonContent;
import org.monflabs.json.impexp.JsonKey;
import org.monflabs.json.impexp.impl.JsonTargetImpl;
import org.monflabs.json.impexp.util.StaticContent;

public class StaticTarget extends JsonTargetImpl {
	
	public static class Builder extends TargetBuilder<StaticTarget,Builder> {
		private Builder() {}
		@Override
		protected StaticTarget _build() {
			return new StaticTarget(this);
		}
	}
	public static Builder newBuilder() {
		return new Builder();
	}

	private static class Content extends StaticContent {
		public Content(JsonKey key, Object json, Instant timestamp) {
			super(key,json,timestamp);
		}
	}

	private Map<String,JsonContent> contents = new LinkedHashMap<>();
	
	protected StaticTarget(Builder builder) {
		super(builder);
	}

	@Override
	public boolean supportsDeletions() {
		return true;
	}

	public Map<String,JsonContent> getContents() {
		return contents;
	}
	public JsonArray getContentAsJson() {
		JsonArray result = JsonArray.create();
		
		String[] keys = contents.keySet().toArray(new String[contents.size()]);
		Arrays.sort(keys);
		
		for(int i=0; i<keys.length; i++) {
			JsonContent content = contents.get(keys[i]);
			JsonObject obj = JsonObject.create();
			String key = composeMapKey(content.getKey());
			obj.putValue("key", key);
			obj.putValue("value", content.getJson());
			result.addValue(obj);
		}
		
		return result;
	}
	
	@Override
	public void saveJsonContent(JsonContent content) {
		switch(content.getType()) {
			case RECORD -> {
				Content c = new Content(content.getKey(), JsonFactory.get().deepClone(content.getJson()), content.getTimestamp());
				String key = composeMapKey(content.getKey());
				contents.put(key,c);
			}
			case DELETION -> {
				String key = composeMapKey(content.getKey());
				contents.remove(key);
			}
		}
	}
	
	private String composeMapKey(JsonKey _key) {
		return _key.keyString();
	}

	@Override
	public void init() {
		super.init();
		contents.clear();
	}

	@Override
	public void close() {
		super.close();
	}
}