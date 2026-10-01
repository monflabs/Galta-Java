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
package org.monflabs.ui.swing.util;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Window;

/**
 * Some Swing utilities: window placement, component lookup and HiDPI scaling.
 * 
 * @author priand
 */
public class SwingUtil {

	/**
	 * Scales a size in pixels by the user scale factor of the look and feel
	 * (FlatLaf's {@code UIScale}: the scale applied when the default font is
	 * larger than usual, or set with {@code flatlaf.uiScale}). The size is
	 * unchanged at 100%.
	 */
	public static int scale(int size) {
		try {
			return com.formdev.flatlaf.util.UIScale.scale(size);
		} catch(RuntimeException | LinkageError e) {
			return size;
		}
	}
		
    public static void centerWindow(Window w) {
    	centerWindow(w, null);
    }
    public static void centerWindow(Window w, Window reference) {
        Rectangle bounds=w.getBounds();
        if(reference!=null) {
            Rectangle refBounds=reference.getBounds();
            bounds.x = refBounds.x + refBounds.width/2 - bounds.width/2;
            bounds.y = refBounds.y + refBounds.height/2 - bounds.height/2;
        } else {
            Dimension screenDim=w.getToolkit().getScreenSize();
            bounds.x= (screenDim.width-bounds.width)/2;
            bounds.y= (screenDim.height-bounds.height)/2;
        }
        
        w.setBounds(bounds);
    }
    
    public static <T> T findParentOfType(Component component, Class<T> cls) {
        while (component != null && !cls.isInstance(component)) {
            component = component.getParent();
        }
        return cls.cast(component);
    }
    
    @SuppressWarnings("unchecked")
	public static <T> T findFirstChildOfType(Container parent, Class<T> cls) {
    	for(Component c: parent.getComponents()) {
    		if(cls.isInstance(c)) {
    			return (T)c;
    		}
            if (c instanceof Container container) {
                T childResult = findFirstChildOfType(container, cls);
                if (childResult != null) {
                    return childResult;
                }
            }    	
        }
        return null;
    }
}


