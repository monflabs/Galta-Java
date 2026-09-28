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
package org.monflabs.util.profiler;

import java.io.PrintStream;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import org.monflabs.util.Console;
import org.monflabs.util.StringFormat;
import org.monflabs.util.profiler.impl.SnapshotAggregator;

public class ProfilerSnapshot {
	
    public static ProfilerSnapshot create(OffsetDateTime snapshotTime, String notes, SnapshotAggregator agg) {
    	return new ProfilerSnapshot(snapshotTime,notes,agg);
    }


	private OffsetDateTime snapshotTime;
	private Aggregator mainAggregator;
	private String notes;

	ProfilerSnapshot(OffsetDateTime snapshotTime, String notes, SnapshotAggregator mainAggregator) {
		this.snapshotTime = snapshotTime;
		this.mainAggregator = mainAggregator;
		this.notes = notes;
	}

	public OffsetDateTime getSnapshotTime() {
		return snapshotTime;
	}

	public Aggregator getMainAggregator() {
		return mainAggregator;
	}

	public String getNotes() {
		return notes;
	}

	public void dump() {
		dump(Console.outStream());
	}

	public void dump(PrintStream pw) {
		pw.println(StringFormat.format("Profiler snapshot, time: {0}", snapshotTime.format(DateTimeFormatter.ISO_OFFSET_TIME)));
		mainAggregator.dump(pw);
	}
}
