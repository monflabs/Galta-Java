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

import org.monflabs.json.JsonException;
import org.monflabs.json.impexp.JsonContent;

/**
 * 
 */
public interface ConflictResolver {

	public static ConflictResolver FAIL_EXCEPTION = (source,target) -> {
		throw new JsonException(null, "Replication conflict on {0}", source.getKey());
	};

	public static ConflictResolver NO_ACTION = (source,target) -> {
		return null;
	};

	public static ConflictResolver SOURCE_WINS = (source,target) -> {
		return new JsonContent[] {source};
	};
	
	public static ConflictResolver TARGET_WINS = (source,target) -> {
		return new JsonContent[] {target};
	};
	
	public static ConflictResolver OLDER_WINS = (source,target) -> {
		if(compareTimestamps(source,target)<=0) {
			return new JsonContent[] {source};
		}
		return new JsonContent[] {target};
	};
	
	public static ConflictResolver NEWER_WINS = (source,target) -> {
		if(compareTimestamps(source,target)>=0) {
			return new JsonContent[] {source};
		}
		return new JsonContent[] {target};
	};
	
	/**
	 * Compare the timestamps of two contents, a missing timestamp being the oldest.
	 */
	public static int compareTimestamps(JsonContent source, JsonContent target) {
		Instant ds = source.getTimestamp();
		Instant dt = target.getTimestamp();
		if(ds==null) {
			return dt==null ? 0 : -1;
		}
		if(dt==null) {
			return 1;
		}
		return ds.compareTo(dt);
	}
	
	public JsonContent[] resolve(JsonContent source, JsonContent target);
}
