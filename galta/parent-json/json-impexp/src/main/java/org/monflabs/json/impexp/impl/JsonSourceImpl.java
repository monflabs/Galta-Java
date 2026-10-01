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
import org.monflabs.json.impexp.replication.ReplicationSource;

/**
 * JSON source.
 * <p>
 * A source can be streamed several times, including concurrently: the state of a stream
 * (an open file, a reader...) is created by {@link #createJsonContentStream(RangeFilter)}
 * and released when the stream is closed.
 * <p>
 * The range filter passed to {@link #stream(RangeFilter)} is applied to the timestamps of
 * the contents (see {@link RangeFilter#accept(JsonContent)}), unless the source handles it
 * itself, see {@link #handlesRangeFilter()}.
 */
public abstract class JsonSourceImpl implements JsonSource {
	
	// The filter of the last stream, see getRangeFilter()
	private volatile RangeFilter rangeFilter;
	
	/**
	 * Called when a stream starts, before {@link #createJsonContentStream(RangeFilter)}.
	 * This should only validate the source: the state of the stream belongs to the stream.
	 */
	public void init(RangeFilter rangeFilter) {
		this.rangeFilter = rangeFilter;
	}
	
	/**
	 * Called when a stream is closed (or failed to start).
	 */
	@Override
	public void close() {
	}
	
	/**
	 * The range filter of the last stream that was started. A source that can be streamed
	 * concurrently should use the filter passed to {@link #createJsonContentStream(RangeFilter)}.
	 */
	public RangeFilter getRangeFilter() {
		return rangeFilter;
	}

	@Override
	public long estimatedCount() {
		return -1;
	}
	
	/**
	 * Whether the source applies the range filter itself. When false, the contents are
	 * filtered on their timestamp ({@link JsonContent#getTimestamp()}), a content without
	 * timestamp being always kept.
	 * <p>
	 * By default, a {@link ReplicationSource} handles the filter (it typically selects the
	 * contents by the date they were stored, not by their timestamp), other sources do not.
	 */
	protected boolean handlesRangeFilter() {
		return this instanceof ReplicationSource;
	}

	@Override
	public final Stream<JsonContent> stream(RangeFilter filter) {
		Stream<JsonContent> stream;
		try {
			init(filter);
			stream = createJsonContentStream(filter);
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
		if(stream==null) {
			close();
			return Stream.empty();
		}
		stream = stream.onClose(this::close);
		if(filter!=null && filter.isBounded() && !handlesRangeFilter()) {
			stream = stream.filter(filter::accept);
		}
		return stream;
	}

	/**
	 * Creates the stream of contents. A resource opened for the stream must be released by
	 * a close handler of the stream ({@link Stream#onClose(Runnable)}).
	 * <p>
	 * The default implementation calls {@link #createJsonContentStream()}.
	 */
	protected Stream<JsonContent> createJsonContentStream(RangeFilter filter) {
		return createJsonContentStream();
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
