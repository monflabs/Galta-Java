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
package org.monflabs.galtajs;

/**
 * Flags used internally to drive the JS engine configuration.
 * These values are static and are mostly for experimentation purposes.
 */
public class StaticConfiguration {
	
	public static final boolean ENABLE_PARENT_CONTEXT_CACHE_VARIABLES = true; // Enable caching of parent context variables for performance

	public static final boolean ENABLE_CONSSTRING = true; // Enable string concatenation optimization


	//
	// Transpiler options
	//
	public static final boolean TRANSPILER_NO_CLOSURE = true;
}
