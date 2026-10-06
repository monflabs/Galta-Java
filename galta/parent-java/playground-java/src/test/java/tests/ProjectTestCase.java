/*
 * Copyright (c) 2023-2026 Philippe Riand
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

import org.junit.Before;
import org.monflabs.filesystem.resources.ResourceFileSystem;
import org.monflabs.playground.PlaygroundConfiguration;
import org.monflabs.playground.SnippetFactory;
import org.monflabs.tests.__BaseTestCase;

public abstract class ProjectTestCase extends __BaseTestCase {

	@Override
	@Before
    public void setUp() throws Exception {
		super.setUp();
		
		FileSystem vfs = ResourceFileSystem.newBuilder()
				.classLoader(ProjectTestCase.class.getClassLoader())
				.root("snippets")
				.build();
		PlaygroundConfiguration.get().setSnippetFactory(new SnippetFactory(vfs));
	}
}
