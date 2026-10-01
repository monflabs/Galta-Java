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

import java.math.BigInteger;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.builtins.NativeObject;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;

/**
 * The Temporal namespace object, and Temporal.Now.
 */
public class TemporalNamespace extends NativeObject {

	public static final String OBJECTNAME = "Temporal";

	public TemporalNamespace(JSEnvironment env) {
		super(env);
		setOwnProperty(Symbol.TO_STRING_TAG,OBJECTNAME,PropertyDescriptor.DESC_PROP_TOSTRINGTAG);
		setOwnProperty(TemporalInstantObject.CLASSNAME,new TemporalInstantObject.ConstructorImpl(env),PropertyDescriptor.DESC_METHOD);
		setOwnProperty(TemporalPlainDateTimeObject.CLASSNAME,new TemporalPlainDateTimeObject.ConstructorImpl(env),PropertyDescriptor.DESC_METHOD);
		setOwnProperty(TemporalPlainDateObject.CLASSNAME,new TemporalPlainDateObject.ConstructorImpl(env),PropertyDescriptor.DESC_METHOD);
		setOwnProperty(TemporalPlainTimeObject.CLASSNAME,new TemporalPlainTimeObject.ConstructorImpl(env),PropertyDescriptor.DESC_METHOD);
		setOwnProperty(TemporalPlainYearMonthObject.CLASSNAME,new TemporalPlainYearMonthObject.ConstructorImpl(env),PropertyDescriptor.DESC_METHOD);
		setOwnProperty(TemporalPlainMonthDayObject.CLASSNAME,new TemporalPlainMonthDayObject.ConstructorImpl(env),PropertyDescriptor.DESC_METHOD);
		setOwnProperty(TemporalDurationObject.CLASSNAME,new TemporalDurationObject.ConstructorImpl(env),PropertyDescriptor.DESC_METHOD);
		setOwnProperty(TemporalZonedDateTimeObject.CLASSNAME,new TemporalZonedDateTimeObject.ConstructorImpl(env),PropertyDescriptor.DESC_METHOD);
		setOwnProperty("Now",new Now(env),PropertyDescriptor.DESC_METHOD);
	}

	@Override
	public String getClassName() {
		return OBJECTNAME;
	}

	public static class Now extends NativeObject {

		public Now(JSEnvironment env) {
			super(env);
			setOwnProperty(Symbol.TO_STRING_TAG,"Temporal.Now",PropertyDescriptor.DESC_PROP_TOSTRINGTAG);
			setOwnMethod(new TemporalFn(env,"instant",0,(t,a) -> createTemporalInstant(systemUTCEpochNanoseconds())));
			setOwnMethod(new TemporalFn(env,"timeZoneId",0,(t,a) -> defaultTimeZone()));
			setOwnMethod(new TemporalFn(env,"zonedDateTimeISO",0,(t,a) -> {
				String tz = timeZoneOf(arg(a,0));
				return createTemporalZonedDateTime(systemUTCEpochNanoseconds(),tz,ISO8601);
			}));
			setOwnMethod(new TemporalFn(env,"plainDateTimeISO",0,(t,a) -> createTemporalDateTime(systemDateTime(arg(a,0)),ISO8601)));
			setOwnMethod(new TemporalFn(env,"plainDateISO",0,(t,a) -> createTemporalDate(systemDateTime(arg(a,0)).date(),ISO8601)));
			setOwnMethod(new TemporalFn(env,"plainTimeISO",0,(t,a) -> createTemporalTime(systemDateTime(arg(a,0)).time())));
		}

		@Override
		public String getClassName() {
			return "Temporal.Now";
		}

		private static String timeZoneOf(Object tz) {
			return isUndefined(tz) ? defaultTimeZone() : toTemporalTimeZoneIdentifier(tz);
		}

		private static IsoDateTime systemDateTime(Object tz) {
			String timeZone = timeZoneOf(tz);
			BigInteger ns = systemUTCEpochNanoseconds();
			return getISODateTimeFor(timeZone,ns);
		}
	}
}
