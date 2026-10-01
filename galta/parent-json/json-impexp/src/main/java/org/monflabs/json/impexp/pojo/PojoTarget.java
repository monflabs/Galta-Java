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
package org.monflabs.json.impexp.pojo;

import java.util.function.Consumer;

import org.monflabs.json.impexp.JsonContent;
import org.monflabs.json.impexp.impl.JsonTargetImpl;

public class PojoTarget<T> extends JsonTargetImpl {
	
	public static class Builder<T> extends TargetBuilder<PojoTarget<T>,Builder<T>> {

		private Consumer<JsonContent> writer;
		private Runnable init;
		private Runnable close;
		
		private Builder() {}
		public Builder<T> writer(Consumer<JsonContent> writer) {
			this.writer = writer;
			return this;
		}
		public Builder<T> init(Runnable init) {
			this.init = init;
			return this;
		}
		public Builder<T> close(Runnable close) {
			this.close = close;
			return this;
		}

		@Override
		protected PojoTarget<T> _build() {
			return new PojoTarget<T>(this);
		}
	}	
	public static <T> Builder<T> newBuilder() {
		return new Builder<T>();
	}

	private Consumer<JsonContent> writer;
	private Runnable init;
	private Runnable close;
	
	protected PojoTarget(Builder<T> builder) {
		super(builder);
		this.writer = builder.writer;
		this.init = builder.init;
		this.close = builder.close;
	}
	
	public Consumer<JsonContent> getWriter() {
		return writer;
	}

	@Override
	public void init() {
		if(init!=null) {
			init.run();
		}
	}

	@Override
	public void close() {
		if(close!=null) {
			close.run();
		}
	}

	/**
	 * The writer receives every content, deletions included, and decides what to do with them.
	 */
	@Override
	public boolean supportsDeletions() {
		return true;
	}

	@Override
	public void saveJsonContent(JsonContent content) {
		if(writer!=null) {
			writer.accept(content);
		}
	}
}
