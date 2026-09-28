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
import org.monflabs.galtajs.rt.builtins.standard.arguments.Arguments;
import org.monflabs.util.StringFormat;

public class JSArrayArguments extends JSBaseArray {

	public static JSArrayArguments of(@NonNull Arguments arguments) {
		return new JSArrayArguments(arguments.getEnvironment(),(Arguments)arguments);
	}

	private Arguments arguments;
	
	private JSArrayArguments(JSEnvironment env, @NonNull Arguments arguments) {
		super(env);
		this.arguments = arguments;
	}

	@Override
	public long arrayLength() {
		return arguments.size();
	}
	@Override
	public Object arrayGet(long index, Object defaultValue) {
		// `Arguments.get(int)` always defaults to UNDEFINED internally
		// (ignores any caller-supplied default), so a `delete
		// arguments[N]` hole was indistinguishable from "present but
		// undefined" to the shared hole-skipping iteration helpers
		// (confirmed via reduce/15.4.4.21-8-b-iii-1-26.js/-27.js, where
		// `delete arguments[0]` should make reduce's no-initial-value
		// accumulator seed from index 1, not index 0's leftover
		// `undefined`). A prior attempt to fix this via
		// `arguments.getOwnProperty(...,NOT_AVAILABLE,...)` hit an NPE
		// ("env is null") - root-caused to THIS class's own now-removed
		// `getEnvironment()` override, an auto-generated stub that
		// unconditionally returned null instead of delegating to
		// JSBaseArray's real one; unrelated to Arguments' lookup
		// machinery, which is otherwise correct (getOwnProperty already
		// distinguishes deleted-and-disconnected indices from a genuinely
		// present undefined).
		return index>=0 && index<arguments.size() ? arguments.getOwnProperty(index, defaultValue, arguments) : defaultValue;
	}
	
	
	@Override
	public boolean arraySetLength(long size, DESC_CHECK check) {
		throw new IllegalStateException(StringFormat.format("Arguments size is readonly", getClass()));
	}
	@Override
	public JSArray arrayAdd(Object value, DESC_CHECK check) {
		throw new IllegalStateException(StringFormat.format("Arguments size is readonly", getClass()));
	}
	@Override
	public JSArray arrayAdd(long index, Object value, DESC_CHECK check) {
		throw new IllegalStateException(StringFormat.format("Arguments size is readonly", getClass()));
	}
	@Override
	public boolean arraySet(long index, Object value, DESC_CHECK check) {
		throw new IllegalStateException(StringFormat.format("Arguments size is readonly", getClass()));
	}
	@Override
	public boolean arrayDelete(long index, DESC_CHECK check) {
		throw new IllegalStateException(StringFormat.format("Arguments size is readonly", getClass()));
	}
	@Override
	public boolean arrayRemove(long index, DESC_CHECK check) {
		throw new IllegalStateException(StringFormat.format("Arguments size is readonly", getClass()));
	}

	@Override
	public void arraySort(Comparator<? super Object> c, DESC_CHECK check) {
		throw new IllegalStateException(StringFormat.format("Arguments size is readonly", getClass()));
	}

}
