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
package org.monflabs.javacompiler.factory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

import org.monflabs.javacompiler.TargetFactory;


public class MapTargetFactory implements TargetFactory {
	
	// Concurrent: compilations may run while classes are loaded from another thread.
	// Sorted: the files of a package are a range of keys, not a scan of every file.
	private final ConcurrentSkipListMap<String,byte[]> files = new ConcurrentSkipListMap<>();
	
	public MapTargetFactory() {
	}
	
	@Override
	public String toString() {
		return "/";
	}
	
	public Map<String,byte[]> getFiles() {
		return files;
	}
	
	@Override
	public OutputStream openOutputStream(String fileName) {
		return new ByteArrayOutputStream() {
		    @Override
			public void close() throws IOException {
		    	files.put(fileName,toByteArray());
		    }
		};
	}
	@Override
	public byte[] readBytes(String fileName) throws IOException {
		return files.get(fileName);
	}
	@Override
	public boolean delete(String fileName) {
		return files.remove(fileName)!=null;
	}
	@Override
	public Collection<String> listClassFiles(String packageFolder) {
		return listClassFiles(packageFolder, false);
	}

	@Override
	public Collection<String> listClassFiles(String packageFolder, boolean recurse) {
		String prefix = packageFolder.isEmpty() ? "" : packageFolder+"/";
		Collection<String> names = prefix.isEmpty() ? files.keySet() : files.subMap(prefix, true, prefix+Character.MAX_VALUE, false).keySet();
		List<String> result = new ArrayList<>();
		for(String name: names) {
			if(name.endsWith(".class") && (recurse || name.indexOf('/',prefix.length())<0)) {
				result.add(name);
			}
		}
		return result;
	}
}
