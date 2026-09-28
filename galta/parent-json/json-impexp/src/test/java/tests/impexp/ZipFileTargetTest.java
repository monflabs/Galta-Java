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
package tests.impexp;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.json.impexp.file.FileBase;
import org.monflabs.json.impexp.file.ZipTarget;
import org.monflabs.json.impexp.impl.JsonTargetImpl;
import org.monflabs.json.java.JavaJsonFactory;
import org.monflabs.util.StringUtil;
import org.monflabs.util.io.ZipUtil;

import tests.ProjectTestCase;
import tests.util.StaticSource;

public class ZipFileTargetTest extends ProjectTestCase {
	
	private static JsonObject readHierarchy(File root) throws IOException {
		try(ZipFile zipFile = new ZipFile(root)) {
			JsonObject o = JavaJsonFactory.get().createObject();
			for(Enumeration<? extends ZipEntry> en=zipFile.entries(); en.hasMoreElements(); ) {
				ZipEntry ze = en.nextElement();
				if(ZipUtil.shouldIgnore(ze)) {
					continue;
				}
				String path = ze.getName();
				if(path.endsWith(".json")) {
					path = path.substring(0,path.length()-5);
					JsonObject parent = o;
					String collection = null;
					if(path.startsWith(FileBase.COLLECTION_PREFIX)) {
						int pos = path.indexOf('/');
						collection = path.substring(0,pos);
						path = path.substring(pos+1);
					}
					String name = path;
					if(path.indexOf('/')>=0) {
						String[] parts = StringUtil.splitString(path, '/');
						for(int i=0; i<parts.length-1; i++) {
							parent = parent.getOrCreateObject(parts[i]);
						}
						name = parts[parts.length-1];
					}
					if(collection!=null) {
						name = collection + "::" + name;
					}
					InputStream is = zipFile.getInputStream(ze);
					Object json = JsonFactory.get().parse(new InputStreamReader(is,StandardCharsets.UTF_8));
					parent.putValue(name,json);
				}
			}
			return o;
		}
	}
	
	public void testZipFileTargetCollections() throws Exception {
		StaticSource source = StaticSource.newBuilder().build();

		File root = support.getProjectDirectory("target/tests/json-zip/json.zip");
		root.getParentFile().mkdirs();
		ZipTarget target = ZipTarget.newBuilder()
								.root(root)
								.estimatedCount(source::estimatedCount)
								.notification(JsonTargetImpl.consoleLogger)
					   			.build();
		
		source.exportTo(target);
		JsonObject dirs = readHierarchy(root);
		support.assertJsonTemplate(dirs, "zipfile-target.json");
	}
	public void testZipFileTargetNoCollections() throws Exception {
		StaticSource source = StaticSource.newBuilder().build();

		File root = support.getProjectDirectory("target/tests/json-zip/json.zip");
		root.getParentFile().mkdirs();
		ZipTarget target = ZipTarget.newBuilder()
								.root(root)
								.ignoreCollection(true)
								.estimatedCount(source::estimatedCount)
								.notification(JsonTargetImpl.consoleLogger)
					   			.build();
		
		source.exportTo(target);
		JsonObject dirs = readHierarchy(root);
		support.assertJsonTemplate(dirs, "zipfile-target-nocol.json");
	}
	public void testFileTargetHierachical() throws Exception {
		StaticSource source = StaticSource.newBuilder().build();

		File root = support.getProjectDirectory("target/tests/json-zip/json-hier.zip");
		root.getParentFile().mkdirs();
		ZipTarget target = ZipTarget.newBuilder()
								.root(root)
								.subdir(2)
								.estimatedCount(source::estimatedCount)
								.notification(JsonTargetImpl.consoleLogger)
					   			.build();
		
		source.exportTo(target);
		JsonObject dirs = readHierarchy(root);
		support.assertJsonTemplate(dirs, "zipfile-target-hier.json");
	}
	public void testFileTargetHierachicalNoCol() throws Exception {
		StaticSource source = StaticSource.newBuilder().build();

		File root = support.getProjectDirectory("target/tests/json-zip/json-hier.zip");
		root.getParentFile().mkdirs();
		ZipTarget target = ZipTarget.newBuilder()
								.root(root)
								.ignoreCollection(true)
								.subdir(2)
								.estimatedCount(source::estimatedCount)
								.notification(JsonTargetImpl.consoleLogger)
					   			.build();
		
		source.exportTo(target);
		JsonObject dirs = readHierarchy(root);
		support.assertJsonTemplate(dirs, "zipfile-target-hier-nocol.json");
	}
}
