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
package org.monflabs.json.impexp.db;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.monflabs.json.impexp.JsonKey;
import org.monflabs.util.iterators.Iterators;

/**
 * Select json records.
 * 
 * @author priand
 *
 */
public class JsonSelect {

	private MemoryJsonDb db;
	private String collection;
	private Predicate<JsonDbRecord> filter;

	JsonSelect(MemoryJsonDb db) {
		this.db = db;
	}
	
	public JsonSelect collection(String collection) {
		this.collection = collection;
		return this;
	}
	
	public JsonSelect filter(Predicate<JsonDbRecord> filter) {
		this.filter = filter;
		return this;
	}
	
	public int count() {
		return Iterators.size(records());
	}
	
	public JsonDbRecord first() {
		return Iterators.first(records());
	}
	
	public List<JsonDbRecord> collect() {
		return collect(new ArrayList<JsonDbRecord>());
	}
	
	public List<JsonDbRecord> collect(List<JsonDbRecord> list) {
		return Iterators.collect(records(),list);
	}
	
	public void forEach(Consumer<JsonDbRecord> f) {
		records().forEachRemaining(f);
	}
	
	public Stream<JsonDbRecord> stream() {
		return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(records(), Spliterator.ORDERED), false);
	}
	
	public Iterator<JsonDbRecord> records() {
		// Iterate a snapshot taken under the DB lock, so the DB can be modified while iterating
		return Iterators.filter(db.snapshotRecords().iterator(), (r) -> {
			if(collection!=null) {
				if(!collection.equals(r.getKey().getCollection())) {
					return false;
				}
			}
			if(filter!=null) {
				if(!filter.test(r)) {
					return false;
				}
			}
			return true;
		});
	}
	
	public List<JsonKey> keys() {
		List<JsonKey> keys = new ArrayList<>();
		for(Iterator<JsonDbRecord> it=records(); it.hasNext(); ) {
			keys.add(it.next().getKey());
		}
		return keys;
	}

}
