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
package org.monflabs.util.dependencies;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Sorts items so that every item comes after the items it depends on.
 * <p>
 * Items are looked up by equals()/hashCode(): equal items are the same item, and appear only
 * once in the result. A dependency reported by getDependencies() that is not part of the
 * collection is ignored (it is considered satisfied), while an item reported by
 * getDependingItems() that is not part of the collection is an error.
 */
public class DependencyEngine<T> {
	
	public interface DependencyCollector<T> {
		public void addDependency(T dependency);
	}
	public static class DependencyFinder<T> {
		public String objectString(Object object) {
			return object!=null ? object.toString() : "<null>";
		}
		public void getDependencies(T source, DependencyCollector<T> collector) {
		}
		public void getDependingItems(T source, DependencyCollector<T> collector) {
		}
	}
	private static final class ItemWrapper<T> implements DependencyCollector<T> {
		private T item;
		private Set<T> dependencies;
		ItemWrapper(T item) {
			this.item = item;
		}
		@Override
		public void addDependency(T dependency) {
			if(dependencies==null) {
				dependencies=Collections.newSetFromMap(new IdentityHashMap<T,Boolean>());
			}
			dependencies.add(dependency);
		}
	}

	private DependencyFinder<T> dependencyFinder;

	public DependencyEngine(DependencyFinder<T> dependencyFinder) {
		this.dependencyFinder = dependencyFinder;
	}
	
	public List<T> sortCollectionAsList(Collection<T> items) throws RuntimeException {
		List<T> result = new ArrayList<>();
		sortList(items,result);
		return result;
	}
	/**
	 * Sorts a mutable list in place. If the sort fails (circular or missing dependency),
	 * the list is left untouched.
	 */
	public void sortList(List<T> items) throws RuntimeException {
		if(items.isEmpty()) {
			return;
		}
		// Sorted into a separate list first: the caller's list must not be left
		// half emptied when a DependencyException is thrown
		List<T> sorted = new ArrayList<>(items.size());
		sortList(items,sorted);
		if(sorted.size()==items.size()) {
			// Also works for fixed-size lists (e.g. Arrays.asList())
			for(int i=0; i<sorted.size(); i++) {
				items.set(i, sorted.get(i));
			}
		} else {
			// Equal items were merged
			items.clear();
			items.addAll(sorted);
		}
	}
	
	private void sortList(Collection<T> source, List<T> items) throws RuntimeException {
		if(source.isEmpty()) {
			return;
		}

		// There is no IdentityHashMap that also maintains the order
		// But we want to maintain it to keep the result deterministic (great for tests!)
		// Map<T,ItemWrapper<T>> itemMap = new IdentityHashMap<>();
		Map<T,ItemWrapper<T>> itemMap = new LinkedHashMap<>();
		
		// Create the list of item wrappers
		for(T item: source) {
			ItemWrapper<T> w = new ItemWrapper<>(item);
			itemMap.put(item, w);
		}
		
		gatherDependencies(itemMap);
		
		// Now, we should sort the itemList based on the dependencies
		boolean circularReference;
		do {
			// First, we assume that there is some circular references
			circularReference = true;
			
			// Then we browse all the objects
			//for(ItemWrapper<T> w: itemMap.values()) {
			for(Iterator<Map.Entry<T,ItemWrapper<T>>> it = itemMap.entrySet().iterator(); it.hasNext(); ) {
				Map.Entry<T,ItemWrapper<T>> e = it.next();
				ItemWrapper<T> w = e.getValue();
				
				// Look if all its dependencies have already been processed
				boolean dependenciesAllProcessed = true;
				if(w.dependencies!=null && !w.dependencies.isEmpty()) {
					for(Iterator<T> it2=w.dependencies.iterator(); it2.hasNext(); ) {
						Object d = it2.next();
						if(itemMap.containsKey(d)) {
							dependenciesAllProcessed = false;
							break;
						}
					}
				}
				if(dependenciesAllProcessed) {
					it.remove();
					items.add(w.item);
					circularReference = false;
				}
			}
			
			if(circularReference) {
				StringBuilder b = new StringBuilder();
				b.append("Circular references detected between the following items: ");
				boolean first = true;
				for(ItemWrapper<T> w: itemMap.values()) {
					if(!first) {
						b.append(", ");
					} else {
						first = false;
					}
					b.append(dependencyFinder.objectString(w.item));
				}
				throw new DependencyException(null,b.toString());
			}
			
		} while(!itemMap.isEmpty());
	}
		
	@SuppressWarnings("hiding")
	private final class ItemWrapperCollector<T> implements DependencyCollector<T> {
		Map<T,ItemWrapper<T>> wrappers;
		ItemWrapper<T> w;
		private ItemWrapperCollector(Map<T,ItemWrapper<T>> wrappers) {
			this.wrappers = wrappers;
		}
		@Override
		public void addDependency(Object dependency) {
			ItemWrapper<T> hw = wrappers.get(dependency);
			if(hw!=null) {
				hw.addDependency(w.item);
			} else {
				throw new DependencyException(null,"Dependency {0} not found for item {1}", 
						dependencyFinder.objectString(dependency), 
						dependencyFinder.objectString(w.item));
			}
		}
	}
	private void gatherDependencies(Map<T,ItemWrapper<T>> wrappers) throws RuntimeException {
		ItemWrapperCollector<T> wl = new ItemWrapperCollector<>(wrappers);
		for(ItemWrapper<T> w: wrappers.values()) {
			dependencyFinder.getDependencies((T)w.item,w);
			
			// Read the depending items
			wl.w = w;
			dependencyFinder.getDependingItems(w.item,wl);
		}
	}
}
