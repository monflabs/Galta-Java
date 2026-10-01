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
 * Temporal.PlainMonthDay.
 */
public class TemporalPlainMonthDayObject extends NativeObject implements TemporalCalendarHolder {

	public static final String CLASSNAME = "PlainMonthDay";

	private final IsoDate isoDate;
	private final String calendar;

	public TemporalPlainMonthDayObject(JSEnvironment env, IsoDate isoDate, String calendar) {
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
		return "Temporal.PlainMonthDay";
	}

	@Override
	protected Object getDefaultPrototype() {
		return Prototype.get(getEnvironment());
	}

	private static TemporalPlainMonthDayObject self(Object o) {
		return receiver(o,TemporalPlainMonthDayObject.class,CLASSNAME);
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
			setOwnProperty(Symbol.TO_STRING_TAG,"Temporal.PlainMonthDay",PropertyDescriptor.DESC_PROP_TOSTRINGTAG);
			TemporalCalendarGetters.install(this,t -> self(t).isoDate,t -> self(t).calendar,"calendarId","monthCode","day");
			setOwnMethod(new TemporalFn(env,"with",1,(t,a) -> {
				TemporalPlainMonthDayObject md = self(t);
				Object like = arg(a,0);
				if(!isObj(like)) {
					throw typeError("invalid argument");
				}
				rejectTemporalLikeObject(like);
				Fields fields = isoDateToFields(md.calendar,md.isoDate,"month-day");
				Fields partial = prepareCalendarFields(md.calendar,like,DATE_FIELDS,NONE,null);
				fields = calendarMergeFields(md.calendar,fields,partial);
				String overflow = getTemporalOverflowOption(getOptionsObject(arg(a,1)));
				return createTemporalMonthDay(calendarMonthDayFromFields(md.calendar,fields,overflow),md.calendar);
			}));
			setOwnMethod(new TemporalFn(env,"equals",1,(t,a) -> {
				TemporalPlainMonthDayObject md = self(t);
				TemporalPlainMonthDayObject o = toTemporalMonthDay(arg(a,0),RuntimeUtil.UNDEFINED);
				return compareISODate(md.isoDate,o.isoDate)==0 && md.calendar.equals(o.calendar);
			}));
			setOwnMethod(new TemporalFn(env,"toString",0,(t,a) -> {
				TemporalPlainMonthDayObject md = self(t);
				String showCalendar = getTemporalShowCalendarNameOption(getOptionsObject(arg(a,0)));
				return temporalMonthDayToString(md.isoDate,md.calendar,showCalendar);
			}));
			setOwnMethod(new TemporalFn(env,"toJSON",0,(t,a) -> {
				TemporalPlainMonthDayObject md = self(t);
				return temporalMonthDayToString(md.isoDate,md.calendar,"auto");
			}));
			setOwnMethod(new TemporalFn(env,"toLocaleString",0,(t,a) -> {
				TemporalPlainMonthDayObject md = self(t);
				return temporalMonthDayToString(md.isoDate,md.calendar,"auto");
			}));
			setOwnMethod(new TemporalFn(env,"valueOf",0,(t,a) -> {
				valueOfThrows(CLASSNAME);
				return null;
			}));
			setOwnMethod(new TemporalFn(env,"toPlainDate",1,(t,a) -> {
				TemporalPlainMonthDayObject md = self(t);
				Object item = arg(a,0);
				if(!isObj(item)) {
					throw typeError("argument should be an object");
				}
				Fields fields = isoDateToFields(md.calendar,md.isoDate,"month-day");
				Fields input = prepareCalendarFields(md.calendar,item,new String[] {"year"},NONE,NONE);
				Fields merged = calendarMergeFields(md.calendar,fields,input);
				return createTemporalDate(calendarDateFromFields(md.calendar,merged,"constrain"),md.calendar);
			}));
		}

		@Override
		public String getClassName() {
			return "Temporal.PlainMonthDay";
		}
	}

	public static class ConstructorImpl extends BaseStandardConstructor {

		public ConstructorImpl(JSEnvironment env) {
			super(env,CLASSNAME,Prototype.get(env),2);
			setOwnMethod(new TemporalFn(env,"from",1,(t,a) -> toTemporalMonthDay(arg(a,0),arg(a,1))));
		}

		@Override
		public Class<?> getNativeClass() {
			return TemporalPlainMonthDayObject.class;
		}

		@Override
		public Object constructObject(Object[] parameters, Constructor topConstructor) {
			double month = toIntegerWithTruncation(arg(parameters,0));
			double day = toIntegerWithTruncation(arg(parameters,1));
			Object cal = arg(parameters,2);
			String calendar = isUndefined(cal) ? ISO8601 : canonicalizeCalendar(requireString(cal));
			Object refYear = arg(parameters,3);
			double year = isUndefined(refYear) ? 1972 : toIntegerWithTruncation(refYear);
			rejectISODate(year,month,day);
			IsoDate date = new IsoDate((long)year,(int)month,(int)day);
			rejectDateRange(date);
			return applyNewTargetPrototype(new TemporalPlainMonthDayObject(getEnvironment(),date,calendar),topConstructor);
		}

		@Override
		public Object call(Object _this, Object[] parameters) {
			throw RuntimeUtil.typeError("Constructor Temporal.PlainMonthDay requires 'new'");
		}
	}
}
