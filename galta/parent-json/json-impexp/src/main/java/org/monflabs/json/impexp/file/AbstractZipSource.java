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
import java.util.zip.ZipEntry;

import org.monflabs.json.JsonFactory;
import org.monflabs.json.impexp.JsonContent;
import org.monflabs.json.impexp.JsonKey;
import org.monflabs.json.impexp.impl.JsonSourceImpl;
import org.monflabs.json.impexp.util.FileNameUtil;
import org.monflabs.json.impexp.util.StaticContent;
import org.monflabs.util.io.ZipUtil;

/**
 * Common code of the zip sources: the layout of the entries is the one written by
 * {@link ZipTarget}.
 */
public abstract class AbstractZipSource extends JsonSourceImpl implements FileBase {

	private final boolean ignoreCollection;
	private final boolean estimateCount;

	protected AbstractZipSource(boolean ignoreCollection, boolean estimateCount) {
		this.ignoreCollection = ignoreCollection;
		this.estimateCount = estimateCount;
	}

	public boolean isIgnoreCollection() {
		return ignoreCollection;
	}

	public boolean isEstimateCount() {
		return estimateCount;
	}

	/**
	 * Whether an entry holds a document.
	 */
	protected static boolean isDocument(ZipEntry ze) {
		return !ZipUtil.shouldIgnore(ze) && !ze.isDirectory() && ze.getName().endsWith(".json");
	}

	/**
	 * Reads the document of an entry.
	 */
	protected JsonContent readDocument(ZipEntry ze, InputStream is) throws IOException {
		String path = ze.getName();
		String docKey = getFileName(path);
		String collection = null;
		if(!isIgnoreCollection()) {
			collection = FileNameUtil.collectionFromZipPath(path);
		}
		docKey = FileNameUtil.decodeFilename(docKey.substring(0,docKey.length()-".json".length()));
		Object json = JsonFactory.get().parse(new InputStreamReader(is,StandardCharsets.UTF_8));
		return new StaticContent(JsonKey.of(collection, docKey), json, zipTime(ze));
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

	/**
	 * Iterator reading the next document on demand.
	 */
	protected static abstract class DocumentIterator implements Iterator<JsonContent> {
		private boolean hasNextValue;
		private JsonContent next;

		/** The next document, or null at the end. */
		protected abstract JsonContent readNext();

		@Override
		public boolean hasNext() {
			if (!hasNextValue) {
				next = readNext();
				hasNextValue = next!=null;
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
}
