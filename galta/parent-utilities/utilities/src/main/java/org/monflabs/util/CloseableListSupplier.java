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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class CloseableListSupplier<T extends AutoCloseable> implements AutoCloseable, Supplier<T>  {
	
	public static <T extends AutoCloseable> CloseableListSupplier<T> of(Supplier<T> factory) {
		return new CloseableListSupplier<>(factory);
	}
	
	private Supplier<T> factory;
	private List<T> list;

	private CloseableListSupplier( Supplier<T> factory ) {
		this.factory = factory;
	}
	
	@Override
	public synchronized T get() {
		if(list==null) {
			list = new ArrayList<>();
		}
		T wrapped = factory.get();
		list.add(wrapped);
		return wrapped;
	}
	
	/**
	 * Closes every supplied object, even when some of them fail: the first failure is
	 * thrown (as a ForwardRuntimeException) once all are closed, the others attached to
	 * it as suppressed exceptions.
	 */
	@Override
	public synchronized void close() {
		if(list!=null) {
			List<T> l = list;
			list = null;
			ForwardRuntimeException failure = null;
			for( T t: l) {
				try {
					if(t!=null) {
						t.close();
					}
				} catch(Exception ex) {
					if(failure==null) {
						failure = new ForwardRuntimeException(ex, "Error while closing stream");
					} else {
						failure.addSuppressed(ex);
					}
				}
			}
			if(failure!=null) {
				throw failure;
			}
		}
	}
}