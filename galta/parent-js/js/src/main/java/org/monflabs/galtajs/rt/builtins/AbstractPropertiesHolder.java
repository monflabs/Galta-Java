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
package org.monflabs.galtajs.rt.builtins;

import org.monflabs.galtajs.jsonfactory.internal.JSObjectInternal;

public abstract class AbstractPropertiesHolder implements PropertiesHolder {

	private JSObjectInternal properties;

	public AbstractPropertiesHolder() {
	}

	@Override
	public JSObjectInternal getPropertiesObject() {
		return properties;
	}
	@Override
	public void setPropertiesObject(JSObjectInternal properties) {
		this.properties = properties;
	}
}
