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
package tests.collections;


import org.monflabs.util.cache.LRUCache;

import tests.ProjectTestCase;

public class LRUCacheTest extends ProjectTestCase {
	
    public void testCacheStartsEmpty() {
    	LRUCache<Integer,Integer> c = new LRUCache<>(2);
        assertEquals(c.get(1), null);
    }

    public void testSetBelowCapacity() {
    	LRUCache<Integer,Integer> c = new LRUCache<>(2);
        c.put(1, 1);
        assertEquals(c.get(1), Integer.valueOf(1));
        assertEquals(c.get(2), null);
        c.put(2, 4);
        assertEquals(c.get(1), Integer.valueOf(1));
        assertEquals(c.get(2), Integer.valueOf(4));
    }

    public void testCapacityReachedOldestRemoved() {
    	LRUCache<Integer,Integer> c = new LRUCache<>(2);
        c.put(1, 1);
        c.put(2, 4);
        c.put(3, 9);
        assertEquals(c.get(1), null);
        assertEquals(c.get(2), Integer.valueOf(4));
        assertEquals(c.get(3), Integer.valueOf(9));
    }

    public void testGetRenewsEntry() {
    	LRUCache<Integer,Integer> c = new LRUCache<>(2);
        c.put(1, 1);
        c.put(2, 4);
        assertEquals(c.get(1), Integer.valueOf(1));
        c.put(3, 9);
        assertEquals(c.get(1), Integer.valueOf(1));
        assertEquals(c.get(2), null);
        assertEquals(c.get(3), Integer.valueOf(9));
    }
}
