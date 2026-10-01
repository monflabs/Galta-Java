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

import java.util.List;

/**
 * Adapter for <code>java.util.List</code>, serialized as a JSON array.
 * <p>
 * The element adapter is the first generic parameter; without one (a raw list, or a list
 * serialized at the top level), the elements are kept as is. The default adapter reads the
 * lists back as <code>ArrayList</code>.
 */
public class ListClassAdapter extends CollectionClassAdapter {

	/**
	 * The adapter of the <code>List</code> interface, read back as an <code>ArrayList</code>.
	 */
	public ListClassAdapter() {
		this(null);
	}

	/**
	 * The adapter of a list class, registered for this class only and read back as an
	 * instance of it if it can be instantiated (or else a compatible standard list). A null
	 * class is the <code>List</code> interface.
	 */
	public ListClassAdapter(Class<? extends List<?>> listClass) {
		super(listClass!=null ? listClass : List.class, CollectionUtil.implementation(listClass!=null ? listClass : List.class, false));
	}
}
