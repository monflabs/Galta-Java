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
package org.monflabs.ui;

import org.monflabs.util.config.Config;

/**
 * Application.
 */
public class UIApplication {
	
	private static UIApplication application;
	public static UIApplication get() {
		return application;
	}
	
	private Config config;

	protected UIApplication(Config config) {
		if(application!=null) {
			throw new IllegalStateException("Application already exists");
		}
		application = this;
		this.config = config;
	}
	
	public Config getConfig() {
		return config;
	}
}