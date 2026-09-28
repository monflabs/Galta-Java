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
package org.monflabs.galtajs.rt.builtins.standard.global;

import java.util.HashMap;
import java.util.Map;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.NativeObject;
import org.monflabs.util.StringFormat;

/**
 * Global objects.
 *
 * This map to globalThis. It includes all the standard objects, the dynamic (undeclared) variables
 * and globalThis itself. 
 */
public class StandardObjects extends NativeObject {

	public static final String GLOBAL_NAME = "globalThis";
	public static final String GLOBAL_ALIAS = "global";

	public StandardObjects(JSEnvironment env) {
		super(env);
	}
	
	@Override
	public String getClassName() {
		return GLOBAL_NAME;
	}

	// The realm's intrinsics (%Error%, %Promise%, ...), captured once the environment is
	// configured. The engine must use these, not whatever the (script-writable) global
	// object holds at the time: `globalThis.Error = 5` or `delete globalThis.Promise`
	// changes the global binding, not the intrinsic.
	private volatile Map<String,Constructor> intrinsics;

	/**
	 * Snapshot the constructors defined on the global object as the realm intrinsics.
	 * Called by the environment once the standard objects and libraries are configured.
	 */
	public void captureIntrinsics() {
		Map<String,Constructor> m = new HashMap<>();
		// Constructors are non-enumerable properties
		for(Map.Entry<?,?> e: entrySet(false)) {
			if(e.getKey() instanceof String k && e.getValue() instanceof Constructor c) {
				m.put(k, c);
			}
		}
		this.intrinsics = m;
	}

	public Constructor getConstructor(String typeName) {
		Map<String,Constructor> m = intrinsics;
		if(m!=null) {
			Constructor c = m.get(typeName);
			if(c!=null) {
				return c;
			}
		}
		// Not an intrinsic (e.g. added later by the host)
		if(getProperty(typeName) instanceof Constructor c) {
			return c;
		}
		// Not a TypeError: creating one goes through this method (e.g. for "TypeError" itself)
		throw new IllegalStateException(StringFormat.format("Constructor for type {0} does not exist",typeName));
	}
}