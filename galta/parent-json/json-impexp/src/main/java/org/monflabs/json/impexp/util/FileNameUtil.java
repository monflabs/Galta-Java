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
package org.monflabs.json.impexp.util;

import org.monflabs.json.JsonException;
import org.monflabs.json.impexp.file.FileBase;

public class FileNameUtil {

	private FileNameUtil() {}

	/**
	 * Encodes a document id or a collection name so it can be used as a single file
	 * name segment.
	 * <p>
	 * Path separators and the characters that are invalid on common file systems are
	 * escaped as %XX, as well as '@', which marks a collection folder: an id such as
	 * "@abc" must not be taken for a collection when it is read back.
	 */
	public static String encodeFilename(String name) {
		StringBuilder b = null;
		int length = name.length();
		for(int i=0; i<length; i++) {
			char c = name.charAt(i);
			switch(c) {
				case 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 	
				     10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 	
				     20, 21, 22, 23, 24, 25, 26, 27, 28, 29,
				     30, 31,
				     '%', 	
				     '<', 	
				     '>', 	
				     ':', 	
				     '"', 	
				     '/', 	
				     '\\',
				     '|',	
				     '?', 	
				     '*',
				     '@':
					if(b==null) {
						b = new StringBuilder(length*2);
						if(i>0) {
							b.append(name, 0, i);
						}
					}
					int cc = c & 0xFF;
					b.append('%');
					b.append(HEX_CHARS[cc >> 4]);
					b.append(HEX_CHARS[cc & 0x0F]);
					break;
				default: {
					if(b!=null) {
						b.append(c);
					}
				}
			}
		}
		return b!=null ? b.toString() : name;
	}

	/**
	 * Returns the name of the folder (or zip path segment) that holds a collection:
	 * the collection prefix followed by the encoded collection name, so a collection
	 * name can never introduce a path separator or a parent reference.
	 */
	public static String encodeCollectionFolder(String collection) {
		return FileBase.COLLECTION_PREFIX + encodeFilename(collection);
	}

	/**
	 * Returns the collection name held by a folder name, or null if the folder is not
	 * a collection folder.
	 */
	public static String decodeCollectionFolder(String folderName) {
		if(folderName.startsWith(FileBase.COLLECTION_PREFIX)) {
			return decodeFilename(folderName.substring(FileBase.COLLECTION_PREFIX.length()));
		}
		return null;
	}

	/**
	 * Returns the collection of a zip entry path, or null when the entry is not
	 * inside a collection folder.
	 */
	public static String collectionFromZipPath(String path) {
		int idx = path.indexOf('/');
		if(idx>0) {
			return decodeCollectionFolder(path.substring(0,idx));
		}
		// A root level entry is never a collection, even when its (legacy) name starts
		// with the collection prefix
		return null;
	}

	private static char[] HEX_CHARS = new char[] {
		'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'
	};

	public static String decodeFilename(String name) {
		StringBuilder b = null;
		int length = name.length();
		for(int i=0; i<length; i++) {
			char c = name.charAt(i);
			if(c=='%') {
				if(b==null) {
					b = new StringBuilder(length*2);
					if(i>0) {
						b.append(name, 0, i);
					}
				}
				if(i+2>=length) {
					throw new JsonException(null,"Invalid hexadecimal escape sequence %XX in file name {0}", name);
				}
				int v1 = hexValue(name.charAt(++i));
				int v2 = hexValue(name.charAt(++i));
				b.append( (char)(v1*16 + v2));
			} else {
				if(b!=null) {
					b.append(c);
				}
			}
		}
		return b!=null ? b.toString() : name;
	}
	private static int hexValue(char c) {
		if(c>='0' && c<='9') {
			return (int)c - '0';
		}
		if(c>='A' && c<='F') {
			return (int)c - 'A' + 10;
		}
		if(c>='a' && c<='f') {
			return (int)c - 'a' + 10;
		}
		throw new JsonException(null,"Invalid hexadecimal escape sequence %XX in file name");
	}
}
