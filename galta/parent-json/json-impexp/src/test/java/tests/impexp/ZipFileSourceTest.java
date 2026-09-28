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

import org.monflabs.json.impexp.file.ZipFileSource;
import org.monflabs.json.impexp.impl.JsonTargetImpl;

import tests.ProjectTestCase;
import tests.util.StaticTarget;

public class ZipFileSourceTest extends ProjectTestCase {

	public void testZipFileSourceWithCollections() throws Exception {
		ZipFileSource source = ZipFileSource.newBuilder()
								.zipFile(support.getTestResourcesDirectory("json-zip/hierarchical.zip"))
								.estimateCount(true)
								.build();
		StaticTarget target = StaticTarget.newBuilder()
				.estimatedCount(source::estimatedCount)
				.notification(JsonTargetImpl.consoleLogger)
				.build();
		
		source.exportTo(target);
		support.assertJsonTemplate(target.getContentAsJson(), "zipfile-source.json");
	}
	public void testZipFileSourceNoCollections() throws Exception {
		ZipFileSource source = ZipFileSource.newBuilder()
								.zipFile(support.getTestResourcesDirectory("json-zip/hierarchical.zip"))
								.ignoreCollection(true)
								.estimateCount(true)
								.build();
		StaticTarget target = StaticTarget.newBuilder()
				.estimatedCount(source::estimatedCount)
				.notification(JsonTargetImpl.consoleLogger)
				.build();
		
		source.exportTo(target);
		support.assertJsonTemplate(target.getContentAsJson(), "zipfile-source-nocol.json");
	}

	// C4: the count must be available before the source is streamed
	public void testEstimatedCountBeforeStream() throws Exception {
		ZipFileSource source = ZipFileSource.newBuilder()
								.zipFile(support.getTestResourcesDirectory("json-zip/hierarchical.zip"))
								.estimateCount(true)
								.build();
		assertEquals(14, source.estimatedCount());
		StaticTarget target = StaticTarget.newBuilder().build();
		source.exportTo(target);
		assertEquals(14, source.estimatedCount());

		ZipFileSource noCount = ZipFileSource.newBuilder()
								.zipFile(support.getTestResourcesDirectory("json-zip/hierarchical.zip"))
								.build();
		assertEquals(-1, noCount.estimatedCount());
	}

	public void testZipFileSourceFlat() throws Exception {
		ZipFileSource source = ZipFileSource.newBuilder()
								.zipFile(support.getTestResourcesDirectory("json-zip/flat.zip"))
								.estimateCount(true)
								.build();
		StaticTarget target = StaticTarget.newBuilder()
				.estimatedCount(source::estimatedCount)
				.notification(JsonTargetImpl.consoleLogger)
				.build();
		
		source.exportTo(target);
		support.assertJsonTemplate(target.getContentAsJson(), "zipfile-source-flat.json");
	}
	public void testZipFileSourceFlatNoCollections() throws Exception {
		ZipFileSource source = ZipFileSource.newBuilder()
								.zipFile(support.getTestResourcesDirectory("json-zip/flat.zip"))
								.ignoreCollection(true)
								.estimateCount(true)
								.build();
		StaticTarget target = StaticTarget.newBuilder()
				.estimatedCount(source::estimatedCount)
				.notification(JsonTargetImpl.consoleLogger)
				.build();
		
		source.exportTo(target);
		support.assertJsonTemplate(target.getContentAsJson(), "zipfile-source-flat-nocol.json");
	}
	
}
