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

import java.util.function.Supplier;
import java.util.stream.Stream;

import org.monflabs.json.impexp.JsonContent;
import org.monflabs.json.impexp.impl.JsonSourceImpl;
import org.monflabs.json.impexp.replication.RangeFilter;
import org.monflabs.util.ObjectBuilder;

public class JsonContentStreamSource extends JsonSourceImpl {

	public static class Builder extends ObjectBuilder<JsonContentStreamSource> {
		private Supplier<Stream<JsonContent>> streamFactory;
		private int estimatedCount = -1;
		
		private Builder() {}
		public Builder streamFactory(Supplier<Stream<JsonContent>> streamFactory) {
			this.streamFactory = streamFactory;
			return this;
		}
		public Builder estimatedCount(int estimatedCount) {
			this.estimatedCount = estimatedCount;
			return this;
		}
		@Override
		protected JsonContentStreamSource _build() {
			return new JsonContentStreamSource(this);
		}
	}
	public static Builder newBuilder() {
		return new Builder();
	}


	private Supplier<Stream<JsonContent>> streamFactory;
	private int estimatedCount;
	
	private Stream<JsonContent> stream;
	
	protected JsonContentStreamSource(Builder builder) {
		this.streamFactory = builder.streamFactory;
		this.estimatedCount = builder.estimatedCount;
	}
	
	@Override
	public long estimatedCount() {
		return estimatedCount;
	}

	@Override
	public void init(RangeFilter filter) {
		super.init(filter);
		
		stream = streamFactory.get();
	}

	@Override
	public void close() {
		// Like StreamSource: the stream from the factory is closed with the source, even
		// when it was never consumed
		Stream<JsonContent> st = stream;
		stream = null;
		if(st!=null) {
			st.close();
		}
	}

	@Override
	protected Stream<JsonContent> createJsonContentStream() {
		if(stream==null) {
			return Stream.empty();
		}
		stream.onClose(this::close);
		return stream;
	}

}
