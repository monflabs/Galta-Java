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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
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
	
    public static void prepareDirectory( File file ) {
    	prepareDirectory(file, true);
    }
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
	
	public static String readContent(File file) {
		try(Reader r = new FileReader(file,StandardCharsets.UTF_8)) {
			return IOStreamUtil.readContent(r);
		} catch(IOException ex) {
			throw new ForwardRuntimeException(ex, "Error while reading file {0}", file.getPath());
		}    
	}
	public static String readContent(File file, Charset cs) {
		try(Reader r = new FileReader(file,cs)) {
			return IOStreamUtil.readContent(r);
		} catch(IOException ex) {
			throw new ForwardRuntimeException(ex, "Error while reading file {0}", file.getPath());
		}    
	}
	
	public static void setContent(File file, String content) {
		try(Writer w = new FileWriter(file,StandardCharsets.UTF_8)) {
			w.write(content);
		} catch(IOException ex) {
			throw new ForwardRuntimeException(ex, "Error while writing into file {0}", file.getPath());
		}
	}
	public static void setContent(File file, String content, Charset cs) {
		try(Writer w = new FileWriter(file,cs)) {
			w.write(content);
		} catch(IOException ex) {
			throw new ForwardRuntimeException(ex, "Error while writing into file {0}", file.getPath());
		}
	}
	
	public static void copy(File src, File tgt) {
		byte[] buf = new byte[4096];
		try(OutputStream os = new FileOutputStream(tgt)) {
			try(InputStream is = new FileInputStream(src)) {
				int len;
				while( (len=is.read(buf)) >0 ) {
					os.write(buf,0,len);
				}
			}
		} catch(IOException ex) {
			throw new ForwardRuntimeException(ex, "Error while copy file {0} to {1}", src.getPath(), tgt.getPath());
		}
	}
}