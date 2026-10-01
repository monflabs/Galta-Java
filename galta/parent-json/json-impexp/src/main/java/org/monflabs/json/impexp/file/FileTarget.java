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
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.impexp.JsonContent;
import org.monflabs.json.impexp.JsonKey;
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
	// The documents written by the current import
	private final Map<String,JsonKey> written = new HashMap<>();
	// When the root is cleared on start: the folder the documents are written to
	private File stagingDir;

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

	/**
	 * Starts the import.
	 * <p>
	 * When the root is cleared on start, the documents are written to a new folder next to
	 * the root, which replaces the root only when the import succeeds: a failed (or
	 * cancelled) import leaves the previous content untouched. Otherwise, the documents are
	 * written in place, each one atomically (to a temporary file then moved).
	 */
	@Override
	public void init() {
		collisions.clear();
		written.clear();
		File root = getRoot();
		if(root.exists() && !root.isDirectory()) {
			throw new JsonException(null,"{0} is not a directory",root.getPath());
		}
		if(clearOnStart) {
			Path r = root.toPath().toAbsolutePath();
			try {
				Files.createDirectories(r.getParent());
				stagingDir = Files.createTempDirectory(r.getParent(), "."+r.getFileName()+".").toFile();
			} catch(IOException ex) {
				throw new JsonException(ex,"Error while creating a temporary folder next to {0}",root.getPath());
			}
		} else {
			root.mkdirs();
			if(!root.isDirectory()) {
				throw new JsonException(null,"Cannot create the directory {0}",root.getPath());
			}
		}
	}

	/**
	 * Ends the import: when the root is cleared on start, the new content replaces it.
	 */
	@Override
	public void close() {
		File staging = stagingDir;
		if(staging!=null) {
			stagingDir = null;
			try {
				replaceRoot(staging.toPath());
			} catch(IOException ex) {
				throw new JsonException(ex,"Error while replacing {0} with the exported content",getRoot().getPath());
			} finally {
				if(staging.exists()) {
					FileUtil.deleteFile(staging);
				}
			}
		}
	}

	/**
	 * Discards the new content: the root is not changed.
	 */
	@Override
	protected void closeOnFailure() {
		File staging = stagingDir;
		stagingDir = null;
		if(staging!=null) {
			FileUtil.deleteFile(staging);
		}
	}

	private void replaceRoot(Path staging) throws IOException {
		Path r = getRoot().toPath().toAbsolutePath();
		if(Files.exists(r, LinkOption.NOFOLLOW_LINKS)) {
			// Moved aside first, and only deleted once the new content is in place
			Path old = r.resolveSibling("."+r.getFileName()+".old-"+UUID.randomUUID());
			Files.move(r, old);
			try {
				Files.move(staging, r);
			} catch(IOException ex) {
				Files.move(old, r);
				throw ex;
			}
			FileUtil.deleteFile(old.toFile());
		} else {
			Files.move(staging, r);
		}
	}

	@Override
	public synchronized void saveJsonContent(JsonContent content) {
		String key = content.getKey().getId();
		boolean inPlace = stagingDir==null;
		File parent = inPlace ? getRoot() : stagingDir;
		String folder = null;
		if(!isIgnoreCollection()) {
			String collection = content.getKey().getCollection();
			if(StringUtil.isNotEmpty(collection)) {
				folder = FileNameUtil.encodeCollectionFolder(collection);
				FileNameUtil.checkFileNameLength(folder);
				parent = new File(parent,folder);
			}
		}
		String fileName = FileNameUtil.encodeFilename(key)+".json";
		FileNameUtil.checkFileNameLength(fileName);
		String subpath = subdirLevels>0 ? FilenameHash.hash(key, subdirLevels) : null;
		// The path relative to the root, used to detect the collisions
		String relPath = (folder!=null ? folder+"/" : "") + (subpath!=null ? subpath+"/" : "") + fileName;
		if(content.getType()==TYPE.RECORD) {
			JsonKey previous = written.put(relPath, content.getKey());
			if(previous!=null && !previous.equals(content.getKey())) {
				throw new JsonException(null,"Document {0} would overwrite document {1} (file {2}): the ids must be unique when the collections are ignored", content.getKey(), previous, relPath);
			}
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
				Object json = content.getJson();
				if(inPlace) {
					// Written to a temporary file then moved, so a failure cannot leave a
					// truncated document
					Path tmp = null;
					try {
						tmp = Files.createTempFile(parent.toPath(), "."+fileName+".", ".tmp");
						writeJson(tmp.toFile(), json);
						ZipTarget.moveAtomically(tmp, doc.toPath());
					} catch(IOException ex) {
						throw new JsonException(ex,"Error while writing JSON file {0}", doc);
					} finally {
						if(tmp!=null) {
							tmp.toFile().delete();
						}
					}
				} else {
					try {
						writeJson(doc, json);
					} catch(IOException ex) {
						throw new JsonException(ex,"Error while writing JSON file {0}", doc);
					}
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
				written.remove(relPath);
				doc.delete();
			}
		}
	}

	private static void writeJson(File file, Object json) throws IOException {
		try(Writer fw = new FastBufferedWriter(new FileWriter(file,StandardCharsets.UTF_8))) {
			JsonFactory.get().stringify(fw, json);
		}
	}
}
