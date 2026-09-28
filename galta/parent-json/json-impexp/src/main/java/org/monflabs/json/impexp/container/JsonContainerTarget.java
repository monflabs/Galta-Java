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
package org.monflabs.json.impexp.container;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonContainer;
import org.monflabs.json.JsonObject;
import org.monflabs.json.impexp.JsonContent;
import org.monflabs.json.impexp.impl.JsonTargetImpl;
import org.monflabs.util.StringUtil;

public class JsonContainerTarget extends JsonTargetImpl {
	
	public static class Builder extends TargetBuilder<JsonContainerTarget,Builder> {
		private JsonInMemoryFormat format = JsonInMemoryFormat.RECORDS;
		private JsonContainer container;
		private Builder() {}
		public Builder format(JsonInMemoryFormat format) {
			this.format = format;
			return this;
		}
		public Builder container(JsonContainer container) {
			this.container = container;
			return this;
		}
		@Override
		protected JsonContainerTarget _build() {
			return new JsonContainerTarget(this);
		}
	}	
	public static Builder newBuilder() {
		return new Builder();
	}

	private JsonInMemoryFormat format;
	private JsonContainer container;
	// RECORDSWITHKEYS: index of the entries by (collection,id), built by init() from
	// the current container content
	private Map<List<String>,JsonObject> keyIndex;

	protected JsonContainerTarget(Builder builder) {
		super(builder);
		this.format = builder.format;
		this.container = builder.container;
		if(container==null) {
			switch(format) {
				case RECORDS, RECORDSWITHKEYS -> {
					container = JsonArray.create();
				}
				case RECORDSBYKEY, RECORDSBYCOL, RECORDSBYCOLKEY -> {
					container = JsonObject.create();
				}
			}
		}
	}

	public JsonContainer getContainer() {
		return container;
	}

	@Override
	public void init() {
		keyIndex = null;
	}
	
	private Map<List<String>,JsonObject> getKeyIndex() {
		if(keyIndex==null) {
			keyIndex = new HashMap<>();
			for(Object o: (JsonArray)container) {
				if(o instanceof JsonObject e) {
					keyIndex.put(Arrays.asList(e.getString("collection"),e.getString("id")), e);
				}
			}
		}
		return keyIndex;
	}

	@Override
	public void close() {
	}

	@Override
	public synchronized void saveJsonContent(JsonContent content) {
		switch(content.getType()) {
			case RECORD -> {
				switch(format) {
					case RECORDS -> {
						JsonArray records = (JsonArray)container;
						records.add(content.getJson());
					}
					case RECORDSWITHKEYS -> {
						// Upsert: a key that is already in the container gets its value replaced
						List<String> key = Arrays.asList(content.getKey().getCollection(), content.getKey().getId());
						JsonObject existing = getKeyIndex().get(key);
						if(existing!=null) {
							existing.put("value", content.getJson());
						} else {
							JsonArray records = (JsonArray)container;
							JsonObject json = JsonObject.of("collection", content.getKey().getCollection(), "id", content.getKey().getId(), "value", content.getJson()); 
							records.add(json);
							getKeyIndex().put(key, json);
						}
					}
					case RECORDSBYCOL -> {
						JsonObject collections = (JsonObject)container;
						JsonArray objects = collections.getOrCreateArray(StringUtil.nonNull(content.getKey().getCollection()));
						objects.add(content.getJson());
					}
					case RECORDSBYKEY -> {
						JsonObject objects = (JsonObject)container;
						objects.put(StringUtil.nonNull(content.getKey().getId()),content.getJson());
					}
					case RECORDSBYCOLKEY -> {
						JsonObject collections = (JsonObject)container;
						JsonObject objects = collections.getOrCreateObject(StringUtil.nonNull(content.getKey().getCollection()));
						objects.put(StringUtil.nonNull(content.getKey().getId()),content.getJson());
					}
				}
			}
			case DELETION -> {
				switch(format) {
					case RECORDS -> {
						// Can't delete, no key...
					}
					case RECORDSWITHKEYS -> {
						JsonArray objects = (JsonArray)container;
						String col = content.getKey().getCollection();
						String id = content.getKey().getId();
						getKeyIndex().remove(Arrays.asList(col,id));
						for(int i=0; i<objects.size(); ) {
							JsonObject o = objects.getObject(i);
							if(StringUtil.equals(col, o.getString("collection")) && StringUtil.equals(id, o.getString("id")) ) {
								objects.remove(i);
							} else {
								i++;
							}
						}
					}
					case RECORDSBYCOL -> {
						// Can't delete, no key...
					}
					case RECORDSBYKEY -> {
						JsonObject objects = (JsonObject)container;
						objects.remove(StringUtil.nonNull(content.getKey().getId()));
					}
					case RECORDSBYCOLKEY -> {
						JsonObject collections = (JsonObject)container;
						JsonObject objects = collections.getObject(StringUtil.nonNull(content.getKey().getCollection()));
						if(objects!=null) {
							objects.remove(StringUtil.nonNull(content.getKey().getId()));
						}
					}
				}
			}
		}
	}
}
