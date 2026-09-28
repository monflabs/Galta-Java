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
package org.monflabs.util.generators;

/**
 * Thrown inside a generator body, at its currently suspended yield() point, when the
 * consumer forces an early completion (e.g. a JS generator's .return(value)). Unlike a
 * normal exception, callers that catch it are expected to immediately rethrow it (it must
 * not be caught by the generator body's own try/catch), while enclosing finally blocks
 * still run normally as the exception unwinds - matching a "return" completion.
 */
@SuppressWarnings("serial")
public class GeneratorReturnSignal extends RuntimeException {

	private final Object value;

	public GeneratorReturnSignal(Object value) {
		super(null, null, false, false);
		this.value = value;
	}

	public Object getValue() {
		return value;
	}
}
