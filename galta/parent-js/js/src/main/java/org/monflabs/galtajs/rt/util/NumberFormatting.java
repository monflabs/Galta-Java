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
package org.monflabs.galtajs.rt.util;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.monflabs.galtajs.external.org_mozilla_javascript.DToA;
import org.monflabs.galtajs.external.org_mozilla_javascript.v8dtoa.FastDtoa;
import org.monflabs.galtajs.rt.RuntimeUtil;

/**
 * Utilities for Number formatting.
 */
public class NumberFormatting {
	
	public static final String numberToString(Number _this) {
		return numberToString(_this,10);
	}
	public static final String numberToString(Number _this, int radix) {
		return numberToString(_this,radix,false);
	}
	public static final String numberToString(Number _this, int radix, boolean suffix) {
		if(_this instanceof Integer i) {
			return Integer.toString(i,radix);
		} else if(_this instanceof Long l) {
			return Long.toString(l,radix);
		} else if(_this instanceof Double || _this instanceof Float) {
			return numberToString(_this.doubleValue(),radix);
		} else if(_this instanceof BigDecimal bd) {
			if(radix==10) {
				return suffix ? bd.toString()+'m' : bd.toString();
			} else {
				return suffix ? numberToString(_this.doubleValue(),radix)+'m' : numberToString(_this.doubleValue(),radix);
			}
		} else if(_this instanceof BigInteger bi) {
			return suffix ? bi.toString(radix)+'n' : bi.toString(radix);
		} else {
			return Long.toString(_this.longValue(),radix);
		}
	}

	public static final String numberToString(double d) {
		return numberToString(d,10);
	}
	public static final String numberToString(double d, int radix) {
		if (radix < 2 || radix > 36) {
            throw RuntimeUtil.rangeError("Invalid radix {0}", radix);
        }
		// Simple int formatting
        int i = (int)d; 
        if(i==d) {
        	return Integer.toString(i,radix);
        }
        
        // Double formatting
		if (Double.isNaN(d)) {
			return "NaN";
		}
		if (d == Double.POSITIVE_INFINITY) {
			return "Infinity";
		}
		if (d == Double.NEGATIVE_INFINITY) {
			return "-Infinity";
		}
        if (radix != 10) {
        	return DToA.JS_dtobasestr(radix, d);
        } else {
        // V8 FastDtoa can't convert all numbers, so try it first but fall back to old DToA in case it fails
	        String result = FastDtoa.numberToString(d);
	        if (result != null) {
	            return result;
	        }
	        StringBuilder buffer = new StringBuilder();
	        DToA.JS_dtostr(buffer, DToA.DTOSTR_STANDARD, 0, d);
	        return buffer.toString();
        }
	}
	
	public static final String toFixed(Number _this, int prec) {
		return toFixed(_this.doubleValue(), prec);
	}
	public static final String toFixed(double d, int prec) {
        if (prec < 0 || prec > 100) {
            throw RuntimeUtil.rangeError("Precision {0} out of range.", prec);
        }
		if (Double.isNaN(d)) {
			return "NaN";
		}
		if (d == Double.POSITIVE_INFINITY) {
			return "Infinity";
		}
		if (d == Double.NEGATIVE_INFINITY) {
			return "-Infinity";
		}
        StringBuilder b = new StringBuilder();
        DToA.JS_dtostr(b, DToA.DTOSTR_FIXED, prec, d);
        return b.toString();	        		
	}
	
	public static final String toExponential(Number _this, int fracDigits) {
		return toExponential(_this.doubleValue(),fracDigits);
	}
	public static final String toExponential(double d, int fracDigits) {
		if (d == Double.POSITIVE_INFINITY) {
			return "Infinity";
		}
		if (d == Double.NEGATIVE_INFINITY) {
			return "-Infinity";
		}
		if (Double.isNaN(d)) {
			return "NaN";
		}
        if (fracDigits!=Integer.MIN_VALUE && ( fracDigits < 0 || fracDigits > 100)) {
            throw RuntimeUtil.rangeError("Invalid fraction digits {0}", fracDigits);
        }
        StringBuilder b = new StringBuilder();
        if (fracDigits<0) {
            DToA.JS_dtostr(b, DToA.DTOSTR_STANDARD_EXPONENTIAL, 0, d);
        } else {
            DToA.JS_dtostr(b, DToA.DTOSTR_EXPONENTIAL, 1 + fracDigits, d);
        }
        return b.toString();
	}
	
	public static final String toPrecision(Number _this, int prec) {
		return toPrecision(_this.doubleValue(),prec);
	}
	public static final String toPrecision(double d, int prec) {
        if (prec == Integer.MIN_VALUE) {
        	return numberToString(d, 10);
        }
		if (d == Double.POSITIVE_INFINITY) {
			return "Infinity";
		}
		if (d == Double.NEGATIVE_INFINITY) {
			return "-Infinity";
		}
		if (Double.isNaN(d)) {
			return "NaN";
		}
        if (prec < 1 || prec > 100) {
            throw RuntimeUtil.rangeError("Invalid precision {0}", prec);
        }
        StringBuilder b = new StringBuilder();
        DToA.JS_dtostr(b, DToA.DTOSTR_PRECISION, prec, d);
        return b.toString();	        		
	}
}