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
package org.monflabs.galtajs.rt.builtins.standard.JSON;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.builtins.NativeObject;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.json.stringifier.JsonStringifier.ReplacerRawJSON;

/**
 * RAW JSON Data.
 */
public class RawJSON extends NativeObject implements ReplacerRawJSON {

	private String rawContent;

	public RawJSON(JSEnvironment env, String rawContent) {
		super(env);
		this.rawContent = rawContent;
		// Spec 25.5.7 JSON.rawJSON: step 5-6, the returned object is
		// OrdinaryObjectCreate(null, ...) with an own "rawJSON" data
		// property (CreateDataPropertyOrThrow) - confirmed via test262
		// built-ins/JSON/rawJSON/returns-expected-object.js, which checks
		// Object.getPrototypeOf()===null and Object.getOwnPropertyNames()
		// is exactly ["rawJSON"].
		setOwnProperty("rawJSON", rawContent, PropertyDescriptor.DESC_DEFAULT);
		freeze();
	}

	@Override
	public String getClassName() {
		return "RawJSON";
	}

	@Override
	public String getRawContent() {
		return rawContent;
	}

	@Override
	protected Object getDefaultPrototype() {
		return null;
	}
}