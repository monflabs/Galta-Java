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
package org.monflabs.galtajs.rt.builtins.standard.map;

import java.util.Iterator;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.builtins.standard.iterator.BuiltinIterator;

/**
 * 
 */
public class BuiltinMapIterator extends BuiltinIterator{
	
	public BuiltinMapIterator(JSEnvironment env, Iterator<?> iterator) {
		super(env, iterator, "Map Iterator");		
	}

	@Override
	protected Object getDefaultPrototype() {
		return BuiltinMapIteratorPrototype.get(getEnvironment());
	}
}
