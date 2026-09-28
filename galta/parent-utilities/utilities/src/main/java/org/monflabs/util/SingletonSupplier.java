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
package org.monflabs.util;

import java.util.function.Supplier;

public class SingletonSupplier<T> implements Supplier<T>  {
	
	public static <T> SingletonSupplier<T> of(Supplier<T> factory) {
		return new SingletonSupplier<>(factory);
	}
	
	private Supplier<T> factory;
	private T wrapped;

	protected SingletonSupplier( Supplier<T> factory ) {
		this.factory = factory;
	}
	
	@Override
	public synchronized T get() {
		if(wrapped==null) {
			wrapped = factory.get();
		}
		return wrapped;
	}
}
