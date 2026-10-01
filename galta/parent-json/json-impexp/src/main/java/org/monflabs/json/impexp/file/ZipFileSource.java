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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.monflabs.json.JsonException;
import org.monflabs.json.impexp.JsonContent;
import org.monflabs.json.impexp.replication.RangeFilter;
import org.monflabs.util.IOStreamUtil;
import org.monflabs.util.ObjectBuilder;
import org.monflabs.util.io.ZipUtil;

/**
 * Reads the documents of a zip file, as written by {@link ZipTarget}.
 * <p>
 * Every stream opens its own {@link ZipFile}, closed with the stream, so the source can
 * be streamed concurrently.
 */
public class ZipFileSource extends AbstractZipSource {
	
	public static class Builder extends ObjectBuilder<ZipFileSource> {
		private File root;
		private boolean ignoreCollection;
		private boolean estimateCount;
		
		private Builder() {}
		public Builder zipFile(File root) {
			this.root = root;
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
		protected ZipFileSource _build() {
			return new ZipFileSource(this);
		}
	}
	public static Builder newBuilder() {
		return new Builder();
	}

	
	private File root;
	private volatile Integer estimatedCount;
	
	protected ZipFileSource(Builder builder) {
		super(builder.ignoreCollection, builder.estimateCount);
		this.root = builder.root;
	}
	
	public File getRoot() {
		return root;
	}
	
	@Override
	public void init(RangeFilter filter) {
		super.init(filter);
		
		File root = getRoot();
		if(!root.exists()) {
			throw new JsonException(null,"Root zip file {0} does not exist",root.getPath());
		}
		if(!root.isFile()) {
			throw new JsonException(null,"File {0} is not a file",root.getPath());
		}
	}
	
	@Override
	public long estimatedCount() {
		if(estimatedCount==null) {
			int count = -1;
			if(isEstimateCount()) {
				try (ZipFile zf = new ZipFile(getRoot())) {
					count = countDocuments(zf);
				} catch(Exception e) {}
			}
			estimatedCount = count;
		}
		return estimatedCount;
	}
	private static int countDocuments(ZipFile zipFile) {
		int count = 0;
		for(Enumeration<? extends ZipEntry> en=zipFile.entries(); en.hasMoreElements(); ) {
			if(isDocument(en.nextElement())) {
				count++;
			}
		}
		return count;
	}
	
	@Override
	protected Stream<JsonContent> createJsonContentStream(RangeFilter filter) {
		ZipFile zipFile;
		try {
			zipFile = new ZipFile(getRoot());
		} catch (Exception e) {
			throw new JsonException(e, "Error while opening ZipFile {0}", getRoot().getPath());
		}
		Enumeration<? extends ZipEntry> en = zipFile.entries();
		DocumentIterator it = new DocumentIterator() {
			@Override
			protected JsonContent readNext() {
				while(en.hasMoreElements()) {
					ZipEntry ze = en.nextElement();
					if(isDocument(ze)) {
						try (InputStream is = zipFile.getInputStream(ze)) {
							return readDocument(ze, is);
						} catch (Exception e) {
							throw new JsonException(e, "Error while iterating ZipFile {0}, entry {1}", getRoot().getPath(), ze.getName());
						}
					}
				}
				return null;
			}
		};
		Spliterator<JsonContent> spit = Spliterators.spliteratorUnknownSize(it, Spliterator.NONNULL); 
		return StreamSupport.stream(spit, false).onClose(() -> IOStreamUtil.close(zipFile));
	}

	public static boolean shouldIgnore(ZipEntry ze) {
		return ZipUtil.shouldIgnore(ze);
	}
}
