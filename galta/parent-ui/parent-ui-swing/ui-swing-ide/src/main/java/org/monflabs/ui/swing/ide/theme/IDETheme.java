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
package org.monflabs.ui.swing.ide.theme;

import java.io.IOException;
import java.io.InputStream;

import org.fife.ui.rsyntaxtextarea.Theme;
import org.monflabs.ui.swing.theme.SwingTheme;
import org.monflabs.util.Console;
import org.monflabs.util.config.Config;

/**
 * IDE theme.
 */
public class IDETheme extends SwingTheme {
	
    private static final String THEME_LIGHT = "/org/fife/ui/rsyntaxtextarea/themes/default.xml";
    //private static final String THEME_DARK = "/org/fife/ui/rsyntaxtextarea/themes/dark.xml";
    private static final String THEME_DARK = "/swing/rsyntaxtextarea/theme-flatlaf.xml";

	private Theme rtTheme;
	
    public IDETheme(Config config) {
    	super(config);
    	
    	rtTheme = loadTheme(isDark()?THEME_DARK:THEME_LIGHT);
    	if(rtTheme==null && isDark()) {
    		rtTheme = loadTheme(THEME_LIGHT);
    	}
    }
    
    private static Theme loadTheme(String resource) {
    	try(InputStream in = IDETheme.class.getResourceAsStream(resource)) {
    		if(in==null) {
    			Console.log("Syntax theme {0} not found", resource);
    			return null;
    		}
    		return Theme.load(in);
    	} catch (IOException | RuntimeException e) {
    		Console.log(e);
    		return null;
    	}
    }
    
    /**
     * The syntax area theme, or null if none could be loaded.
     */
    public Theme getSyntaxAreaTheme() {
   		return rtTheme;
    }
}