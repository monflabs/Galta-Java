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
package org.monflabs.galtajs.rt.builtins.standard.promise;

import org.monflabs.galtajs.rt.builtins.Callable;

/**
 * [[PromiseCapability]] record.
 *
 * The [[Promise]] slot is a plain Object, not necessarily a BuiltinPromise -
 * NewPromiseCapability(C) can be called with ANY constructor C (e.g. a
 * completely unrelated function used via Promise.all.call(C, ...)), and the
 * object produced by `new C(executor)` need not be a real Promise instance
 * at all.
 */
public  class PromiseCapability {

    private Object promise;
    private Callable resolve; // set by constructor
    private Callable reject;  // set by constructor

    public PromiseCapability() {
    }
    public PromiseCapability(Object promise, Callable resolve, Callable reject) {
        this.promise = promise;
        this.resolve = resolve;
        this.reject = reject;
    }

	public Object getPromise() {
		return promise;
	}
	public void setPromise(Object promise) {
		this.promise = promise;
	}

	public Callable getResolve() {
		return resolve;
	}
	public void setResolve(Callable resolve) {
		this.resolve = resolve;
	}


	public Callable getReject() {
		return reject;
	}
	public void setReject(Callable reject) {
		this.reject = reject;
	}
}