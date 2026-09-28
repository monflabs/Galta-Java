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
package org.monflabs.galtajs.rt.builtins.privatename;

import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;

/**
 * Implemented by any runtime object type that can hold [[PrivateElements]] -
 * ordinary objects (JSObjectImpl) and also Proxy exotic objects
 * (BuiltinProxy), since a Proxy can itself become `this` for a class
 * construction (a base class constructor returning `new Proxy(this, {...})`,
 * per spec's derived-class-construction algorithm) and per spec, private
 * field access on such a Proxy must still work directly, entirely bypassing
 * its trap handlers (test262's privatefield-on-proxy.js and friends).
 * RuntimeUtil.getPrivateField/setPrivateField/hasPrivateField dispatch
 * through this interface rather than a concrete class check.
 */
public interface PrivateElementsHolder {

	boolean hasPrivateElement(PrivateName name);
	Object getPrivateElementValue(PrivateName name);
	PropertyDescriptor getPrivateElementDescriptor(PrivateName name);
	void definePrivateElement(PrivateName name, Object value, PropertyDescriptor descriptor);
	boolean setPrivateElementValue(PrivateName name, Object value);
}
