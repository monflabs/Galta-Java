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
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
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
	// The zip is written to a temporary file, moved to the root when the import succeeds
	private Path tempFile;
	private OutputStream os;
	private ZipOutputStream zipOs;
	// The entries written by the current import
	private final Set<String> entries = new HashSet<>();

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

	/**
	 * Starts writing the zip file. The content is written to a temporary file next to the
	 * root, which replaces the root only when the import succeeds: a failed (or cancelled)
	 * import leaves the previous zip file, if any, untouched.
	 */
	@Override
	public void init() {
		File root = getRoot();
		entries.clear();
		try {
			if(root.isDirectory()) {
				throw new JsonException(null, "{0} is a directory, not a zip file", root.getPath());
			}
			Path target = root.toPath().toAbsolutePath();
			Path parent = target.getParent();
			if(parent!=null) {
				Files.createDirectories(parent);
			}
			tempFile = Files.createTempFile(parent, "."+target.getFileName().toString()+".", ".tmp");
			os =  new FastBufferedOutputStream(Files.newOutputStream(tempFile));
			this.zipOs = new ZipOutputStream(os);
		} catch (JsonException e) {
			throw e;
		} catch (Exception e) {
			discard();
			throw new JsonException(e, "Error while opening ZipFile {0}", root.getPath());
		}
	}

	/**
	 * Finishes the zip file and moves it to the root.
	 */
	@Override
	public void close() {
		// Must be safe when init() failed, and when called more than once
		Path tmp = tempFile;
		try {
			closeStreams();
			if(tmp!=null) {
				tempFile = null;
				moveAtomically(tmp, getRoot().toPath().toAbsolutePath());
			}
		} catch(IOException ex) {
			throw new JsonException(ex,"Error while closing the zip file {0}", getRoot().getPath());
		} finally {
			deleteQuietly(tmp);
		}
	}

	/**
	 * Discards the zip being written: the root is not changed.
	 */
	@Override
	protected void closeOnFailure() {
		discard();
	}

	private void discard() {
		Path tmp = tempFile;
		tempFile = null;
		try {
			closeStreams();
		} catch(IOException ex) {
			// The file is discarded anyway
		} finally {
			deleteQuietly(tmp);
		}
	}

	private void closeStreams() throws IOException {
		try {
			if(zipOs!=null) {
				ZipOutputStream z = zipOs;
				zipOs = null;
				z.finish();
			}
		} finally {
			OutputStream o = os;
			os = null;
			IOStreamUtil.close(o);
		}
	}

	static void moveAtomically(Path from, Path to) throws IOException {
		try {
			Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch(AtomicMoveNotSupportedException ex) {
			Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static void deleteQuietly(Path p) {
		if(p!=null) {
			try {
				Files.deleteIfExists(p);
			} catch(IOException ex) {
				// Best effort
			}
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
			if(!entries.add(entryName)) {
				throw new JsonException(null,"Duplicate document {0}: the zip entry {1} was already written by this import (a document cannot be written twice to a zip file, and the ids must be unique when the collections are ignored)", content.getKey(), entryName);
			}
			try {
				ZipEntry e = new ZipEntry(entryName);
				// Keep the content timestamp, which the zip sources read back (the zip format
				// stores it with a precision of 1 second, or 2 seconds for the MS-DOS time)
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
