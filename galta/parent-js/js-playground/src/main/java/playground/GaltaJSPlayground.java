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

import java.awt.Image;
import java.awt.Taskbar;
import java.awt.Toolkit;
import java.io.File;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.swing.SwingUtilities;

import org.monflabs.filesystem.delegate.PathDelegatingFileSystem;
import org.monflabs.filesystem.resources.ResourceFileSystem;
import org.monflabs.playground.ExecutionContext;
import org.monflabs.playground.ExecutionEngine;
import org.monflabs.playground.ExecutionEngineFactory;
import org.monflabs.playground.PlaygroundConfiguration;
import org.monflabs.playground.PlaygroundException;
import org.monflabs.playground.SnippetFactory;
import org.monflabs.json.config.JsonFileConfig;
import org.monflabs.ui.swing.ide.IDEApplication;
import org.monflabs.ui.swing.settings.UiPersistentSettings;
import org.monflabs.util.Console;
import org.monflabs.util.UserPath;

import com.monflabs.playground.galtajs.GaltaJSExecutionEngine;
import com.monflabs.playground.swing.PlaygroundFrame;

import playground.impl.GaltaJSPlaygroundFrame;
import playground.impl.GaltaJSPlaygroundLayout;

/**
 *
 *
 */
public class GaltaJSPlayground {
	
	// Launch fat jar
	//   java -jar jsplaygroud.jar
	// Remote debug
	//   java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:8000 -jar jsplaygroud.jar
    public static void main(String[] args) {
    	// Built on the main thread, before any component exists (as FlatLaf
    	// recommends): reading the OS appearance runs external commands, which
    	// must not hold the event dispatch thread
		IDEApplication.newBuilder()
			.config(null)
			.applicationName("GaltaJS Playground")
			.build();
		installSettings();
    	SwingUtilities.invokeLater( () -> {
            setTaskbarIcon();
            configure();

        	PlaygroundFrame f = new GaltaJSPlaygroundFrame();
        	f.initialSize();   
            f.setVisible(true);
    	});
    }

    /**
     * Shows the playground logo in the taskbar/dock, where the platform has
     * one that supports it (not on every desktop, nor headless).
     */
    private static void setTaskbarIcon() {
    	URL logoUrl = GaltaJSPlayground.class.getResource("/swing/icon.png");
    	if(logoUrl==null || !Taskbar.isTaskbarSupported()) {
    		return;
    	}
    	Taskbar taskbar = Taskbar.getTaskbar();
    	if(!taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
    		return;
    	}
    	try {
    		Image img = Toolkit.getDefaultToolkit().createImage(logoUrl);
    		taskbar.setIconImage(img);
    	} catch(UnsupportedOperationException | SecurityException e) {
    		Console.log(e);
    	}
    }

    /**
     * Persists the playground's UI state (options, window bounds, last
     * snippet) in {@code ~/.monflabs/playground-galtajs/settings.json}, unless
     * a store is already set.
     */
    protected static void installSettings() {
    	if(UiPersistentSettings.isAvailable()) {
    		return;
    	}
    	try {
    		UiPersistentSettings.set(JsonFileConfig.newBuilder()
    				.folder(UserPath.getMonflabsFolder().resolve("playground-galtajs"))
    				.fileName("settings.json")
    				.build());
    	} catch(RuntimeException e) {
    		// the playground works without persisted settings
    		Console.log(e);
    	}
    }

    protected static void configure() {
    	FileSystem fs = createSnippetFs();
    	PlaygroundConfiguration.get().setFrameTitle("GaltaJS Playground");
    	PlaygroundConfiguration.get().setEditable(!fs.isReadOnly());
    	PlaygroundConfiguration.get().setSnippetFactory(new SnippetFactory(fs));
    	PlaygroundConfiguration.get().setExecutionEngineFactory( new ExecutionEngineFactory() {
			private static String[] FILES =  new String[] {GaltaJSExecutionEngine.DEFAULT_JS};
			@Override
			public ExecutionEngine createExecutionEngine(ExecutionContext context) {
				return new GaltaJSExecutionEngine(context);
			}
			@Override
			public String[] getMainExecutableFileNames() {
				return FILES;
			}
    	});
    	PlaygroundConfiguration.get().setLayout(new GaltaJSPlaygroundLayout());
    }
    protected static FileSystem createSnippetFs() {
		// If there is a snippet physical dir, we are running from the IDE and thus we use the file system
		String userDir = System.getProperties().getProperty("user.dir");
		Path snippetDir = new File(userDir,"src/main/resources/snippets").toPath();
		if(Files.isDirectory(snippetDir)) {
			try {
				return PathDelegatingFileSystem.newBuilder()
					.root(snippetDir)
					.build();
			} catch(Exception e) {
				throw new PlaygroundException(e);
			}
		}

		return ResourceFileSystem.newBuilder()
				.classLoader(GaltaJSPlayground.class.getClassLoader())
				.root("snippets")
				.build();
	}
}