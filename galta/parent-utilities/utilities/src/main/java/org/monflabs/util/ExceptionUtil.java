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

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;


public class ExceptionUtil {
	
	@FunctionalInterface
	public interface ThrowingRunnable<E extends Exception> {
	    public void run() throws E;
	}
	
	@FunctionalInterface
	public interface ThrowingConsumer<T, E extends Exception> {
		public void accept(T t) throws E;
	}
	
	@FunctionalInterface
	public interface ThrowingSupplier<T, E extends Exception> {
		public T get() throws E;
	}
	
	@FunctionalInterface
	public interface ThrowingFunction<T, R, E extends Exception> {
		public R apply(T t) throws E;
	}
	
	public static Runnable unchecked(ThrowingRunnable<?> tc) {
	    return () -> {
	        try {
	            tc.run();
	        } catch (RuntimeException e) {
	            throw e; // Already unchecked: don't wrap it
	        } catch (Exception e) {
	            throw new ForwardRuntimeException(e);
	        }
	    };
	}
	
	public static <T> Consumer<T> unchecked(ThrowingConsumer<T, ?> tc) {
	    return t -> {
	        try {
	            tc.accept(t);
	        } catch (RuntimeException e) {
	            throw e; // Already unchecked: don't wrap it
	        } catch (Exception e) {
	            throw new ForwardRuntimeException(e);
	        }
	    };
	}
	
	public static <T> Supplier<T> unchecked(ThrowingSupplier<T, ?> ts) {
	    return () -> {
	        try {
	            return ts.get();
	        } catch (RuntimeException e) {
	            throw e; // Already unchecked: don't wrap it
	        } catch (Exception e) {
	            throw new ForwardRuntimeException(e);
	        }
	    };
	}
	
	public static <T, R> Function<T, R> unchecked(ThrowingFunction<T, R, ?> tf) {
	    return t -> {
	        try {
	            return tf.apply(t);
	        } catch (RuntimeException e) {
	            throw e; // Already unchecked: don't wrap it
	        } catch (Exception e) {
	            throw new ForwardRuntimeException(e);
	        }
	    };
	}
}
