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
package org.monflabs.galtajs.rt.builtins.primitives.array.arraylike;

import java.util.Comparator;

import org.eclipse.jdt.annotation.NonNull;
import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.rt.RuntimeUtil;

public class JSArrayString extends JSBaseArray {

	public static JSArrayString of(JSEnvironment env, @NonNull String str) {
		return new JSArrayString(env,str);
	}

	private String str;
	
	private JSArrayString(JSEnvironment env, @NonNull String str) {
		super(env);
		this.str = str;
	}

	@Override
	public long arrayLength() {
		return str.length();
	}
	@Override
	public boolean arraySetLength(long size, DESC_CHECK check) {
		// A real String wrapper's "length" is a non-writable data property -
		// per [[Set]]'s OrdinarySetWithOwnDescriptor, a Set against a
		// non-writable property always fails regardless of whether the new
		// value equals the current one, so a STRICT (Throw=true) caller must
		// always throw here (confirmed via push/throws-with-string-receiver.js,
		// which expects a throw even for `Array.prototype.push.call('')` -
		// zero items pushed, length unchanged, but step 7's trailing
		// Set(O,"length",len,true) still runs and still fails).
		if(check==DESC_CHECK.STRICT) {
			throw RuntimeUtil.typeError("String is not mutable");
		}
		if(check==DESC_CHECK.NONE) {
			if(size!=str.length()) {
				if(size<str.length()) {
					str = str.substring(0,(int)size);
				} else {
					while(str.length()<size) {
						str += " ";
					}
				}
			}
			return true;
		}
		return false;
	}
	@Override
	public Object arrayGet(long index, Object defaultValue) {
		int size = str.length();
		if(index<0 || index>=size) {
			return defaultValue;
		}
		// A single-character STRING (matching StringAccessor.getOwnProperty's
		// own convention for the same access, "abc"[0]) - not a raw Java
		// char/Character, which no accessor is registered for and which
		// downstream JS-value handling (e.g. ToNumber) doesn't recognize.
		int i = (int)index;
		return str.substring(i,i+1);
	}
	
	
	@Override
	public JSArray arrayAdd(Object value, DESC_CHECK check) {
		throw RuntimeUtil.typeError("String is not mutable");
	}
	@Override
	public JSArray arrayAdd(long index, Object value, DESC_CHECK check) {
		throw RuntimeUtil.typeError("String is not mutable");
	}
	@Override
	public boolean arraySet(long index, Object value, DESC_CHECK check) {
		throw RuntimeUtil.typeError("String is not mutable");
	}
	@Override
	public boolean arrayDelete(long index, DESC_CHECK check) {
		throw RuntimeUtil.typeError("String is not mutable");
	}
	@Override
	public boolean arrayRemove(long index, DESC_CHECK check) {
		// Can't delete a character, strings are immutable - respect the
		// caller's DESC_CHECK the same way arraySet/arrayDelete/arrayAdd
		// above do, rather than the unrelated global env.isStrictMode()
		// dialect toggle (which doesn't reflect whether the actual calling
		// code - e.g. pop()'s internal Set(O,"length",...) - runs with
		// Throw=true; confirmed via pop/throws-with-string-receiver.js,
		// where the caller passes DESC_CHECK.STRICT explicitly).
		if(index>=0 && index<str.length()) {
			if(check!=DESC_CHECK.NONE && RuntimeUtil.isStrictCheck(check)) {
				throw RuntimeUtil.typeError("Cannot delete property '{0}' of object '{1}'", index, RuntimeUtil.objectTypeName(getEnvironment(), str));
			}
			return false;
		}
		return true;
	}
	@Override
	public void arraySort(Comparator<? super Object> c, DESC_CHECK check) {
		// Sorting 0 or 1 elements never needs to write anything back, so it
		// must not throw even on an immutable receiver - confirmed via
		// sort/call-with-primitive.js's `[].sort.call("")`.
		if(str.length()>1) {
			throw RuntimeUtil.typeError("String is not mutable");
		}
	}
}
