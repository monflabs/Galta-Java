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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.JSRuntimeException;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.standard.temporal.TemporalMath.RoundingMode;

/**
 * Temporal abstract operations, ported from the TC39 proposal's reference
 * polyfill (ecmascript.mjs, BSD licensed, Ecma International). Only the
 * ISO 8601 calendar is supported (no ECMA-402).
 */
public final class TemporalAO {

	private TemporalAO() {
	}

	public static final BigInteger NS_MAX = new BigInteger("8640000000000000000000");
	public static final BigInteger NS_MIN = NS_MAX.negate();
	public static final BigInteger NS_PER_DAY = TimeDuration.NS_PER_DAY;
	public static final long DAY_NANOS = 86_400_000_000_000L;
	private static final BigInteger DATETIME_NS_MAX = NS_MAX.add(NS_PER_DAY).subtract(BigInteger.ONE);
	private static final BigInteger DATETIME_NS_MIN = NS_MIN.subtract(NS_PER_DAY).add(BigInteger.ONE);
	public static final long YEAR_MIN = -271821;
	public static final long YEAR_MAX = 275760;
	public static final String ISO8601 = "iso8601";

	public static final Object REQUIRED = new Object();

	//
	// Basic helpers
	//

	public static JSEnvironment env() {
		return JSEnvironment.getEnvironment();
	}

	public static JSRuntimeException rangeError(String msg) {
		return RuntimeUtil.rangeError("{0}", msg);
	}
	public static JSRuntimeException typeError(String msg) {
		return RuntimeUtil.typeError("{0}", msg);
	}

	public static boolean isUndefined(Object v) {
		return v==RuntimeUtil.UNDEFINED;
	}
	public static boolean isObj(Object v) {
		return v!=null && v!=RuntimeUtil.UNDEFINED && RuntimeUtil.isObject(env(),v);
	}
	public static boolean isStr(Object v) {
		return v instanceof CharSequence && !RuntimeUtil.isBoxedString(env(),v);
	}
	public static boolean isNumber(Object v) {
		return v instanceof Number && !(v instanceof BigInteger) && !(v instanceof BigDecimal) && !RuntimeUtil.isBoxedNumber(env(),v);
	}

	public static Object getProp(Object obj, String key) {
		return RuntimeUtil.getProperty(env(),obj,key);
	}

	public static String toJSString(Object v) {
		return RuntimeUtil.toString(env(),v);
	}

	public static double toNumber(Object v) {
		return RuntimeUtil.toNumber(env(),v).doubleValue();
	}

	// A JS Number result: Integer when exact, Double otherwise
	public static Object num(double d) {
		if(d==(int)d && !(d==0 && 1/d<0)) {
			return Integer.valueOf((int)d);
		}
		return Double.valueOf(d);
	}
	public static Object num(long l) {
		if(l==(int)l) {
			return Integer.valueOf((int)l);
		}
		return Double.valueOf(l);
	}

	public static String requireString(Object v) {
		if(!isStr(v)) {
			throw typeError("expected a string, not "+describe(v));
		}
		return v.toString();
	}

	private static String describe(Object v) {
		if(v==null) return "null";
		if(v==RuntimeUtil.UNDEFINED) return "undefined";
		return RuntimeUtil.objectTypeName(env(),v);
	}

	public static double toIntegerWithTruncation(Object value) {
		double number = toNumber(value);
		if(Double.isNaN(number) || Double.isInfinite(number)) {
			throw rangeError("invalid number value");
		}
		double integer = number<0 ? Math.ceil(number) : Math.floor(number);
		return integer==0 ? 0 : integer;
	}

	public static double toPositiveIntegerWithTruncation(Object value, String property) {
		double integer = toIntegerWithTruncation(value);
		if(integer<=0) {
			throw rangeError("property '"+property+"' cannot be a number less than one");
		}
		return integer;
	}

	public static double toIntegerIfIntegral(Object value) {
		double number = toNumber(value);
		if(Double.isNaN(number) || Double.isInfinite(number)) {
			throw rangeError("infinity is out of range");
		}
		if(number!=Math.floor(number)) {
			throw rangeError("unsupported fractional value "+number);
		}
		return number==0 ? 0 : number;
	}

	public static String asciiLowercase(String s) {
		StringBuilder b = null;
		for(int i=0; i<s.length(); i++) {
			char c = s.charAt(i);
			if(c>='A' && c<='Z') {
				if(b==null) {
					b = new StringBuilder(s);
				}
				b.setCharAt(i,(char)(c+0x20));
			}
		}
		return b!=null ? b.toString() : s;
	}

	public static String asciiUppercase(String s) {
		StringBuilder b = null;
		for(int i=0; i<s.length(); i++) {
			char c = s.charAt(i);
			if(c>='a' && c<='z') {
				if(b==null) {
					b = new StringBuilder(s);
				}
				b.setCharAt(i,(char)(c-0x20));
			}
		}
		return b!=null ? b.toString() : s;
	}

	//
	// Options
	//

	// GetOptionsObject: null stands for an empty options object
	public static Object getOptionsObject(Object options) {
		if(isUndefined(options)) {
			return null;
		}
		if(isObj(options)) {
			return options;
		}
		throw typeError("Options parameter must be an object, not "+describe(options));
	}

	public static Object getOpt(Object options, String key) {
		if(options==null) {
			return RuntimeUtil.UNDEFINED;
		}
		return getProp(options,key);
	}

	public static String getOption(Object options, String property, String[] allowed, Object fallback) {
		Object value = getOpt(options,property);
		if(!isUndefined(value)) {
			String s = toJSString(value);
			for(String a: allowed) {
				if(a.equals(s)) {
					return a;
				}
			}
			throw rangeError(property+" must be one of "+String.join(", ",allowed)+", not "+s);
		}
		if(fallback==REQUIRED) {
			throw rangeError(property+" option is required");
		}
		return (String)fallback;
	}

	public static final String[] OVERFLOW_VALUES = {"constrain","reject"};
	public static final String[] DISAMBIGUATION_VALUES = {"compatible","earlier","later","reject"};
	public static final String[] OFFSET_VALUES = {"prefer","use","ignore","reject"};
	public static final String[] ROUNDING_MODES = {"ceil","floor","expand","trunc","halfCeil","halfFloor","halfExpand","halfTrunc","halfEven"};

	public static String getTemporalOverflowOption(Object options) {
		return getOption(options,"overflow",OVERFLOW_VALUES,"constrain");
	}
	public static String getTemporalDisambiguationOption(Object options) {
		return getOption(options,"disambiguation",DISAMBIGUATION_VALUES,"compatible");
	}
	public static String getTemporalOffsetOption(Object options, String fallback) {
		return getOption(options,"offset",OFFSET_VALUES,fallback);
	}
	public static RoundingMode getRoundingModeOption(Object options, RoundingMode fallback) {
		return RoundingMode.of(getOption(options,"roundingMode",ROUNDING_MODES,fallback.jsName));
	}
	public static String getTemporalShowCalendarNameOption(Object options) {
		return getOption(options,"calendarName",new String[] {"auto","always","never","critical"},"auto");
	}
	public static String getTemporalShowTimeZoneNameOption(Object options) {
		return getOption(options,"timeZoneName",new String[] {"auto","never","critical"},"auto");
	}
	public static String getTemporalShowOffsetOption(Object options) {
		return getOption(options,"offset",new String[] {"auto","never"},"auto");
	}
	public static String getDirectionOption(Object options) {
		return getOption(options,"direction",new String[] {"next","previous"},REQUIRED);
	}

	public static long getRoundingIncrementOption(Object options) {
		Object value = getOpt(options,"roundingIncrement");
		if(isUndefined(value)) {
			return 1;
		}
		double integer = toIntegerWithTruncation(value);
		if(integer<1 || integer>1e9) {
			throw rangeError("roundingIncrement must be at least 1 and at most 1e9, not "+toJSString(value));
		}
		return (long)integer;
	}

	public static void validateTemporalRoundingIncrement(long increment, long dividend, boolean inclusive) {
		long maximum = inclusive ? dividend : dividend-1;
		if(increment>maximum) {
			throw rangeError("roundingIncrement must be at least 1 and less than "+maximum+", not "+increment);
		}
		if(dividend%increment!=0) {
			throw rangeError("Rounding increment must divide evenly into "+dividend);
		}
	}

	// Precision: PRECISION_AUTO, PRECISION_MINUTE or 0..9 digits
	public static final int PRECISION_AUTO = -1;
	public static final int PRECISION_MINUTE = -2;

	public static int getTemporalFractionalSecondDigitsOption(Object options) {
		Object value = getOpt(options,"fractionalSecondDigits");
		if(isUndefined(value)) {
			return PRECISION_AUTO;
		}
		if(!isNumber(value)) {
			String s = toJSString(value);
			if(!"auto".equals(s)) {
				throw rangeError("fractionalSecondDigits must be 'auto' or 0 through 9, not "+s);
			}
			return PRECISION_AUTO;
		}
		double d = Math.floor(((Number)value).doubleValue());
		if(Double.isNaN(d) || Double.isInfinite(d) || d<0 || d>9) {
			throw rangeError("fractionalSecondDigits must be 'auto' or 0 through 9, not "+toJSString(value));
		}
		return (int)d;
	}

	// ToSecondsStringPrecisionRecord: {precision, unit, increment}
	public record PrecisionRecord(int precision, TemporalUnit unit, long increment) {
	}

	public static PrecisionRecord toSecondsStringPrecisionRecord(TemporalUnit smallestUnit, int precision) {
		if(smallestUnit!=null) {
			switch(smallestUnit) {
				case MINUTE: return new PrecisionRecord(PRECISION_MINUTE,TemporalUnit.MINUTE,1);
				case SECOND: return new PrecisionRecord(0,TemporalUnit.SECOND,1);
				case MILLISECOND: return new PrecisionRecord(3,TemporalUnit.MILLISECOND,1);
				case MICROSECOND: return new PrecisionRecord(6,TemporalUnit.MICROSECOND,1);
				case NANOSECOND: return new PrecisionRecord(9,TemporalUnit.NANOSECOND,1);
				default:
			}
		}
		if(precision==PRECISION_AUTO) {
			return new PrecisionRecord(precision,TemporalUnit.NANOSECOND,1);
		}
		if(precision==0) {
			return new PrecisionRecord(0,TemporalUnit.SECOND,1);
		}
		if(precision<=3) {
			return new PrecisionRecord(precision,TemporalUnit.MILLISECOND,pow10(3-precision));
		}
		if(precision<=6) {
			return new PrecisionRecord(precision,TemporalUnit.MICROSECOND,pow10(6-precision));
		}
		return new PrecisionRecord(precision,TemporalUnit.NANOSECOND,pow10(9-precision));
	}

	private static long pow10(int n) {
		long r = 1;
		for(int i=0; i<n; i++) {
			r *= 10;
		}
		return r;
	}

	private static final String[] UNIT_VALUES;
	static {
		List<String> l = new ArrayList<>();
		for(TemporalUnit u: TemporalUnit.values()) {
			if(u!=TemporalUnit.AUTO) {
				l.add(u.singular);
				l.add(u.plural);
			}
		}
		l.add("auto");
		UNIT_VALUES = l.toArray(new String[l.size()]);
	}

	// Returns null when the option is undefined (and not required)
	public static TemporalUnit getTemporalUnitValuedOption(Object options, String key, Object requiredOrNull) {
		String s = getOption(options,key,UNIT_VALUES,requiredOrNull==REQUIRED ? REQUIRED : null);
		if(s==null) {
			return null;
		}
		return TemporalUnit.ofName(s);
	}

	// The string shorthand of round()/total(): {smallestUnit: s} or {unit: s}
	public static TemporalUnit unitFromStringParam(String key, String s) {
		for(String a: UNIT_VALUES) {
			if(a.equals(s)) {
				return TemporalUnit.ofName(s);
			}
		}
		throw rangeError(key+" must be one of "+String.join(", ",UNIT_VALUES)+", not "+s);
	}

	// unitGroup: "date", "time" or "datetime"
	public static void validateTemporalUnitValue(TemporalUnit value, String unitGroup, boolean allowAuto) {
		if(value==null) {
			return;
		}
		if(value==TemporalUnit.AUTO) {
			if(allowAuto) {
				return;
			}
		} else {
			if(unitGroup.equals("datetime")) {
				return;
			}
			if(unitGroup.equals("date") && value.date) {
				return;
			}
			if(unitGroup.equals("time") && !value.date) {
				return;
			}
		}
		throw rangeError(value.singular+" not allowed as a "+unitGroup+" unit");
	}

	//
	// ISO calendar arithmetic
	//

	public static boolean isLeapYear(long year) {
		return (year%4==0) && (year%100!=0 || year%400==0);
	}

	private static final int[] DAYS_IN_MONTH = {31,28,31,30,31,30,31,31,30,31,30,31};

	public static int isoDaysInMonth(long year, int month) {
		if(month==2 && isLeapYear(year)) {
			return 29;
		}
		return DAYS_IN_MONTH[month-1];
	}

	// Days since 1970-01-01 (proleptic Gregorian)
	public static long epochDays(long y, int m, int d) {
		y -= m<=2 ? 1 : 0;
		long era = Math.floorDiv(y,400);
		long yoe = y-era*400;
		long doy = (153*(m+(m>2 ? -3 : 9))+2)/5+d-1;
		long doe = yoe*365+yoe/4-yoe/100+doy;
		return era*146097+doe-719468;
	}
	public static long epochDays(IsoDate d) {
		return epochDays(d.year(),d.month(),d.day());
	}

	public static IsoDate fromEpochDays(long z) {
		z += 719468;
		long era = Math.floorDiv(z,146097);
		long doe = z-era*146097;
		long yoe = (doe-doe/1460+doe/36524-doe/146096)/365;
		long y = yoe+era*400;
		long doy = doe-(365*yoe+yoe/4-yoe/100);
		long mp = (5*doy+2)/153;
		int d = (int)(doy-(153*mp+2)/5+1);
		int m = (int)(mp<10 ? mp+3 : mp-9);
		return new IsoDate(y+(m<=2 ? 1 : 0),m,d);
	}

	// BalanceISOYearMonth: {year, month}
	public static long[] balanceISOYearMonth(long year, long month) {
		month -= 1;
		year += Math.floorDiv(month,12);
		month = Math.floorMod(month,12)+1;
		return new long[] {year,month};
	}

	public static IsoDate addDaysToISODate(IsoDate date, long days) {
		if(days==0) {
			return date;
		}
		return fromEpochDays(epochDays(date)+days);
	}

	public static int compareISODate(IsoDate d1, IsoDate d2) {
		if(d1.year()!=d2.year()) return d1.year()<d2.year() ? -1 : 1;
		if(d1.month()!=d2.month()) return d1.month()<d2.month() ? -1 : 1;
		if(d1.day()!=d2.day()) return d1.day()<d2.day() ? -1 : 1;
		return 0;
	}

	public static int compareTimeRecord(TimeRecord t1, TimeRecord t2) {
		if(t1.hour()!=t2.hour()) return Integer.compare(t1.hour(),t2.hour());
		if(t1.minute()!=t2.minute()) return Integer.compare(t1.minute(),t2.minute());
		if(t1.second()!=t2.second()) return Integer.compare(t1.second(),t2.second());
		if(t1.millisecond()!=t2.millisecond()) return Integer.compare(t1.millisecond(),t2.millisecond());
		if(t1.microsecond()!=t2.microsecond()) return Integer.compare(t1.microsecond(),t2.microsecond());
		return Integer.compare(t1.nanosecond(),t2.nanosecond());
	}

	public static int compareISODateTime(IsoDateTime dt1, IsoDateTime dt2) {
		int c = compareISODate(dt1.date(),dt2.date());
		if(c!=0) {
			return c;
		}
		return compareTimeRecord(dt1.time(),dt2.time());
	}

	public static double constrainToRange(double value, double min, double max) {
		return Math.min(max,Math.max(min,value));
	}

	public static void rejectToRange(double value, double min, double max) {
		if(value<min || value>max) {
			throw rangeError("value out of range: "+fmt(min)+" <= "+fmt(value)+" <= "+fmt(max));
		}
	}

	private static String fmt(double d) {
		return d==(long)d ? Long.toString((long)d) : Double.toString(d);
	}

	private static long clampYear(double year) {
		// Anything this far out is rejected by the range checks
		if(year>1e12) return (long)1e12;
		if(year<-1e12) return (long)-1e12;
		return (long)year;
	}

	public static void rejectISODate(double year, double month, double day) {
		rejectToRange(month,1,12);
		rejectToRange(day,1,isoDaysInMonth(clampYear(year),(int)month));
	}

	public static IsoDate regulateISODate(double year, double month, double day, String overflow) {
		if("reject".equals(overflow)) {
			rejectISODate(year,month,day);
		} else {
			month = constrainToRange(month,1,12);
			day = constrainToRange(day,1,isoDaysInMonth(clampYear(year),(int)month));
		}
		return new IsoDate(clampYear(year),(int)month,(int)day);
	}

	public static void rejectTime(double hour, double minute, double second, double millisecond, double microsecond, double nanosecond) {
		rejectToRange(hour,0,23);
		rejectToRange(minute,0,59);
		rejectToRange(second,0,59);
		rejectToRange(millisecond,0,999);
		rejectToRange(microsecond,0,999);
		rejectToRange(nanosecond,0,999);
	}

	public static TimeRecord regulateTime(double hour, double minute, double second, double millisecond, double microsecond, double nanosecond, String overflow) {
		if("reject".equals(overflow)) {
			rejectTime(hour,minute,second,millisecond,microsecond,nanosecond);
		} else {
			hour = constrainToRange(hour,0,23);
			minute = constrainToRange(minute,0,59);
			second = constrainToRange(second,0,59);
			millisecond = constrainToRange(millisecond,0,999);
			microsecond = constrainToRange(microsecond,0,999);
			nanosecond = constrainToRange(nanosecond,0,999);
		}
		return new TimeRecord((int)hour,(int)minute,(int)second,(int)millisecond,(int)microsecond,(int)nanosecond);
	}

	public static TimeRecord balanceTime(long hour, long minute, long second, long millisecond, long microsecond, long nanosecond) {
		microsecond += Math.floorDiv(nanosecond,1000);
		nanosecond = Math.floorMod(nanosecond,1000);
		millisecond += Math.floorDiv(microsecond,1000);
		microsecond = Math.floorMod(microsecond,1000);
		second += Math.floorDiv(millisecond,1000);
		millisecond = Math.floorMod(millisecond,1000);
		minute += Math.floorDiv(second,60);
		second = Math.floorMod(second,60);
		hour += Math.floorDiv(minute,60);
		minute = Math.floorMod(minute,60);
		long deltaDays = Math.floorDiv(hour,24);
		hour = Math.floorMod(hour,24);
		return new TimeRecord(deltaDays,(int)hour,(int)minute,(int)second,(int)millisecond,(int)microsecond,(int)nanosecond);
	}

	public static IsoDateTime balanceISODateTime(long year, int month, int day, long hour, long minute, long second, long millisecond, long microsecond, long nanosecond) {
		TimeRecord time = balanceTime(hour,minute,second,millisecond,microsecond,nanosecond);
		IsoDate date = addDaysToISODate(new IsoDate(year,month,day),time.deltaDays());
		return new IsoDateTime(date,time.withoutDays());
	}

	// AddTime: returns a time record carrying deltaDays
	public static TimeRecord addTime(TimeRecord t, TimeDuration d) {
		BigInteger[] qr = d.getTotalNs().divideAndRemainder(TimeDuration.NS_PER_SECOND);
		BigInteger[] mq = qr[0].divideAndRemainder(BigInteger.valueOf(60));
		return balanceTime(t.hour(),t.minute()+mq[0].longValueExact(),t.second()+mq[1].longValue(),t.millisecond(),t.microsecond(),t.nanosecond()+qr[1].longValue());
	}

	public static TimeDuration differenceTime(TimeRecord t1, TimeRecord t2) {
		return TimeDuration.fromComponents(t2.hour()-t1.hour(),t2.minute()-t1.minute(),t2.second()-t1.second(),
				t2.millisecond()-t1.millisecond(),t2.microsecond()-t1.microsecond(),t2.nanosecond()-t1.nanosecond());
	}

	public static int isoDayOfWeek(IsoDate d) {
		return (int)Math.floorMod(epochDays(d)+3,7)+1;
	}

	public static int isoDayOfYear(IsoDate d) {
		return (int)(epochDays(d)-epochDays(d.year(),1,1)+1);
	}

	private static int isoWeeksInYear(long y) {
		long p = Math.floorMod(y+Math.floorDiv(y,4)-Math.floorDiv(y,100)+Math.floorDiv(y,400),7);
		long y1 = y-1;
		long p1 = Math.floorMod(y1+Math.floorDiv(y1,4)-Math.floorDiv(y1,100)+Math.floorDiv(y1,400),7);
		return (p==4 || p1==3) ? 53 : 52;
	}

	// {week, year}
	public static long[] isoWeekOfYear(IsoDate d) {
		int dow = isoDayOfWeek(d);
		int doy = isoDayOfYear(d);
		long year = d.year();
		long week = Math.floorDiv(doy-dow+10,7);
		if(week<1) {
			year -= 1;
			week = isoWeeksInYear(year);
		} else if(week>isoWeeksInYear(year)) {
			year += 1;
			week = 1;
		}
		return new long[] {week,year};
	}

	public static IsoDate calendarDateAdd(String calendar, IsoDate date, DateDuration duration, String overflow) {
		long[] ym = balanceISOYearMonth(date.year()+duration.years(),date.month()+duration.months());
		IsoDate intermediate = regulateISODate(ym[0],ym[1],date.day(),overflow);
		IsoDate result = addDaysToISODate(intermediate,Math.addExact(duration.days(),Math.multiplyExact(7,duration.weeks())));
		rejectDateRange(result);
		return result;
	}

	private static boolean compareSurpasses(int sign, long year, long month, long day, IsoDate target) {
		if(year!=target.year()) {
			return sign*Long.signum(year-target.year())>0;
		}
		if(month!=target.month()) {
			return sign*Long.signum(month-target.month())>0;
		}
		if(day!=target.day()) {
			return sign*Long.signum(day-target.day())>0;
		}
		return false;
	}

	private static boolean isoDateSurpasses(int sign, IsoDate base, IsoDate date2, long years, long months) {
		long y0 = base.year()+years;
		if(compareSurpasses(sign,y0,base.month(),base.day(),date2)) {
			return true;
		}
		if(months==0) {
			return false;
		}
		long[] ym = balanceISOYearMonth(y0,base.month()+months);
		return compareSurpasses(sign,ym[0],ym[1],base.day(),date2);
	}

	public static DateDuration calendarDateUntil(String calendar, IsoDate one, IsoDate two, TemporalUnit largestUnit) {
		int sign = -compareISODate(one,two);
		if(sign==0) {
			return DateDuration.ZERO;
		}
		long years = 0;
		long months = 0;
		if(largestUnit==TemporalUnit.YEAR || largestUnit==TemporalUnit.MONTH) {
			long candidateYears = two.year()-one.year();
			if(candidateYears!=0) {
				candidateYears -= sign;
			}
			while(!isoDateSurpasses(sign,one,two,candidateYears,0)) {
				years = candidateYears;
				candidateYears += sign;
			}
			long candidateMonths = sign;
			while(!isoDateSurpasses(sign,one,two,years,candidateMonths)) {
				months = candidateMonths;
				candidateMonths += sign;
			}
			if(largestUnit==TemporalUnit.MONTH) {
				months += years*12;
				years = 0;
			}
		}
		long[] ym = balanceISOYearMonth(one.year()+years,one.month()+months);
		IsoDate constrained = regulateISODate(ym[0],ym[1],one.day(),"constrain");
		long weeks = 0;
		long days = epochDays(two)-epochDays(constrained);
		if(largestUnit==TemporalUnit.WEEK) {
			weeks = days/7;
			days %= 7;
		}
		return new DateDuration(years,months,weeks,days);
	}

	//
	// Ranges
	//

	public static BigInteger getUTCEpochNanoseconds(IsoDate date, TimeRecord time) {
		long days = epochDays(date);
		long secs = time.hour()*3600L+time.minute()*60L+time.second();
		BigInteger s = BigInteger.valueOf(days).multiply(BigInteger.valueOf(86400)).add(BigInteger.valueOf(secs));
		return s.multiply(TimeDuration.NS_PER_SECOND).add(BigInteger.valueOf(time.subSecondNanoseconds()));
	}
	public static BigInteger getUTCEpochNanoseconds(IsoDateTime dt) {
		return getUTCEpochNanoseconds(dt.date(),dt.time());
	}

	private static boolean dateOutOfRange(IsoDate date) {
		return date.year()<YEAR_MIN-1 || date.year()>YEAR_MAX+1;
	}

	public static void rejectDateRange(IsoDate date) {
		if(dateOutOfRange(date)) {
			throw rangeError("date is outside of supported range");
		}
		BigInteger ns = getUTCEpochNanoseconds(date,TimeRecord.NOON);
		if(ns.compareTo(DATETIME_NS_MIN)<0 || ns.compareTo(DATETIME_NS_MAX)>0) {
			throw rangeError(isoDateToString(date)+" is outside of supported range");
		}
	}

	public static boolean isoDateTimeWithinLimits(IsoDateTime dt) {
		if(dateOutOfRange(dt.date())) {
			return false;
		}
		BigInteger ns = getUTCEpochNanoseconds(dt);
		return ns.compareTo(DATETIME_NS_MIN)>=0 && ns.compareTo(DATETIME_NS_MAX)<=0;
	}

	public static void rejectDateTimeRange(IsoDateTime dt) {
		if(!isoDateTimeWithinLimits(dt)) {
			throw rangeError("date/time is outside of supported range");
		}
	}

	public static void rejectYearMonthRange(IsoDate date) {
		rejectToRange(date.year(),YEAR_MIN,YEAR_MAX);
		if(date.year()==YEAR_MIN) {
			rejectToRange(date.month(),4,12);
		} else if(date.year()==YEAR_MAX) {
			rejectToRange(date.month(),1,9);
		}
	}

	public static boolean isValidEpochNanoseconds(BigInteger ns) {
		return ns.compareTo(NS_MIN)>=0 && ns.compareTo(NS_MAX)<=0;
	}

	public static void validateEpochNanoseconds(BigInteger ns) {
		if(!isValidEpochNanoseconds(ns)) {
			throw rangeError("date/time value is outside of supported range");
		}
	}

	public static void checkISODaysRange(IsoDate date) {
		if(dateOutOfRange(date) || Math.abs(epochDays(date))>100_000_000L) {
			throw rangeError("date/time value is outside the supported range");
		}
	}

	// GetISOPartsFromEpoch
	public static IsoDateTime isoDateTimeFromEpochNs(BigInteger ns) {
		BigInteger[] qr = ns.divideAndRemainder(NS_PER_DAY);
		long days = qr[0].longValue();
		long nsOfDay = qr[1].longValue();
		if(nsOfDay<0) {
			nsOfDay += DAY_NANOS;
			days -= 1;
		}
		IsoDate date = fromEpochDays(days);
		int nanosecond = (int)(nsOfDay%1000);
		int microsecond = (int)(nsOfDay/1000%1000);
		int millisecond = (int)(nsOfDay/1_000_000%1000);
		long secs = nsOfDay/1_000_000_000L;
		return new IsoDateTime(date,new TimeRecord((int)(secs/3600),(int)(secs/60%60),(int)(secs%60),millisecond,microsecond,nanosecond));
	}

	//
	// Formatting
	//

	private static String pad(long n, int len) {
		String s = Long.toString(n);
		if(s.length()>=len) {
			return s;
		}
		StringBuilder b = new StringBuilder(len);
		for(int i=s.length(); i<len; i++) {
			b.append('0');
		}
		return b.append(s).toString();
	}

	public static String isoYearString(long year) {
		if(year<0 || year>9999) {
			return (year<0 ? "-" : "+")+pad(Math.abs(year),6);
		}
		return pad(year,4);
	}

	public static String twoDigits(long n) {
		return pad(n,2);
	}

	public static String formatFractionalSeconds(long subSecondNanoseconds, int precision) {
		if(precision==PRECISION_AUTO) {
			if(subSecondNanoseconds==0) {
				return "";
			}
			String f = pad(subSecondNanoseconds,9);
			int end = f.length();
			while(end>0 && f.charAt(end-1)=='0') {
				end--;
			}
			return "."+f.substring(0,end);
		}
		if(precision==0) {
			return "";
		}
		return "."+pad(subSecondNanoseconds,9).substring(0,precision);
	}

	public static String formatTimeString(long hour, long minute, long second, long subSecondNanoseconds, int precision) {
		String result = twoDigits(hour)+":"+twoDigits(minute);
		if(precision==PRECISION_MINUTE) {
			return result;
		}
		return result+":"+twoDigits(second)+formatFractionalSeconds(subSecondNanoseconds,precision);
	}

	public static String timeRecordToString(TimeRecord t, int precision) {
		return formatTimeString(t.hour(),t.minute(),t.second(),t.subSecondNanoseconds(),precision);
	}

	public static String isoDateToString(IsoDate d) {
		return isoYearString(d.year())+"-"+twoDigits(d.month())+"-"+twoDigits(d.day());
	}

	public static String formatCalendarAnnotation(String id, String showCalendar) {
		if("never".equals(showCalendar)) {
			return "";
		}
		if("auto".equals(showCalendar) && ISO8601.equals(id)) {
			return "";
		}
		String flag = "critical".equals(showCalendar) ? "!" : "";
		return "["+flag+"u-ca="+id+"]";
	}

	public static String isoDateTimeToString(IsoDateTime dt, String calendar, int precision, String showCalendar) {
		return isoDateToString(dt.date())+"T"+timeRecordToString(dt.time(),precision)+formatCalendarAnnotation(calendar,showCalendar);
	}

	public static String temporalDateToString(IsoDate date, String calendar, String showCalendar) {
		return isoDateToString(date)+formatCalendarAnnotation(calendar,showCalendar);
	}

	public static String temporalMonthDayToString(IsoDate date, String calendar, String showCalendar) {
		String result = twoDigits(date.month())+"-"+twoDigits(date.day());
		if("always".equals(showCalendar) || "critical".equals(showCalendar) || !ISO8601.equals(calendar)) {
			result = isoYearString(date.year())+"-"+result;
		}
		return result+formatCalendarAnnotation(calendar,showCalendar);
	}

	public static String temporalYearMonthToString(IsoDate date, String calendar, String showCalendar) {
		String result = isoYearString(date.year())+"-"+twoDigits(date.month());
		if("always".equals(showCalendar) || "critical".equals(showCalendar) || !ISO8601.equals(calendar)) {
			result += "-"+twoDigits(date.day());
		}
		return result+formatCalendarAnnotation(calendar,showCalendar);
	}

	public static String formatUTCOffsetNanoseconds(long offsetNs) {
		String sign = offsetNs<0 ? "-" : "+";
		long abs = Math.abs(offsetNs);
		long hour = abs/3_600_000_000_000L;
		long minute = abs/60_000_000_000L%60;
		long second = abs/1_000_000_000L%60;
		long subSecond = abs%1_000_000_000L;
		int precision = second==0 && subSecond==0 ? PRECISION_MINUTE : PRECISION_AUTO;
		return sign+formatTimeString(hour,minute,second,subSecond,precision);
	}

	public static String formatOffsetTimeZoneIdentifier(long offsetMinutes) {
		String sign = offsetMinutes<0 ? "-" : "+";
		long abs = Math.abs(offsetMinutes);
		return sign+formatTimeString(abs/60,abs%60,0,0,PRECISION_MINUTE);
	}

	public static String formatDateTimeUTCOffsetRounded(long offsetNs) {
		long rounded = TemporalMath.roundLongToIncrement(offsetNs,60_000_000_000L,RoundingMode.HALF_EXPAND);
		return formatOffsetTimeZoneIdentifier(rounded/60_000_000_000L);
	}

	// Duration field values as decimal strings (never in exponent form)
	private static String formatAsDecimalNumber(double d) {
		if(d<=9007199254740991.0) {
			return Long.toString((long)d);
		}
		return new BigDecimal(d).toBigInteger().toString();
	}

	public static String temporalDurationToString(double[] f, int precision) {
		int sign = durationSign(f);
		StringBuilder datePart = new StringBuilder();
		if(f[0]!=0) datePart.append(formatAsDecimalNumber(Math.abs(f[0]))).append('Y');
		if(f[1]!=0) datePart.append(formatAsDecimalNumber(Math.abs(f[1]))).append('M');
		if(f[2]!=0) datePart.append(formatAsDecimalNumber(Math.abs(f[2]))).append('W');
		if(f[3]!=0) datePart.append(formatAsDecimalNumber(Math.abs(f[3]))).append('D');
		StringBuilder timePart = new StringBuilder();
		if(f[4]!=0) timePart.append(formatAsDecimalNumber(Math.abs(f[4]))).append('H');
		if(f[5]!=0) timePart.append(formatAsDecimalNumber(Math.abs(f[5]))).append('M');
		TimeDuration secondsDuration = TimeDuration.fromComponents(0,0,f[6],f[7],f[8],f[9]);
		TemporalUnit largest = defaultTemporalLargestUnit(f);
		if(!secondsDuration.isZero() || largest.ordinal()>=TemporalUnit.SECOND.ordinal() || precision!=PRECISION_AUTO) {
			TimeDuration abs = secondsDuration.abs();
			timePart.append(abs.sec().toString()).append(formatFractionalSeconds(abs.subsec(),precision)).append('S');
		}
		StringBuilder result = new StringBuilder();
		if(sign<0) {
			result.append('-');
		}
		result.append('P').append(datePart);
		if(timePart.length()>0) {
			result.append('T').append(timePart);
		}
		return result.toString();
	}

	//
	// Parsing (RFC 9557 / ISO 8601 strings), adapted from regex.mjs: Java
	// can't reuse a group name, so alternatives get numbered names.
	//

	private static final String TZ_COMPONENT = "[A-Za-z._][A-Za-z._0-9+-]*";
	private static final String OFFSET_ID_NOCAPTURE = "(?:[+-](?:[01][0-9]|2[0-3])(?::?[0-5][0-9])?)";
	private static final String TIMEZONE_ID = "(?:"+OFFSET_ID_NOCAPTURE+"|(?:"+TZ_COMPONENT+")(?:/(?:"+TZ_COMPONENT+"))*)";
	private static final String YEARPART = "(?:[+-]\\d{6}|\\d{4})";
	private static final String MONTHPART = "(?:0[1-9]|1[0-2])";
	private static final String DAYPART = "(?:0[1-9]|[12]\\d|3[01])";
	private static final String DATESPLIT = "(?<yearpart>"+YEARPART+")(?:-(?<monthpart1>"+MONTHPART+")-(?<daypart1>"+DAYPART+")|(?<monthpart2>"+MONTHPART+")(?<daypart2>"+DAYPART+"))";
	private static String secondsPart(String n) {
		return "(?:(?<second"+n+">\\d{2}))(?:(?:[.,](?<fraction"+n+">\\d{1,9})))?";
	}
	private static final String TIMESPLIT = "(?:(?:"
			+ "(?<hour1>\\d{2})(?::(?<minute1>\\d{2}))?(?::"+secondsPart("1")+")?"
			+ ")|(?:"
			+ "(?<hour2>\\d{2})(?:(?<minute2>\\d{2}))?(?:"+secondsPart("2")+")?"
			+ "))";
	private static final String OFFSET_HOUR = "(?:[01][0-9]|2[0-3])";
	private static final String OFFSET_MINSEC_NOCAPTURE = "(?:(?::[0-5][0-9])(?::[0-5][0-9](?:[.,]\\d{1,9})?)?)|(?:[0-5][0-9](?:[0-5][0-9](?:[.,]\\d{1,9})?)?)";
	private static final String OFFSET = "(?<offset>[+-]"+OFFSET_HOUR+"(?:"+OFFSET_MINSEC_NOCAPTURE+")?)";
	private static final String OFFSETPART = "(?<z>[zZ])|"+OFFSET+"?";
	private static final String ANNOTATION_NOCAPTURE = "\\[!?[a-z_][a-z0-9_-]*=[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*\\]";
	private static final java.util.regex.Pattern ANNOTATION = java.util.regex.Pattern.compile("\\[(!)?([a-z_][a-z0-9_-]*)=([A-Za-z0-9]+(?:-[A-Za-z0-9]+)*)\\]");

	private static final java.util.regex.Pattern ZONEDDATETIME = java.util.regex.Pattern.compile(
			"^"+DATESPLIT
			+ "(?:[tT ]"+TIMESPLIT+"(?:"+OFFSETPART+")?)?"
			+ "(?:\\[!?(?<timeZoneID>"+TIMEZONE_ID+")\\])?"
			+ "(?<annotation>(?:"+ANNOTATION_NOCAPTURE+")*)$");
	private static final java.util.regex.Pattern TIME = java.util.regex.Pattern.compile(
			"^[tT]?"+TIMESPLIT
			+ "(?:"+OFFSETPART+")?"
			+ "(?:\\[!?(?<timeZoneID>"+TIMEZONE_ID+")\\])?"
			+ "(?<annotation>(?:"+ANNOTATION_NOCAPTURE+")*)$");
	private static final java.util.regex.Pattern YEARMONTH = java.util.regex.Pattern.compile(
			"^(?<year>"+YEARPART+")-?(?<month>"+MONTHPART+")(?:\\[!?"+TIMEZONE_ID+"\\])?(?<annotation>(?:"+ANNOTATION_NOCAPTURE+")*)$");
	private static final java.util.regex.Pattern MONTHDAY = java.util.regex.Pattern.compile(
			"^(?:--)?(?<month>"+MONTHPART+")-?(?<day>"+DAYPART+")(?:\\[!?"+TIMEZONE_ID+"\\])?(?<annotation>(?:"+ANNOTATION_NOCAPTURE+")*)$");
	private static final String NUMBER_FRACTION = "(\\d+)(?:[.,](\\d{1,9}))?";
	private static final java.util.regex.Pattern DURATION = java.util.regex.Pattern.compile(
			"^([+-])?P(?:(\\d+)Y)?(?:(\\d+)M)?(?:(\\d+)W)?(?:(\\d+)D)?(?:T(?!$)(?:"+NUMBER_FRACTION+"H)?(?:"+NUMBER_FRACTION+"M)?(?:"+NUMBER_FRACTION+"S)?)?$",
			java.util.regex.Pattern.CASE_INSENSITIVE);
	private static final java.util.regex.Pattern TIMEZONE_IDENTIFIER = java.util.regex.Pattern.compile("^"+TIMEZONE_ID+"$",java.util.regex.Pattern.CASE_INSENSITIVE);
	private static final java.util.regex.Pattern OFFSET_IDENTIFIER = java.util.regex.Pattern.compile("^([+-])([01][0-9]|2[0-3])(?::?([0-5][0-9])?)?$");
	private static final java.util.regex.Pattern OFFSET_ONLY = java.util.regex.Pattern.compile("^[+-]"+OFFSET_HOUR+"(?:"+OFFSET_MINSEC_NOCAPTURE+")?$");
	private static final java.util.regex.Pattern OFFSET_WITH_PARTS = java.util.regex.Pattern.compile(
			"^(?<offsetSign>[+-])(?<offsetHour>"+OFFSET_HOUR+")(?:"
			+ "(?:(?::(?<offsetMinute1>[0-5][0-9]))(?::(?<offsetSecond1>[0-5][0-9])(?:[.,](?<offsetSubseconds1>\\d{1,9}))?)?)"
			+ "|(?:(?<offsetMinute2>[0-5][0-9])(?:(?<offsetSecond2>[0-5][0-9])(?:[.,](?<offsetSubseconds2>\\d{1,9}))?)?)"
			+ ")?$");

	private static String group2(java.util.regex.Matcher m, String name) {
		String s = m.group(name+"1");
		return s!=null ? s : m.group(name+"2");
	}

	public record ParsedDateTime(long year, int month, int day, TimeRecord time, String tzAnnotation, String offset, boolean z, String calendar) {
	}

	private static String processAnnotations(String annotations) {
		String calendar = null;
		boolean calendarWasCritical = false;
		java.util.regex.Matcher m = ANNOTATION.matcher(annotations);
		while(m.find()) {
			boolean critical = m.group(1)!=null;
			String key = m.group(2);
			String value = m.group(3);
			if(key.equals("u-ca")) {
				if(calendar==null) {
					calendar = value;
					calendarWasCritical = critical;
				} else if(critical || calendarWasCritical) {
					throw rangeError("Invalid annotations in "+annotations+": more than one u-ca present with critical flag");
				}
			} else if(critical) {
				throw rangeError("Unrecognized annotation: !"+key+"="+value);
			}
		}
		return calendar;
	}

	private static int fractionDigits(String fraction, int from, int to) {
		String f = (fraction==null ? "" : fraction)+"000000000";
		return Integer.parseInt(f.substring(from,to));
	}

	public static ParsedDateTime parseISODateTime(String isoString) {
		java.util.regex.Matcher m = ZONEDDATETIME.matcher(isoString);
		if(!m.matches()) {
			throw rangeError("invalid RFC 9557 string: "+isoString);
		}
		String calendar = processAnnotations(m.group("annotation"));
		String yearString = m.group("yearpart");
		if(yearString.equals("-000000")) {
			throw rangeError("invalid RFC 9557 string: "+isoString);
		}
		long year = Long.parseLong(yearString.startsWith("+") ? yearString.substring(1) : yearString);
		int month = Integer.parseInt(group2(m,"monthpart"));
		int day = Integer.parseInt(group2(m,"daypart"));
		String hourS = group2(m,"hour");
		String minuteS = group2(m,"minute");
		String secondS = group2(m,"second");
		String fraction = group2(m,"fraction");
		boolean hasTime = hourS!=null;
		if(fraction!=null && (secondS==null || (minuteS==null && hasTime))) {
			throw rangeError("invalid RFC 9557 string: "+isoString+", only seconds may be fractional");
		}
		int hour = hourS!=null ? Integer.parseInt(hourS) : 0;
		int minute = minuteS!=null ? Integer.parseInt(minuteS) : 0;
		int second = secondS!=null ? Integer.parseInt(secondS) : 0;
		if(second==60) {
			second = 59;
		}
		int millisecond = fractionDigits(fraction,0,3);
		int microsecond = fractionDigits(fraction,3,6);
		int nanosecond = fractionDigits(fraction,6,9);
		String offset = null;
		boolean z = false;
		if(m.group("z")!=null) {
			z = true;
		} else if(m.group("offset")!=null) {
			offset = m.group("offset");
		}
		String tzAnnotation = m.group("timeZoneID");
		rejectISODate(year,month,day);
		rejectTime(hour,minute,second,millisecond,microsecond,nanosecond);
		return new ParsedDateTime(year,month,day,hasTime ? new TimeRecord(hour,minute,second,millisecond,microsecond,nanosecond) : null,tzAnnotation,offset,z,calendar);
	}

	public static ParsedDateTime parseTemporalInstantString(String isoString) {
		ParsedDateTime result = parseISODateTime(isoString);
		if(!result.z() && result.offset()==null) {
			throw rangeError("Temporal.Instant requires a time zone offset");
		}
		return result;
	}

	public static ParsedDateTime parseTemporalZonedDateTimeString(String isoString) {
		ParsedDateTime result = parseISODateTime(isoString);
		if(result.tzAnnotation()==null) {
			throw rangeError("Temporal.ZonedDateTime requires a time zone ID in brackets");
		}
		return result;
	}

	public record ParsedTime(TimeRecord time, String calendar) {
	}

	private static final java.util.regex.Pattern HAS_TIME_DESIGNATOR = java.util.regex.Pattern.compile("[tT ][0-9][0-9]");

	public static ParsedTime parseTemporalTimeString(String isoString) {
		java.util.regex.Matcher m = TIME.matcher(isoString);
		TimeRecord time;
		String calendar;
		if(m.matches()) {
			calendar = processAnnotations(m.group("annotation"));
			String hourS = group2(m,"hour");
			String minuteS = group2(m,"minute");
			String secondS = group2(m,"second");
			String fraction = group2(m,"fraction");
			if(fraction!=null && (secondS==null || (minuteS==null && hourS!=null))) {
				throw rangeError("invalid RFC 9557 string: "+isoString+", only seconds may be fractional");
			}
			int hour = Integer.parseInt(hourS);
			int minute = minuteS!=null ? Integer.parseInt(minuteS) : 0;
			int second = secondS!=null ? Integer.parseInt(secondS) : 0;
			if(second==60) {
				second = 59;
			}
			if(m.group("z")!=null) {
				throw rangeError("Z designator not supported for PlainTime");
			}
			rejectTime(hour,minute,second,0,0,0);
			time = new TimeRecord(hour,minute,second,fractionDigits(fraction,0,3),fractionDigits(fraction,3,6),fractionDigits(fraction,6,9));
		} else {
			ParsedDateTime p = parseISODateTime(isoString);
			if(p.time()==null) {
				throw rangeError("time is missing in string: "+isoString);
			}
			if(p.z()) {
				throw rangeError("Z designator not supported for PlainTime");
			}
			time = p.time();
			calendar = p.calendar();
		}
		if(HAS_TIME_DESIGNATOR.matcher(isoString).find()) {
			return new ParsedTime(time,calendar);
		}
		// An ambiguous time-only string that is also a valid month-day or
		// year-month must be rejected
		try {
			ParsedYearMonthDay md = parseTemporalMonthDayString(isoString);
			rejectISODate(1972,md.month(),md.day());
		} catch(JSRuntimeException e) {
			try {
				ParsedYearMonthDay ym = parseTemporalYearMonthString(isoString);
				rejectISODate(ym.year(),ym.month(),1);
			} catch(JSRuntimeException e2) {
				return new ParsedTime(time,calendar);
			}
		}
		throw rangeError("invalid RFC 9557 time-only string "+isoString+"; may need a T prefix");
	}

	// year/month/day, one of them may be a reference value
	public record ParsedYearMonthDay(long year, int month, int day, String calendar, boolean hasYear, boolean hasDay) {
	}

	public static ParsedYearMonthDay parseTemporalYearMonthString(String isoString) {
		java.util.regex.Matcher m = YEARMONTH.matcher(isoString);
		if(m.matches()) {
			String calendar = processAnnotations(m.group("annotation"));
			String yearString = m.group("year");
			if(yearString.equals("-000000")) {
				throw rangeError("invalid RFC 9557 string: "+isoString);
			}
			long year = Long.parseLong(yearString.startsWith("+") ? yearString.substring(1) : yearString);
			int month = Integer.parseInt(m.group("month"));
			if(calendar!=null) {
				calendar = asciiLowercase(calendar);
				if(!calendar.equals(ISO8601)) {
					throw rangeError("YYYY-MM format is only valid with iso8601 calendar");
				}
			}
			return new ParsedYearMonthDay(year,month,1,calendar,true,false);
		}
		ParsedDateTime p = parseISODateTime(isoString);
		if(p.z()) {
			throw rangeError("Z designator not supported for PlainYearMonth");
		}
		return new ParsedYearMonthDay(p.year(),p.month(),p.day(),p.calendar(),true,true);
	}

	public static ParsedYearMonthDay parseTemporalMonthDayString(String isoString) {
		java.util.regex.Matcher m = MONTHDAY.matcher(isoString);
		if(m.matches()) {
			String calendar = processAnnotations(m.group("annotation"));
			int month = Integer.parseInt(m.group("month"));
			int day = Integer.parseInt(m.group("day"));
			if(calendar!=null) {
				calendar = asciiLowercase(calendar);
				if(!calendar.equals(ISO8601)) {
					throw rangeError("MM-DD format is only valid with iso8601 calendar");
				}
			}
			rejectISODate(1972,month,day);
			return new ParsedYearMonthDay(1972,month,day,calendar,false,true);
		}
		ParsedDateTime p = parseISODateTime(isoString);
		if(p.z()) {
			throw rangeError("Z designator not supported for PlainMonthDay");
		}
		return new ParsedYearMonthDay(p.year(),p.month(),p.day(),p.calendar(),true,true);
	}

	public static long parseDateTimeUTCOffset(String string) {
		java.util.regex.Matcher m = OFFSET_WITH_PARTS.matcher(string);
		if(!m.matches()) {
			throw rangeError("invalid time zone offset: "+string+"; must match ±HH:MM[:SS.SSSSSSSSS]");
		}
		long sign = m.group("offsetSign").equals("-") ? -1 : 1;
		long hours = Long.parseLong(m.group("offsetHour"));
		String minuteS = group2(m,"offsetMinute");
		String secondS = group2(m,"offsetSecond");
		String subS = group2(m,"offsetSubseconds");
		long minutes = minuteS!=null ? Long.parseLong(minuteS) : 0;
		long seconds = secondS!=null ? Long.parseLong(secondS) : 0;
		long nanoseconds = fractionDigits(subS,0,9);
		return sign*(((hours*60+minutes)*60+seconds)*1_000_000_000L+nanoseconds);
	}

	// Has the offset string a seconds part (sub-minute precision)?
	public static boolean offsetHasSeconds(String offset) {
		java.util.regex.Matcher m = OFFSET_WITH_PARTS.matcher(offset);
		return m.matches() && group2(m,"offsetSecond")!=null;
	}

	public static boolean isOffsetTimeZoneIdentifier(String s) {
		return OFFSET_IDENTIFIER.matcher(s).matches();
	}

	// ParseTimeZoneIdentifier: {offsetMinutes} or {tzName}
	public record TimeZoneIdentifier(String tzName, Long offsetMinutes) {
	}

	private static RuntimeException badTimeZoneString(String tz) {
		String msg = OFFSET_ONLY.matcher(tz).matches() ? "Seconds not allowed in offset time zone" : "Invalid time zone";
		return rangeError(msg+": "+tz);
	}

	public static TimeZoneIdentifier parseTimeZoneIdentifier(String identifier) {
		if(!TIMEZONE_IDENTIFIER.matcher(identifier).matches()) {
			throw badTimeZoneString(identifier);
		}
		if(OFFSET_IDENTIFIER.matcher(identifier).matches()) {
			long offsetNanoseconds = parseDateTimeUTCOffset(identifier);
			return new TimeZoneIdentifier(null,offsetNanoseconds/60_000_000_000L);
		}
		return new TimeZoneIdentifier(identifier,null);
	}

	public static TimeZoneIdentifier parseTemporalTimeZoneString(String s) {
		if(TIMEZONE_IDENTIFIER.matcher(s).matches()) {
			return parseTimeZoneIdentifier(s);
		}
		ParsedDateTime p = null;
		try {
			p = parseISODateTime(s);
		} catch(JSRuntimeException e) {
			// fall through
		}
		if(p!=null) {
			if(p.tzAnnotation()!=null) {
				return parseTimeZoneIdentifier(p.tzAnnotation());
			}
			if(p.z()) {
				return parseTimeZoneIdentifier("UTC");
			}
			if(p.offset()!=null) {
				return parseTimeZoneIdentifier(p.offset());
			}
		}
		throw badTimeZoneString(s);
	}

	// Duration fields: years, months, weeks, days, hours, minutes, seconds,
	// milliseconds, microseconds, nanoseconds
	public static double[] parseTemporalDurationString(String isoString) {
		java.util.regex.Matcher m = DURATION.matcher(isoString);
		if(!m.matches()) {
			throw rangeError("invalid duration: "+isoString);
		}
		boolean any = false;
		for(int i=2; i<=m.groupCount(); i++) {
			if(m.group(i)!=null) {
				any = true;
				break;
			}
		}
		if(!any) {
			throw rangeError("invalid duration: "+isoString);
		}
		int sign = "-".equals(m.group(1)) ? -1 : 1;
		double years = numberOf(m.group(2))*sign;
		double months = numberOf(m.group(3))*sign;
		double weeks = numberOf(m.group(4))*sign;
		double days = numberOf(m.group(5))*sign;
		double hours = numberOf(m.group(6))*sign;
		String fHours = m.group(7);
		String minutesStr = m.group(8);
		String fMinutes = m.group(9);
		String secondsStr = m.group(10);
		String fSeconds = m.group(11);
		double minutes = 0;
		double seconds = 0;
		long excessNanoseconds = 0;
		if(fHours!=null) {
			if(minutesStr!=null || fMinutes!=null || secondsStr!=null || fSeconds!=null) {
				throw rangeError("only the smallest unit can be fractional");
			}
			excessNanoseconds = fractionDigits(fHours,0,9)*3600L*sign;
		} else {
			minutes = numberOf(minutesStr)*sign;
			if(fMinutes!=null) {
				if(secondsStr!=null || fSeconds!=null) {
					throw rangeError("only the smallest unit can be fractional");
				}
				excessNanoseconds = fractionDigits(fMinutes,0,9)*60L*sign;
			} else {
				seconds = numberOf(secondsStr)*sign;
				if(fSeconds!=null) {
					excessNanoseconds = fractionDigits(fSeconds,0,9)*(long)sign;
				}
			}
		}
		double nanoseconds = excessNanoseconds%1000;
		double microseconds = (excessNanoseconds/1000)%1000;
		double milliseconds = (excessNanoseconds/1_000_000)%1000;
		seconds += (excessNanoseconds/1_000_000_000L)%60;
		minutes += excessNanoseconds/60_000_000_000L;
		double[] f = {years,months,weeks,days,hours,minutes,seconds,milliseconds,microseconds,nanoseconds};
		for(int i=0; i<f.length; i++) {
			if(f[i]==0) {
				f[i] = 0; // no negative zero
			}
		}
		rejectDuration(f);
		return f;
	}

	private static double numberOf(String digits) {
		if(digits==null) {
			return 0;
		}
		return Double.parseDouble(digits);
	}

	//
	// Time zones: UTC, fixed offsets, and the IANA zones of java.time
	//

	private static volatile java.util.Map<String,String> namedZones;

	private static java.util.Map<String,String> namedZones() {
		java.util.Map<String,String> m = namedZones;
		if(m==null) {
			m = new java.util.HashMap<>();
			for(String id: java.time.ZoneId.getAvailableZoneIds()) {
				if(id.startsWith("SystemV/")) {
					continue;
				}
				m.put(asciiLowercase(id),id);
			}
			m.put("utc","UTC");
			namedZones = m;
		}
		return m;
	}

	// GetAvailableNamedTimeZoneIdentifier: the case-normalized identifier, or null
	public static String getAvailableNamedTimeZoneIdentifier(String identifier) {
		return namedZones().get(asciiLowercase(identifier));
	}

	private static final java.util.Map<String,java.time.zone.ZoneRules> RULES = new java.util.concurrent.ConcurrentHashMap<>();

	private static java.time.zone.ZoneRules rules(String tz) {
		java.time.zone.ZoneRules r = RULES.get(tz);
		if(r==null) {
			r = tz.equals("UTC") ? java.time.ZoneOffset.UTC.getRules() : java.time.ZoneId.of(tz).getRules();
			RULES.put(tz,r);
		}
		return r;
	}

	public static String toTemporalTimeZoneIdentifier(Object timeZoneLike) {
		if(timeZoneLike instanceof TemporalZonedDateTimeObject zdt) {
			return zdt.getTimeZone();
		}
		String s = requireString(timeZoneLike);
		if(s.equals("UTC")) {
			return "UTC";
		}
		TimeZoneIdentifier id = parseTemporalTimeZoneString(s);
		if(id.offsetMinutes()!=null) {
			return formatOffsetTimeZoneIdentifier(id.offsetMinutes());
		}
		String identifier = getAvailableNamedTimeZoneIdentifier(id.tzName());
		if(identifier==null) {
			throw rangeError("Unrecognized time zone "+id.tzName());
		}
		return identifier;
	}

	private static Long offsetMinutesOf(String timeZone) {
		if(!timeZone.isEmpty() && (timeZone.charAt(0)=='+' || timeZone.charAt(0)=='-')) {
			return parseDateTimeUTCOffset(timeZone)/60_000_000_000L;
		}
		return null;
	}

	private static String primaryIdentifier(String tz) {
		String lower = asciiLowercase(tz);
		switch(lower) {
			case "etc/utc": case "etc/gmt": case "gmt": case "etc/universal": case "universal": case "etc/zulu": case "zulu":
			case "etc/uct": case "uct": case "etc/greenwich": case "greenwich": case "etc/gmt0": case "etc/gmt+0": case "etc/gmt-0":
			case "gmt0": case "gmt+0": case "gmt-0":
				return "UTC";
			default:
				return tz;
		}
	}

	public static boolean timeZoneEquals(String one, String two) {
		if(one.equals(two)) {
			return true;
		}
		Long o1 = offsetMinutesOf(one);
		Long o2 = offsetMinutesOf(two);
		if(o1==null && o2==null) {
			String p1 = primaryIdentifier(one);
			String p2 = primaryIdentifier(two);
			if(p1.equals(p2)) {
				return true;
			}
			if(p1.equals("UTC") || p2.equals("UTC")) {
				return false;
			}
			return rules(one).equals(rules(two));
		}
		return false;
	}

	private static java.time.Instant toInstant(BigInteger epochNs) {
		BigInteger[] qr = epochNs.divideAndRemainder(TimeDuration.NS_PER_SECOND);
		long secs = qr[0].longValue();
		long nanos = qr[1].longValue();
		if(nanos<0) {
			nanos += 1_000_000_000L;
			secs -= 1;
		}
		return java.time.Instant.ofEpochSecond(secs,nanos);
	}

	private static BigInteger fromInstant(java.time.Instant i) {
		return BigInteger.valueOf(i.getEpochSecond()).multiply(TimeDuration.NS_PER_SECOND).add(BigInteger.valueOf(i.getNano()));
	}

	public static long getOffsetNanosecondsFor(String timeZone, BigInteger epochNs) {
		Long offsetMinutes = offsetMinutesOf(timeZone);
		if(offsetMinutes!=null) {
			return offsetMinutes*60_000_000_000L;
		}
		if(timeZone.equals("UTC")) {
			return 0;
		}
		return rules(timeZone).getOffset(toInstant(epochNs)).getTotalSeconds()*1_000_000_000L;
	}

	public static IsoDateTime getISODateTimeFor(String timeZone, BigInteger epochNs) {
		long offsetNs = getOffsetNanosecondsFor(timeZone,epochNs);
		return isoDateTimeFromEpochNs(epochNs.add(BigInteger.valueOf(offsetNs)));
	}

	public static List<BigInteger> getPossibleEpochNanoseconds(String timeZone, IsoDateTime dt) {
		Long offsetMinutes = offsetMinutesOf(timeZone);
		if(timeZone.equals("UTC") || offsetMinutes!=null) {
			long off = offsetMinutes!=null ? offsetMinutes : 0;
			IsoDateTime balanced = off==0 ? dt : balanceISODateTime(dt.date().year(),dt.date().month(),dt.date().day(),dt.time().hour(),dt.time().minute()-off,dt.time().second(),dt.time().millisecond(),dt.time().microsecond(),dt.time().nanosecond());
			checkISODaysRange(balanced.date());
			BigInteger epochNs = getUTCEpochNanoseconds(balanced);
			validateEpochNanoseconds(epochNs);
			return List.of(epochNs);
		}
		checkISODaysRange(dt.date());
		java.time.LocalDateTime ldt = java.time.LocalDateTime.of((int)dt.date().year(),dt.date().month(),dt.date().day(),dt.time().hour(),dt.time().minute(),dt.time().second(),(int)dt.time().subSecondNanoseconds());
		BigInteger utc = getUTCEpochNanoseconds(dt);
		List<BigInteger> result = new ArrayList<>(2);
		for(java.time.ZoneOffset o: rules(timeZone).getValidOffsets(ldt)) {
			BigInteger ns = utc.subtract(BigInteger.valueOf(o.getTotalSeconds()*1_000_000_000L));
			validateEpochNanoseconds(ns);
			result.add(ns);
		}
		result.sort(null);
		return result;
	}

	public static BigInteger getEpochNanosecondsFor(String timeZone, IsoDateTime dt, String disambiguation) {
		return disambiguatePossibleEpochNanoseconds(getPossibleEpochNanoseconds(timeZone,dt),timeZone,dt,disambiguation);
	}

	public static BigInteger disambiguatePossibleEpochNanoseconds(List<BigInteger> possible, String timeZone, IsoDateTime dt, String disambiguation) {
		int n = possible.size();
		if(n==1) {
			return possible.get(0);
		}
		if(n>0) {
			switch(disambiguation) {
				case "compatible":
				case "earlier":
					return possible.get(0);
				case "later":
					return possible.get(n-1);
				default:
					throw rangeError("multiple instants found");
			}
		}
		if(disambiguation.equals("reject")) {
			throw rangeError("no such instant found");
		}
		BigInteger utcns = getUTCEpochNanoseconds(dt);
		BigInteger dayBefore = utcns.subtract(NS_PER_DAY);
		validateEpochNanoseconds(dayBefore);
		long offsetBefore = getOffsetNanosecondsFor(timeZone,dayBefore);
		BigInteger dayAfter = utcns.add(NS_PER_DAY);
		validateEpochNanoseconds(dayAfter);
		long offsetAfter = getOffsetNanosecondsFor(timeZone,dayAfter);
		long nanoseconds = offsetAfter-offsetBefore;
		if(disambiguation.equals("earlier")) {
			TimeRecord earlierTime = addTime(dt.time(),TimeDuration.of(BigInteger.valueOf(-nanoseconds)));
			IsoDate earlierDate = addDaysToISODate(dt.date(),earlierTime.deltaDays());
			return getPossibleEpochNanoseconds(timeZone,new IsoDateTime(earlierDate,earlierTime.withoutDays())).get(0);
		}
		TimeRecord laterTime = addTime(dt.time(),TimeDuration.of(BigInteger.valueOf(nanoseconds)));
		IsoDate laterDate = addDaysToISODate(dt.date(),laterTime.deltaDays());
		List<BigInteger> p = getPossibleEpochNanoseconds(timeZone,new IsoDateTime(laterDate,laterTime.withoutDays()));
		return p.get(p.size()-1);
	}

	public static BigInteger getStartOfDay(String timeZone, IsoDate date) {
		IsoDateTime dt = new IsoDateTime(date,TimeRecord.MIDNIGHT);
		List<BigInteger> possible = getPossibleEpochNanoseconds(timeZone,dt);
		if(!possible.isEmpty()) {
			return possible.get(0);
		}
		BigInteger dayBefore = getUTCEpochNanoseconds(dt).subtract(NS_PER_DAY);
		validateEpochNanoseconds(dayBefore);
		return getNamedTimeZoneNextTransition(timeZone,dayBefore);
	}

	public static BigInteger getNamedTimeZoneNextTransition(String timeZone, BigInteger epochNs) {
		if(timeZone.equals("UTC") || offsetMinutesOf(timeZone)!=null) {
			return null;
		}
		java.time.zone.ZoneOffsetTransition t = rules(timeZone).nextTransition(toInstant(epochNs));
		if(t==null) {
			return null;
		}
		BigInteger ns = fromInstant(t.getInstant());
		return isValidEpochNanoseconds(ns) ? ns : null;
	}

	public static BigInteger getNamedTimeZonePreviousTransition(String timeZone, BigInteger epochNs) {
		if(timeZone.equals("UTC") || offsetMinutesOf(timeZone)!=null) {
			return null;
		}
		java.time.zone.ZoneOffsetTransition t = rules(timeZone).previousTransition(toInstant(epochNs));
		if(t==null) {
			return null;
		}
		BigInteger ns = fromInstant(t.getInstant());
		return isValidEpochNanoseconds(ns) ? ns : null;
	}

	public static String defaultTimeZone() {
		java.time.ZoneId z = java.time.ZoneId.systemDefault();
		if(z instanceof java.time.ZoneOffset o) {
			return formatOffsetTimeZoneIdentifier(o.getTotalSeconds()/60);
		}
		String id = getAvailableNamedTimeZoneIdentifier(z.getId());
		return id!=null ? id : "UTC";
	}

	//
	// Durations
	//

	public static final String[] DURATION_FIELDS_ALPHA = {"days","hours","microseconds","milliseconds","minutes","months","nanoseconds","seconds","weeks","years"};
	// Index of each alphabetical field in the fields array
	private static final int[] DURATION_ALPHA_INDEX = {3,4,8,7,5,1,9,6,2,0};

	public static int durationSign(double[] f) {
		for(double d: f) {
			if(d!=0) {
				return d<0 ? -1 : 1;
			}
		}
		return 0;
	}

	private static final BigInteger TWO_53 = BigInteger.ONE.shiftLeft(53);

	public static boolean isValidDuration(double[] f) {
		int sign = 0;
		for(double d: f) {
			if(Double.isNaN(d) || Double.isInfinite(d)) {
				return false;
			}
			int s = d<0 ? -1 : d>0 ? 1 : 0;
			if(s!=0) {
				if(sign!=0 && s!=sign) {
					return false;
				}
				sign = s;
			}
		}
		if(Math.abs(f[0])>=4294967296.0 || Math.abs(f[1])>=4294967296.0 || Math.abs(f[2])>=4294967296.0) {
			return false;
		}
		// Exact: days*86400 + hours*3600 + ... + ns/1e9 < 2^53
		BigInteger totalNs = TimeDuration.big(f[9])
				.add(TimeDuration.big(f[8]).multiply(BigInteger.valueOf(1000L)))
				.add(TimeDuration.big(f[7]).multiply(BigInteger.valueOf(1_000_000L)))
				.add(TimeDuration.big(f[6]).multiply(TimeDuration.NS_PER_SECOND))
				.add(TimeDuration.big(f[5]).multiply(BigInteger.valueOf(60_000_000_000L)))
				.add(TimeDuration.big(f[4]).multiply(BigInteger.valueOf(3_600_000_000_000L)))
				.add(TimeDuration.big(f[3]).multiply(NS_PER_DAY));
		BigInteger secs = totalNs.abs().divide(TimeDuration.NS_PER_SECOND);
		return secs.compareTo(TWO_53)<0;
	}

	public static void rejectDuration(double[] f) {
		for(double d: f) {
			if(Double.isNaN(d) || Double.isInfinite(d)) {
				throw rangeError("infinite values not allowed as duration fields");
			}
		}
		int sign = 0;
		for(double d: f) {
			int s = d<0 ? -1 : d>0 ? 1 : 0;
			if(s!=0) {
				if(sign!=0 && s!=sign) {
					throw rangeError("mixed-sign values not allowed as duration fields");
				}
				sign = s;
			}
		}
		if(!isValidDuration(f)) {
			throw rangeError("duration out of range");
		}
	}

	public static TemporalUnit defaultTemporalLargestUnit(double[] f) {
		for(int i=0; i<9; i++) {
			if(f[i]!=0) {
				return TemporalUnit.values()[i];
			}
		}
		return TemporalUnit.NANOSECOND;
	}

	public static TemporalDurationObject createTemporalDuration(double[] f) {
		rejectDuration(f);
		double[] c = new double[10];
		for(int i=0; i<10; i++) {
			c[i] = f[i]==0 ? 0 : f[i];
		}
		return new TemporalDurationObject(env(),c);
	}

	public static TemporalDurationObject createNegatedTemporalDuration(TemporalDurationObject d) {
		double[] f = d.getFields();
		double[] n = new double[10];
		for(int i=0; i<10; i++) {
			n[i] = f[i]==0 ? 0 : -f[i];
		}
		return createTemporalDuration(n);
	}

	public static InternalDuration toInternalDurationRecord(double[] f) {
		DateDuration date = new DateDuration((long)f[0],(long)f[1],(long)f[2],(long)f[3]);
		TimeDuration time = TimeDuration.fromComponents(f[4],f[5],f[6],f[7],f[8],f[9]);
		return new InternalDuration(date,time);
	}

	public static InternalDuration toInternalDurationRecordWith24HourDays(double[] f) {
		TimeDuration time = TimeDuration.fromComponents(f[4],f[5],f[6],f[7],f[8],f[9]).add24HourDays(f[3]);
		return new InternalDuration(new DateDuration((long)f[0],(long)f[1],(long)f[2],0),time);
	}

	public static DateDuration toDateDurationRecordWithoutTime(double[] f) {
		InternalDuration d = toInternalDurationRecordWith24HourDays(f);
		long days = d.time().getTotalNs().divide(NS_PER_DAY).longValueExact();
		rejectDuration(new double[] {d.date().years(),d.date().months(),d.date().weeks(),days,0,0,0,0,0,0});
		return d.date().withDays(days);
	}

	public static DateDuration adjustDateDurationRecord(DateDuration d, long days, Long weeks, Long months) {
		long m = months!=null ? months : d.months();
		long w = weeks!=null ? weeks : d.weeks();
		rejectDuration(new double[] {d.years(),m,w,days,0,0,0,0,0,0});
		return new DateDuration(d.years(),m,w,days);
	}

	public static InternalDuration combineDateAndTimeDuration(DateDuration date, TimeDuration time) {
		return new InternalDuration(date,time);
	}

	public static TemporalDurationObject temporalDurationFromInternal(InternalDuration d, TemporalUnit largestUnit) {
		int sign = d.time().sign();
		BigInteger abs = d.time().getTotalNs().abs();
		BigInteger thousand = BigInteger.valueOf(1000);
		double days = 0, hours = 0, minutes = 0, seconds = 0, milliseconds = 0, microseconds = 0, nanoseconds = 0;
		switch(largestUnit) {
			case YEAR: case MONTH: case WEEK: case DAY: {
				BigInteger[] qr = abs.divideAndRemainder(NS_PER_DAY);
				days = qr[0].doubleValue();
				long rest = qr[1].longValue();
				nanoseconds = rest%1000; rest /= 1000;
				microseconds = rest%1000; rest /= 1000;
				milliseconds = rest%1000; rest /= 1000;
				seconds = rest%60; rest /= 60;
				minutes = rest%60; rest /= 60;
				hours = rest;
				break;
			}
			case HOUR: {
				BigInteger[] qr = abs.divideAndRemainder(BigInteger.valueOf(3_600_000_000_000L));
				hours = qr[0].doubleValue();
				long rest = qr[1].longValue();
				nanoseconds = rest%1000; rest /= 1000;
				microseconds = rest%1000; rest /= 1000;
				milliseconds = rest%1000; rest /= 1000;
				seconds = rest%60; rest /= 60;
				minutes = rest;
				break;
			}
			case MINUTE: {
				BigInteger[] qr = abs.divideAndRemainder(BigInteger.valueOf(60_000_000_000L));
				minutes = qr[0].doubleValue();
				long rest = qr[1].longValue();
				nanoseconds = rest%1000; rest /= 1000;
				microseconds = rest%1000; rest /= 1000;
				milliseconds = rest%1000; rest /= 1000;
				seconds = rest;
				break;
			}
			case SECOND: {
				BigInteger[] qr = abs.divideAndRemainder(TimeDuration.NS_PER_SECOND);
				seconds = qr[0].doubleValue();
				long rest = qr[1].longValue();
				nanoseconds = rest%1000; rest /= 1000;
				microseconds = rest%1000; rest /= 1000;
				milliseconds = rest;
				break;
			}
			case MILLISECOND: {
				BigInteger[] qr = abs.divideAndRemainder(BigInteger.valueOf(1_000_000L));
				milliseconds = qr[0].doubleValue();
				long rest = qr[1].longValue();
				nanoseconds = rest%1000; rest /= 1000;
				microseconds = rest;
				break;
			}
			case MICROSECOND: {
				BigInteger[] qr = abs.divideAndRemainder(thousand);
				microseconds = qr[0].doubleValue();
				nanoseconds = qr[1].longValue();
				break;
			}
			default:
				nanoseconds = abs.doubleValue();
		}
		DateDuration date = d.date();
		return createTemporalDuration(new double[] {date.years(),date.months(),date.weeks(),date.days()+sign*days,
				sign*hours,sign*minutes,sign*seconds,sign*milliseconds,sign*microseconds,sign*nanoseconds});
	}

	// ToTemporalPartialDurationRecord: undefined entries are null
	public static Double[] toTemporalPartialDurationRecord(Object like) {
		if(!isObj(like)) {
			throw typeError("invalid duration-like");
		}
		Double[] result = new Double[10];
		boolean any = false;
		for(int i=0; i<DURATION_FIELDS_ALPHA.length; i++) {
			Object value = getProp(like,DURATION_FIELDS_ALPHA[i]);
			if(!isUndefined(value)) {
				any = true;
				result[DURATION_ALPHA_INDEX[i]] = toIntegerIfIntegral(value);
			}
		}
		if(!any) {
			throw typeError("invalid duration-like");
		}
		return result;
	}

	public static TemporalDurationObject toTemporalDuration(Object item) {
		if(item instanceof TemporalDurationObject d) {
			return createTemporalDuration(d.getFields());
		}
		if(!isObj(item)) {
			return createTemporalDuration(parseTemporalDurationString(requireString(item)));
		}
		Double[] partial = toTemporalPartialDurationRecord(item);
		double[] f = new double[10];
		for(int i=0; i<10; i++) {
			f[i] = partial[i]!=null ? partial[i] : 0;
		}
		return createTemporalDuration(f);
	}

	public static TimeDuration roundTimeDuration(TimeDuration d, long increment, TemporalUnit unit, RoundingMode mode) {
		return d.round(unit.bigNsPerUnit.multiply(BigInteger.valueOf(increment)),mode);
	}

	public static double totalTimeDuration(TimeDuration d, TemporalUnit unit) {
		return d.fdiv(unit.bigNsPerUnit);
	}

	public static long dateDurationDays(DateDuration d, TemporalPlainDateObject plainRelativeTo) {
		DateDuration ymw = adjustDateDurationRecord(d,0,null,null);
		if(ymw.sign()==0) {
			return d.days();
		}
		IsoDate date = plainRelativeTo.getIsoDate();
		IsoDate later = calendarDateAdd(plainRelativeTo.getCalendar(),date,ymw,"constrain");
		return d.days()+(epochDays(later)-epochDays(date));
	}

	//
	// Calendars (ISO 8601 only) and calendar fields
	//

	public static String canonicalizeCalendar(String id) {
		String lower = asciiLowercase(id);
		if(!lower.equals(ISO8601)) {
			throw rangeError("invalid calendar identifier "+id);
		}
		return lower;
	}

	public static String toTemporalCalendarIdentifier(Object calendarLike) {
		if(calendarLike instanceof TemporalCalendarHolder h) {
			return h.getCalendar();
		}
		String identifier = requireString(calendarLike);
		if(asciiLowercase(identifier).equals(ISO8601)) {
			return ISO8601;
		}
		String calendar = null;
		boolean parsed = false;
		try {
			calendar = parseISODateTime(identifier).calendar();
			parsed = true;
		} catch(JSRuntimeException e) {
			// try the other formats
		}
		if(!parsed) {
			try {
				calendar = parseTemporalTimeString(identifier).calendar();
				parsed = true;
			} catch(JSRuntimeException e) {
				// try the other formats
			}
		}
		if(!parsed) {
			try {
				calendar = parseTemporalYearMonthString(identifier).calendar();
				parsed = true;
			} catch(JSRuntimeException e) {
				// last chance
			}
		}
		if(!parsed) {
			calendar = parseTemporalMonthDayString(identifier).calendar();
		}
		if(calendar==null) {
			return ISO8601;
		}
		return canonicalizeCalendar(calendar);
	}

	public static String getTemporalCalendarIdentifierWithISODefault(Object item) {
		if(item instanceof TemporalCalendarHolder h) {
			return h.getCalendar();
		}
		Object calendar = getProp(item,"calendar");
		if(isUndefined(calendar)) {
			return ISO8601;
		}
		return toTemporalCalendarIdentifier(calendar);
	}

	// Calendar fields record; unset fields are null
	public static final class Fields {
		public Double year;
		public Double month;
		public String monthCode;
		public Double day;
		public Double hour;
		public Double minute;
		public Double second;
		public Double millisecond;
		public Double microsecond;
		public Double nanosecond;
		public String offset;
		public String timeZone;

		Object getValue(String key) {
			switch(key) {
				case "year": return year;
				case "month": return month;
				case "monthCode": return monthCode;
				case "day": return day;
				case "hour": return hour;
				case "minute": return minute;
				case "second": return second;
				case "millisecond": return millisecond;
				case "microsecond": return microsecond;
				case "nanosecond": return nanosecond;
				case "offset": return offset;
				case "timeZone": return timeZone;
				default: return null;
			}
		}

		void setValue(String key, Object v) {
			switch(key) {
				case "year": year = (Double)v; break;
				case "month": month = (Double)v; break;
				case "monthCode": monthCode = (String)v; break;
				case "day": day = (Double)v; break;
				case "hour": hour = (Double)v; break;
				case "minute": minute = (Double)v; break;
				case "second": second = (Double)v; break;
				case "millisecond": millisecond = (Double)v; break;
				case "microsecond": microsecond = (Double)v; break;
				case "nanosecond": nanosecond = (Double)v; break;
				case "offset": offset = (String)v; break;
				case "timeZone": timeZone = (String)v; break;
				default:
			}
		}
	}

	public static final String[] DATE_FIELDS = {"year","month","monthCode","day"};
	public static final String[] YEAR_MONTH_FIELDS = {"year","month","monthCode"};
	public static final String[] TIME_FIELDS = {"hour","minute","second","millisecond","microsecond","nanosecond"};
	public static final String[] TIME_ZONE_FIELDS = {"hour","minute","second","millisecond","microsecond","nanosecond","offset","timeZone"};
	public static final String[] NONE = {};
	// Order of CalendarMergeFields/CalendarFieldKeysPresent
	private static final String[] CALENDAR_FIELD_KEYS = {"year","month","monthCode","day","hour","minute","second","millisecond","microsecond","nanosecond","offset","timeZone"};

	private static boolean isTimeField(String key) {
		switch(key) {
			case "hour": case "minute": case "second": case "millisecond": case "microsecond": case "nanosecond":
				return true;
			default:
				return false;
		}
	}

	// ParseMonthCode, returning the month number (leap months are rejected
	// later, by the ISO calendar)
	public static String toMonthCode(Object value) {
		Object prim = RuntimeUtil.toPrimitive(env(),value,RuntimeUtil.HINT.STRING);
		if(!isStr(prim)) {
			throw typeError("monthCode must be a string");
		}
		String s = prim.toString();
		int len = s.length();
		if((len!=3 && len!=4) || s.charAt(0)!='M' || !isDigit(s.charAt(1)) || !isDigit(s.charAt(2)) || (len==4 && s.charAt(3)!='L')) {
			throw rangeError("invalid monthCode "+s);
		}
		if(s.charAt(1)=='0' && s.charAt(2)=='0' && len!=4) {
			throw rangeError("invalid monthCode "+s);
		}
		return s;
	}

	private static boolean isDigit(char c) {
		return c>='0' && c<='9';
	}

	private static String toOffsetString(Object value) {
		Object prim = RuntimeUtil.toPrimitive(env(),value,RuntimeUtil.HINT.STRING);
		String s = requireString(prim);
		parseDateTimeUTCOffset(s);
		return s;
	}

	private static Object castField(String key, Object value) {
		switch(key) {
			case "year": return toIntegerWithTruncation(value);
			case "month": return toPositiveIntegerWithTruncation(value,"month");
			case "monthCode": return toMonthCode(value);
			case "day": return toPositiveIntegerWithTruncation(value,"day");
			case "offset": return toOffsetString(value);
			case "timeZone": return toTemporalTimeZoneIdentifier(value);
			default: return toIntegerWithTruncation(value);
		}
	}

	// requiredFields null means "partial"
	public static Fields prepareCalendarFields(String calendar, Object bag, String[] calendarFieldNames, String[] nonCalendarFieldNames, String[] requiredFields) {
		List<String> names = new ArrayList<>(Arrays.asList(calendarFieldNames));
		names.addAll(Arrays.asList(nonCalendarFieldNames));
		java.util.Collections.sort(names);
		Fields result = new Fields();
		boolean any = false;
		for(String property: names) {
			Object value = getProp(bag,property);
			if(!isUndefined(value)) {
				any = true;
				result.setValue(property,castField(property,value));
			} else if(requiredFields!=null) {
				if(Arrays.asList(requiredFields).contains(property)) {
					throw typeError("required property '"+property+"' missing or undefined");
				}
				if(isTimeField(property)) {
					result.setValue(property,0.0);
				}
			}
		}
		if(requiredFields==null && !any) {
			throw typeError("no supported properties found");
		}
		return result;
	}

	public static Fields isoDateToFields(String calendar, IsoDate date, String type) {
		Fields f = new Fields();
		f.monthCode = createMonthCode(date.month());
		if(type.equals("month-day") || type.equals("date")) {
			f.day = (double)date.day();
		}
		if(type.equals("year-month") || type.equals("date")) {
			f.year = (double)date.year();
		}
		return f;
	}

	public static String createMonthCode(int month) {
		return month<10 ? "M0"+month : "M"+month;
	}

	public static Fields calendarMergeFields(String calendar, Fields fields, Fields additional) {
		Fields merged = new Fields();
		boolean additionalHasMonth = additional.month!=null;
		boolean additionalHasMonthCode = additional.monthCode!=null;
		for(String key: CALENDAR_FIELD_KEYS) {
			Object v = null;
			Object fv = fields.getValue(key);
			boolean ignored = additional.getValue(key)!=null
					|| (key.equals("monthCode") && additionalHasMonth)
					|| (key.equals("month") && additionalHasMonthCode);
			if(fv!=null && !ignored) {
				v = fv;
			}
			Object av = additional.getValue(key);
			if(av!=null) {
				v = av;
			}
			if(v!=null) {
				merged.setValue(key,v);
			}
		}
		return merged;
	}

	// CalendarResolveFields for the ISO calendar
	public static void calendarResolveFields(String calendar, Fields fields, String type) {
		if((type.equals("date") || type.equals("year-month")) && fields.year==null) {
			throw typeError("year is required");
		}
		if((type.equals("date") || type.equals("month-day")) && fields.day==null) {
			throw typeError("day is required");
		}
		if(fields.monthCode==null) {
			if(fields.month==null) {
				throw typeError("Either month or monthCode are required");
			}
			return;
		}
		String mc = fields.monthCode;
		int monthNumber = Integer.parseInt(mc.substring(1,3));
		if(mc.length()==4 || monthNumber<1 || monthNumber>12) {
			throw rangeError("Invalid monthCode: "+mc+" does not exist in calendar "+calendar);
		}
		if(fields.month!=null && fields.month!=monthNumber) {
			throw rangeError("monthCode "+mc+" and month "+fmt(fields.month)+" must match if both are present");
		}
		fields.month = (double)monthNumber;
	}

	public static IsoDate calendarDateFromFields(String calendar, Fields fields, String overflow) {
		calendarResolveFields(calendar,fields,"date");
		IsoDate result = regulateISODate(fields.year,fields.month,fields.day,overflow);
		rejectDateRange(result);
		return result;
	}

	public static IsoDate calendarYearMonthFromFields(String calendar, Fields fields, String overflow) {
		calendarResolveFields(calendar,fields,"year-month");
		IsoDate result = regulateISODate(fields.year,fields.month,1,overflow);
		rejectYearMonthRange(result);
		return result;
	}

	public static IsoDate calendarMonthDayFromFields(String calendar, Fields fields, String overflow) {
		calendarResolveFields(calendar,fields,"month-day");
		double year = fields.year!=null ? fields.year : 1972;
		IsoDate r = regulateISODate(year,fields.month,fields.day,overflow);
		IsoDate result = new IsoDate(1972,r.month(),r.day());
		rejectDateRange(result);
		return result;
	}

	// InterpretTemporalDateTimeFields
	public static IsoDateTime interpretTemporalDateTimeFields(String calendar, Fields fields, String overflow) {
		IsoDate date = calendarDateFromFields(calendar,fields,overflow);
		TimeRecord time = regulateTime(fields.hour,fields.minute,fields.second,fields.millisecond,fields.microsecond,fields.nanosecond,overflow);
		return new IsoDateTime(date,time);
	}

	// ToTemporalTimeRecord: partial leaves unset fields null
	public static Double[] toTemporalTimeRecord(Object bag, boolean partial) {
		String[] names = {"hour","microsecond","millisecond","minute","nanosecond","second"};
		int[] index = {0,4,3,1,5,2};
		Double[] result = new Double[6];
		boolean any = false;
		for(int i=0; i<names.length; i++) {
			Object value = getProp(bag,names[i]);
			if(!isUndefined(value)) {
				result[index[i]] = toIntegerWithTruncation(value);
				any = true;
			} else if(!partial) {
				result[index[i]] = 0.0;
			}
		}
		if(!any) {
			throw typeError("invalid time-like");
		}
		return result;
	}

	public static void rejectTemporalLikeObject(Object item) {
		if(item instanceof TemporalCalendarHolder || item instanceof TemporalZonedDateTimeObject) {
			throw typeError("with() does not support a calendar or timeZone property");
		}
		if(item instanceof TemporalPlainTimeObject) {
			throw typeError("with() does not accept Temporal.PlainTime, use withPlainTime() instead");
		}
		if(!isUndefined(getProp(item,"calendar"))) {
			throw typeError("with() does not support a calendar property");
		}
		if(!isUndefined(getProp(item,"timeZone"))) {
			throw typeError("with() does not support a timeZone property");
		}
	}

	//
	// Creation and conversion
	//

	public static TemporalPlainDateObject createTemporalDate(IsoDate date, String calendar) {
		rejectDateRange(date);
		return new TemporalPlainDateObject(env(),date,calendar);
	}

	public static TemporalPlainDateTimeObject createTemporalDateTime(IsoDateTime dt, String calendar) {
		rejectDateTimeRange(dt);
		return new TemporalPlainDateTimeObject(env(),new IsoDateTime(dt.date(),dt.time().withoutDays()),calendar);
	}

	public static TemporalPlainTimeObject createTemporalTime(TimeRecord time) {
		return new TemporalPlainTimeObject(env(),time.withoutDays());
	}

	public static TemporalPlainYearMonthObject createTemporalYearMonth(IsoDate date, String calendar) {
		rejectYearMonthRange(date);
		return new TemporalPlainYearMonthObject(env(),date,calendar);
	}

	public static TemporalPlainMonthDayObject createTemporalMonthDay(IsoDate date, String calendar) {
		rejectDateRange(date);
		return new TemporalPlainMonthDayObject(env(),date,calendar);
	}

	public static TemporalZonedDateTimeObject createTemporalZonedDateTime(BigInteger epochNs, String timeZone, String calendar) {
		validateEpochNanoseconds(epochNs);
		return new TemporalZonedDateTimeObject(env(),epochNs,timeZone,calendar);
	}

	public static TemporalInstantObject createTemporalInstant(BigInteger epochNs) {
		validateEpochNanoseconds(epochNs);
		return new TemporalInstantObject(env(),epochNs);
	}

	public static TemporalPlainDateObject toTemporalDate(Object item, Object options) {
		if(isObj(item)) {
			if(item instanceof TemporalPlainDateObject d) {
				getTemporalOverflowOption(getOptionsObject(options));
				return createTemporalDate(d.getIsoDate(),d.getCalendar());
			}
			if(item instanceof TemporalZonedDateTimeObject z) {
				IsoDateTime dt = getISODateTimeFor(z.getTimeZone(),z.getEpochNs());
				getTemporalOverflowOption(getOptionsObject(options));
				return createTemporalDate(dt.date(),z.getCalendar());
			}
			if(item instanceof TemporalPlainDateTimeObject dt) {
				getTemporalOverflowOption(getOptionsObject(options));
				return createTemporalDate(dt.getIsoDateTime().date(),dt.getCalendar());
			}
			String calendar = getTemporalCalendarIdentifierWithISODefault(item);
			Fields fields = prepareCalendarFields(calendar,item,DATE_FIELDS,NONE,NONE);
			String overflow = getTemporalOverflowOption(getOptionsObject(options));
			IsoDate date = calendarDateFromFields(calendar,fields,overflow);
			return createTemporalDate(date,calendar);
		}
		ParsedDateTime p = parseISODateTime(requireString(item));
		if(p.z()) {
			throw rangeError("Z designator not supported for PlainDate");
		}
		String calendar = p.calendar()!=null ? canonicalizeCalendar(p.calendar()) : ISO8601;
		getTemporalOverflowOption(getOptionsObject(options));
		return createTemporalDate(new IsoDate(p.year(),p.month(),p.day()),calendar);
	}

	public static TemporalPlainDateTimeObject toTemporalDateTime(Object item, Object options) {
		if(isObj(item)) {
			if(item instanceof TemporalPlainDateTimeObject dt) {
				getTemporalOverflowOption(getOptionsObject(options));
				return createTemporalDateTime(dt.getIsoDateTime(),dt.getCalendar());
			}
			if(item instanceof TemporalZonedDateTimeObject z) {
				IsoDateTime dt = getISODateTimeFor(z.getTimeZone(),z.getEpochNs());
				getTemporalOverflowOption(getOptionsObject(options));
				return createTemporalDateTime(dt,z.getCalendar());
			}
			if(item instanceof TemporalPlainDateObject d) {
				getTemporalOverflowOption(getOptionsObject(options));
				return createTemporalDateTime(new IsoDateTime(d.getIsoDate(),TimeRecord.MIDNIGHT),d.getCalendar());
			}
			String calendar = getTemporalCalendarIdentifierWithISODefault(item);
			Fields fields = prepareCalendarFields(calendar,item,DATE_FIELDS,TIME_FIELDS,NONE);
			String overflow = getTemporalOverflowOption(getOptionsObject(options));
			IsoDateTime dt = interpretTemporalDateTimeFields(calendar,fields,overflow);
			return createTemporalDateTime(dt,calendar);
		}
		ParsedDateTime p = parseISODateTime(requireString(item));
		if(p.z()) {
			throw rangeError("Z designator not supported for PlainDateTime");
		}
		TimeRecord time = p.time()!=null ? p.time() : TimeRecord.MIDNIGHT;
		String calendar = p.calendar()!=null ? canonicalizeCalendar(p.calendar()) : ISO8601;
		getTemporalOverflowOption(getOptionsObject(options));
		return createTemporalDateTime(new IsoDateTime(new IsoDate(p.year(),p.month(),p.day()),time),calendar);
	}

	public static TemporalInstantObject toTemporalInstant(Object item) {
		if(isObj(item)) {
			if(item instanceof TemporalInstantObject i) {
				return createTemporalInstant(i.getEpochNs());
			}
			if(item instanceof TemporalZonedDateTimeObject z) {
				return createTemporalInstant(z.getEpochNs());
			}
			item = RuntimeUtil.toPrimitive(env(),item,RuntimeUtil.HINT.STRING);
		}
		ParsedDateTime p = parseTemporalInstantString(requireString(item));
		TimeRecord t = p.time();
		long offsetNs = p.z() ? 0 : parseDateTimeUTCOffset(p.offset());
		IsoDateTime balanced = balanceISODateTime(p.year(),p.month(),p.day(),t.hour(),t.minute(),t.second(),t.millisecond(),t.microsecond(),t.nanosecond()-offsetNs);
		checkISODaysRange(balanced.date());
		BigInteger epochNs = getUTCEpochNanoseconds(balanced);
		return createTemporalInstant(epochNs);
	}

	public static TemporalPlainTimeObject toTemporalTime(Object item, Object options) {
		TimeRecord time;
		if(isObj(item)) {
			if(item instanceof TemporalPlainTimeObject t) {
				getTemporalOverflowOption(getOptionsObject(options));
				return createTemporalTime(t.getTime());
			}
			if(item instanceof TemporalPlainDateTimeObject dt) {
				getTemporalOverflowOption(getOptionsObject(options));
				return createTemporalTime(dt.getIsoDateTime().time());
			}
			if(item instanceof TemporalZonedDateTimeObject z) {
				IsoDateTime dt = getISODateTimeFor(z.getTimeZone(),z.getEpochNs());
				getTemporalOverflowOption(getOptionsObject(options));
				return createTemporalTime(dt.time());
			}
			Double[] r = toTemporalTimeRecord(item,false);
			String overflow = getTemporalOverflowOption(getOptionsObject(options));
			time = regulateTime(r[0],r[1],r[2],r[3],r[4],r[5],overflow);
		} else {
			time = parseTemporalTimeString(requireString(item)).time();
			getTemporalOverflowOption(getOptionsObject(options));
		}
		return createTemporalTime(time);
	}

	public static TimeRecord toTimeRecordOrMidnight(Object item) {
		if(isUndefined(item)) {
			return TimeRecord.MIDNIGHT;
		}
		return toTemporalTime(item,RuntimeUtil.UNDEFINED).getTime();
	}

	public static TemporalPlainYearMonthObject toTemporalYearMonth(Object item, Object options) {
		if(isObj(item)) {
			if(item instanceof TemporalPlainYearMonthObject ym) {
				getTemporalOverflowOption(getOptionsObject(options));
				return createTemporalYearMonth(ym.getIsoDate(),ym.getCalendar());
			}
			String calendar = getTemporalCalendarIdentifierWithISODefault(item);
			Fields fields = prepareCalendarFields(calendar,item,YEAR_MONTH_FIELDS,NONE,NONE);
			String overflow = getTemporalOverflowOption(getOptionsObject(options));
			IsoDate date = calendarYearMonthFromFields(calendar,fields,overflow);
			return createTemporalYearMonth(date,calendar);
		}
		ParsedYearMonthDay p = parseTemporalYearMonthString(requireString(item));
		String calendar = p.calendar()!=null ? canonicalizeCalendar(p.calendar()) : ISO8601;
		getTemporalOverflowOption(getOptionsObject(options));
		IsoDate date = new IsoDate(p.year(),p.month(),p.day());
		rejectYearMonthRange(date);
		return createTemporalYearMonth(new IsoDate(p.year(),p.month(),1),calendar);
	}

	public static TemporalPlainMonthDayObject toTemporalMonthDay(Object item, Object options) {
		if(isObj(item)) {
			if(item instanceof TemporalPlainMonthDayObject md) {
				getTemporalOverflowOption(getOptionsObject(options));
				return createTemporalMonthDay(md.getIsoDate(),md.getCalendar());
			}
			String calendar;
			if(item instanceof TemporalCalendarHolder h) {
				calendar = h.getCalendar();
			} else {
				Object c = getProp(item,"calendar");
				calendar = isUndefined(c) ? ISO8601 : toTemporalCalendarIdentifier(c);
			}
			Fields fields = prepareCalendarFields(calendar,item,DATE_FIELDS,NONE,NONE);
			String overflow = getTemporalOverflowOption(getOptionsObject(options));
			IsoDate date = calendarMonthDayFromFields(calendar,fields,overflow);
			return createTemporalMonthDay(date,calendar);
		}
		ParsedYearMonthDay p = parseTemporalMonthDayString(requireString(item));
		String calendar = p.calendar()!=null ? canonicalizeCalendar(p.calendar()) : ISO8601;
		getTemporalOverflowOption(getOptionsObject(options));
		return createTemporalMonthDay(new IsoDate(1972,p.month(),p.day()),calendar);
	}

	public static BigInteger interpretISODateTimeOffset(IsoDate date, TimeRecord time, String offsetBehaviour, long offsetNs, String timeZone, String disambiguation, String offsetOpt, boolean matchMinute) {
		if(time==null) {
			return getStartOfDay(timeZone,date);
		}
		IsoDateTime dt = new IsoDateTime(date,time);
		if(offsetBehaviour.equals("wall") || offsetOpt.equals("ignore")) {
			return getEpochNanosecondsFor(timeZone,dt,disambiguation);
		}
		if(offsetBehaviour.equals("exact") || offsetOpt.equals("use")) {
			IsoDateTime balanced = balanceISODateTime(date.year(),date.month(),date.day(),time.hour(),time.minute(),time.second(),time.millisecond(),time.microsecond(),time.nanosecond()-offsetNs);
			checkISODaysRange(balanced.date());
			BigInteger epochNs = getUTCEpochNanoseconds(balanced);
			validateEpochNanoseconds(epochNs);
			return epochNs;
		}
		checkISODaysRange(date);
		BigInteger utcEpochNs = getUTCEpochNanoseconds(dt);
		List<BigInteger> possible = getPossibleEpochNanoseconds(timeZone,dt);
		for(BigInteger candidate: possible) {
			long candidateOffset = utcEpochNs.subtract(candidate).longValue();
			long rounded = TemporalMath.roundLongToIncrement(candidateOffset,60_000_000_000L,RoundingMode.HALF_EXPAND);
			if(candidateOffset==offsetNs || (matchMinute && rounded==offsetNs)) {
				return candidate;
			}
		}
		if(offsetOpt.equals("reject")) {
			throw rangeError("Offset "+formatUTCOffsetNanoseconds(offsetNs)+" is invalid for "+isoDateTimeToString(dt,ISO8601,PRECISION_AUTO,"auto")+" in "+timeZone);
		}
		return disambiguatePossibleEpochNanoseconds(possible,timeZone,dt,disambiguation);
	}

	public static TemporalZonedDateTimeObject toTemporalZonedDateTime(Object item, Object options) {
		IsoDate date;
		TimeRecord time;
		String timeZone;
		String offset;
		String calendar;
		boolean matchMinute = false;
		boolean hasUTCDesignator = false;
		String disambiguation;
		String offsetOpt;
		if(isObj(item)) {
			if(item instanceof TemporalZonedDateTimeObject z) {
				Object resolved = getOptionsObject(options);
				getTemporalDisambiguationOption(resolved);
				getTemporalOffsetOption(resolved,"reject");
				getTemporalOverflowOption(resolved);
				return createTemporalZonedDateTime(z.getEpochNs(),z.getTimeZone(),z.getCalendar());
			}
			calendar = getTemporalCalendarIdentifierWithISODefault(item);
			Fields fields = prepareCalendarFields(calendar,item,DATE_FIELDS,TIME_ZONE_FIELDS,new String[] {"timeZone"});
			offset = fields.offset;
			timeZone = fields.timeZone;
			Object resolved = getOptionsObject(options);
			disambiguation = getTemporalDisambiguationOption(resolved);
			offsetOpt = getTemporalOffsetOption(resolved,"reject");
			String overflow = getTemporalOverflowOption(resolved);
			IsoDateTime dt = interpretTemporalDateTimeFields(calendar,fields,overflow);
			date = dt.date();
			time = dt.time();
		} else {
			ParsedDateTime p = parseTemporalZonedDateTimeString(requireString(item));
			timeZone = toTemporalTimeZoneIdentifier(p.tzAnnotation());
			offset = p.offset();
			hasUTCDesignator = p.z();
			calendar = p.calendar()!=null ? canonicalizeCalendar(p.calendar()) : ISO8601;
			matchMinute = true;
			if(offset!=null && offsetHasSeconds(offset)) {
				matchMinute = false;
			}
			Object resolved = getOptionsObject(options);
			disambiguation = getTemporalDisambiguationOption(resolved);
			offsetOpt = getTemporalOffsetOption(resolved,"reject");
			getTemporalOverflowOption(resolved);
			date = new IsoDate(p.year(),p.month(),p.day());
			time = p.time();
		}
		String offsetBehaviour = "option";
		if(hasUTCDesignator) {
			offsetBehaviour = "exact";
		} else if(offset==null) {
			offsetBehaviour = "wall";
		}
		long offsetNs = offsetBehaviour.equals("option") ? parseDateTimeUTCOffset(offset) : 0;
		BigInteger epochNs = interpretISODateTimeOffset(date,time,offsetBehaviour,offsetNs,timeZone,disambiguation,offsetOpt,matchMinute);
		return createTemporalZonedDateTime(epochNs,timeZone,calendar);
	}

	// GetTemporalRelativeToOption: a PlainDate, a ZonedDateTime or null
	public static Object getTemporalRelativeToOption(Object options) {
		Object relativeTo = getOpt(options,"relativeTo");
		if(isUndefined(relativeTo)) {
			return null;
		}
		String offsetBehaviour = "option";
		boolean matchMinutes = false;
		IsoDate date;
		TimeRecord time;
		String calendar;
		String timeZone = null;
		String offset = null;
		if(isObj(relativeTo)) {
			if(relativeTo instanceof TemporalZonedDateTimeObject z) {
				return z;
			}
			if(relativeTo instanceof TemporalPlainDateObject d) {
				return d;
			}
			if(relativeTo instanceof TemporalPlainDateTimeObject dt) {
				return createTemporalDate(dt.getIsoDateTime().date(),dt.getCalendar());
			}
			calendar = getTemporalCalendarIdentifierWithISODefault(relativeTo);
			Fields fields = prepareCalendarFields(calendar,relativeTo,DATE_FIELDS,TIME_ZONE_FIELDS,NONE);
			IsoDateTime dt = interpretTemporalDateTimeFields(calendar,fields,"constrain");
			date = dt.date();
			time = dt.time();
			offset = fields.offset;
			timeZone = fields.timeZone;
			if(offset==null) {
				offsetBehaviour = "wall";
			}
		} else {
			ParsedDateTime p = parseISODateTime(requireString(relativeTo));
			time = p.time();
			if(p.tzAnnotation()!=null) {
				timeZone = toTemporalTimeZoneIdentifier(p.tzAnnotation());
				offset = p.offset();
				if(p.z()) {
					offsetBehaviour = "exact";
				} else if(offset==null) {
					offsetBehaviour = "wall";
				}
				matchMinutes = true;
				if(offset!=null && offsetHasSeconds(offset)) {
					matchMinutes = false;
				}
			} else if(p.z()) {
				throw rangeError("Z designator not supported for PlainDate relativeTo; either remove the Z or add a bracketed time zone");
			}
			calendar = p.calendar()!=null ? canonicalizeCalendar(p.calendar()) : ISO8601;
			date = new IsoDate(p.year(),p.month(),p.day());
		}
		if(timeZone==null) {
			return createTemporalDate(date,calendar);
		}
		long offsetNs = offsetBehaviour.equals("option") ? parseDateTimeUTCOffset(offset) : 0;
		BigInteger epochNs = interpretISODateTimeOffset(date,time,offsetBehaviour,offsetNs,timeZone,"compatible","reject",matchMinutes);
		return createTemporalZonedDateTime(epochNs,timeZone,calendar);
	}

	//
	// Differences and relative rounding
	//

	private static InternalDuration differenceInstant(BigInteger ns1, BigInteger ns2, long increment, TemporalUnit smallestUnit, RoundingMode mode) {
		TimeDuration d = TimeDuration.fromEpochNsDiff(ns2,ns1);
		d = roundTimeDuration(d,increment,smallestUnit,mode);
		return new InternalDuration(DateDuration.ZERO,d);
	}

	private static InternalDuration differenceISODateTime(IsoDateTime dt1, IsoDateTime dt2, String calendar, TemporalUnit largestUnit) {
		TimeDuration timeDuration = differenceTime(dt1.time(),dt2.time());
		int timeSign = timeDuration.sign();
		int dateSign = compareISODate(dt1.date(),dt2.date());
		IsoDate adjustedDate = dt2.date();
		if(dateSign==timeSign) {
			adjustedDate = addDaysToISODate(adjustedDate,timeSign);
			timeDuration = timeDuration.add24HourDays(-timeSign);
		}
		TemporalUnit dateLargestUnit = TemporalUnit.larger(TemporalUnit.DAY,largestUnit);
		DateDuration dateDifference = calendarDateUntil(calendar,dt1.date(),adjustedDate,dateLargestUnit);
		if(largestUnit!=dateLargestUnit) {
			timeDuration = timeDuration.add24HourDays(dateDifference.days());
			dateDifference = dateDifference.withDays(0);
		}
		return new InternalDuration(dateDifference,timeDuration);
	}

	public static InternalDuration differenceZonedDateTime(BigInteger ns1, BigInteger ns2, String timeZone, String calendar, TemporalUnit largestUnit) {
		BigInteger nsDiff = ns2.subtract(ns1);
		if(nsDiff.signum()==0) {
			return InternalDuration.ZERO;
		}
		int sign = nsDiff.signum()<0 ? 1 : -1;
		IsoDateTime start = getISODateTimeFor(timeZone,ns1);
		IsoDateTime end = getISODateTimeFor(timeZone,ns2);
		if(compareISODate(start.date(),end.date())==0) {
			return new InternalDuration(DateDuration.ZERO,TimeDuration.of(nsDiff));
		}
		int dayCorrection = 0;
		int maxDayCorrection = sign==-1 ? 2 : 1;
		TimeDuration timeDuration = differenceTime(start.time(),end.time());
		if(timeDuration.sign()==sign) {
			dayCorrection++;
		}
		IsoDateTime intermediate = null;
		boolean found = false;
		for(; dayCorrection<=maxDayCorrection; dayCorrection++) {
			IsoDate intermediateDate = addDaysToISODate(end.date(),(long)dayCorrection*sign);
			intermediate = new IsoDateTime(intermediateDate,start.time());
			BigInteger intermediateNs = getEpochNanosecondsFor(timeZone,intermediate,"compatible");
			timeDuration = TimeDuration.fromEpochNsDiff(ns2,intermediateNs);
			if(timeDuration.sign()!=sign) {
				found = true;
				break;
			}
		}
		if(!found) {
			throw rangeError("time zone returned inconsistent instants");
		}
		TemporalUnit dateLargestUnit = TemporalUnit.larger(TemporalUnit.DAY,largestUnit);
		DateDuration dateDifference = calendarDateUntil(calendar,start.date(),intermediate.date(),dateLargestUnit);
		return new InternalDuration(dateDifference,timeDuration);
	}

	private record NudgeWindow(long r1, long r2, BigInteger startEpochNs, BigInteger endEpochNs, DateDuration startDuration, DateDuration endDuration) {
	}

	private static BigInteger epochNsFor(String timeZone, IsoDateTime dt) {
		return timeZone!=null ? getEpochNanosecondsFor(timeZone,dt,"compatible") : getUTCEpochNanoseconds(dt);
	}

	private static NudgeWindow computeNudgeWindow(int sign, InternalDuration duration, BigInteger originEpochNs, IsoDateTime dt, String timeZone, String calendar, long increment, TemporalUnit unit, boolean additionalShift) {
		long r1, r2;
		DateDuration startDuration, endDuration;
		DateDuration date = duration.date();
		switch(unit) {
			case YEAR: {
				long years = TemporalMath.roundLongToIncrement(date.years(),increment,RoundingMode.TRUNC);
				r1 = !additionalShift ? years : years+increment*sign;
				r2 = r1+increment*sign;
				startDuration = new DateDuration(r1,0,0,0);
				endDuration = new DateDuration(r2,0,0,0);
				break;
			}
			case MONTH: {
				long months = TemporalMath.roundLongToIncrement(date.months(),increment,RoundingMode.TRUNC);
				r1 = !additionalShift ? months : months+increment*sign;
				r2 = r1+increment*sign;
				startDuration = adjustDateDurationRecord(date,0,0L,r1);
				endDuration = adjustDateDurationRecord(date,0,0L,r2);
				break;
			}
			case WEEK: {
				DateDuration yearsMonths = adjustDateDurationRecord(date,0,0L,null);
				IsoDate weeksStart = calendarDateAdd(calendar,dt.date(),yearsMonths,"constrain");
				IsoDate weeksEnd = addDaysToISODate(weeksStart,date.days());
				DateDuration untilResult = calendarDateUntil(calendar,weeksStart,weeksEnd,TemporalUnit.WEEK);
				long weeks = TemporalMath.roundLongToIncrement(date.weeks()+untilResult.weeks(),increment,RoundingMode.TRUNC);
				r1 = weeks;
				r2 = weeks+increment*sign;
				startDuration = adjustDateDurationRecord(date,0,r1,null);
				endDuration = adjustDateDurationRecord(date,0,r2,null);
				break;
			}
			default: {
				long days = TemporalMath.roundLongToIncrement(date.days(),increment,RoundingMode.TRUNC);
				r1 = days;
				r2 = days+increment*sign;
				startDuration = adjustDateDurationRecord(date,r1,null,null);
				endDuration = adjustDateDurationRecord(date,r2,null,null);
			}
		}
		BigInteger startEpochNs;
		if(startDuration.sign()==0) {
			startEpochNs = originEpochNs;
		} else {
			IsoDate start = calendarDateAdd(calendar,dt.date(),startDuration,"constrain");
			startEpochNs = epochNsFor(timeZone,new IsoDateTime(start,dt.time()));
		}
		IsoDate end = calendarDateAdd(calendar,dt.date(),endDuration,"constrain");
		BigInteger endEpochNs = epochNsFor(timeZone,new IsoDateTime(end,dt.time()));
		return new NudgeWindow(r1,r2,startEpochNs,endEpochNs,startDuration,endDuration);
	}

	private record NudgeResult(InternalDuration duration, BigInteger nudgedEpochNs, boolean didExpandCalendarUnit, double total) {
	}

	private static boolean between(BigInteger lo, BigInteger x, BigInteger hi) {
		return lo.compareTo(x)<=0 && x.compareTo(hi)<=0;
	}

	private static NudgeResult nudgeToCalendarUnit(int sign, InternalDuration duration, BigInteger originEpochNs, BigInteger destEpochNs, IsoDateTime dt, String timeZone, String calendar, long increment, TemporalUnit unit, RoundingMode mode) {
		boolean didExpandCalendarUnit = false;
		NudgeWindow w = computeNudgeWindow(sign,duration,originEpochNs,dt,timeZone,calendar,increment,unit,false);
		if(sign==1) {
			if(!between(w.startEpochNs(),destEpochNs,w.endEpochNs())) {
				w = computeNudgeWindow(sign,duration,originEpochNs,dt,timeZone,calendar,increment,unit,true);
				didExpandCalendarUnit = true;
			}
		} else {
			if(!between(w.endEpochNs(),destEpochNs,w.startEpochNs())) {
				w = computeNudgeWindow(sign,duration,originEpochNs,dt,timeZone,calendar,increment,unit,true);
				didExpandCalendarUnit = true;
			}
		}
		BigInteger numerator = destEpochNs.subtract(w.startEpochNs());
		BigInteger denominator = w.endEpochNs().subtract(w.startEpochNs());
		if(denominator.signum()==0) {
			throw rangeError(unit.singular+" was 0 days long");
		}
		TemporalMath.UnsignedRoundingMode unsigned = mode.unsigned(sign<0);
		int cmp = numerator.add(numerator).abs().subtract(denominator.abs()).signum();
		boolean even = (Math.abs(w.r1())/increment)%2==0;
		long roundedUnit;
		if(numerator.signum()==0) {
			roundedUnit = Math.abs(w.r1());
		} else if(numerator.equals(denominator)) {
			roundedUnit = Math.abs(w.r2());
		} else {
			roundedUnit = TemporalMath.pickUpper(cmp,even,unsigned) ? Math.abs(w.r2()) : Math.abs(w.r1());
		}
		double total = Double.NaN;
		if(increment==1) {
			// r1 + numerator/denominator, exactly
			BigInteger fakeNumerator = denominator.multiply(BigInteger.valueOf(w.r1())).add(numerator.multiply(BigInteger.valueOf(sign)));
			total = new BigDecimal(fakeNumerator).divide(new BigDecimal(denominator),new java.math.MathContext(60)).doubleValue();
		}
		didExpandCalendarUnit |= roundedUnit==Math.abs(w.r2());
		InternalDuration result = new InternalDuration(roundedUnit==Math.abs(w.r2()) ? w.endDuration() : w.startDuration(),TimeDuration.ZERO);
		return new NudgeResult(result,didExpandCalendarUnit ? w.endEpochNs() : w.startEpochNs(),didExpandCalendarUnit,total);
	}

	private static NudgeResult nudgeToZonedTime(int sign, InternalDuration duration, IsoDateTime dt, String timeZone, String calendar, long increment, TemporalUnit unit, RoundingMode mode) {
		IsoDate start = calendarDateAdd(calendar,dt.date(),duration.date(),"constrain");
		IsoDate endDate = addDaysToISODate(start,sign);
		BigInteger startEpochNs = getEpochNanosecondsFor(timeZone,new IsoDateTime(start,dt.time()),"compatible");
		BigInteger endEpochNs = getEpochNanosecondsFor(timeZone,new IsoDateTime(endDate,dt.time()),"compatible");
		TimeDuration daySpan = TimeDuration.fromEpochNsDiff(endEpochNs,startEpochNs);
		if(daySpan.sign()!=sign) {
			throw rangeError("time zone returned inconsistent Instants");
		}
		BigInteger unitIncrement = unit.bigNsPerUnit.multiply(BigInteger.valueOf(increment));
		TimeDuration rounded = duration.time().round(unitIncrement,mode);
		TimeDuration beyondDaySpan = rounded.subtract(daySpan);
		boolean didRoundBeyondDay = beyondDaySpan.sign()!=-sign;
		long dayDelta;
		BigInteger nudgedEpochNs;
		if(didRoundBeyondDay) {
			dayDelta = sign;
			rounded = beyondDaySpan.round(unitIncrement,mode);
			nudgedEpochNs = rounded.addToEpochNs(endEpochNs);
		} else {
			dayDelta = 0;
			nudgedEpochNs = rounded.addToEpochNs(startEpochNs);
		}
		DateDuration dateDuration = adjustDateDurationRecord(duration.date(),duration.date().days()+dayDelta,null,null);
		return new NudgeResult(new InternalDuration(dateDuration,rounded),nudgedEpochNs,didRoundBeyondDay,Double.NaN);
	}

	private static NudgeResult nudgeToDayOrTime(InternalDuration duration, BigInteger destEpochNs, TemporalUnit largestUnit, long increment, TemporalUnit smallestUnit, RoundingMode mode) {
		TimeDuration timeDuration = duration.time().add24HourDays(duration.date().days());
		TimeDuration roundedTime = timeDuration.round(smallestUnit.bigNsPerUnit.multiply(BigInteger.valueOf(increment)),mode);
		TimeDuration diffTime = roundedTime.subtract(timeDuration);
		long wholeDays = timeDuration.divideToLong(NS_PER_DAY);
		long roundedWholeDays = roundedTime.divideToLong(NS_PER_DAY);
		boolean didExpandDays = Long.signum(roundedWholeDays-wholeDays)==timeDuration.sign();
		BigInteger nudgedEpochNs = diffTime.addToEpochNs(destEpochNs);
		long days = 0;
		TimeDuration remainder = roundedTime;
		if(largestUnit.date) {
			days = roundedWholeDays;
			remainder = roundedTime.add(TimeDuration.of(BigInteger.valueOf(-roundedWholeDays).multiply(NS_PER_DAY)));
		}
		DateDuration dateDuration = adjustDateDurationRecord(duration.date(),days,null,null);
		return new NudgeResult(new InternalDuration(dateDuration,remainder),nudgedEpochNs,didExpandDays,Double.NaN);
	}

	private static InternalDuration bubbleRelativeDuration(int sign, InternalDuration duration, BigInteger nudgedEpochNs, IsoDateTime dt, String timeZone, String calendar, TemporalUnit largestUnit, TemporalUnit smallestUnit) {
		if(smallestUnit==largestUnit) {
			return duration;
		}
		TemporalUnit[] units = TemporalUnit.values();
		for(int unitIndex=smallestUnit.ordinal()-1; unitIndex>=largestUnit.ordinal(); unitIndex--) {
			TemporalUnit unit = units[unitIndex];
			if(unit==TemporalUnit.WEEK && largestUnit!=TemporalUnit.WEEK) {
				continue;
			}
			DateDuration endDuration;
			switch(unit) {
				case YEAR:
					endDuration = new DateDuration(duration.date().years()+sign,0,0,0);
					break;
				case MONTH:
					endDuration = adjustDateDurationRecord(duration.date(),0,0L,duration.date().months()+sign);
					break;
				default:
					endDuration = adjustDateDurationRecord(duration.date(),0,duration.date().weeks()+sign,null);
			}
			IsoDate end = calendarDateAdd(calendar,dt.date(),endDuration,"constrain");
			BigInteger endEpochNs = epochNsFor(timeZone,new IsoDateTime(end,dt.time()));
			boolean didExpandToEnd = nudgedEpochNs.compareTo(endEpochNs)!=-sign;
			if(didExpandToEnd) {
				duration = new InternalDuration(endDuration,TimeDuration.ZERO);
			} else {
				break;
			}
		}
		return duration;
	}

	private static InternalDuration roundRelativeDuration(InternalDuration duration, BigInteger originEpochNs, BigInteger destEpochNs, IsoDateTime dt, String timeZone, String calendar, TemporalUnit largestUnit, long increment, TemporalUnit smallestUnit, RoundingMode mode) {
		boolean irregularLengthUnit = smallestUnit.isCalendarUnit() || (timeZone!=null && smallestUnit==TemporalUnit.DAY);
		int sign = duration.sign()<0 ? -1 : 1;
		NudgeResult nudge;
		if(irregularLengthUnit) {
			nudge = nudgeToCalendarUnit(sign,duration,originEpochNs,destEpochNs,dt,timeZone,calendar,increment,smallestUnit,mode);
		} else if(timeZone!=null) {
			nudge = nudgeToZonedTime(sign,duration,dt,timeZone,calendar,increment,smallestUnit,mode);
		} else {
			nudge = nudgeToDayOrTime(duration,destEpochNs,largestUnit,increment,smallestUnit,mode);
		}
		duration = nudge.duration();
		if(nudge.didExpandCalendarUnit() && smallestUnit!=TemporalUnit.WEEK) {
			duration = bubbleRelativeDuration(sign,duration,nudge.nudgedEpochNs(),dt,timeZone,calendar,largestUnit,TemporalUnit.larger(smallestUnit,TemporalUnit.DAY));
		}
		return duration;
	}

	private static double totalRelativeDuration(InternalDuration duration, BigInteger originEpochNs, BigInteger destEpochNs, IsoDateTime dt, String timeZone, String calendar, TemporalUnit unit) {
		if(unit.isCalendarUnit() || (timeZone!=null && unit==TemporalUnit.DAY)) {
			int sign = duration.sign()<0 ? -1 : 1;
			return nudgeToCalendarUnit(sign,duration,originEpochNs,destEpochNs,dt,timeZone,calendar,1,unit,RoundingMode.TRUNC).total();
		}
		TimeDuration timeDuration = duration.time().add24HourDays(duration.date().days());
		return totalTimeDuration(timeDuration,unit);
	}

	public static InternalDuration differencePlainDateTimeWithRounding(IsoDateTime dt1, IsoDateTime dt2, String calendar, TemporalUnit largestUnit, long increment, TemporalUnit smallestUnit, RoundingMode mode) {
		if(compareISODateTime(dt1,dt2)==0) {
			return InternalDuration.ZERO;
		}
		rejectDateTimeRange(dt1);
		rejectDateTimeRange(dt2);
		InternalDuration duration = differenceISODateTime(dt1,dt2,calendar,largestUnit);
		if(smallestUnit==TemporalUnit.NANOSECOND && increment==1) {
			return duration;
		}
		BigInteger originEpochNs = getUTCEpochNanoseconds(dt1);
		BigInteger destEpochNs = getUTCEpochNanoseconds(dt2);
		return roundRelativeDuration(duration,originEpochNs,destEpochNs,dt1,null,calendar,largestUnit,increment,smallestUnit,mode);
	}

	public static double differencePlainDateTimeWithTotal(IsoDateTime dt1, IsoDateTime dt2, String calendar, TemporalUnit unit) {
		if(compareISODateTime(dt1,dt2)==0) {
			return 0;
		}
		rejectDateTimeRange(dt1);
		rejectDateTimeRange(dt2);
		InternalDuration duration = differenceISODateTime(dt1,dt2,calendar,unit);
		if(unit==TemporalUnit.NANOSECOND) {
			return duration.time().getTotalNs().doubleValue();
		}
		BigInteger originEpochNs = getUTCEpochNanoseconds(dt1);
		BigInteger destEpochNs = getUTCEpochNanoseconds(dt2);
		return totalRelativeDuration(duration,originEpochNs,destEpochNs,dt1,null,calendar,unit);
	}

	public static InternalDuration differenceZonedDateTimeWithRounding(BigInteger ns1, BigInteger ns2, String timeZone, String calendar, TemporalUnit largestUnit, long increment, TemporalUnit smallestUnit, RoundingMode mode) {
		if(!largestUnit.date) {
			return differenceInstant(ns1,ns2,increment,smallestUnit,mode);
		}
		InternalDuration duration = differenceZonedDateTime(ns1,ns2,timeZone,calendar,largestUnit);
		if(smallestUnit==TemporalUnit.NANOSECOND && increment==1) {
			return duration;
		}
		IsoDateTime dt = getISODateTimeFor(timeZone,ns1);
		return roundRelativeDuration(duration,ns1,ns2,dt,timeZone,calendar,largestUnit,increment,smallestUnit,mode);
	}

	public static double differenceZonedDateTimeWithTotal(BigInteger ns1, BigInteger ns2, String timeZone, String calendar, TemporalUnit unit) {
		if(!unit.date) {
			return totalTimeDuration(TimeDuration.fromEpochNsDiff(ns2,ns1),unit);
		}
		InternalDuration duration = differenceZonedDateTime(ns1,ns2,timeZone,calendar,unit);
		IsoDateTime dt = getISODateTimeFor(timeZone,ns1);
		return totalRelativeDuration(duration,ns1,ns2,dt,timeZone,calendar,unit);
	}

	public record DifferenceSettings(TemporalUnit largestUnit, long roundingIncrement, RoundingMode roundingMode, TemporalUnit smallestUnit) {
	}

	public static DifferenceSettings getDifferenceSettings(boolean since, Object options, String group, TemporalUnit[] disallowed, TemporalUnit fallbackSmallest, TemporalUnit smallestLargestDefaultUnit) {
		List<TemporalUnit> disallowedList = Arrays.asList(disallowed);
		TemporalUnit largestUnit = getTemporalUnitValuedOption(options,"largestUnit",null);
		long roundingIncrement = getRoundingIncrementOption(options);
		RoundingMode roundingMode = getRoundingModeOption(options,RoundingMode.TRUNC);
		TemporalUnit smallestUnit = getTemporalUnitValuedOption(options,"smallestUnit",null);
		validateTemporalUnitValue(largestUnit,group,true);
		if(largestUnit==null) {
			largestUnit = TemporalUnit.AUTO;
		}
		if(disallowedList.contains(largestUnit)) {
			throw rangeError("largestUnit "+largestUnit+" not allowed");
		}
		if(since) {
			roundingMode = roundingMode.negate();
		}
		validateTemporalUnitValue(smallestUnit,group,false);
		if(smallestUnit==null) {
			smallestUnit = fallbackSmallest;
		}
		if(disallowedList.contains(smallestUnit)) {
			throw rangeError("smallestUnit "+smallestUnit+" not allowed");
		}
		TemporalUnit defaultLargestUnit = TemporalUnit.larger(smallestLargestDefaultUnit,smallestUnit);
		if(largestUnit==TemporalUnit.AUTO) {
			largestUnit = defaultLargestUnit;
		}
		if(TemporalUnit.larger(largestUnit,smallestUnit)!=largestUnit) {
			throw rangeError("largestUnit "+largestUnit+" cannot be smaller than smallestUnit "+smallestUnit);
		}
		long maximum = maximumIncrement(smallestUnit);
		if(maximum!=0) {
			validateTemporalRoundingIncrement(roundingIncrement,maximum,false);
		}
		return new DifferenceSettings(largestUnit,roundingIncrement,roundingMode,smallestUnit);
	}

	public static long maximumIncrement(TemporalUnit unit) {
		switch(unit) {
			case HOUR: return 24;
			case MINUTE: case SECOND: return 60;
			case MILLISECOND: case MICROSECOND: case NANOSECOND: return 1000;
			default: return 0;
		}
	}

	private static final TemporalUnit[] NO_UNITS = {};

	public static TemporalDurationObject differenceTemporalInstant(boolean since, TemporalInstantObject instant, Object other, Object options) {
		TemporalInstantObject o = toTemporalInstant(other);
		Object resolved = getOptionsObject(options);
		DifferenceSettings s = getDifferenceSettings(since,resolved,"time",NO_UNITS,TemporalUnit.NANOSECOND,TemporalUnit.SECOND);
		InternalDuration d = differenceInstant(instant.getEpochNs(),o.getEpochNs(),s.roundingIncrement(),s.smallestUnit(),s.roundingMode());
		TemporalDurationObject result = temporalDurationFromInternal(d,s.largestUnit());
		return since ? createNegatedTemporalDuration(result) : result;
	}

	private static void checkSameCalendar(String c1, String c2) {
		if(!c1.equals(c2)) {
			throw rangeError("cannot compute difference between dates of "+c1+" and "+c2+" calendars");
		}
	}

	public static TemporalDurationObject differenceTemporalPlainDate(boolean since, TemporalPlainDateObject date, Object other, Object options) {
		TemporalPlainDateObject o = toTemporalDate(other,RuntimeUtil.UNDEFINED);
		checkSameCalendar(date.getCalendar(),o.getCalendar());
		Object resolved = getOptionsObject(options);
		DifferenceSettings s = getDifferenceSettings(since,resolved,"date",NO_UNITS,TemporalUnit.DAY,TemporalUnit.DAY);
		IsoDate d1 = date.getIsoDate();
		IsoDate d2 = o.getIsoDate();
		if(compareISODate(d1,d2)==0) {
			return createTemporalDuration(new double[10]);
		}
		DateDuration dateDifference = calendarDateUntil(date.getCalendar(),d1,d2,s.largestUnit());
		InternalDuration duration = new InternalDuration(dateDifference,TimeDuration.ZERO);
		if(!(s.smallestUnit()==TemporalUnit.DAY && s.roundingIncrement()==1)) {
			IsoDateTime dt1 = new IsoDateTime(d1,TimeRecord.MIDNIGHT);
			IsoDateTime dt2 = new IsoDateTime(d2,TimeRecord.MIDNIGHT);
			duration = roundRelativeDuration(duration,getUTCEpochNanoseconds(dt1),getUTCEpochNanoseconds(dt2),dt1,null,date.getCalendar(),s.largestUnit(),s.roundingIncrement(),s.smallestUnit(),s.roundingMode());
		}
		TemporalDurationObject result = temporalDurationFromInternal(duration,TemporalUnit.DAY);
		return since ? createNegatedTemporalDuration(result) : result;
	}

	public static TemporalDurationObject differenceTemporalPlainDateTime(boolean since, TemporalPlainDateTimeObject dateTime, Object other, Object options) {
		TemporalPlainDateTimeObject o = toTemporalDateTime(other,RuntimeUtil.UNDEFINED);
		checkSameCalendar(dateTime.getCalendar(),o.getCalendar());
		Object resolved = getOptionsObject(options);
		DifferenceSettings s = getDifferenceSettings(since,resolved,"datetime",NO_UNITS,TemporalUnit.NANOSECOND,TemporalUnit.DAY);
		IsoDateTime dt1 = dateTime.getIsoDateTime();
		IsoDateTime dt2 = o.getIsoDateTime();
		if(compareISODateTime(dt1,dt2)==0) {
			return createTemporalDuration(new double[10]);
		}
		InternalDuration duration = differencePlainDateTimeWithRounding(dt1,dt2,dateTime.getCalendar(),s.largestUnit(),s.roundingIncrement(),s.smallestUnit(),s.roundingMode());
		TemporalDurationObject result = temporalDurationFromInternal(duration,s.largestUnit());
		return since ? createNegatedTemporalDuration(result) : result;
	}

	public static TemporalDurationObject differenceTemporalPlainTime(boolean since, TemporalPlainTimeObject time, Object other, Object options) {
		TemporalPlainTimeObject o = toTemporalTime(other,RuntimeUtil.UNDEFINED);
		Object resolved = getOptionsObject(options);
		DifferenceSettings s = getDifferenceSettings(since,resolved,"time",NO_UNITS,TemporalUnit.NANOSECOND,TemporalUnit.HOUR);
		TimeDuration d = differenceTime(time.getTime(),o.getTime());
		d = roundTimeDuration(d,s.roundingIncrement(),s.smallestUnit(),s.roundingMode());
		TemporalDurationObject result = temporalDurationFromInternal(new InternalDuration(DateDuration.ZERO,d),s.largestUnit());
		return since ? createNegatedTemporalDuration(result) : result;
	}

	public static TemporalDurationObject differenceTemporalPlainYearMonth(boolean since, TemporalPlainYearMonthObject ym, Object other, Object options) {
		TemporalPlainYearMonthObject o = toTemporalYearMonth(other,RuntimeUtil.UNDEFINED);
		String calendar = ym.getCalendar();
		checkSameCalendar(calendar,o.getCalendar());
		Object resolved = getOptionsObject(options);
		DifferenceSettings s = getDifferenceSettings(since,resolved,"date",new TemporalUnit[] {TemporalUnit.WEEK,TemporalUnit.DAY},TemporalUnit.MONTH,TemporalUnit.YEAR);
		if(compareISODate(ym.getIsoDate(),o.getIsoDate())==0) {
			return createTemporalDuration(new double[10]);
		}
		Fields thisFields = isoDateToFields(calendar,ym.getIsoDate(),"year-month");
		thisFields.day = 1.0;
		IsoDate thisDate = calendarDateFromFields(calendar,thisFields,"constrain");
		Fields otherFields = isoDateToFields(calendar,o.getIsoDate(),"year-month");
		otherFields.day = 1.0;
		IsoDate otherDate = calendarDateFromFields(calendar,otherFields,"constrain");
		DateDuration dateDifference = calendarDateUntil(calendar,thisDate,otherDate,s.largestUnit());
		InternalDuration duration = new InternalDuration(adjustDateDurationRecord(dateDifference,0,0L,null),TimeDuration.ZERO);
		if(s.smallestUnit()!=TemporalUnit.MONTH || s.roundingIncrement()!=1) {
			IsoDateTime dt1 = new IsoDateTime(thisDate,TimeRecord.MIDNIGHT);
			IsoDateTime dt2 = new IsoDateTime(otherDate,TimeRecord.MIDNIGHT);
			duration = roundRelativeDuration(duration,getUTCEpochNanoseconds(dt1),getUTCEpochNanoseconds(dt2),dt1,null,calendar,s.largestUnit(),s.roundingIncrement(),s.smallestUnit(),s.roundingMode());
		}
		TemporalDurationObject result = temporalDurationFromInternal(duration,TemporalUnit.DAY);
		return since ? createNegatedTemporalDuration(result) : result;
	}

	public static TemporalDurationObject differenceTemporalZonedDateTime(boolean since, TemporalZonedDateTimeObject zdt, Object other, Object options) {
		TemporalZonedDateTimeObject o = toTemporalZonedDateTime(other,RuntimeUtil.UNDEFINED);
		String calendar = zdt.getCalendar();
		checkSameCalendar(calendar,o.getCalendar());
		Object resolved = getOptionsObject(options);
		DifferenceSettings s = getDifferenceSettings(since,resolved,"datetime",NO_UNITS,TemporalUnit.NANOSECOND,TemporalUnit.HOUR);
		BigInteger ns1 = zdt.getEpochNs();
		BigInteger ns2 = o.getEpochNs();
		TemporalDurationObject result;
		if(!s.largestUnit().date) {
			InternalDuration d = differenceInstant(ns1,ns2,s.roundingIncrement(),s.smallestUnit(),s.roundingMode());
			result = temporalDurationFromInternal(d,s.largestUnit());
		} else {
			String timeZone = zdt.getTimeZone();
			if(!timeZoneEquals(timeZone,o.getTimeZone())) {
				throw rangeError("When calculating difference between time zones, largestUnit must be 'hours' or smaller because day lengths can vary between time zones due to DST or time zone offset changes.");
			}
			if(ns1.equals(ns2)) {
				return createTemporalDuration(new double[10]);
			}
			InternalDuration d = differenceZonedDateTimeWithRounding(ns1,ns2,timeZone,calendar,s.largestUnit(),s.roundingIncrement(),s.smallestUnit(),s.roundingMode());
			result = temporalDurationFromInternal(d,TemporalUnit.HOUR);
		}
		return since ? createNegatedTemporalDuration(result) : result;
	}

	//
	// Addition
	//

	public static BigInteger addInstant(BigInteger epochNs, TimeDuration d) {
		BigInteger result = d.addToEpochNs(epochNs);
		validateEpochNanoseconds(result);
		return result;
	}

	public static BigInteger addZonedDateTime(BigInteger epochNs, String timeZone, String calendar, InternalDuration duration, String overflow) {
		if(duration.date().sign()==0) {
			return addInstant(epochNs,duration.time());
		}
		IsoDateTime dt = getISODateTimeFor(timeZone,epochNs);
		IsoDate addedDate = calendarDateAdd(calendar,dt.date(),duration.date(),overflow);
		BigInteger intermediateNs = getEpochNanosecondsFor(timeZone,new IsoDateTime(addedDate,dt.time()),"compatible");
		return addInstant(intermediateNs,duration.time());
	}

	private static TemporalDurationObject toDurationOperand(boolean subtract, Object durationLike) {
		TemporalDurationObject d = toTemporalDuration(durationLike);
		return subtract ? createNegatedTemporalDuration(d) : d;
	}

	public static TemporalDurationObject addDurations(boolean subtract, TemporalDurationObject duration, Object other) {
		TemporalDurationObject o = toDurationOperand(subtract,other);
		TemporalUnit largestUnit = TemporalUnit.larger(defaultTemporalLargestUnit(duration.getFields()),defaultTemporalLargestUnit(o.getFields()));
		if(largestUnit.isCalendarUnit()) {
			throw rangeError("For years, months, or weeks arithmetic, use date arithmetic relative to a starting point");
		}
		InternalDuration d1 = toInternalDurationRecordWith24HourDays(duration.getFields());
		InternalDuration d2 = toInternalDurationRecordWith24HourDays(o.getFields());
		return temporalDurationFromInternal(new InternalDuration(DateDuration.ZERO,d1.time().add(d2.time())),largestUnit);
	}

	public static TemporalInstantObject addDurationToInstant(boolean subtract, TemporalInstantObject instant, Object durationLike) {
		TemporalDurationObject d = toDurationOperand(subtract,durationLike);
		TemporalUnit largestUnit = defaultTemporalLargestUnit(d.getFields());
		if(largestUnit.date) {
			throw rangeError("Duration field "+largestUnit+" not supported by Temporal.Instant. Try Temporal.ZonedDateTime instead.");
		}
		InternalDuration internal = toInternalDurationRecordWith24HourDays(d.getFields());
		return createTemporalInstant(addInstant(instant.getEpochNs(),internal.time()));
	}

	public static TemporalPlainDateObject addDurationToDate(boolean subtract, TemporalPlainDateObject date, Object durationLike, Object options) {
		String calendar = date.getCalendar();
		TemporalDurationObject d = toDurationOperand(subtract,durationLike);
		DateDuration dateDuration = toDateDurationRecordWithoutTime(d.getFields());
		Object resolved = getOptionsObject(options);
		String overflow = getTemporalOverflowOption(resolved);
		IsoDate added = calendarDateAdd(calendar,date.getIsoDate(),dateDuration,overflow);
		return createTemporalDate(added,calendar);
	}

	public static TemporalPlainDateTimeObject addDurationToDateTime(boolean subtract, TemporalPlainDateTimeObject dateTime, Object durationLike, Object options) {
		TemporalDurationObject d = toDurationOperand(subtract,durationLike);
		Object resolved = getOptionsObject(options);
		String overflow = getTemporalOverflowOption(resolved);
		String calendar = dateTime.getCalendar();
		InternalDuration internal = toInternalDurationRecordWith24HourDays(d.getFields());
		IsoDateTime dt = dateTime.getIsoDateTime();
		TimeRecord timeResult = addTime(dt.time(),internal.time());
		DateDuration dateDuration = adjustDateDurationRecord(internal.date(),timeResult.deltaDays(),null,null);
		IsoDate added = calendarDateAdd(calendar,dt.date(),dateDuration,overflow);
		return createTemporalDateTime(new IsoDateTime(added,timeResult.withoutDays()),calendar);
	}

	public static TemporalPlainTimeObject addDurationToTime(boolean subtract, TemporalPlainTimeObject time, Object durationLike) {
		TemporalDurationObject d = toDurationOperand(subtract,durationLike);
		InternalDuration internal = toInternalDurationRecordWith24HourDays(d.getFields());
		TimeRecord t = addTime(time.getTime(),internal.time());
		return createTemporalTime(t.withoutDays());
	}

	public static TemporalPlainYearMonthObject addDurationToYearMonth(boolean subtract, TemporalPlainYearMonthObject ym, Object durationLike, Object options) {
		TemporalDurationObject d = toDurationOperand(subtract,durationLike);
		InternalDuration internal = toInternalDurationRecord(d.getFields());
		Object resolved = getOptionsObject(options);
		String overflow = getTemporalOverflowOption(resolved);
		DateDuration toAdd = internal.date();
		if(toAdd.weeks()!=0 || toAdd.days()!=0 || !internal.time().isZero()) {
			throw rangeError("only years and months can be added to Temporal.PlainYearMonth");
		}
		String calendar = ym.getCalendar();
		Fields fields = isoDateToFields(calendar,ym.getIsoDate(),"year-month");
		fields.day = 1.0;
		IsoDate date = calendarDateFromFields(calendar,fields,"constrain");
		IsoDate added = calendarDateAdd(calendar,date,toAdd,overflow);
		Fields addedFields = isoDateToFields(calendar,added,"year-month");
		IsoDate isoDate = calendarYearMonthFromFields(calendar,addedFields,overflow);
		return createTemporalYearMonth(isoDate,calendar);
	}

	public static TemporalZonedDateTimeObject addDurationToZonedDateTime(boolean subtract, TemporalZonedDateTimeObject zdt, Object durationLike, Object options) {
		TemporalDurationObject d = toDurationOperand(subtract,durationLike);
		Object resolved = getOptionsObject(options);
		String overflow = getTemporalOverflowOption(resolved);
		InternalDuration internal = toInternalDurationRecord(d.getFields());
		BigInteger ns = addZonedDateTime(zdt.getEpochNs(),zdt.getTimeZone(),zdt.getCalendar(),internal,overflow);
		return createTemporalZonedDateTime(ns,zdt.getTimeZone(),zdt.getCalendar());
	}

	//
	// Rounding of times and instants
	//

	public static BigInteger roundTemporalInstant(BigInteger epochNs, long increment, TemporalUnit unit, RoundingMode mode) {
		return TemporalMath.roundAsIfPositive(epochNs,unit.bigNsPerUnit.multiply(BigInteger.valueOf(increment)),mode);
	}

	public static TimeRecord roundTime(TimeRecord t, long increment, TemporalUnit unit, RoundingMode mode) {
		long quantity;
		switch(unit) {
			case DAY: case HOUR:
				quantity = ((((t.hour()*60L+t.minute())*60L+t.second())*1000L+t.millisecond())*1000L+t.microsecond())*1000L+t.nanosecond();
				break;
			case MINUTE:
				quantity = (((t.minute()*60L+t.second())*1000L+t.millisecond())*1000L+t.microsecond())*1000L+t.nanosecond();
				break;
			case SECOND:
				quantity = ((t.second()*1000L+t.millisecond())*1000L+t.microsecond())*1000L+t.nanosecond();
				break;
			case MILLISECOND:
				quantity = (t.millisecond()*1000L+t.microsecond())*1000L+t.nanosecond();
				break;
			case MICROSECOND:
				quantity = t.microsecond()*1000L+t.nanosecond();
				break;
			default:
				quantity = t.nanosecond();
		}
		long nsPerUnit = unit.nsPerUnit;
		long result = TemporalMath.roundLongToIncrement(quantity,nsPerUnit*increment,mode)/nsPerUnit;
		switch(unit) {
			case DAY: return new TimeRecord(result,0,0,0,0,0,0);
			case HOUR: return balanceTime(result,0,0,0,0,0);
			case MINUTE: return balanceTime(t.hour(),result,0,0,0,0);
			case SECOND: return balanceTime(t.hour(),t.minute(),result,0,0,0);
			case MILLISECOND: return balanceTime(t.hour(),t.minute(),t.second(),result,0,0);
			case MICROSECOND: return balanceTime(t.hour(),t.minute(),t.second(),t.millisecond(),result,0);
			default: return balanceTime(t.hour(),t.minute(),t.second(),t.millisecond(),t.microsecond(),result);
		}
	}

	public static IsoDateTime roundISODateTime(IsoDateTime dt, long increment, TemporalUnit unit, RoundingMode mode) {
		TimeRecord time = roundTime(dt.time(),increment,unit,mode);
		IsoDate date = addDaysToISODate(dt.date(),time.deltaDays());
		return new IsoDateTime(date,time.withoutDays());
	}

	public static String temporalInstantToString(BigInteger epochNs, String timeZone, int precision) {
		String outputTimeZone = timeZone!=null ? timeZone : "UTC";
		IsoDateTime iso = getISODateTimeFor(outputTimeZone,epochNs);
		String dateTimeString = isoDateTimeToString(iso,ISO8601,precision,"never");
		String timeZoneString = "Z";
		if(timeZone!=null) {
			timeZoneString = formatDateTimeUTCOffsetRounded(getOffsetNanosecondsFor(outputTimeZone,epochNs));
		}
		return dateTimeString+timeZoneString;
	}

	public static String temporalZonedDateTimeToString(TemporalZonedDateTimeObject zdt, int precision, String showCalendar, String showTimeZone, String showOffset, long increment, TemporalUnit unit, RoundingMode mode) {
		BigInteger epochNs = zdt.getEpochNs();
		if(unit!=null) {
			epochNs = roundTemporalInstant(epochNs,increment,unit,mode);
		}
		String tz = zdt.getTimeZone();
		long offsetNs = getOffsetNanosecondsFor(tz,epochNs);
		IsoDateTime iso = getISODateTimeFor(tz,epochNs);
		StringBuilder b = new StringBuilder(isoDateTimeToString(iso,ISO8601,precision,"never"));
		if(!"never".equals(showOffset)) {
			b.append(formatDateTimeUTCOffsetRounded(offsetNs));
		}
		if(!"never".equals(showTimeZone)) {
			b.append('[');
			if("critical".equals(showTimeZone)) {
				b.append('!');
			}
			b.append(tz).append(']');
		}
		b.append(formatCalendarAnnotation(zdt.getCalendar(),showCalendar));
		return b.toString();
	}

	public static BigInteger systemUTCEpochNanoseconds() {
		java.time.Instant now = java.time.Instant.now();
		return BigInteger.valueOf(now.getEpochSecond()).multiply(TimeDuration.NS_PER_SECOND).add(BigInteger.valueOf(now.getNano()));
	}

	public static void valueOfThrows(String constructorName) {
		throw typeError("Do not use built-in arithmetic operators with Temporal objects. When comparing, use Temporal."+constructorName+".compare(obj1, obj2), not obj1 > obj2.");
	}
}
