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
package org.monflabs.ui.swing.settings;

import java.util.function.Consumer;

import org.monflabs.util.StringUtil;

/**
 * Persist data in a text file that is human friendly.
 * 
 * It is inspired by MIME multi part, although simplified.
 * It also have everything in memory, which is ok for a client
 * 
 * 
 * @author priand
 *
 */
public class MultipartTextFile {
	
	@FunctionalInterface
	public static interface Serializer {
		public void serialize(String name, String content);
	}

	@FunctionalInterface
	public static interface Deserializer {
		public void deserialize(String name, String content);
	}
	
	private static final String HEADER_START = "---------- PART: [";
	private static final String HEADER_END = "]\n";
	
	
	public MultipartTextFile() {
	}
	
	public String serialize(Consumer<Serializer> serializer) {
		StringBuilder b = new StringBuilder(8192);
		serializer.accept( (name,content) -> {
			if(content!=null) {
				b.append(HEADER_START);
				b.append(name);
				b.append(HEADER_END);
				b.append(content);
			}
		});
		
		return StringUtil.normalizeLineBreaks(b.toString());
	}
	
	public void deserialize(String source, Deserializer deserializer) {
		source = StringUtil.normalizeLineBreaks(source);
		int start = source.indexOf(HEADER_START, 0);
		while(start>=0) {
			int end = source.indexOf(HEADER_END,start);
			if(end<0) {
				// Truncated file: a header without its end
				break;
			}
			String fileName = source.substring(start+HEADER_START.length(),end);

			int contentStart = end+HEADER_END.length();
			start = source.indexOf(HEADER_START, contentStart);
			int contentEnd = start>=0 ? start : source.length();
			
			String content = source.substring(contentStart,contentEnd);
			deserializer.deserialize(fileName, content);
		}
	}
}
