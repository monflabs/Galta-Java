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
package org.monflabs.json.impexp;

import org.monflabs.util.StringFormat;

public class ImportResult {
	
	private long duration;
	
	private long inserted;
	private long deleted;
	
	public ImportResult() {
	}
	
	@Override
	public String toString() {
		return StringFormat.format("Processed={0} (inserted={1}, deleted={2})", getProcessed(), getInserted(), getDeleted());
	}
	
	public long getDuration() {
		return duration;
	}
	public void setDuration(long duration) {
		this.duration = duration;
	}

	public long getProcessed() {
		return getInserted()+getDeleted();
	}
	public long getInserted() {
		return inserted;
	}
	public long getDeleted() {
		return deleted;
	}
	
	public void addInserted() {
		inserted++;
	}
	public void addDeleted() {
		deleted++;
	}
}