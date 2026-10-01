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
 * The date part of an internal duration record.
 */
public record DateDuration(long years, long months, long weeks, long days) {

	public static final DateDuration ZERO = new DateDuration(0,0,0,0);

	public int sign() {
		if(years!=0) return Long.signum(years);
		if(months!=0) return Long.signum(months);
		if(weeks!=0) return Long.signum(weeks);
		return Long.signum(days);
	}

	public DateDuration withDays(long d) {
		return new DateDuration(years,months,weeks,d);
	}
}
