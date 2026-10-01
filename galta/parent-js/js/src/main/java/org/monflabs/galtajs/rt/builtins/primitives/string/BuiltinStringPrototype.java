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
package org.monflabs.galtajs.rt.builtins.primitives.string;

import org.monflabs.galtajs.rt.builtins.BuiltinUtil;
import java.text.Collator;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.JSRuntimeException;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.BasePrimitivePrototype;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.rt.builtins.standard.regexp.BuiltinRegExpIterator;
import org.monflabs.galtajs.rt.builtins.standard.regexp.RegExp;
import org.monflabs.galtajs.rt.builtins.standard.regexp.RegExpConstructor;
import org.monflabs.galtajs.rt.util.PrimitivePropertyMap;
import org.monflabs.util.StringUtil;

/**
 * Eqv of the JavaScript String prototype.
 */
public class BuiltinStringPrototype extends BasePrimitivePrototype {

	public static BuiltinStringPrototype get(JSEnvironment env) {
		BuiltinStringPrototype proto = (BuiltinStringPrototype)env.getRegisteredPrototype(BuiltinStringPrototype.class);
		if(proto==null) {
			proto = new BuiltinStringPrototype(env);
			env.registerPrototype(BuiltinStringPrototype.class,proto);
		}
		return proto;
	}

	private BuiltinStringPrototype(JSEnvironment env) {
		super(env);
		setOwnProperty("length",0,PropertyDescriptor.DESC_READONLY_HIDDEN_PROP); // String.prototype is supposed to be an an empty string
		setOwnMethod(new Method(env,MethodId.at,1));
		setOwnMethod(new Method(env,MethodId.charAt,1));
		setOwnMethod(new Method(env,MethodId.charCodeAt,1));
		setOwnMethod(new Method(env,MethodId.codePointAt,1));
		setOwnMethod(new Method(env,MethodId.concat,1));
		setOwnMethod(new Method(env,MethodId.endsWith,1));
		setOwnMethod(new Method(env,MethodId.includes,1));
		setOwnMethod(new Method(env,MethodId.indexOf,1));
		setOwnMethod(new Method(env,MethodId.isWellFormed,0));
		setOwnMethod(new Method(env,MethodId.lastIndexOf,1));
		setOwnMethod(new Method(env,MethodId.localeCompare,1));
		setOwnMethod(new Method(env,MethodId.match,1));
		setOwnMethod(new Method(env,MethodId.matchAll,1));
		setOwnMethod(new Method(env,MethodId.normalize,0));
		setOwnMethod(new Method(env,MethodId.padEnd,1));
		setOwnMethod(new Method(env,MethodId.padStart,1));
		setOwnMethod(new Method(env,MethodId.repeat,1));
		setOwnMethod(new Method(env,MethodId.replace,2));
		setOwnMethod(new Method(env,MethodId.replaceAll,2));
		setOwnMethod(new Method(env,MethodId.search,1));
		setOwnMethod(new Method(env,MethodId.slice,2));
		setOwnMethod(new Method(env,MethodId.split,2));
		setOwnMethod(new Method(env,MethodId.startsWith,1));
		setOwnMethod(new Method(env,MethodId.substring,2));
		setOwnMethod(new Method(env,MethodId.toLocaleLowerCase,0));
		setOwnMethod(new Method(env,MethodId.toLocaleUpperCase,0));
		setOwnMethod(new Method(env,MethodId.toLowerCase,0));
		setOwnMethod(new Method(env,MethodId.toString,0));
		setOwnMethod(new Method(env,MethodId.toUpperCase,0));
		setOwnMethod(new Method(env,MethodId.toWellFormed,0));
		setOwnMethod(new Method(env,MethodId.trim,0));
		setOwnMethod(new Method(env,MethodId.trimEnd,0));
		setOwnAlias(MethodId.trimEnd.id,MethodId.trimRight.id);
		setOwnMethod(new Method(env,MethodId.trimStart,0));			
		setOwnAlias(MethodId.trimStart.id,MethodId.trimLeft.id);
		setOwnMethod(new Method(env,MethodId.valueOf,0));
		
		// Symbols
		setOwnMethod(new Method(env,MethodId.iterator,0));
		
		// Deprecated
		if(env.isDeprecatedApis()) {
			setOwnMethod(new Method(env,MethodId.substr,2));
			
			setOwnMethod(new Method(env,MethodId.anchor,1));
			setOwnMethod(new Method(env,MethodId.big,0));
			setOwnMethod(new Method(env,MethodId.blink,0));
			setOwnMethod(new Method(env,MethodId.bold,0));
			setOwnMethod(new Method(env,MethodId.fixed,0));
			setOwnMethod(new Method(env,MethodId.fontcolor,1));
			setOwnMethod(new Method(env,MethodId.fontsize,1));
			setOwnMethod(new Method(env,MethodId.italics,0));
			setOwnMethod(new Method(env,MethodId.link,1));
			setOwnMethod(new Method(env,MethodId.small,0));
			setOwnMethod(new Method(env,MethodId.strike,0));
			setOwnMethod(new Method(env,MethodId.sub,0));
			setOwnMethod(new Method(env,MethodId.sup,0));
		}
	}
	
	@Override
	public String getClassName() {
		return BuiltinStringConstructor.CLASSNAME;
	}
	
	@Override
	public Class<?> getNativeClass() {
		return String.class;
	}
	
	private static enum MethodId {
		at,
		charAt,
		charCodeAt,
		codePointAt,
		concat,
		endsWith,
		includes,
		indexOf,
		isWellFormed,
		lastIndexOf,
		localeCompare,
		match,
		matchAll,
		normalize,
		padEnd,
		padStart,
		repeat,
		replace,
		replaceAll,
		search,
		slice,
		split,
		startsWith,
		substring,
		toLocaleLowerCase,
		toLocaleUpperCase,
		toLowerCase,
		toString,
		toWellFormed,
		toUpperCase,
		trim,
		trimEnd,
		trimStart,
		trimLeft,
		trimRight,
		valueOf,
		// Symbols
		iterator(Symbol.ITERATOR),
		// Deprecated
		substr,
		
	    anchor,
	    big,
	    blink,
	    bold,
	    fixed,
	    fontcolor,
	    fontsize,
	    italics,
	    link,
	    small,
	    strike,
	    sub,
	    sup,	
	  ;
		Object id;
		MethodId() {
			this.id = name();
		}
		MethodId(Symbol id) {
			this.id = id;
		}
	}
	
	// Collators per locale, cached per thread (a Collator is not thread-safe):
	// localeCompare is the usual sort comparator, called O(n log n) times.
	private static final ThreadLocal<Map<Locale,Collator>> COLLATORS = ThreadLocal.withInitial(HashMap::new);

	private static Collator collator(Locale locale) {
		return COLLATORS.get().computeIfAbsent(locale, Collator::getInstance);
	}

	private final static class Method extends BaseMethod {
		private MethodId methodId;

		private Method(JSEnvironment env, MethodId methodId, int length) {
			super(env,methodId.id,length);
			this.methodId = methodId;
		}

		// Sentinel: "receiver has no such Symbol method, proceed with the
		// native string-only algorithm" - distinct from a legitimate result
		// of null/undefined from an actual dispatched call.
		private static final Object NO_DISPATCH = new Object();

		// Per spec, match/matchAll/replace/replaceAll/search/split all first
		// try a generic Symbol-keyed method on their argument (GetMethod) -
		// not a hardcoded native RegExp shortcut - so a custom object with
		// its own e.g. Symbol.replace is honored, and a real RegExp's
		// INHERITED method is reached via this SAME generic path (not
		// bypassed by a Java `instanceof RegExp` check).
		private Object dispatchSymbolMethod(Object receiver, Symbol symbol, Object... args) {
			if(RuntimeUtil.isNullOrUndefined(receiver)) {
				return NO_DISPATCH;
			}
			if(RuntimeUtil.isPrimitiveValue(getEnvironment(), receiver)) {
				return NO_DISPATCH;
			}
			JSAccessor acc = getEnvironment().getAccessor(receiver);
			Object method = acc.getProperty(receiver, symbol, RuntimeUtil.UNDEFINED);
			if(RuntimeUtil.isNullOrUndefined(method)) {
				return NO_DISPATCH;
			}
			if(!(method instanceof Callable c && c.isCallable())) {
				throw notCallableError(receiver, symbol, method);
			}
			return c.call(receiver, args);
		}

		// Matches the established GaltaJS "property is not callable" message
		// convention (e.g. `Cannot call property Symbol.matchAll in object
		// [object Object]. It is not a function, it is "number".`). The
		// receiver is deliberately rendered as the generic "[object Object]"
		// tag rather than via RuntimeUtil.toString(receiver) - per spec,
		// GetMethod's abrupt completion must NOT invoke the receiver's own
		// (possibly poisoned/throwing) toString().
		private JSRuntimeException notCallableError(Object receiver, Symbol symbol, Object method) {
			String type = method == null ? "null" :
				RuntimeUtil.isPrimitiveValue(getEnvironment(), method) ?
					RuntimeUtil.typeof(getEnvironment(), method) : "object";
			return RuntimeUtil.typeError(
				"Cannot call property {0} in object {1}. It is not a function, it is \"{2}\".",
				symbol.getDescription(),
				"[object Object]",
				type
			);
		}

		// Invoke(receiver, symbol, args): unlike dispatchSymbolMethod, this
		// REQUIRES the method to exist and be callable, throwing TypeError
		// otherwise - used for the "RegExpCreate(...) then Invoke(rx,
		// @@method, ...)" fallback step of match/matchAll/search, which must
		// still go through a real property lookup+call (not a Java-native
		// shortcut) so a deleted/overridden RegExp.prototype[@@method] is
		// observed even on a freshly-constructed RegExp.
		private Object invokeMethodRequired(Object receiver, Symbol symbol, Object... args) {
			JSAccessor acc = getEnvironment().getAccessor(receiver);
			Object method = acc.getProperty(receiver, symbol, RuntimeUtil.UNDEFINED);
			if(!(method instanceof Callable c && c.isCallable())) {
				throw notCallableError(receiver, symbol, method);
			}
			return c.call(receiver, args);
		}

		// IsRegExp(v): duck-typed via a truthy (possibly-inherited)
		// Symbol.match property - not a hardcoded `instanceof RegExp` check -
		// falling back to `instanceof RegExp` only when Symbol.match is
		// absent entirely.
		private boolean isRegExpGeneric(Object v) {
			if(RuntimeUtil.isPrimitiveValue(getEnvironment(), v)) {
				return false;
			}
			JSAccessor acc = getEnvironment().getAccessor(v);
			Object matcher = acc.getProperty(v, Symbol.MATCH, RuntimeUtil.NOT_AVAILABLE);
			if(matcher!=RuntimeUtil.NOT_AVAILABLE) {
				return RuntimeUtil.toBoolean(getEnvironment(), matcher);
			}
			return v instanceof RegExp;
		}

		// Shared by replaceAll/matchAll: if `v` IsRegExp, its "flags"
		// property (RequireObjectCoercible'd, then ToString'd) must contain
		// "g", or throw - checked via the real property (observable to a
		// poisoned flags getter/toString), not a native boolean flag.
		private void requireGlobalFlagIfRegExp(Object v, String methodName) {
			if(!isRegExpGeneric(v)) {
				return;
			}
			JSAccessor acc = getEnvironment().getAccessor(v);
			Object flags = acc.getProperty(v, "flags", RuntimeUtil.UNDEFINED);
			if(RuntimeUtil.isNullOrUndefined(flags)) {
				throw RuntimeUtil.typeError("{0} called with a RegExp whose flags is null or undefined", methodName);
			}
			String sFlags = RuntimeUtil.toString(getEnvironment(), flags);
			if(sFlags.indexOf('g')<0) {
				throw RuntimeUtil.typeError("{0} called with a non-global RegExp argument", methodName);
			}
		}

		@Override
		protected Object invoke(final Object obj, final Object[] args) {
	    	if(RuntimeUtil.isNullOrUndefined(obj)) {
	    		throw nullThis();
	    	}

			switch(methodId) {
	        	case at -> {
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
        	        int index = paramInt(args,0,0);
        	        if(index<0) {
        	        	index += _this.length();
        	        }
	        	    if (index>=0 && index<_this.length()) {
	        	    	return String.valueOf(_this.charAt(index));
	        	    }
	        	    return RuntimeUtil.UNDEFINED; // why undefined while charAt return empty string?
	        	}
	        	case charAt -> {
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
        	        int index = paramInt(args,0,0);
	        	    if (index>=0 && index<_this.length()) {
	        	    	return String.valueOf(_this.charAt(index));
	        	    }
	        	    return "";
	        	}
	        	case charCodeAt -> {
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
        	        int index = paramInt(args,0,0);
	        	    if (index>=0 && index<_this.length()) {
	        	    	return (int)_this.charAt(index);
	        	    }
	        	    return Double.NaN;
	        	}
	        	case codePointAt -> {
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
        	        int index = paramInt(args,0,0);
	        	    if (index>=0 && index<_this.length()) {
	        	    	return (int)_this.codePointAt(index);
	        	    }
	        	    // Per spec §22.1.3.4: return undefined for out-of-range position
	        	    return RuntimeUtil.UNDEFINED;
	        	}
	        	case concat -> {
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
	        		StringBuilder b = new StringBuilder(_this.length());
	        		b.append(_this);
	        		for(int i=0; i<args.length; i++) {
	        			b.append(RuntimeUtil.toString(getEnvironment(),args[i]));
	        		}
	        		return b.toString();
	        	}
	        	case endsWith -> {
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
	        		Object rawSearch = param(args, 0, RuntimeUtil.UNDEFINED);
	        		if(isRegExpGeneric(rawSearch)) {
	        			throw RuntimeUtil.typeError("String.prototype.endsWith called with a RegExp argument");
	        		}
        	        String s = paramString(args, 0, null);
        	        if(s==null) {
        	        	return false;
        	        }
        	        // Per spec §22.1.3.7: endPosition limits how much of _this to consider
        	        int end = _this.length();
        	        if(args.length > 1 && args[1] != RuntimeUtil.UNDEFINED) {
        	        	end = Math.max(0, Math.min(paramInt(args, 1), end));
        	        }
        	        String subject = end == _this.length() ? _this : _this.substring(0, end);
        	        return subject.endsWith(s);
	        	}
	        	case includes -> {
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
	        		Object rawSearch = param(args, 0, RuntimeUtil.UNDEFINED);
	        		if(isRegExpGeneric(rawSearch)) {
	        			throw RuntimeUtil.typeError("String.prototype.includes called with a RegExp argument");
	        		}
        	        String s = paramString(args, 0, null);
        	        if(s==null) {
        	        	return false;
        	        }
        	        int index = paramInt(args,1,0);
        	        return _this.indexOf(s,index)>=0;
	        	}
	        	case indexOf -> {
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
	        		// Per spec, searchString is genuinely ToString()-converted -
	        		// no special-casing of a missing/undefined argument (unlike
	        		// includes/startsWith/endsWith's optional-with-default
	        		// params): "foo".indexOf() must search for the literal
	        		// string "undefined", not short-circuit to -1 (confirmed
	        		// via indexOf/searchstring-tostring.js:
	        		// "__undefined__".indexOf(undefined) === 2).
        	        String s = RuntimeUtil.toString(getEnvironment(), param(args, 0, RuntimeUtil.UNDEFINED));
        	        int index = paramInt(args,1,0);
        	        return _this.indexOf(s,index);
	        	}
	        	case isWellFormed -> {
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
        	        return isWellFormed(_this);
	        	}
	        	case lastIndexOf -> {
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
	        		// Same "always genuinely ToString(searchString)" rationale
	        		// as indexOf above.
        	        String s = RuntimeUtil.toString(getEnvironment(), param(args, 0, RuntimeUtil.UNDEFINED));
        	        Object p1 = param(args,1,null);
        	        int index;
        	        if(p1==null) {
        	        	index = Integer.MAX_VALUE;
        	        } else {
        	        	// ToNumber must be invoked exactly once - p1 may be an object
        	        	// with an observable valueOf(), so the NaN check below cannot
        	        	// re-derive the number from the raw argument a second time
        	        	// (confirmed via lastIndexOf/S15.5.4.8_A1_T10.js:
        	        	// `{valueOf(){return NaN}}` as the position argument must
        	        	// still search from the end, not position 0 - the previous
        	        	// `p1 instanceof Number` check only caught an ALREADY-numeric
        	        	// argument, never one needing ToNumber conversion first).
        	        	Number n1 = RuntimeUtil.toNumber(getEnvironment(), p1);
        	        	index = RuntimeUtil.isNaN(n1) ? Integer.MAX_VALUE : RuntimeUtil.toInt(getEnvironment(), n1);
        	        }
        	        return _this.lastIndexOf(s,index);
	        	}
	        	case localeCompare -> {
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
	        		// Per spec, `that` is genuinely ToString()-converted - a
	        		// missing/undefined argument becomes the literal string
	        		// "undefined", not "" (confirmed via
	        		// localeCompare/15.5.4.9_3.js).
	        		String that = RuntimeUtil.toString(getEnvironment(), param(args, 0, RuntimeUtil.UNDEFINED));
	        		Locale locale = findLocale(paramString(args, 1, null));
	        		// Per spec, localeCompare "must treat Strings that are
	        		// canonically equivalent according to the Unicode standard as
	        		// identical" (e.g. combining-mark sequences that differ only in
	        		// composition or in an order canonical reordering makes
	        		// equivalent). A plain String.compareTo() is a raw UTF-16
	        		// code-unit comparison with no such awareness, and Collator.compare()
	        		// alone isn't sufficient either - it doesn't canonically reorder
	        		// combining marks by combining class the way Normalizer does, so two
	        		// differently-ORDERED (but canonically equivalent) combining
	        		// sequences can still compare unequal. Normalizing both operands to
	        		// NFC first (which performs that reordering) before handing them to
	        		// a Collator (Locale.ROOT when no locale is explicitly requested)
	        		// satisfies both requirements.
	        		// isNormalized() is a cheap scan (no allocation) that
	        		// short-circuits the actual normalize() call for the
	        		// common case (ASCII/already-NFC text) - this is the
	        		// idiomatic sort comparator (arr.sort((a,b)=>a.localeCompare(b))),
	        		// so it runs O(n log n) times per sort.
	        		String thisNorm = Normalizer.isNormalized(_this, Form.NFC) ? _this : Normalizer.normalize(_this, Form.NFC);
	        		String thatNorm = Normalizer.isNormalized(that, Form.NFC) ? that : Normalizer.normalize(that, Form.NFC);
	        		return collator(locale!=null ? locale : Locale.ROOT).compare(thisNorm,thatNorm);
	        	}
	    		case match -> {
	    			// Per spec: RequireObjectCoercible(this) already happened
	    			// above - `this` is NOT ToString'd until after the dispatch
	    			// check below, so a poisoned `this.toString()` isn't invoked
	    			// when a Symbol.match-bearing regexp handles the call instead.
	    			Object regexp = param(args,0,RuntimeUtil.UNDEFINED);
	    			Object dispatched = dispatchSymbolMethod(regexp, Symbol.MATCH, obj);
	    			if(dispatched!=NO_DISPATCH) {
	    				return dispatched;
	    			}
	    			final String _this = RuntimeUtil.toString(getEnvironment(),obj);
	    			RegExpConstructor ctor = (RegExpConstructor)getEnvironment().getStandardObjects().getConstructor(RegExpConstructor.CLASSNAME);
	    			RegExp re = (RegExp)ctor.constructObject(new Object[] {regexp});
	    			return invokeMethodRequired(re, Symbol.MATCH, _this);
	        	}
	        	case matchAll -> {
	    			Object regexp = param(args,0,RuntimeUtil.UNDEFINED);

	    			// If regexp is neither undefined nor null
	    			if(!RuntimeUtil.isNullOrUndefined(regexp)) {
	    				requireGlobalFlagIfRegExp(regexp, "String.prototype.matchAll");
	    				Object dispatched = dispatchSymbolMethod(regexp, Symbol.MATCH_ALL, obj);
	    				if(dispatched!=NO_DISPATCH) {
	    					return dispatched;
	    				}
	    			}

	    			final String _this = RuntimeUtil.toString(getEnvironment(),obj);
	    			// No @@matchAll property - RegExpCreate(regexp, "g"). Pass
	    			// `regexp` itself (not pre-ToString()-converted) - the
	    			// RegExp constructor already special-cases a genuinely
	    			// undefined pattern as the empty source "(?:)", which is
	    			// NOT the same as the literal 4-character string
	    			// "undefined" that ToString(undefined) would produce
	    			// (confirmed via matchAll/regexp-is-undefined.js:
	    			// "a".matchAll(undefined) must match the empty pattern at
	    			// every position, not search for the literal text
	    			// "undefined").
	    			RegExpConstructor ctor = (RegExpConstructor)getEnvironment().getStandardObjects().getConstructor(RegExpConstructor.CLASSNAME);
	    			RegExp rx = (RegExp)ctor.constructObject(new Object[] {regexp, "g"});
	    			return invokeMethodRequired(rx, Symbol.MATCH_ALL, _this);
	        	}
	        	case normalize -> {
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
	        		String s = paramString(args,0, "NFC");
	        		Normalizer.Form form = 
	                switch (s) {
	                	case "NFC" -> Form.NFC;
	                	case "NFD" -> Form.NFD;
	                	case "NFKC" -> Form.NFKC;
	                	case "NFKD" -> Form.NFKD;
	                	default ->
	                    	throw RuntimeUtil.rangeError("Invalid normalization form {0}", s);
	                };
	                return Normalizer.normalize(_this,form);
	        	}
	        	case padEnd -> {
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
	        		int len = paramInt(args, 0, 0);
	        		String filler = paramString(args, 1, " ");
	        		if(len<=_this.length()) {
	        			return _this;
	        		}
	        		if(StringUtil.isEmpty(filler)) {
	        			return _this;
	        		}
	        		StringBuilder b = new StringBuilder(len);
	        		b.append(_this);
	        		if(filler.length()==1) {
	        			char c = filler.charAt(0);
	        			char[] fill = new char[len-b.length()];
	        			java.util.Arrays.fill(fill,c);
	        			b.append(fill);
	        		} else {
		        		while(b.length()<len) {
		        			int avail = len-b.length();
		        			if(avail<filler.length()) {
		        				b.append(filler,0,avail);
		        			} else {
		        				b.append(filler);
		        			}
		        		}
	        		}
	        		return b.toString();
	        	}
	        	case padStart -> {
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
	        		int len = paramInt(args, 0, 0);
	        		String filler = paramString(args, 1, " ");
	        		if(len<=_this.length()) {
	        			return _this;
	        		}
	        		if(StringUtil.isEmpty(filler)) {
	        			return _this;
	        		}
	        		int totalLen = len;
	        		len -= _this.length();
	        		StringBuilder b = new StringBuilder(totalLen);
	        		if(filler.length()==1) {
	        			char c = filler.charAt(0);
	        			char[] fill = new char[len];
	        			java.util.Arrays.fill(fill,c);
	        			b.append(fill);
	        		} else {
		        		while(b.length()<len) {
		        			int avail = len-b.length();
		        			if(avail<filler.length()) {
		        				b.append(filler,0,avail);
		        			} else {
		        				b.append(filler);
		        			}
		        		}
	        		}
	        		b.append(_this);
	        		return b.toString();
	        	}
	        	case repeat -> {
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
	        		double d = RuntimeUtil.toDouble(getEnvironment(), param(args, 0, 0.0));
	        		if(Double.isInfinite(d) || d < 0) {
	        			throw RuntimeUtil.rangeError("Invalid repeat count {0}", d);
	        		}
	        		int len = Double.isNaN(d) ? 0 : (int)d;
	        		if(len==0 || _this.length()==0) {
	        			return "";
	        		}
	        		String result = _this.repeat(len);
            		if(result==_this) {
            			return RuntimeUtil.objectAsPrimitive(getEnvironment(),result);
            		}
            		return result;
	        	}
	        	case replace -> {
	        		Object pattern = param(args, 0, RuntimeUtil.UNDEFINED);
	        		Object substr = param(args, 1, RuntimeUtil.UNDEFINED);
	        		Object dispatched = dispatchSymbolMethod(pattern, Symbol.REPLACE, obj, substr);
	        		if(dispatched!=NO_DISPATCH) {
	        			return dispatched;
	        		}
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
        			return replaceString(_this, pattern, substr, true);
	        	}
	        	case replaceAll -> {
	        		Object pattern = param(args, 0, RuntimeUtil.UNDEFINED);
	        		Object substr = param(args, 1, RuntimeUtil.UNDEFINED);
	        		if(!RuntimeUtil.isNullOrUndefined(pattern)) {
	        			requireGlobalFlagIfRegExp(pattern, "String.prototype.replaceAll");
	        			Object dispatched = dispatchSymbolMethod(pattern, Symbol.REPLACE, obj, substr);
	        			if(dispatched!=NO_DISPATCH) {
	        				return dispatched;
	        			}
	        		}
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
        			return replaceString(_this, pattern, substr, false);
	        	}
	        	case search -> {
	        		Object pattern = param(args, 0, RuntimeUtil.UNDEFINED);
	        		Object dispatched = dispatchSymbolMethod(pattern, Symbol.SEARCH, obj);
	        		if(dispatched!=NO_DISPATCH) {
	        			return dispatched;
	        		}
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
	        		RegExpConstructor ctor = (RegExpConstructor)getEnvironment().getStandardObjects().getConstructor(RegExpConstructor.CLASSNAME);
	        		RegExp re = (RegExp)ctor.constructObject(new Object[] {pattern});
        			return invokeMethodRequired(re, Symbol.SEARCH, _this);
	        	}
	        	case slice -> {
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
	        		int begin = paramInt(args, 0, 0);
	        		if(begin<0) {
	        			begin = _this.length() + begin;
	        		}
	        		if(begin>=_this.length()) {
	        			return "";
	        		}
	        		begin = Math.max(begin, 0);
	        		int end = paramInt(args, 1, _this.length() );
	        		if(end<0) {
	        			end = _this.length() + end;
	        		}
	        		end = Math.min(end, _this.length());
	        		
	        		if(end<=begin) {
	        			return "";
	        		}
            		String result = _this.substring(begin,end);
            		if(result==_this) {
            			return RuntimeUtil.objectAsPrimitive(getEnvironment(),result);
            		}
            		return result;
	        	}
	        	case split -> {
	        		JSArray a = JSArray.create(getEnvironment());
	        		// A missing separator is undefined (not null, which is the "null" separator)
	        		Object sep = param(args, 0, RuntimeUtil.UNDEFINED);
	        		Object pLimit = param(args,1,RuntimeUtil.UNDEFINED);
	        		// Per spec, a custom @@split method is called with the RAW
	        		// limit argument, unconverted - ToUint32(limit) only
	        		// happens inside the generic (non-custom) split algorithm
	        		// below, never before dispatching to a user-supplied
	        		// @@split (confirmed via split/cstm-split-invocation.js:
	        		// the string "limit" must reach @@split as-is, not as a
	        		// coerced number).
	        		Object dispatched = dispatchSymbolMethod(sep, Symbol.SPLIT, obj, pLimit);
	        		if(dispatched!=NO_DISPATCH) {
	        			return dispatched;
	        		}
	        		// Per spec, the receiver is ToString()-converted BEFORE
	        		// limit is ToUint32()-converted - confirmed via
	        		// split/this-value-tostring-error.js: a throwing receiver
	        		// ToString must win over a throwing (Symbol) limit.
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
	        		int limit;
	        		if(pLimit==RuntimeUtil.UNDEFINED) {
	        			limit = Integer.MAX_VALUE;
	        		} else {
	        			// Clamp, don't mask: `& 0x7FFFFFFFL` discards bit 31
	        			// entirely rather than saturating, so a uint32 value of
	        			// exactly 2**31 (a genuinely valid ToUint32 result,
	        			// e.g. from a limit of 2**31) wrongly became 0 instead
	        			// of a large-but-representable limit (confirmed via
	        			// split/separator-undef-limit-custom.js: limit=2**31
	        			// with an undefined separator must still return the
	        			// single-element `[S]` result, not `[]`).
	        			limit = (int) Math.min(RuntimeUtil.toUInt32(getEnvironment(), pLimit), Integer.MAX_VALUE);
	        		}
	        		//int limit = (int)Math.min(RuntimeUtil.toLength(context,pLimit),Integer.MAX_VALUE); // Doesn't make sense to have a huge limit (long)
	        		// A RegExp reaches this point only when its @@split was removed:
	        		// it is then an ordinary object, converted with ToString below
	        		if(sep==RuntimeUtil.UNDEFINED) {
	        			if(limit>0) {
	        				a.arrayAdd(_this);
	        			}
	        			return a;
	        		}
	        		// Per spec, separator is ToString()-converted BEFORE the
	        		// "if limit is 0" check - confirmed via
	        		// split/separator-tostring-error.js: a throwing separator
	        		// ToString must fire even when limit is 0 (which would
	        		// otherwise short-circuit to an empty array without ever
	        		// touching the separator at all).
	        		String ssep = RuntimeUtil.toString(getEnvironment(),sep);
	        		if(limit==0) {
	        			return a;
	        		}
	        		if(ssep.length()==0) {
	        			int count = Math.min(limit, _this.length());
		        		for(int i=0; i<count; i++) {
		        			a.arrayAdd(String.valueOf(_this.charAt(i)));
		        		}
	        		} else {
	        			int pos = 0;
	        			while(limit>0) {
	        				int next = _this.indexOf(ssep, pos);
	        				if(next<0) {
	        					a.arrayAdd(_this.substring(pos));
	        					break;
	        				}
        					a.arrayAdd(_this.substring(pos,next));
        					pos = next+ssep.length();
        					limit--;
	        			}
	        		}
            		return a;
	        	}
	        	case startsWith -> {
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
	        		Object rawSearch = param(args, 0, RuntimeUtil.UNDEFINED);
	        		if(isRegExpGeneric(rawSearch)) {
	        			throw RuntimeUtil.typeError("String.prototype.startsWith called with a RegExp argument");
	        		}
	        		String start = paramString(args, 0, null);
	        		// Per spec, position is clamped to [0, length] - Java's own
	        		// String.startsWith(prefix, toffset) doesn't clamp a
	        		// negative toffset (always returns false instead), and an
	        		// offset beyond length would throw/misbehave rather than
	        		// clamp either (confirmed via
	        		// startsWith/out-of-bounds-position.js: position -1 must
	        		// search from the start, same as position 0).
	        		int pos = Math.max(0, Math.min(paramInt(args,1,0), _this.length()));
	        		if(start==null) {
	        			return false;
	        		}
	        		if(start.length()==0) {
	        			return true;
	        		}
            		return _this.startsWith(start, pos);
	        	}
	        	case substring -> {
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
	        		int start = paramInt(args,0,0);
	        		int end = paramInt(args,1,_this.length());
	        		if(start>end) {
	        			int t = start;
	        			start = end;
	        			end = t;
	        		}
	        		start = Math.min(_this.length(),Math.max(0, start));
	        		end = Math.min(_this.length(),Math.max(0, end));
            		if(start==end) {
            			return "";
            		}
            		String result = _this.substring(start,end);
            		if(result==_this) {
            			return RuntimeUtil.objectAsPrimitive(getEnvironment(),result);
            		}
            		return result;
	        	}
	        	case toLocaleLowerCase -> {
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
	        		Locale locale = findLocale(paramString(args, 0, null));
	        		if(locale!=null) {
	        			String result = _this.toLowerCase(locale);
	            		if(result==_this) {
	            			return RuntimeUtil.objectAsPrimitive(getEnvironment(),result);
	            		}
	            		return result;
	        		}
    				String result = finalSigmaLowerCase(_this, null);
            		if(result==_this) {
            			return RuntimeUtil.objectAsPrimitive(getEnvironment(),result);
            		}
            		return result;
	        	}
	        	case toLocaleUpperCase -> {
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
	        		Locale locale = findLocale(paramString(args, 0, null));
	        		if(locale!=null) {
	    				String result = _this.toUpperCase(locale);
	            		if(result==_this) {
	            			return RuntimeUtil.objectAsPrimitive(getEnvironment(),result);
	            		}
	            		return result;
	        		}
	        		String result = _this.toUpperCase();
            		if(result==_this) {
            			return RuntimeUtil.objectAsPrimitive(getEnvironment(),result);
            		}
            		return result;
	        	}
	        	case toLowerCase -> {
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
	        		// Locale.ROOT ensures that the case conversion does not apply any locale-specific rules, providing behavior consistent with JavaScript's Unicode-based case conversion.
	        		String result = finalSigmaLowerCase(_this, Locale.ROOT);
            		if(result==_this) {
            			return RuntimeUtil.objectAsPrimitive(getEnvironment(),result);
            		}
            		return result;
	        	}
	        	case toString -> {
	        		if(obj instanceof CharSequence cs) {
        				return asPrimitive(getEnvironment(),cs);
	        		}
	        		if(obj instanceof BuiltinStringPrototype) {
	        			return "";
	        		}
	        		throw RuntimeUtil.typeError("String.toString can only called on Strings");
	        	}
	        	case toUpperCase -> {
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
	        		// Locale.ROOT ensures that the case conversion does not apply any locale-specific rules, providing behavior consistent with JavaScript's Unicode-based case conversion.
	        		//String result = _this.toUpperCase(); 
	        		String result = _this.toUpperCase(Locale.ROOT); 
            		if(result==_this) {
            			return RuntimeUtil.objectAsPrimitive(getEnvironment(),result);
            		}
            		return result;
	        	}
	        	case toWellFormed -> {
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
        	        return toWellFormed(_this);
	        	}
	        	case trim -> {
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
	        		String result = RuntimeUtil.trimWhiteSpaces(_this);
            		if(result==_this) {
            			return RuntimeUtil.objectAsPrimitive(getEnvironment(),result);
            		}
            		return result;
	        	}
	        	case trimEnd -> {
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
	        		String result = RuntimeUtil.trimTrailingWhiteSpaces(_this);
            		if(result==_this) {
            			return RuntimeUtil.objectAsPrimitive(getEnvironment(),result);
            		}
            		return result;
	        	}
	        	case trimStart -> {
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
	        		String result = RuntimeUtil.trimLeadingWhiteSpaces(_this);
            		if(result==_this) {
            			return RuntimeUtil.objectAsPrimitive(getEnvironment(),result);
            		}
            		return result;
	        	}
	        	case valueOf -> {
	        		if(obj instanceof CharSequence cs) {
        				return asPrimitive(getEnvironment(),cs);
	        		}
	        		if(obj instanceof BuiltinStringPrototype) {
	        			return "";
	        		}
	        		throw RuntimeUtil.typeError("String.valueOf can only called on Strings");
	        	}

	        	case iterator -> {
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
	        		// Strings iterate by Unicode code point, not UTF-16 code unit - a
	        		// surrogate pair must be yielded together as one 2-char step.
                    return new BuiltinStringIterator(getEnvironment(),
                    	new Iterator<Object>() {
                    		private int index = 0;
                    		@Override
                    		public boolean hasNext() {
                    			return index < _this.length();
                    		}
                    		@Override
                    		public Object next() {
                    			int codePoint = _this.codePointAt(index);
                    			int charCount = Character.charCount(codePoint);
                    			String s = _this.substring(index, index+charCount);
                    			index += charCount;
                    			return s;
                    		}
                    	}
                	);
	            }

	        	
	        	//
	        	// Deprecated
	        	//
	        	case substr -> {
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
	        		int size = _this.length();
	        		// start/length come out of paramInt() as Integer.MIN_VALUE/MAX_VALUE
	        		// for -Infinity/+Infinity (see RuntimeUtil.toInt()) - each MUST be
	        		// clamped into [0,size] independently, in this exact spec order
	        		// (start first, then length), BEFORE they're ever added together:
	        		// adding an unclamped Integer.MAX_VALUE-ish length to a small start
	        		// silently overflows int (wraps negative), which used to reach
	        		// String.substring() as a bogus negative end index.
	        		int start = paramInt(args,0,0);
	        		if(start<0) {
	        			start = Math.max(start+size, 0);
	        		} else {
	        			start = Math.min(start, size);
	        		}
	        		int length = paramInt(args,1,size);
	        		length = Math.min(Math.max(length,0), size);
	        		int end = Math.min(start+length, size);
            		String result = _this.substring(start,end);
            		if(result==_this) {
            			// Make sure that the result is a primitive
            			return RuntimeUtil.objectAsPrimitive(getEnvironment(),result);
            		}
            		return result;
	        	}
	        	case anchor -> {
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
	        		return createHTML(_this,"a","name",param(args,0,RuntimeUtil.UNDEFINED));
	        	}
	        	case big -> {
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
	        		return createHTML(_this,"big",null,null);
	        	}
	        	case blink -> {
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
	        		return createHTML(_this,"blink",null,null);
	        	}
	        	case bold -> {
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
	        		return createHTML(_this,"b",null,null);
	        	}
	        	case fixed -> {
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
	        		return createHTML(_this,"tt",null,null);
	        	}
	        	case fontcolor -> {
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
	        		return createHTML(_this,"font","color",param(args,0,RuntimeUtil.UNDEFINED));
	        	}
	        	case fontsize -> {
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
	        		return createHTML(_this,"font","size",param(args,0,RuntimeUtil.UNDEFINED));
	        	}
	        	case italics -> {
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
	        		return createHTML(_this,"i",null,null);
	        	}
	        	case link -> {
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
	        		return createHTML(_this,"a","href",param(args,0,RuntimeUtil.UNDEFINED));
	        	}
	        	case small -> {
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
	        		return createHTML(_this,"small",null,null);
	        	}
	        	case strike -> {
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
	        		return createHTML(_this,"strike",null,null);
	        	}
	        	case sub -> {
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
	        		return createHTML(_this,"sub",null,null);
	        	}
	        	case sup -> {
	        		final String _this = RuntimeUtil.toString(getEnvironment(),obj);
	        		return createHTML(_this,"sup",null,null);
	        	}
	        	
	            default -> {
	    		    throw new IllegalStateException(); // Should never be here 
	            }
	        }
	    }

		// CreateHTML(string,tag,attribute,value) (Annex B.2.3.1): `string` has
		// ALREADY been RequireObjectCoercible+ToString'd by the caller before
		// this is reached (matching every other case here) - the attribute
		// value (if any) is ToString'd HERE, second, so a throwing attribute
		// toString only ever surfaces after a successful `this` coercion
		// (confirmed via each method's own this-val-tostring-err.js running
		// BEFORE attr-tostring-err.js would even be reached).
		private String createHTML(String string, String tag, String attribute, Object value) {
			StringBuilder b = new StringBuilder();
			b.append('<').append(tag);
			if(attribute!=null) {
				String v = RuntimeUtil.toString(getEnvironment(),value);
				b.append(' ').append(attribute).append("=\"").append(v.replace("\"","&quot;")).append('"');
			}
			b.append('>').append(string).append("</").append(tag).append('>');
			return b.toString();
		}

		public static String asPrimitive(JSEnvironment env, CharSequence v) {
			if(v instanceof String) { // Consstring cannot be an object
				PrimitivePropertyMap map = env.getStringProperties();
				if(map!=null) {
					if(map.containsKey(v)) {
						// Make a physical copy of the string
						return new String(v.toString());
					}
				}
			}
			return v.toString();
		}

	    // The native (non-regex, no-Symbol.replace) string-only algorithm -
	    // callers (case replace/replaceAll) already dispatch to a RegExp/
	    // Symbol.replace-bearing `pattern` generically BEFORE reaching here.
	    public final String replaceString(String source, Object pattern, Object replace, boolean firstOnly) {
    		JSEnvironment env = getEnvironment();
	    	String value = RuntimeUtil.toString(env, pattern);
	    	// Per spec, a non-callable replaceValue is ToString()-converted
	    	// UNCONDITIONALLY, before the search even happens - not lazily,
	    	// only once a match is actually found (confirmed via
	    	// replace/replaceValue-evaluation-order.js:
	    	// "".replace("a", {toString(){...}}) must still invoke toString()
	    	// exactly once even though "a" is never found in "").
	    	String nonCallableReplace = BuiltinUtil.isCallable(replace) ? null : RuntimeUtil.toString(env, replace);
			int idx = indexOf(source,value,0);
			if (idx >= 0) {
				StringBuilder b = new StringBuilder(source.length()+64);
				if(idx>0) {
					b.append(source, 0, idx);
				}
				int next;
				do {
					// Add the replacement
					String toReplace;
					if(replace instanceof Callable cb && cb.isCallable()) {
		                final Object[] args = new Object[] {
		                	value,
		                	idx,
		                	source
		                };
		                // Called with no receiver: this must be `undefined`, not `null`
		                // (matters for a strict-mode replacer function's own `this`).
		                toReplace = RuntimeUtil.toString(env,cb.call(RuntimeUtil.UNDEFINED,args));
					} else {
						toReplace = nonCallableReplace;
					}
					addReplaceString(b,source,idx,idx+value.length(),toReplace);
					
					// and go to the next one
					//next = idx + (value.isEmpty() ? 1 : value.length());
					next = idx + value.length();
					if(firstOnly) {
						b.append(source, next, source.length());
						break;
					} else {
						idx = indexOf(source, value, value.isEmpty() ? next+1 : next);
						b.append(source, next, idx >= 0 ? idx : source.length());
					}
				} while (idx >= 0);
				return b.toString();
			}
			
			return source;
		}	
	    private static int indexOf(String source, String value, int pos) {
	    	if(value.isEmpty()) {
	    		return pos<=source.length() ? pos : -1;
	    	}
	    	return source.indexOf(value,pos);
	    }
		
		private void addReplaceString(StringBuilder result, String str, int start, int end, String newSubStr) {
			int subLength = newSubStr.length();
	        for (int i = 0; i < subLength;) {
	            char c = newSubStr.charAt(i++);
	            if (c != '$' || i==subLength) { // last $ is a regular character
	                result.append(c);
	                continue;
	            }
	
	            // special handling for: $$, $&, $`, $', $n, $nn
	            char after$ = newSubStr.charAt(i++);
	            switch (after$) {
	                case '$':
	                    result.append('$');
	                    break;
	                case '&':
	                    result.append(str.substring(start,end));
	                    break;
	                case '`':
	                    result.append(str, 0, start);
	                    break;
	                case '\'':
	                    result.append(str, end, str.length());
	                    break;
	                default:
	                    result.append('$').append(after$);
				}
		    }
		}
	}
		
	// Unicode's Default Case Algorithm's "Final_Sigma" condition (3.13):
	// GREEK CAPITAL LETTER SIGMA (U+03A3) lowercases to FINAL SIGMA (U+03C2,
	// "ς") - not the ordinary U+03C3 ("σ") - exactly when it's preceded by a
	// Cased letter (with zero or more Case_Ignorable characters in between)
	// and NOT followed by a Cased letter (again allowing intervening
	// Case_Ignorable characters). java.lang.String.toLowerCase() DOES
	// implement this (confirmed correct for every BMP case - test262's
	// special_casing_conditional.js's non-supplementary assertions all
	// pass), but its Case_Ignorable classification doesn't correctly cover
	// SUPPLEMENTARY-PLANE codepoints (e.g. U+1D242 COMBINING GREEK MUSICAL
	// TRISEME, a surrogate pair) - "A𝉂Σ".toLowerCase() wrongly
	// produces plain "σ" instead of the required final "ς". Rather than
	// reimplement ALL of SpecialCasing.txt ourselves (String.toLowerCase()
	// already gets the other, unconditional special casings - e.g. U+0130's
	// 1-to-2 expansion to "i̇" - exactly right, and duplicating that
	// from scratch would risk regressing tests that already pass), this
	// only recomputes Final_Sigma itself, using the SAME Cased/Case_Ignorable
	// Unicode property data already bundled for RegExp \p{...} property
	// escapes (UnicodePropertyData) - which, unlike String.toLowerCase(),
	// is correctly codepoint-aware. Splitting the string into the segments
	// BETWEEN each Sigma (each independently lowercased via the JDK, exactly
	// as before) and inserting our own correctly-computed sigma/final-sigma
	// character between them sidesteps the need to track any output-length
	// offset shift from an earlier expansion - Sigma itself always maps to
	// exactly one output character either way, so no offset math is needed.
	private static String finalSigmaLowerCase(String s, Locale locale) {
		int sigma = s.indexOf('Σ');
		if(sigma<0) {
			return locale==null ? s.toLowerCase() : s.toLowerCase(locale);
		}
		StringBuilder sb = new StringBuilder(s.length());
		int segStart = 0;
		int i = 0;
		int len = s.length();
		while(i<len) {
			if(s.charAt(i)=='Σ') {
				String seg = s.substring(segStart,i);
				sb.append(locale==null ? seg.toLowerCase() : seg.toLowerCase(locale));
				sb.append(isFinalSigma(s,i) ? 'ς' : 'σ');
				segStart = i+1;
			}
			i++;
		}
		String seg = s.substring(segStart);
		sb.append(locale==null ? seg.toLowerCase() : seg.toLowerCase(locale));
		return sb.toString();
	}

	// idx is the (BMP, single-UTF16-unit) position of the U+03A3 itself.
	private static boolean isFinalSigma(String s, int idx) {
		boolean precededByCased = false;
		int p = idx;
		while(p>0) {
			int cp = s.codePointBefore(p);
			p -= Character.charCount(cp);
			if(isCaseIgnorable(cp)) {
				continue;
			}
			precededByCased = isCased(cp);
			break;
		}
		if(!precededByCased) {
			return false;
		}
		int q = idx+1;
		int len = s.length();
		while(q<len) {
			int cp = s.codePointAt(q);
			if(isCaseIgnorable(cp)) {
				q += Character.charCount(cp);
				continue;
			}
			return !isCased(cp);
		}
		return true;
	}

	private static boolean isCased(int cp) {
		return inRanges(org.monflabs.galtajs.rt.builtins.standard.regexp.jdk.UnicodePropertyData.getRanges("Cased"), cp);
	}
	private static boolean isCaseIgnorable(int cp) {
		return inRanges(org.monflabs.galtajs.rt.builtins.standard.regexp.jdk.UnicodePropertyData.getRanges("Case_Ignorable"), cp);
	}
	private static boolean inRanges(int[] ranges, int cp) {
		if(ranges==null) {
			return false;
		}
		int lo = 0, hi = (ranges.length/2)-1;
		while(lo<=hi) {
			int mid = (lo+hi)>>>1;
			int start = ranges[mid*2];
			int end = ranges[mid*2+1];
			if(cp<start) {
				hi = mid-1;
			} else if(cp>end) {
				lo = mid+1;
			} else {
				return true;
			}
		}
		return false;
	}

	private static boolean isWellFormed(String input) {
	    int length = input.length();
	    for (int i = 0; i < length; i++) {
	        char c = input.charAt(i);
	        if (Character.isHighSurrogate(c)) {
	            if (i + 1 < length && Character.isLowSurrogate(input.charAt(i + 1))) {
	                i++; // valid pair, skip the low surrogate
	            } else {
	                return false; // lone high surrogate
	            }
	        } else if (Character.isLowSurrogate(c)) {
	            return false; // lone low surrogate
	        }
	    }
	    return true;
	}	
	private static String toWellFormed(String input) {
	    // Unlike finalSigmaLowerCase (which scans first and returns the
	    // input unchanged with zero allocation when its trigger character
	    // is absent), this had no such short-circuit despite isWellFormed()
	    // already existing in this same file and being exactly the check
	    // needed - most real-world strings have no lone surrogates at all.
	    if (isWellFormed(input)) {
	        return input;
	    }
	    int length = input.length();
	    StringBuilder result = new StringBuilder(length);
	    for (int i = 0; i < length; i++) {
	        char c = input.charAt(i);
	        if (Character.isHighSurrogate(c)) {
	            if (i + 1 < length && Character.isLowSurrogate(input.charAt(i + 1))) {
	                result.append(c).append(input.charAt(++i));
	            } else {
	                result.append('\uFFFD');
	            }
	        } else if (Character.isLowSurrogate(c)) {
	            result.append('\uFFFD');
	        } else {
	            result.append(c);
	        }
	    }
	    return result.toString();
	}
}
