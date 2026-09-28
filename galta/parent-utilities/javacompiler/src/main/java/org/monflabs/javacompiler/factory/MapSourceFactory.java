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

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.HashMap;
import java.util.Map;

import org.monflabs.javacompiler.SourceFactory;

public class MapSourceFactory implements SourceFactory {
	
	public static MapSourceFactory of(String name, String content) {
		MapSourceFactory f = new MapSourceFactory();
		f.put(name, content);
		return f;
	}
	
	private Map<String,String> files = new HashMap<>();
	
	public MapSourceFactory() {
	}
	
	@Override
	public String toString() {
		return "/";
	}
	
	@Override
	public boolean isValid() {
		return true;
	}

	
	public Map<String,String> getFiles() {
		return files;
	}
	
	public MapSourceFactory put(String name, String content) {
		getFiles().put(name, content);
		return this;
	}
	
	@Override
	public String readString(String fileName) throws IOException {
		String content = files.get(fileName);
		if(content==null) {
			throw new NoSuchFileException(fileName);
		}
		return content;
	}
	
}
