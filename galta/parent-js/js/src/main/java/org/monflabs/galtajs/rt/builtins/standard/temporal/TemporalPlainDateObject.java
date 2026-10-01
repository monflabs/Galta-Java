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
import static org.monflabs.galtajs.rt.builtins.standard.temporal.TemporalFn.arg;
import static org.monflabs.galtajs.rt.builtins.standard.temporal.TemporalFn.receiver;

import java.math.BigInteger;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BasePrototype;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.NativeObject;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.BaseStandardConstructor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;

/**
 * Temporal.PlainDate.
 */
public class TemporalPlainDateObject extends NativeObject implements TemporalCalendarHolder {

	public static final String CLASSNAME = "PlainDate";

	private final IsoDate isoDate;
	private final String calendar;

	public TemporalPlainDateObject(JSEnvironment env, IsoDate isoDate, String calendar) {
		super(env);
		this.isoDate = isoDate;
		this.calendar = calendar;
	}

	public IsoDate getIsoDate() {
		return isoDate;
	}

	@Override
	public String getCalendar() {
		return calendar;
	}

	@Override
	public String getClassName() {
		return "Temporal.PlainDate";
	}

	@Override
	protected Object getDefaultPrototype() {
		return Prototype.get(getEnvironment());
	}

	private static TemporalPlainDateObject self(Object o) {
		return receiver(o,TemporalPlainDateObject.class,CLASSNAME);
	}

	public static class Prototype extends BasePrototype {

		public static Prototype get(JSEnvironment env) {
			Prototype proto = (Prototype)env.getRegisteredPrototype(Prototype.class);
			if(proto==null) {
				proto = new Prototype(env);
				env.registerPrototype(Prototype.class,proto);
			}
			return proto;
		}

		private Prototype(JSEnvironment env) {
			super(env);
			setOwnProperty(Symbol.TO_STRING_TAG,"Temporal.PlainDate",PropertyDescriptor.DESC_PROP_TOSTRINGTAG);
			TemporalCalendarGetters.install(this,t -> self(t).isoDate,t -> self(t).calendar,TemporalCalendarGetters.DATE_GETTERS);
			setOwnMethod(new TemporalFn(env,"toPlainYearMonth",0,(t,a) -> {
				TemporalPlainDateObject d = self(t);
				Fields fields = isoDateToFields(d.calendar,d.isoDate,"date");
				return createTemporalYearMonth(calendarYearMonthFromFields(d.calendar,fields,"constrain"),d.calendar);
			}));
			setOwnMethod(new TemporalFn(env,"toPlainMonthDay",0,(t,a) -> {
				TemporalPlainDateObject d = self(t);
				Fields fields = isoDateToFields(d.calendar,d.isoDate,"date");
				return createTemporalMonthDay(calendarMonthDayFromFields(d.calendar,fields,"constrain"),d.calendar);
			}));
			setOwnMethod(new TemporalFn(env,"add",1,(t,a) -> addDurationToDate(false,self(t),arg(a,0),arg(a,1))));
			setOwnMethod(new TemporalFn(env,"subtract",1,(t,a) -> addDurationToDate(true,self(t),arg(a,0),arg(a,1))));
			setOwnMethod(new TemporalFn(env,"with",1,(t,a) -> with(self(t),arg(a,0),arg(a,1))));
			setOwnMethod(new TemporalFn(env,"withCalendar",1,(t,a) -> {
				TemporalPlainDateObject d = self(t);
				String calendar = toTemporalCalendarIdentifier(arg(a,0));
				return createTemporalDate(d.isoDate,calendar);
			}));
			setOwnMethod(new TemporalFn(env,"until",1,(t,a) -> differenceTemporalPlainDate(false,self(t),arg(a,0),arg(a,1))));
			setOwnMethod(new TemporalFn(env,"since",1,(t,a) -> differenceTemporalPlainDate(true,self(t),arg(a,0),arg(a,1))));
			setOwnMethod(new TemporalFn(env,"equals",1,(t,a) -> {
				TemporalPlainDateObject d = self(t);
				TemporalPlainDateObject o = toTemporalDate(arg(a,0),RuntimeUtil.UNDEFINED);
				return compareISODate(d.isoDate,o.isoDate)==0 && d.calendar.equals(o.calendar);
			}));
			setOwnMethod(new TemporalFn(env,"toPlainDateTime",0,(t,a) -> {
				TemporalPlainDateObject d = self(t);
				TimeRecord time = toTimeRecordOrMidnight(arg(a,0));
				return createTemporalDateTime(new IsoDateTime(d.isoDate,time),d.calendar);
			}));
			setOwnMethod(new TemporalFn(env,"toZonedDateTime",1,(t,a) -> toZonedDateTime(self(t),arg(a,0))));
			setOwnMethod(new TemporalFn(env,"toString",0,(t,a) -> {
				TemporalPlainDateObject d = self(t);
				String showCalendar = getTemporalShowCalendarNameOption(getOptionsObject(arg(a,0)));
				return temporalDateToString(d.isoDate,d.calendar,showCalendar);
			}));
			setOwnMethod(new TemporalFn(env,"toLocaleString",0,(t,a) -> {
				TemporalPlainDateObject d = self(t);
				return temporalDateToString(d.isoDate,d.calendar,"auto");
			}));
			setOwnMethod(new TemporalFn(env,"toJSON",0,(t,a) -> {
				TemporalPlainDateObject d = self(t);
				return temporalDateToString(d.isoDate,d.calendar,"auto");
			}));
			setOwnMethod(new TemporalFn(env,"valueOf",0,(t,a) -> {
				valueOfThrows(CLASSNAME);
				return null;
			}));
		}

		@Override
		public String getClassName() {
			return "Temporal.PlainDate";
		}
	}

	private static Object with(TemporalPlainDateObject d, Object like, Object options) {
		if(!isObj(like)) {
			throw typeError("invalid argument");
		}
		rejectTemporalLikeObject(like);
		Fields fields = isoDateToFields(d.calendar,d.isoDate,"date");
		Fields partial = prepareCalendarFields(d.calendar,like,DATE_FIELDS,NONE,null);
		fields = calendarMergeFields(d.calendar,fields,partial);
		String overflow = getTemporalOverflowOption(getOptionsObject(options));
		return createTemporalDate(calendarDateFromFields(d.calendar,fields,overflow),d.calendar);
	}

	private static Object toZonedDateTime(TemporalPlainDateObject d, Object item) {
		String timeZone;
		Object temporalTime = RuntimeUtil.UNDEFINED;
		if(isObj(item)) {
			Object timeZoneLike = getProp(item,"timeZone");
			if(isUndefined(timeZoneLike)) {
				timeZone = toTemporalTimeZoneIdentifier(item);
			} else {
				timeZone = toTemporalTimeZoneIdentifier(timeZoneLike);
				temporalTime = getProp(item,"plainTime");
			}
		} else {
			timeZone = toTemporalTimeZoneIdentifier(item);
		}
		BigInteger epochNs;
		if(isUndefined(temporalTime)) {
			epochNs = getStartOfDay(timeZone,d.isoDate);
		} else {
			TemporalPlainTimeObject time = toTemporalTime(temporalTime,RuntimeUtil.UNDEFINED);
			epochNs = getEpochNanosecondsFor(timeZone,new IsoDateTime(d.isoDate,time.getTime()),"compatible");
		}
		return createTemporalZonedDateTime(epochNs,timeZone,d.calendar);
	}

	public static class ConstructorImpl extends BaseStandardConstructor {

		public ConstructorImpl(JSEnvironment env) {
			super(env,CLASSNAME,Prototype.get(env),3);
			setOwnMethod(new TemporalFn(env,"from",1,(t,a) -> toTemporalDate(arg(a,0),arg(a,1))));
			setOwnMethod(new TemporalFn(env,"compare",2,(t,a) -> {
				TemporalPlainDateObject one = toTemporalDate(arg(a,0),RuntimeUtil.UNDEFINED);
				TemporalPlainDateObject two = toTemporalDate(arg(a,1),RuntimeUtil.UNDEFINED);
				return compareISODate(one.isoDate,two.isoDate);
			}));
		}

		@Override
		public Class<?> getNativeClass() {
			return TemporalPlainDateObject.class;
		}

		@Override
		public Object constructObject(Object[] parameters, Constructor topConstructor) {
			double year = toIntegerWithTruncation(arg(parameters,0));
			double month = toIntegerWithTruncation(arg(parameters,1));
			double day = toIntegerWithTruncation(arg(parameters,2));
			Object cal = arg(parameters,3);
			String calendar = isUndefined(cal) ? ISO8601 : canonicalizeCalendar(requireString(cal));
			rejectISODate(year,month,day);
			IsoDate date = new IsoDate((long)year,(int)month,(int)day);
			rejectDateRange(date);
			return applyNewTargetPrototype(new TemporalPlainDateObject(getEnvironment(),date,calendar),topConstructor);
		}

		@Override
		public Object call(Object _this, Object[] parameters) {
			throw RuntimeUtil.typeError("Constructor Temporal.PlainDate requires 'new'");
		}
	}
}
