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
package org.monflabs.json.config;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.monflabs.util.config.ConfigException;


/**
 * 
 */ 
public class CustomJsonConfig extends AbstractJsonConfig {

	public static class Builder extends ConfigBuilder<CustomJsonConfig,Builder> {
		private Function<String,InputStream> reader;
		private BiFunction<String,Consumer<OutputStream>,InputStream>writer;
		private Builder() {}
		public Builder resourceReader(Function<String,InputStream> reader) {
			this.reader = reader;
			return this;
		}
		public Builder resourceWriter(BiFunction<String,Consumer<OutputStream>,InputStream> writer) {
			this.writer = writer;
			return this;
		}
		@Override
		protected CustomJsonConfig _build() {
			return new CustomJsonConfig(this);
		}
	}
	public static Builder newBuilder() {
		return new Builder();
	}

	private Function<String,InputStream> reader;
	private BiFunction<String,Consumer<OutputStream>,InputStream> writer;

	private CustomJsonConfig(Builder b) {
		super(b);
		this.reader = b.reader;
		this.writer = b.writer;
		load();
	}
	
	@Override
	public InputStream getResource(String path) {
		if(reader!=null) {
			return reader.apply(path);
		}
		return null;
	}
	@Override
	public void setResource(String path, Consumer<OutputStream> save) {
		if(isReadOnly()) {
			throw new ConfigException(null,"Config is readonly");
		}
		writer.apply(path,save);
	}

	@Override
	public boolean isReadOnly() {
		return writer==null;
	}
}
