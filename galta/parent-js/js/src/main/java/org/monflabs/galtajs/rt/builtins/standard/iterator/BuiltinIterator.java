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
package org.monflabs.galtajs.rt.builtins.standard.iterator;

import java.util.Iterator;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.YieldStarDelegateResult;
import org.monflabs.galtajs.rt.builtins.NativeObject;

/**
 * 
 */
public abstract class BuiltinIterator extends NativeObject implements Iterator<Object> {
	
	private Iterator<Object> iterator;
	private String className;
	
	@SuppressWarnings("unchecked")
	public BuiltinIterator(JSEnvironment env, Iterator<?> iterator, String className) {
		super(env);
		this.iterator = (Iterator<Object>)iterator;
		this.className = className;
		// Symbol.toStringTag belongs on the shared PROTOTYPE
		// (%XIteratorPrototype%[Symbol.toStringTag]), not as an own
		// property of every instance - each concrete iterator's own
		// prototype class (BuiltinMapIteratorPrototype etc., or
		// BuiltinIteratorHelperPrototype for the generic case) already sets
		// it. An own-instance copy here shadowed the prototype's entirely,
		// so redefining/deleting it on the prototype (as tests do) was
		// never observable (confirmed via
		// Object/prototype/toString/symbol-tag-*-builtin.js).
	}

	@Override
	protected abstract Object getDefaultPrototype();
	
	@Override
	public String getClassName() {
		return className;
	}

	public Iterator<Object> getIterator() {
		return iterator;
	}

	@Override
	public boolean hasNext() {
		return iterator.hasNext();
	}

	@Override
	public Object next() {
		Object v = iterator.next();
		if(v instanceof YieldStarDelegateResult ysr) {
			// Internal (Java-level) iteration - used by for-of, spread, etc. - only
			// cares about the plain value, not the {value,done} shape yield*
			// delegation forwards for a JS-level .next()/.throw()/.return() caller.
			return RuntimeUtil.getProperty(getEnvironment(), ysr.rawResult(), "value");
		}
		return v;
	}

	@Override
	public void remove() {
		iterator.remove();
	}
}
