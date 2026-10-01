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
package org.monflabs.json.impexp.impl;

import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.monflabs.json.JsonException;
import org.monflabs.json.impexp.JsonContent;
import org.monflabs.json.impexp.JsonSource;
import org.monflabs.json.impexp.replication.RangeFilter;

/**
 * JSON source.
 */
public abstract class JsonSourceImpl implements JsonSource {
	
	private RangeFilter rangeFilter;
	
	public void init(RangeFilter rangeFilter) {
		this.rangeFilter = rangeFilter;
	}
	
	@Override
	public void close() {
	}
	
	public RangeFilter getRangeFilter() {
		return rangeFilter;
	}

	@Override
	public long estimatedCount() {
		return -1;
	}

	@Override
	public final Stream<JsonContent> stream(RangeFilter filter) {
		Stream<JsonContent> stream;
		try {
			init(filter);
			stream = createJsonContentStream();
		} catch(RuntimeException | Error e) {
			// init() may have opened resources (a reader...) before failing: the caller
			// never gets a stream to close, so release them here
			try {
				close();
			} catch(Exception ce) {
				e.addSuppressed(ce);
			}
			throw e;
		}
		if(stream!=null) {
			return stream.onClose(this::close);
		} else {
			close();
			return Stream.empty();
		}
	}

	protected Stream<JsonContent> createJsonContentStream() {
		Iterator<JsonContent> it = createJsonContentIterator();
		if(it!=null) {
			Spliterator<JsonContent> spit = Spliterators.spliteratorUnknownSize(it, Spliterator.NONNULL); 
			Stream<JsonContent> stream = StreamSupport.stream(spit, false);
			return stream;
		}
		return null;
	}

	protected Iterator<JsonContent> createJsonContentIterator() {
		throw new JsonException(null,"Json source is missing an iterator implementation");
	}
}