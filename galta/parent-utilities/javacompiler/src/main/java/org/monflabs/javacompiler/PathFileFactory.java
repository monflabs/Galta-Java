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
package org.monflabs.javacompiler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import org.monflabs.util.path.FilesUtil;

public class PathFileFactory implements SourceFactory, TargetFactory {
	
	private Path path;
	private Charset cs;
	
	public PathFileFactory(Path path, Charset cs) {
		this.path = path;
		this.cs = cs;
	}
	
	@Override
	public String toString() {
		return path.toString();
	}
	
	@Override
	public boolean isValid() {
		return path!=null && Files.isDirectory(path);
	}
	
	@Override
	public OutputStream openOutputStream(String fileName) {
		try {
			Path p = path.resolve(fileName);
			Files.createDirectories(p.getParent());
			return Files.newOutputStream(p);
		} catch(Exception e) {
			throw new JavaCompilerException(e);
		}
	}
	
	@Override
	public String readString(String fileName) throws IOException {
		Path p = path.resolve(fileName);
		return FilesUtil.readString(p,cs);
	}
	
	@Override
	public byte[] readBytes(String fileName) throws IOException {
		Path p = path.resolve(fileName);
		if(Files.isRegularFile(p)) {
			return Files.readAllBytes(p);
		}
		return null;
	}

	@Override
	public Collection<String> listClassFiles(String packageFolder, boolean recurse) throws IOException {
		if(!recurse) {
			return listClassFiles(packageFolder);
		}
		List<String> result = new ArrayList<>();
		Path dir = packageFolder.isEmpty() ? path : path.resolve(packageFolder);
		if(Files.isDirectory(dir)) {
			try(Stream<Path> s = Files.walk(dir)) {
				s.forEach( p -> {
					if(p.getFileName().toString().endsWith(".class") && Files.isRegularFile(p)) {
						String relative = path.relativize(p).toString();
						String sep = p.getFileSystem().getSeparator();
						result.add(sep.equals("/") ? relative : relative.replace(sep, "/"));
					}
				});
			}
		}
		return result;
	}

	@Override
	public boolean delete(String fileName) throws IOException {
		return Files.deleteIfExists(path.resolve(fileName));
	}

	@Override
	public Collection<String> listClassFiles(String packageFolder) throws IOException {
		List<String> result = new ArrayList<>();
		Path dir = packageFolder.isEmpty() ? path : path.resolve(packageFolder);
		if(Files.isDirectory(dir)) {
			try(Stream<Path> s = Files.list(dir)) {
				s.forEach( p -> {
					String name = p.getFileName().toString();
					if(name.endsWith(".class") && Files.isRegularFile(p)) {
						result.add(packageFolder.isEmpty() ? name : packageFolder+"/"+name);
					}
				});
			}
		}
		return result;
	}
}
