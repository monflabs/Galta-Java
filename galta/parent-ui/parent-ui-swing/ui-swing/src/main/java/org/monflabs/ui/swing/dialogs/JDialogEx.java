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
package org.monflabs.ui.swing.dialogs;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;

/**
 * Extended JDialog with a few helpers.
 * 
 * @author priand
 */
@SuppressWarnings("serial")
public class JDialogEx<T> extends JDialog {

	public static class Result<T> {
		private boolean hasValue;
		private T value;
		public Result() {
		}
		public Result(T value) {
			this.hasValue = true;
			this.value = value;
		}
		public boolean hasValue() {
			return hasValue;
		}
		public T getValue() {
			return value;
		}
	}
	
	private Result<T> result;

    public JDialogEx(String title) {
        setTitle(title);
    }
    
    @Override
	protected JRootPane createRootPane() {
        JRootPane rootPane = super.createRootPane();
        
        KeyStroke stroke = KeyStroke.getKeyStroke( KeyEvent.VK_ESCAPE, 0 );
        rootPane.registerKeyboardAction(
            new ActionListener() {
                @Override
				public void actionPerformed( ActionEvent actionEvent ) {
                	cancel();
                }
            },
            stroke,
            JComponent.WHEN_IN_FOCUSED_WINDOW );
        
        return rootPane;
    }

    
    public Result<T> openModal() {
    	this.result = null;
        setModal(true);
        setVisible(true);
        Result<T> r = result;
        result = null;
        return r!=null ? r : new Result<>();
    }
    
    public void ok() {
    	if(!canClose()) {
    		return;
    	}
    	this.result = readResult();
    	if(result==null) {
    		return;
    	}
    	this.dispose();
    }
    public void cancel() {
    	this.result = null;
    	this.dispose();
    }
    
    public boolean canClose() {
    	return true;
    }
    
    protected Result<T> readResult() {
    	return null;
    }
}
