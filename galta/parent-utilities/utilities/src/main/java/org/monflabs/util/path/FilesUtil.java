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

package org.monflabs.util.path;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.stream.Stream;

/**
 * @author Philippe Riand
 */
public class FilesUtil {
	
    // Index of the extension dot in a file name, or -1. A leading dot is not an extension
    // separator: ".bashrc" is a hidden file without extension
    private static int extensionDot(String name) {
    	int dot = name.lastIndexOf('.');
    	return dot>0 ? dot : -1;
    }

    /**
     * Returns the extension of a file, without the dot: "" when there is none (including for
     * a root path or a dotfile like ".bashrc"), and null for a null path.
     */
    public static final String getFileExtension(Path path) {
    	if(path!=null) {
    		Path fileName = path.getFileName();
    		if(fileName==null) {
    			return ""; // A root path
    		}
	    	String name = fileName.toString();
	    	int dot = extensionDot(name);
	    	return dot>=0
	    	           ? name.substring(dot + 1)
	    	           : "";
    	}
    	return null;
    }

    /**
     * Replaces (or adds) the extension of a file. A dotfile like ".bashrc" gets the extension
     * appended (".bashrc.txt").
     * @throws IllegalArgumentException for a root path, which has no file name
     */
    public static Path setExtension(Path path, String newExtension) {
    	if(path!=null) {
    		Path name = path.getFileName();
    		if(name==null) {
    			throw new IllegalArgumentException("Cannot set the extension of a root path "+path);
    		}
	    	String fileName = name.toString();
			int dotIndex = extensionDot(fileName);
			String baseName = (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
			String ext = newExtension.startsWith(".") ? newExtension : "." + newExtension;
			String newFileName = baseName + ext;
			return path.resolveSibling(newFileName);
    	}
    	return null;
	}

    public static boolean isRoot(Path p) {
        return p.getRoot() != null && p.getParent() == null;
    }
    
	public static final Path getRoot(FileSystem fs) {
		return fs.getPath(fs.getSeparator());
	}
	
	public static final byte[] readAllBytes(Path path) {
		try {
			return Files.readAllBytes(path);
		} catch(IOException ex) {
			throw new FileSystemRuntimeException(ex);
		}
	}

	public static final String readString(Path path) {
		try {
			return Files.readString(path);
		} catch(IOException ex) {
			throw new FileSystemRuntimeException(ex);
		}
	}

	public static final String readString(Path path, Charset cs) {
		try {
			return cs!=null ? Files.readString(path,cs) : Files.readString(path);
		} catch(IOException ex) {
			throw new FileSystemRuntimeException(ex);
		}
	}

	public static final Path write(Path path, byte[] bytes, OpenOption...options) {
		try {
			return Files.write(path,bytes,options);
		} catch(IOException ex) {
			throw new FileSystemRuntimeException(ex);
		}
	}

	public static final Path writeString(Path path, String text) {
		try {
			return Files.writeString(path,text);
		} catch(IOException ex) {
			throw new FileSystemRuntimeException(ex);
		}
	}

	public static final Path writeString(Path path, String text, Charset cs) {
		try {
			return cs!=null ? Files.writeString(path,text,cs) : Files.writeString(path,text);
		} catch(IOException ex) {
			throw new FileSystemRuntimeException(ex);
		}
	}
	
	
	public static void clearDirectory(Path dir) throws IOException {
	    try (Stream<Path> stream = Files.list(dir)) {
	        for (Path p : stream.toList()) {
	            deleteRecursively(p);
	        }
	    }
	}
	
	public static void deleteRecursively(Path root) throws IOException {
		Files.walkFileTree(root, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				if (exc != null) {
					throw exc;
				}
				Files.delete(dir);
				return FileVisitResult.CONTINUE;
			}
		});
	}
}
