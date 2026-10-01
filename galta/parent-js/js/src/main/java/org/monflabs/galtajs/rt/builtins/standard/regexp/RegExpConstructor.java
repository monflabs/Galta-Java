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
package org.monflabs.galtajs.rt.builtins.standard.regexp;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseGetter;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.BaseSetter;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.primitives.BaseStandardConstructor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;

/**
 *
 */
public class RegExpConstructor extends BaseStandardConstructor {

	public static final String CLASSNAME = "RegExp";

	// Legacy static properties (the "legacy RegExp features" proposal, once
	// Annex B): [[RegExpInput]], [[RegExpLastMatch]], [[RegExpLastParen]],
	// [[RegExpLeftContext]], [[RegExpRightContext]], [[RegExpParen1-9]], ""
	// until the first match. Every successful match of a RegExp created in
	// this environment calls updateLegacyStaticProperties() (see
	// RegExp.updateLegacyStaticProperties()). The match is kept as the
	// subject plus its capture spans and the strings are only built when an
	// accessor reads them, so a match costs one small array copy.
	private String legacyInput = "";
	private String legacySubject = "";
	// [start, end] of the whole match, then of each capture (-1 when the
	// capture did not participate), in UTF-16 code units
	private int[] legacySpans;

	public RegExpConstructor(JSEnvironment env) {
		super(env,CLASSNAME,RegExpPrototype.get(env),2);

		setOwnMethod(new Method(env,MethodId.escape,1));
		// get [Symbol.species] () { return this; } - a getter-only accessor
		// (not a static value) so subclasses correctly return themselves.
		setOwnProperty(Symbol.SPECIES, true, false, (base,key) -> base, null);

		BaseGetter.Getter inputGetter = (base,key) -> { requireSelf(base); return legacyInput; };
		BaseSetter.Setter inputSetter = (base,key,value) -> { requireSelf(base); legacyInput = RuntimeUtil.toString(getEnvironment(),value); return true; };
		setOwnProperty("input", true, false, inputGetter, inputSetter);
		setOwnProperty("$_", true, false, inputGetter, inputSetter);

		BaseGetter.Getter lastMatchGetter = (base,key) -> { requireSelf(base); return legacySpan(0); };
		setOwnProperty("lastMatch", true, false, lastMatchGetter, null);
		setOwnProperty("$&", true, false, lastMatchGetter, null);

		BaseGetter.Getter lastParenGetter = (base,key) -> {
			requireSelf(base);
			int n = legacySpans==null ? 0 : legacySpans.length/2 - 1;
			return n>0 ? legacySpan(n) : "";
		};
		setOwnProperty("lastParen", true, false, lastParenGetter, null);
		setOwnProperty("$+", true, false, lastParenGetter, null);

		BaseGetter.Getter leftContextGetter = (base,key) -> {
			requireSelf(base);
			return legacySpans==null ? "" : legacySubject.substring(0,legacySpans[0]);
		};
		setOwnProperty("leftContext", true, false, leftContextGetter, null);
		setOwnProperty("$`", true, false, leftContextGetter, null);

		BaseGetter.Getter rightContextGetter = (base,key) -> {
			requireSelf(base);
			return legacySpans==null ? "" : legacySubject.substring(legacySpans[1]);
		};
		setOwnProperty("rightContext", true, false, rightContextGetter, null);
		setOwnProperty("$'", true, false, rightContextGetter, null);

		for(int i=0; i<9; i++) {
			final int idx = i;
			BaseGetter.Getter parenGetter = (base,key) -> { requireSelf(base); return legacySpan(idx+1); };
			setOwnProperty("$"+(i+1), true, false, parenGetter, null);
		}
	}

	// UpdateLegacyRegExpStaticProperties(C, S, startIndex, endIndex,
	// capturedValues). Takes ownership of `spans`.
	public void updateLegacyStaticProperties(String subject, int[] spans) {
		this.legacyInput = subject;
		this.legacySubject = subject;
		this.legacySpans = spans;
	}

	// The text of span `group` (0 = the whole match), "" if there is none or
	// the capture did not participate (an undefined capturedValue is "").
	private String legacySpan(int group) {
		int[] spans = legacySpans;
		if(spans==null || 2*group+1>=spans.length || spans[2*group]<0) {
			return "";
		}
		return legacySubject.substring(spans[2*group],spans[2*group+1]);
	}

	// GetLegacyRegExpStaticProperty/SetLegacyRegExpStaticProperty's shared
	// "SameValue(%RegExp%, this value)" receiver check - identity is exactly
	// SameValue here since there's only ever this one %RegExp% object.
	private void requireSelf(Object base) {
		if(base!=this) {
			throw RuntimeUtil.typeError("Method RegExp legacy accessor called on incompatible receiver");
		}
	}
	
	@Override
	public Class<?> getNativeClass() {
		return RegExp.class;
	}

	// Per spec 22.2.4.1, calling RegExp(pattern, flags) WITHOUT `new` (so
	// NewTarget defaults to %RegExp% itself) short-circuits to returning
	// `pattern` UNCHANGED - not a new/re-wrapped instance - when pattern is
	// itself IsRegExp-true, flags is undefined, AND pattern's own
	// "constructor" property is genuinely this exact %RegExp% (a RegExp
	// subclass instance must NOT take this shortcut, since re-wrapping
	// through the base constructor would silently lose its actual class).
	// `new RegExp(...)` never reaches this override at all (construction
	// goes through constructObject() directly), so this only affects the
	// bare function-call form (confirmed via S15.10.3.1_A1_T1.js: `var
	// __instance = RegExp(__re)` must be the SAME object as `__re`, not a
	// copy - a later `__re.indicator = 1` must be visible on `__instance`).
	@Override
	public Object call(Object _this, Object[] parameters) {
		Object pattern = parameters.length>=1 ? parameters[0] : RuntimeUtil.UNDEFINED;
		Object flagsParam = parameters.length>=2 ? parameters[1] : RuntimeUtil.UNDEFINED;
		if(isRegExp(pattern) && RuntimeUtil.isNullOrUndefined(flagsParam)) {
			JSAccessor acc = getEnvironment().getAccessor(pattern);
			if(acc.getProperty(pattern,"constructor",RuntimeUtil.UNDEFINED)==this) {
				return pattern;
			}
		}
		return constructObject(parameters);
	}

	@Override
	public Object constructObject(Object[] parameters, Constructor topConstructor) {
		Object pattern = parameters.length>=1 ? parameters[0] : RuntimeUtil.UNDEFINED;
		Object flagsParam = parameters.length>=2 ? parameters[1] : RuntimeUtil.UNDEFINED;
		// Step 4 (22.2.3.1 RegExp(pattern, flags)): IsRegExp(pattern) - i.e.
		// Get(pattern, @@match) - is called EXACTLY ONCE here, unconditionally,
		// BEFORE any source/flags are read below, even when pattern is a
		// genuine RegExp instance (whose OWN [[OriginalSource]]/
		// [[OriginalFlags]] are what get read, not this call's boolean
		// result). A poisoned/overridden own Symbol.match - even on a real
		// RegExp instance - can have an observable side effect (e.g. a getter
		// calling pattern.compile(...), replacing its source/flags) that MUST
		// run before those slots are read afterward, matching spec's real
		// step order. Fixes annexB/built-ins/RegExp/prototype/Symbol.split/
		// Symbol.match-getter-recompiles-source.js, whose @@split-constructed
		// splitter was reading the PRE-mutation pattern since this method
		// never consulted @@match at all for a genuine RegExp `pattern`.
		boolean patternIsRegExp = isRegExp(pattern);
		String source;
		Object flags;
		if(pattern instanceof RegExp re) {
			// A genuine RegExp instance's [[OriginalSource]]/[[OriginalFlags]]
			// slots are used directly - NOT its (possibly overridden)
			// "source"/"flags" JS-level properties.
			source = re.getSource();
			flags = RuntimeUtil.isNotNullOrUndefined(flagsParam) ? flagsParam : re.getFlags();
		} else if(patternIsRegExp) {
			// IsRegExp(pattern) true but not a genuine RegExp (e.g. a plain
			// object or Proxy with its own possibly-overridden Symbol.match) -
			// per RegExpInitialize, "source"/"flags" ARE read as ordinary JS
			// properties here (unlike the genuine-RegExp branch above).
			JSAccessor acc = getEnvironment().getAccessor(pattern);
			source = RuntimeUtil.toString(getEnvironment(), acc.getProperty(pattern,"source",RuntimeUtil.UNDEFINED));
			flags = RuntimeUtil.isNotNullOrUndefined(flagsParam) ? flagsParam : RuntimeUtil.toString(getEnvironment(), acc.getProperty(pattern,"flags",RuntimeUtil.UNDEFINED));
		} else {
			source = pattern==RuntimeUtil.UNDEFINED ? "" : RuntimeUtil.toString(getEnvironment(),pattern);
			flags = flagsParam;
		}
		RegExp o = new RegExp(getEnvironment(),source,flags);
		return applyNewTargetPrototype(o, topConstructor);
	}

	// IsRegExp(argument): Get(argument, @@match) is consulted FIRST (even for
	// a genuine RegExp instance, in principle) - only falls back to the
	// internal [[RegExpMatcher]] slot check (i.e. a real RegExp instance)
	// when @@match is undefined. Accessing @@match here (rather than
	// short-circuiting on `instanceof RegExp`) is required so that a custom
	// Symbol.match override - even a throwing one - is observably consulted,
	// e.g. by RegExp.prototype[Symbol.matchAll]'s Construct(C, «R, flags»)
	// call for a non-RegExp `R`.
	private boolean isRegExp(Object arg) {
		if(!RuntimeUtil.isObject(getEnvironment(),arg)) {
			return false;
		}
		JSAccessor acc = getEnvironment().getAccessor(arg);
		Object m = acc.getProperty(arg,Symbol.MATCH,RuntimeUtil.UNDEFINED);
		if(m!=RuntimeUtil.UNDEFINED) {
			return RuntimeUtil.toBoolean(getEnvironment(),m);
		}
		return arg instanceof RegExp;
	}

	private static enum MethodId {
		escape,
	}
	private static final class Method extends BaseMethod {
		private MethodId methodId;

		private Method(JSEnvironment env, MethodId methodId, int length) {
			super(env,methodId.name(),length);
			this.methodId = methodId;
		}

		@Override
		protected Object invoke(final Object obj, final Object[] args) {
			switch(methodId) {
				case escape -> {
					Object arg = args.length > 0 ? args[0] : RuntimeUtil.UNDEFINED;
					if(!(arg instanceof CharSequence)) {
						throw RuntimeUtil.typeError("RegExp.escape requires a string argument");
					}
					String s = arg.toString();
					int len = s.length();
					StringBuilder sb = new StringBuilder(len + 8);
					int i = 0;
					boolean first = true;
					while(i < len) {
						int cp = s.codePointAt(i);
						// A leading DecimalDigit or AsciiLetter is escaped as \xHH
						// (not via EncodeForRegExpEscape) so the result can't be
						// misread as an extension of a preceding \0/\1.. DecimalEscape
						// or \c control-letter escape when spliced into a larger
						// pattern - both are always <=0x7A, so hex is always 2 digits.
						if(first && (isDecimalDigit(cp) || isAsciiLetter(cp))) {
							sb.append("\\x").append(String.format("%02x", cp));
						} else {
							appendEncodeForRegExpEscape(sb, cp);
						}
						first = false;
						i += Character.charCount(cp);
					}
					return sb.toString();
				}
				default -> {
					throw new IllegalStateException();
				}
			}
		}

		// EncodeForRegExpEscape ( c ), applied to every code point EXCEPT a
		// leading DecimalDigit/AsciiLetter (handled by the caller before this
		// is reached - see the leading-char check above).
		private static void appendEncodeForRegExpEscape(StringBuilder sb, int c) {
			if(isSyntaxCharacter(c) || c == '/') {
				sb.append('\\').appendCodePoint(c);
				return;
			}
			// ControlEscape table (t/n/v/f/r) takes priority over the
			// generic \\xHH / \\uHHHH forms below, even though TAB/VT/FF/LF/CR
			// also fall within the WhiteSpace/LineTerminator set checked next
			// (confirmed via escape/escaped-control-characters.js: '\\t' not
			// '\\x09' is expected).
			switch(c) {
				case 0x09 -> { sb.append("\\t"); return; }
				case 0x0A -> { sb.append("\\n"); return; }
				case 0x0B -> { sb.append("\\v"); return; }
				case 0x0C -> { sb.append("\\f"); return; }
				case 0x0D -> { sb.append("\\r"); return; }
			}
			// otherPunctuators, OR WhiteSpace/LineTerminator (RuntimeUtil's
			// own isWhiteSpace already covers exactly the JS WhiteSpace +
			// LineTerminator productions - the remaining ones not already
			// caught by the ControlEscape switch above), OR a lone surrogate
			// code unit (0xD800-0xDFFF - only reachable here for an UNPAIRED
			// surrogate, since codePointAt() above already combines a valid
			// surrogate pair into one non-surrogate astral code point).
			if(isOtherPunctuator(c) || (c <= 0xFFFF && RuntimeUtil.isWhiteSpace((char) c)) || (c >= 0xD800 && c <= 0xDFFF)) {
				if(c <= 0xFF) {
					sb.append("\\x").append(String.format("%02x", c));
				} else {
					// UTF16EncodeCodePoint(c) followed by a \\uHHHH UnicodeEscape
					// of each resulting code unit - only ever a single code unit
					// here, since every trigger above (otherPunctuators,
					// whitespace/lineterm, lone surrogate) is itself a single
					// BMP code unit.
					sb.append("\\u").append(String.format("%04x", c));
				}
				return;
			}
			// Fallback: UTF16EncodeCodePoint(c), unescaped - for an astral
			// code point this emits its natural surrogate pair.
			sb.appendCodePoint(c);
		}

		private static boolean isSyntaxCharacter(int c) {
			return c == '^' || c == '$' || c == '\\' || c == '.'
				|| c == '*' || c == '+' || c == '?' || c == '('
				|| c == ')' || c == '[' || c == ']' || c == '{'
				|| c == '}' || c == '|' || c == '/';
		}

		private static boolean isOtherPunctuator(int c) {
			return c == ',' || c == '-' || c == '=' || c == '<' || c == '>'
				|| c == '#' || c == '&' || c == '!' || c == '%' || c == ':'
				|| c == ';' || c == '@' || c == '~' || c == '\'' || c == '`'
				|| c == '"';
		}

		private static boolean isDecimalDigit(int c) {
			return c >= '0' && c <= '9';
		}

		private static boolean isAsciiLetter(int c) {
			return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
		}
	}
}
