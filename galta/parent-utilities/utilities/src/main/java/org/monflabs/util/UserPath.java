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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Access user folders.
 */
public class UserPath {
	
	public static final String MONFLABS_FOLDER = ".monflabs";

	public static Path getUserHome() {
		return Path.of(System.getProperty("user.home"));
	}
	public static Path getUserHomeDev() {
		// This is for development environments
		String envvar = System.getenv("MONFLABS_USER_HOME");
		if(StringUtil.isNotEmpty(envvar)) {
			Path d = Path.of(envvar);
			if(Files.exists(d)) {
				return d;
			}
		}
		Path mf = getUserHome().resolve("monflabs-dev-settings/user-settings".replace(PathUtil.POSIX_SEP,File.separatorChar));
		if(Files.exists(mf)) {
			return mf;
		}
		return null;
	}

	public static Path getDownloadFolder() {
		// See: https://blog.samirhadzic.com/2018/03/01/get-the-user-download-folder-path/
		// does not work on Windows when the download folder has been changed
		Path downloadFolder = Paths.get(System.getProperty("user.home"), "Downloads");
		return downloadFolder;
	}

	public static Path getMonflabsFolder() {
		return getUserHome().resolve(MONFLABS_FOLDER);
	}

	public static Path getMonflabsDevFolder() {
		Path userHomeDev = getUserHomeDev();
		if(userHomeDev!=null) {
			Path mf = userHomeDev.resolve(MONFLABS_FOLDER);
			if(Files.exists(mf)) {
				return mf;
			}
		}
		return null;
	}
}
