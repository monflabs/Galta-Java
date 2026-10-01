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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

import javax.swing.JComponent;

import org.monflabs.util.StringFormat;

/**
 * Idle updater: runs the attached updaters when the AWT event queue becomes
 * empty, to refresh the state of components (enabled, visible, text...).
 * <p>
 * The event queue hook is only installed when the first updater is attached.
 * The components are held weakly: the updater of a {@link JComponent} is
 * stored on the component itself (a client property), so an updater
 * referencing its component does not keep it alive - a component that is
 * never detached can still be garbage collected.
 *
 * @author priand
 */
public class SwingIdleUpdater {

	private static final class Holder {
		static final SwingIdleUpdater INSTANCE = new SwingIdleUpdater();
	}

	public static SwingIdleUpdater get() {
		return Holder.INSTANCE;
	}

    protected class IdleDispatchEventQueue extends EventQueue {
        @Override
		protected void dispatchEvent(final AWTEvent event) {
        	super.dispatchEvent(event);

        	if(peekEvent()==null) {
        		updateComponents();
        	}
        }
    }

    private static final Object UPDATER_KEY = new Object() {
    	@Override
    	public String toString() {
    		return "SwingIdleUpdater.updater";
    	}
    };

    // Weak keys. The value is null for a JComponent (its updater is a client
    // property of the component), the updater itself for another component.
    private final Map<Component,Consumer<UIUpdater>> updaters = new WeakHashMap<>();
    private final UIUpdater updater = new UIUpdater();
    private boolean installed;

	protected SwingIdleUpdater() {
	}

	/**
	 * Pushes the idle-detecting event queue, once.
	 */
	protected synchronized void install() {
		if(!installed) {
			installed = true;
	        EventQueue eventQueue = Toolkit.getDefaultToolkit().getSystemEventQueue();
	        eventQueue.push(new IdleDispatchEventQueue());
		}
	}

	/**
	 * Whether the event queue hook is installed (after the first attach).
	 */
	public synchronized boolean isInstalled() {
		return installed;
	}

	@SuppressWarnings("unchecked")
	protected void updateComponents() {
		List<Map.Entry<Component,Consumer<UIUpdater>>> entries;
		synchronized(this) {
			if(updaters.isEmpty()) {
				return;
			}
			entries = new ArrayList<>(updaters.entrySet());
		}
		for(Map.Entry<Component,Consumer<UIUpdater>> e: entries) {
			Component c = e.getKey();
			if(c==null) {
				continue;
			}
			Consumer<UIUpdater> r = e.getValue();
			if(r==null && c instanceof JComponent jc) {
				r = (Consumer<UIUpdater>)jc.getClientProperty(UPDATER_KEY);
			}
			if(r!=null) {
				r.accept(updater);
			}
		}
	}

	/**
	 * Whether an updater is attached to the component.
	 */
	public synchronized boolean isAttached(Component c) {
		return updaters.containsKey(c);
	}

	/**
	 * Attaches the updater of a component.
	 *
	 * @throws IllegalStateException when one is already attached
	 */
    public void attach(Component c, Consumer<UIUpdater> r) {
    	synchronized(this) {
	    	if(updaters.containsKey(c)) {
	    		throw new IllegalStateException(StringFormat.format("Updater is already attached for component {0}",c));
	    	}
	    	if(c instanceof JComponent jc) {
	    		jc.putClientProperty(UPDATER_KEY, r);
	    		updaters.put(c, null);
	    	} else {
	    		updaters.put(c,r);
	    	}
    	}
    	install();
    }

	/**
	 * Detaches the updater of a component.
	 *
	 * @throws IllegalStateException when none is attached
	 */
    public synchronized void detach(Component c) {
    	if(!updaters.containsKey(c)) {
    		throw new IllegalStateException(StringFormat.format("Updater is not attached to the component {0}",c));
    	}
    	updaters.remove(c);
    	if(c instanceof JComponent jc) {
    		jc.putClientProperty(UPDATER_KEY, null);
    	}
    }
}
