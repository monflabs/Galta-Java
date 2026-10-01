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
import org.monflabs.galtajs.rt.builtins.standard.temporal.TemporalMath.RoundingMode;

/**
 * Temporal.PlainTime.
 */
public class TemporalPlainTimeObject extends NativeObject {

	public static final String CLASSNAME = "PlainTime";

	private final TimeRecord time;

	public TemporalPlainTimeObject(JSEnvironment env, TimeRecord time) {
		super(env);
		this.time = time;
	}

	public TimeRecord getTime() {
		return time;
	}

	@Override
	public String getClassName() {
		return "Temporal.PlainTime";
	}

	@Override
	protected Object getDefaultPrototype() {
		return Prototype.get(getEnvironment());
	}

	private static TemporalPlainTimeObject self(Object o) {
		return receiver(o,TemporalPlainTimeObject.class,CLASSNAME);
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
			setOwnProperty(Symbol.TO_STRING_TAG,"Temporal.PlainTime",PropertyDescriptor.DESC_PROP_TOSTRINGTAG);
			setOwnProperty("hour",true,false,(t,k) -> self(t).time.hour(),null);
			setOwnProperty("minute",true,false,(t,k) -> self(t).time.minute(),null);
			setOwnProperty("second",true,false,(t,k) -> self(t).time.second(),null);
			setOwnProperty("millisecond",true,false,(t,k) -> self(t).time.millisecond(),null);
			setOwnProperty("microsecond",true,false,(t,k) -> self(t).time.microsecond(),null);
			setOwnProperty("nanosecond",true,false,(t,k) -> self(t).time.nanosecond(),null);
			setOwnMethod(new TemporalFn(env,"with",1,(t,a) -> with(self(t),arg(a,0),arg(a,1))));
			setOwnMethod(new TemporalFn(env,"add",1,(t,a) -> addDurationToTime(false,self(t),arg(a,0))));
			setOwnMethod(new TemporalFn(env,"subtract",1,(t,a) -> addDurationToTime(true,self(t),arg(a,0))));
			setOwnMethod(new TemporalFn(env,"until",1,(t,a) -> differenceTemporalPlainTime(false,self(t),arg(a,0),arg(a,1))));
			setOwnMethod(new TemporalFn(env,"since",1,(t,a) -> differenceTemporalPlainTime(true,self(t),arg(a,0),arg(a,1))));
			setOwnMethod(new TemporalFn(env,"round",1,(t,a) -> round(self(t),arg(a,0))));
			setOwnMethod(new TemporalFn(env,"equals",1,(t,a) -> {
				TemporalPlainTimeObject p = self(t);
				return compareTimeRecord(p.time,toTemporalTime(arg(a,0),RuntimeUtil.UNDEFINED).time)==0;
			}));
			setOwnMethod(new TemporalFn(env,"toString",0,(t,a) -> toStringImpl(self(t),arg(a,0))));
			setOwnMethod(new TemporalFn(env,"toJSON",0,(t,a) -> timeRecordToString(self(t).time,PRECISION_AUTO)));
			setOwnMethod(new TemporalFn(env,"toLocaleString",0,(t,a) -> timeRecordToString(self(t).time,PRECISION_AUTO)));
			setOwnMethod(new TemporalFn(env,"valueOf",0,(t,a) -> {
				valueOfThrows(CLASSNAME);
				return null;
			}));
		}

		@Override
		public String getClassName() {
			return "Temporal.PlainTime";
		}
	}

	private static Object with(TemporalPlainTimeObject p, Object like, Object options) {
		if(!isObj(like)) {
			throw typeError("invalid argument");
		}
		rejectTemporalLikeObject(like);
		Double[] partial = toTemporalTimeRecord(like,true);
		TimeRecord t = p.time;
		double[] f = {t.hour(),t.minute(),t.second(),t.millisecond(),t.microsecond(),t.nanosecond()};
		for(int i=0; i<6; i++) {
			if(partial[i]!=null) {
				f[i] = partial[i];
			}
		}
		String overflow = getTemporalOverflowOption(getOptionsObject(options));
		return createTemporalTime(regulateTime(f[0],f[1],f[2],f[3],f[4],f[5],overflow));
	}

	private static Object round(TemporalPlainTimeObject p, Object roundTo) {
		if(isUndefined(roundTo)) {
			throw typeError("options parameter is required");
		}
		long roundingIncrement;
		RoundingMode roundingMode;
		TemporalUnit smallestUnit;
		if(isStr(roundTo)) {
			roundingIncrement = 1;
			roundingMode = RoundingMode.HALF_EXPAND;
			smallestUnit = unitFromStringParam("smallestUnit",roundTo.toString());
		} else {
			Object options = getOptionsObject(roundTo);
			roundingIncrement = getRoundingIncrementOption(options);
			roundingMode = getRoundingModeOption(options,RoundingMode.HALF_EXPAND);
			smallestUnit = getTemporalUnitValuedOption(options,"smallestUnit",REQUIRED);
		}
		validateTemporalUnitValue(smallestUnit,"time",false);
		validateTemporalRoundingIncrement(roundingIncrement,maximumIncrement(smallestUnit),false);
		return createTemporalTime(roundTime(p.time,roundingIncrement,smallestUnit,roundingMode));
	}

	private static Object toStringImpl(TemporalPlainTimeObject p, Object options) {
		Object resolved = getOptionsObject(options);
		int digits = getTemporalFractionalSecondDigitsOption(resolved);
		RoundingMode roundingMode = getRoundingModeOption(resolved,RoundingMode.TRUNC);
		TemporalUnit smallestUnit = getTemporalUnitValuedOption(resolved,"smallestUnit",null);
		validateTemporalUnitValue(smallestUnit,"time",false);
		if(smallestUnit==TemporalUnit.HOUR) {
			throw rangeError("smallestUnit must be a time unit other than \"hour\"");
		}
		PrecisionRecord pr = toSecondsStringPrecisionRecord(smallestUnit,digits);
		TimeRecord time = roundTime(p.time,pr.increment(),pr.unit(),roundingMode);
		return timeRecordToString(time,pr.precision());
	}

	public static class ConstructorImpl extends BaseStandardConstructor {

		public ConstructorImpl(JSEnvironment env) {
			super(env,CLASSNAME,Prototype.get(env),0);
			setOwnMethod(new TemporalFn(env,"from",1,(t,a) -> toTemporalTime(arg(a,0),arg(a,1))));
			setOwnMethod(new TemporalFn(env,"compare",2,(t,a) -> {
				TemporalPlainTimeObject one = toTemporalTime(arg(a,0),RuntimeUtil.UNDEFINED);
				TemporalPlainTimeObject two = toTemporalTime(arg(a,1),RuntimeUtil.UNDEFINED);
				return Integer.signum(compareTimeRecord(one.time,two.time));
			}));
		}

		@Override
		public Class<?> getNativeClass() {
			return TemporalPlainTimeObject.class;
		}

		@Override
		public Object constructObject(Object[] parameters, Constructor topConstructor) {
			double[] f = new double[6];
			for(int i=0; i<6; i++) {
				Object v = arg(parameters,i);
				f[i] = isUndefined(v) ? 0 : toIntegerWithTruncation(v);
			}
			rejectTime(f[0],f[1],f[2],f[3],f[4],f[5]);
			TimeRecord time = new TimeRecord((int)f[0],(int)f[1],(int)f[2],(int)f[3],(int)f[4],(int)f[5]);
			return applyNewTargetPrototype(new TemporalPlainTimeObject(getEnvironment(),time),topConstructor);
		}

		@Override
		public Object call(Object _this, Object[] parameters) {
			throw RuntimeUtil.typeError("Constructor Temporal.PlainTime requires 'new'");
		}
	}
}
