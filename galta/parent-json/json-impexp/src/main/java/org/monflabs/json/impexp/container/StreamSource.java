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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.monflabs.json.impexp.JsonContent;
import org.monflabs.json.impexp.JsonKey;
import org.monflabs.json.impexp.impl.JsonSourceImpl;
import org.monflabs.json.impexp.replication.RangeFilter;
import org.monflabs.json.impexp.util.StaticContent;
import org.monflabs.util.ObjectBuilder;

public class StreamSource extends JsonSourceImpl {

	public static class Builder extends ObjectBuilder<StreamSource> {
		private Supplier<Stream<Object>> streamFactory;
		private int estimatedCount = -1;
		private Function<Object,String> collectionFunction;
		private Function<Object,String> keyFunction;
		private Function<Object,Object> valueFunction;
		private Function<Object,Instant> timestampFunction;
		
		private Builder() {}
		public Builder streamFactory(Supplier<Stream<Object>> streamFactory) {
			this.streamFactory = streamFactory;
			return this;
		}
		public Builder estimatedCount(int estimatedCount) {
			this.estimatedCount = estimatedCount;
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
		public Builder valueFunction(Function<Object,Object> valueFunction) {
			this.valueFunction = valueFunction;
			return this;
		}
		public Builder timestampFunction(Function<Object,Instant>timestampFunction) {
			this.timestampFunction = timestampFunction;
			return this;
		}
		@Override
		protected StreamSource _build() {
			return new StreamSource(this);
		}
	}
	public static Builder newBuilder() {
		return new Builder();
	}


	private Supplier<Stream<Object>> streamFactory;
	private int estimatedCount;
	private Function<Object,String> collectionFunction;
	private Function<Object,String> keyFunction;
	private Function<Object,Object> valueFunction;
	private Function<Object,Instant> timestampFunction;
	
	private Stream<Object> stream;
	
	protected StreamSource(Builder builder) {
		this.streamFactory = builder.streamFactory;
		this.estimatedCount = builder.estimatedCount;
		this.collectionFunction = builder.collectionFunction;
		this.keyFunction = builder.keyFunction;
		this.valueFunction = builder.valueFunction;
		this.timestampFunction = builder.timestampFunction;
	}

	@Override
	public void init(RangeFilter filter) {
		super.init(filter);
		
		stream = streamFactory.get();
	}

	@Override
	public void close() {
		// Safe when init() was not called, when the factory returned no stream,
		// and when called more than once
		Stream<Object> st = stream;
		stream = null;
		if(st!=null) {
			st.close();
		}
	}
	
	@Override
	public long estimatedCount() {
		return estimatedCount;
	}

	@Override
	protected Stream<JsonContent> createJsonContentStream() {
		if(stream==null) {
			return null;
		}
		AtomicLong indexCounter = new AtomicLong(); 
		Stream<JsonContent> s = stream.map( (o) -> {
			long index = indexCounter.getAndIncrement();
			String col = getCollection(index, o);
			String key = getKey(index, o);
			Object value = getValue(index, o);
			Instant timestamp = getTimestamp(index, o);
			return new StaticContent(JsonKey.of(col, key), value, timestamp);
		});
		return s;

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
	protected Object getValue(long index, Object o) {
		if(valueFunction!=null) {
			return valueFunction.apply(o);
		}
		return o;
	}
	protected Instant getTimestamp(long index, Object o) {
		if(timestampFunction!=null) {
			return timestampFunction.apply(o);
		}
		return null;
	}
}
