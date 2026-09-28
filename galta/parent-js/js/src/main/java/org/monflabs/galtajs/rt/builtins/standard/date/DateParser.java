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

import java.text.ParseException;

import org.monflabs.util.StringUtil;
import org.monflabs.util.datetime.DateTimeParts;
import org.monflabs.util.datetime.ISO8601;


/**
 */
public class DateParser {
	
    public static double parseDate(String str) {
    	if(StringUtil.isNotEmpty(str)) {
	    	try {
		    	DateTimeParts parts = ISO8601.parseDateTimeParts(str);
		    	if(parts.getOffset()!=null) {
		    		return DateUtil.timeClip(parts.toEpochMilli());
		    	}
		    	// Date Time String Format: a date-TIME string with no explicit
		    	// UTC offset is interpreted as LOCAL time (unlike a date-only
		    	// string, which is always UTC - see the parseDateParts()
		    	// fallback below).
		    	return DateUtil.localDate(parts.getYear(), parts.getMonth()-1, parts.getDay(), parts.getHour(), parts.getMinute(), parts.getSecond(), parts.getMillis());
	    	} catch(ParseException ex) {}
	    	try {
	    		// Date-only ISO form (e.g. "1970", "1970-01", "1970-01-01" -
	    		// month/day default to 1 when absent) - always UTC.
		    	DateTimeParts parts = ISO8601.parseDateParts(str);
		    	return DateUtil.timeClip(parts.toEpochMilli());
	    	} catch(ParseException ex) {}
    	}
    	return parseNonStandardDate(str);
    }

    private static double parseNonStandardDate(String s) {
        int year = -1;
        int mon = -1;
        int mday = -1;
        int hour = -1;
        int min = -1;
        int sec = -1;
        int milli = -1;
        char c = 0;
        char si = 0;
        int i = 0;
        int n = -1;
        double tzoffset = -1;
        char prevc = 0;
        int limit = 0;
        boolean seenplusminus = false;

        limit = s.length();
        while (i < limit) {
            c = s.charAt(i);
            i++;
            if (c <= ' ' || c == ',' || c == '-') {
                if (i < limit) {
                    si = s.charAt(i);
                    if (c == '-' && '0' <= si && si <= '9') {
                        prevc = c;
                    }
                }
                continue;
            }
            if (c == '(') {
                /* comments) */
                int depth = 1;
                while (i < limit) {
                    c = s.charAt(i);
                    i++;
                    if (c == '(') depth++;
                    else if (c == ')') if (--depth <= 0) break;
                }
                continue;
            }
            if ('0' <= c && c <= '9') {
                n = c - '0';
                while (i < limit && '0' <= (c = s.charAt(i)) && c <= '9') {
                    n = n * 10 + c - '0';
                    i++;
                }

                /* allow TZA before the year, so
                 * 'Wed Nov 05 21:49:11 GMT-0800 1997'
                 * works */

                /* uses of seenplusminus allow : in TZA, so Java
                 * no-timezone style of GMT+4:30 works
                 */
                if ((prevc == '+' || prevc == '-') /*  && year>=0 */) {
                    /* make ':' case below change tzoffset */
                    seenplusminus = true;

                    /* offset */
                    if (n < 24) n = n * 60; /* EG. "GMT-3" */
                    else n = n % 100 + n / 100 * 60; /* eg "GMT-0430" */
                    if (prevc == '+') /* plus means east of GMT */ n = -n;
                    if (tzoffset != 0 && tzoffset != -1) return Double.NaN;
                    tzoffset = n;
                } else if(prevc=='.') {
                    milli = n;
                } else if (n > 70 || (prevc == '/' && mon >= 0 && mday >= 0 && year < 0)) {
                    if (year >= 0) return Double.NaN;
                    else if (c <= ' ' || c == ',' || c == '/' || i >= limit)
                        year = n < 100 ? n + 1900 : n;
                    else return Double.NaN;
                } else if (c == ':') {
                    if (hour < 0) hour = n;
                    else if (min < 0) min = n;
                    else return Double.NaN;
                } else if (c == '/') {
                    if (mon < 0) mon = n - 1;
                    else if (mday < 0) mday = n;
                    else return Double.NaN;
                } else if (i < limit && c != ',' && c > ' ' && c != '-' && c!= '.') {
                    return Double.NaN;
                } else if (seenplusminus && n < 60) {
                    /* handle GMT-3:30 */
                    if (tzoffset < 0) tzoffset -= n;
                    else tzoffset += n;
                } else if (hour >= 0 && min < 0) {
                    min = n;
                } else if (min >= 0 && sec < 0) {
                    sec = n;
                } else if (mday < 0) {
                    mday = n;
                } else {
                    return Double.NaN;
                }
                prevc = 0;
            } else if (c == '/' || c == ':' || c == '+' || c == '-' || c == '.') {
                prevc = c;
            } else {
                int st = i - 1;
                while (i < limit) {
                    c = s.charAt(i);
                    if (!(('A' <= c && c <= 'Z') || ('a' <= c && c <= 'z'))) break;
                    i++;
                }
                int letterCount = i - st;
                if (letterCount < 2) return Double.NaN;
                /*
                 * Use ported code from jsdate.c rather than the locale-specific
                 * date-parsing code from Java, to keep js and rhino consistent.
                 * Is this the right strategy?
                 */
                String wtb =
                        "am;pm;"
                                + "monday;tuesday;wednesday;thursday;friday;"
                                + "saturday;sunday;"
                                + "january;february;march;april;may;june;"
                                + "july;august;september;october;november;december;"
                                + "gmt;ut;utc;est;edt;cst;cdt;mst;mdt;pst;pdt;";
                int index = 0;
                for (int wtbOffset = 0; ; ) {
                    int wtbNext = wtb.indexOf(';', wtbOffset);
                    if (wtbNext < 0) return Double.NaN;
                    if (wtb.regionMatches(true, wtbOffset, s, st, letterCount)) break;
                    wtbOffset = wtbNext + 1;
                    ++index;
                }
                if (index < 2) {
                    /*
                     * AM/PM. Count 12:30 AM as 00:30, 12:30 PM as
                     * 12:30, instead of blindly adding 12 if PM.
                     */
                    if (hour > 12 || hour < 0) {
                        return Double.NaN;
                    } else if (index == 0) {
                        // AM
                        if (hour == 12) hour = 0;
                    } else {
                        // PM
                        if (hour != 12) hour += 12;
                    }
                } else if ((index -= 2) < 7) {
                    // ignore week days
                } else if ((index -= 7) < 12) {
                    // month
                    if (mon < 0) {
                        mon = index;
                    } else {
                        return Double.NaN;
                    }
                } else {
                    index -= 12;
                    // timezones
                    switch (index) {
                        case 0 /* gmt */:
                            tzoffset = 0;
                            break;
                        case 1 /* ut */:
                            tzoffset = 0;
                            break;
                        case 2 /* utc */:
                            tzoffset = 0;
                            break;
                        case 3 /* est */:
                            tzoffset = 5 * 60;
                            break;
                        case 4 /* edt */:
                            tzoffset = 4 * 60;
                            break;
                        case 5 /* cst */:
                            tzoffset = 6 * 60;
                            break;
                        case 6 /* cdt */:
                            tzoffset = 5 * 60;
                            break;
                        case 7 /* mst */:
                            tzoffset = 7 * 60;
                            break;
                        case 8 /* mdt */:
                            tzoffset = 6 * 60;
                            break;
                        case 9 /* pst */:
                            tzoffset = 8 * 60;
                            break;
                        case 10 /* pdt */:
                            tzoffset = 7 * 60;
                            break;
                    }
                }
            }
        }
        if (year < 0 || mon < 0 || mday < 0) return Double.NaN;
        if (sec < 0) sec = 0;
        if (min < 0) min = 0;
        if (hour < 0) hour = 0;
        if (milli < 0) milli = 0;

        if (tzoffset == -1) {
            double msec = DateUtil.date(year, mon, mday, hour, min, sec, milli);
        	return msec;
        }
        double msec = DateUtil.UTC(year, mon, mday, hour, min, sec, milli);
        return msec + tzoffset * (60*1000); // msPerMinute
    }
 }
