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
import org.monflabs.galtajs.rt.builtins.standard.bigint.BuiltinBigIntConstructor;
import org.monflabs.galtajs.rt.builtins.standard.temporal.TemporalMath.RoundingMode;

/**
 * Temporal.Instant.
 */
public class TemporalInstantObject extends NativeObject {

	public static final String CLASSNAME = "Instant";
	private static final BigInteger MILLION = BigInteger.valueOf(1_000_000L);

	private final BigInteger epochNs;

	public TemporalInstantObject(JSEnvironment env, BigInteger epochNs) {
		super(env);
		this.epochNs = epochNs;
	}

	public BigInteger getEpochNs() {
		return epochNs;
	}

	@Override
	public String getClassName() {
		return "Temporal.Instant";
	}

	@Override
	protected Object getDefaultPrototype() {
		return Prototype.get(getEnvironment());
	}

	private static TemporalInstantObject self(Object o) {
		return receiver(o,TemporalInstantObject.class,CLASSNAME);
	}

	static BigInteger toBigInt(Object v) {
		Object prim = RuntimeUtil.toPrimitive(env(),v,RuntimeUtil.HINT.NUMBER);
		return BuiltinBigIntConstructor.toBigInt(env(),prim);
	}

	static Object epochMilliseconds(BigInteger ns) {
		BigInteger ms = ns.divide(MILLION);
		if(ns.signum()<0 && ns.remainder(MILLION).signum()!=0) {
			ms = ms.subtract(BigInteger.ONE);
		}
		return num(ms.longValue());
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
			setOwnProperty(Symbol.TO_STRING_TAG,"Temporal.Instant",PropertyDescriptor.DESC_PROP_TOSTRINGTAG);
			setOwnProperty("epochMilliseconds",true,false,(t,k) -> epochMilliseconds(self(t).epochNs),null);
			setOwnProperty("epochNanoseconds",true,false,(t,k) -> self(t).epochNs,null);
			setOwnMethod(new TemporalFn(env,"add",1,(t,a) -> addDurationToInstant(false,self(t),arg(a,0))));
			setOwnMethod(new TemporalFn(env,"subtract",1,(t,a) -> addDurationToInstant(true,self(t),arg(a,0))));
			setOwnMethod(new TemporalFn(env,"until",1,(t,a) -> differenceTemporalInstant(false,self(t),arg(a,0),arg(a,1))));
			setOwnMethod(new TemporalFn(env,"since",1,(t,a) -> differenceTemporalInstant(true,self(t),arg(a,0),arg(a,1))));
			setOwnMethod(new TemporalFn(env,"round",1,(t,a) -> round(self(t),arg(a,0))));
			setOwnMethod(new TemporalFn(env,"equals",1,(t,a) -> {
				TemporalInstantObject i = self(t);
				return i.epochNs.equals(toTemporalInstant(arg(a,0)).epochNs);
			}));
			setOwnMethod(new TemporalFn(env,"toString",0,(t,a) -> toStringImpl(self(t),arg(a,0))));
			setOwnMethod(new TemporalFn(env,"toJSON",0,(t,a) -> temporalInstantToString(self(t).epochNs,null,PRECISION_AUTO)));
			setOwnMethod(new TemporalFn(env,"toLocaleString",0,(t,a) -> temporalInstantToString(self(t).epochNs,null,PRECISION_AUTO)));
			setOwnMethod(new TemporalFn(env,"valueOf",0,(t,a) -> {
				valueOfThrows(CLASSNAME);
				return null;
			}));
			setOwnMethod(new TemporalFn(env,"toZonedDateTimeISO",1,(t,a) -> {
				TemporalInstantObject i = self(t);
				String timeZone = toTemporalTimeZoneIdentifier(arg(a,0));
				return createTemporalZonedDateTime(i.epochNs,timeZone,ISO8601);
			}));
		}

		@Override
		public String getClassName() {
			return "Temporal.Instant";
		}
	}

	static long maximumInstantIncrement(TemporalUnit unit) {
		switch(unit) {
			case HOUR: return 24;
			case MINUTE: return 1440;
			case SECOND: return 86400;
			case MILLISECOND: return 86_400_000L;
			case MICROSECOND: return 86_400_000_000L;
			default: return 86_400_000_000_000L;
		}
	}

	private static Object round(TemporalInstantObject i, Object roundTo) {
		if(isUndefined(roundTo)) {
			throw typeError("options parameter is required");
		}
		long roundingIncrement;
		RoundingMode roundingMode;
		TemporalUnit smallestUnit;
		if(isStr(roundTo)) {
			roundingIncrement = 1;
			roundingMode = RoundingMode.HALF_EXPAND;
			smallestUnit = TemporalUnit.ofName(roundTo.toString());
			if(smallestUnit==null) {
				throw rangeError("smallestUnit "+roundTo+" is not a valid unit");
			}
		} else {
			Object options = getOptionsObject(roundTo);
			roundingIncrement = getRoundingIncrementOption(options);
			roundingMode = getRoundingModeOption(options,RoundingMode.HALF_EXPAND);
			smallestUnit = getTemporalUnitValuedOption(options,"smallestUnit",REQUIRED);
		}
		validateTemporalUnitValue(smallestUnit,"time",false);
		validateTemporalRoundingIncrement(roundingIncrement,maximumInstantIncrement(smallestUnit),true);
		return createTemporalInstant(roundTemporalInstant(i.epochNs,roundingIncrement,smallestUnit,roundingMode));
	}

	private static Object toStringImpl(TemporalInstantObject i, Object options) {
		Object resolved = getOptionsObject(options);
		int digits = getTemporalFractionalSecondDigitsOption(resolved);
		RoundingMode roundingMode = getRoundingModeOption(resolved,RoundingMode.TRUNC);
		TemporalUnit smallestUnit = getTemporalUnitValuedOption(resolved,"smallestUnit",null);
		Object timeZoneArg = getOpt(resolved,"timeZone");
		validateTemporalUnitValue(smallestUnit,"time",false);
		if(smallestUnit==TemporalUnit.HOUR) {
			throw rangeError("smallestUnit must be a time unit other than \"hour\"");
		}
		String timeZone = isUndefined(timeZoneArg) ? null : toTemporalTimeZoneIdentifier(timeZoneArg);
		PrecisionRecord p = toSecondsStringPrecisionRecord(smallestUnit,digits);
		BigInteger roundedNs = roundTemporalInstant(i.epochNs,p.increment(),p.unit(),roundingMode);
		validateEpochNanoseconds(roundedNs);
		return temporalInstantToString(roundedNs,timeZone,p.precision());
	}

	public static class ConstructorImpl extends BaseStandardConstructor {

		public ConstructorImpl(JSEnvironment env) {
			super(env,CLASSNAME,Prototype.get(env),1);
			setOwnMethod(new TemporalFn(env,"from",1,(t,a) -> toTemporalInstant(arg(a,0))));
			setOwnMethod(new TemporalFn(env,"fromEpochMilliseconds",1,(t,a) -> {
				double ms = toNumber(arg(a,0));
				if(Double.isNaN(ms) || Double.isInfinite(ms) || ms!=Math.floor(ms)) {
					throw rangeError("The number "+toJSString(arg(a,0))+" cannot be converted to a BigInt because it is not an integer");
				}
				BigInteger ns = TimeDuration.big(ms).multiply(MILLION);
				validateEpochNanoseconds(ns);
				return createTemporalInstant(ns);
			}));
			setOwnMethod(new TemporalFn(env,"fromEpochNanoseconds",1,(t,a) -> {
				BigInteger ns = toBigInt(arg(a,0));
				validateEpochNanoseconds(ns);
				return createTemporalInstant(ns);
			}));
			setOwnMethod(new TemporalFn(env,"compare",2,(t,a) -> {
				TemporalInstantObject one = toTemporalInstant(arg(a,0));
				TemporalInstantObject two = toTemporalInstant(arg(a,1));
				return one.epochNs.compareTo(two.epochNs);
			}));
		}

		@Override
		public Class<?> getNativeClass() {
			return TemporalInstantObject.class;
		}

		@Override
		public Object constructObject(Object[] parameters, Constructor topConstructor) {
			BigInteger ns = toBigInt(arg(parameters,0));
			validateEpochNanoseconds(ns);
			return applyNewTargetPrototype(new TemporalInstantObject(getEnvironment(),ns),topConstructor);
		}

		@Override
		public Object call(Object _this, Object[] parameters) {
			throw RuntimeUtil.typeError("Constructor Temporal.Instant requires 'new'");
		}
	}
}
