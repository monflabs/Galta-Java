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
package org.monflabs.galtajs.rt.builtins.standard.finalizationregistry;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.NativeObject;

/**
 * Internal state for a FinalizationRegistry instance - [[CleanupCallback]]
 * and [[Cells]]. Cleanup firing is best-effort rather than driven by a
 * dedicated background thread: the spec itself only requires
 * HostEnqueueFinalizationRegistryCleanupJob to run "at the host's
 * discretion" (timing is deliberately unspecified), so this drains its
 * {@link ReferenceQueue} - collected targets the JVM has already reclaimed -
 * opportunistically whenever {@link #register} or {@link #unregister} run on
 * ANY FinalizationRegistry, invoking the JS callback synchronously (already
 * on a valid JS thread with context established, so no cross-thread
 * marshaling is needed, unlike Atomics.waitAsync's background-thread case).
 */
public class BuiltinFinalizationRegistry extends NativeObject {

	private final Object cleanupCallback;
	private final List<Cell> cells = new ArrayList<>();
	private final ReferenceQueue<Object> queue = new ReferenceQueue<>();

	private static final class Cell {
		final WeakReference<Object> targetRef;
		final Object heldValue;
		// Held weakly, as the spec requires: register(obj, held, obj) - the common
		// idiom - must not keep obj alive through its own token.
		// null means "no token" (spec's ~empty~)
		final WeakReference<Object> unregisterToken;

		Cell(WeakReference<Object> targetRef, Object heldValue, Object unregisterToken) {
			this.targetRef = targetRef;
			this.heldValue = heldValue;
			this.unregisterToken = unregisterToken!=null ? new WeakReference<>(unregisterToken) : null;
		}
	}

	public BuiltinFinalizationRegistry(JSEnvironment env, Object cleanupCallback) {
		super(env);
		this.cleanupCallback = cleanupCallback;
	}

	@Override
	public String getClassName() {
		return BuiltinFinalizationRegistryConstructor.CLASSNAME;
	}

	@Override
	protected Object getDefaultPrototype() {
		return BuiltinFinalizationRegistryPrototype.get(getEnvironment());
	}

	public void register(Object target, Object heldValue, Object unregisterToken) {
		drainAndCleanup();
		cells.add(new Cell(new WeakReference<>(target, queue), heldValue, unregisterToken));
	}

	public boolean unregister(Object unregisterToken) {
		drainAndCleanup();
		boolean removed = false;
		Iterator<Cell> it = cells.iterator();
		while(it.hasNext()) {
			Cell c = it.next();
			if(c.unregisterToken!=null && c.unregisterToken.get()==unregisterToken) {
				it.remove();
				removed = true;
			}
		}
		return removed;
	}

	private void drainAndCleanup() {
		List<Object> dueHeldValues = null;
		Reference<?> ref;
		while((ref = queue.poll())!=null) {
			Iterator<Cell> it = cells.iterator();
			while(it.hasNext()) {
				Cell c = it.next();
				if(c.targetRef==ref) {
					it.remove();
					if(dueHeldValues==null) {
						dueHeldValues = new ArrayList<>();
					}
					dueHeldValues.add(c.heldValue);
					break;
				}
			}
		}
		if(dueHeldValues!=null) {
			JSEnvironment env = getEnvironment();
			for(Object heldValue: dueHeldValues) {
				RuntimeUtil.call(env, cleanupCallback, RuntimeUtil.UNDEFINED, new Object[] { heldValue });
			}
		}
	}
}
