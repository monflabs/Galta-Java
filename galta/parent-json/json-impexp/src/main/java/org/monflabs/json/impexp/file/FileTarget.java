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
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.impexp.JsonContent;
import org.monflabs.json.impexp.JsonContent.TYPE;
import org.monflabs.json.impexp.impl.JsonTargetImpl;
import org.monflabs.json.impexp.util.FileNameUtil;
import org.monflabs.util.FileUtil;
import org.monflabs.util.StringUtil;
import org.monflabs.util.io.FastBufferedWriter;

public class FileTarget extends JsonTargetImpl implements FileBase {
	
	public static class Builder extends TargetBuilder<FileTarget,Builder> {
		private File root;
		private boolean ignoreCollection;
		private int subdirLevels;
		private boolean clearOnStart = true;
		private boolean keepTimestamp;
		
		private Builder() {}
		public Builder root(File root) {
			this.root = root;
			return this;
		}
		public Builder ignoreCollection(boolean ignoreCollection) {
			this.ignoreCollection = ignoreCollection;
			return this;
		}
		public Builder subdir(int levels) {
			this.subdirLevels = levels;
			return this;
		}
		public Builder clearOnStart(boolean clearOnStart) {
			this.clearOnStart = clearOnStart;
			return this;
		}
		public Builder keepTimestamp(boolean keepTimestamp) {
			this.keepTimestamp = keepTimestamp;
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
		protected FileTarget _build() {
			return new FileTarget(this);
		}
	}	
	public static Builder newBuilder() {
		return new Builder();
	}

	private File root;
	private boolean ignoreCollection;
	private int subdirLevels;
	private boolean clearOnStart;
	private boolean keepTimestamp;
	// Names written by the current import, to detect the ones that only differ by their case
	private final FileNameUtil.CaseCollisionDetector collisions = new FileNameUtil.CaseCollisionDetector();

	protected FileTarget(Builder builder) {
		super(builder);
		this.root = builder.root;
		this.ignoreCollection = builder.ignoreCollection;
		this.subdirLevels = builder.subdirLevels;
		this.clearOnStart = builder.clearOnStart;
		this.keepTimestamp = builder.keepTimestamp;
	}
	
	public File getRoot() {
		return root;
	}

	@Override
	public boolean supportsDeletions() {
		return true;
	}

	public boolean isIgnoreCollection() {
		return ignoreCollection;
	}

	public boolean isClearOnStart() {
		return clearOnStart;
	}

	@Override
	public void init() {
		collisions.clear();
		FileUtil.prepareDirectory(getRoot(),clearOnStart);
	}

	@Override
	public void close() {
	}

	@Override
	public synchronized void saveJsonContent(JsonContent content) {
		String key = content.getKey().getId();
		File parent = getRoot();
		String folder = null;
		if(!isIgnoreCollection()) {
			String collection = content.getKey().getCollection();
			if(StringUtil.isNotEmpty(collection)) {
				folder = FileNameUtil.encodeCollectionFolder(collection);
				parent = new File(parent,folder);
			}
		}
		String fileName = FileNameUtil.encodeFilename(key)+".json";
		String subpath = subdirLevels>0 ? FilenameHash.hash(key, subdirLevels) : null;
		// The path relative to the root, used to detect the case collisions
		String relPath = (folder!=null ? folder+"/" : "") + (subpath!=null ? subpath+"/" : "") + fileName;
		if(content.getType()==TYPE.RECORD) {
			// Two collections only differing by their case would share a folder
			if(folder!=null) {
				collisions.register(folder+"/");
			}
			collisions.register(relPath);
			parent.mkdirs();
		}
		if(subpath!=null) {
			parent = new File(parent,subpath);
			if(content.getType()==TYPE.RECORD) {
				parent.mkdirs();
			}
		}
		File doc = new File(parent,fileName);
		switch(content.getType()) {
			case RECORD -> {
				try(Writer fw = new FastBufferedWriter(new FileWriter(doc,StandardCharsets.UTF_8))) {
					Object json = content.getJson();
					JsonFactory.get().stringify(fw, json);
				} catch(IOException ex) {
					throw new JsonException(ex,"Error while writing JSON file {0}", doc);
				}
				if(keepTimestamp) {
					Instant ts = content.getTimestamp();
					if(ts!=null) {
						doc.setLastModified(ts.toEpochMilli());
					}
				}
			}
			case DELETION -> {
				collisions.unregister(relPath);
				doc.delete();
			}
		}
	}
}
