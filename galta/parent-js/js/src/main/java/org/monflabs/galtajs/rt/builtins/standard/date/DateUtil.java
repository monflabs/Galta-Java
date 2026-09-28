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
 */
package org.monflabs.galtajs.rt.builtins.standard.date;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.util.StringUtil;
import org.monflabs.util.datetime.ISO8601;


/**
 * To simplify the management of Dates, we use the Java representation of Dates
 * If this works well for Date in our era, we might see differences with historical dates.
 */
public class DateUtil {
	
	public static long doubleToLong(double d) {
		if(Double.isNaN(d) || Double.isInfinite(d) ) {
			return Long.MIN_VALUE;
		}
		return (long)d;
	}
	public static double longToDouble(long l) {
		if(l==Long.MIN_VALUE) {
			return Double.NaN;
		}
		return (double)l;
	}
	public static double dateToDouble(Date _this) {
		long l = _this.getTime();
		if(l==Long.MIN_VALUE) {
			return Double.NaN;
		}
		return (double)l;
	}
	
	private static GregorianCalendar staticCalendar = newProlepticGregorianCalendar(TimeZone.getDefault());
    private static GregorianCalendar utcCalendar = newProlepticGregorianCalendar(TimeZone.getTimeZone("UTC"));
    //private static GregorianCalendar gmtCalendar = new GregorianCalendar(TimeZone.getTimeZone("GMT"));

    // ECMAScript Date math assumes a pure proleptic Gregorian calendar for
    // ALL time values (see spec "Overview of Date Objects and Definitions
    // of Abstract Operations" / Year Number), with no Julian-calendar
    // switchover. java.util.GregorianCalendar defaults to switching to the
    // Julian calendar for instants before 1582-10-15, which silently
    // corrupts year/month/day/weekday fields (and therefore every
    // getter/setter and string-format method) for historical dates. Moving
    // the Gregorian change to the earliest representable instant makes the
    // calendar purely proleptic Gregorian, matching the spec.
    private static GregorianCalendar newProlepticGregorianCalendar(TimeZone tz) {
    	GregorianCalendar cal = new GregorianCalendar(tz);
    	cal.setGregorianChange(new Date(Long.MIN_VALUE));
    	return cal;
    }

	//public static final String FORMAT_DATE_TIME = "EEE MMM dd yyyy HH:mm:ss z";
	public static final String FORMAT_DATE_TIME = "EEE MMM dd yyyy HH:mm:ss 'GMT'XXX";
	public static final String FORMAT_DATE 		= "EEE MMM dd yyyy";
	public static final String FORMAT_TIME 		= "HH:mm:ss 'GMT'XXX";

	// toDateString()/toString()/toTimeString()/toUTCString() are built by
	// hand (rather than via SimpleDateFormat, which is used only by the
	// locale-flavoured toLocaleXxxString() methods) for two spec reasons
	// SimpleDateFormat can't satisfy:
	//  1. Year must be a MINIMUM 4-digit, sign-prefixed field ("-0001",
	//     "-123456", but never "+" for positive years) - SimpleDateFormat's
	//     "yyyy" instead renders an era-relative, unsigned year.
	//  2. The UTC offset must render as "GMT+HHMM" (no colon) - "XXX"
	//     renders "+HH:MM" with a colon.
	private static final String[] DAY_NAMES = {"Sun","Mon","Tue","Wed","Thu","Fri","Sat"};
	private static final String[] MONTH_NAMES = {"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};

	private static String pad2(int v) {
		return v<10 ? "0"+v : String.valueOf(v);
	}

	// Zero-pads to a MINIMUM of 4 digits (never truncates a longer year),
	// sign-prefixed only when negative - see ECMA-262 DateString/
	// UTCDateString "yearSign"/"paddedYear" steps.
	private static String formatYear(int year) {
		String sign = year<0 ? "-" : "";
		String digits = Long.toString(Math.abs((long)year));
		if(digits.length()<4) {
			digits = "0000".substring(digits.length()) + digits;
		}
		return sign+digits;
	}

	// Calendar.YEAR is always positive; the BC/AD ERA carries the sign.
	// 1 BC (ERA=BC, YEAR=1) is proleptic year 0, 2 BC is year -1, etc.
	private static int eraAwareYear(GregorianCalendar cal) {
		int year = cal.get(Calendar.YEAR);
		return cal.get(Calendar.ERA)==GregorianCalendar.BC ? 1-year : year;
	}

	// Setting only Calendar.YEAR (leaving ERA untouched) is unsafe on a
	// calendar that was just loaded from an existing time value via
	// setTimeInMillis()/set(): ERA keeps whatever the PREVIOUS date's sign
	// was, so e.g. going from a BC-era date to setFullYear(1) would silently
	// stay in the BC era (giving year 0 instead of year 1). Setting ERA and
	// YEAR together makes the proleptic year unambiguous regardless of the
	// calendar's prior state.
	private static void setProlepticYear(GregorianCalendar cal, int prolepticYear) {
		if(prolepticYear<=0) {
			cal.set(Calendar.ERA, GregorianCalendar.BC);
			cal.set(Calendar.YEAR, 1-prolepticYear);
		} else {
			cal.set(Calendar.ERA, GregorianCalendar.AD);
			cal.set(Calendar.YEAR, prolepticYear);
		}
	}

	// DateString: "Www Mon dd yyyy" (toDateString()/toString())
	private static String dateStringOf(GregorianCalendar cal) {
		String weekday = DAY_NAMES[cal.get(Calendar.DAY_OF_WEEK)-1];
		String month = MONTH_NAMES[cal.get(Calendar.MONTH)];
		String day = pad2(cal.get(Calendar.DAY_OF_MONTH));
		return weekday+" "+month+" "+day+" "+formatYear(eraAwareYear(cal));
	}

	// UTCDateString: "Www, dd Mon yyyy" (toUTCString())
	private static String utcDateStringOf(GregorianCalendar cal) {
		String weekday = DAY_NAMES[cal.get(Calendar.DAY_OF_WEEK)-1];
		String month = MONTH_NAMES[cal.get(Calendar.MONTH)];
		String day = pad2(cal.get(Calendar.DAY_OF_MONTH));
		return weekday+", "+day+" "+month+" "+formatYear(eraAwareYear(cal));
	}

	// TimeString: "HH:mm:ss GMT" (toTimeString()/toString()/toUTCString())
	private static String timeStringOf(GregorianCalendar cal) {
		return pad2(cal.get(Calendar.HOUR_OF_DAY))+":"+pad2(cal.get(Calendar.MINUTE))+":"+pad2(cal.get(Calendar.SECOND))+" GMT";
	}

	// TimeZoneString offset part: "+HHMM"/"-HHMM", no "GMT" prefix and no
	// colon (concatenated directly after TimeString's trailing "GMT").
	private static String timeZoneOffsetStringOf(GregorianCalendar cal) {
		int offsetMinutes = (cal.get(Calendar.ZONE_OFFSET)+cal.get(Calendar.DST_OFFSET))/60000;
		String sign = offsetMinutes>=0 ? "+" : "-";
		int abs = Math.abs(offsetMinutes);
		return sign+pad2(abs/60)+pad2(abs%60);
	}
    
	private static Map<Locale,Map<String,DateFormat>> formats = new HashMap<>();
	public static synchronized DateFormat getDateFormat(String pattern, Locale l) {
		Map<String,DateFormat> lf = formats.get(l);
		if(lf==null) {
			lf = new HashMap<>();
			formats.put(l,lf);
		}
		DateFormat fmt = lf.get(pattern);
		if(fmt==null) {
			fmt = new SimpleDateFormat(pattern,l);
			lf.put(pattern,fmt);
		}
		// SimpleDateFormat is not thread-safe: the cached instance is a prototype,
		// each caller formats with its own copy
		return (DateFormat)fmt.clone();
	}

	
	public static double timeClip(double time) {
        if (Double.isInfinite(time) || Double.isNaN(time)) {
            return Double.NaN;
        }
        if (time < -8.64e15 || time > 8.64e15) {
            return Double.NaN;
        }
        return (double)(long)time;
    }

	/**
	 * The Date constructor with separate components, in local time: a year
	 * between 0 and 99 maps to 1900-1999, as the specification requires.
	 */
	public static double date(double year, double month, double date, double hours, double minutes, double seconds, double ms) {
        if(!Double.isNaN(year)) {
        	double yi = toIntegerOrInfinity(year);
        	if(yi>=0 && yi<=99) {
        		year = 1900+yi;
        	}
        }
        return localDate(year, month, date, hours, minutes, seconds, ms);
	}

	/**
	 * A local date-time from its components, without the 0-99 year mapping
	 * (ISO strings like "0050-06-15T12:00" are year 50, not 1950).
	 */
	public static double localDate(double year, double month, double date, double hours, double minutes, double seconds, double ms) {
        double d = makeDate(makeDay(year,month,date), makeTime(hours,minutes,seconds,ms));
        return timeClip(utc(d));
	}

	//
	// Pure ECMAScript Date arithmetic (MakeTime/MakeDay/MakeDate), used by
	// Date.UTC() - unlike the Calendar-based date()/setXxx() family above,
	// Date.UTC() must handle argument values far outside int/Calendar's
	// range (test262 exercises hour/month/ms values in the 1e18+ magnitude)
	// and must match the spec's exact floating-point arithmetic, not
	// java.util.Calendar's lenient int-based overflow semantics.
	//
	private static final double MS_PER_DAY = 86400000.0;

	// ToIntegerOrInfinity, for an argument already known finite-or-infinite
	// (NaN is filtered by callers beforehand).
	private static double toIntegerOrInfinity(double d) {
		if(Double.isNaN(d)) {
			return 0;
		}
		if(Double.isInfinite(d)) {
			return d;
		}
		return d<0 ? Math.ceil(d) : Math.floor(d);
	}

	public static double makeTime(double hour, double min, double sec, double ms) {
		if(!Double.isFinite(hour) || !Double.isFinite(min) || !Double.isFinite(sec) || !Double.isFinite(ms)) {
			return Double.NaN;
		}
		double h = toIntegerOrInfinity(hour);
		double m = toIntegerOrInfinity(min);
		double s = toIntegerOrInfinity(sec);
		double milli = toIntegerOrInfinity(ms);
		return h*3600000.0 + m*60000.0 + s*1000.0 + milli;
	}

	private static boolean isLeapYear(double y) {
		if(y%4!=0) {
			return false;
		}
		if(y%100!=0) {
			return true;
		}
		return y%400==0;
	}
	private static double dayFromYear(double y) {
		return 365*(y-1970) + Math.floor((y-1969)/4) - Math.floor((y-1901)/100) + Math.floor((y-1601)/400);
	}
	// Cumulative days before each month (0-indexed) in a non-leap year.
	private static final int[] CUM_DAYS_BEFORE_MONTH = {0,31,59,90,120,151,181,212,243,273,304,334};
	private static double dayFromMonthYear(double ym, double mn) {
		double days = CUM_DAYS_BEFORE_MONTH[(int)mn];
		if(mn>=2 && isLeapYear(ym)) {
			days += 1;
		}
		return dayFromYear(ym) + days;
	}

	public static double makeDay(double year, double month, double date) {
		if(!Double.isFinite(year) || !Double.isFinite(month) || !Double.isFinite(date)) {
			return Double.NaN;
		}
		double y = toIntegerOrInfinity(year);
		double m = toIntegerOrInfinity(month);
		double dt = toIntegerOrInfinity(date);
		double ym = y + Math.floor(m/12.0);
		if(!Double.isFinite(ym)) {
			return Double.NaN;
		}
		double mn = ((m%12)+12)%12;
		double day = dayFromMonthYear(ym, mn);
		return day + dt - 1;
	}

	public static double makeDate(double day, double time) {
		if(!Double.isFinite(day) || !Double.isFinite(time)) {
			return Double.NaN;
		}
		return day*MS_PER_DAY + time;
	}
	
//	public LocalDateTime toLocalDateTime() {
//		return LocalDateTime.ofInstant(Instant.ofEpochMilli((long)utcTime),ZoneId.systemDefault());
//	}

	public static boolean isValid(Date _this) {
		double utcTime = dateToDouble(_this);
		return !Double.isNaN(utcTime);
	}
	

//    public static Date parseISOString(String str) {
//    	ZonedDateTime ld = JsonUtil.parseZonedDateTime(str);
//    	return new Date(ld.toInstant().toEpochMilli());
//    }


    public static double UTC(double year){
        return UTC(year,0,1,0,0,0,0);
    }

    public static double UTC(double year, double month){
        return UTC(year,month,1,0,0,0,0);
    }

    public static double UTC(double year, double month, double date){
        return UTC(year,month,date,0,0,0,0);
    }

    public static double UTC(double year, double month, double date, double hours){
        return UTC(year,month,date,hours,0,0,0);
    }

    public static double UTC(double year, double month, double date, double hours, double minutes){
        return UTC(year,month,date,hours,minutes,0,0);
    }

    public static double UTC(double year, double month, double date, double hours, double minutes, double seconds){
        return UTC(year,month,date,hours,minutes,seconds,0);
    }

    public static double UTC(double year, double month, double date, double hours, double minutes, double seconds, double ms){
        // 0 <= ToInteger(y) <= 99 (inclusive) -> yr = 1900+ToInteger(y).
        // Applies to `year` alone - other invalid/non-finite arguments
        // propagate NaN naturally through makeDay/makeTime below, matching
        // the exact spec step order.
        double yr = year;
        if(!Double.isNaN(year)) {
            double yi = toIntegerOrInfinity(year);
            if(yi>=0 && yi<=99) {
                yr = 1900+yi;
            }
        }
        double day = makeDay(yr,month,date);
        double time = makeTime(hours,minutes,seconds,ms);
        return timeClip(makeDate(day,time));
    }

    public static double valueOf(Date _this){
		double utcTime = dateToDouble(_this);
        return utcTime;
    }

    public static double getTime(Date _this){
		double utcTime = dateToDouble(_this);
        return utcTime;
    }

    public static double getYear(Date _this){
		double utcTime = dateToDouble(_this);
        if(Double.isNaN(utcTime)) {
            return Double.NaN;
        }
        return getFullYear(_this)-1900;
    }

    public static double getFullYear(Date _this){
		double utcTime = dateToDouble(_this);
        if(Double.isNaN(utcTime)) {
            return Double.NaN;
        }
        synchronized(staticCalendar) {
            staticCalendar.setTimeInMillis((long)utcTime);
            return eraAwareYear(staticCalendar);
        }
    }

    public static double getUTCFullYear(Date _this){
		double utcTime = dateToDouble(_this);
        if(Double.isNaN(utcTime)) {
            return Double.NaN;
        }
        synchronized(utcCalendar) {
            utcCalendar.setTimeInMillis((long)utcTime);
            return eraAwareYear(utcCalendar);
        }
    }

    public static double getMonth(Date _this){
		double utcTime = dateToDouble(_this);
        if(Double.isNaN(utcTime)) {
            return Double.NaN;
        }
        synchronized(staticCalendar) {
            staticCalendar.setTimeInMillis((long)utcTime);
            return staticCalendar.get(Calendar.MONTH);
        }
    }

    public static double getUTCMonth(Date _this){
		double utcTime = dateToDouble(_this);
        if(Double.isNaN(utcTime)) {
            return Double.NaN;
        }
        synchronized(utcCalendar) {
            utcCalendar.setTimeInMillis((long)utcTime);
            return utcCalendar.get(Calendar.MONTH);
        }
    }

    public static double getDate(Date _this){
		double utcTime = dateToDouble(_this);
        if(Double.isNaN(utcTime)) {
            return Double.NaN;
        }
        synchronized(staticCalendar) {
            staticCalendar.setTimeInMillis((long)utcTime);
            return staticCalendar.get(Calendar.DAY_OF_MONTH);
        }
    }

    public static double getUTCDate(Date _this){
		double utcTime = dateToDouble(_this);
        if(Double.isNaN(utcTime)) {
            return Double.NaN;
        }
        synchronized(utcCalendar) {
            utcCalendar.setTimeInMillis((long)utcTime);
            return utcCalendar.get(Calendar.DAY_OF_MONTH);
        }
    }

    public static double getDay(Date _this){
		double utcTime = dateToDouble(_this);
        if(Double.isNaN(utcTime)) {
            return Double.NaN;
        }
        synchronized(staticCalendar) {
            staticCalendar.setTimeInMillis((long)utcTime);
            return staticCalendar.get(Calendar.DAY_OF_WEEK)-1;
        }
    }

    public static double getUTCDay(Date _this){
		double utcTime = dateToDouble(_this);
        if(Double.isNaN(utcTime)) {
            return Double.NaN;
        }
        synchronized(utcCalendar) {
            utcCalendar.setTimeInMillis((long)utcTime);
            return utcCalendar.get(Calendar.DAY_OF_WEEK)-1;
        }
    }

    public static double getHours(Date _this){
		double utcTime = dateToDouble(_this);
        if(Double.isNaN(utcTime)) {
            return Double.NaN;
        }
        synchronized(staticCalendar) {
            staticCalendar.setTimeInMillis((long)utcTime);
            return staticCalendar.get(Calendar.HOUR_OF_DAY);
        }
    }

    public static double getUTCHours(Date _this){
		double utcTime = dateToDouble(_this);
        if(Double.isNaN(utcTime)) {
            return Double.NaN;
        }
        synchronized(utcCalendar) {
            utcCalendar.setTimeInMillis((long)utcTime);
            return utcCalendar.get(Calendar.HOUR_OF_DAY);
        }
    }

    public static double getMinutes(Date _this){
		double utcTime = dateToDouble(_this);
        if(Double.isNaN(utcTime)) {
            return Double.NaN;
        }
        synchronized(staticCalendar) {
            staticCalendar.setTimeInMillis((long)utcTime);
            return staticCalendar.get(Calendar.MINUTE);
        }
    }

    public static double getUTCMinutes(Date _this){
		double utcTime = dateToDouble(_this);
        if(Double.isNaN(utcTime)) {
            return Double.NaN;
        }
        synchronized(utcCalendar) {
            utcCalendar.setTimeInMillis((long)utcTime);
            return utcCalendar.get(Calendar.MINUTE);
        }
    }

    public static double getSeconds(Date _this){
		double utcTime = dateToDouble(_this);
        if(Double.isNaN(utcTime)) {
            return Double.NaN;
        }
        synchronized(staticCalendar) {
            staticCalendar.setTimeInMillis((long)utcTime);
            return staticCalendar.get(Calendar.SECOND);
        }
    }

    public static double getUTCSeconds(Date _this){
		double utcTime = dateToDouble(_this);
        if(Double.isNaN(utcTime)) {
            return Double.NaN;
        }
        synchronized(utcCalendar) {
            utcCalendar.setTimeInMillis((long)utcTime);
            return utcCalendar.get(Calendar.SECOND);
        }
    }

    public static double getMilliseconds(Date _this){
		double utcTime = dateToDouble(_this);
        if(Double.isNaN(utcTime)) {
            return Double.NaN;
        }
        synchronized(staticCalendar) {
            staticCalendar.setTimeInMillis((long)utcTime);
            return staticCalendar.get(Calendar.MILLISECOND);
        }
    }

    public static double getUTCMilliseconds(Date _this){
		double utcTime = dateToDouble(_this);
        if(Double.isNaN(utcTime)) {
            return Double.NaN;
        }
        synchronized(utcCalendar) {
            utcCalendar.setTimeInMillis((long)utcTime);
            return utcCalendar.get(Calendar.MILLISECOND);
        }
    }

    public static double getTimezoneOffset(Date _this){
		double utcTime = dateToDouble(_this);
        if(Double.isNaN(utcTime)) {
            return Double.NaN;
        }
        synchronized(staticCalendar) {
            staticCalendar.setTimeInMillis((long)utcTime);
            TimeZone tz = staticCalendar.getTimeZone();
            int offset;
            if (tz.inDaylightTime(new java.util.Date((long)utcTime))) {
                offset = tz.getRawOffset() + tz.getDSTSavings();
            } else {
                offset = tz.getRawOffset();
            }
            int minutes =  -(offset / 1000 / 60);
            return minutes;
        }
    }

    public static double setTime(Date _this, double time){
    	double t = timeClip(time);
    	_this.setTime(doubleToLong(t));
        return t;
    }

    // Every setter below takes the ALREADY-READ [[DateValue]] (`t`) as an
    // explicit parameter, rather than re-deriving it from `_this` internally.
    // Per spec, `t` must be captured BEFORE argument ToNumber conversion -
    // callers (DatePrototype) must snapshot `dateToDouble(_this)` first,
    // since a poisoned valueOf() on an argument can call back into setTime()
    // and mutate `_this` mid-conversion (test262's
    // date-value-read-before-tonumber-*.js).

    public static double setMilliseconds(Date _this, double t, double ms){
        return setLocalFields(_this, t, F_MS, ms);
    }

    public static double setUTCMilliseconds(Date _this, double t, double ms){
        return setUTCFields(_this, t, F_MS, ms);
    }

    public static double setSeconds(Date _this, double t, double sec){
        return setLocalFields(_this, t, F_SEC, sec);
    }

    public static double setSeconds(Date _this, double t, double sec, double ms){
        return setLocalFields(_this, t, F_SEC, sec, ms);
    }

    public static double setUTCSeconds(Date _this, double t, double sec){
        return setUTCFields(_this, t, F_SEC, sec);
    }

    public static double setUTCSeconds(Date _this, double t, double sec, double ms){
        return setUTCFields(_this, t, F_SEC, sec, ms);
    }

    public static double setMinutes(Date _this, double t, double min){
        return setLocalFields(_this, t, F_MIN, min);
    }

    public static double setMinutes(Date _this, double t, double min, double sec){
        return setLocalFields(_this, t, F_MIN, min, sec);
    }

    public static double setMinutes(Date _this, double t, double min, double sec, double ms){
        return setLocalFields(_this, t, F_MIN, min, sec, ms);
    }

    public static double setUTCMinutes(Date _this, double t, double min){
        return setUTCFields(_this, t, F_MIN, min);
    }

    public static double setUTCMinutes(Date _this, double t, double min, double sec){
        return setUTCFields(_this, t, F_MIN, min, sec);
    }

    public static double setUTCMinutes(Date _this, double t, double min, double sec, double ms){
        return setUTCFields(_this, t, F_MIN, min, sec, ms);
    }

    public static double setHours(Date _this, double t, double hour){
        return setLocalFields(_this, t, F_HOUR, hour);
    }

    public static double setHours(Date _this, double t, double hour, double min){
        return setLocalFields(_this, t, F_HOUR, hour, min);
    }

    public static double setHours(Date _this, double t, double hour, double min, double sec){
        return setLocalFields(_this, t, F_HOUR, hour, min, sec);
    }

    public static double setHours(Date _this, double t, double hour, double min, double sec, double ms){
        return setLocalFields(_this, t, F_HOUR, hour, min, sec, ms);
    }

    public static double setUTCHours(Date _this, double t, double hour){
        return setUTCFields(_this, t, F_HOUR, hour);
    }

    public static double setUTCHours(Date _this, double t, double hour, double min){
        return setUTCFields(_this, t, F_HOUR, hour, min);
    }

    public static double setUTCHours(Date _this, double t, double hour, double min, double sec){
        return setUTCFields(_this, t, F_HOUR, hour, min, sec);
    }

    public static double setUTCHours(Date _this, double t, double hour, double min, double sec, double ms){
        return setUTCFields(_this, t, F_HOUR, hour, min, sec, ms);
    }

    public static double setDate(Date _this, double t, double date){
        return setLocalFields(_this, t, F_DATE, date);
    }

    public static double setUTCDate(Date _this, double t, double date){
        return setUTCFields(_this, t, F_DATE, date);
    }

    public static double setMonth(Date _this, double t, double month){
        return setLocalFields(_this, t, F_MONTH, month);
    }

    public static double setMonth(Date _this, double t, double month, double date){
        return setLocalFields(_this, t, F_MONTH, month, date);
    }

    public static double setUTCMonth(Date _this, double t, double month){
        return setUTCFields(_this, t, F_MONTH, month);
    }

    public static double setUTCMonth(Date _this, double t, double month, double date){
        return setUTCFields(_this, t, F_MONTH, month, date);
    }

    // setFullYear/setUTCFullYear are the ONLY date-component setters that
    // "revive" an invalid Date: per spec, if t is NaN, t is treated as +0
    // rather than propagating NaN through to the result. For the local
    // variant, spec step 2 says "if t is NaN, set t to +0" - NOT
    // "LocalTime(+0)" - so the revived value is 1970-01-01T00:00:00.000
    // as ALREADY-LOCAL wall-clock fields.
    public static double setFullYear(Date _this, double t, double year){
        return setFullYearFields(_this, t, true, year);
    }

    public static double setFullYear(Date _this, double t, double year, double month){
        return setFullYearFields(_this, t, true, year, month);
    }

    public static double setFullYear(Date _this, double t, double year, double month, double date){
        return setFullYearFields(_this, t, true, year, month, date);
    }

    // `rawT` is `this`'s [[DateValue]] read by the CALLER before ToNumber(year)
    // - per spec (B.2.4.2 step 3-4), [[DateValue]] is read FIRST, so a `year`
    // whose own ToNumber/valueOf() mutates `this`'s time (e.g. via setTime())
    // must NOT affect this call's own notion of `t`. A NaN `t` is revived as
    // the local +0 wall-clock time by setFullYear, exactly like the spec's
    // "if t is NaN, set t to +0". The 0<=y<=99 range check is against
    // ToIntegerOrInfinity(y), not the raw value (-0.9999999 maps to 1900).
    public static double setYear(Date _this, double rawT, double year){
        if(Double.isNaN(year)) {
        	return setTime(_this,Double.NaN);
        }
        double y = toIntegerOrInfinity(year);
        if (y >= 0 && y <= 99) {
            return setFullYear(_this,rawT,1900 + y);
        } else {
        	return setFullYear(_this,rawT,y);
        }
    }

    public static double setUTCFullYear(Date _this, double t, double year){
        return setFullYearFields(_this, t, false, year);
    }

    public static double setUTCFullYear(Date _this, double t, double year, double month){
        return setFullYearFields(_this, t, false, year, month);
    }

    public static double setUTCFullYear(Date _this, double t, double year, double month, double date){
        return setFullYearFields(_this, t, false, year, month, date);
    }

    //
    // Spec arithmetic for the setters: the time value is decomposed into its
    // (local or UTC) fields, the provided fields are replaced starting at
    // `first`, and the result is recomposed with MakeDay/MakeTime/MakeDate.
    // Everything stays in double, so huge or infinite arguments produce NaN
    // (or the exact value) instead of saturating through an (int) cast.
    //
    private static final int F_YEAR = 0;
    private static final int F_MONTH = 1;
    private static final int F_DATE = 2;
    private static final int F_HOUR = 3;
    private static final int F_MIN = 4;
    private static final int F_SEC = 5;
    private static final int F_MS = 6;

    private static double setLocalFields(Date _this, double t, int first, double... values) {
        if(Double.isNaN(t)) {
        	return Double.NaN;
        }
        double[] f = fields(localTime(t));
        return setTime(_this, compose(f, first, values, true));
    }

    private static double setUTCFields(Date _this, double t, int first, double... values) {
        if(Double.isNaN(t)) {
        	return Double.NaN;
        }
        double[] f = fields(t);
        return setTime(_this, compose(f, first, values, false));
    }

    private static double setFullYearFields(Date _this, double t, boolean local, double... values) {
        double base = Double.isNaN(t) ? 0 : (local ? localTime(t) : t);
        double[] f = fields(base);
        return setTime(_this, compose(f, F_YEAR, values, local));
    }

    private static double compose(double[] f, int first, double[] values, boolean local) {
        System.arraycopy(values, 0, f, first, values.length);
        double d = makeDate(makeDay(f[F_YEAR], f[F_MONTH], f[F_DATE]), makeTime(f[F_HOUR], f[F_MIN], f[F_SEC], f[F_MS]));
        return timeClip(local ? utc(d) : d);
    }

    /**
     * Decompose a (finite) time value into year, month, date, hours, minutes,
     * seconds and milliseconds (YearFromTime, MonthFromTime, ...).
     */
    private static double[] fields(double t) {
        double day = Math.floor(t/MS_PER_DAY);
        double timeInDay = t - day*MS_PER_DAY;
        double y = Math.floor(day/365.2425) + 1970;
        while(dayFromYear(y) > day) {
        	y--;
        }
        while(dayFromYear(y+1) <= day) {
        	y++;
        }
        int dayInYear = (int)(day - dayFromYear(y));
        int leap = isLeapYear(y) ? 1 : 0;
        int month = 11;
        while(month>0 && dayInYear < CUM_DAYS_BEFORE_MONTH[month] + (month>=2 ? leap : 0)) {
        	month--;
        }
        int date = dayInYear - CUM_DAYS_BEFORE_MONTH[month] - (month>=2 ? leap : 0) + 1;
        double hour = Math.floor(timeInDay/3600000.0);
        double min = Math.floor((timeInDay%3600000.0)/60000.0);
        double sec = Math.floor((timeInDay%60000.0)/1000.0);
        double ms = timeInDay%1000.0;
        return new double[] {y, month, date, hour, min, sec, ms};
    }

    private static TimeZone localTimeZone() {
    	return staticCalendar.getTimeZone();
    }

    /**
     * LocalTime(t): UTC time value to local wall-clock time value.
     */
    static double localTime(double t) {
    	return t + localTimeZone().getOffset((long)t);
    }

    /**
     * UTC(t): local wall-clock time value to UTC time value. The offset is the one
     * in effect at the resulting instant, which resolves DST transitions the way
     * java.util.Calendar does.
     */
    static double utc(double t) {
    	if(!Double.isFinite(t)) {
    		return Double.NaN;
    	}
    	TimeZone tz = localTimeZone();
    	int offset = tz.getOffset((long)(t - tz.getRawOffset()));
    	offset = tz.getOffset((long)(t - offset));
    	return t - offset;
    }

	public static String formatString(Date _this, String pattern, String locale) {
		final double utcTime = dateToDouble(_this);
        if(Double.isNaN(utcTime)) {
            return "Invalid Date";
        }
        Locale l = StringUtil.isNotEmpty(locale) ? Locale.forLanguageTag(locale) : Locale.getDefault();
    	return getDateFormat(pattern,l).format(new java.util.Date((long)utcTime));
    }

    public static String toString(Date _this){
		final double utcTime = dateToDouble(_this);
        if(Double.isNaN(utcTime)) {
            return "Invalid Date";
        }
        synchronized(staticCalendar) {
        	staticCalendar.setTimeInMillis((long)utcTime);
        	return dateStringOf(staticCalendar)+" "+timeStringOf(staticCalendar)+timeZoneOffsetStringOf(staticCalendar);
        }
    }

    public static String toDateString(Date _this) {
		final double utcTime = dateToDouble(_this);
        if(Double.isNaN(utcTime)) {
            return "Invalid Date";
        }
        synchronized(staticCalendar) {
        	staticCalendar.setTimeInMillis((long)utcTime);
        	return dateStringOf(staticCalendar);
        }
    }

    public static String toTimeString(Date _this){
		final double utcTime = dateToDouble(_this);
        if(Double.isNaN(utcTime)) {
            return "Invalid Date";
        }
        synchronized(staticCalendar) {
        	staticCalendar.setTimeInMillis((long)utcTime);
        	return timeStringOf(staticCalendar)+timeZoneOffsetStringOf(staticCalendar);
        }
    }

    public static String toLocaleString(Date _this, String locale){
    	return formatString(_this,FORMAT_DATE_TIME,locale);
    }

    public static String toLocaleDateString(Date _this, String locale){
    	return formatString(_this,FORMAT_DATE,locale);
    }

    public static String toLocaleTimeString(Date _this, String locale){
    	return formatString(_this,FORMAT_TIME,locale);
    }
    
    public static String toUTCString(Date _this){
		final double utcTime = dateToDouble(_this);
        if(Double.isNaN(utcTime)) {
            return "Invalid Date";
        }
        synchronized(utcCalendar) {
        	utcCalendar.setTimeInMillis((long)utcTime);
        	return utcDateStringOf(utcCalendar)+" "+timeStringOf(utcCalendar);
        }
    }

    public static String toISOString(Date _this){
		final double utcTime = dateToDouble(_this);
        if(Double.isNaN(utcTime)) {
        	// Unlike toString()/toUTCString() (which render "Invalid Date"),
        	// toISOString() must throw for a non-finite time value per spec
        	// (21.4.4.36 step 2: "If tv is not finite, throw a RangeError").
            throw RuntimeUtil.rangeError("Invalid time value");
        }
        synchronized(utcCalendar) {
        	utcCalendar.setTimeInMillis((long)utcTime);
            return ISO8601.formatISO8601(utcCalendar);
        }
    }

    public static Date adjust(JSEnvironment env, Date _this, int years, int months, int days, int hours, int minutes, int seconds, int ms, boolean localTime ) {
		final double utcTime = dateToDouble(_this);
        if(Double.isNaN(utcTime)) {
            return new Date(doubleToLong(Double.NaN));
        }
        GregorianCalendar cal = localTime ? staticCalendar : utcCalendar;
        synchronized(cal) {
            cal.setTimeInMillis((long)utcTime);
            cal.add(GregorianCalendar.YEAR, years);
            cal.add(GregorianCalendar.MONTH, months);
            cal.add(GregorianCalendar.DAY_OF_MONTH, days);
            cal.add(GregorianCalendar.HOUR, hours);
            cal.add(GregorianCalendar.MINUTE, minutes);
            cal.add(GregorianCalendar.SECOND, seconds);
            Date result = new Date(cal.getTimeInMillis());
            return result;
        }
    }
}
