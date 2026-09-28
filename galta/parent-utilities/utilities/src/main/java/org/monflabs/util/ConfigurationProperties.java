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

import java.util.Locale;

/**
 * Access system configuration.
 */ 
public class ConfigurationProperties {
	
	private static ConfigurationProperties instance = new ConfigurationProperties();
	
	public static final ConfigurationProperties get() {
		return instance;
	}
	
	public String get(String key) {
		return get(key,null);
	}
	public String get(String key, String defaultValue) {
		if(key==null) {
			return defaultValue;
		}
		String envKey = key.toUpperCase(Locale.ROOT).replace('.', '_');
		// Read from the env variables by default
		String r = System.getenv(envKey);
		return StringUtil.isNotEmpty(r) ? r : defaultValue;
	}
}
