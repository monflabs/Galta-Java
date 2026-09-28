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
package org.monflabs.ui.swing.settings;

import java.awt.event.ActionListener;
import java.util.function.Consumer;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import java.awt.event.ItemListener;

import org.monflabs.util.Console;
import org.monflabs.util.PathUtil;
import org.monflabs.util.StringUtil;

/**
 * Manage how some controls persist there state.
 * 
 * @author priand
 *
 */
public class ComponentStateManager {

	public abstract class Persister {
		private Persister next;
		private String path;
		protected Persister(String path) {
			this.path = PathUtil.POSIX.concat(getBasePath(), path);
		}
		public String getPath() {
			return path;
		}
		public abstract void register();
		public abstract void unregister();
	}
	
	private final class Combobox extends Persister {
		private JComboBox<?> combo;
		private int defaultIndex;
		private Consumer<Integer> init;
		private ActionListener listener;
		private Combobox(JComboBox<?> combo, String path, int defaultIndex, Consumer<Integer> init) {
			super(path);
			this.combo = combo;
			this.defaultIndex = defaultIndex;
			this.init = init;
		}
		@Override
		public void register() {
			unregister();
			int index = UiPersistentSettings.get().getInt(getPath(),defaultIndex);
			// The items can have changed since the index was saved
			if(index<-1 || index>=combo.getItemCount()) {
				index = defaultIndex>=-1 && defaultIndex<combo.getItemCount() ? defaultIndex : -1;
			}
			combo.setSelectedIndex(index);
			listener = (e) -> {
		        UiPersistentSettings.get().updateValues( (u) -> {
		        	u.put(getPath(), combo.getSelectedIndex());
		        });
			};
			combo.addActionListener(listener);
	        if(init!=null) {
	        	init.accept(index);
	        }
		}
		@Override
		public void unregister() {
			if(listener!=null) {
				combo.removeActionListener(listener);
			}
		}
	}
	
	private final class Checkbox extends Persister {
		private JCheckBox checkbox;
		private boolean defaultValue;
		private Consumer<Boolean> init;
		private ItemListener listener;
		private Checkbox(JCheckBox checkbox, String path, boolean defaultValue, Consumer<Boolean> init) {
			super(path);
			this.checkbox = checkbox;
			this.defaultValue = defaultValue;
			this.init = init;
		}
		@Override
		public void register() {
			unregister();
			boolean selected = UiPersistentSettings.get().getBoolean(getPath(),defaultValue);
			checkbox.setSelected(selected);
			listener = (e) -> {
		        UiPersistentSettings.get().updateValues( (u) -> {
			        u.put(getPath(), checkbox.isSelected());
		        } );
			};
			// An ItemListener only fires when the selection changes (a ChangeListener also
			// fires on rollover and press)
			checkbox.addItemListener(listener);
	        if(init!=null) {
	        	init.accept(selected);
	        }
		}
		@Override
		public void unregister() {
			if(listener!=null) {
				checkbox.removeItemListener(listener);
			}
		}
	}
	
	private boolean enabled;
	private String basePath;
	private Persister first;
	
	public ComponentStateManager(String basePath) {
		this.enabled = StringUtil.isNotEmpty(basePath);
		this.basePath = basePath;
	}
	
	public String getBasePath() {
		return basePath;
	}
	
	public boolean isEnabled() {
		return enabled;
	}

	public void unregister() {
		if(isEnabled()) {
			for(Persister p=first; p!=null; p=p.next) {
				p.unregister();
			}
			first = null;
		}
	}
	
	public ComponentStateManager add(Persister p) {
		if(isEnabled()) {
			p.next = first;
			first = p;
			p.register();
		}
		return this;
	}

	public ComponentStateManager add(JComboBox<?> combo, String path, Consumer<Integer> init) {
		return add(combo, path, 0, init);
	}
	public ComponentStateManager add(JComboBox<?> combo, String path, int defaultIndex, Consumer<Integer> init) {
		return add(new Combobox(combo, path, defaultIndex, init));
	}
	public ComponentStateManager add(JCheckBox checkbox, String path, Consumer<Boolean> init) {
		return add(checkbox, path, false, init);
	}
	public ComponentStateManager add(JCheckBox checkbox, String path, boolean defaultIndex, Consumer<Boolean> init) {
		return add(new Checkbox(checkbox, path, defaultIndex, init));
	}
	
	public void dump() {
		for(Persister p=first; p!=null; p=p.next) {
			Console.log("Persister: {0}, {1}", p.getClass(), p.getPath());
		}
	}
}
