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
package org.monflabs.ui.swing.ide.frame;

import java.awt.Dimension;
import java.awt.Toolkit;

import org.monflabs.ui.swing.frame.JFrameCloseable;

import com.formdev.flatlaf.util.SystemInfo;

/**
 * 
 */
@SuppressWarnings("serial")
public class IDEFrame extends JFrameCloseable {
	
    public IDEFrame() {
    	if( SystemInfo.isMacOS ) {
    		if( SystemInfo.isMacFullWindowContentSupported ) {
				//getRootPane().putClientProperty( "apple.awt.fullWindowContent", true );
				getRootPane().putClientProperty( "apple.awt.transparentTitleBar", true );    		
			}
    	}
    }

    public void initialSize() {
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		int width  = (int) (screen.width  * 0.75);
		int height = (int) (screen.height * 0.75);
		setSize(width, height);   
    }
} 