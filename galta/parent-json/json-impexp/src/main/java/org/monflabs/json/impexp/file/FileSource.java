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
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

import org.monflabs.json.JsonException;
import org.monflabs.json.impexp.JsonContent;
import org.monflabs.json.impexp.JsonKey;
import org.monflabs.json.impexp.impl.JsonSourceImpl;
import org.monflabs.json.impexp.replication.RangeFilter;
import org.monflabs.json.impexp.util.FileNameUtil;
import org.monflabs.util.ObjectBuilder;
import org.monflabs.util.iterators.Iterators;

public class FileSource extends JsonSourceImpl implements FileBase {
	
	public static class Builder extends ObjectBuilder<FileSource> {
		private File root;
		private boolean ignoreCollection;
		private boolean estimateCount;
		
		private Builder() {}
		public Builder root(File root) {
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
		protected FileSource _build() {
			return new FileSource(this);
		}
	}
	public static Builder newBuilder() {
		return new Builder();
	}

	private File root;
	private boolean ignoreCollection;
	private boolean estimateCount;
	private Integer estimatedCount;
	
	protected FileSource(Builder builder) {
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
	public long estimatedCount() {
		if(estimatedCount==null) {
			estimatedCount = -1;
			if(isEstimateCount()) {
				try {
					estimatedCount = countInFolder(getRoot(), newVisited(getRoot()));
				} catch(Exception e) {}
			}
		}
		return estimatedCount;
	}
	/**
	 * The set of the (real paths of the) folders already visited, starting with the root.
	 * A folder reached again through a symbolic link is skipped, so a link loop cannot
	 * make the iteration infinite, and a document is never produced twice.
	 */
	private static Set<Path> newVisited(File root) {
		Set<Path> visited = new HashSet<>();
		enter(root, visited);
		return visited;
	}
	private static boolean enter(File folder, Set<Path> visited) {
		Path p;
		try {
			p = folder.toPath().toRealPath();
		} catch(IOException ex) {
			// A dangling link, or a folder that cannot be read
			return false;
		}
		return visited.add(p);
	}
	private int countInFolder(File folder, Set<Path> visited) throws JsonException, IOException {
		int count = 0;
		File[] files = listFiles(folder);
		for(int i=0; i<files.length; i++) {
			File file = files[i];
			if(file.isDirectory()) {
				if(enter(file, visited)) {
					count += countInFolder(file, visited);
				}
			} else if(file.isFile() && file.getPath().endsWith(".json")) {
				count++;
			}
		}
		return count;
	}
	

	@Override
	public void init(RangeFilter filter) {
		super.init(filter);
		
		File root = getRoot();
		if(!root.exists()) {
			throw new JsonException(null,"Root directory {0} does not exist",root.getPath());
		}
		if(!root.isDirectory()) {
			throw new JsonException(null,"File {0} is not a directory",root.getPath());
		}
	}

	// File.listFiles() order is unspecified and differs between platforms: sort
	// the entries so the documents are always produced in the same order
	private static File[] listFiles(File folder) {
		File[] files = folder.listFiles();
		if(files==null) {
			throw new JsonException(null,"Cannot list the content of directory {0}",folder.getPath());
		}
		Arrays.sort(files, (f1,f2) -> f1.getName().compareTo(f2.getName()));
		return files;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public Iterator<JsonContent> createJsonContentIterator() {
		return (Iterator)Iterators.flatten( new FileIterator(getRoot(), 0, null, newVisited(getRoot())), null);
	}
	
	private class FileIterator implements Iterator<Object> {
		private int index;
		private String collection;
		
		private int iteratorIndex;
		private File[] files;
		private boolean hasNextValue;
		private Object next;
		
		private Set<Path> visited;
		
		FileIterator(File folder, int index, String collection, Set<Path> visited) {
			this.visited = visited;
			this.index = index;
			this.collection = collection;
			this.files = listFiles(folder);
			hasNextValue = moveToNext();
		}
		
		private boolean moveToNext() {
			while(iteratorIndex<files.length) {
				File file = files[iteratorIndex++];
				if(file.isDirectory()) {
					if(!enter(file, visited)) {
						continue;
					}
					String col = collection;
					if(!isIgnoreCollection() && index==0) {
						String c = FileNameUtil.decodeCollectionFolder(file.getName());
						if(c!=null) {
							col = c;
						}
					}
					next = new FileIterator(file, index+1, col, visited);
					return true;
				} else if(file.isFile()) {
					String docKey = file.getName();
					if(docKey.endsWith(".json")) {
						docKey = FileNameUtil.decodeFilename(docKey.substring(0,docKey.length()-".json".length()));
						next = new FileContent(JsonKey.of(collection,docKey), file);
						return true;
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
		public Object next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			hasNextValue = false;
			return next;
		}
	}	
}
