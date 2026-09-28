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
package org.monflabs.galtajs.jsonfactory;

import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;

/**
 * Specialized map to store JavaScript symbol.
 * Allows the use of property descriptors to manage the access.
 * It also use the flags of the contain object to manage the state (frozen, sealed, ...)
 */
public class SymbolPropertyMap extends ObjectPropertiesMap<Symbol>  {

	private ObjectPropertiesMap<String> parent;
	
    public SymbolPropertyMap() {
    }
    public SymbolPropertyMap(ObjectPropertiesMap<String> parent) {
    	this.parent = parent;
    }
	
    @Override
	public int getObjectFlags() {
    	return parent!=null ? parent.getObjectFlags() :0;
    }
}
