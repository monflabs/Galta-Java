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

import java.util.function.Function;
import java.util.stream.Stream;

public final class StreamUtil {
	private StreamUtil() {}
	
    /**
     * @deprecated the same as {@link ExceptionUtil.ThrowingFunction}, which
     * {@link #usingWihException} accepts
     */
    @Deprecated
    @FunctionalInterface
    public interface ThrowingFunction<T, R, E extends Exception> extends ExceptionUtil.ThrowingFunction<T, R, E> {
    }
    
    
    public static <T, R> R using(Stream<T> stream, Function<? super Stream<T>, ? extends R> fn) {
        try (stream) {
            return fn.apply(stream);
        }
    }
    public static <T, R, E extends Exception> R usingWihException(Stream<T> stream, ExceptionUtil.ThrowingFunction<? super Stream<T>, ? extends R, E> fn) throws E {
        try (stream) {
            return fn.apply(stream);
        }
    }
}
