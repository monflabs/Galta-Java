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

import java.math.BigInteger;

/**
 * Temporal units, largest first (the enum order is the spec's
 * "descending units" table). AUTO is only an option value.
 */
public enum TemporalUnit {
	YEAR("year","years",true,0),
	MONTH("month","months",true,0),
	WEEK("week","weeks",true,0),
	DAY("day","days",true,86_400_000_000_000L),
	HOUR("hour","hours",false,3_600_000_000_000L),
	MINUTE("minute","minutes",false,60_000_000_000L),
	SECOND("second","seconds",false,1_000_000_000L),
	MILLISECOND("millisecond","milliseconds",false,1_000_000L),
	MICROSECOND("microsecond","microseconds",false,1_000L),
	NANOSECOND("nanosecond","nanoseconds",false,1L),
	AUTO("auto","auto",false,0);

	public final String singular;
	public final String plural;
	public final boolean date;
	public final long nsPerUnit;
	public final BigInteger bigNsPerUnit;

	TemporalUnit(String singular, String plural, boolean date, long nsPerUnit) {
		this.singular = singular;
		this.plural = plural;
		this.date = date;
		this.nsPerUnit = nsPerUnit;
		this.bigNsPerUnit = BigInteger.valueOf(nsPerUnit);
	}

	public boolean isCalendarUnit() {
		return this==YEAR || this==MONTH || this==WEEK;
	}

	public static TemporalUnit larger(TemporalUnit u1, TemporalUnit u2) {
		return u1.ordinal()<=u2.ordinal() ? u1 : u2;
	}

	public static TemporalUnit ofName(String s) {
		for(TemporalUnit u: values()) {
			if(u.singular.equals(s) || u.plural.equals(s)) {
				return u;
			}
		}
		return null;
	}

	@Override
	public String toString() {
		return singular;
	}
}
