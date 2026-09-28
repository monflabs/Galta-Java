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
package tests.javascript.libraries;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.library.StaticLibrary;
import org.monflabs.galtajs.library.node.NodeLibrary;
import org.monflabs.galtajs.library.platform.HostLibrary;

import tests.javascript.JavaScriptStrictTestCase;

/**
 * Tests for the node:fs and node:fs/promises modules.
 *
 * <p>A per-test temp directory is created and its path is injected as the
 * global {@code TMPDIR} into the script, so the .js code has an absolute
 * root it can write under. HostLibrary is registered for setTimeout, which
 * is used to observe Promise resolutions from the promises API.
 */
public class NodeFsTest extends JavaScriptStrictTestCase {

	private Path tmpDir;

	public void testScript() throws Exception {
		execute();
	}

	@Override
	protected JSEnvironment.Builder createEnvironment() {
		try {
			tmpDir = Files.createTempDirectory("galtajs-nodefs-");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		JSEnvironment.Builder builder = super.createEnvironment();
		builder.registerLibrary(new HostLibrary());
		builder.registerLibrary(new NodeLibrary());
		StaticLibrary globals = new StaticLibrary();
		globals.addStaticGlobal("TMPDIR", tmpDir.toString());
		builder.registerLibrary(globals);
		return builder;
	}
}
