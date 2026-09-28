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
package tests;

import java.nio.file.FileSystem;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.monflabs.filesystem.resources.ResourceFileSystem;
import org.monflabs.filesystem.resources.ResourceFileSystemProvider;
import org.monflabs.playground.PlaygroundConfiguration;
import org.monflabs.playground.PlaygroundException;
import org.monflabs.playground.SnippetFactory;
import org.monflabs.tests.__BaseTestCase;

import playground.GaltaJSPlayground;

public abstract class ProjectTestCase extends __BaseTestCase {

	@Override
	@Before
    public void setUp() throws Exception {
		super.setUp();
		
		ResourceFileSystemProvider provider = ResourceFileSystem.DEFAULT_PROVIDER;
        Map<String, Object> env = new HashMap<>();
        env.put(ResourceFileSystemProvider.CLASSLOADER_PARAM, GaltaJSPlayground.class.getClassLoader());
        env.put(ResourceFileSystemProvider.BASE_PATH_PARAM, "snippets");
		try {
	        FileSystem fs = provider.newFileSystem(ResourceFileSystem.DEFAULT_URI, env);
			PlaygroundConfiguration.get().setSnippetFactory(new SnippetFactory(fs));
		} catch(Exception e) {
			throw new PlaygroundException(e);
		}
	}
}
