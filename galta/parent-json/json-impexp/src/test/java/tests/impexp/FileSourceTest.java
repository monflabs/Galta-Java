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

import org.monflabs.json.impexp.file.FileSource;
import org.monflabs.json.impexp.impl.JsonTargetImpl;

import tests.ProjectTestCase;
import tests.util.StaticTarget;

public class FileSourceTest extends ProjectTestCase {
	
	public void testFileSource() throws Exception {
		FileSource source = FileSource.newBuilder()
								.root(support.getTestResourcesDirectory("json"))
								.estimateCount(true)
								.build();
		StaticTarget target = StaticTarget.newBuilder()
								.estimatedCount(source::estimatedCount)
								.notification(JsonTargetImpl.consoleLogger)
								.build();
		
		source.exportTo(target);
		support.assertJsonTemplate(target.getContentAsJson(), "file-source.json");
	}
	public void testFileSourceNoCollections() throws Exception {
		FileSource source = FileSource.newBuilder()
								.root(support.getTestResourcesDirectory("json"))
								.ignoreCollection(true)
								.estimateCount(true)
								.build();
		StaticTarget target = StaticTarget.newBuilder()
				.estimatedCount(source::estimatedCount)
				.notification(JsonTargetImpl.consoleLogger)
				.build();
		
		source.exportTo(target);
		support.assertJsonTemplate(target.getContentAsJson(), "file-source-nocol.json");
	}
	public void testFileSourceFlat() throws Exception {
		FileSource source = FileSource.newBuilder()
								.root(support.getTestResourcesDirectory("json-flat"))
								.estimateCount(true)
								.build();
		StaticTarget target = StaticTarget.newBuilder()
				.estimatedCount(source::estimatedCount)
				.notification(JsonTargetImpl.consoleLogger)
				.build();
		
		source.exportTo(target);
		support.assertJsonTemplate(target.getContentAsJson(), "file-source-flat.json");
	}
	public void testFileSourceFlatNoCollection() throws Exception {
		FileSource source = FileSource.newBuilder()
								.root(support.getTestResourcesDirectory("json-flat"))
								.ignoreCollection(true)
								.estimateCount(true)
								.build();
		StaticTarget target = StaticTarget.newBuilder()
				.estimatedCount(source::estimatedCount)
				.notification(JsonTargetImpl.consoleLogger)
				.build();
		
		source.exportTo(target);
		support.assertJsonTemplate(target.getContentAsJson(), "file-source-flat-nocol.json");
	}
}
