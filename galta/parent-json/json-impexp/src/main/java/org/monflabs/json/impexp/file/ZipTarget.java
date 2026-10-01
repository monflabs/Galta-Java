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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.impexp.JsonContent;
import org.monflabs.json.impexp.JsonContent.TYPE;
import org.monflabs.json.impexp.impl.JsonTargetImpl;
import org.monflabs.json.impexp.util.FileNameUtil;
import org.monflabs.util.IOStreamUtil;
import org.monflabs.util.StringUtil;
import org.monflabs.util.io.FastBufferedOutputStream;

public class ZipTarget extends JsonTargetImpl implements FileBase {
	
	public static class Builder extends TargetBuilder<ZipTarget,Builder>{
		private File root;
		private int subdirLevels;
		private boolean ignoreCollection;
		
		private Builder() {}
		public Builder root(File root) {
			this.root = root;
			return this;
		}
		public Builder subdir(int levels) {
			this.subdirLevels = levels;
			return this;
		}
		public Builder ignoreCollection(boolean ignoreCollection) {
			this.ignoreCollection = ignoreCollection;
			return this;
		}

		@Override
		protected void validate() {
			super.validate();
			if(subdirLevels>4) {
				throw exception("Cannot have more than 4 sub directory levels");
			}
		}
		@Override
		protected ZipTarget _build() {
			return new ZipTarget(this);
		}
	}	
	public static Builder newBuilder() {
		return new Builder();
	}

	private File root;
	private int subdirLevels;
	private boolean ignoreCollection;
	private OutputStream os;
	private ZipOutputStream zipOs;

	protected ZipTarget(Builder builder) {
		super(builder);
		this.root = builder.root;
		this.ignoreCollection = builder.ignoreCollection;
		this.subdirLevels = builder.subdirLevels;
	}
	
	public File getRoot() {
		return root;
	}

	public int getSubdirLevels() {
		return subdirLevels;
	}

	public boolean isIgnoreCollection() {
		return ignoreCollection;
	}

	@Override
	public void init() {
		File root = getRoot();
		try {
			os =  new FastBufferedOutputStream(new FileOutputStream(root));
			this.zipOs = new ZipOutputStream(os);
		} catch (Exception e) {
			throw new JsonException(e, "Error while opening ZipFile {0}", root.getPath());
		}
	}

	@Override
	public void close() {
		// Must be safe when init() failed, and when called more than once
		try {
//			ZipEntry e = new ZipEntry("manifest.json");
//			zipOs.putNextEntry(e);
//				JsonObject o = JsonObject.create();
//				o.put("date", ZonedDateTime.now());
//				JsonFactory.get().stringify(o);
//			zipOs.closeEntry();
			if(zipOs!=null) {
				ZipOutputStream z = zipOs;
				zipOs = null;
				z.finish();
			}
		} catch(IOException ex) {
			throw new JsonException(ex,"Error while closing the zip file");
		} finally {
			OutputStream o = os;
			os = null;
			IOStreamUtil.close(o);
		}
	}

	@Override
	public synchronized void saveJsonContent(JsonContent content) {
		if(content.getType()==TYPE.RECORD) {
			String key = content.getKey().getId();
			String entryName;
			if(subdirLevels>0) {
				String subpath = FilenameHash.hash(key, subdirLevels);
				entryName = subpath + '/' + FileNameUtil.encodeFilename(key)+".json";
			} else {
				entryName = FileNameUtil.encodeFilename(key)+".json";
			}
			if(!isIgnoreCollection()) {
				String collection = content.getKey().getCollection();
				if(StringUtil.isNotEmpty(collection)) {
					entryName = FileNameUtil.encodeCollectionFolder(collection) + '/' + entryName;
				}
			}
			try {
				ZipEntry e = new ZipEntry(entryName);
				// Keep the content timestamp, which the zip sources read back
				Instant ts = content.getTimestamp();
				if(ts!=null) {
					e.setLastModifiedTime(FileTime.from(ts));
				}
				zipOs.putNextEntry(e);
				Writer w = new OutputStreamWriter(zipOs,StandardCharsets.UTF_8);
				Object json = content.getJson();
				JsonFactory.get().stringify(w, json);
				w.flush();
				zipOs.closeEntry();
			} catch(IOException ex) {
				throw new JsonException(ex,"Error while writting zip entry {0}",entryName);
			}
		}
	}
}
