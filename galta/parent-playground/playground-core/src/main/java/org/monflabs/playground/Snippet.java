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
package org.monflabs.playground;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.stream.Stream;

import org.monflabs.util.Console;
import org.monflabs.util.path.FilesUtil;

public class Snippet {
	
	public static final String FILENAME_MAIN = "main";
	public static final String FILEPATH_DOCUMENTATION = "README.md";

	public static final String LINK_PROPERTIES = "_links.properties";
	
	private Path folder;

	public Snippet(Path folder) {
		this.folder = folder;
	}

	public Path getFolder() {
		return folder;
	}

	// There is one FileSystem per snippet, ephemeral, only for temporary use
	public MemoryFileSystemSnippet createSnippetFs() {
		MemoryFileSystemSnippet fs = MemoryFileSystemSnippet.create();
		if(Files.isDirectory(folder)) {
			try (Stream<Path> stream = Files.list(folder)) {
			    stream.filter(Files::isRegularFile).forEach( (p) -> {
					if(Snippet.isVisible(p)) {
						Path memFile = fs.getPath(p.getFileName().toString()); 
						FilesUtil.write(memFile,FilesUtil.readAllBytes(p));
						fs.setPhysicalFile(memFile, p);
					}
				});
			} catch(IOException ex) {
				// a partial snippet still opens: report what could not be read
				Console.log(ex);
			}
	
			Path propsPath = folder.resolve(LINK_PROPERTIES);
			if(Files.isRegularFile(propsPath)) {
				Properties props = new Properties();
				try(InputStream is=Files.newInputStream(propsPath)) {
					props.load(is);
				} catch(IOException ex) {
					Console.log(ex);
				}
				for(Object k: props.keySet()) {
					String name = (String)k;
					String path = (String)props.get(k);
					Path p = folder.resolve(path).normalize();
					if(Files.isRegularFile(p)) {
						Path memFile = fs.getPath(name); 
						FilesUtil.write(memFile,FilesUtil.readAllBytes(p));
						fs.setPhysicalFile(memFile, p);
					}
				}
			}
		}
		return fs;
	}

	public static boolean isVisible(Path path) {
		return !path.getFileName().toString().startsWith("_");
	}
}
	