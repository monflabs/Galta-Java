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

import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.json.impexp.file.FileBase;
import org.monflabs.json.impexp.file.FileTarget;
import org.monflabs.json.impexp.impl.JsonTargetImpl;
import org.monflabs.json.java.JavaJsonFactory;
import org.monflabs.util.FileUtil;
import org.monflabs.util.StringUtil;

import tests.ProjectTestCase;
import tests.util.StaticSource;

public class FileTargetTest extends ProjectTestCase {
	
	private static JsonObject readHierarchy(File root) {
		JsonObject o = JsonFactory.get().createObject();
		return readHierarchy(root, o, 0, null);
	}
	private static JsonObject readHierarchy(File root, JsonObject o, int index, String collection) {
//		JsonObject o = JavaJsonFactory.get().createObject();
		File[] files = root.listFiles();
		for(int i=0; i<files.length; i++) {
			File f = files[i];
			if(f.isFile()) {
				String content = FileUtil.readContent(f);
				String name = f.getName();
				if(StringUtil.isNotEmpty(collection)) {
					name = collection + "::" + name;
				}
				o.put(name,JavaJsonFactory.get().parse(content));
			} else if(f.isDirectory()) {
				String col = collection;
				if(index==0) {
					String n = f.getName();
					if(n.startsWith(FileBase.COLLECTION_PREFIX)) {
						col = n;
					}
				}
				JsonObject d = readHierarchy(f,JsonFactory.get().createObject(),index+1,col);
				o.put(f.getName(), d);
			}
		}
		return o;
	}
	
	public void testFileTarget() throws Exception {
		StaticSource source = StaticSource.newBuilder().build();

		File root = support.getProjectDirectory("target/tests/json");
		root.getParentFile().mkdirs();
		FileTarget target = FileTarget.newBuilder()
								.root(root)
								.clearOnStart(true)
								.estimatedCount(source::estimatedCount)
								.notification(JsonTargetImpl.consoleLogger)
					   			.build();
		
		source.exportTo(target);
		JsonObject dirs = readHierarchy(root);
		support.assertJsonTemplate(dirs, "file-target.json");
	}
	public void testFileTargetNoCollections() throws Exception {
		StaticSource source = StaticSource.newBuilder().build();

		File root = support.getProjectDirectory("target/tests/json-nocol");
		root.getParentFile().mkdirs();
		FileTarget target = FileTarget.newBuilder()
								.root(root)
								.clearOnStart(true)
								.ignoreCollection(true)
								.estimatedCount(source::estimatedCount)
								.notification(JsonTargetImpl.consoleLogger)
					   			.build();
		
		source.exportTo(target);
		JsonObject dirs = readHierarchy(root);
		support.assertJsonTemplate(dirs, "file-target-nocol.json");
	}
	public void testFileTargetHierachical() throws Exception {
		StaticSource source = StaticSource.newBuilder().build();

		File root = support.getProjectDirectory("target/tests/json-hier");
		root.getParentFile().mkdirs();
		FileTarget target = FileTarget.newBuilder()
								.root(root)
								.clearOnStart(true)
								.subdir(2)
								.estimatedCount(source::estimatedCount)
								.notification(JsonTargetImpl.consoleLogger)
					   			.build();
		
		source.exportTo(target);
		JsonObject dirs = readHierarchy(root);
		support.assertJsonTemplate(dirs, "file-target-hier.json");
	}
	public void testFileTargetHierachicalNoCollection() throws Exception {
		StaticSource source = StaticSource.newBuilder().build();

		File root = support.getProjectDirectory("target/tests/json-hier-nocol");
		root.getParentFile().mkdirs();
		FileTarget target = FileTarget.newBuilder()
								.root(root)
								.clearOnStart(true)
								.subdir(2)
								.ignoreCollection(true)
								.estimatedCount(source::estimatedCount)
								.notification(JsonTargetImpl.consoleLogger)
					   			.build();
		
		source.exportTo(target);
		JsonObject dirs = readHierarchy(root);
		support.assertJsonTemplate(dirs, "file-target-hier-nocol.json");
	}
	
	
	// Test
	public void testFileTargetTimestamp() throws Exception {
		
	}
	
	
	// Creation and Deletions
	public void testFileTargetWithDeletions() throws Exception {
		assertTrue(FileTarget.newBuilder().build().supportsDeletions());
		
		File root = support.getProjectDirectory("target/tests/json-deletions");
		root.getParentFile().mkdirs();
		
		// Create the records
		{
			FileTarget targetCreated = FileTarget.newBuilder()
									.root(root)
									.clearOnStart(true)
									.notification(JsonTargetImpl.consoleLogger)
						   			.build();
			StaticSource sourceCreated = StaticSource.newBuilder().content(StaticSource.CONTENT.CREATED).build();
			sourceCreated.exportTo(targetCreated);
			support.assertJsonTemplate(readHierarchy(root), "file-deletions-1.json");
		}
		
		// Create the records
		{
			FileTarget targetDeleted = FileTarget.newBuilder()
									.root(root)
									.clearOnStart(false)
									.notification(JsonTargetImpl.consoleLogger)
						   			.build();
			StaticSource sourceDeleted = StaticSource.newBuilder().content(StaticSource.CONTENT.DELETED).build();
			sourceDeleted.exportTo(targetDeleted);
			support.assertJsonTemplate(readHierarchy(root), "file-deletions-2.json");
		}
		
		// Mixed creation & deletions
		{
			FileTarget targetMixed= FileTarget.newBuilder()
									.root(root)
									.clearOnStart(false)
									.notification(JsonTargetImpl.consoleLogger)
						   			.build();
			StaticSource sourceMixed = StaticSource.newBuilder().content(StaticSource.CONTENT.MIXED).build();
			sourceMixed.exportTo(targetMixed);
			support.assertJsonTemplate(readHierarchy(root), "file-deletions-3.json");
		}
	}

}
