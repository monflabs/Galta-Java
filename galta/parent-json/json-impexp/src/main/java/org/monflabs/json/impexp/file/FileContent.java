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
package org.monflabs.json.impexp.file;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.impexp.JsonContent;
import org.monflabs.json.impexp.JsonKey;
import org.monflabs.util.io.FastBufferedReader;

public class FileContent implements JsonContent {
	
	private JsonKey docKey;
	private File doc;
	
	private Object json;
	
	public FileContent(JsonKey docKey, File doc) {
		this.docKey = docKey;
		this.doc = doc;
	}
	
	private void initialize() {
		if(json==null) {
			try(Reader r = new FastBufferedReader(new FileReader(doc, StandardCharsets.UTF_8)))  {
				this.json = JsonFactory.get().parse(r);
			} catch(IOException ie) {
				throw new JsonException(ie,"Error while reading JSON file");
			}
		}
	}
	
	@Override
	public TYPE getType() {
		return TYPE.RECORD;
	}
	@Override
	public JsonKey getKey() {
		return docKey;
	}
	@Override
	public Object getJson() {
		initialize();
		return json;
	}
	@Override
	public Instant getTimestamp() {
		return Instant.ofEpochMilli(doc.lastModified());
	}
}
