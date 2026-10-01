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
 * Temporal.ZonedDateTime.
 */
public class TemporalZonedDateTimeObject extends NativeObject implements TemporalCalendarHolder {

	public static final String CLASSNAME = "ZonedDateTime";

	private final BigInteger epochNs;
	private final String timeZone;
	private final String calendar;

	public TemporalZonedDateTimeObject(JSEnvironment env, BigInteger epochNs, String timeZone, String calendar) {
		super(env);
		this.epochNs = epochNs;
		this.timeZone = timeZone;
		this.calendar = calendar;
	}

	public BigInteger getEpochNs() {
		return epochNs;
	}

	public String getTimeZone() {
		return timeZone;
	}

	@Override
	public String getCalendar() {
		return calendar;
	}

	public IsoDateTime dateTime() {
		return getISODateTimeFor(timeZone,epochNs);
	}

	@Override
	public String getClassName() {
		return "Temporal.ZonedDateTime";
	}

	@Override
	protected Object getDefaultPrototype() {
		return Prototype.get(getEnvironment());
	}

	private static TemporalZonedDateTimeObject self(Object o) {
		return receiver(o,TemporalZonedDateTimeObject.class,CLASSNAME);
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
			setOwnProperty(Symbol.TO_STRING_TAG,"Temporal.ZonedDateTime",PropertyDescriptor.DESC_PROP_TOSTRINGTAG);
			TemporalCalendarGetters.install(this,t -> self(t).dateTime().date(),t -> self(t).calendar,TemporalCalendarGetters.DATE_GETTERS);
			setOwnProperty("timeZoneId",true,false,(t,k) -> self(t).timeZone,null);
			setOwnProperty("hour",true,false,(t,k) -> self(t).dateTime().time().hour(),null);
			setOwnProperty("minute",true,false,(t,k) -> self(t).dateTime().time().minute(),null);
			setOwnProperty("second",true,false,(t,k) -> self(t).dateTime().time().second(),null);
			setOwnProperty("millisecond",true,false,(t,k) -> self(t).dateTime().time().millisecond(),null);
			setOwnProperty("microsecond",true,false,(t,k) -> self(t).dateTime().time().microsecond(),null);
			setOwnProperty("nanosecond",true,false,(t,k) -> self(t).dateTime().time().nanosecond(),null);
			setOwnProperty("epochMilliseconds",true,false,(t,k) -> TemporalInstantObject.epochMilliseconds(self(t).epochNs),null);
			setOwnProperty("epochNanoseconds",true,false,(t,k) -> self(t).epochNs,null);
			setOwnProperty("hoursInDay",true,false,(t,k) -> {
				TemporalZonedDateTimeObject z = self(t);
				IsoDate today = z.dateTime().date();
				IsoDate tomorrow = addDaysToISODate(today,1);
				BigInteger todayNs = getStartOfDay(z.timeZone,today);
				BigInteger tomorrowNs = getStartOfDay(z.timeZone,tomorrow);
				return num(totalTimeDuration(TimeDuration.fromEpochNsDiff(tomorrowNs,todayNs),TemporalUnit.HOUR));
			},null);
			setOwnProperty("offsetNanoseconds",true,false,(t,k) -> {
				TemporalZonedDateTimeObject z = self(t);
				return num(getOffsetNanosecondsFor(z.timeZone,z.epochNs));
			},null);
			setOwnProperty("offset",true,false,(t,k) -> {
				TemporalZonedDateTimeObject z = self(t);
				return formatUTCOffsetNanoseconds(getOffsetNanosecondsFor(z.timeZone,z.epochNs));
			},null);
			setOwnMethod(new TemporalFn(env,"with",1,(t,a) -> with(self(t),arg(a,0),arg(a,1))));
			setOwnMethod(new TemporalFn(env,"withPlainTime",0,(t,a) -> {
				TemporalZonedDateTimeObject z = self(t);
				IsoDate iso = z.dateTime().date();
				Object temporalTime = arg(a,0);
				BigInteger ns;
				if(isUndefined(temporalTime)) {
					ns = getStartOfDay(z.timeZone,iso);
				} else {
					TemporalPlainTimeObject time = toTemporalTime(temporalTime,RuntimeUtil.UNDEFINED);
					ns = getEpochNanosecondsFor(z.timeZone,new IsoDateTime(iso,time.getTime()),"compatible");
				}
				return createTemporalZonedDateTime(ns,z.timeZone,z.calendar);
			}));
			setOwnMethod(new TemporalFn(env,"withTimeZone",1,(t,a) -> {
				TemporalZonedDateTimeObject z = self(t);
				String tz = toTemporalTimeZoneIdentifier(arg(a,0));
				return createTemporalZonedDateTime(z.epochNs,tz,z.calendar);
			}));
			setOwnMethod(new TemporalFn(env,"withCalendar",1,(t,a) -> {
				TemporalZonedDateTimeObject z = self(t);
				String cal = toTemporalCalendarIdentifier(arg(a,0));
				return createTemporalZonedDateTime(z.epochNs,z.timeZone,cal);
			}));
			setOwnMethod(new TemporalFn(env,"add",1,(t,a) -> addDurationToZonedDateTime(false,self(t),arg(a,0),arg(a,1))));
			setOwnMethod(new TemporalFn(env,"subtract",1,(t,a) -> addDurationToZonedDateTime(true,self(t),arg(a,0),arg(a,1))));
			setOwnMethod(new TemporalFn(env,"until",1,(t,a) -> differenceTemporalZonedDateTime(false,self(t),arg(a,0),arg(a,1))));
			setOwnMethod(new TemporalFn(env,"since",1,(t,a) -> differenceTemporalZonedDateTime(true,self(t),arg(a,0),arg(a,1))));
			setOwnMethod(new TemporalFn(env,"round",1,(t,a) -> round(self(t),arg(a,0))));
			setOwnMethod(new TemporalFn(env,"equals",1,(t,a) -> {
				TemporalZonedDateTimeObject z = self(t);
				TemporalZonedDateTimeObject o = toTemporalZonedDateTime(arg(a,0),RuntimeUtil.UNDEFINED);
				return z.epochNs.equals(o.epochNs) && timeZoneEquals(z.timeZone,o.timeZone) && z.calendar.equals(o.calendar);
			}));
			setOwnMethod(new TemporalFn(env,"toString",0,(t,a) -> toStringImpl(self(t),arg(a,0))));
			setOwnMethod(new TemporalFn(env,"toJSON",0,(t,a) -> temporalZonedDateTimeToString(self(t),PRECISION_AUTO,"auto","auto","auto",1,null,null)));
			setOwnMethod(new TemporalFn(env,"toLocaleString",0,(t,a) -> temporalZonedDateTimeToString(self(t),PRECISION_AUTO,"auto","auto","auto",1,null,null)));
			setOwnMethod(new TemporalFn(env,"valueOf",0,(t,a) -> {
				valueOfThrows(CLASSNAME);
				return null;
			}));
			setOwnMethod(new TemporalFn(env,"startOfDay",0,(t,a) -> {
				TemporalZonedDateTimeObject z = self(t);
				return createTemporalZonedDateTime(getStartOfDay(z.timeZone,z.dateTime().date()),z.timeZone,z.calendar);
			}));
			setOwnMethod(new TemporalFn(env,"getTimeZoneTransition",1,(t,a) -> getTimeZoneTransition(self(t),arg(a,0))));
			setOwnMethod(new TemporalFn(env,"toInstant",0,(t,a) -> createTemporalInstant(self(t).epochNs)));
			setOwnMethod(new TemporalFn(env,"toPlainDate",0,(t,a) -> {
				TemporalZonedDateTimeObject z = self(t);
				return createTemporalDate(z.dateTime().date(),z.calendar);
			}));
			setOwnMethod(new TemporalFn(env,"toPlainTime",0,(t,a) -> createTemporalTime(self(t).dateTime().time())));
			setOwnMethod(new TemporalFn(env,"toPlainDateTime",0,(t,a) -> {
				TemporalZonedDateTimeObject z = self(t);
				return createTemporalDateTime(z.dateTime(),z.calendar);
			}));
		}

		@Override
		public String getClassName() {
			return "Temporal.ZonedDateTime";
		}
	}

	private static Object with(TemporalZonedDateTimeObject z, Object like, Object options) {
		if(!isObj(like)) {
			throw typeError("invalid zoned-date-time-like");
		}
		rejectTemporalLikeObject(like);
		long offsetNs = getOffsetNanosecondsFor(z.timeZone,z.epochNs);
		IsoDateTime dt = z.dateTime();
		Fields fields = TemporalPlainDateTimeObject.dateTimeFields(dt,z.calendar);
		fields.offset = formatUTCOffsetNanoseconds(offsetNs);
		Fields partial = prepareCalendarFields(z.calendar,like,DATE_FIELDS,new String[] {"hour","minute","second","millisecond","microsecond","nanosecond","offset"},null);
		fields = calendarMergeFields(z.calendar,fields,partial);
		Object resolved = getOptionsObject(options);
		String disambiguation = getTemporalDisambiguationOption(resolved);
		String offset = getTemporalOffsetOption(resolved,"prefer");
		String overflow = getTemporalOverflowOption(resolved);
		IsoDateTime newDateTime = interpretTemporalDateTimeFields(z.calendar,fields,overflow);
		long newOffsetNs = parseDateTimeUTCOffset(fields.offset);
		BigInteger ns = interpretISODateTimeOffset(newDateTime.date(),newDateTime.time(),"option",newOffsetNs,z.timeZone,disambiguation,offset,false);
		return createTemporalZonedDateTime(ns,z.timeZone,z.calendar);
	}

	private static Object round(TemporalZonedDateTimeObject z, Object roundTo) {
		long[] increment = new long[1];
		RoundingMode[] mode = new RoundingMode[1];
		TemporalUnit smallestUnit = TemporalPlainDateTimeObject.roundOptions(roundTo,increment,mode)[0];
		if(smallestUnit==TemporalUnit.NANOSECOND && increment[0]==1) {
			return createTemporalZonedDateTime(z.epochNs,z.timeZone,z.calendar);
		}
		BigInteger thisNs = z.epochNs;
		IsoDateTime iso = z.dateTime();
		BigInteger ns;
		if(smallestUnit==TemporalUnit.DAY) {
			IsoDate dateStart = iso.date();
			IsoDate dateEnd = addDaysToISODate(dateStart,1);
			BigInteger startNs = getStartOfDay(z.timeZone,dateStart);
			BigInteger endNs = getStartOfDay(z.timeZone,dateEnd);
			if(thisNs.compareTo(endNs)>=0) {
				thisNs = endNs.subtract(BigInteger.ONE);
			}
			BigInteger dayLengthNs = endNs.subtract(startNs);
			TimeDuration dayProgress = TimeDuration.fromEpochNsDiff(thisNs,startNs);
			TimeDuration roundedDay = dayProgress.round(dayLengthNs,mode[0]);
			ns = roundedDay.addToEpochNs(startNs);
		} else {
			IsoDateTime rounded = roundISODateTime(iso,increment[0],smallestUnit,mode[0]);
			long offsetNs = getOffsetNanosecondsFor(z.timeZone,thisNs);
			ns = interpretISODateTimeOffset(rounded.date(),rounded.time(),"option",offsetNs,z.timeZone,"compatible","prefer",false);
		}
		return createTemporalZonedDateTime(ns,z.timeZone,z.calendar);
	}

	private static Object toStringImpl(TemporalZonedDateTimeObject z, Object options) {
		Object resolved = getOptionsObject(options);
		String showCalendar = getTemporalShowCalendarNameOption(resolved);
		int digits = getTemporalFractionalSecondDigitsOption(resolved);
		String showOffset = getTemporalShowOffsetOption(resolved);
		RoundingMode roundingMode = getRoundingModeOption(resolved,RoundingMode.TRUNC);
		TemporalUnit smallestUnit = getTemporalUnitValuedOption(resolved,"smallestUnit",null);
		String showTimeZone = getTemporalShowTimeZoneNameOption(resolved);
		validateTemporalUnitValue(smallestUnit,"time",false);
		if(smallestUnit==TemporalUnit.HOUR) {
			throw rangeError("smallestUnit must be a time unit other than \"hour\"");
		}
		PrecisionRecord p = toSecondsStringPrecisionRecord(smallestUnit,digits);
		return temporalZonedDateTimeToString(z,p.precision(),showCalendar,showTimeZone,showOffset,p.increment(),p.unit(),roundingMode);
	}

	private static Object getTimeZoneTransition(TemporalZonedDateTimeObject z, Object directionParam) {
		if(isUndefined(directionParam)) {
			throw typeError("options parameter is required");
		}
		String direction;
		if(isStr(directionParam)) {
			direction = directionParam.toString();
			if(!direction.equals("next") && !direction.equals("previous")) {
				throw rangeError("direction must be one of next, previous, not "+direction);
			}
		} else {
			direction = getDirectionOption(getOptionsObject(directionParam));
		}
		if(isOffsetTimeZoneIdentifier(z.timeZone) || z.timeZone.equals("UTC")) {
			return null;
		}
		BigInteger ns = direction.equals("next") ? getNamedTimeZoneNextTransition(z.timeZone,z.epochNs) : getNamedTimeZonePreviousTransition(z.timeZone,z.epochNs);
		return ns==null ? null : createTemporalZonedDateTime(ns,z.timeZone,z.calendar);
	}

	public static class ConstructorImpl extends BaseStandardConstructor {

		public ConstructorImpl(JSEnvironment env) {
			super(env,CLASSNAME,Prototype.get(env),2);
			setOwnMethod(new TemporalFn(env,"from",1,(t,a) -> toTemporalZonedDateTime(arg(a,0),arg(a,1))));
			setOwnMethod(new TemporalFn(env,"compare",2,(t,a) -> {
				TemporalZonedDateTimeObject one = toTemporalZonedDateTime(arg(a,0),RuntimeUtil.UNDEFINED);
				TemporalZonedDateTimeObject two = toTemporalZonedDateTime(arg(a,1),RuntimeUtil.UNDEFINED);
				return one.epochNs.compareTo(two.epochNs);
			}));
		}

		@Override
		public Class<?> getNativeClass() {
			return TemporalZonedDateTimeObject.class;
		}

		@Override
		public Object constructObject(Object[] parameters, Constructor topConstructor) {
			BigInteger ns = TemporalInstantObject.toBigInt(arg(parameters,0));
			String tz = requireString(arg(parameters,1));
			TimeZoneIdentifier id = parseTimeZoneIdentifier(tz);
			if(id.offsetMinutes()==null) {
				String identifier = getAvailableNamedTimeZoneIdentifier(id.tzName());
				if(identifier==null) {
					throw rangeError("unknown time zone "+id.tzName());
				}
				tz = identifier;
			} else {
				tz = formatOffsetTimeZoneIdentifier(id.offsetMinutes());
			}
			Object cal = arg(parameters,2);
			String calendar = isUndefined(cal) ? ISO8601 : canonicalizeCalendar(requireString(cal));
			validateEpochNanoseconds(ns);
			return applyNewTargetPrototype(new TemporalZonedDateTimeObject(getEnvironment(),ns,tz,calendar),topConstructor);
		}

		@Override
		public Object call(Object _this, Object[] parameters) {
			throw RuntimeUtil.typeError("Constructor Temporal.ZonedDateTime requires 'new'");
		}
	}
}
