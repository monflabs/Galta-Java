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
import org.monflabs.galtajs.rt.builtins.standard.temporal.TemporalMath.RoundingMode;

/**
 * Temporal.PlainDateTime.
 */
public class TemporalPlainDateTimeObject extends NativeObject implements TemporalCalendarHolder {

	public static final String CLASSNAME = "PlainDateTime";

	private final IsoDateTime isoDateTime;
	private final String calendar;

	public TemporalPlainDateTimeObject(JSEnvironment env, IsoDateTime isoDateTime, String calendar) {
		super(env);
		this.isoDateTime = isoDateTime;
		this.calendar = calendar;
	}

	public IsoDateTime getIsoDateTime() {
		return isoDateTime;
	}

	@Override
	public String getCalendar() {
		return calendar;
	}

	@Override
	public String getClassName() {
		return "Temporal.PlainDateTime";
	}

	@Override
	protected Object getDefaultPrototype() {
		return Prototype.get(getEnvironment());
	}

	private static TemporalPlainDateTimeObject self(Object o) {
		return receiver(o,TemporalPlainDateTimeObject.class,CLASSNAME);
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
			setOwnProperty(Symbol.TO_STRING_TAG,"Temporal.PlainDateTime",PropertyDescriptor.DESC_PROP_TOSTRINGTAG);
			TemporalCalendarGetters.install(this,t -> self(t).isoDateTime.date(),t -> self(t).calendar,TemporalCalendarGetters.DATE_GETTERS);
			setOwnProperty("hour",true,false,(t,k) -> self(t).isoDateTime.time().hour(),null);
			setOwnProperty("minute",true,false,(t,k) -> self(t).isoDateTime.time().minute(),null);
			setOwnProperty("second",true,false,(t,k) -> self(t).isoDateTime.time().second(),null);
			setOwnProperty("millisecond",true,false,(t,k) -> self(t).isoDateTime.time().millisecond(),null);
			setOwnProperty("microsecond",true,false,(t,k) -> self(t).isoDateTime.time().microsecond(),null);
			setOwnProperty("nanosecond",true,false,(t,k) -> self(t).isoDateTime.time().nanosecond(),null);
			setOwnMethod(new TemporalFn(env,"with",1,(t,a) -> with(self(t),arg(a,0),arg(a,1))));
			setOwnMethod(new TemporalFn(env,"withPlainTime",0,(t,a) -> {
				TemporalPlainDateTimeObject d = self(t);
				TimeRecord time = toTimeRecordOrMidnight(arg(a,0));
				return createTemporalDateTime(new IsoDateTime(d.isoDateTime.date(),time),d.calendar);
			}));
			setOwnMethod(new TemporalFn(env,"withCalendar",1,(t,a) -> {
				TemporalPlainDateTimeObject d = self(t);
				String calendar = toTemporalCalendarIdentifier(arg(a,0));
				return createTemporalDateTime(d.isoDateTime,calendar);
			}));
			setOwnMethod(new TemporalFn(env,"add",1,(t,a) -> addDurationToDateTime(false,self(t),arg(a,0),arg(a,1))));
			setOwnMethod(new TemporalFn(env,"subtract",1,(t,a) -> addDurationToDateTime(true,self(t),arg(a,0),arg(a,1))));
			setOwnMethod(new TemporalFn(env,"until",1,(t,a) -> differenceTemporalPlainDateTime(false,self(t),arg(a,0),arg(a,1))));
			setOwnMethod(new TemporalFn(env,"since",1,(t,a) -> differenceTemporalPlainDateTime(true,self(t),arg(a,0),arg(a,1))));
			setOwnMethod(new TemporalFn(env,"round",1,(t,a) -> round(self(t),arg(a,0))));
			setOwnMethod(new TemporalFn(env,"equals",1,(t,a) -> {
				TemporalPlainDateTimeObject d = self(t);
				TemporalPlainDateTimeObject o = toTemporalDateTime(arg(a,0),RuntimeUtil.UNDEFINED);
				return compareISODateTime(d.isoDateTime,o.isoDateTime)==0 && d.calendar.equals(o.calendar);
			}));
			setOwnMethod(new TemporalFn(env,"toString",0,(t,a) -> toStringImpl(self(t),arg(a,0))));
			setOwnMethod(new TemporalFn(env,"toJSON",0,(t,a) -> {
				TemporalPlainDateTimeObject d = self(t);
				return isoDateTimeToString(d.isoDateTime,d.calendar,PRECISION_AUTO,"auto");
			}));
			setOwnMethod(new TemporalFn(env,"toLocaleString",0,(t,a) -> {
				TemporalPlainDateTimeObject d = self(t);
				return isoDateTimeToString(d.isoDateTime,d.calendar,PRECISION_AUTO,"auto");
			}));
			setOwnMethod(new TemporalFn(env,"valueOf",0,(t,a) -> {
				valueOfThrows(CLASSNAME);
				return null;
			}));
			setOwnMethod(new TemporalFn(env,"toZonedDateTime",1,(t,a) -> {
				TemporalPlainDateTimeObject d = self(t);
				String timeZone = toTemporalTimeZoneIdentifier(arg(a,0));
				Object resolved = getOptionsObject(arg(a,1));
				String disambiguation = getTemporalDisambiguationOption(resolved);
				BigInteger epochNs = getEpochNanosecondsFor(timeZone,d.isoDateTime,disambiguation);
				return createTemporalZonedDateTime(epochNs,timeZone,d.calendar);
			}));
			setOwnMethod(new TemporalFn(env,"toPlainDate",0,(t,a) -> {
				TemporalPlainDateTimeObject d = self(t);
				return createTemporalDate(d.isoDateTime.date(),d.calendar);
			}));
			setOwnMethod(new TemporalFn(env,"toPlainTime",0,(t,a) -> createTemporalTime(self(t).isoDateTime.time())));
		}

		@Override
		public String getClassName() {
			return "Temporal.PlainDateTime";
		}
	}

	static Fields dateTimeFields(IsoDateTime dt, String calendar) {
		Fields fields = isoDateToFields(calendar,dt.date(),"date");
		TimeRecord t = dt.time();
		fields.hour = (double)t.hour();
		fields.minute = (double)t.minute();
		fields.second = (double)t.second();
		fields.millisecond = (double)t.millisecond();
		fields.microsecond = (double)t.microsecond();
		fields.nanosecond = (double)t.nanosecond();
		return fields;
	}

	private static Object with(TemporalPlainDateTimeObject d, Object like, Object options) {
		if(!isObj(like)) {
			throw typeError("invalid argument");
		}
		rejectTemporalLikeObject(like);
		Fields fields = dateTimeFields(d.isoDateTime,d.calendar);
		Fields partial = prepareCalendarFields(d.calendar,like,DATE_FIELDS,TIME_FIELDS,null);
		fields = calendarMergeFields(d.calendar,fields,partial);
		String overflow = getTemporalOverflowOption(getOptionsObject(options));
		return createTemporalDateTime(interpretTemporalDateTimeFields(d.calendar,fields,overflow),d.calendar);
	}

	static TemporalUnit[] roundOptions(Object roundTo, long[] increment, RoundingMode[] mode) {
		if(isUndefined(roundTo)) {
			throw typeError("options parameter is required");
		}
		TemporalUnit smallestUnit;
		if(isStr(roundTo)) {
			increment[0] = 1;
			mode[0] = RoundingMode.HALF_EXPAND;
			smallestUnit = unitFromStringParam("smallestUnit",roundTo.toString());
		} else {
			Object options = getOptionsObject(roundTo);
			increment[0] = getRoundingIncrementOption(options);
			mode[0] = getRoundingModeOption(options,RoundingMode.HALF_EXPAND);
			smallestUnit = getTemporalUnitValuedOption(options,"smallestUnit",REQUIRED);
		}
		if(smallestUnit!=TemporalUnit.DAY) {
			validateTemporalUnitValue(smallestUnit,"time",false);
		}
		long maximum = smallestUnit==TemporalUnit.DAY ? 1 : maximumIncrement(smallestUnit);
		validateTemporalRoundingIncrement(increment[0],maximum,maximum==1);
		return new TemporalUnit[] {smallestUnit};
	}

	private static Object round(TemporalPlainDateTimeObject d, Object roundTo) {
		long[] increment = new long[1];
		RoundingMode[] mode = new RoundingMode[1];
		TemporalUnit smallestUnit = roundOptions(roundTo,increment,mode)[0];
		if(increment[0]==1 && smallestUnit==TemporalUnit.NANOSECOND) {
			return createTemporalDateTime(d.isoDateTime,d.calendar);
		}
		return createTemporalDateTime(roundISODateTime(d.isoDateTime,increment[0],smallestUnit,mode[0]),d.calendar);
	}

	private static Object toStringImpl(TemporalPlainDateTimeObject d, Object options) {
		Object resolved = getOptionsObject(options);
		String showCalendar = getTemporalShowCalendarNameOption(resolved);
		int digits = getTemporalFractionalSecondDigitsOption(resolved);
		RoundingMode roundingMode = getRoundingModeOption(resolved,RoundingMode.TRUNC);
		TemporalUnit smallestUnit = getTemporalUnitValuedOption(resolved,"smallestUnit",null);
		validateTemporalUnitValue(smallestUnit,"time",false);
		if(smallestUnit==TemporalUnit.HOUR) {
			throw rangeError("smallestUnit must be a time unit other than \"hour\"");
		}
		PrecisionRecord p = toSecondsStringPrecisionRecord(smallestUnit,digits);
		IsoDateTime result = roundISODateTime(d.isoDateTime,p.increment(),p.unit(),roundingMode);
		rejectDateTimeRange(result);
		return isoDateTimeToString(result,d.calendar,p.precision(),showCalendar);
	}

	public static class ConstructorImpl extends BaseStandardConstructor {

		public ConstructorImpl(JSEnvironment env) {
			super(env,CLASSNAME,Prototype.get(env),3);
			setOwnMethod(new TemporalFn(env,"from",1,(t,a) -> toTemporalDateTime(arg(a,0),arg(a,1))));
			setOwnMethod(new TemporalFn(env,"compare",2,(t,a) -> {
				TemporalPlainDateTimeObject one = toTemporalDateTime(arg(a,0),RuntimeUtil.UNDEFINED);
				TemporalPlainDateTimeObject two = toTemporalDateTime(arg(a,1),RuntimeUtil.UNDEFINED);
				return Integer.signum(compareISODateTime(one.isoDateTime,two.isoDateTime));
			}));
		}

		@Override
		public Class<?> getNativeClass() {
			return TemporalPlainDateTimeObject.class;
		}

		@Override
		public Object constructObject(Object[] parameters, Constructor topConstructor) {
			double year = toIntegerWithTruncation(arg(parameters,0));
			double month = toIntegerWithTruncation(arg(parameters,1));
			double day = toIntegerWithTruncation(arg(parameters,2));
			double[] t = new double[6];
			for(int i=0; i<6; i++) {
				Object v = arg(parameters,3+i);
				t[i] = isUndefined(v) ? 0 : toIntegerWithTruncation(v);
			}
			Object cal = arg(parameters,9);
			String calendar = isUndefined(cal) ? ISO8601 : canonicalizeCalendar(requireString(cal));
			rejectISODate(year,month,day);
			rejectTime(t[0],t[1],t[2],t[3],t[4],t[5]);
			IsoDateTime dt = new IsoDateTime(new IsoDate((long)year,(int)month,(int)day),new TimeRecord((int)t[0],(int)t[1],(int)t[2],(int)t[3],(int)t[4],(int)t[5]));
			rejectDateTimeRange(dt);
			return applyNewTargetPrototype(new TemporalPlainDateTimeObject(getEnvironment(),dt,calendar),topConstructor);
		}

		@Override
		public Object call(Object _this, Object[] parameters) {
			throw RuntimeUtil.typeError("Constructor Temporal.PlainDateTime requires 'new'");
		}
	}
}
