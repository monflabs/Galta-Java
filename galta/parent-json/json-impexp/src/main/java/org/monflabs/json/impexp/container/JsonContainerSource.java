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

import java.time.Instant;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonContainer;
import org.monflabs.json.JsonObject;
import org.monflabs.json.impexp.JsonContent;
import org.monflabs.json.impexp.JsonKey;
import org.monflabs.json.impexp.impl.JsonSourceImpl;
import org.monflabs.json.impexp.util.StaticContent;
import org.monflabs.util.ObjectBuilder;
import org.monflabs.util.iterators.Iterators;

public class JsonContainerSource extends JsonSourceImpl {

	public static class Builder extends ObjectBuilder<JsonContainerSource> {
		private JsonInMemoryFormat format = JsonInMemoryFormat.RECORDS;
		private JsonContainer container;
		private Function<Object,String> collectionFunction;
		private Function<Object,String> keyFunction;
		private Function<Object,Instant> timestampFunction;
		
		private Builder() {}
		public Builder format(JsonInMemoryFormat format) {
			this.format = format;
			return this;
		}
		public Builder container(JsonContainer container) {
			this.container = container;
			return this;
		}
		public Builder collectionFunction(Function<Object,String> collectionFunction) {
			this.collectionFunction = collectionFunction;
			return this;
		}
		public Builder keyFunction(Function<Object,String> keyFunction) {
			this.keyFunction = keyFunction;
			return this;
		}
		public Builder timestampFunction(Function<Object,Instant>timestampFunction) {
			this.timestampFunction = timestampFunction;
			return this;
		}
		@Override
		protected JsonContainerSource _build() {
			return new JsonContainerSource(this);
		}
	}
	public static Builder newBuilder() {
		return new Builder();
	}


	private JsonInMemoryFormat format;
	private JsonContainer container;
	private Function<Object,String> collectionFunction;
	private Function<Object,String> keyFunction;
	private Function<Object,Instant> timestampFunction;
	
	protected JsonContainerSource(Builder builder) {
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
		this.collectionFunction = builder.collectionFunction;
		this.keyFunction = builder.keyFunction;
		this.timestampFunction = builder.timestampFunction;
	}

	public JsonContainer getContainer() {
		return container;
	}

	@Override
	public long estimatedCount() {
		int count = 0;
		switch(format) {
			case RECORDS, RECORDSWITHKEYS, RECORDSBYKEY -> {
				count = container.size();
			}
			case RECORDSBYCOL, RECORDSBYCOLKEY -> {
				JsonObject cols = (JsonObject)container;
				for(Object o: cols.values()) {
					count += ((JsonContainer)o).size();
				}
			}
		}
		return count;
	}
	
	@Override
	protected Iterator<JsonContent> createJsonContentIterator() {
		// TODO: this should be enhance to better use spliterator() and support parallel processing
		// Do not use a sequential iterator, but wrap the spliterator()
		switch(format) {
			case RECORDS -> 		{ return iteratorRecord(); }
			case RECORDSWITHKEYS ->	{ return iteratorRecordWithKeys(); }
			case RECORDSBYCOL -> 	{ return iteratorRecordByCol(); }
			case RECORDSBYKEY -> 	{ return iteratorRecordByKey(); }
			case RECORDSBYCOLKEY -> { return iteratorRecordByColKey(); }
			default -> 				{ return null; }
		}
	}

	//
	public Iterator<JsonContent> iteratorRecord() {
		JsonArray records = (JsonArray)container;

		AtomicLong indexCounter = new AtomicLong();
		Iterator<JsonContent> it = Iterators.map(records.iterator(), (o) -> {
			long index = indexCounter.getAndIncrement();
			String col = getCollection(index, o);
			String key = getKey(index, o);
			Object value = o;
			Instant timestamp = getTimestamp(index, o);
			return new StaticContent(JsonKey.of(col, key), value, timestamp);
		});
		return it;
	}
	public Iterator<JsonContent> iteratorRecordWithKeys() {
		JsonArray records = (JsonArray)container;

		AtomicLong indexCounter = new AtomicLong();
		Iterator<JsonContent> it = Iterators.map(records.iterator(), (o) -> {
			long index = indexCounter.getAndIncrement();
			JsonObject json = (JsonObject)o;
			String col = json.getString("collection");
			String key = json.getString("id");
			Object value = json.get("value");
			Instant timestamp = getTimestamp(index, o);
			return new StaticContent(JsonKey.of(col, key), value, timestamp);
		});
		return it;
	}
	public Iterator<JsonContent> iteratorRecordByCol() {
		JsonObject collections = (JsonObject)container;

		Iterator<JsonContent> it = Iterators.nested(collections.entrySet().iterator(), (oe) -> {
			String col = oe.getKey();
			AtomicLong indexCounter = new AtomicLong();
			JsonArray records = (JsonArray)oe.getValue();
			return Iterators.map(records.iterator(), (o) -> {
				long index = indexCounter.getAndIncrement();
				String key = getKey(index, o);
				Object value = o;
				Instant timestamp = getTimestamp(index, o);
				return new StaticContent(JsonKey.of(col, key), value, timestamp);
			});
		});
		return it;
	}
	public Iterator<JsonContent> iteratorRecordByKey() {
		JsonObject records = (JsonObject)container;

		AtomicLong indexCounter = new AtomicLong();
		Iterator<JsonContent> it = Iterators.map(records.entrySet().iterator(), (ce) -> {
			long index = indexCounter.getAndIncrement();
			String col = getCollection(index, ce.getValue());
			String key = ce.getKey();
			Object value = ce.getValue();
			Instant timestamp = getTimestamp(index, ce.getValue());
			return new StaticContent(JsonKey.of(col, key), value, timestamp);
		});
		return it;
	}
	public Iterator<JsonContent> iteratorRecordByColKey() {
		JsonObject collections = (JsonObject)container;

		Iterator<JsonContent> it = Iterators.nested(collections.entrySet().iterator(), (ce) -> {
			String col = ce.getKey();
			JsonObject records = (JsonObject)ce.getValue();
			AtomicLong indexCounter = new AtomicLong();
			return Iterators.map(records.entrySet().iterator(), (oe) -> {
				long index = indexCounter.getAndIncrement();
				String key = oe.getKey();
				Object value = oe.getValue();
				Instant timestamp = getTimestamp(index, oe.getValue());
				return new StaticContent(JsonKey.of(col, key), value, timestamp);
			});
		});
		return it;
	}

	protected String getCollection(long index, Object o) {
		if(collectionFunction!=null) {
			return collectionFunction.apply(o);
		}
		return null;
	}
	protected String getKey(long index, Object o) {
		if(keyFunction!=null) {
			return keyFunction.apply(o);
		}
		return Long.toString(index);
	}
	protected Instant getTimestamp(long index, Object o) {
		if(timestampFunction!=null) {
			return timestampFunction.apply(o);
		}
		return null;
	}
}
