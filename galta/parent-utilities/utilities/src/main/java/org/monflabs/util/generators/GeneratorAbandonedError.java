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
 * Thrown inside a generator body, at its yield() point, when the body yielded again
 * after being asked to return ({@link GeneratorReturnSignal}) by a consumer that gave
 * up on it (it was closed while a finally block yielded, or its consumer was
 * interrupted). It is an {@link Error} so that a body catching {@code RuntimeException}
 * or {@code Exception} around its yields still unwinds. A body that yields yet again is
 * left parked in that yield() for good.
 */
@SuppressWarnings("serial")
public class GeneratorAbandonedError extends Error {

	public GeneratorAbandonedError() {
		super("The generator was abandoned by its consumer", null, false, false);
	}
}
