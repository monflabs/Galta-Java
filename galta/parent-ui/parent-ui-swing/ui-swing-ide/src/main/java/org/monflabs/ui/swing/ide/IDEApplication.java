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
package org.monflabs.ui.swing.ide;

import org.monflabs.json.config.CustomJsonConfig;
import org.monflabs.ui.UIApplication;
import org.monflabs.ui.swing.UISwingApplication;
import org.monflabs.ui.swing.ide.theme.IDETheme;
import org.monflabs.util.ObjectBuilder;
import org.monflabs.util.config.Config;

/**
 * IDE Application
 */
public class IDEApplication extends UISwingApplication {

	public static IDEApplication get() {
		return (IDEApplication)UIApplication.get();
	}
	
	public static class Builder extends ObjectBuilder<IDEApplication> {
		private Config config;
		private IDETheme theme;
		private Builder() {}
		public Builder config(Config config) {
			this.config = config;
			return this;
		}
		public Builder theme(IDETheme theme) {
			this.theme = theme;
			return this;
		}
		@Override
		protected void validate() {
			if(config==null) {
				config = CustomJsonConfig.newBuilder().build();
			}
			if(theme==null) {
				theme = new IDETheme(config);
			}
		}
		@Override
		protected IDEApplication _build() {
			return new IDEApplication(this);
		}
	}	
	public static Builder newBuilder() {
		return new Builder();
	}
	

	private IDEApplication(Builder builder) {
		super(builder.config, builder.theme);
	}

	@Override
	public IDETheme getTheme() {
		return (IDETheme)super.getTheme();
	}
}