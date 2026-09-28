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
package org.monflabs.galtajs.jsonfactory;

import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonFactoryService;
import org.monflabs.json.java.JavaJsonFactory;

/**
 * Global JSON factory registered by the GaltaJS engine.
 * <p>
 * A {@link GaltaJsJsonFactory} belongs to one {@link org.monflabs.galtajs.JSEnvironment},
 * so there is no JVM-wide instance of it: the global factory stays the plain
 * {@link JavaJsonFactory}. Code that builds JSON values for scripts must use its
 * environment's factory ({@code env.getJsonFactory()}), not {@link JsonFactory#get()}.
 */
public class JavascriptJsonFactoryService implements JsonFactoryService {

	@Override
	public JsonFactory get() {
		return JavaJsonFactory.instance;
	}

}
