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
package playground;

import org.monflabs.tests.__BaseTestCase;
import org.monflabs.util.config.Config;

/**
 * The --theme argument passed by the documentation site.
 */
public class ThemeConfigTest extends __BaseTestCase {

	public void testThemeArgument() {
		Config dark = JavaPlaygroundCheerpJ.themeConfig(new String[] {"--theme=dark"});
		assertTrue(dark.getBoolean("ui/dark"));
		Config light = JavaPlaygroundCheerpJ.themeConfig(new String[] {"--theme=light"});
		assertTrue(light.has("ui/dark"));
		assertFalse(light.getBoolean("ui/dark"));
		// No argument: the system's mode
		assertNull(JavaPlaygroundCheerpJ.themeConfig(new String[0]));
	}
}
