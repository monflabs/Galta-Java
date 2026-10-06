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
package playground;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Prints what the Java runtime offers to a compiler: its home folder, the JDK classes as
 * resources, the jrt file system and the packages of the boot layer. Run by
 * cheerpj/diagnostics.html, to see what CheerpJ provides.
 */
public class CheerpJDiagnostics {

	public static void main(String[] args) {
		System.out.println("=== java.version="+System.getProperty("java.version")+" java.home="+System.getProperty("java.home"));
		System.out.println("=== java.class.path="+System.getProperty("java.class.path"));
		System.out.println("=== sun.boot.class.path="+System.getProperty("sun.boot.class.path"));
		listFolder(new File(System.getProperty("java.home", "/")), 0);
		for(String r: new String[] {"java/lang/String.class", "java/util/List.class", "javax/swing/JFrame.class"}) {
			try {
				URL url = ClassLoader.getSystemResource(r);
				int size = -1;
				try(InputStream is = ClassLoader.getSystemResourceAsStream(r)) {
					if(is!=null) {
						size = is.readAllBytes().length;
					}
				}
				System.out.println("=== resource "+r+": url="+url+" bytes="+size);
			} catch(Throwable t) {
				System.out.println("=== resource "+r+": "+t);
			}
		}
		try {
			FileSystem jrt = FileSystems.getFileSystem(URI.create("jrt:/"));
			try(Stream<Path> s = Files.list(jrt.getPath("/modules"))) {
				System.out.println("=== jrt:/ modules: "+s.limit(10).toList());
			}
			System.out.println("=== jrt String.class bytes="+Files.size(jrt.getPath("/modules/java.base/java/lang/String.class")));
		} catch(Throwable t) {
			System.out.println("=== jrt:/ : "+t);
		}
		try {
			FileSystem jrt = FileSystems.newFileSystem(URI.create("jrt:/"), Map.of("java.home", System.getProperty("java.home")));
			System.out.println("=== jrt:/ with java.home: "+jrt);
		} catch(Throwable t) {
			System.out.println("=== jrt:/ with java.home: "+t);
		}
		try {
			var modules = ModuleLayer.boot().modules();
			int packages = modules.stream().mapToInt(m -> m.getPackages().size()).sum();
			System.out.println("=== boot layer: "+modules.size()+" modules, "+packages+" packages; java.base has "+Object.class.getModule().getPackages().size()+" packages");
		} catch(Throwable t) {
			System.out.println("=== boot layer: "+t);
		}
		System.out.println("=== done");
	}

	private static void listFolder(File f, int depth) {
		File[] files = f.listFiles();
		System.out.println("=== "+"  ".repeat(depth)+f+(files==null ? " (not a folder or unreadable)" : " ("+files.length+" entries)"));
		if(files!=null && depth<2) {
			int n = 0;
			for(File c: files) {
				if(n++>=25) {
					System.out.println("=== "+"  ".repeat(depth+1)+"...");
					break;
				}
				if(c.isDirectory()) {
					listFolder(c, depth+1);
				} else {
					System.out.println("=== "+"  ".repeat(depth+1)+c.getName()+" "+c.length());
				}
			}
		}
	}
}
