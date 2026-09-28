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
package org.monflabs.util.iterators;

import java.util.Iterator;
import java.util.function.IntFunction;

public interface IntIterator {
	
    boolean hasNext();
    
    int next();
    
    default void remove() {
		throw new UnsupportedOperationException();
    }

    public default Iterator<Integer> objectIterator() {
    	return iterator(Integer::valueOf);
    }
    public default <T> Iterator<T> iterator(IntFunction<T> wrapper) {
    	return new Iterator<T>() {
    	    @Override
    		public boolean hasNext() {
    	        return IntIterator.this.hasNext();
    	    }
    	    @Override
    		public T next() {
    	        return wrapper.apply(IntIterator.this.next());
    	    }
    	    @Override
    		public void remove() {
    	    	IntIterator.this.remove();
    	    }
    	};
    }
}