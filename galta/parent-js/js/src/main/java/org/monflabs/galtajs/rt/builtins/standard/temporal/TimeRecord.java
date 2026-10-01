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
 *
 * Portions are derived from the TC39 Temporal proposal reference polyfill,
 * Copyright (c) 2017, 2018, 2019, 2020 Ecma International. All rights
 * reserved. Distributed under the BSD License, see LICENSE.txt in this
 * folder.
 */
package org.monflabs.galtajs.rt.builtins.standard.temporal;

/**
 * A wall-clock time record, with the day overflow of a balancing operation.
 */
public record TimeRecord(long deltaDays, int hour, int minute, int second, int millisecond, int microsecond, int nanosecond) {

	public static final TimeRecord MIDNIGHT = new TimeRecord(0,0,0,0,0,0,0);
	public static final TimeRecord NOON = new TimeRecord(0,12,0,0,0,0,0);

	public TimeRecord(int hour, int minute, int second, int millisecond, int microsecond, int nanosecond) {
		this(0,hour,minute,second,millisecond,microsecond,nanosecond);
	}

	public TimeRecord withoutDays() {
		return deltaDays==0 ? this : new TimeRecord(0,hour,minute,second,millisecond,microsecond,nanosecond);
	}

	public long subSecondNanoseconds() {
		return millisecond*1_000_000L + microsecond*1_000L + nanosecond;
	}
}
