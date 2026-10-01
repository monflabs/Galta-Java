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
package org.monflabs.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * 
 */ 
public class FileUtil {
	
    /**
     * Creates the directory, deleting it first if it exists: the same as
     * {@link #prepareEmptyDirectory(File)}.
     * @deprecated the name doesn't say that the existing content is deleted: use
     * {@link #prepareEmptyDirectory(File)}, or {@link #prepareDirectory(File, boolean)}
     */
    @Deprecated
    public static void prepareDirectory( File file ) {
    	prepareEmptyDirectory(file);
    }
    /**
     * Creates the directory and its parents, after deleting it (and its whole content)
     * if it already exists.
     */
    public static void prepareEmptyDirectory( File file ) {
    	prepareDirectory(file, true);
    }
    /**
     * Creates the directory and its parents. With clear, the directory is deleted first
     * (with its whole content) if it already exists.
     */
    public static void prepareDirectory( File file, boolean clear ) {
    	if(clear && file.exists()) {
    		prune(file,true);
    	}
    	file.mkdirs();
    }

    public static boolean deleteFile( File file ) {
		return prune(file, true);
    }

    public static boolean emptyDirectory( File directory ) {
    	if(directory.isDirectory()) {
    		return prune(directory, false);
    	}
    	return false;
    }
    
	private static boolean prune(File f, boolean delete) {
		boolean result = true;
		// Never descend through a symbolic link: deleting a link must not delete the
		// content of its target (File.isDirectory() follows links).
		// The root of an emptyDirectory() call is the exception - the caller asked for it.
		if(f.isDirectory() && (!delete || !Files.isSymbolicLink(f.toPath()))) {
			File[] children = f.listFiles();
			if(children!=null) {
				for(int i=0; i<children.length; i++) {
					if(!prune(children[i],true)) {
						result = false;
					}
				}
			}
		}
		if(delete) {
			if(!f.delete()) {
				result = false;
			}
		}
		return result;
	}	
	
	/**
	 * Reads a UTF-8 text file. Malformed input is replaced (U+FFFD) instead of being
	 * reported.
	 * @deprecated use {@link org.monflabs.util.path.FilesUtil#readString(java.nio.file.Path)}
	 * or {@link Files#readString(java.nio.file.Path)}, which report malformed input
	 */
	@Deprecated
	public static String readContent(File file) {
		return readContent(file, StandardCharsets.UTF_8);
	}
	/**
	 * Reads a text file. Malformed input is replaced (U+FFFD) instead of being reported.
	 * @deprecated use {@link Files#readString(java.nio.file.Path, Charset)}, which reports
	 * malformed input
	 */
	@Deprecated
	public static String readContent(File file, Charset cs) {
		try {
			return new String(Files.readAllBytes(file.toPath()), cs);
		} catch(IOException ex) {
			throw new ForwardRuntimeException(ex, "Error while reading file {0}", file.getPath());
		}
	}
	
	/**
	 * Writes a UTF-8 text file, replacing it.
	 * @deprecated use {@link Files#writeString(java.nio.file.Path, CharSequence, java.nio.file.OpenOption...)}
	 */
	@Deprecated
	public static void setContent(File file, String content) {
		setContent(file, content, StandardCharsets.UTF_8);
	}
	/**
	 * Writes a text file, replacing it. Unmappable characters are replaced.
	 * @deprecated use {@link Files#writeString(java.nio.file.Path, CharSequence, Charset, java.nio.file.OpenOption...)},
	 * which reports unmappable characters
	 */
	@Deprecated
	public static void setContent(File file, String content, Charset cs) {
		try {
			Files.write(file.toPath(), content.getBytes(cs));
		} catch(IOException ex) {
			throw new ForwardRuntimeException(ex, "Error while writing into file {0}", file.getPath());
		}
	}

	// Through an output stream rather than Files.copy(Path,Path): that one would replace
	// a link or an empty directory at the target instead of writing into the file
	public static void copy(File src, File tgt) {
		try(OutputStream os = new FileOutputStream(tgt)) {
			Files.copy(src.toPath(), os);
		} catch(IOException ex) {
			throw new ForwardRuntimeException(ex, "Error while copy file {0} to {1}", src.getPath(), tgt.getPath());
		}
	}
}