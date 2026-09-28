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

import java.util.stream.Stream;

import org.monflabs.json.impexp.replication.RangeFilter;

/**
 * JSON source.
 */
public interface JsonSource extends JsonAccessor {
	
	public long estimatedCount();
	
	public default Stream<JsonContent> stream() {
		return stream(null);
	}
	public Stream<JsonContent> stream(RangeFilter filter);
	
	public default ImportResult exportTo(JsonTarget target) {
		return exportTo(target,null);
	}
	public default ImportResult exportTo(JsonTarget target, RangeFilter filter) {
		return target.importFrom(this, filter);
	}
}