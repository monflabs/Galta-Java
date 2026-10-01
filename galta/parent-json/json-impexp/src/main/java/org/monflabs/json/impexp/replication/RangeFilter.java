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
package org.monflabs.json.impexp.replication;

import java.time.Instant;

import org.monflabs.json.impexp.JsonContent;

/**
 * A range of dates, both ends included. A null end is not bounded.
 */
public class RangeFilter {
	
	private Instant since;
	private Instant until;

	public RangeFilter(Instant since, Instant until) {
		this.since = since;
		this.until = until;
	}

	public Instant getSince() {
		return since;
	}

	public Instant getUntil() {
		return until;
	}

	/**
	 * Whether a date is in the range. A null date (unknown) is always accepted.
	 */
	public boolean accept(Instant t) {
		if(t==null) {
			return true;
		}
		if(since!=null && t.compareTo(since)<0) {
			return false;
		}
		if(until!=null && t.compareTo(until)>0) {
			return false;
		}
		return true;
	}

	/**
	 * Whether the timestamp of a content is in the range, see {@link #accept(Instant)}.
	 */
	public boolean accept(JsonContent content) {
		return accept(content.getTimestamp());
	}
	
	/**
	 * Whether the range is bounded (has a start or an end).
	 */
	public boolean isBounded() {
		return since!=null || until!=null;
	}

	@Override
	public String toString() {
		return "[" + since + ", " + until + "]";
	}
}
