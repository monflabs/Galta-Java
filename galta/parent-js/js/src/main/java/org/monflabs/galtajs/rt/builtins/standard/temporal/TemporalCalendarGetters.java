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

import static org.monflabs.galtajs.rt.builtins.standard.temporal.TemporalAO.*;

import java.util.function.Function;

import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.rt.RuntimeUtil;

/**
 * The calendar-derived getters shared by the date-bearing Temporal types
 * (ISO 8601 calendar).
 */
final class TemporalCalendarGetters {

	private TemporalCalendarGetters() {
	}

	// dateOf performs the receiver check and returns the ISO date
	static void install(JSObject proto, Function<Object,IsoDate> dateOf, Function<Object,String> calendarOf, String... names) {
		for(String name: names) {
			switch(name) {
				case "calendarId":
					proto.setOwnProperty(name,true,false,(t,k) -> calendarOf.apply(t),null);
					break;
				case "era":
				case "eraYear":
					proto.setOwnProperty(name,true,false,(t,k) -> {
						dateOf.apply(t);
						return RuntimeUtil.UNDEFINED;
					},null);
					break;
				case "year":
					proto.setOwnProperty(name,true,false,(t,k) -> num(dateOf.apply(t).year()),null);
					break;
				case "month":
					proto.setOwnProperty(name,true,false,(t,k) -> dateOf.apply(t).month(),null);
					break;
				case "monthCode":
					proto.setOwnProperty(name,true,false,(t,k) -> createMonthCode(dateOf.apply(t).month()),null);
					break;
				case "day":
					proto.setOwnProperty(name,true,false,(t,k) -> dateOf.apply(t).day(),null);
					break;
				case "dayOfWeek":
					proto.setOwnProperty(name,true,false,(t,k) -> isoDayOfWeek(dateOf.apply(t)),null);
					break;
				case "dayOfYear":
					proto.setOwnProperty(name,true,false,(t,k) -> isoDayOfYear(dateOf.apply(t)),null);
					break;
				case "weekOfYear":
					proto.setOwnProperty(name,true,false,(t,k) -> num(isoWeekOfYear(dateOf.apply(t))[0]),null);
					break;
				case "yearOfWeek":
					proto.setOwnProperty(name,true,false,(t,k) -> num(isoWeekOfYear(dateOf.apply(t))[1]),null);
					break;
				case "daysInWeek":
					proto.setOwnProperty(name,true,false,(t,k) -> {
						dateOf.apply(t);
						return 7;
					},null);
					break;
				case "daysInMonth":
					proto.setOwnProperty(name,true,false,(t,k) -> {
						IsoDate d = dateOf.apply(t);
						return isoDaysInMonth(d.year(),d.month());
					},null);
					break;
				case "daysInYear":
					proto.setOwnProperty(name,true,false,(t,k) -> isLeapYear(dateOf.apply(t).year()) ? 366 : 365,null);
					break;
				case "monthsInYear":
					proto.setOwnProperty(name,true,false,(t,k) -> {
						dateOf.apply(t);
						return 12;
					},null);
					break;
				case "inLeapYear":
					proto.setOwnProperty(name,true,false,(t,k) -> isLeapYear(dateOf.apply(t).year()),null);
					break;
				default:
					throw new IllegalArgumentException(name);
			}
		}
	}

	static final String[] DATE_GETTERS = {"calendarId","era","eraYear","year","month","monthCode","day","dayOfWeek","dayOfYear","weekOfYear","yearOfWeek","daysInWeek","daysInMonth","daysInYear","monthsInYear","inLeapYear"};
}
