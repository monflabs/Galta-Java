/*
 * Copyright (c) 2023-2026 Philippe Riand
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
import org.monflabs.ui.swing.ide.IDEApplication;

import com.monflabs.playground.swing.PlaygroundFrame;

import playground.impl.JavaPlaygroundLayout;
import playground.impl.engine.java.JavaExecutionEngine;
import playground.impl.engine.jshell.JShellExecutionEngine;

/**
 * The Java playground: Main.java snippets compiled in memory, Main.jshell snippets run by
 * JShell.
 */
public class JavaPlayground {

    public static void main(String[] args) {
    	SwingUtilities.invokeLater( () -> {
    		IDEApplication.newBuilder()
				.config(null)
				.build();
    		
	    	java.net.URL logoUrl = ClassLoader.getSystemResource("swing/app-logo.png");
	    	Toolkit kit = Toolkit.getDefaultToolkit();
	    	Image img = kit.createImage(logoUrl);
	    	
	        final Taskbar taskbar = Taskbar.getTaskbar();
	        try {
	            taskbar.setIconImage(img);
	        } catch (final UnsupportedOperationException e) {
	            System.out.println("The os does not support: 'taskbar.setIconImage'");
	        } catch (final SecurityException e) {
	            System.out.println("There was a security exception for: 'taskbar.setIconImage'");
	        }

	        configure();

	    	PlaygroundFrame f = new PlaygroundFrame();
	    	f.initialSize();   
	        f.setVisible(true);
    	});
    }

    /**
     * Configures the playground: the snippets, the engines and the layout. Shared by the
     * desktop application and its browser (CheerpJ) build.
     */
    protected static void configure() {
    	FileSystem vfs = createSnippetVFS();

    	PlaygroundConfiguration.get().setFrameTitle("Java Playground");
    	PlaygroundConfiguration.get().setSnippetFactory(new SnippetFactory(vfs));
    	PlaygroundConfiguration.get().setExecutionEngineFactory( new ExecutionEngineFactory() {
			private static String[] FILES =  new String[] {JavaExecutionEngine.DEFAULT_JAVA,JShellExecutionEngine.DEFAULT_JSHELL};
			@Override
			public ExecutionEngine createExecutionEngine(ExecutionContext ctx) {
	    		FileSystem v = ctx.getSnippetFs();
	    		if(Files.exists(v.getPath(JShellExecutionEngine.DEFAULT_JSHELL))) {
	        		return new JShellExecutionEngine(ctx);
	    		}
	    		return new JavaExecutionEngine(ctx);
			}
			@Override
			public String[] getMainExecutableFileNames() {
				return FILES;
			}
			// The scratchpad is a JShell buffer: statements run as typed, with no class
			@Override
			public String getMainFileName() {
				return JShellExecutionEngine.DEFAULT_JSHELL;
			}
    	});

    	PlaygroundConfiguration.get().setLayout(new JavaPlaygroundLayout());
    }

    private static FileSystem createSnippetVFS() {
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
				.classLoader(JavaPlayground.class.getClassLoader())
				.root("snippets")
				.build();
	}
}