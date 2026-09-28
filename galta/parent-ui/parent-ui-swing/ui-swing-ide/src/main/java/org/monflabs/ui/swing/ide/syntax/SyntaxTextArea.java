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
package org.monflabs.ui.swing.ide.syntax;

import java.awt.Color;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;

import org.fife.rsta.ui.GoToDialog;
import org.fife.rsta.ui.search.FindDialog;
import org.fife.rsta.ui.search.ReplaceDialog;
import org.fife.rsta.ui.search.SearchEvent;
import org.fife.rsta.ui.search.SearchListener;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.RSyntaxUtilities;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.FoldIndicatorStyle;
import org.fife.ui.rtextarea.Gutter;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;
import org.fife.ui.rtextarea.SearchResult;
import org.monflabs.ui.swing.ide.IDEApplication;
import org.monflabs.util.StringFormat;

/**
 *
 */
@SuppressWarnings("serial")
public class SyntaxTextArea extends RSyntaxTextArea {
	
	private int arrowPosition = -1;
	
	private boolean searchAction=true;
	private boolean replaceAction=true;
	private boolean gotoLineAction=true;
	
	public SyntaxTextArea() {
		try {
			Theme t = IDEApplication.get().getTheme().getSyntaxAreaTheme();
			if (t != null) {
				t.apply(this);
			}
		} catch (Exception ex) {
		}

		setAnimateBracketMatching(true);
		setAutoIndentEnabled(true);
		setBracketMatchingEnabled(true);
		setCloseCurlyBraces(true);
		setCodeFoldingEnabled(true);
		setMarginLineEnabled(true);
		
		
		//
		// Extra actions actions
		//
		{
			// Map the keystroke to the action name
			KeyStroke keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_F, menuShortcutMask());
	        InputMap inputMap = getInputMap(JComponent.WHEN_FOCUSED);
	        inputMap.put(keyStroke, "findAction");
	        
	        // Associate the action name with the action
	        ActionMap actionMap = getActionMap();
	        actionMap.put("findAction", new ShowFindDialogAction(this) );
		}
		{
			// Map the keystroke to the action name
			KeyStroke keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_R, menuShortcutMask());
	        InputMap inputMap = getInputMap(JComponent.WHEN_FOCUSED);
	        inputMap.put(keyStroke, "replaceAction");
	        
	        // Associate the action name with the action
	        ActionMap actionMap = getActionMap();
	        actionMap.put("replaceAction", new ShowReplaceDialogAction(this) );
		}
		{
			// Map the keystroke to the action name
			KeyStroke keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_G, menuShortcutMask());
	        InputMap inputMap = getInputMap(JComponent.WHEN_FOCUSED);
	        inputMap.put(keyStroke, "gotoLine");
	        
	        // Associate the action name with the action
	        ActionMap actionMap = getActionMap();
	        actionMap.put("gotoLine", new GoToLineAction(this) );
		}
	}
	
    /**
     * The platform menu shortcut modifier: Cmd on macOS, Ctrl elsewhere.
     */
    static int menuShortcutMask() {
    	try {
    		return Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
    	} catch(UnsupportedOperationException e) { // HeadlessException included
    		return InputEvent.CTRL_DOWN_MASK;
    	}
    }
    
    @Override
    public void addNotify() {
        super.addNotify(); // Always call the superclass method
        
        // Configure the enclosing scrollpane
        Gutter gutter = RSyntaxUtilities.getGutter(this);
        if(gutter!=null) {
        	gutter.setFoldIndicatorStyle(FoldIndicatorStyle.CLASSIC);
        	gutter.setFoldIndicatorEnabled(true);
        }
    }
	
    public void setArrowPosition(int position) {
    	if(this.arrowPosition != position) {
	        this.arrowPosition = position;
	        repaint(); // Request a repaint so the arrow gets drawn
    	}
    }
    
    public boolean isSearchAction() {
		return searchAction;
	}

	public void setSearchAction(boolean searchAction) {
		this.searchAction = searchAction;
		if(!searchAction && findDialog!=null) {
			findDialog.dispose();
			this.findDialog = null;
		}
	}

	public boolean isReplaceAction() {
		return replaceAction;
	}

	public void setReplaceAction(boolean replaceAction) {
		this.replaceAction = replaceAction;
		if(!replaceAction && replaceDialog!=null) {
			replaceDialog.dispose();
			this.replaceDialog = null;
		}
	}

	public boolean isGotoLineAction() {
		return gotoLineAction;
	}

	public void setGotoLineAction(boolean gotoLineAction) {
		this.gotoLineAction = gotoLineAction;
	}

	
	
	public void displayFind() {
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (arrowPosition >= 0) {
            try {
                @SuppressWarnings("deprecation")
				Rectangle rect = modelToView(arrowPosition);
                int arrowX = 4 + rect.x - 10; // Adjust as needed
                int arrowY = rect.y + rect.height / 2; // Center the arrow vertically in the line

                // Example arrow drawing
                g.setColor(Color.YELLOW); // Arrow color
                g.fillPolygon(new int[]{arrowX, arrowX + 5, arrowX}, new int[]{arrowY - 5, arrowY, arrowY + 5}, 3);
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        }
    }
    
    
    //
    // Actions
    //
    
    
	private class GoToLineAction extends AbstractAction {
    	private SyntaxTextArea textArea;
		GoToLineAction(SyntaxTextArea textArea) {
			super("Go To Line...");
			this.textArea = textArea;
		}
		@Override
		public void actionPerformed(ActionEvent e) {
			if(!gotoLineAction) {
				return;
			}
			if (findDialog!=null && findDialog.isVisible()) {
				findDialog.setVisible(false);
			}
			if (replaceDialog!=null && replaceDialog.isVisible()) {
				replaceDialog.setVisible(false);
			}
			Window w = SwingUtilities.getWindowAncestor(SyntaxTextArea.this);
			GoToDialog dialog = w instanceof Dialog d ? new GoToDialog(d) : new GoToDialog(w instanceof Frame f ? f : null);
			dialog.setMaxLineNumberAllowed(textArea.getLineCount());
			dialog.setVisible(true);
			int line = dialog.getLineNumber();
			if (line>0) {
				try {
					textArea.setCaretPosition(textArea.getLineStartOffset(line-1));
				} catch (BadLocationException ble) { // Never happens
					UIManager.getLookAndFeel().provideErrorFeedback(textArea);
					ble.printStackTrace();
				}
			}
		}
	}    
	private class ShowFindDialogAction extends AbstractAction {
		ShowFindDialogAction(SyntaxTextArea textArea) {
			super("Find...");
		}
		@Override
		public void actionPerformed(ActionEvent e) {
			if(!searchAction) {
				return;
			}
			initFindDialog();
			if (replaceDialog!=null &&replaceDialog.isVisible()) {
				replaceDialog.setVisible(false);
			}
			findDialog.setVisible(true);
		}
	}
	private class ShowReplaceDialogAction extends AbstractAction {
		ShowReplaceDialogAction(SyntaxTextArea textArea) {
			super("Replace...");
		}
		@Override
		public void actionPerformed(ActionEvent e) {
			if(!replaceAction || !isEditable()) {
				return;
			}
			initReplaceDialog();
			if (findDialog!=null && findDialog.isVisible()) {
				findDialog.setVisible(false);
			}
			replaceDialog.setVisible(true);
		}
	}    
    
    
    //
    // Search dialogs
    //
    
	private FindDialog findDialog;
	private ReplaceDialog replaceDialog;
    
	private void initFindDialog() {
		if(findDialog==null) {
			if(searchListener==null) {
				searchListener = new DefaultSearchListener(this);
			}
			
			Window w = SwingUtilities.getWindowAncestor(SyntaxTextArea.this);
			findDialog = w instanceof Dialog d ? new FindDialog(d, searchListener) : new FindDialog(w instanceof Frame f ? f : null, searchListener);
		}
	}
	private void initReplaceDialog() {
		if(replaceDialog==null) {
			if(searchListener==null) {
				searchListener = new DefaultSearchListener(this);
			}
			
			Window w = SwingUtilities.getWindowAncestor(SyntaxTextArea.this);
			replaceDialog = w instanceof Dialog d ? new ReplaceDialog(d, searchListener) : new ReplaceDialog(w instanceof Frame f ? f : null, searchListener);

			if(findDialog!=null) {
				// This ties the properties of the two dialogs together (match case,
				// regex, etc.).
				SearchContext context = findDialog.getSearchContext();
				replaceDialog.setSearchContext(context);
			}
		}
	}

	
	private DefaultSearchListener searchListener;

    protected static class DefaultSearchListener implements SearchListener {
    	
    	private SyntaxTextArea textArea;
    	
    	private DefaultSearchListener(SyntaxTextArea textArea) {
    		this.textArea = textArea;
    	}
    	
    	@Override
		public void searchEvent(SearchEvent e) {
    		SearchEvent.Type type = e.getType();
    		SearchContext context = e.getSearchContext();
    		SearchResult result=null;

    		switch (type) {
    			case MARK_ALL -> {
    				result = SearchEngine.markAll(textArea, context);
    			}
    			case FIND -> {
    				result = SearchEngine.find(textArea, context);
    				if (!result.wasFound() || result.isWrapped()) {
    					// Wrap around: restart from the beginning, or from the end when searching backward
    					Caret c = textArea.getCaret();
    					int dot = c.getDot();
    					c.setDot(context.getSearchForward() ? 0 : textArea.getDocument().getLength());
        				result = SearchEngine.find(textArea, context);
        				if (!result.wasFound() || result.isWrapped()) {
        					c.setDot(dot);
        				}
    					//UIManager.getLookAndFeel().provideErrorFeedback(textArea);
    				}
    			}
    			case REPLACE -> {
    				result = SearchEngine.replace(textArea, context);
    				if (!result.wasFound() || result.isWrapped()) {
    					//UIManager.getLookAndFeel().provideErrorFeedback(textArea);
    				}
    			}
    			case REPLACE_ALL -> {
    				result = SearchEngine.replaceAll(textArea, context);
    				JOptionPane.showMessageDialog(null, StringFormat.format("{0} occurrences replaced.", result.getCount()));
    			}
    		}

//    		String text;
//    		if (result.wasFound()) {
//    			text = "Text found; occurrences marked: " + result.getMarkedCount();
//    		}
//    		else if (type==SearchEvent.Type.MARK_ALL) {
//    			if (result.getMarkedCount()>0) {
//    				text = "Occurrences marked: " + result.getMarkedCount();
//    			}
//    			else {
//    				text = "";
//    			}
//    		}
//    		else {
//    			text = "Text not found";
//    		}
//    		//statusBar.setLabel(text);
    	}
    	
    	@Override
    	public String getSelectedText() {
    		return textArea.getSelectedText();
    	}    
    }

}
