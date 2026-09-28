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

import org.monflabs.galtajs.rt.builtins.privatename.PrivateName;

/**
 * Spec's [[PrivateElements]] internal slot: a dedicated storage structure for
 * an object's private fields/methods/accessors, keyed by identity-compared
 * PrivateName tokens. Deliberately NOT the same map as ObjectPropertiesMap's
 * String/Symbol storage (SymbolPropertyMap) and NEVER routed through
 * JSAccessor/ProxyAccessor - private element access must bypass the
 * prototype chain, Proxy traps, and [[Extensible]]/frozen/sealed checks
 * entirely, per spec. Unlike SymbolPropertyMap, getObjectFlags() does NOT
 * delegate to the containing object - private field writes must succeed
 * even on a frozen/sealed/non-extensible object (a genuine, deliberate spec
 * quirk: PrivateFieldAdd/Set never consult [[Extensible]] at all).
 */
public class PrivateElementsMap extends ObjectPropertiesMap<PrivateName> {

	@Override
	public int getObjectFlags() {
		return 0;
	}
}
