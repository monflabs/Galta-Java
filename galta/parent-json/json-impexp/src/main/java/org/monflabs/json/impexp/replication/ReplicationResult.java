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

import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.monflabs.json.JsonObject;
import org.monflabs.json.JsonUtil;
import org.monflabs.json.impexp.ImportResult;
import org.monflabs.util.StringFormat;

/**
 * Result of a replication.
 * <p>
 * Every source content is counted exactly once, in one of these categories: inserted,
 * deleted, conflicts or ignored, so {@link #getProcessed()} is the number of source
 * contents. The contents written to resolve a conflict are not counted again as inserted
 * or deleted.
 */
public class ReplicationResult extends ImportResult {
	
	private RangeFilter rangeFilter;
	
	private long conflicts;
	private long ignored;
	
	public ReplicationResult() {
	}
	
	@Override
	public String toString() {
		return StringFormat.format("Processed={0} (inserted={1}, deleted={2}, conflicts={3}, ignored={4})", getProcessed(), getInserted(), getDeleted(), getConflicts(), getIgnored());
	}
	
	public JsonObject toJson() {
		return toJson(null);
	}
	public JsonObject toJson(ZoneId zoneId) {
		if(zoneId==null) {
			zoneId = ZoneId.systemDefault(); // Or GTM?
		}
		RangeFilter rangeFilter = getRangeFilter();
		OffsetDateTime since = rangeFilter!=null && rangeFilter.getSince()!=null ? OffsetDateTime.ofInstant(rangeFilter.getSince(), zoneId) : null;
		OffsetDateTime until = rangeFilter!=null && rangeFilter.getUntil()!=null ? OffsetDateTime.ofInstant(rangeFilter.getUntil(), zoneId) : null;
		return JsonObject.of(
				"duration", getDuration(), 
				"rangeFilter", JsonObject.of("since", since!=null ? JsonUtil.toString(since) : null, "until", until!=null ? JsonUtil.toString(until) : null),
				"created", getInserted(),
				"deleted", getDeleted(),
				"conflicts", getConflicts()
			);
	}
	
	
	public RangeFilter getRangeFilter() {
		return rangeFilter;
	}
	public void setRangeFilter(RangeFilter rangeFilter) {
		this.rangeFilter = rangeFilter;
	}

	@Override
	public long getProcessed() {
		return super.getProcessed()+getConflicts()+getIgnored();
	}

	public long getConflicts() {
		return conflicts;
	}
	public long getIgnored() {
		return ignored;
	}
	
	public void addConflict() {
		conflicts++;
	}
	public void addIgnored() {
		ignored++;
	}
}