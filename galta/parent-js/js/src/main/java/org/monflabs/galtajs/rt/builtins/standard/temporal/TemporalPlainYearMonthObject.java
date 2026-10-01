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

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BasePrototype;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.NativeObject;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.BaseStandardConstructor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;

/**
 * Temporal.PlainYearMonth.
 */
public class TemporalPlainYearMonthObject extends NativeObject implements TemporalCalendarHolder {

	public static final String CLASSNAME = "PlainYearMonth";

	private final IsoDate isoDate;
	private final String calendar;

	public TemporalPlainYearMonthObject(JSEnvironment env, IsoDate isoDate, String calendar) {
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
		return "Temporal.PlainYearMonth";
	}

	@Override
	protected Object getDefaultPrototype() {
		return Prototype.get(getEnvironment());
	}

	private static TemporalPlainYearMonthObject self(Object o) {
		return receiver(o,TemporalPlainYearMonthObject.class,CLASSNAME);
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
			setOwnProperty(Symbol.TO_STRING_TAG,"Temporal.PlainYearMonth",PropertyDescriptor.DESC_PROP_TOSTRINGTAG);
			TemporalCalendarGetters.install(this,t -> self(t).isoDate,t -> self(t).calendar,
					"calendarId","era","eraYear","year","month","monthCode","daysInMonth","daysInYear","monthsInYear","inLeapYear");
			setOwnMethod(new TemporalFn(env,"with",1,(t,a) -> {
				TemporalPlainYearMonthObject ym = self(t);
				Object like = arg(a,0);
				if(!isObj(like)) {
					throw typeError("invalid argument");
				}
				rejectTemporalLikeObject(like);
				Fields fields = isoDateToFields(ym.calendar,ym.isoDate,"year-month");
				Fields partial = prepareCalendarFields(ym.calendar,like,YEAR_MONTH_FIELDS,NONE,null);
				fields = calendarMergeFields(ym.calendar,fields,partial);
				String overflow = getTemporalOverflowOption(getOptionsObject(arg(a,1)));
				return createTemporalYearMonth(calendarYearMonthFromFields(ym.calendar,fields,overflow),ym.calendar);
			}));
			setOwnMethod(new TemporalFn(env,"add",1,(t,a) -> addDurationToYearMonth(false,self(t),arg(a,0),arg(a,1))));
			setOwnMethod(new TemporalFn(env,"subtract",1,(t,a) -> addDurationToYearMonth(true,self(t),arg(a,0),arg(a,1))));
			setOwnMethod(new TemporalFn(env,"until",1,(t,a) -> differenceTemporalPlainYearMonth(false,self(t),arg(a,0),arg(a,1))));
			setOwnMethod(new TemporalFn(env,"since",1,(t,a) -> differenceTemporalPlainYearMonth(true,self(t),arg(a,0),arg(a,1))));
			setOwnMethod(new TemporalFn(env,"equals",1,(t,a) -> {
				TemporalPlainYearMonthObject ym = self(t);
				TemporalPlainYearMonthObject o = toTemporalYearMonth(arg(a,0),RuntimeUtil.UNDEFINED);
				return compareISODate(ym.isoDate,o.isoDate)==0 && ym.calendar.equals(o.calendar);
			}));
			setOwnMethod(new TemporalFn(env,"toString",0,(t,a) -> {
				TemporalPlainYearMonthObject ym = self(t);
				String showCalendar = getTemporalShowCalendarNameOption(getOptionsObject(arg(a,0)));
				return temporalYearMonthToString(ym.isoDate,ym.calendar,showCalendar);
			}));
			setOwnMethod(new TemporalFn(env,"toJSON",0,(t,a) -> {
				TemporalPlainYearMonthObject ym = self(t);
				return temporalYearMonthToString(ym.isoDate,ym.calendar,"auto");
			}));
			setOwnMethod(new TemporalFn(env,"toLocaleString",0,(t,a) -> {
				TemporalPlainYearMonthObject ym = self(t);
				return temporalYearMonthToString(ym.isoDate,ym.calendar,"auto");
			}));
			setOwnMethod(new TemporalFn(env,"valueOf",0,(t,a) -> {
				valueOfThrows(CLASSNAME);
				return null;
			}));
			setOwnMethod(new TemporalFn(env,"toPlainDate",1,(t,a) -> {
				TemporalPlainYearMonthObject ym = self(t);
				Object item = arg(a,0);
				if(!isObj(item)) {
					throw typeError("argument should be an object");
				}
				Fields fields = isoDateToFields(ym.calendar,ym.isoDate,"year-month");
				Fields input = prepareCalendarFields(ym.calendar,item,new String[] {"day"},NONE,NONE);
				Fields merged = calendarMergeFields(ym.calendar,fields,input);
				return createTemporalDate(calendarDateFromFields(ym.calendar,merged,"constrain"),ym.calendar);
			}));
		}

		@Override
		public String getClassName() {
			return "Temporal.PlainYearMonth";
		}
	}

	public static class ConstructorImpl extends BaseStandardConstructor {

		public ConstructorImpl(JSEnvironment env) {
			super(env,CLASSNAME,Prototype.get(env),2);
			setOwnMethod(new TemporalFn(env,"from",1,(t,a) -> toTemporalYearMonth(arg(a,0),arg(a,1))));
			setOwnMethod(new TemporalFn(env,"compare",2,(t,a) -> {
				TemporalPlainYearMonthObject one = toTemporalYearMonth(arg(a,0),RuntimeUtil.UNDEFINED);
				TemporalPlainYearMonthObject two = toTemporalYearMonth(arg(a,1),RuntimeUtil.UNDEFINED);
				return compareISODate(one.isoDate,two.isoDate);
			}));
		}

		@Override
		public Class<?> getNativeClass() {
			return TemporalPlainYearMonthObject.class;
		}

		@Override
		public Object constructObject(Object[] parameters, Constructor topConstructor) {
			double year = toIntegerWithTruncation(arg(parameters,0));
			double month = toIntegerWithTruncation(arg(parameters,1));
			Object cal = arg(parameters,2);
			String calendar = isUndefined(cal) ? ISO8601 : canonicalizeCalendar(requireString(cal));
			Object refDay = arg(parameters,3);
			double day = isUndefined(refDay) ? 1 : toIntegerWithTruncation(refDay);
			rejectISODate(year,month,day);
			IsoDate date = new IsoDate((long)year,(int)month,(int)day);
			rejectYearMonthRange(date);
			return applyNewTargetPrototype(new TemporalPlainYearMonthObject(getEnvironment(),date,calendar),topConstructor);
		}

		@Override
		public Object call(Object _this, Object[] parameters) {
			throw RuntimeUtil.typeError("Constructor Temporal.PlainYearMonth requires 'new'");
		}
	}
}
