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
 * Temporal.Duration.
 */
public class TemporalDurationObject extends NativeObject {

	public static final String CLASSNAME = "Duration";
	private static final String[] FIELD_NAMES = {"years","months","weeks","days","hours","minutes","seconds","milliseconds","microseconds","nanoseconds"};

	private final double[] fields;

	public TemporalDurationObject(JSEnvironment env, double[] fields) {
		super(env);
		this.fields = fields;
	}

	public double[] getFields() {
		return fields;
	}

	@Override
	public String getClassName() {
		return "Temporal.Duration";
	}

	@Override
	protected Object getDefaultPrototype() {
		return Prototype.get(getEnvironment());
	}

	private static TemporalDurationObject self(Object o) {
		return receiver(o,TemporalDurationObject.class,CLASSNAME);
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
			setOwnProperty(Symbol.TO_STRING_TAG,"Temporal.Duration",PropertyDescriptor.DESC_PROP_TOSTRINGTAG);
			for(int i=0; i<FIELD_NAMES.length; i++) {
				final int index = i;
				setOwnProperty(FIELD_NAMES[i],true,false,(t,k) -> num(self(t).fields[index]),null);
			}
			setOwnProperty("sign",true,false,(t,k) -> num(durationSign(self(t).fields)),null);
			setOwnProperty("blank",true,false,(t,k) -> durationSign(self(t).fields)==0,null);

			setOwnMethod(new TemporalFn(env,"with",1,(t,a) -> {
				TemporalDurationObject d = self(t);
				Double[] partial = toTemporalPartialDurationRecord(arg(a,0));
				double[] f = new double[10];
				for(int i=0; i<10; i++) {
					f[i] = partial[i]!=null ? partial[i] : d.fields[i];
				}
				return createTemporalDuration(f);
			}));
			setOwnMethod(new TemporalFn(env,"negated",0,(t,a) -> createNegatedTemporalDuration(self(t))));
			setOwnMethod(new TemporalFn(env,"abs",0,(t,a) -> {
				double[] f = self(t).fields.clone();
				for(int i=0; i<10; i++) {
					f[i] = Math.abs(f[i]);
				}
				return createTemporalDuration(f);
			}));
			setOwnMethod(new TemporalFn(env,"add",1,(t,a) -> addDurations(false,self(t),arg(a,0))));
			setOwnMethod(new TemporalFn(env,"subtract",1,(t,a) -> addDurations(true,self(t),arg(a,0))));
			setOwnMethod(new TemporalFn(env,"round",1,(t,a) -> round(self(t),arg(a,0))));
			setOwnMethod(new TemporalFn(env,"total",1,(t,a) -> total(self(t),arg(a,0))));
			setOwnMethod(new TemporalFn(env,"toString",0,(t,a) -> toStringImpl(self(t),arg(a,0))));
			setOwnMethod(new TemporalFn(env,"toJSON",0,(t,a) -> temporalDurationToString(self(t).fields,PRECISION_AUTO)));
			setOwnMethod(new TemporalFn(env,"toLocaleString",0,(t,a) -> temporalDurationToString(self(t).fields,PRECISION_AUTO)));
			setOwnMethod(new TemporalFn(env,"valueOf",0,(t,a) -> {
				valueOfThrows(CLASSNAME);
				return null;
			}));
		}

		@Override
		public String getClassName() {
			return "Temporal.Duration";
		}
	}

	private static Object round(TemporalDurationObject d, Object roundTo) {
		if(isUndefined(roundTo)) {
			throw typeError("options parameter is required");
		}
		TemporalUnit existingLargestUnit = defaultTemporalLargestUnit(d.fields);
		Object options;
		TemporalUnit smallestUnit;
		TemporalUnit largestUnit;
		Object relativeTo;
		long roundingIncrement;
		RoundingMode roundingMode;
		if(isStr(roundTo)) {
			options = null;
			largestUnit = null;
			relativeTo = null;
			roundingIncrement = 1;
			roundingMode = RoundingMode.HALF_EXPAND;
			smallestUnit = unitFromString("smallestUnit",roundTo.toString());
		} else {
			options = getOptionsObject(roundTo);
			largestUnit = getTemporalUnitValuedOption(options,"largestUnit",null);
			relativeTo = getTemporalRelativeToOption(options);
			roundingIncrement = getRoundingIncrementOption(options);
			roundingMode = getRoundingModeOption(options,RoundingMode.HALF_EXPAND);
			smallestUnit = getTemporalUnitValuedOption(options,"smallestUnit",null);
		}
		validateTemporalUnitValue(smallestUnit,"datetime",false);
		boolean smallestUnitPresent = true;
		if(smallestUnit==null) {
			smallestUnitPresent = false;
			smallestUnit = TemporalUnit.NANOSECOND;
		}
		TemporalUnit defaultLargestUnit = TemporalUnit.larger(existingLargestUnit,smallestUnit);
		boolean largestUnitPresent = true;
		if(largestUnit==null) {
			largestUnitPresent = false;
			largestUnit = defaultLargestUnit;
		}
		if(largestUnit==TemporalUnit.AUTO) {
			largestUnit = defaultLargestUnit;
		}
		if(!smallestUnitPresent && !largestUnitPresent) {
			throw rangeError("at least one of smallestUnit or largestUnit is required");
		}
		if(TemporalUnit.larger(largestUnit,smallestUnit)!=largestUnit) {
			throw rangeError("largestUnit "+largestUnit+" cannot be smaller than smallestUnit "+smallestUnit);
		}
		long maximum = maximumIncrement(smallestUnit);
		if(maximum!=0) {
			validateTemporalRoundingIncrement(roundingIncrement,maximum,false);
		}
		if(roundingIncrement>1 && smallestUnit.date && largestUnit!=smallestUnit) {
			throw rangeError("For calendar units with roundingIncrement > 1, use largestUnit = smallestUnit");
		}
		if(relativeTo instanceof TemporalZonedDateTimeObject z) {
			InternalDuration duration = toInternalDurationRecord(d.fields);
			BigInteger target = addZonedDateTime(z.getEpochNs(),z.getTimeZone(),z.getCalendar(),duration,"constrain");
			duration = differenceZonedDateTimeWithRounding(z.getEpochNs(),target,z.getTimeZone(),z.getCalendar(),largestUnit,roundingIncrement,smallestUnit,roundingMode);
			if(largestUnit.date) {
				largestUnit = TemporalUnit.HOUR;
			}
			return temporalDurationFromInternal(duration,largestUnit);
		}
		if(relativeTo instanceof TemporalPlainDateObject p) {
			InternalDuration duration = toInternalDurationRecordWith24HourDays(d.fields);
			TimeRecord targetTime = addTime(TimeRecord.MIDNIGHT,duration.time());
			DateDuration dateDuration = adjustDateDurationRecord(duration.date(),targetTime.deltaDays(),null,null);
			IsoDate targetDate = calendarDateAdd(p.getCalendar(),p.getIsoDate(),dateDuration,"constrain");
			IsoDateTime dt = new IsoDateTime(p.getIsoDate(),TimeRecord.MIDNIGHT);
			IsoDateTime target = new IsoDateTime(targetDate,targetTime.withoutDays());
			duration = differencePlainDateTimeWithRounding(dt,target,p.getCalendar(),largestUnit,roundingIncrement,smallestUnit,roundingMode);
			return temporalDurationFromInternal(duration,largestUnit);
		}
		if(existingLargestUnit.isCalendarUnit()) {
			throw rangeError("a starting point is required for "+existingLargestUnit.plural+" balancing");
		}
		if(largestUnit.isCalendarUnit()) {
			throw rangeError("a starting point is required for "+largestUnit.plural+" balancing");
		}
		InternalDuration internal = toInternalDurationRecordWith24HourDays(d.fields);
		if(smallestUnit==TemporalUnit.DAY) {
			TimeDuration rounded = roundTimeDuration(internal.time(),roundingIncrement,TemporalUnit.DAY,roundingMode);
			long days = rounded.divideToLong(NS_PER_DAY);
			internal = new InternalDuration(new DateDuration(0,0,0,days),TimeDuration.ZERO);
		} else {
			TimeDuration rounded = roundTimeDuration(internal.time(),roundingIncrement,smallestUnit,roundingMode);
			internal = new InternalDuration(DateDuration.ZERO,rounded);
		}
		return temporalDurationFromInternal(internal,largestUnit);
	}

	// A unit given as the string shorthand of an options bag
	private static TemporalUnit unitFromString(String key, String s) {
		TemporalUnit u = TemporalUnit.ofName(s);
		if(u==null) {
			throw rangeError(key+" "+s+" is not a valid unit");
		}
		return u;
	}

	private static Object total(TemporalDurationObject d, Object totalOf) {
		if(isUndefined(totalOf)) {
			throw typeError("options argument is required");
		}
		Object relativeTo;
		TemporalUnit unit;
		if(isStr(totalOf)) {
			relativeTo = null;
			unit = unitFromString("unit",totalOf.toString());
		} else {
			Object options = getOptionsObject(totalOf);
			relativeTo = getTemporalRelativeToOption(options);
			unit = getTemporalUnitValuedOption(options,"unit",REQUIRED);
		}
		validateTemporalUnitValue(unit,"datetime",false);
		if(relativeTo instanceof TemporalZonedDateTimeObject z) {
			InternalDuration duration = toInternalDurationRecord(d.fields);
			BigInteger target = addZonedDateTime(z.getEpochNs(),z.getTimeZone(),z.getCalendar(),duration,"constrain");
			return num(differenceZonedDateTimeWithTotal(z.getEpochNs(),target,z.getTimeZone(),z.getCalendar(),unit));
		}
		if(relativeTo instanceof TemporalPlainDateObject p) {
			InternalDuration duration = toInternalDurationRecordWith24HourDays(d.fields);
			TimeRecord targetTime = addTime(TimeRecord.MIDNIGHT,duration.time());
			DateDuration dateDuration = adjustDateDurationRecord(duration.date(),targetTime.deltaDays(),null,null);
			IsoDate targetDate = calendarDateAdd(p.getCalendar(),p.getIsoDate(),dateDuration,"constrain");
			IsoDateTime dt = new IsoDateTime(p.getIsoDate(),TimeRecord.MIDNIGHT);
			IsoDateTime target = new IsoDateTime(targetDate,targetTime.withoutDays());
			return num(differencePlainDateTimeWithTotal(dt,target,p.getCalendar(),unit));
		}
		TemporalUnit largestUnit = defaultTemporalLargestUnit(d.fields);
		if(largestUnit.isCalendarUnit()) {
			throw rangeError("a starting point is required for "+largestUnit.plural+" total");
		}
		if(unit.isCalendarUnit()) {
			throw rangeError("a starting point is required for "+unit.plural+" total");
		}
		InternalDuration duration = toInternalDurationRecordWith24HourDays(d.fields);
		return num(totalTimeDuration(duration.time(),unit));
	}

	private static Object toStringImpl(TemporalDurationObject d, Object options) {
		Object resolved = getOptionsObject(options);
		int digits = getTemporalFractionalSecondDigitsOption(resolved);
		RoundingMode roundingMode = getRoundingModeOption(resolved,RoundingMode.TRUNC);
		TemporalUnit smallestUnit = getTemporalUnitValuedOption(resolved,"smallestUnit",null);
		validateTemporalUnitValue(smallestUnit,"time",false);
		if(smallestUnit==TemporalUnit.HOUR || smallestUnit==TemporalUnit.MINUTE) {
			throw rangeError("smallestUnit must be a time unit other than \"hours\" or \"minutes\"");
		}
		PrecisionRecord p = toSecondsStringPrecisionRecord(smallestUnit,digits);
		if(p.unit()==TemporalUnit.NANOSECOND && p.increment()==1) {
			return temporalDurationToString(d.fields,p.precision());
		}
		TemporalUnit largestUnit = defaultTemporalLargestUnit(d.fields);
		InternalDuration internal = toInternalDurationRecord(d.fields);
		TimeDuration time = roundTimeDuration(internal.time(),p.increment(),p.unit(),roundingMode);
		internal = new InternalDuration(internal.date(),time);
		TemporalDurationObject rounded = temporalDurationFromInternal(internal,TemporalUnit.larger(largestUnit,TemporalUnit.SECOND));
		return temporalDurationToString(rounded.fields,p.precision());
	}

	private static Object compare(Object oneArg, Object twoArg, Object options) {
		TemporalDurationObject one = toTemporalDuration(oneArg);
		TemporalDurationObject two = toTemporalDuration(twoArg);
		Object resolved = getOptionsObject(options);
		Object relativeTo = getTemporalRelativeToOption(resolved);
		if(java.util.Arrays.equals(one.fields,two.fields)) {
			return 0;
		}
		TemporalUnit largestUnit1 = defaultTemporalLargestUnit(one.fields);
		TemporalUnit largestUnit2 = defaultTemporalLargestUnit(two.fields);
		InternalDuration duration1 = toInternalDurationRecord(one.fields);
		InternalDuration duration2 = toInternalDurationRecord(two.fields);
		if(relativeTo instanceof TemporalZonedDateTimeObject z && (largestUnit1.date || largestUnit2.date)) {
			BigInteger after1 = addZonedDateTime(z.getEpochNs(),z.getTimeZone(),z.getCalendar(),duration1,"constrain");
			BigInteger after2 = addZonedDateTime(z.getEpochNs(),z.getTimeZone(),z.getCalendar(),duration2,"constrain");
			return after1.compareTo(after2);
		}
		long d1 = duration1.date().days();
		long d2 = duration2.date().days();
		if(largestUnit1.isCalendarUnit() || largestUnit2.isCalendarUnit()) {
			if(!(relativeTo instanceof TemporalPlainDateObject p)) {
				throw rangeError("A starting point is required for years, months, or weeks comparison");
			}
			d1 = dateDurationDays(duration1.date(),p);
			d2 = dateDurationDays(duration2.date(),p);
		}
		TimeDuration t1 = duration1.time().add24HourDays(d1);
		TimeDuration t2 = duration2.time().add24HourDays(d2);
		return Integer.signum(t1.compareTo(t2));
	}

	public static class ConstructorImpl extends BaseStandardConstructor {

		public ConstructorImpl(JSEnvironment env) {
			super(env,CLASSNAME,Prototype.get(env),0);
			setOwnMethod(new TemporalFn(env,"from",1,(t,a) -> toTemporalDuration(arg(a,0))));
			setOwnMethod(new TemporalFn(env,"compare",2,(t,a) -> compare(arg(a,0),arg(a,1),arg(a,2))));
		}

		@Override
		public Class<?> getNativeClass() {
			return TemporalDurationObject.class;
		}

		@Override
		public Object constructObject(Object[] parameters, Constructor topConstructor) {
			double[] f = new double[10];
			for(int i=0; i<10; i++) {
				Object v = arg(parameters,i);
				f[i] = isUndefined(v) ? 0 : toIntegerIfIntegral(v);
			}
			rejectDuration(f);
			return applyNewTargetPrototype(new TemporalDurationObject(getEnvironment(),f),topConstructor);
		}

		@Override
		public Object call(Object _this, Object[] parameters) {
			throw RuntimeUtil.typeError("Constructor Temporal.Duration requires 'new'");
		}
	}
}
