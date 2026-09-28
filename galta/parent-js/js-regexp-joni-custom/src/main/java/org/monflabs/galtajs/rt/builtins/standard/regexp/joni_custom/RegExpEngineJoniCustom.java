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
package org.monflabs.galtajs.rt.builtins.standard.regexp.joni_custom;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.BiFunction;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.external.org_joni.Matcher;
import org.monflabs.galtajs.external.org_joni.NameEntry;
import org.monflabs.galtajs.external.org_joni.Option;
import org.monflabs.galtajs.external.org_joni.Regex;
import org.monflabs.galtajs.external.org_joni.Region;
import org.monflabs.galtajs.external.org_joni.Syntax;
import org.monflabs.galtajs.external.org_joni.constants.MetaChar;
import org.monflabs.galtajs.external.org_joni.constants.SyntaxProperties;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.standard.regexp.RegExp;
import org.monflabs.galtajs.rt.builtins.standard.regexp.RegExpEngine;
import org.monflabs.galtajs.rt.builtins.standard.regexp.jdk.UnicodePropertyData;
import org.monflabs.util.StringUtil;

/**
 * RegExp engine using Joni (Oniguruma for Java) with ECMAScript syntax.
 * Joni natively supports ECMAScript regex semantics including proper capture
 * group clearing in repetitions, which java.util.regex cannot handle.
 */
public class RegExpEngineJoniCustom implements RegExpEngine {

	private static final String LS = " ";
	private static final String PS = " ";
	private static final String DOT_REPLACEMENT = "[^\\n\\r" + LS + PS + "]";
	private static final String CARET_MULTILINE = "(?:\\A|(?<=[\\n\\r" + LS + PS + "]))";
	private static final String DOLLAR_MULTILINE = "(?=\\r\\n|[\\n\\r" + LS + PS + "]|\\z)";

	private static final Syntax JS_SYNTAX = new Syntax(
			"JavaScript",
			(( SyntaxProperties.GNU_REGEX_OP | SyntaxProperties.OP_QMARK_NON_GREEDY |
			SyntaxProperties.OP_ESC_OCTAL3 | SyntaxProperties.OP_ESC_X_HEX2 |
			SyntaxProperties.OP_ESC_CONTROL_CHARS | SyntaxProperties.OP_ESC_C_CONTROL |
			SyntaxProperties.OP_DECIMAL_BACKREF | SyntaxProperties.OP_ESC_D_DIGIT |
			SyntaxProperties.OP_ESC_S_WHITE_SPACE | SyntaxProperties.OP_ESC_W_WORD )
			& ~SyntaxProperties.OP_ESC_LTGT_WORD_BEGIN_END ),

			( SyntaxProperties.OP2_ESC_CAPITAL_Q_QUOTE |
			SyntaxProperties.OP2_QMARK_GROUP_EFFECT | SyntaxProperties.OP2_OPTION_PERL |
			SyntaxProperties.OP2_ESC_P_BRACE_CHAR_PROPERTY |
			SyntaxProperties.OP2_ESC_P_BRACE_CIRCUMFLEX_NOT |
			SyntaxProperties.OP2_ESC_U_HEX4 | SyntaxProperties.OP2_ESC_V_VTAB |
			SyntaxProperties.OP2_QMARK_LT_NAMED_GROUP |
			SyntaxProperties.OP2_ESC_K_NAMED_BACKREF ),

			SyntaxProperties.OP3_OPTION_ECMASCRIPT,

			( SyntaxProperties.CONTEXT_INDEP_ANCHORS |
			SyntaxProperties.CONTEXT_INDEP_REPEAT_OPS |
			SyntaxProperties.CONTEXT_INVALID_REPEAT_OPS |
			SyntaxProperties.ALLOW_INVALID_INTERVAL |
			SyntaxProperties.BACKSLASH_ESCAPE_IN_CC |
			SyntaxProperties.ALLOW_DOUBLE_RANGE_OP_IN_CC |
			SyntaxProperties.DIFFERENT_LEN_ALT_LOOK_BEHIND |
			SyntaxProperties.ALLOW_MULTIPLEX_DEFINITION_NAME ),

			Option.NONE,

			new Syntax.MetaCharTable(
				'\\',
				MetaChar.INEFFECTIVE_META_CHAR,
				MetaChar.INEFFECTIVE_META_CHAR,
				MetaChar.INEFFECTIVE_META_CHAR,
				MetaChar.INEFFECTIVE_META_CHAR,
				MetaChar.INEFFECTIVE_META_CHAR
			)
	);

	private static final BiFunction<JSEnvironment,RegExp,RegExpEngine> FACTORY =
			(env, regexp) -> new RegExpEngineJoniCustom(env, regexp);
	public static BiFunction<JSEnvironment,RegExp,RegExpEngine> factory() {
		return FACTORY;
	}

	private final JSEnvironment env;
	private final RegExp regExp;
	private Regex regex;
	// "u"/"v" mode combines a valid surrogate pair into one 4-byte "character"
	// (correct for that mode - a match may never start strictly between the
	// two halves of a pair); without either flag, JS operates at the raw
	// UTF-16 code-unit level instead, where either half of what looks like a
	// valid pair is its own independent character and a match may start on
	// it directly - see LenientUTF16BECodeUnitEncoding's class comment.
	// Every Regex this instance compiles (the main pattern plus any
	// CustomLookbehind fragment) must agree on this, so it's computed once
	// and reused rather than always defaulting to the combining encoding.
	private final org.monflabs.galtajs.external.org_joni.encoding.Encoding joniEncoding;
	private final Map<String, Integer> namedGroups = new LinkedHashMap<>();
	// Only populated for a name shared by more than one GroupSpecifier (duplicate
	// named capturing groups in mutually exclusive alternatives) - see extractNamedGroups().
	private final Map<String, int[]> multiplexNamedGroups = new LinkedHashMap<>();

	// Cached byte representation of last matched string
	private byte[] lastBytes;
	// Parallel char[] view of the same string, populated only when the joni
	// encoding is fixedWidth2 (every code unit is 2 bytes). Handed to the
	// Joni Matcher as a sidecar so hot MB opcodes can read a code unit via
	// a single chars[s>>1] load instead of reconstructing it from two byte
	// reads + shift/or. Amortized across the many iterations of a global/
	// exec loop against the same subject string via the lastString identity
	// cache below.
	private char[] lastChars;
	private String lastString;
	private int lastMatchStart = -1;
	private int lastMatchEnd = -1;
	private Region lastRegion;

	// Non-null only for a pattern of the exact shape (PREFIX)?(?<=BODY)(SUFFIX)
	// or (PREFIX)?(?<!BODY)(SUFFIX) where BODY contains true variable-length
	// content (\w+, [abc]*, {n,}, ...) that Joni's own compiled lookbehind
	// opcode unconditionally rejects (Oniguruma requires fixed-length, or
	// alternatives of differing-but-individually-fixed lengths). See
	// setUpCustomLookbehind()/matchCustomLookbehind() for the full approach:
	// BODY is reversed (LookbehindReversal) and matched forward against a
	// reversed copy of the string prefix, sidestepping Joni's native
	// lookbehind opcode entirely rather than working around its length
	// restriction.
	private CustomLookbehind customLookbehind;

	private static final class CustomLookbehind {
		boolean negative;
		boolean prefixDotStar;
		// Non-null for a prefix that's neither empty nor a bare ".*" (e.g.
		// "^faaao?") - matched via a bounded-range search (see
		// tryGenericPrefixMatchAt) that emulates backtracking across the
		// prefix's own internal choices (quantifiers, alternation) by
		// trying candidate end positions widest-first and checking whether
		// the prefix, restricted to THAT range, naturally reaches exactly
		// that boundary.
		Regex genericPrefixRegex;
		LBNode bodyNode; // reversed BODY, matched forward against a reversed string prefix - see LBNode/matchLBNode
		Map<Integer, String> bodyGroupSyntheticNames; // original group number -> synthetic name used in bodyNode's leaves
		Regex suffixRegex; // SUFFIX, matched forward starting exactly at the lookbehind's anchor position
		int suffixGroupOffset; // SUFFIX's own capturing groups are numbered starting at this + 1
		// Non-null only when SUFFIX's own source text contains a
		// backreference (\N or \k<name>) to a group defined INSIDE body -
		// Joni can't resolve that natively since body and suffix are
		// separately-compiled Regex objects, so such a pattern needs
		// suffixRegex rebuilt per match attempt with that backreference
		// substituted for BODY's actual (just-computed) captured text.
		String suffixSourceForBridging;
		int totalGroupCount; // whole ORIGINAL pattern's capturing-group count (body's + suffix's)
		int compileOptions;
	}

	public RegExpEngineJoniCustom(JSEnvironment env, RegExp regExp) {
		this.env = env;
		this.regExp = regExp;
		this.joniEncoding = (regExp.isUnicode() || regExp.isUnicodeSets())
				? LenientUTF16BEEncoding.INSTANCE
				: LenientUTF16BECodeUnitEncoding.INSTANCE;

		String source = regExp.getSource();
		validateDuplicateGroupNames(source);
		String translated = translatePattern(source);
		// UTF-16BE, not UTF-8: JS strings are UTF-16 code-unit sequences that may
		// contain lone (unpaired) surrogates - valid JS content, but not
		// representable in standard UTF-8 at all (the JDK's UTF-8 encoder silently
		// replaces an unpaired surrogate with U+FFFD, corrupting any match/property-
		// escape test against it - confirmed via test262's property-escapes suite,
		// which deliberately probes surrogate-range code points). A fixed
		// 2-bytes-per-JS-char-unit mapping avoids that loss - but ONLY via the
		// hand-rolled toUtf16BEBytes()/fromUtf16BEBytes() helpers below, NOT
		// Java's own UTF_16BE Charset: String.getBytes(StandardCharsets.UTF_16BE)
		// and new String(bytes, StandardCharsets.UTF_16BE) both turn out to
		// silently substitute U+FFFD for a lone surrogate too, in EITHER
		// direction (confirmed empirically) - exactly the same class of bug
		// as the UTF-8 one this comment already warns about, just one layer
		// deeper and easy to assume away instead of verifying. jcodings' own
		// UTF16BEEncoding still decodes valid surrogate PAIRS into their real
		// supplementary code point for \p{...} matching - see getBytes()/
		// charIndexToByteIndex()/byteIndexToCharIndex() below, now a trivial *2//2
		// mapping instead of hand-rolled (and here, lossy) UTF-8 walking.
		//
		// LenientUTF16BEEncoding, not jcodings' own UTF16BEEncoding: JS regex
		// operations can start a search/match at any UTF-16 code-unit index,
		// including one that splits a surrogate pair (e.g. a global regex
		// resuming from a lastIndex left mid-pair). jcodings' encoding reports
		// CHAR_INVALID for a lone surrogate half, which makes Joni's own scan
		// loop (`p += enc.length(...)`) go non-positive and spin forever - see
		// LenientUTF16BEEncoding's class comment for the full analysis.
		byte[] patternBytes = toUtf16BEBytes(translated);

		// SINGLELINE = ^/$ match only at string start/end (not before \n). When multiline
		// flag is active, pattern translation replaces ^/$ with explicit lookaround.
		int options = Option.SINGLELINE;
		if (regExp.isIgnoreCase()) options |= Option.IGNORECASE;
		// In Joni, MULTILINE = dot matches \n (like Perl's /s). We handle ^/$ via pattern translation.
		if (regExp.isDotAll()) options |= Option.MULTILINE;

		try {
			this.regex = new Regex(patternBytes, 0, patternBytes.length, options,
					joniEncoding, JS_SYNTAX);
			extractNamedGroups(regex, 0, namedGroups);
		} catch (RuntimeException ex) {
			// Joni's own compiled lookbehind opcode unconditionally rejects
			// true variable-length content, regardless of syntax flags (see
			// CustomLookbehind's own class comment) - for the narrow pattern
			// shape setUpCustomLookbehind() recognizes, implement it
			// ourselves instead of surfacing Joni's rejection as a
			// SyntaxError. Anything outside that recognized shape falls
			// through to the original error, unchanged from before.
			this.customLookbehind = trySetUpCustomLookbehind(translated, options, source, ex);
			if (this.customLookbehind == null) {
				throw RuntimeUtil.syntaxError("Invalid RegExp '/{0}/{1}': {2}",
						source, regExp.getFlags(), ex.getMessage());
			}
		}
	}

	// Recognizes the narrow pattern shape this custom-lookbehind path
	// supports: an optional PREFIX (empty, a bare ".*", or - via
	// tryGenericPrefixMatchAt's bounded-range search - arbitrary
	// Joni-native-compilable content), then EXACTLY one top-level
	// "(?<=BODY)"/"(?<!BODY)", then an arbitrary SUFFIX running to the end
	// of the pattern.
	private CustomLookbehind trySetUpCustomLookbehind(String translated, int options, String source, RuntimeException originalException) {
		String msg = originalException.getMessage();
		if (msg == null || !msg.contains("invalid pattern in look-behind")) {
			return null;
		}

		String dotAtom = regExp.isDotAll() ? "." : DOT_REPLACEMENT;
		String dotStarPrefix = dotAtom + "*";
		boolean prefixDotStar = translated.startsWith(dotStarPrefix);
		int lbStart;
		String genericPrefixText = null;
		if (prefixDotStar) {
			lbStart = dotStarPrefix.length();
		} else if (translated.startsWith("(?<=") || translated.startsWith("(?<!")) {
			lbStart = 0;
		} else {
			// Not a recognized bare prefix - look for a top-level lookbehind
			// LATER in the pattern text and, if found, try treating
			// everything before it as an arbitrary prefix (see
			// tryGenericPrefixMatchAt for how its own internal backtracking
			// choices, e.g. "faaao?", are emulated via bounded-range search).
			int found = findTopLevelLookbehind(translated);
			if (found <= 0) return null;
			genericPrefixText = translated.substring(0, found);
			lbStart = found;
		}

		if (lbStart + 4 > translated.length()
				|| translated.charAt(lbStart) != '(' || translated.charAt(lbStart + 1) != '?'
				|| translated.charAt(lbStart + 2) != '<') {
			return null;
		}
		char kind = translated.charAt(lbStart + 3);
		boolean negative;
		if (kind == '=') {
			negative = false;
		} else if (kind == '!') {
			negative = true;
		} else {
			return null;
		}

		int lbEnd;
		try {
			lbEnd = LookbehindReversal.scanGroup(translated, lbStart);
		} catch (RuntimeException e) {
			return null;
		}
		if (lbEnd <= lbStart + 4 || lbEnd > translated.length()) {
			return null;
		}

		String body = translated.substring(lbStart + 4, lbEnd - 1);
		String suffix = translated.substring(lbEnd);
		if (body.isEmpty()) {
			return null;
		}

		Regex genericPrefixRegex = null;
		if (genericPrefixText != null) {
			try {
				byte[] gb = toUtf16BEBytes(genericPrefixText);
				genericPrefixRegex = new Regex(gb, 0, gb.length, options, joniEncoding, JS_SYNTAX);
			} catch (RuntimeException e) {
				return null;
			}
		}

		LookbehindReversal rev;
		LBNode bodyNode;
		try {
			rev = LookbehindReversal.reverse(body, 0);
			bodyNode = compileLBFragment(rev.reversedText, options,
					new java.util.HashSet<>(rev.groupNumberToSyntheticName.values()));
		} catch (RuntimeException e) {
			// Couldn't reverse/compile this specific body (e.g. it uses a
			// construct the reversal transform doesn't recognize) - fall
			// back to the original Joni error rather than guess.
			return null;
		}
		if (bodyNode == null) {
			// BODY's reversed text still isn't Joni-compilable even after
			// compileLBFragment()'s own recursive peeling attempt (e.g. the
			// nested lookbehind it found doesn't fit the narrow shape that
			// recursion supports either) - same graceful degradation as any
			// other unrecognized shape.
			return null;
		}

		CustomLookbehind cl = new CustomLookbehind();
		cl.negative = negative;
		cl.prefixDotStar = prefixDotStar;
		cl.genericPrefixRegex = genericPrefixRegex;
		cl.bodyNode = bodyNode;
		cl.bodyGroupSyntheticNames = rev.groupNumberToSyntheticName;
		cl.suffixGroupOffset = rev.groupCount;
		cl.compileOptions = options;
		cl.totalGroupCount = countCapturingGroups(translated);

		for (Map.Entry<Integer, String> e : rev.groupNumberToOriginalName.entrySet()) {
			namedGroups.put(e.getValue(), e.getKey());
		}

		boolean suffixCrossesIntoBody = suffixReferencesGroupsUpTo(suffix, rev.groupCount, rev.groupNumberToOriginalName.values());
		if (suffixCrossesIntoBody) {
			cl.suffixSourceForBridging = suffix;
		} else {
			String renumberedSuffix = renumberSuffixBackreferences(suffix, rev.groupCount);
			try {
				byte[] suffixBytes = toUtf16BEBytes(renumberedSuffix);
				cl.suffixRegex = new Regex(suffixBytes, 0, suffixBytes.length, options, joniEncoding, JS_SYNTAX);
			} catch (RuntimeException e) {
				return null;
			}
			extractNamedGroups(cl.suffixRegex, cl.suffixGroupOffset, namedGroups);
		}

		return cl;
	}

	// A compiled, possibly-recursively-split lookbehind body fragment. Most
	// bodies are a single `flatNode` - Joni compiles the reversed text
	// directly with no further work needed. A body like
	// "(?<=a(?=([bc]{2}(?<!a{2}))d)\\w{3})" reverses (via LookbehindReversal)
	// into "\\w{3}(?<=d(?<g1>(?!a{2})[bc]{2}))a" - which STILL isn't
	// Joni-compilable, because the nested lookbehind it now contains has a
	// lookahead inside it (same ALLOWED_IN_LB restriction as the outer
	// pattern, just one level down: reversing the OUTER assertion swapped
	// its type but couldn't remove a lookahead genuinely nested two levels
	// deep). compileLBFragment() handles this by peeling that inner
	// lookbehind off too, recursively: split the fragment into
	// prefixRegex + "(?<=INNER)"/"(?<!INNER)" + suffixRegex, reverse INNER
	// (a second, independent LookbehindReversal.reverse() call) exactly the
	// same way the outer body was reversed, and recurse. Named groups
	// minted by an ENCLOSING reversal pass ("gN" markers - see
	// LookbehindReversal's preserveNames) are passed through unchanged so a
	// group found this way still resolves under the SAME synthetic name the
	// top-level CustomLookbehind.bodyGroupSyntheticNames map expects.
	private static final class LBNode {
		Regex flatRegex; // leaf: compiles and matches directly
		// lookbehind split: prefixRegex, then assert INNER holds
		// (recursively, in the OPPOSITE reading direction) ending at the
		// checkpoint (a zero-width assertion, contributes nothing to the
		// overall match's own consumed span), then suffixRegex starting
		// there.
		Regex prefixRegex;
		boolean innerNegative;
		LBNode innerNode;
		Regex suffixRegex;
		// group split: seqPrefixRegex CONSUMES, then seqGroupInner CONSUMES
		// too (SAME reading direction and buffer - a plain group boundary,
		// unlike a lookaround assertion, doesn't flip direction), its own
		// match extending the overall match and (if seqGroupSyntheticName
		// is non-null) recorded as that group's own capture, then
		// seqSuffixRegex consumes from there. Used when the failing
		// construct is nested inside an ordinary group rather than sitting
		// at this fragment's own top level (e.g. "(?<!a*)" inside
		// "(?<g1>[bc]{2}(?<!a*))" - variable-length, so Joni rejects it
		// regardless of nesting, and findTopLevelLookbehind() can't find it
		// directly since it's one level deeper than this fragment's depth
		// 0). Recursing into the group's own inner text as a FRESH fragment
		// makes the failing construct depth-0 relative to THAT recursive
		// call, where the ordinary lookbehind-split path above handles it.
		Regex seqPrefixRegex;
		LBNode seqGroupInner;
		String seqGroupSyntheticName;
		Regex seqSuffixRegex;
	}

	// Finds the first DEPTH-0 "(?<=" / "(?<!" in `s`, or -1 if none.
	private static int findTopLevelLookbehind(String s) {
		int depth = 0;
		boolean inCC = false;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '\\') { i++; continue; }
			if (inCC) {
				if (c == ']') inCC = false;
				continue;
			}
			if (c == '[') {
				inCC = true;
			} else if (c == '(') {
				if (depth == 0 && i + 3 < s.length() && s.charAt(i + 1) == '?' && s.charAt(i + 2) == '<'
						&& (s.charAt(i + 3) == '=' || s.charAt(i + 3) == '!')) {
					return i;
				}
				depth++;
			} else if (c == ')') {
				depth--;
			}
		}
		return -1;
	}

	// Finds the first DEPTH-0 group of any kind EXCEPT a lookaround
	// ("(?=" / "(?!" / "(?<=" / "(?<!" - those are findTopLevelLookbehind()'s
	// job, tried first) - i.e. a plain "(", non-capturing "(?:", or named
	// "(?<name>" group. Returns {groupStart, innerStart, innerEnd,
	// groupEnd} or null if none.
	private static int[] findFirstTopLevelGroup(String s) {
		int depth = 0;
		boolean inCC = false;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '\\') { i++; continue; }
			if (inCC) {
				if (c == ']') inCC = false;
				continue;
			}
			if (c == '[') {
				inCC = true;
			} else if (c == '(') {
				if (depth == 0) {
					boolean isLookaround = i + 2 < s.length() && s.charAt(i + 1) == '?'
							&& (s.charAt(i + 2) == '=' || s.charAt(i + 2) == '!'
								|| (s.charAt(i + 2) == '<' && i + 3 < s.length()
									&& (s.charAt(i + 3) == '=' || s.charAt(i + 3) == '!')));
					if (!isLookaround) {
						int innerStart;
						if (i + 1 < s.length() && s.charAt(i + 1) == '?') {
							if (i + 2 < s.length() && s.charAt(i + 2) == ':') {
								innerStart = i + 3;
							} else {
								int nameEnd = s.indexOf('>', i + 3);
								if (nameEnd < 0) return null;
								innerStart = nameEnd + 1;
							}
						} else {
							innerStart = i + 1;
						}
						int groupEnd;
						try {
							groupEnd = LookbehindReversal.scanGroup(s, i);
						} catch (RuntimeException e) {
							return null;
						}
						return new int[]{i, innerStart, groupEnd - 1, groupEnd};
					}
				}
				depth++;
			} else if (c == ')') {
				depth--;
			}
		}
		return null;
	}

	// null for a plain "(" or non-capturing "(?:" group; the name for a
	// named "(?<name>" one - fragments this recurses over only ever
	// contain synthetic "gN" names (LookbehindReversal renames every
	// capturing group, whatever its original syntax), never a bare "(".
	private static String groupSyntheticNameAt(String s, int groupStart) {
		if (s.charAt(groupStart + 1) != '?' || s.charAt(groupStart + 2) != '<') return null;
		int nameEnd = s.indexOf('>', groupStart + 3);
		return nameEnd < 0 ? null : s.substring(groupStart + 3, nameEnd);
	}

	// Tries a flat compile of `fragmentText` first; on Joni's specific
	// "invalid pattern in look-behind" rejection, peels one nested
	// (?<=X)/(?<!X) off (see LBNode's comment) and recurses into X.
	// `preserveNames` carries every synthetic "gN" name minted so far, up
	// this recursion chain, so nested reverse() calls treat them as
	// pass-through instead of trying to (re)number them as fresh groups.
	private LBNode compileLBFragment(String fragmentText, int options, java.util.Set<String> preserveNames) {
		try {
			byte[] bytes = toUtf16BEBytes(fragmentText);
			Regex re = new Regex(bytes, 0, bytes.length, options, joniEncoding, JS_SYNTAX);
			LBNode leaf = new LBNode();
			leaf.flatRegex = re;
			return leaf;
		} catch (RuntimeException ex) {
			String msg = ex.getMessage();
			if (msg == null || !msg.contains("invalid pattern in look-behind")) {
				return null;
			}
		}

		int idx = findTopLevelLookbehind(fragmentText);
		if (idx < 0) {
			return compileLBFragmentViaGroup(fragmentText, options, preserveNames);
		}
		boolean negative = fragmentText.charAt(idx + 3) == '!';
		int end;
		try {
			end = LookbehindReversal.scanGroup(fragmentText, idx);
		} catch (RuntimeException e) {
			return null;
		}
		if (end <= idx + 4 || end > fragmentText.length()) return null;

		String prefixText = fragmentText.substring(0, idx);
		String innerBodyText = fragmentText.substring(idx + 4, end - 1);
		String suffixText = fragmentText.substring(end);
		if (innerBodyText.isEmpty()) return null;

		Regex prefixRegex;
		Regex suffixRegex;
		try {
			byte[] pb = toUtf16BEBytes(prefixText);
			prefixRegex = new Regex(pb, 0, pb.length, options, joniEncoding, JS_SYNTAX);
			byte[] sb = toUtf16BEBytes(suffixText);
			suffixRegex = new Regex(sb, 0, sb.length, options, joniEncoding, JS_SYNTAX);
		} catch (RuntimeException e) {
			// PREFIX/SUFFIX (around the nested lookbehind, at THIS
			// fragment's own level) don't themselves get the recursive
			// treatment - only the lookbehind body itself does. Falling
			// through here (rather than recursing further) keeps this
			// bounded to the shapes actually seen in practice.
			return null;
		}

		LookbehindReversal innerRev;
		LBNode innerNode;
		try {
			innerRev = LookbehindReversal.reverse(innerBodyText, 0, preserveNames);
			java.util.Set<String> nextPreserve = new java.util.HashSet<>(preserveNames);
			nextPreserve.addAll(innerRev.groupNumberToSyntheticName.values());
			innerNode = compileLBFragment(innerRev.reversedText, options, nextPreserve);
		} catch (RuntimeException e) {
			return null;
		}
		if (innerNode == null) return null;

		LBNode split = new LBNode();
		split.prefixRegex = prefixRegex;
		split.innerNegative = negative;
		split.innerNode = innerNode;
		split.suffixRegex = suffixRegex;
		return split;
	}

	// Fallback for when the failing construct isn't at THIS fragment's own
	// top level at all (findTopLevelLookbehind() found nothing) but is
	// nested one level inside an ordinary group instead - see LBNode's
	// seqPrefixRegex/seqGroupInner comment for why recursing into the
	// group's own inner text (a fresh fragment, so the failing construct
	// becomes depth-0 relative to it) resolves this.
	private LBNode compileLBFragmentViaGroup(String fragmentText, int options, java.util.Set<String> preserveNames) {
		int[] grp = findFirstTopLevelGroup(fragmentText);
		if (grp == null) return null;
		String prefixText = fragmentText.substring(0, grp[0]);
		String innerText = fragmentText.substring(grp[1], grp[2]);
		String suffixText = fragmentText.substring(grp[3]);
		String groupName = groupSyntheticNameAt(fragmentText, grp[0]);

		Regex prefixRegex;
		Regex suffixRegex;
		try {
			byte[] pb = toUtf16BEBytes(prefixText);
			prefixRegex = new Regex(pb, 0, pb.length, options, joniEncoding, JS_SYNTAX);
			byte[] sb = toUtf16BEBytes(suffixText);
			suffixRegex = new Regex(sb, 0, sb.length, options, joniEncoding, JS_SYNTAX);
		} catch (RuntimeException e) {
			return null;
		}

		// No LookbehindReversal.reverse() call here - a plain group
		// boundary doesn't flip reading direction, so the group's inner
		// text is already correctly oriented for a fresh compileLBFragment
		// pass (its own top-level constructs, e.g. "(?<!a*)" here, are
		// simply depth-0 relative to THIS recursive call).
		LBNode innerNode = compileLBFragment(innerText, options, preserveNames);
		if (innerNode == null) return null;

		LBNode seq = new LBNode();
		seq.seqPrefixRegex = prefixRegex;
		seq.seqGroupInner = innerNode;
		seq.seqGroupSyntheticName = groupName;
		seq.seqSuffixRegex = suffixRegex;
		return seq;
	}

	// Converts a char position in one of the two GLOBAL buffers (the real
	// subject string itself for sign +1, or its full reversal for sign -1 -
	// see LBBuffers) into an absolute char position in the real subject
	// string. Both buffers span the WHOLE string, not just some
	// level-local truncation: a nested assertion inside a lookbehind's body
	// can legitimately peek at content on the OTHER side of an enclosing
	// checkpoint (e.g. "^f[oa]+(?=o)" as a lookbehind body has its trailing
	// (?=o) checking the character exactly AT the outer checkpoint, which
	// isn't part of the lookbehind's own "prefix territory") - per spec,
	// lookaround assertions aren't confined to whatever region encloses
	// them, so the buffer they search must be the whole string, with only
	// the ANCHOR (where each level's own matching starts) moving as
	// recursion descends and the reading direction alternately flips.
	private static final class LBTransform {
		final int sign; // +1 (buffer = str) or -1 (buffer = reverse(str))
		final int strLen;
		LBTransform(int sign, int strLen) { this.sign = sign; this.strLen = strLen; }
		int toOriginal(int bufLocalPos) { return sign > 0 ? bufLocalPos : strLen - bufLocalPos; }
		int anchorFor(int originalPos) { return sign > 0 ? originalPos : strLen - originalPos; }
		LBTransform flipped() { return new LBTransform(-sign, strLen); }
	}

	private static final class LBMatchResult {
		int endChar; // buf-local position just past the match
		Map<Integer, int[]> groupSpans = new java.util.HashMap<>(); // original group number -> [charBeg, charEnd) in the REAL subject string
	}

	// The two global buffers every LBTransform reads from - computed once
	// per checkLookbehind() call and threaded through the recursion instead
	// of rebuilt (reversed) per level.
	private static final class LBBuffers {
		final String forward; // == the real subject string
		final String reversed; // == reverse(forward)
		LBBuffers(String str) {
			forward = str;
			reversed = new StringBuilder(str).reverse().toString();
		}
		String forSign(int sign) { return sign > 0 ? forward : reversed; }
	}

	// Extracts every synthetic "gN" capturing group's span from `region`
	// (a just-completed match against `re`), translating each to the REAL
	// subject string's absolute char coordinates via `t`, and merges them
	// into `into`. Shared by both LBNode leaf-matching and prefix/suffix
	// matching within a split node - all of them can carry "gN" captures.
	private void collectLBGroupSpans(Regex re, Region region, LBTransform t, Map<String, Integer> syntheticToOriginal, Map<Integer, int[]> into) {
		Iterator<NameEntry> it = re.namedBackrefIterator();
		while (it.hasNext()) {
			NameEntry entry = it.next();
			String name = fromUtf16BEBytes(entry.name, entry.nameP, entry.nameEnd - entry.nameP);
			Integer origGroup = syntheticToOriginal.get(name);
			if (origGroup == null) continue;
			int[] refs = entry.getBackRefs();
			if (refs.length == 0) continue;
			int rBeg = region.getBeg(refs[0]);
			int rEnd = region.getEnd(refs[0]);
			if (rBeg < 0) continue;
			int localBeg = rBeg / 2;
			int localEnd = rEnd / 2;
			int origA = t.toOriginal(localBeg);
			int origB = t.toOriginal(localEnd);
			into.put(origGroup, t.sign < 0 ? new int[]{origB, origA} : new int[]{origA, origB});
		}
	}

	// Tries to match `node` anchored at buf-local position `startChar`
	// within `bufs.forSign(t.sign)`. Returns null on no match. On success,
	// returns the buf-local end position plus every "gN" group's span
	// translated to REAL subject string coordinates.
	private LBMatchResult matchLBNode(LBNode node, LBBuffers bufs, int startChar, LBTransform t, Map<String, Integer> syntheticToOriginal) {
		String buf = bufs.forSign(t.sign);
		byte[] bufBytes = getBytes(buf);
		if (node.flatRegex != null) {
			int startByte = charIndexToByteIndex(buf, bufBytes, startChar);
			Matcher m = node.flatRegex.matcher(bufBytes, 0, bufBytes.length);
			int r = m.match(startByte, bufBytes.length, Option.NONE);
			if (r < 0) return null;
			Region region = m.getEagerRegion();
			LBMatchResult result = new LBMatchResult();
			result.endChar = byteIndexToCharIndex(buf, bufBytes, region.getEnd(0));
			collectLBGroupSpans(node.flatRegex, region, t, syntheticToOriginal, result.groupSpans);
			return result;
		}

		if (node.seqPrefixRegex != null) {
			int startByte = charIndexToByteIndex(buf, bufBytes, startChar);
			Matcher pm = node.seqPrefixRegex.matcher(bufBytes, 0, bufBytes.length);
			int pr = pm.match(startByte, bufBytes.length, Option.NONE);
			if (pr < 0) return null;
			Region prefixRegion = pm.getEagerRegion();
			int checkpoint = byteIndexToCharIndex(buf, bufBytes, prefixRegion.getEnd(0));

			// SAME buffer/direction as this level - a plain group boundary
			// doesn't flip reading direction the way a lookaround does.
			LBMatchResult innerResult = matchLBNode(node.seqGroupInner, bufs, checkpoint, t, syntheticToOriginal);
			if (innerResult == null) return null;

			int afterGroupByte = charIndexToByteIndex(buf, bufBytes, innerResult.endChar);
			Matcher sm = node.seqSuffixRegex.matcher(bufBytes, 0, bufBytes.length);
			int sr = sm.match(afterGroupByte, bufBytes.length, Option.NONE);
			if (sr < 0) return null;
			Region suffixRegion = sm.getEagerRegion();

			LBMatchResult result = new LBMatchResult();
			result.endChar = byteIndexToCharIndex(buf, bufBytes, suffixRegion.getEnd(0));
			collectLBGroupSpans(node.seqPrefixRegex, prefixRegion, t, syntheticToOriginal, result.groupSpans);
			result.groupSpans.putAll(innerResult.groupSpans);
			if (node.seqGroupSyntheticName != null) {
				Integer origGroup = syntheticToOriginal.get(node.seqGroupSyntheticName);
				if (origGroup != null) {
					int origA = t.toOriginal(checkpoint);
					int origB = t.toOriginal(innerResult.endChar);
					result.groupSpans.put(origGroup, t.sign < 0 ? new int[]{origB, origA} : new int[]{origA, origB});
				}
			}
			collectLBGroupSpans(node.seqSuffixRegex, suffixRegion, t, syntheticToOriginal, result.groupSpans);
			return result;
		}

		int startByte = charIndexToByteIndex(buf, bufBytes, startChar);
		Matcher pm = node.prefixRegex.matcher(bufBytes, 0, bufBytes.length);
		int pr = pm.match(startByte, bufBytes.length, Option.NONE);
		if (pr < 0) return null;
		Region prefixRegion = pm.getEagerRegion();
		int checkpoint = byteIndexToCharIndex(buf, bufBytes, prefixRegion.getEnd(0));

		LBTransform childT = t.flipped();
		int childAnchor = childT.anchorFor(t.toOriginal(checkpoint));
		LBMatchResult innerResult = matchLBNode(node.innerNode, bufs, childAnchor, childT, syntheticToOriginal);
		boolean innerHolds = innerResult != null;
		if (node.innerNegative == innerHolds) return null;

		int checkpointByte = charIndexToByteIndex(buf, bufBytes, checkpoint);
		Matcher sm = node.suffixRegex.matcher(bufBytes, 0, bufBytes.length);
		int sr = sm.match(checkpointByte, bufBytes.length, Option.NONE);
		if (sr < 0) return null;
		Region suffixRegion = sm.getEagerRegion();

		LBMatchResult result = new LBMatchResult();
		result.endChar = byteIndexToCharIndex(buf, bufBytes, suffixRegion.getEnd(0));
		collectLBGroupSpans(node.prefixRegex, prefixRegion, t, syntheticToOriginal, result.groupSpans);
		if (!node.innerNegative && innerResult != null) {
			result.groupSpans.putAll(innerResult.groupSpans);
		}
		collectLBGroupSpans(node.suffixRegex, suffixRegion, t, syntheticToOriginal, result.groupSpans);
		return result;
	}

	// True if `text` contains a backreference (\N or \k<name>) targeting a
	// group number <= bodyGroupCount (or a name body defines) - such a
	// reference crosses from SUFFIX into BODY, which are separately-compiled
	// Regex objects Joni can't resolve a reference across on its own.
	private static boolean suffixReferencesGroupsUpTo(String text, int bodyGroupCount, java.util.Collection<String> bodyGroupNames) {
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c != '\\' || i + 1 >= text.length()) continue;
			char next = text.charAt(i + 1);
			if (next >= '1' && next <= '9') {
				int j = i + 1;
				while (j < text.length() && Character.isDigit(text.charAt(j))) j++;
				int n = Integer.parseInt(text.substring(i + 1, j));
				if (n <= bodyGroupCount) return true;
				i = j - 1;
			} else if (next == 'k' && i + 2 < text.length() && text.charAt(i + 2) == '<') {
				int end = text.indexOf('>', i + 3);
				if (end > 0 && bodyGroupNames.contains(text.substring(i + 3, end))) return true;
				i = end;
			} else {
				i++;
			}
		}
		return false;
	}

	// Shifts every \N backreference in SUFFIX text down by bodyGroupCount,
	// converting it from the OVERALL pattern's numbering to the numbering
	// SUFFIX will get when compiled as its own standalone Regex (whose
	// groups always start at 1 regardless of what number they represent in
	// the original pattern). Only called once suffixReferencesGroupsUpTo()
	// has confirmed no backreference here crosses into body, so every \N
	// found is guaranteed > bodyGroupCount.
	private static String renumberSuffixBackreferences(String text, int bodyGroupCount) {
		if (bodyGroupCount == 0) return text;
		StringBuilder out = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '\\' && i + 1 < text.length() && Character.isDigit(text.charAt(i + 1)) && text.charAt(i + 1) != '0') {
				int j = i + 1;
				while (j < text.length() && Character.isDigit(text.charAt(j))) j++;
				int n = Integer.parseInt(text.substring(i + 1, j));
				out.append('\\').append(n - bodyGroupCount);
				i = j - 1;
			} else {
				out.append(c);
			}
		}
		return out.toString();
	}

	// JS allows the same GroupSpecifier name to be reused ONLY when every pair of
	// same-named groups sits in different Alternatives of some Disjunction they
	// both fall under (e.g. "(?<x>a)|(?<x>y)") - never as plain siblings in the
	// same alternative (e.g. "(?<x>a)(?<x>y)"). Joni's own ALLOW_MULTIPLEX_
	// DEFINITION_NAME syntax flag (enabled on JS_SYNTAX so Joni's matcher can
	// natively resolve \k<name> against whichever duplicate actually
	// participates - see JS_SYNTAX/extractNamedGroups()) is more permissive
	// than the spec and happily compiles the sequential-siblings case too, so
	// this reimplements just the spec's mutual-exclusion check ourselves,
	// ahead of the real compile, and rejects it exactly as duplicate-named-
	// capturing-groups-syntax.js (test262) expects.
	//
	// Walks the raw pattern text tracking, for every open group (of ANY kind -
	// capturing, non-capturing, lookaround), a small "frame" of (frameId,
	// currentAlternativeIndex). Each named GroupSpecifier's occurrence records
	// a snapshot of the frame stack in effect at that point (outer to inner).
	// Two occurrences of the same name are mutually exclusive iff, at the
	// shallowest frame they share, their recorded alternative indices differ -
	// see mutuallyExclusive() below for the exact walk.
	private void validateDuplicateGroupNames(String source) {
		Map<String, List<List<int[]>>> pathsByName = new LinkedHashMap<>();
		List<int[]> stack = new ArrayList<>();
		stack.add(new int[]{0, 0});
		int nextFrameId = 1;
		boolean inClass = false;
		int len = source.length();
		for (int i = 0; i < len; i++) {
			char c = source.charAt(i);
			if (c == '\\') {
				i++;
				continue;
			}
			if (inClass) {
				if (c == ']') inClass = false;
				continue;
			}
			if (c == '[') {
				inClass = true;
			} else if (c == '|') {
				stack.get(stack.size() - 1)[1]++;
			} else if (c == ')') {
				if (stack.size() <= 1) return; // unbalanced - let Joni's own compile surface the real error
				stack.remove(stack.size() - 1);
			} else if (c == '(') {
				if (i + 1 < len && source.charAt(i + 1) == '?') {
					char k = i + 2 < len ? source.charAt(i + 2) : 0;
					if (k == '=' || k == '!') {
						stack.add(new int[]{nextFrameId++, 0});
						i += 2;
					} else if (k == ':') {
						stack.add(new int[]{nextFrameId++, 0});
						i += 2;
					} else if (k == '<' && i + 3 < len && (source.charAt(i + 3) == '=' || source.charAt(i + 3) == '!')) {
						stack.add(new int[]{nextFrameId++, 0});
						i += 3;
					} else if (k == '<') {
						int nameStart = i + 3;
						int closeAngle = source.indexOf('>', nameStart);
						if (closeAngle < 0) return; // malformed - let Joni's own compile surface the real error
						String name = source.substring(nameStart, closeAngle);
						List<int[]> path = new ArrayList<>(stack.size());
						for (int[] frame : stack) path.add(new int[]{frame[0], frame[1]});
						pathsByName.computeIfAbsent(name, n -> new ArrayList<>()).add(path);
						stack.add(new int[]{nextFrameId++, 0});
						i = closeAngle;
					} else {
						stack.add(new int[]{nextFrameId++, 0});
						i++;
					}
				} else {
					stack.add(new int[]{nextFrameId++, 0});
				}
			}
		}

		for (Map.Entry<String, List<List<int[]>>> e : pathsByName.entrySet()) {
			List<List<int[]>> paths = e.getValue();
			if (paths.size() <= 1) continue;
			for (int a = 0; a < paths.size(); a++) {
				for (int b = a + 1; b < paths.size(); b++) {
					if (!mutuallyExclusive(paths.get(a), paths.get(b))) {
						throw RuntimeUtil.syntaxError("Invalid RegExp '/{0}/{1}': duplicate capture group name <{2}>",
								source, regExp.getFlags(), e.getKey());
					}
				}
			}
		}
	}

	private static boolean mutuallyExclusive(List<int[]> pathA, List<int[]> pathB) {
		int i = 0;
		while (i < pathA.size() && i < pathB.size()) {
			int[] a = pathA.get(i);
			int[] b = pathB.get(i);
			if (a[0] != b[0]) return false;
			if (a[1] != b[1]) return true;
			i++;
		}
		return false;
	}

	private void extractNamedGroups(Regex re, int numberOffset, Map<String, Integer> into) {
		extractNamedGroups(re, numberOffset, into, multiplexNamedGroups);
	}

	private void extractNamedGroups(Regex re, int numberOffset, Map<String, Integer> into, Map<String, int[]> multiplexInto) {
		Iterator<NameEntry> it = re.namedBackrefIterator();
		while (it.hasNext()) {
			NameEntry entry = it.next();
			String name = fromUtf16BEBytes(entry.name, entry.nameP, entry.nameEnd - entry.nameP);
			int[] refs = entry.getBackRefs();
			if (refs.length > 0) {
				into.put(name, refs[0] + numberOffset);
				// Duplicate named groups in mutually exclusive alternatives
				// (e.g. "(?<x>a)|(?<x>y)") - Joni's ALLOW_MULTIPLEX_DEFINITION_NAME
				// syntax flag merges every same-named GroupSpecifier into one
				// NameEntry with ALL of their group numbers as backrefs, so at
				// most one of them can actually have captured for any given
				// match (that's the whole point of requiring mutual exclusion -
				// see validateDuplicateGroupNames()). "into" alone can only
				// remember one number per name, so multiplexNamedGroups records
				// every candidate for extractMatchNamedGroups()/
				// extractMatchNamedGroupIndices()/the $<name> replacement path
				// to pick whichever one actually matched.
				if (refs.length > 1) {
					int[] offsetRefs = new int[refs.length];
					for (int i = 0; i < refs.length; i++) {
						offsetRefs[i] = refs[i] + numberOffset;
					}
					multiplexInto.put(name, offsetRefs);
				}
			}
		}
	}

	// Counts capturing groups (both plain "(" and named "(?<name>", but not
	// non-capturing "(?:", lookahead "(?=" / "(?!", or lookbehind "(?<=" /
	// "(?<!") for validating a \N DecimalEscape's target number in unicode
	// mode - JS spec allows a backreference to a group defined LATER in the
	// pattern (a forward reference), so only a total count is needed, not a
	// position-aware walk.
	private static int countCapturingGroups(String source) {
		int count = 0;
		boolean inCC = false;
		for (int i = 0; i < source.length(); i++) {
			char c = source.charAt(i);
			if (c == '\\') {
				i++;
				continue;
			}
			if (c == '[') {
				inCC = true;
			} else if (c == ']') {
				inCC = false;
			} else if (c == '(' && !inCC) {
				if (i + 1 < source.length() && source.charAt(i + 1) == '?') {
					if (i + 3 < source.length() && source.charAt(i + 2) == '<'
							&& source.charAt(i + 3) != '=' && source.charAt(i + 3) != '!') {
						count++;
					}
				} else {
					count++;
				}
			}
		}
		return count;
	}

	private String translatePattern(String source) {
		boolean unicodeMode = regExp.isUnicode() || regExp.isUnicodeSets();

		if (regExp.isUnicodeSets()) {
			throw RuntimeUtil.syntaxError(
					"Unicode Sets mode (v flag) is not yet fully implemented. " +
					"Please use the u flag for unicode mode.");
		}

		StringBuilder result = new StringBuilder(source.length());
		boolean inCC = false;
		int capturingGroupCount = countCapturingGroups(source);
		// The ES2025 "RegExp modifiers" (?i:...)/(?-i:...) proposal lets a
		// group locally override ignoreCase for everything inside it - this
		// tracks the EFFECTIVE ignoreCase at whatever point in the pattern
		// is currently being translated, one stack entry per open group
		// (of ANY kind - modifier groups change the value, everything else
		// just inherits the enclosing one, but still needs an entry so `)`
		// pops back to the right level). Needed for \\w/\\W/\\b/\\B - see
		// their handling below for why they can't just use Joni's own
		// compile-time Option.IGNORECASE (which is fixed for the whole
		// pattern and can't reflect a LOCAL override).
		java.util.ArrayDeque<Boolean> ignoreCaseStack = new java.util.ArrayDeque<>();
		ignoreCaseStack.push(regExp.isIgnoreCase());

		for (int i = 0; i < source.length(); i++) {
			char c = source.charAt(i);
			switch (c) {
				case '\\':
					if (i + 1 >= source.length()) {
						result.append('\\');
						break;
					}
					char next = source.charAt(i + 1);
					if (next == 'x') {
						// \xHH - JavaScript hex escape (code point U+00HH). Emitted as
						// a \\uHHHH escape (Joni's OP2_ESC_U_HEX4, the same flag JS's
						// own \\u escape maps to) rather than Joni's \xHH byte escape
						// (OP_ESC_X_HEX2) - the latter is designed for byte-oriented
						// encodings, where a single escaped byte combines with
						// whatever bytes surround it in the pattern to form one
						// multi-byte character; under a 2-bytes-per-unit encoding
						// (UTF-16BE/LenientUTF16BEEncoding) a lone \x00 has no partner
						// byte to combine with and Joni's compiler rejects it ("too
						// short multibyte code string") - confirmed via /\0/u, whose
						// entire pattern is just that one escape. \\uHHHH carries the
						// full 16-bit value in the escape itself, so Joni can encode
						// it directly with no adjacent-byte dependency.
						if (i + 3 < source.length()) {
							String hex = source.substring(i + 2, i + 4);
							try {
								int val = Integer.parseInt(hex, 16);
								appendUnicodeEscape(result, val);
								i += 3;
								break;
							} catch (NumberFormatException e) {
								// Not valid hex - pass through
							}
						}
						// Not followed by exactly 2 valid hex digits: a legacy
						// IdentityEscape (literal 'x') in non-unicode mode, but an
						// early SyntaxError in unicode mode - RegExp("\\x","u") must
						// throw, not silently match literal "x".
						if (unicodeMode) {
							throw RuntimeUtil.syntaxError("Invalid escape sequence: \\x");
						}
						result.append('\\').append(next);
						i++;
					} else if (next == 'u') {
						i++;
						i = translateUnicodeEscape(source, i, result, unicodeMode);
					} else if (next == 'c') {
						char after = (i + 2 < source.length()) ? source.charAt(i + 2) : '\0';
						boolean validControlLetter = (after >= 'a' && after <= 'z') || (after >= 'A' && after <= 'Z');
						if (!validControlLetter && unicodeMode) {
							// \c not followed by a ControlLetter is an early
							// SyntaxError in unicode mode (IdentityEscape doesn't
							// cover 'c' there) - isJsRegexEscape() below would
							// otherwise let this fall through as an unchecked
							// passthrough regardless of what follows the 'c'.
							throw RuntimeUtil.syntaxError("Invalid escape sequence: \\c");
						}
						result.append('\\').append(next);
						i++;
					} else if (next == 'k') {
						boolean validNamedBackref = i + 2 < source.length() && source.charAt(i + 2) == '<';
						if (!validNamedBackref && unicodeMode) {
							// Same story as \c above: \k not followed by a
							// GroupName's opening "<" is an early SyntaxError in
							// unicode mode - isJsRegexEscape() would otherwise
							// let it fall through as an unchecked passthrough.
							throw RuntimeUtil.syntaxError("Invalid escape sequence: \\k");
						}
						result.append('\\').append(next);
						i++;
					} else if (next == 'w' || next == 'W') {
						// Replaces Joni's own \\w/\\W entirely (not just under a
						// modifier scope) - see buildWordCharClass()'s comment for
						// why: Joni's own \\w is Unicode-category-based, far wider
						// than JS's actual word-char set, independent of ignoreCase.
						result.append(buildWordCharClass(ignoreCaseStack.peek(), next == 'W', inCC));
						i++;
					} else if (!inCC && (next == 'b' || next == 'B')) {
						// \\b/\\B as an Assertion (outside a class - \\b inside a
						// class means backspace instead, left untouched below).
						// Same rationale as \\w/\\W just above, via
						// buildWordBoundaryAssertion() - plus this is the ONLY way
						// to make \\b respect a LOCAL (?i:...)/(?-i:...) override:
						// Joni compiles the whole pattern with one fixed
						// Option.IGNORECASE, so its own \\b (even if it were
						// otherwise spec-exact) has no way to see a scope-local
						// override at all.
						//
						// Special case: "(?<=\\b)" / "(?<=\\B)" / "(?<!\\b)" /
						// "(?<!\\B)" - a lookbehind whose ENTIRE body is just
						// this escape. Since \\b/\\B are zero-width, "(?<=X)"
						// for a zero-width X is mathematically just X itself
						// (matching X so it ENDS at the current position is
						// the same as X holding AT the current position,
						// because X consumes nothing) - "(?<!X)" is likewise
						// just the negation of X. Simplifying this away
						// matters, not just for tidiness: buildWordBoundaryAssertion()'s
						// own expansion contains lookahead, and feeding that
						// into an outer lookbehind is broken two ways - Joni's
						// native compiler forbids lookahead anywhere inside a
						// lookbehind's tree, and even where the CustomLookbehind
						// fallback (see trySetUpCustomLookbehind) could compile
						// it, that engine only ever hands the body a buffer of
						// characters BEFORE the checkpoint - it has no access
						// to the character AT/after the checkpoint that \\b's
						// own lookahead half needs, since \\b's zero-width point
						// coincides exactly with the lookbehind's own checkpoint.
						boolean negated = next == 'B';
						boolean afterLookbehindOpen = result.length() >= 4
								&& result.charAt(result.length() - 4) == '('
								&& result.charAt(result.length() - 3) == '?'
								&& result.charAt(result.length() - 2) == '<'
								&& (result.charAt(result.length() - 1) == '=' || result.charAt(result.length() - 1) == '!');
						boolean beforeCloseParen = i + 2 < source.length() && source.charAt(i + 2) == ')';
						if (afterLookbehindOpen && beforeCloseParen) {
							boolean lookbehindNegative = result.charAt(result.length() - 1) == '!';
							result.setLength(result.length() - 4);
							result.append(buildWordBoundaryAssertion(ignoreCaseStack.peek(), negated != lookbehindNegative));
							if (ignoreCaseStack.size() > 1) ignoreCaseStack.pop();
							i += 2;
						} else {
							result.append(buildWordBoundaryAssertion(ignoreCaseStack.peek(), negated));
							i++;
						}
					} else if (next == 'B' && inCC && unicodeMode) {
						// \B (unlike \b) has no valid ClassEscape form at all -
						// it's only ever a non-word-boundary Assertion OUTSIDE a
						// class. isJsRegexEscape() includes 'B' unconditionally
						// (correct for the atom/outside-class case), so without
						// this inCC-aware check it would fall through as an
						// unchecked passthrough inside a class too.
						throw RuntimeUtil.syntaxError("Invalid escape sequence: \\B in character class");
					} else if (next == '0') {
						// \0 is handled on its own, separately from \1-\7 below:
						// group numbering starts at 1, so \0 is NEVER a valid
						// backreference and is unambiguously safe to translate
						// directly - unlike \1-\7, which this per-character
						// translation pass can't safely disambiguate from a
						// backreference without knowing the pattern's total
						// capturing-group count (see the \1-\7 branch below).
						if (unicodeMode) {
							// \0 not followed by a digit is allowed even in unicode
							// mode; \0 followed by a digit is a (forbidden) octal
							// escape there.
							if (i + 2 >= source.length() || !Character.isDigit(source.charAt(i + 2))) {
								appendUnicodeEscape(result, 0);
								i++;
							} else {
								throw RuntimeUtil.syntaxError("Octal escapes are not allowed in unicode mode");
							}
						} else {
							// Annex B: \0 can extend into a full octal escape
							// (\0-\377) if followed by up to 2 more octal digits.
							// Emitted as a \\uHHHH escape, not passed through as
							// literal \0 text for Joni's own OP_ESC_OCTAL3 to
							// parse - same "too short multibyte code string"
							// failure as the \xHH case above: Joni's octal escape
							// is also a single-BYTE construct, and a lone \0
							// (S15.10.2.11_A1_T1.js's entire pattern) has no
							// adjacent byte to combine with under a
							// 2-bytes-per-unit encoding.
							int value = 0;
							int j = i + 2;
							for (int k = 0; k < 2 && j < source.length(); k++, j++) {
								char d = source.charAt(j);
								if (d < '0' || d > '7') break;
								value = (value << 3) | (d - '0');
							}
							appendUnicodeEscape(result, value);
							i = j - 1;
						}
					} else if (next >= '1' && next <= '9') {
						// A DecimalEscape (\1, \12, ...) is a backreference to
						// that numbered capturing group if the pattern has at
						// least that many groups (forward references are legal
						// per spec, so this only needs a total COUNT, not
						// position) - valid in either mode, and safe to pass
						// through unchanged to Joni's own OP_DECIMAL_BACKREF
						// handling. When it's NOT a valid backreference: unicode
						// mode has no fallback, so it's an unconditional early
						// SyntaxError; non-unicode mode falls back to a Annex B
						// LegacyOctalEscapeSequence, which backtracks to
						// interpret starting from just the FIRST digit (1-3
						// octal digits, per the same first-digit-0-3-vs-4-7 rule
						// as \0 above) REGARDLESS of how many decimal digits the
						// backreference attempt originally scanned - any digits
						// beyond what the octal escape consumes are left as
						// literal characters for the next loop iteration. Like
						// \0, this is emitted as a \\uHHHH escape rather than
						// passed through as literal octal-digit text: Joni's own
						// OP_ESC_OCTAL3 has the identical "too short multibyte
						// code string" failure under a 2-bytes-per-unit encoding.
						int j = i + 1;
						while (j < source.length() && Character.isDigit(source.charAt(j))) j++;
						int refNumber = Integer.parseInt(source.substring(i + 1, j));
						if (refNumber <= capturingGroupCount) {
							result.append(source, i, j);
							i = j - 1;
						} else if (unicodeMode) {
							throw RuntimeUtil.syntaxError("Invalid backreference: \\{0}", String.valueOf(refNumber));
						} else if (next <= '7') {
							int value = next - '0';
							int maxExtraDigits = next <= '3' ? 2 : 1;
							int k = i + 2;
							for (int d = 0; d < maxExtraDigits && k < source.length(); d++, k++) {
								char ch = source.charAt(k);
								if (ch < '0' || ch > '7') break;
								value = (value << 3) | (ch - '0');
							}
							appendUnicodeEscape(result, value);
							i = k - 1;
						} else {
							// \8 / \9 with no matching group: not a valid octal
							// digit either, so Annex B's IdentityEscape applies -
							// literal '8'/'9', matching how the letter-IdentityEscape
							// branch below treats an unrecognized non-unicode escape.
							result.append(next);
							i++;
						}
					} else if (next == 'p' || next == 'P') {
						if (unicodeMode && i + 2 < source.length() && source.charAt(i + 2) == '{') {
							i += 3; // skip \p{
							int braceStart = i;
							while (i < source.length() && source.charAt(i) != '}') {
								i++;
							}
							if (i >= source.length()) {
								throw RuntimeUtil.syntaxError("Invalid Unicode property escape");
							}
							String propExpr = source.substring(braceStart, i);
							boolean negated = (next == 'P');
							String dataTableFragment = translatePropertyFromDataTable(propExpr, negated, inCC);
							if (dataTableFragment != null) {
								result.append(dataTableFragment);
							} else {
								String joniProp = translatePropertyForJoni(propExpr);
								if (joniProp == null) {
									throw RuntimeUtil.syntaxError("Invalid Unicode property escape: \\{0}'{'{1}'}'", String.valueOf(next), propExpr);
								}
								result.append(negated ? "\\P{" : "\\p{").append(joniProp).append('}');
							}
						} else if (unicodeMode) {
							throw RuntimeUtil.syntaxError("Invalid Unicode property escape");
						} else {
							// Non-unicode mode: \p is literal 'p'
							result.append(next);
							i++;
						}
					} else if (Character.isLetter(next) && !isJsRegexEscape(next) && next != 'u') {
						if (unicodeMode) {
							throw RuntimeUtil.syntaxError("Invalid escape sequence: \\{0}", String.valueOf(next));
						}
						// Non-unicode: identity escape
						result.append('\\').append(next);
						i++;
					} else {
						result.append('\\').append(next);
						i++;
					}
					break;
				case '(':
					if (!inCC) {
						if (i + 2 < source.length() && source.charAt(i + 1) == '?' && source.charAt(i + 2) == '<'
								&& (i + 3 >= source.length() || (source.charAt(i + 3) != '=' && source.charAt(i + 3) != '!'))) {
							// GroupSpecifier, not a lookbehind - validate the name
							// against JS's real IdentifierStart/IdentifierPart rules
							// before letting Joni's own (much more permissive, e.g.
							// no real Unicode-category check at all) name parsing see
							// it - Joni would otherwise happily accept a name like an
							// emoji or a digit-category character that JS requires to
							// be an early SyntaxError.
							validateGroupName(source, i + 3);
						}
						Boolean modifierIgnoreCase = parseModifierGroupIgnoreCase(source, i, ignoreCaseStack.peek());
						ignoreCaseStack.push(modifierIgnoreCase != null ? modifierIgnoreCase : ignoreCaseStack.peek());
					}
					result.append(c);
					break;
				case ')':
					if (!inCC && ignoreCaseStack.size() > 1) {
						ignoreCaseStack.pop();
					}
					result.append(c);
					break;
				case '[':
					inCC = true;
					result.append(c);
					break;
				case ']':
					inCC = false;
					result.append(c);
					break;
				case '.':
					if (!inCC && !regExp.isDotAll()) {
						// JS dot excludes \n, \r, U+2028, U+2029
						result.append(DOT_REPLACEMENT);
					} else {
						result.append(c);
					}
					break;
				case '^':
					if (!inCC && regExp.isMultiline()) {
						result.append(CARET_MULTILINE);
					} else {
						result.append(c);
					}
					break;
				case '$':
					if (!inCC && regExp.isMultiline()) {
						result.append(DOLLAR_MULTILINE);
					} else {
						result.append(c);
					}
					break;
				default:
					result.append(c);
			}
		}
		return result.toString();
	}

	private static String translatePropertyForJoni(String propExpr) {
		int eqIdx = propExpr.indexOf('=');
		if (eqIdx >= 0) {
			String prop = propExpr.substring(0, eqIdx).trim();
			String value = propExpr.substring(eqIdx + 1).trim();
			switch (prop) {
				case "General_Category": case "gc":
				case "Script": case "sc":
				case "Script_Extensions": case "scx":
					return value;
				default:
					return null;
			}
		}
		// Lone value - pass through directly to Joni (it handles Lu, Letter, Greek, Alphabetic, etc.)
		return propExpr;
	}

	// Ground-truth codepoint data (the same unicode-properties.txt table built
	// for the JDK engine from test262's own generated property-escapes suite -
	// see UnicodePropertyData/UnicodeProperties in the jdk package) takes
	// precedence over Joni's native \p{...} support: Joni's bundled Unicode
	// property tables are close but not byte-for-byte exact matches for every
	// alias test262 exercises (e.g. Script_Extensions), and this table is
	// exact by construction. Returns null (falling back to Joni's native
	// \p{...}/\P{...} support) when the exact property expression isn't in
	// the table.
	private static String translatePropertyFromDataTable(String propExpr, boolean negated, boolean insideCharClass) {
		int[] ranges = UnicodePropertyData.getRanges(propExpr);
		if (ranges == null) {
			return null;
		}

		// Every member of a JS character class (positive or negated)
		// contributes to that class's UNION, never an intersection. Unlike
		// java.util.regex, Joni's JS_SYNTAX does NOT treat a bracket nested
		// inside an already-open class as a plain union (confirmed
		// empirically: even the single-element case [\P{Hex}] alone, with
		// nothing else in the class, matches wrong once nested) - so a
		// negated fragment can't be embedded as a nested [^...] the way the
		// JDK engine's equivalent translate() does. Instead, when merging a
		// NEGATED property into an already-open class, compute its
		// complement ourselves and emit that as a plain (unbracketed) range
		// list - the exact same flat-range-list mechanism already proven to
		// work for the positive case, just fed the complement data instead.
		if (insideCharClass && negated) {
			ranges = complementRanges(ranges);
			negated = false;
		}

		StringBuilder sb = new StringBuilder();
		sb.append(negated ? "[^" : "[");
		for (int i = 0; i < ranges.length; i += 2) {
			appendRangeSplitAtSurrogateBoundary(sb, ranges[i], ranges[i + 1]);
		}
		sb.append(']');

		if (insideCharClass) {
			// negated was already resolved to a plain complement range list
			// above, so this is always the "strip brackets, merge into the
			// enclosing class's list" case.
			return sb.substring(1, sb.length() - 1);
		}
		return sb.toString();
	}

	// Computes the complement of a set of codepoint ranges over the full
	// [0, 0x10FFFF] Unicode range. Sorts and merges overlapping/adjacent
	// ranges first rather than assuming unicode-properties.txt's own ranges
	// are already in that shape - cheap insurance against a data-table
	// entry that isn't, since a wrong complement here would silently match
	// the wrong set rather than fail loudly.
	private static int[] complementRanges(int[] ranges) {
		int pairCount = ranges.length / 2;
		Integer[] order = new Integer[pairCount];
		for (int i = 0; i < pairCount; i++) {
			order[i] = i;
		}
		java.util.Arrays.sort(order, (a, b) -> Integer.compare(ranges[a * 2], ranges[b * 2]));

		java.util.List<Integer> out = new java.util.ArrayList<>();
		int prevEnd = -1;
		for (int idx : order) {
			int start = ranges[idx * 2];
			int end = ranges[idx * 2 + 1];
			if (start > prevEnd + 1) {
				out.add(prevEnd + 1);
				out.add(start - 1);
			}
			if (end > prevEnd) {
				prevEnd = end;
			}
		}
		if (prevEnd < 0x10FFFF) {
			out.add(prevEnd + 1);
			out.add(0x10FFFF);
		}

		int[] result = new int[out.size()];
		for (int i = 0; i < result.length; i++) {
			result[i] = out.get(i);
		}
		return result;
	}

	// Emits a codepoint as a literal character (so no dependency on Joni's
	// own \\x{...}/\\u{...} escape syntax being enabled), escaping it first if
	// it would otherwise be read as character-class syntax rather than a
	// literal member - a real concern here, not a defensive-only check: e.g.
	// the Dash property's own data literally includes U+002D HYPHEN-MINUS.
	private static void appendEscapedCodePoint(StringBuilder sb, int codePoint) {
		if (codePoint == ']' || codePoint == '\\' || codePoint == '^' || codePoint == '-') {
			sb.append('\\');
		}
		sb.appendCodePoint(codePoint);
	}

	private static final int SURROGATE_FIRST = 0xD800;
	private static final int SURROGATE_LAST = 0xDFFF;

	// Emits one codepoint range, splitting it in two at the surrogate-block
	// boundary if needed. Empirically confirmed (via a targeted bisection
	// against Joni's own Regex constructor, not documented Joni/Oniguruma
	// behavior): a character-class range whose START falls inside
	// [U+D800, U+DFFF] and whose END is past U+DFFF fails to compile
	// ("empty range in char class") even though both a range confined
	// entirely to the surrogate block (e.g. a literal backslash-u-D800 to backslash-u-DFFF) and a range that
	// merely spans OVER it from a start below U+D800 (e.g. U+D000-U+E000)
	// compile and match correctly - so this is specifically about a range
	// whose first token is read as a surrogate value, not about surrogates
	// being present in the resulting set at all. Splitting at the boundary
	// (into [start,0xDFFF] and [0xE000,end]) sidesteps the failing case
	// while covering the exact same set of codepoints.
	private static void appendRangeSplitAtSurrogateBoundary(StringBuilder sb, int start, int end) {
		if (start >= SURROGATE_FIRST && start <= SURROGATE_LAST && end > SURROGATE_LAST) {
			appendRangeSplitAtSurrogateBoundary(sb, start, SURROGATE_LAST);
			appendRangeSplitAtSurrogateBoundary(sb, SURROGATE_LAST + 1, end);
			return;
		}
		appendEscapedCodePoint(sb, start);
		if (end != start) {
			sb.append('-');
			appendEscapedCodePoint(sb, end);
		}
	}

	// Emits a single BMP char value (0x0000-0xFFFF) as a \\uHHHH escape -
	// Joni's own OP2_ESC_U_HEX4 syntax (the same flag JS's own \\u escape
	// enables), which always denotes a literal character regardless of its
	// value or surrounding context (inside/outside a character class, or
	// coinciding with a regex metacharacter) - unlike emitting the raw
	// character, which would need re-escaping for context-dependent
	// metacharacters, and unlike Joni's \xHH byte escape, which fails to
	// compile under a 2-bytes-per-unit encoding when it has no adjacent
	// byte to combine with (see the \\x handling above for the concrete
	// case that surfaced this).
	private static void appendUnicodeEscape(StringBuilder result, int charValue) {
		result.append(String.format("\\u%04x", charValue));
	}

	private int translateUnicodeEscape(String source, int i, StringBuilder result, boolean unicodeMode) {
		// i points to 'u' (already consumed backslash)
		if (unicodeMode && i + 1 < source.length() && source.charAt(i + 1) == '{') {
			// \\u{HHHH...} syntax
			int start = i + 2;
			int end = start;
			while (end < source.length() && source.charAt(end) != '}') {
				end++;
			}
			if (end >= source.length() || end == start) {
				throw RuntimeUtil.syntaxError("Invalid unicode escape");
			}
			String hexStr = source.substring(start, end);
			int codePoint;
			try {
				codePoint = Integer.parseInt(hexStr, 16);
			} catch (NumberFormatException e) {
				throw RuntimeUtil.syntaxError("Invalid unicode escape");
			}
			if (codePoint > 0x10FFFF) {
				throw RuntimeUtil.syntaxError("Unicode code point out of range");
			}
			// BMP: emit as a \\uHHHH escape - see appendUnicodeEscape's comment
			// for why (an ASCII-range codepoint especially needs this, to
			// avoid Joni's byte-oriented \\xHH escape's "too short multibyte"
			// failure and to sidestep having to re-escape it if it happens to
			// collide with a regex metacharacter).
			//
			// Supplementary (>0xFFFF): emit as a RAW LITERAL surrogate pair
			// instead - NOT as two separate \\uHHHH escapes. Each \\uHHHH is
			// its own independent 16-bit-VALUE atom to Joni's parser, so two
			// of them back-to-back are two SEPARATE atoms, not one combined
			// supplementary codepoint - fine for a standalone literal (Joni
			// just matches the two atoms in sequence, same net effect), but
			// wrong as a character-class RANGE boundary: Joni resolves a
			// range's endpoint from the single atom immediately touching the
			// "-", so a range spelled as "\\uD83D\\uDE00-\\uD83D\\uDE4F"
			// resolves to a range from 0xDE00 down to 0xD83D - backwards,
			// "empty range in char class" (confirmed via
			// [\\u{1F600}-\\u{1F64F}], a real regression this exact bug
			// caused). A raw literal pair doesn't have this problem: Joni's
			// own encoding-aware mbcToCode() (LenientUTF16BEEncoding) already
			// combines a genuine surrogate PAIR into one supplementary
			// codepoint value when reading pattern bytes, so it's read as
			// ONE atom either as a standalone literal or as a range
			// boundary - and a supplementary codepoint's UTF-16 units are
			// always in 0xD800-0xDFFF, which can never collide with any
			// ASCII regex metacharacter, so there's no re-escaping concern
			// here the way there is for a low/BMP codepoint.
			if (codePoint <= 0xFFFF) {
				appendUnicodeEscape(result, codePoint);
			} else {
				appendEscapedCodePoint(result, codePoint);
			}
			return end; // point to '}'
		} else if (i + 4 < source.length()) {
			// \\uXXXX syntax
			String hex = source.substring(i + 1, i + 5);
			try {
				int val = Integer.parseInt(hex, 16);
				// RegExpUnicodeEscapeSequence :: u LeadSurrogate \\u
				// TrailSurrogate (unicode mode only): two CONSECUTIVE
				// \\uHHHH escapes forming a valid surrogate pair denote ONE
				// combined supplementary character, per spec step 3 (
				// UTF16Decode(lead, trail)) - not two separate BMP
				// characters. Emitted as a raw literal pair (same as a
				// \\u{...} supplementary escape above, and for the identical
				// reason: two independent \\uHHHH atoms don't get
				// automatically recombined by Joni's own parser, so as a
				// character-class member "[\\ud800\\udc00]" would otherwise
				// match a lone \\ud800 OR \\udc00, not the real combined
				// codepoint - confirmed via test262's u-surrogate-pairs.js/
				// u-surrogate-pairs-atom-char-class.js, a real regression
				// this exact gap caused).
				if (unicodeMode && Character.isHighSurrogate((char) val)
						&& i + 10 < source.length() && source.charAt(i + 5) == '\\' && source.charAt(i + 6) == 'u') {
					try {
						int low = Integer.parseInt(source.substring(i + 7, i + 11), 16);
						if (Character.isLowSurrogate((char) low)) {
							appendEscapedCodePoint(result, Character.toCodePoint((char) val, (char) low));
							return i + 10;
						}
					} catch (NumberFormatException ignore) {
						// not a second \\uHHHH escape - fall through to the lone-escape case below
					}
				}
				appendUnicodeEscape(result, val);
				return i + 4;
			} catch (NumberFormatException e) {
				if (unicodeMode) {
					throw RuntimeUtil.syntaxError("Invalid unicode escape");
				}
				// Non-unicode mode: treat as literal 'u'
				result.append('u');
				return i;
			}
		} else {
			if (unicodeMode) {
				throw RuntimeUtil.syntaxError("Invalid unicode escape");
			}
			result.append('u');
			return i;
		}
	}

	// Returns the ignoreCase value that's in effect INSIDE the group opening
	// at `openParen`, given `currentIgnoreCase` (the value in effect just
	// before it) - or null if this isn't a Modifiers group at all (an
	// ordinary "(?:"/"(?="/"(?!"/"(?<=" / "(?<!" / "(?<name>" or plain "("
	// capturing group), in which case the caller should just inherit
	// `currentIgnoreCase` unchanged. Peeks characters starting at "(?" only
	// far enough to tell modifier syntax ("(?[ims]*(-[ims]*)?:") apart from
	// everything else - doesn't consume/return an index, since the modifier
	// letters and ":" all pass through the main translation loop completely
	// unchanged regardless (only Joni's OWN literal-matching semantics for
	// "i" need this syntax verbatim; \\w/\\W/\\b/\\B are the only things
	// that need to know the resulting VALUE, via the ignoreCaseStack this
	// feeds).
	private static Boolean parseModifierGroupIgnoreCase(String source, int openParen, boolean currentIgnoreCase) {
		int i = openParen + 1;
		if (i >= source.length() || source.charAt(i) != '?') return null;
		i++;
		int addStart = i;
		while (i < source.length() && "ims".indexOf(source.charAt(i)) >= 0) i++;
		int addEnd = i;
		boolean hasDash = i < source.length() && source.charAt(i) == '-';
		int removeStart = -1, removeEnd = -1;
		if (hasDash) {
			i++;
			removeStart = i;
			while (i < source.length() && "ims".indexOf(source.charAt(i)) >= 0) i++;
			removeEnd = i;
		}
		if (i >= source.length() || source.charAt(i) != ':') return null;
		if (addStart == addEnd && !hasDash) return null; // plain "(?:", not a modifiers group
		boolean ignoreCase = currentIgnoreCase;
		for (int k = addStart; k < addEnd; k++) {
			if (source.charAt(k) == 'i') ignoreCase = true;
		}
		for (int k = removeStart; k < removeEnd; k++) {
			if (source.charAt(k) == 'i') ignoreCase = false;
		}
		return ignoreCase;
	}

	// The Unicode codepoints outside JS's basic word-char set (A-Za-z0-9_)
	// whose simple case folding lands on a basic-Latin letter - the "extra"
	// characters GetWordCharacters(rer)/Canonicalize add to \\w/\\b's word-char
	// set when ignoreCase is active. Derived by scanning every codepoint via
	// Character.toLowerCase/toUpperCase for one landing in a-z/A-Z (a
	// reasonable proxy for Unicode simple case folding here, not sourced
	// from the Unicode Character Database's own CaseFolding.txt directly -
	// confirmed to land on exactly these 4 well-known "gotcha" characters:
	// LATIN CAPITAL LETTER I WITH DOT ABOVE, LATIN SMALL LETTER DOTLESS I,
	// LATIN SMALL LETTER LONG S, and KELVIN SIGN).
	private static final int[] EXTRA_WORD_CHARS = {0x0130, 0x0131, 0x017F, 0x212A};

	// The word-char set (JS spec's GetWordCharacters(rer)) as codepoint
	// ranges, for a given effective ignoreCase - reused by both \\w/\\W
	// (directly) and \\b/\\B (built from two \\w-class lookarounds).
	private static int[] wordCharRanges(boolean ignoreCaseActive) {
		int extra = ignoreCaseActive ? EXTRA_WORD_CHARS.length : 0;
		int[] ranges = new int[8 + extra * 2];
		int i = 0;
		ranges[i++] = '0'; ranges[i++] = '9';
		ranges[i++] = 'A'; ranges[i++] = 'Z';
		ranges[i++] = '_'; ranges[i++] = '_';
		ranges[i++] = 'a'; ranges[i++] = 'z';
		if (ignoreCaseActive) {
			for (int cp : EXTRA_WORD_CHARS) {
				ranges[i++] = cp;
				ranges[i++] = cp;
			}
		}
		return ranges;
	}

	// Replaces Joni's own native \\w/\\W: under a Unicode-aware encoding,
	// Joni's \\w matches by Unicode General Category (effectively "is this
	// a Unicode letter/digit"), which is a much WIDER set than JS's own
	// spec-defined word-char set (confirmed empirically: Joni's \\w matches
	// U+3042 HIRAGANA A and U+00E9 LATIN SMALL LETTER E WITH ACUTE, neither
	// of which JS's \\w should match) - and this holds regardless of
	// ignoreCase, so it's not just the (?i:...)/(?-i:...) scoped-modifiers
	// case that needs this, though that's what surfaced it (test262's
	// regexp-modifiers files specifically probe the well-known
	// canonicalize-extension "gotcha" characters). Built the same way
	// \\P{...} is (see translatePropertyFromDataTable) - complement computed
	// ourselves rather than emitted as a nested [^...] bracket, since Joni's
	// JS_SYNTAX doesn't treat a bracket nested inside an already-open class
	// as a plain union.
	private static String buildWordCharClass(boolean ignoreCaseActive, boolean negated, boolean insideCharClass) {
		int[] ranges = wordCharRanges(ignoreCaseActive);
		if (insideCharClass && negated) {
			ranges = complementRanges(ranges);
			negated = false;
		}
		StringBuilder sb = new StringBuilder();
		sb.append(negated ? "[^" : "[");
		for (int i = 0; i < ranges.length; i += 2) {
			appendRangeSplitAtSurrogateBoundary(sb, ranges[i], ranges[i + 1]);
		}
		sb.append(']');
		if (insideCharClass) {
			return sb.substring(1, sb.length() - 1);
		}
		return sb.toString();
	}

	// Replaces Joni's own native \\b/\\B (which inherits the same
	// too-broad-word-char-set problem \\w/\\W have) with an equivalent pair
	// of same-position lookarounds - a position is a word boundary (\\b)
	// iff EXACTLY ONE of "preceded by a word char" / "followed by a word
	// char" holds, and \\B iff both agree. Each lookaround is fixed-length
	// (exactly one character), so this compiles under Joni's native
	// lookbehind without the variable-length restriction ever coming up.
	private static String buildWordBoundaryAssertion(boolean ignoreCaseActive, boolean negated) {
		String wc = buildWordCharClass(ignoreCaseActive, false, false);
		if (!negated) {
			return "(?:(?<=" + wc + ")(?!" + wc + ")|(?<!" + wc + ")(?=" + wc + "))";
		}
		return "(?:(?<=" + wc + ")(?=" + wc + ")|(?<!" + wc + ")(?!" + wc + "))";
	}

	// Validates a GroupSpecifier's RegExpIdentifierName against JS's real
	// IdentifierStart/IdentifierPart rules (throwing SyntaxError on the
	// first invalid codepoint), without touching `result` - the main
	// translation loop's own per-character handling (including this exact
	// \\u/\\u{...} decoding) resumes right after this validates and returns,
	// so it doesn't duplicate the actual translation. `nameStart` points
	// just past the opening "(?<".
	private void validateGroupName(String source, int nameStart) {
		int i = nameStart;
		boolean first = true;
		while (i < source.length() && source.charAt(i) != '>') {
			int codePoint;
			int next;
			if (source.charAt(i) == '\\' && i + 1 < source.length() && source.charAt(i + 1) == 'u') {
				int j = i + 2;
				if (j < source.length() && source.charAt(j) == '{') {
					int start = j + 1;
					int end = start;
					while (end < source.length() && source.charAt(end) != '}') end++;
					if (end >= source.length() || end == start) {
						throw RuntimeUtil.syntaxError("Invalid unicode escape");
					}
					try {
						codePoint = Integer.parseInt(source.substring(start, end), 16);
					} catch (NumberFormatException e) {
						throw RuntimeUtil.syntaxError("Invalid unicode escape");
					}
					next = end + 1;
				} else if (j + 4 <= source.length()) {
					try {
						codePoint = Integer.parseInt(source.substring(j, j + 4), 16);
					} catch (NumberFormatException e) {
						throw RuntimeUtil.syntaxError("Invalid unicode escape");
					}
					next = j + 4;
					// A \\uHHHH high surrogate immediately followed by another
					// \\uHHHH low surrogate combines into one codepoint - the
					// same rule JS string/identifier escapes use generally.
					if (Character.isHighSurrogate((char) codePoint) && next + 6 <= source.length()
							&& source.charAt(next) == '\\' && source.charAt(next + 1) == 'u') {
						try {
							int low = Integer.parseInt(source.substring(next + 2, next + 6), 16);
							if (Character.isLowSurrogate((char) low)) {
								codePoint = Character.toCodePoint((char) codePoint, (char) low);
								next = next + 6;
							}
						} catch (NumberFormatException ignore) {
							// leave as the lone high surrogate
						}
					}
				} else {
					throw RuntimeUtil.syntaxError("Invalid unicode escape");
				}
			} else {
				codePoint = source.codePointAt(i);
				next = i + Character.charCount(codePoint);
			}
			boolean valid = first
					? (Character.isUnicodeIdentifierStart(codePoint) || codePoint == '$' || codePoint == '_')
					: (Character.isUnicodeIdentifierPart(codePoint) || codePoint == '$' || codePoint == '_'
							|| codePoint == 0x200C || codePoint == 0x200D);
			if (!valid) {
				throw RuntimeUtil.syntaxError("Invalid character in group name");
			}
			first = false;
			i = next;
		}
	}

	private static boolean isJsRegexEscape(char c) {
		switch (c) {
			case 'd': case 'D': case 'w': case 'W': case 's': case 'S':
			case 'b': case 'B':
			case 'n': case 'r': case 't': case 'f': case 'v':
			case 'c': case 'x': case 'k':
			case 'p': case 'P':
				return true;
			default:
				return false;
		}
	}

	private byte[] getBytes(String str) {
		if (str == lastString) return lastBytes;
		lastString = str;
		lastBytes = toUtf16BEBytes(str);
		lastChars = null; // invalidate; populated lazily via getChars() if needed
		return lastBytes;
	}

	// Returns a char[] parallel to lastBytes for the current lastString.
	// Only meaningful when the joni encoding is fixedWidth2 - populated
	// lazily so callers under the u/v encoding (LenientUTF16BEEncoding, which
	// combines surrogate pairs) never pay for it. String.toCharArray() is
	// one allocation + one arraycopy from the String's internal char[]/
	// byte[] backing store, amortized once across every iteration of a
	// global/exec loop against the same String instance via lastString.
	// PRECONDITION: caller must have just called getBytes(str) for the same
	// String identity, so lastString == str. We keep lastBytes/lastChars
	// paired under that single lastString key to avoid a desync where
	// bytes describe one string and chars another.
	private char[] getChars(String str) {
		if (lastChars == null) lastChars = str.toCharArray();
		return lastChars;
	}

	// Lossless String<->byte[] conversion for UTF-16BE, NOT
	// String.getBytes(StandardCharsets.UTF_16BE)/new String(bytes,
	// StandardCharsets.UTF_16BE): both directions of Java's built-in
	// UTF-16BE charset codec turn out to be JUST AS LOSSY for a lone
	// (unpaired) surrogate as UTF-8 was - encoding silently substitutes
	// U+FFFD for a char with no pairing partner, and decoding does the same
	// for a 2-byte unit that looks like an unpaired surrogate (confirmed
	// empirically: new String(new char[]{0xD800}).getBytes(UTF_16BE)
	// produces the bytes for U+FFFD, not 0xD8,0x00; the reverse direction
	// substitutes the same way). This silently broke every match against a
	// subject string containing a lone surrogate (compiles fine, pattern
	// bytes are equally affected, but the SUBJECT is what a real script's
	// string data actually looks like) - JS strings are raw UTF-16 code-unit
	// sequences with no such validation, so every char must round-trip
	// through exactly 2 bytes, verbatim, with zero substitution.
	private static byte[] toUtf16BEBytes(String str) {
		int len = str.length();
		byte[] bytes = new byte[len * 2];
		for (int i = 0; i < len; i++) {
			char c = str.charAt(i);
			bytes[i * 2] = (byte) (c >>> 8);
			bytes[i * 2 + 1] = (byte) c;
		}
		return bytes;
	}

	private static String fromUtf16BEBytes(byte[] bytes, int offset, int length) {
		char[] chars = new char[length / 2];
		for (int i = 0; i < chars.length; i++) {
			int p = offset + i * 2;
			chars[i] = (char) (((bytes[p] & 0xff) << 8) | (bytes[p + 1] & 0xff));
		}
		return new String(chars);
	}

	// A JS string index IS a UTF-16 code-unit index, and toUtf16BEBytes()
	// above is a fixed 2-bytes-per-char mapping, so this is trivial
	// arithmetic rather than a variable-width walk.
	private int charIndexToByteIndex(String str, byte[] bytes, int charIndex) {
		return Math.max(0, charIndex) * 2;
	}

	private int byteIndexToCharIndex(String str, byte[] bytes, int byteIndex) {
		return Math.max(0, byteIndex) / 2;
	}

	private boolean execInternal(String str) {
		boolean global = regExp.isGlobal();
		boolean sticky = regExp.isSticky();
		int strLen = str.length();

		int charIndex = 0;
		if (global || sticky) {
			charIndex = regExp.getLastIndex();
			if (charIndex < 0 || charIndex > strLen) {
				regExp.setLastIndex(0);
				lastRegion = null;
				return false;
			}
		}

		if (customLookbehind != null) {
			return execInternalCustomLookbehind(str, charIndex, global, sticky);
		}

		byte[] bytes = getBytes(str);
		int byteStart = charIndexToByteIndex(str, bytes, charIndex);

		char[] chars = joniEncoding.isFixedWidth2() ? getChars(str) : null;
		Matcher matcher = joniEncoding.isFixedWidth2()
				? regex.matcher(bytes, chars, str, 0, bytes.length)
				: regex.matcher(bytes, chars, 0, bytes.length);
		int result;
		if (sticky) {
			result = matcher.match(byteStart, bytes.length, Option.NONE);
		} else {
			result = matcher.search(byteStart, bytes.length, Option.NONE);
		}

		if (result >= 0) {
			Region region = matcher.getEagerRegion();
			lastRegion = region;
			lastMatchStart = byteIndexToCharIndex(str, bytes, region.getBeg(0));
			lastMatchEnd = byteIndexToCharIndex(str, bytes, region.getEnd(0));

			if (global || sticky) {
				// Per RegExpBuiltinExec, lastIndex is set to the match's end
				// index unconditionally - even for a zero-width match. The
				// "advance past a zero-width match by one position" step
				// belongs exclusively to the CALLER (match/matchAll/replace/
				// split's own AdvanceStringIndex), not to exec() itself - see
				// the identical fix/rationale in RegExpEngineJdk.
				regExp.setLastIndex(lastMatchEnd);
			}
			return true;
		}

		if (global || sticky) {
			regExp.setLastIndex(0);
		}
		lastRegion = null;
		return false;
	}

	// A candidate match's captures, indexed the same way as an ordinary
	// Joni Region: [char-index beg, char-index end] per group number
	// (0 = overall match), or nulls for a non-participating group.
	private static final class CustomMatchResult {
		int overallStart;
		int overallEnd;
		Map<Integer, int[]> groupSpans = new java.util.HashMap<>(); // original group number -> [charBeg, charEnd)
	}

	private boolean execInternalCustomLookbehind(String str, int charIndex, boolean global, boolean sticky) {
		int strLen = str.length();
		CustomMatchResult found = null;
		if (sticky) {
			found = tryCustomMatchAt(str, charIndex);
		} else {
			for (int s = charIndex; s <= strLen; s++) {
				found = tryCustomMatchAt(str, s);
				if (found != null) break;
			}
		}

		if (found != null) {
			Region region = Region.newRegion(customLookbehind.totalGroupCount + 1);
			region.setBeg(0, found.overallStart * 2);
			region.setEnd(0, found.overallEnd * 2);
			for (Map.Entry<Integer, int[]> e : found.groupSpans.entrySet()) {
				int[] span = e.getValue();
				region.setBeg(e.getKey(), span[0] * 2);
				region.setEnd(e.getKey(), span[1] * 2);
			}
			lastRegion = region;
			lastMatchStart = found.overallStart;
			lastMatchEnd = found.overallEnd;
			if (global || sticky) {
				regExp.setLastIndex(lastMatchEnd);
			}
			return true;
		}

		if (global || sticky) {
			regExp.setLastIndex(0);
		}
		lastRegion = null;
		return false;
	}

	// Tries to match the WHOLE custom-lookbehind pattern (optional ".*"
	// prefix + lookbehind + suffix) with its overall match starting exactly
	// at char position s.
	private CustomMatchResult tryCustomMatchAt(String str, int s) {
		int strLen = str.length();
		if (customLookbehind.genericPrefixRegex != null) {
			return tryGenericPrefixMatchAt(str, s);
		}
		if (!customLookbehind.prefixDotStar) {
			return tryLookbehindAndSuffixAt(str, s, s);
		}
		// Greedy ".*": try the WIDEST possible prefix span first (largest p),
		// backtracking to smaller spans only if that fails - same preference
		// order a real greedy ".*" would produce. "." excludes line
		// terminators unless dotAll, so p can't extend past the first one.
		int maxP = strLen;
		if (!regExp.isDotAll()) {
			for (int k = s; k < strLen; k++) {
				char ch = str.charAt(k);
				if (ch == '\n' || ch == '\r' || ch == LS.charAt(0) || ch == PS.charAt(0)) {
					maxP = k;
					break;
				}
			}
		}
		for (int p = maxP; p >= s; p--) {
			CustomMatchResult r = tryLookbehindAndSuffixAt(str, s, p);
			if (r != null) return r;
		}
		return null;
	}

	// Emulates backtracking across an arbitrary prefix's OWN internal
	// choices (quantifiers, alternation - e.g. "^faaao?") without a real
	// backtracking engine: try candidate end positions widest-first,
	// restricting genericPrefixRegex's match to THAT range each time (see
	// this class's own diagnostic confirming Joni's range parameter forces
	// the search to the longest match fitting within it, backing off its
	// own greedy choices as needed) and requiring the match to use the
	// FULL range - a shorter natural match at that candidateEnd means the
	// prefix can't actually reach that boundary. Whichever candidateEnd
	// resolves the lookbehind + suffix chain first wins, same greedy
	// preference order a real backtracking engine would produce.
	private CustomMatchResult tryGenericPrefixMatchAt(String str, int s) {
		int strLen = str.length();
		byte[] bytes = getBytes(str);
		int startByte = charIndexToByteIndex(str, bytes, s);
		for (int candidateEnd = strLen; candidateEnd >= s; candidateEnd--) {
			int candidateEndByte = charIndexToByteIndex(str, bytes, candidateEnd);
			Matcher m = customLookbehind.genericPrefixRegex.matcher(bytes, 0, bytes.length);
			int r = m.match(startByte, candidateEndByte, Option.NONE);
			if (r < 0) continue;
			Region region = m.getEagerRegion();
			if (byteIndexToCharIndex(str, bytes, region.getEnd(0)) != candidateEnd) continue;
			CustomMatchResult res = tryLookbehindAndSuffixAt(str, s, candidateEnd);
			if (res != null) return res;
		}
		return null;
	}

	// Checks the lookbehind assertion holds at char position p (the
	// position right after any ".*" prefix), then - only if it does - that
	// SUFFIX matches starting exactly at p. On success, returns a result
	// spanning [overallStart, suffix's match end) with body's and suffix's
	// captures both present, correctly numbered to the ORIGINAL pattern.
	private CustomMatchResult tryLookbehindAndSuffixAt(String str, int overallStart, int p) {
		LookbehindCheckResult lb = checkLookbehind(str, p);
		if (lb == null) return null;

		byte[] bytes = getBytes(str);
		int byteP = charIndexToByteIndex(str, bytes, p);
		Regex suffixRegex = customLookbehind.suffixRegex;
		Region suffixRegion;
		if (suffixRegex == null) {
			// Cross-boundary backreference bridging: SUFFIX's own source
			// references a group defined inside BODY, which Joni can't
			// resolve across two separately-compiled Regex objects - so
			// substitute BODY's just-computed captured text as a literal
			// (escaped) run and compile+match that per attempt instead.
			String bridged = bridgeBackreferences(customLookbehind.suffixSourceForBridging, lb, str);
			byte[] suffixBytes = toUtf16BEBytes(bridged);
			try {
				suffixRegex = new Regex(suffixBytes, 0, suffixBytes.length, customLookbehind.compileOptions,
						joniEncoding, JS_SYNTAX);
			} catch (RuntimeException e) {
				return null;
			}
		}
		Matcher m = suffixRegex.matcher(bytes, 0, bytes.length);
		int r = m.match(byteP, bytes.length, Option.NONE);
		if (r < 0) return null;
		suffixRegion = m.getEagerRegion();

		CustomMatchResult result = new CustomMatchResult();
		result.overallStart = overallStart;
		result.overallEnd = byteIndexToCharIndex(str, bytes, suffixRegion.getEnd(0));
		if (!customLookbehind.negative) {
			result.groupSpans.putAll(lb.groupSpans);
		}
		for (int localGroup = 1; localGroup < suffixRegion.getNumRegs(); localGroup++) {
			int beg = suffixRegion.getBeg(localGroup);
			int end = suffixRegion.getEnd(localGroup);
			if (beg < 0) continue;
			int origGroup = localGroup + customLookbehind.suffixGroupOffset;
			result.groupSpans.put(origGroup, new int[]{
					byteIndexToCharIndex(str, bytes, beg), byteIndexToCharIndex(str, bytes, end)});
		}
		return result;
	}

	// The lookbehind's own captures (original group number -> [charBeg,
	// charEnd) in `str`), plus whether the assertion held at all.
	private static final class LookbehindCheckResult {
		Map<Integer, int[]> groupSpans = new java.util.HashMap<>();
	}

	private LookbehindCheckResult checkLookbehind(String str, int p) {
		LBBuffers bufs = new LBBuffers(str);
		LBTransform t = new LBTransform(-1, str.length());
		int anchor = t.anchorFor(p);
		Map<String, Integer> syntheticToOriginal = new java.util.HashMap<>();
		for (Map.Entry<Integer, String> e : customLookbehind.bodyGroupSyntheticNames.entrySet()) {
			syntheticToOriginal.put(e.getValue(), e.getKey());
		}
		LBMatchResult m = matchLBNode(customLookbehind.bodyNode, bufs, anchor, t, syntheticToOriginal);
		boolean matched = m != null;

		if (customLookbehind.negative) {
			return matched ? null : new LookbehindCheckResult();
		}
		if (!matched) return null;

		LookbehindCheckResult lb = new LookbehindCheckResult();
		lb.groupSpans.putAll(m.groupSpans);
		return lb;
	}

	// Rewrites SUFFIX's source text for one specific lookbehind match
	// attempt: a backreference crossing into BODY (\N, N <= body's group
	// count) is replaced by BODY's actual captured text for group N,
	// escaped as literal \\uHHHH runs (matches nothing - i.e. the empty
	// string - if that group didn't participate, per spec); anything else
	// is copied through unchanged (renumberSuffixBackreferences() already
	// handled non-crossing backreferences once, in the constructor, before
	// this per-attempt bridging was known to be needed for THIS suffix -
	// see suffixReferencesGroupsUpTo()'s all-or-nothing check).
	private String bridgeBackreferences(String suffixText, LookbehindCheckResult lb, String str) {
		StringBuilder out = new StringBuilder(suffixText.length());
		for (int i = 0; i < suffixText.length(); i++) {
			char c = suffixText.charAt(i);
			if (c == '\\' && i + 1 < suffixText.length() && Character.isDigit(suffixText.charAt(i + 1))
					&& suffixText.charAt(i + 1) != '0') {
				int j = i + 1;
				while (j < suffixText.length() && Character.isDigit(suffixText.charAt(j))) j++;
				int n = Integer.parseInt(suffixText.substring(i + 1, j));
				if (n <= customLookbehind.suffixGroupOffset) {
					int[] span = lb.groupSpans.get(n);
					if (span != null) {
						for (int k = span[0]; k < span[1]; k++) {
							appendUnicodeEscape(out, str.charAt(k));
						}
					}
				} else {
					out.append('\\').append(n - customLookbehind.suffixGroupOffset);
				}
				i = j - 1;
			} else {
				out.append(c);
			}
		}
		return out.toString();
	}

	private String getGroupValue(String str, byte[] bytes, Region region, int group) {
		if (group >= region.getNumRegs()) return null;
		int beg = region.getBeg(group);
		int end = region.getEnd(group);
		if (beg < 0) return null;
		// Slice the original String directly rather than decoding bytes we
		// only encoded from that same String moments ago - byte offsets are
		// always even (2 bytes per JS char unit), so >> 1 recovers the char
		// index losslessly.
		return str.substring(beg >>> 1, end >>> 1);
	}

	@Override
	public JSArray exec(JSRuntimeContext context, String str) {
		if (execInternal(str)) {
			byte[] bytes = getBytes(str);
			Region region = lastRegion;

			JSArray result = JSArray.create(env);
			result.getMembers(true).setOwnProperty("index", lastMatchStart);
			result.getMembers(true).setOwnProperty("input", str);

			// Named groups
			Object groupsObj = extractMatchNamedGroups(str, bytes, region);
			result.getMembers(true).setOwnProperty("groups", groupsObj);

			// Indices
			JSArray indices = null;
			if (regExp.isHasIndices()) {
				indices = JSArray.create(env);
				indices.getMembers(true).setOwnProperty("groups",
						groupsObj != RuntimeUtil.UNDEFINED ? extractMatchNamedGroupIndices(str, bytes, region) : RuntimeUtil.UNDEFINED);
				result.getMembers(true).setOwnProperty("indices", indices);
			}

			int gc = region.getNumRegs();
			for (int j = 0; j < gc; j++) {
				String g = getGroupValue(str, bytes, region, j);
				result.arrayAdd(j, g != null ? g : RuntimeUtil.UNDEFINED);
				if (indices != null) {
					if (g != null) {
						JSArray a = JSArray.create(env);
						a.arrayAdd(byteIndexToCharIndex(str, bytes, region.getBeg(j)));
						a.arrayAdd(byteIndexToCharIndex(str, bytes, region.getEnd(j)));
						indices.arrayAdd(a);
					} else {
						indices.arrayAdd(RuntimeUtil.UNDEFINED);
					}
				}
			}
			return result;
		}
		regExp.setLastIndex(0);
		return null;
	}

	// A duplicate name (mutually exclusive alternatives, e.g. "(?<x>a)|(?<x>y)")
	// has several candidate group numbers, of which at most one actually
	// participated in any given match - pick that one, falling back to the
	// map's single remembered index (still correct for the common, non-
	// duplicate case) when nothing captured, so a value-less lookup still
	// reports "not captured" rather than a wrong index.
	private int resolveNamedGroupIndex(String name, int fallbackIdx, Region region) {
		int[] refs = multiplexNamedGroups.get(name);
		if (refs == null) return fallbackIdx;
		for (int idx : refs) {
			if (idx < region.getNumRegs() && region.getBeg(idx) >= 0) {
				return idx;
			}
		}
		return fallbackIdx;
	}

	private Object extractMatchNamedGroups(String str, byte[] bytes, Region region) {
		if (namedGroups.isEmpty()) return RuntimeUtil.UNDEFINED;
		JSObject groupsObj = JSObject.create(env);
		for (Map.Entry<String, Integer> entry : namedGroups.entrySet()) {
			String name = entry.getKey();
			int idx = resolveNamedGroupIndex(name, entry.getValue(), region);
			String value = getGroupValue(str, bytes, region, idx);
			groupsObj.setOwnProperty(name, value != null ? value : RuntimeUtil.UNDEFINED);
		}
		return groupsObj;
	}

	private Object extractMatchNamedGroupIndices(String str, byte[] bytes, Region region) {
		if (namedGroups.isEmpty()) return RuntimeUtil.UNDEFINED;
		JSObject groupsObj = JSObject.create(env);
		for (Map.Entry<String, Integer> entry : namedGroups.entrySet()) {
			String name = entry.getKey();
			int idx = resolveNamedGroupIndex(name, entry.getValue(), region);
			String value = getGroupValue(str, bytes, region, idx);
			if (value != null) {
				JSArray a = JSArray.create(env);
				a.arrayAdd(byteIndexToCharIndex(str, bytes, region.getBeg(idx)));
				a.arrayAdd(byteIndexToCharIndex(str, bytes, region.getEnd(idx)));
				groupsObj.setOwnProperty(name, a);
			} else {
				groupsObj.setOwnProperty(name, RuntimeUtil.UNDEFINED);
			}
		}
		return groupsObj;
	}

	@Override
	public boolean test(JSRuntimeContext context, String str) {
		// test() ignores the Region entirely - only cares whether a match was
		// found. The generic execInternal path would call getEagerRegion() on
		// success (which allocates a SingleRegion when the pattern has no
		// capture groups, since msaRegion is null) and would also update
		// lastRegion/lastMatchStart/lastMatchEnd fields that nothing here
		// consumes. Short-circuit both when we can.
		if (customLookbehind != null) {
			// Custom lookbehind produces the Region itself as part of its
			// work - no shortcut available.
			return execInternal(str);
		}

		boolean global = regExp.isGlobal();
		boolean sticky = regExp.isSticky();
		int strLen = str.length();

		int charIndex = 0;
		if (global || sticky) {
			charIndex = regExp.getLastIndex();
			if (charIndex < 0 || charIndex > strLen) {
				regExp.setLastIndex(0);
				lastRegion = null;
				return false;
			}
		}

		byte[] bytes = getBytes(str);
		int byteStart = charIndexToByteIndex(str, bytes, charIndex);

		char[] chars = joniEncoding.isFixedWidth2() ? getChars(str) : null;
		Matcher matcher = joniEncoding.isFixedWidth2()
				? regex.matcher(bytes, chars, str, 0, bytes.length)
				: regex.matcher(bytes, chars, 0, bytes.length);
		int result;
		if (sticky) {
			result = matcher.match(byteStart, bytes.length, Option.NONE);
		} else {
			result = matcher.search(byteStart, bytes.length, Option.NONE);
		}

		if (result >= 0) {
			if (global || sticky) {
				// lastIndex still needs to move to the match's end - read
				// msaBegin/msaEnd directly (getEnd() returns msaEnd) without
				// allocating a Region.
				int matchEndChar = byteIndexToCharIndex(str, bytes, matcher.getEnd());
				lastMatchStart = byteIndexToCharIndex(str, bytes, matcher.getBegin());
				lastMatchEnd = matchEndChar;
				regExp.setLastIndex(matchEndChar);
			}
			// lastRegion is left untouched - callers of test() never read it,
			// and exec()/replace()/etc. all overwrite it themselves before use.
			return true;
		}

		if (global || sticky) {
			regExp.setLastIndex(0);
		}
		lastRegion = null;
		return false;
	}

	@Override
	public JSArray split(JSRuntimeContext context, String str, int limit) {
		JSArray result = JSArray.create(env);

		if (limit == 0) return result;

		// Empty pattern - split into individual characters (or code points)
		if (StringUtil.isEmpty(regExp.getSource())) {
			if (regExp.isUnicode() || regExp.isUnicodeSets()) {
				int offset = 0;
				int count = 0;
				while (offset < str.length() && (limit <= 0 || count < limit)) {
					int codePoint = str.codePointAt(offset);
					result.arrayAdd(new String(Character.toChars(codePoint)));
					offset += Character.charCount(codePoint);
					count++;
				}
			} else {
				int count = limit > 0 ? Math.min(limit, str.length()) : str.length();
				for (int i = 0; i < count; i++) {
					result.arrayAdd(String.valueOf(str.charAt(i)));
				}
			}
			return result;
		}

		byte[] bytes = getBytes(str);
		// Route through the fw2 String/char sidecar path (as execInternal does) so
		// Search can lean on String.indexOf's HotSpot SIMD intrinsic and hot
		// opcodes read chars[s>>1] instead of reconstructing the code unit from
		// two byte reads on every advance.
		Matcher matcher = joniEncoding.isFixedWidth2()
				? regex.matcher(bytes, getChars(str), str, 0, bytes.length)
				: regex.matcher(bytes, 0, bytes.length);

		// When the pattern has no capture groups, the group-append loop below
		// is a no-op and the Region held on the Matcher (msaRegion) is null -
		// so getEagerRegion() would allocate a fresh SingleRegion every match.
		// Read msaBegin/msaEnd directly through getBegin()/getEnd() instead;
		// for a benchmark like split(/,/) that gets called thousands of times
		// per subject, this eliminates one SingleRegion allocation per match.
		final boolean hasGroups = regex.numberOfCaptures() > 0;

		int pos = 0; // byte position
		int resultCount = 0;
		boolean brokeEarly = false;

		while (limit <= 0 || resultCount < limit) {
			int searchResult = matcher.search(pos, bytes.length, Option.NONE);
			if (searchResult < 0) break;

			int matchStart;
			int matchEnd;
			if (hasGroups) {
				Region region = matcher.getEagerRegion();
				matchStart = region.getBeg(0);
				matchEnd = region.getEnd(0);

				// Add substring before match
				String before = str.substring(pos >>> 1, matchStart >>> 1);
				result.arrayAdd(before);
				resultCount++;

				// Add capturing groups
				if (limit <= 0 || resultCount < limit) {
					int gc = region.getNumRegs();
					for (int i = 1; i < gc && (limit <= 0 || resultCount < limit); i++) {
						String g = getGroupValue(str, bytes, region, i);
						result.arrayAdd(g != null ? g : RuntimeUtil.UNDEFINED);
						resultCount++;
					}
				}
			} else {
				matchStart = matcher.getBegin();
				matchEnd = matcher.getEnd();
				result.arrayAdd(str.substring(pos >>> 1, matchStart >>> 1));
				resultCount++;
			}

			pos = matchEnd;

			// Handle empty matches
			if (matchStart == matchEnd) {
				if (pos >= bytes.length) {
					brokeEarly = true;
					break;
				}
				// Advance by one JS char unit (always exactly 2 bytes in UTF-16BE -
				// unlike UTF-8, no need to ask the encoding for the width, and no
				// risk of it disagreeing with what a JS string index means).
				pos += 2;
			}
		}

		if (!brokeEarly && (limit <= 0 || resultCount < limit) && pos <= bytes.length) {
			result.arrayAdd(str.substring(pos >>> 1));
		}

		return result;
	}

	@Override
	public JSArray match(JSRuntimeContext context, String str) {
		if (!regExp.isGlobal()) {
			return exec(context, str);
		}

		byte[] bytes = getBytes(str);
		Matcher matcher = joniEncoding.isFixedWidth2()
				? regex.matcher(bytes, getChars(str), str, 0, bytes.length)
				: regex.matcher(bytes, 0, bytes.length);
		JSArray a = JSArray.create(env);

		int pos = 0;
		while (true) {
			int searchResult = matcher.search(pos, bytes.length, Option.NONE);
			if (searchResult < 0) break;

			// String#match(/g/) returns only the overall matches - group 0
			// only. Read msaBegin/msaEnd directly to avoid the SingleRegion
			// allocation that getEagerRegion() would produce for a group-less
			// pattern on every iteration.
			int matchStart = matcher.getBegin();
			int matchEnd = matcher.getEnd();

			String matched = str.substring(matchStart >>> 1, matchEnd >>> 1);
			a.arrayAdd(matched);

			if (matchStart == matchEnd) {
				if (pos >= bytes.length) break;
				pos = matchEnd + 2;
			} else {
				pos = matchEnd;
			}
		}

		return a.arrayLength() > 0 ? a : null;
	}

	@Override
	public Iterator<JSArray> matchAll(JSRuntimeContext context, String str) {
		final boolean isGlobal = regExp.isGlobal();
		return new Iterator<JSArray>() {
			JSArray res = exec(context, str);

			@Override
			public boolean hasNext() {
				return res != null;
			}

			@Override
			public JSArray next() {
				if (res != null) {
					JSArray ret = res;
					if (!isGlobal) {
						res = null;
					} else {
						res = exec(context, str);
					}
					return ret;
				}
				throw new NoSuchElementException();
			}
		};
	}

	@Override
	public int search(JSRuntimeContext context, String str) {
		byte[] bytes = getBytes(str);
		Matcher matcher = joniEncoding.isFixedWidth2()
				? regex.matcher(bytes, getChars(str), str, 0, bytes.length)
				: regex.matcher(bytes, 0, bytes.length);
		int searchResult = matcher.search(0, bytes.length, Option.NONE);
		if (searchResult >= 0) {
			// String#search only reports the beginning of the primary match -
			// no groups needed. Skip getEagerRegion() (would allocate a
			// SingleRegion for a group-less pattern) and read msaBegin directly.
			return byteIndexToCharIndex(str, bytes, matcher.getBegin());
		}
		return -1;
	}

	@Override
	public String replace(JSRuntimeContext context, String str, Object replace) {
		lastRegion = null;
		if (regExp.isGlobal()) {
			regExp.setLastIndex(0);
		}

		Callable cb = null;
		String newSubStr = "";
		boolean substrGroups = false;
		if (replace instanceof Callable cb0) {
			cb = cb0;
		} else {
			newSubStr = RuntimeUtil.toString(env, replace);
			substrGroups = newSubStr.indexOf('$') >= 0;
		}

		StringBuilder result = new StringBuilder();
		int start = 0; // byte position

		while (true) {
			boolean ok = execInternal(str);
			if (ok) {
				byte[] bytes = getBytes(str);
				Region region = lastRegion;

				int matchStartByte = charIndexToByteIndex(str, bytes, lastMatchStart);
				int matchEndByte = charIndexToByteIndex(str, bytes, lastMatchEnd);

				// Append text before match
				result.append(str, start >>> 1, matchStartByte >>> 1);
				start = matchEndByte;

				// execInternal() no longer auto-advances lastIndex past a
				// zero-width match (that's the caller's job per spec) - this
				// loop is such a caller, so it must do that advance itself
				// to avoid re-matching the same empty string forever.
				if (lastMatchStart == lastMatchEnd) {
					int nextIndex = lastMatchEnd;
					if (nextIndex < str.length()) {
						if (regExp.isUnicode() || regExp.isUnicodeSets()) {
							int codePoint = str.codePointAt(nextIndex);
							nextIndex += Character.charCount(codePoint);
						} else {
							nextIndex++;
						}
					} else {
						nextIndex++;
					}
					regExp.setLastIndex(nextIndex);
				}

				if (cb != null) {
					int groupCount = region.getNumRegs() - 1;
					Object[] args = new Object[groupCount + 3];
					int idx = 0;
					args[idx++] = getGroupValue(str, bytes, region, 0);
					for (int i = 1; i <= groupCount; i++) {
						String gi = getGroupValue(str, bytes, region, i);
						args[idx++] = gi != null ? gi : RuntimeUtil.UNDEFINED;
					}
					args[idx++] = lastMatchStart;
					args[idx++] = str;
					Object res = cb.call(null, args);
					result.append(RuntimeUtil.toString(env, res));
				} else if (!substrGroups) {
					result.append(newSubStr);
				} else {
					appendSubstitution(result, str, bytes, region, newSubStr);
				}
			}

			if (!ok || !regExp.isGlobal()) break;
		}

		// Append remaining
		result.append(str, start >>> 1, str.length());

		return result.toString();
	}

	private void appendSubstitution(StringBuilder result, String str, byte[] bytes, Region region, String newSubStr) {
		int subLength = newSubStr.length();
		for (int i = 0; i < subLength; ) {
			char c = newSubStr.charAt(i++);
			if (c != '$' || i == subLength) {
				result.append(c);
				continue;
			}

			char after$ = newSubStr.charAt(i++);
			switch (after$) {
				case '$':
					result.append('$');
					break;
				case '&':
					String match = getGroupValue(str, bytes, region, 0);
					if (match != null) result.append(match);
					break;
				case '`':
					// Text before match
					int matchStartByte = region.getBeg(0);
					result.append(str, 0, matchStartByte >>> 1);
					break;
				case '\'':
					// Text after match
					int matchEndByte = region.getEnd(0);
					result.append(str, matchEndByte >>> 1, str.length());
					break;
				case '<':
					// Named group reference $<name>
					int closeAngle = newSubStr.indexOf('>', i);
					if (closeAngle >= 0) {
						String name = newSubStr.substring(i, closeAngle);
						Integer groupIdx = namedGroups.get(name);
						if (groupIdx != null) {
							int idx = resolveNamedGroupIndex(name, groupIdx, region);
							String gv = getGroupValue(str, bytes, region, idx);
							if (gv != null) result.append(gv);
						}
						i = closeAngle + 1;
					} else {
						result.append('$').append(after$);
					}
					break;
				case '1': case '2': case '3': case '4': case '5':
				case '6': case '7': case '8': case '9':
					int n = after$ - '0';
					int groupCount = region.getNumRegs() - 1;
					if (i < subLength) {
						char next2 = newSubStr.charAt(i);
						if (next2 >= '0' && next2 <= '9') {
							int nn = n * 10 + next2 - '0';
							if (nn > 0 && nn <= groupCount) {
								n = nn;
								i++;
							}
						}
					}
					if (n > 0 && n <= groupCount) {
						String group = getGroupValue(str, bytes, region, n);
						if (group != null) result.append(group);
						break;
					}
					// fall through
				default:
					result.append('$').append(after$);
			}
		}
	}
}
