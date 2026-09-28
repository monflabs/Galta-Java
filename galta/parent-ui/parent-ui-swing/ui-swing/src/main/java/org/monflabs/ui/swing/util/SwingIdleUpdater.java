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

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.monflabs.util.StringFormat;

/**
 * IDLE Updater.
 * 
 * Used to update the UI when there is no event anymore in tha AWT event Querue
 * 
 * @author priand
 */
public class SwingIdleUpdater {

	private static SwingIdleUpdater instance = new SwingIdleUpdater();
	
	public static SwingIdleUpdater get() {
		return instance;
	}
	
    protected class IdleDispatchEventQueue extends EventQueue {
        @Override
		protected void dispatchEvent(final AWTEvent event) {
        	super.dispatchEvent(event);
        	
        	if(peekEvent()==null) {
        		updateComponents();
        		//Console.log("Swing IDLE");
        	}
        }
    }

    private Map<Component,Consumer<UIUpdater>> updaters = new IdentityHashMap<>();
    private UIUpdater updater = new UIUpdater();
	
	protected SwingIdleUpdater() {
        EventQueue eventQueue = Toolkit.getDefaultToolkit().getSystemEventQueue();
        eventQueue.push(new IdleDispatchEventQueue());
	}
	
	protected synchronized void updateComponents() {
		if(!updaters.isEmpty()) {
			for(Consumer<UIUpdater> r: updaters.values()) {
				r.accept(updater);
			}
		}
	}
    
    public synchronized void attach(Component c, Consumer<UIUpdater> r) {
    	if(updaters.containsKey(c)) {
    		throw new IllegalStateException(StringFormat.format("Updater is already attached for component {0}",c));
    	}
    	updaters.put(c,r);
    }
    
    public synchronized void detach(Component c) {
    	if(!updaters.containsKey(c)) {
    		throw new IllegalStateException(StringFormat.format("Updater is not attached to thr component {0}",c));
    	}
    	updaters.remove(c);
    }
    
}
