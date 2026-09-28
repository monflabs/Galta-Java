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
package tests.dependencies;

import java.util.Arrays;
import java.util.List;

import org.monflabs.util.dependencies.DependencyEngine;
import org.monflabs.util.dependencies.DependencyEngine.DependencyCollector;
import org.monflabs.util.dependencies.DependencyEngine.DependencyFinder;

import tests.ProjectTestCase;

public class DependenciesTest extends ProjectTestCase {
	
	public static class Item {
		String name;
		Item[] dependencies;
		Item(String name) {
			this.name = name;
		}
		@Override
		public String toString() {
			return name;
		}
	}
	
	private static Item a = new Item("A");
	private static Item b = new Item("B");
	private static Item c = new Item("C");
	private static Item d = new Item("D");
	
	static {
		a.dependencies = new Item[] {};
		b.dependencies = new Item[] {a};
		c.dependencies = new Item[] {a,b};
		d.dependencies = new Item[] {a,b,c};
	}
	
	public static class ItemDependencies extends DependencyFinder<Object> {
		@Override
		public void getDependencies(Object source, DependencyCollector<Object> collector) {
			Item item = (Item)source;
			if(item.dependencies!=null) {
				for(int i=0; i<item.dependencies.length; i++) {
					collector.addDependency(item.dependencies[i]);
				}
			}
		}
	}

	public void testDirectDependencies() throws Exception {
		DependencyEngine<Object> eng = new DependencyEngine<>(new ItemDependencies());
		
		List<Object> list = Arrays.asList(d,a,c,b);
		List<Object> sorted = eng.sortCollectionAsList(list);
		
		assertSame(a, sorted.get(0));
		assertSame(b, sorted.get(1));
		assertSame(c, sorted.get(2));
		assertSame(d, sorted.get(3));
	}
}
