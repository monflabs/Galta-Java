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
package org.monflabs.galtajs.rt.util.strings;

/**
 * Singleton representing an empty CharSequence
 */
public class EmptyCharSequence implements ConsSequence {
	
    public static final EmptyCharSequence INSTANCE = new EmptyCharSequence();
    
    private EmptyCharSequence() {}
    
    @Override
    public int length() {
        return 0;
    }
    
    @Override
    public char charAt(int index) {
        throw new StringIndexOutOfBoundsException(index);
    }
    
    @Override
    public CharSequence subSequence(int start, int end) {
        if (start != 0 || end != 0) {
            throw new StringIndexOutOfBoundsException("start=" + start + ", end=" + end);
        }
        return this;
    }
    
    @Override
    public String toString() {
        return "";
    }
    
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof CharSequence) {
            return ((CharSequence) obj).length() == 0;
        }
        return false;
    }
    
    @Override
    public int hashCode() {
        return 0;
    }
}