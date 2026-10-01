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
package org.monflabs.ui.swing.frame;

import java.awt.event.WindowEvent;

import javax.swing.JFrame;

/**
 * 
 */
@SuppressWarnings("serial")
public class JFrameCloseable extends JFrame {
	
	// Number of frames currently open; only touched on the event dispatch thread
	private static int frameCount = 0;
	
	private boolean counted;
	
    public JFrameCloseable() {
    	
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
            	if(!counted) {
            		counted = true;
            		frameCount++;
            	}
            }
            @Override
			public void windowClosing(WindowEvent e) {
            	if(canClose()) {
            		dispose();
            	}
            }
            @Override
            public void windowClosed(WindowEvent e) {
            	// Also reached when the frame is disposed from code, not only through the close button
            	if(counted) {
            		counted = false;
            		frameCount--;
            		if(frameCount==0) {
            			onLastFrameClosed();
            		}
            	}
            }
        });        
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);        
        setLocationByPlatform(true);
    }
    
    private static volatile boolean exitOnLastFrameClosed = true;

    /**
     * Whether closing the last open {@code JFrameCloseable} exits the JVM
     * (true by default: these frames are the application's main windows).
     */
    public static boolean isExitOnLastFrameClosed() {
    	return exitOnLastFrameClosed;
    }

    /**
     * Sets whether closing the last open frame exits the JVM: false for code
     * that keeps running without the frames (a host application, tests).
     * Subclasses can also override {@link #onLastFrameClosed()}.
     */
    public static void setExitOnLastFrameClosed(boolean exit) {
    	exitOnLastFrameClosed = exit;
    }

    /**
     * Called on the event dispatch thread when the last open frame has been
     * closed (disposed). Exits the JVM with status 0, unless
     * {@link #setExitOnLastFrameClosed(boolean)} turned it off.
     */
    protected void onLastFrameClosed() {
    	if(exitOnLastFrameClosed) {
    		System.exit(0);
    	}
    }
    
    public boolean canClose() {
    	return true;
    }
} 