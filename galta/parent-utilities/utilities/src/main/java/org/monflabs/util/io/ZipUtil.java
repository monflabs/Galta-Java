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
package org.monflabs.util.io;

import java.util.zip.ZipEntry;

/**
 * Zip file utilities.
 */
public class ZipUtil {

	public static boolean shouldIgnore(ZipEntry ze) {
		String path = ze.getName();
		// https://stackoverflow.com/questions/10924236/mac-zip-compress-without-macosx-folder
		if(path.startsWith("__MACOSX/")) {
			return true;
		}
		return false;
	}
}
