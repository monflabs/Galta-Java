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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
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
    
    // The OS appearance is read from a command at startup: never let a stuck
    // command hang the application (the theme is typically built while the UI
    // is starting, possibly on the event dispatch thread)
    private static final long COMMAND_TIMEOUT_SECONDS = 2;

    /**
     * Runs a command with a time limit, returning its exit code and standard
     * output, or null if it could not run or did not finish in time.
     */
    static CommandResult runCommand(String... command) {
    	Process process = null;
        try {
            process = new ProcessBuilder(command)
            		.redirectError(ProcessBuilder.Redirect.DISCARD)
            		.start();
            process.getOutputStream().close();
            if(!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            	return null;
            }
            // the output of these queries is a line or two: it fits in the
            // pipe, so reading it once the process is done cannot block
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return new CommandResult(process.exitValue(), output);
        } catch (InterruptedException e) {
        	Thread.currentThread().interrupt();
        	return null;
        } catch (IOException | RuntimeException e) {
            return null;	// command not available: not dark
        } finally {
        	if(process!=null && process.isAlive()) {
        		process.destroyForcibly();
        	}
        }
    }

    record CommandResult(int exitCode, String output) {}

    private static boolean isMacOSDark() {
        // "defaults" exits with 0 only when a dark style is set
        CommandResult r = runCommand("defaults", "read", "-g", "AppleInterfaceStyle");
        return r!=null && r.exitCode()==0;
    }
    private static boolean isWindowsDark() {
        CommandResult r = runCommand("reg", "query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize", "/v", "AppsUseLightTheme");
        if(r==null) {
        	return false;
        }
        for(String line: r.output().split("\\R")) {
            if (line.contains("AppsUseLightTheme")) {
                // 0x0 = dark, 0x1 = light
                return line.contains("0x0");
            }
        }
        return false;
    }
    private static boolean isLinuxDark() {
        CommandResult r = runCommand("gsettings", "get", "org.gnome.desktop.interface", "gtk-theme");
        return r!=null && r.output().toLowerCase(Locale.ROOT).contains("dark");
    }
}
