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
package org.monflabs.json.serialization.classes.collections;

import java.util.Set;

/**
 * Adapter for <code>java.util.Set</code>, serialized as a JSON array.
 * <p>
 * The default adapter reads the sets back as <code>LinkedHashSet</code>, so the order of the
 * array is kept.
 */
public class SetClassAdapter extends CollectionClassAdapter {

	/**
	 * The adapter of the <code>Set</code> interface, read back as a <code>LinkedHashSet</code>.
	 */
	public SetClassAdapter() {
		this(null);
	}

	/**
	 * The adapter of a set class, registered for this class only and read back as an
	 * instance of it if it can be instantiated (or else a compatible standard set). A null
	 * class is the <code>Set</code> interface.
	 */
	public SetClassAdapter(Class<? extends Set<?>> setClass) {
		super(setClass!=null ? setClass : Set.class, CollectionUtil.implementation(setClass!=null ? setClass : Set.class, false));
	}
}
