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
package org.monflabs.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class ResourceLoader {

	public static String loadTextResource(String name) {
		return loadTextResource(ResourceLoader.class.getClassLoader(), name);
	}

	public static String loadTextResource(ClassLoader cl, String name) {
		try {
			try (InputStream is = cl.getResourceAsStream(name)) {
				if (is == null) {
					throw new UtilException(null, "Resource {0} does not exist", name);
				}
				return IOStreamUtil.readString(new InputStreamReader(is, StandardCharsets.UTF_8));
			}
		} catch (IOException ex) {
			throw new UtilException(ex, "Error while reading resource {0}", name);
		}
	}
}