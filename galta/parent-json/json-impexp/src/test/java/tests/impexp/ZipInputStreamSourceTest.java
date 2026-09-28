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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Supplier;

import org.monflabs.json.JsonException;
import org.monflabs.json.impexp.file.ZipInputStreamSource;
import org.monflabs.json.impexp.impl.JsonTargetImpl;
import org.monflabs.util.io.FastBufferedInputStream;

import tests.ProjectTestCase;
import tests.util.StaticTarget;

public class ZipInputStreamSourceTest extends ProjectTestCase {
	
	private Supplier<InputStream> zipFile(File file) {
		return () -> {
			try {
				return new FastBufferedInputStream(new FileInputStream(file));
			} catch(IOException ex) {
				throw new JsonException(ex, "Error while opening zip file {0}", file.getPath());
			}
		};
	}


	public void testZipFileSourceWithCollections() throws Exception {
		ZipInputStreamSource source = ZipInputStreamSource.newBuilder()
								.zipInputStream(zipFile(support.getTestResourcesDirectory("json-zip/hierarchical.zip")))
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
		ZipInputStreamSource source = ZipInputStreamSource.newBuilder()
								.zipInputStream(zipFile(support.getTestResourcesDirectory("json-zip/hierarchical.zip")))
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

	public void testZipFileSourceFlat() throws Exception {
		ZipInputStreamSource source = ZipInputStreamSource.newBuilder()
								.zipInputStream(zipFile(support.getTestResourcesDirectory("json-zip/flat.zip")))
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
		ZipInputStreamSource source = ZipInputStreamSource.newBuilder()
								.zipInputStream(zipFile(support.getTestResourcesDirectory("json-zip/flat.zip")))
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
