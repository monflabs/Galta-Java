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
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.impexp.JsonContent;
import org.monflabs.json.impexp.JsonKey;
import org.monflabs.json.impexp.impl.JsonSourceImpl;
import org.monflabs.json.impexp.replication.RangeFilter;
import org.monflabs.json.impexp.util.FileNameUtil;
import org.monflabs.json.impexp.util.StaticContent;
import org.monflabs.util.IOStreamUtil;
import org.monflabs.util.ObjectBuilder;

public class ZipFileSource extends JsonSourceImpl implements FileBase {
	
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
	private boolean ignoreCollection;
	private boolean estimateCount;
	private Integer estimatedCount;
	private ZipFile zipFile;
	
	protected ZipFileSource(Builder builder) {
		this.root = builder.root;
		this.ignoreCollection = builder.ignoreCollection;
		this.estimateCount = builder.estimateCount;
	}
	
	public File getRoot() {
		return root;
	}

	public boolean isIgnoreCollection() {
		return ignoreCollection;
	}

	public boolean isEstimateCount() {
		return estimateCount;
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
		try {
			zipFile = new ZipFile(root);
		} catch (Exception e) {
			throw new JsonException(e, "Error while opening ZipFile {0}", root.getPath());
		}
	}

	@Override
	public void close() {
		if(zipFile!=null) {
			IOStreamUtil.close(zipFile);
			zipFile = null;
		}
	}
	
	@Override
	public long estimatedCount() {
		if(estimatedCount==null) {
			estimatedCount = -1;
			if(isEstimateCount()) {
				try {
					estimatedCount = countDocuments();
				} catch(Exception e) {}
			}
		}
		return estimatedCount;
	}
	private int countDocuments() throws JsonException, IOException {
		// The count can be requested before init() opened the zip file
		if(zipFile!=null) {
			return countDocuments(zipFile);
		}
		try (ZipFile zf = new ZipFile(getRoot())) {
			return countDocuments(zf);
		}
	}
	private static int countDocuments(ZipFile zipFile) {
		int count = 0;
		for(Enumeration<? extends ZipEntry> en=zipFile.entries(); en.hasMoreElements(); ) {
			ZipEntry ze = en.nextElement();
			if(shouldIgnore(ze)) {
				continue;
			}
			String path = ze.getName();
			if(path.endsWith(".json")) {
				count++;
			}
		}
		return count;
	}
	
	@Override
	public Iterator<JsonContent> createJsonContentIterator() {
		return new ZipIterator(zipFile.entries());
	}

	private class ZipIterator implements Iterator<JsonContent> {
		private Enumeration<? extends ZipEntry> en;
		
		private boolean hasNextValue;
		private JsonContent next;
		
		ZipIterator(Enumeration<? extends ZipEntry> en) {
			this.en = en;
		}
		
		private boolean moveToNext() {
			while(en.hasMoreElements()) {
				ZipEntry ze = en.nextElement();
				if(shouldIgnore(ze)) {
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
							Object json;
							try (InputStream is = zipFile.getInputStream(ze)) {
								json = JsonFactory.get().parse(new InputStreamReader(is,StandardCharsets.UTF_8));
							}
							next = new StaticContent(JsonKey.of(collection, docKey), json, zipTime(ze));
							return true;
						} catch (Exception e) {
							throw new JsonException(e, "Error while iterating ZipFile {0}, entry {1}", getRoot().getPath(), path);
						}
					}
				}
			}
			next = null;
			return false;
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
	
	public static boolean shouldIgnore(ZipEntry ze) {
		String path = ze.getName();
		// https://stackoverflow.com/questions/10924236/mac-zip-compress-without-macosx-folder
		if(path.startsWith("__MACOSX/")) {
			return true;
		}
		return false;
	}


	// ZipEntry.getTime() is -1 when the entry has no timestamp
	private static Instant zipTime(ZipEntry ze) {
		long t = ze.getTime();
		return t>=0 ? Instant.ofEpochMilli(t) : null;
	}
}
