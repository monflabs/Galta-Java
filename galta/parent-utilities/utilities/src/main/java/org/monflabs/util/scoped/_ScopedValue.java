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
package org.monflabs.util.scoped;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Temporary implementation, waiting for Java ScopedValue to be officially supported.
 * <p>
 * The value is bound for the duration of a {@link Carrier#run}/{@link Carrier#get}/{@link Carrier#call}
 * and restored afterwards, even when the operation throws.
 * <p>
 * Unlike java.lang.ScopedValue, the value is backed by an {@link InheritableThreadLocal}:
 * a thread created while a value is bound (including the virtual threads of an executor that
 * creates a thread per task) starts with that value and <b>keeps it for its whole life</b>,
 * after the scope ended. This is relied upon by GaltaJS, whose generator and async bodies run
 * on such threads and read the current JSContext. Don't create long-lived threads (e.g. a
 * thread pool) inside a scope when the value must not outlive it.
 */
public final class _ScopedValue<T> {
	
	public static class Carrier<T> {
        _ScopedValue<T> key;
        T value;
		Carrier(_ScopedValue<T> key, T value) {
			this.key = key;
			this.value = value;
		}
        public void run(Runnable op) {
        	ThreadLocal<T> t = key.threadLocal;
    		T old = t.get();
    		t.set(value);
    		try {
    			op.run();
    		} finally {
    			if(old!=null) {
    				t.set(old);
    			} else {
    				t.remove();
    			}
    		}
        }        
        public <R> R get(Supplier<? extends R> op) {
        	ThreadLocal<T> t = key.threadLocal;
    		T old = t.get();
    		t.set(value);
    		try {
    			return op.get();
    		} finally {
    			if(old!=null) {
    				t.set(old);
    			} else {
    				t.remove();
    			}
    		}
        }
        public <R> R call(Callable<? extends R> op) throws Exception {
        	ThreadLocal<T> t = key.threadLocal;
    		T old = t.get();
    		t.set(value);
    		try {
    			return op.call();
    		} finally {
    			if(old!=null) {
    				t.set(old);
    			} else {
    				t.remove();
    			}
    		}
        }
	}
	
	public static <T> _ScopedValue<T> newInstance() {
		return new _ScopedValue<T>();
	}

	public static <T> Carrier<T> where(_ScopedValue<T> key, T value) {
        return new Carrier<>(key, value);
	}

	//private ThreadLocal<T> threadLocal = new ThreadLocal<>();
	private InheritableThreadLocal<T> threadLocal = new InheritableThreadLocal<>();
	
	public T get() {
		return threadLocal.get();
	}
}
