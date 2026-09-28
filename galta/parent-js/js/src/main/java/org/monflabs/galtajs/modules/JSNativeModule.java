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
package org.monflabs.galtajs.modules;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSModuleDescriptor;
import org.monflabs.galtajs.jsonfactory.JSObject;

/**
 */
public class JSNativeModule extends AbstractModule {
	
	public JSNativeModule(JSEnvironment env, JSModuleDescriptor descriptor) {
		this(env,descriptor,null,null);
	}
	public JSNativeModule(JSEnvironment env, JSModuleDescriptor descriptor, Object defaultExport, JSObject namedExports) {
		super(env,descriptor);
		setDefaultExport(defaultExport);
		setNamedExports(namedExports!=null ? namedExports : JSObject.create(getEnvironment()));
	}
}
