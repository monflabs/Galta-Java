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
package org.monflabs.galtajs.rt.builtins.primitives;

import java.util.Locale;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BasePrototype;

/**
 * Base prototype class.
 */
public abstract class BasePrimitivePrototype extends BasePrototype {
	
	protected BasePrimitivePrototype(JSEnvironment env) {
		super(env);
	}
	
	public abstract Class<?> getNativeClass();

	//
	// Utilities
	//
	public static Locale findLocale(String locale) {
		if(locale!=null) {
			Locale loc = Locale.forLanguageTag(locale);
			if(loc==null) {
				throw RuntimeUtil.rangeError("Invalid locale {0}", locale);
			}
    		return loc;
		}
		return null;
	}
}
