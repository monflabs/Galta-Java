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
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.impexp.JsonContent;
import org.monflabs.json.impexp.JsonKey;
import org.monflabs.json.impexp.impl.JsonSourceImpl;
import org.monflabs.json.impexp.util.FileNameUtil;
import org.monflabs.json.impexp.util.StaticContent;
import org.monflabs.util.IOStreamUtil;
import org.monflabs.util.ObjectBuilder;
import org.monflabs.util.io.ZipUtil;

public class ZipInputStreamSource extends JsonSourceImpl implements FileBase {
	
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
	private boolean ignoreCollection;
	private boolean estimateCount;
	private long estimatedCount = -1;
	
	private ZipInputStream zis;
	
	protected ZipInputStreamSource(Builder builder) {
		this.supplier = builder.supplier;
		this.ignoreCollection = builder.ignoreCollection;
		this.estimateCount = builder.estimateCount;
	}

	public boolean isIgnoreCollection() {
		return ignoreCollection;
	}

	public boolean isEstimateCount() {
		return estimateCount;
	}
	
	@Override
	public void close() {
		if(zis!=null) {
			IOStreamUtil.close(zis);
			zis = null;
		}
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
				if(ZipUtil.shouldIgnore(ze)) {
					continue;
				}
				String path = ze.getName();
				if(path.endsWith(".json")) {
					count++;
				}
			}
			return count;
		}
	}

	@Override
	public Iterator<JsonContent> createJsonContentIterator() {
		if(zis!=null) {
			IOStreamUtil.close(zis);
			zis = null;
		}
		zis = new ZipInputStream(supplier.get());
		return new ZipIterator(zis);
	}

	private class ZipIterator implements Iterator<JsonContent> {
		private ZipInputStream zis;
		
		private boolean hasNextValue;
		private JsonContent next;
		
		ZipIterator(ZipInputStream zis) {
			this.zis = zis;
		}
		
		private boolean moveToNext() {
			while(true) {
				ZipEntry ze = null;
				try {
					ze = zis.getNextEntry();
					if(ze==null) {
						next = null;
						return false;
					}
				} catch(IOException ex) {
					throw new JsonException(ex);
				}
				if(ZipUtil.shouldIgnore(ze)) {
					continue;
				}
				if(!ze.isDirectory()) {  // Must be a file
					String path = ze.getName();
					String docKey = getFileName(path);
					if(docKey.endsWith(".json")) {
						// Extract the collection name
						String collection = null;
						if(!isIgnoreCollection()) {
							collection = FileNameUtil.collectionFromZipPath(path);
						}
						try {
							docKey = FileNameUtil.decodeFilename(docKey.substring(0,docKey.length()-".json".length()));
							// Not sure if this can be executed in parallel
							// Let's be conservative for now
							Object json = JsonFactory.get().parse(new InputStreamReader(zis,StandardCharsets.UTF_8));
							next = new StaticContent(JsonKey.of(collection, docKey), json, zipTime(ze));
							return true;
						} catch (Exception e) {
							throw new JsonException(e, "Error while iterating ZipFile, entry {0}", path);
						}
					}
				}
			}
		}

		@Override
		public boolean hasNext() {
			if (!hasNextValue) {
				hasNextValue = moveToNext();
			}
			return hasNextValue;
		}

		@Override
		public JsonContent next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			hasNextValue = false;
			return next;
		}
	}

	private static String getFileName(String path) {
		int idx = path.lastIndexOf('/');
		if(idx>=0) {
			return path.substring(idx+1);
		}
		return path;
	}


	// ZipEntry.getTime() is -1 when the entry has no timestamp
	private static Instant zipTime(ZipEntry ze) {
		long t = ze.getTime();
		return t>=0 ? Instant.ofEpochMilli(t) : null;
	}
}
