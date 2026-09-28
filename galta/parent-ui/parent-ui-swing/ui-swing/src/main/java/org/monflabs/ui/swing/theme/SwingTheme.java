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
package org.monflabs.ui.swing.theme;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.UIManager;

import org.monflabs.util.config.Config;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.util.SystemInfo;

/**
 * Swing theme based on flatlaf
 */
public class SwingTheme {
	
	private boolean dark;
	
    public SwingTheme(Config config) {
    	
    	// Mac OS
    	if( SystemInfo.isMacOS ) {
			// hide menu items that are in macOS application menu
//			exitMenuItem.setVisible( false );
//			aboutMenuItem.setVisible( false );

        	if(config!=null && config.has("ui/dark")) {
            	this.dark = config.getBoolean("ui/dark");
        	} else {
            	this.dark = isMacOSDark();
        	}
    		
    		if(dark) {
        		FlatMacDarkLaf.setup();
        	} else {
        		FlatLightLaf.setup();
        	}
			
    		// "system" is documented but unreliable here: apple.awt.transparentTitleBar
    		// (IDEFrame) makes the title bar strip show the app's own (FlatLaf-painted)
    		// background, but the NATIVE title TEXT color comes from this property's
    		// NSAppearance value, read once at native window creation - if that read
    		// doesn't line up with the app's own resolved "dark" (e.g. "system" not
    		// reliably tracking actual OS state at this point in startup, or a
    		// config-forced ui/dark that disagrees with the OS), the text stays the
    		// OTHER appearance's color - black text on FlatMacDarkLaf's dark fill,
    		// unreadable. Setting the concrete NSAppearance name tied to our own
    		// already-resolved `dark` removes the ambiguity entirely.
    		System.setProperty( "apple.awt.application.appearance", dark ? "NSAppearanceNameDarkAqua" : "NSAppearanceNameAqua" );
    		System.setProperty( "apple.laf.useScreenMenuBar", "true" );
    		System.setProperty( "apple.awt.application.name", "IDE" );
    	}
    	
		// Linux
		if( SystemInfo.isLinux ) {
        	if(config!=null && config.has("ui/dark")) {
            	this.dark = config.getBoolean("ui/dark");
        	} else {
            	this.dark = isLinuxDark();
        	}
        	
			// enable custom window decorations
			JFrame.setDefaultLookAndFeelDecorated( true );
			JDialog.setDefaultLookAndFeelDecorated( true );
    		if(dark) {
        		FlatDarkLaf.setup();
        	} else {
        		FlatLightLaf.setup();
        	}
		}    	
    	
		// Windows
		if( SystemInfo.isWindows ) {
        	if(config!=null && config.has("ui/dark")) {
            	this.dark = config.getBoolean("ui/dark");
        	} else {
            	this.dark = isWindowsDark();
        	}

        	if(dark) {
        		FlatDarkLaf.setup();
        	} else {
        		FlatLightLaf.setup();
        	}
		}    	
    	
    	if(dark) {
        	UIManager.put( "Component.focusedBorderColor", new Color(0) );
        	UIManager.put( "Component.focusWidth", 0 );
        	UIManager.put( "Component.innerFocusWidth", 0 );
        	UIManager.put( "Component.outerFocusWidth", 0 );
    	}
    }
    
    public boolean isDark() {
    	return dark;
    }
    
    private static boolean isMacOSDark() {
        try {
            Process process = Runtime.getRuntime().exec(
                new String[]{"defaults", "read", "-g", "AppleInterfaceStyle"}
            );
            // "defaults" exits with 0 only when a dark style is set
            if(!process.waitFor(2, TimeUnit.SECONDS)) {
            	process.destroy();
            	return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
    private static boolean isWindowsDark() {
        try {
            Process process = Runtime.getRuntime().exec(
                "reg query HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize /v AppsUseLightTheme"
            );
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("AppsUseLightTheme")) {
                        // 0x0 = dark, 0x1 = light
                        return line.contains("0x0");
                    }
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }
    private static boolean isLinuxDark() {
        try {
            Process process = Runtime.getRuntime().exec(
                new String[]{"gsettings", "get", "org.gnome.desktop.interface", "gtk-theme"}
            );
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String theme = reader.readLine();
                return theme != null && theme.toLowerCase().contains("dark");
            }
        } catch (Exception e) {
            return false;
        }
    }
}