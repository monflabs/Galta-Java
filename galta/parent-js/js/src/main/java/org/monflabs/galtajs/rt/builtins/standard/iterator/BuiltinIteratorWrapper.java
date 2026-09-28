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
import java.util.NoSuchElementException;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.NativeObject;

/**
 * %WrapForValidIteratorPrototype%-backed instance, produced by Iterator.from()
 * when the argument isn't already Iterator.prototype-descended. Unlike
 * JavaIterator, "next" is read once at construction (a real [[Get]] - walks
 * the prototype chain and invokes an inherited getter, per spec
 * GetIteratorDirect's "Let nextMethod be ? Get(iterated, "next")" - confirmed
 * via test262 Iterator/from/get-next-method-only-once.js, where "next" is a
 * class-prototype getter, not an own property) WITHOUT requiring it to be
 * callable yet (a plain {} argument is valid - it just fails if next() is
 * ever actually invoked), and "return" is looked up fresh on every call
 * rather than cached.
 */
public class BuiltinIteratorWrapper extends NativeObject implements Iterator<Object> {

	private JSEnvironment env;
	private Object wrapped;
	private Callable nextMethod;

	private boolean shouldReadNext = true;
	private boolean done;
	private Object value;

	public BuiltinIteratorWrapper(JSEnvironment env, Object wrapped) {
		super(env);
		this.env = env;
		this.wrapped = wrapped;
		Object m = RuntimeUtil.getProperty(env, wrapped, "next");
		this.nextMethod = m instanceof Callable c ? c : null;
	}

	public Object getWrapped() {
		return wrapped;
	}

	@Override
	protected Object getDefaultPrototype() {
		return WrapForValidIteratorPrototype.get(env);
	}

	@Override
	public String getClassName() {
		return BuiltinIteratorConstructor.CLASSNAME;
	}

	private void readNext() {
		if(nextMethod==null) {
			throw RuntimeUtil.typeError("next is not a function");
		}
		Object res = nextMethod.call(wrapped,RuntimeUtil.EMPTY_PARAMS);
		if(RuntimeUtil.isPrimitiveType(res)) {
			throw RuntimeUtil.typeError("next() should return an object");
		}
		JSAccessor a = env.getAccessor(res);
		this.done = RuntimeUtil.toBoolean(env,a.getOwnProperty(res,"done",Boolean.FALSE,res));
		this.value = this.done ? RuntimeUtil.UNDEFINED : a.getOwnProperty(res,"value",RuntimeUtil.UNDEFINED,res);
		this.shouldReadNext = false;
	}

	@Override
	public boolean hasNext() {
		if(shouldReadNext) {
			readNext();
		}
		return !done;
	}

	@Override
	public Object next() {
		if(shouldReadNext) {
			readNext();
		}
		if(!done) {
			this.shouldReadNext = true;
			return value;
		}
		throw new NoSuchElementException();
	}
}
