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
package org.monflabs.json.impexp.file;

import java.io.IOException;
import java.io.InputStream;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.monflabs.json.JsonException;
import org.monflabs.json.impexp.JsonContent;
import org.monflabs.json.impexp.replication.RangeFilter;
import org.monflabs.util.IOStreamUtil;
import org.monflabs.util.ObjectBuilder;

/**
 * Reads the documents of a zip stream, as written by {@link ZipTarget}.
 * <p>
 * Every stream gets its own input stream from the supplier, closed with the stream.
 */
public class ZipInputStreamSource extends AbstractZipSource {
	
	public static class Builder extends ObjectBuilder<ZipInputStreamSource> {
		private Supplier<InputStream> supplier;
		private boolean ignoreCollection;
		private boolean estimateCount;
		
		private Builder() {}
		public Builder zipInputStream(Supplier<InputStream> supplier) {
			this.supplier = supplier;
			return this;
		}
		public Builder ignoreCollection(boolean ignoreCollection) {
			this.ignoreCollection = ignoreCollection;
			return this;
		}
		public Builder estimateCount(boolean estimateCount) {
			this.estimateCount = estimateCount;
			return this;
		}

		@Override
		protected ZipInputStreamSource _build() {
			return new ZipInputStreamSource(this);
		}
	}
	public static Builder newBuilder() {
		return new Builder();
	}

	
	private Supplier<InputStream> supplier;
	private volatile long estimatedCount = -1;
	
	protected ZipInputStreamSource(Builder builder) {
		super(builder.ignoreCollection, builder.estimateCount);
		this.supplier = builder.supplier;
	}
	
	@Override
	public long estimatedCount() {
		if(estimatedCount<0) {
			if(isEstimateCount()) {
				try {
					estimatedCount = countDocuments();
				} catch(Exception e) {}
			}
		}
		return estimatedCount;
	}
	private long countDocuments() throws JsonException, IOException {
		try (ZipInputStream zis = new ZipInputStream(supplier.get())) {
			long count = 0;
			ZipEntry ze = null;
			while((ze = zis.getNextEntry()) != null) {
				if(isDocument(ze)) {
					count++;
				}
			}
			return count;
		}
	}

	@Override
	protected Stream<JsonContent> createJsonContentStream(RangeFilter filter) {
		ZipInputStream zis = new ZipInputStream(supplier.get());
		DocumentIterator it = new DocumentIterator() {
			@Override
			protected JsonContent readNext() {
				while(true) {
					ZipEntry ze = null;
					try {
						ze = zis.getNextEntry();
					} catch(IOException ex) {
						throw new JsonException(ex, "Error while iterating the zip stream");
					}
					if(ze==null) {
						return null;
					}
					if(isDocument(ze)) {
						try {
							return readDocument(ze, zis);
						} catch (Exception e) {
							throw new JsonException(e, "Error while iterating ZipFile, entry {0}", ze.getName());
						}
					}
				}
			}
		};
		Spliterator<JsonContent> spit = Spliterators.spliteratorUnknownSize(it, Spliterator.NONNULL); 
		return StreamSupport.stream(spit, false).onClose(() -> IOStreamUtil.close(zis));
	}
}
