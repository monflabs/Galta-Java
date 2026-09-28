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
package org.monflabs.galtajs.library.node;

import org.monflabs.galtajs.JSEnvironment.Builder;
import org.monflabs.galtajs.library.GlobalLibrary;

/**
 * Node.js compatibility library — registers native module resolvers for
 * Node built-ins such as {@code node:fs} and {@code node:fs/promises}.
 *
 * <p>Usage:
 * <pre>
 *   env.registerLibrary(new NodeLibrary());
 *   ...
 *   import fs from 'node:fs';
 *   import { readFile } from 'node:fs/promises';
 * </pre>
 */
public class NodeLibrary extends GlobalLibrary {

	public NodeLibrary() {
	}

	@Override
	public void configureEnvironment(Builder builder) {
		builder.addModuleResolver(new NodeModuleResolver());
	}
}
