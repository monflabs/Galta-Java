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
package org.monflabs.galtajs.rt.builtins.standard.regexp.joni;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.BiFunction;

import org.monflabs.galtajs.external.org_joni.Matcher;
import org.monflabs.galtajs.external.org_joni.NameEntry;
import org.monflabs.galtajs.external.org_joni.Option;
import org.monflabs.galtajs.external.org_joni.Regex;
import org.monflabs.galtajs.external.org_joni.Region;
import org.monflabs.galtajs.external.org_joni.Syntax;
import org.monflabs.galtajs.external.org_joni.constants.MetaChar;
import org.monflabs.galtajs.external.org_joni.constants.SyntaxProperties;
import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.standard.regexp.RegExp;
import org.monflabs.galtajs.rt.builtins.standard.regexp.RegExpEngine;
import org.monflabs.galtajs.rt.builtins.standard.regexp.jdk.UnicodePropertyData;
import org.monflabs.galtajs.rt.builtins.standard.regexp.jdk.UnicodeStringPropertyData;
import org.monflabs.util.StringUtil;

/**
 * RegExp engine using Joni (Oniguruma for Java) with ECMAScript syntax.
 * Joni natively supports ECMAScript regex semantics including proper capture
 * group clearing in repetitions, which java.util.regex cannot handle.
 */
public class RegExpEngineJoni implements RegExpEngine {

	private static final String LS = " ";
	private static final String PS = " ";
	private static final String DOT_REPLACEMENT = "[^\\n\\r" + LS + PS + "]";
	// dotAll-active '.': matches literally any character (including line
	// terminators) - spelled out as an explicit class rather than passed
	// through as a raw '.', since Joni's own native dot is governed by a
	// single PATTERN-WIDE compile-time option that can't reflect a LOCAL
	// (?s:...) override (see the '.' case in translatePattern()).
	private static final String DOT_ANY = "[\\s\\S]";
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
			(env, regexp) -> new RegExpEngineJoni(env, regexp);
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
	// namedGroups/multiplexNamedGroups: not `final` with an inline initializer
	// (as they were before compiled-pattern caching below) because a cache
	// HIT assigns the SAME Map instance a previous compile already built,
	// rather than this instance's own fresh one - see the Compiled cache
	// and its own doc comment just below. Safe to share: confirmed (by
	// reading every call site) that both maps, like `regex`/
	// `customLookbehind`/`topLevelAlternatives`, are populated ONLY during
	// construction and never mutated again afterward.
	private Map<String, Integer> namedGroups;
	// Only populated for a name shared by more than one GroupSpecifier (duplicate
	// named capturing groups in mutually exclusive alternatives) - see extractNamedGroups().
	private Map<String, int[]> multiplexNamedGroups;

	// Cached byte representation of last matched string
	private byte[] lastBytes;
	private String lastString;
	private char[] lastChars;
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
		// Non-null only when SUFFIX itself has ANOTHER top-level lookbehind
		// Joni cannot compile natively (e.g. "\w+(?<=(?=\r\n|[\n\r...]|\z))" -
		// the multiline "$" translation's own lookahead, nested inside the
		// user's explicit trailing "(?<=$)") - recognized by recursively
		// re-running trySetUpCustomLookbehind() on the suffix text itself
		// (same "(prefix)(lookbehind)(suffix)" shape, one level down).
		// suffixRegex stays null in this case - see tryLookbehindAndSuffixAt().
		// This nested structure's OWN group numbers are relative to ITSELF
		// (as if it were its own top-level pattern) - suffixGroupOffset
		// above is applied on top when reading its results, exactly like
		// the plain-suffixRegex case.
		CustomLookbehind suffixCustomLookbehind;
		int totalGroupCount; // whole ORIGINAL pattern's capturing-group count (body's + suffix's)
		int compileOptions;
		// Non-null only when BODY itself contains a bare "\N" backreference
		// to a group defined in the PREFIX, BEFORE the lookbehind (e.g.
		// "/^(f)oo(?<=^\1o+)$/" - \1 here is the "(f)" group). bodyNode is
		// compiled ONCE, before any match attempt, so it can't natively
		// resolve a reference to a group whose captured text is only known
		// once the PREFIX has actually matched - this is the opposite
		// direction of bridgeBackreferences()'s existing suffix-references-
		// body handling. When set, checkLookbehind() rebuilds body (and
		// re-derives bodyNode) fresh per match attempt, substituting each
		// referenced group's actual captured text (from the prefix match
		// that already happened by the time checkLookbehind runs) as a
		// literal - see bridgeExternalBodyBackreferences(). Only reachable
		// via genericPrefixRegex (only a real, compiled prefix can have its
		// own capturing groups at all).
		String bodySourceForBridging;
		java.util.Set<Integer> bodyExternalGroupRefs;
		// How many capturing groups appear in PREFIX, before this
		// lookbehind - body's own groups (bodyGroupSyntheticNames' keys)
		// are numbered starting right after these. Needed again whenever
		// bodySourceForBridging is recompiled fresh per match attempt (see
		// checkLookbehind()) so body's own groups keep the SAME true
		// overall numbering as the one-time compile above used.
		int prefixGroupOffset;
	}

	// Non-null only for a pattern with a TOP-LEVEL "|" (outside every
	// lookbehind's own body/suffix) where Joni's native compile of the
	// WHOLE pattern failed but EVERY branch, considered independently, is
	// handleable (either natively Joni-compilable on its own, or fits the
	// single "(prefix)(lookbehind)(suffix)" CustomLookbehind shape) - e.g.
	// "(?<!(?<a>\D){3})f|f" (test262 named-groups/lookbehind.js), where the
	// lookbehind only constrains the FIRST alternative, not the whole
	// pattern. The existing single-shape trySetUpCustomLookbehind()
	// treats a top-level "|" after its recognized lookbehind as ordinary
	// SUFFIX content, which is wrong: the second alternative has no
	// lookbehind constraint at all. Each branch's own group numbers are
	// LOCAL (as if it were its own standalone pattern, exactly like
	// trySetUpCustomLookbehind's existing prefix/body/suffix numbering) -
	// AltBranch.groupOffset (the running total of EARLIER branches' own
	// group counts) is added on top when merging a branch's match result,
	// by tryTopLevelAlternativesAt() below.
	private java.util.List<AltBranch> topLevelAlternatives;

	private static final class AltBranch {
		Regex plainRegex;    // non-null if this branch alone compiles natively via Joni
		CustomLookbehind cl; // non-null if this branch alone needs the custom-lookbehind machinery
		int groupOffset;
	}

	// The 5 fields this constructor spends most of its own body computing
	// (validation, pattern translation, and the actual native Joni compile)
	// depend on nothing but `regExp`'s source+flags - confirmed by reading
	// every write site in this class: `regex`/`namedGroups`/
	// `multiplexNamedGroups`/`customLookbehind`/`topLevelAlternatives` are
	// all set ONLY here in the constructor (directly or via
	// extractNamedGroups()/trySetUpCustomLookbehind(), both called only
	// from here) and never mutated again afterward. `RegExp.toString()`
	// (source + ALL flags, sorted) is already trusted as a complete cache
	// key by RegExpEngineJdk's own analogous env.getRegExp() cache. A
	// regex literal re-evaluated in a loop, or a `new RegExp(sameSource)`
	// call repeated with the same flags, previously re-ran the full
	// validation + pattern-translation + native-compile pipeline (including,
	// on the custom-lookbehind path, its own nested Regex compiles) every
	// single time.
	private static final class Compiled {
		final Regex regex;
		final Map<String, Integer> namedGroups;
		final Map<String, int[]> multiplexNamedGroups;
		final CustomLookbehind customLookbehind;
		final java.util.List<AltBranch> topLevelAlternatives;
		Compiled(Regex regex, Map<String, Integer> namedGroups, Map<String, int[]> multiplexNamedGroups,
				CustomLookbehind customLookbehind, java.util.List<AltBranch> topLevelAlternatives) {
			this.regex = regex;
			this.namedGroups = namedGroups;
			this.multiplexNamedGroups = multiplexNamedGroups;
			this.customLookbehind = customLookbehind;
			this.topLevelAlternatives = topLevelAlternatives;
		}
	}

	// Process-wide rather than per-JSEnvironment: compiled state above is a
	// pure function of (source,flags), with no reference to anything
	// environment- or realm-specific, so sharing it across JSEnvironment
	// instances in the same JVM is safe - matching how RegExpEngineJdk's
	// own env.getRegExp() cache is ALSO never actually enabled anywhere in
	// this codebase today (regexpCacheSize defaults to 0 - confirmed no
	// caller sets it), so routing through that dormant, opt-in mechanism
	// would not have fixed the reported problem for default configurations.
	// A concurrent map, not a synchronized LRU: lookups never contend. The
	// bound is enforced by evicting an arbitrary entry when it is full, which
	// only matters for programs that compile more than 512 distinct patterns.
	private static final int COMPILED_CACHE_SIZE = 512;
	private static final java.util.concurrent.ConcurrentHashMap<String, Compiled> COMPILED_CACHE =
			new java.util.concurrent.ConcurrentHashMap<>();

	private static void cacheCompiled(String key, Compiled compiled) {
		if (COMPILED_CACHE.size() >= COMPILED_CACHE_SIZE) {
			java.util.Iterator<String> it = COMPILED_CACHE.keySet().iterator();
			if (it.hasNext()) {
				it.next();
				it.remove();
			}
		}
		COMPILED_CACHE.put(key, compiled);
	}

	public RegExpEngineJoni(JSEnvironment env, RegExp regExp) {
		this.env = env;
		this.regExp = regExp;
		this.joniEncoding = (regExp.isUnicode() || regExp.isUnicodeSets())
				? LenientUTF16BEEncoding.INSTANCE
				: LenientUTF16BECodeUnitEncoding.INSTANCE;

		String cacheKey = regExp.toString();
		Compiled cached = COMPILED_CACHE.get(cacheKey);
		if (cached != null) {
			this.regex = cached.regex;
			this.namedGroups = cached.namedGroups;
			this.multiplexNamedGroups = cached.multiplexNamedGroups;
			this.customLookbehind = cached.customLookbehind;
			this.topLevelAlternatives = cached.topLevelAlternatives;
			return;
		}
		this.namedGroups = new LinkedHashMap<>();
		this.multiplexNamedGroups = new LinkedHashMap<>();

		String source = regExp.getSource();
		validateDuplicateGroupNames(source);
		validateNoQuantifiableAssertions(source, regExp.isUnicode() || regExp.isUnicodeSets());
		if (regExp.isUnicode()) {
			// "v" mode is excluded: its character classes go through
			// VClassParser's own separate grammar entirely (see the "["
			// case in translatePattern below), which has its own static
			// semantics for what's allowed either side of a class range.
			validateNoClassEscapeInRange(source);
		}
		String translated = translatePattern(source);
		translated = neutralizeUnsetForwardBackreferences(translated);
		try {
			translated = neutralizeAlwaysEmptyOptionalLookaround(translated);
		} catch (RuntimeException e) {
			// Defensive fallback: this narrow rewrite pass runs
			// UNCONDITIONALLY on every pattern (not gated behind a
			// native-compile-failure catch block like the rest of this
			// class's own pattern-shape-specific machinery), and outside
			// the constructor's own try/catch that turns a native-compile
			// failure into a proper SyntaxError - so any bug in it (2
			// already found and fixed via full-suite sweeps: mishandling
			// the regexp-modifiers proposal's "(?i:...)" groups, and an
			// unclosed "(" producing a bogus groupEnd) would otherwise
			// surface as a confusing raw, unwrapped exception instead of
			// either a correct match OR a correct SyntaxError. On ANY
			// failure here, fall back to the UNTRANSFORMED text - worst
			// case this one narrow RepeatMatcher special case (see the
			// method's own comment) doesn't apply for this one pattern,
			// and everything else proceeds exactly as it did before this
			// pass existed.
		}
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

		// Joni's own NATIVE lookbehind opcode - used below whenever a
		// lookbehind body is fixed-length enough for Joni to compile
		// directly, without needing this class's own CustomLookbehind
		// reversal machinery at all - reads a repeated CAPTURING group
		// inside that body in FORWARD text order (effectively re-matching
		// the body forward starting at position-length), not the spec's
		// true right-to-left order (each iteration attempted starting from
		// the anchor and moving backward, so the LAST iteration - the one
		// whose capture wins - is the LEFTMOST, not the rightmost, match).
		// Confirmed empirically against plain Joni: `(?<=(\w){3})def` on
		// "abcdef" natively gives group1="c" (forward-order last
		// iteration), where spec/V8 want "a" (backward-order last
		// iteration) - see lookBehind/captures.js #4. GaltaJS's own
		// CustomLookbehind path (LookbehindReversal + compileLBFragment)
		// gets this right, since it's built specifically to simulate
		// right-to-left matching - so for this narrow, detectable shape,
		// skip Joni's native lookbehind entirely and force that path,
		// rather than trying to fix Joni's own opcode.
		boolean forceCustomForRepeatedCapture = lookbehindHasRepeatedCapture(translated);

		try {
			if (forceCustomForRepeatedCapture) {
				throw new RuntimeException("invalid pattern in look-behind");
			}
			this.regex = new Regex(patternBytes, 0, patternBytes.length, options,
					joniEncoding, JS_SYNTAX);
			extractNamedGroups(regex, 0, namedGroups);
		} catch (RuntimeException ex) {
			// A top-level "|" (TopLevelAlternatives, above) is tried BEFORE
			// the single-shape path - if translated has no top-level "|" at
			// all, findTopLevelAlternationBranches() returns null and this
			// is a no-op, falling straight through to the existing logic
			// unchanged.
			java.util.List<String> altBranches = findTopLevelAlternationBranches(translated);
			if (altBranches != null) {
				this.topLevelAlternatives = trySetUpTopLevelAlternatives(altBranches, options, source);
			}
			if (this.topLevelAlternatives != null) {
				CustomLookbehind sentinel = new CustomLookbehind();
				sentinel.totalGroupCount = countCapturingGroups(translated);
				this.customLookbehind = sentinel;
				cacheCompiled(cacheKey, new Compiled(regex, namedGroups, multiplexNamedGroups, customLookbehind, topLevelAlternatives));
				return;
			}

			// Joni's own compiled lookbehind opcode unconditionally rejects
			// true variable-length content, regardless of syntax flags (see
			// CustomLookbehind's own class comment) - for the narrow pattern
			// shape setUpCustomLookbehind() recognizes, implement it
			// ourselves instead of surfacing Joni's rejection as a
			// SyntaxError. Anything outside that recognized shape falls
			// through to the original error, unchanged from before.
			this.customLookbehind = trySetUpCustomLookbehind(translated, options, source, ex, 0);
			if (this.customLookbehind == null) {
				if (forceCustomForRepeatedCapture) {
					// Our own narrow repeated-capture detector fired, but
					// trySetUpCustomLookbehind() couldn't set up its
					// (separately narrow) reversal path for this
					// particular pattern SHAPE either - fall back to
					// Joni's native compile (which we know succeeds, since
					// we only threw a SYNTHETIC exception above) rather
					// than spuriously rejecting a pattern Joni itself is
					// happy to compile. This keeps the pattern WORKING,
					// just with its pre-existing narrower capture-order
					// bug, instead of a regression to SyntaxError.
					this.regex = new Regex(patternBytes, 0, patternBytes.length, options,
							joniEncoding, JS_SYNTAX);
					extractNamedGroups(regex, 0, namedGroups);
				} else {
					throw RuntimeUtil.syntaxError("Invalid RegExp '/{0}/{1}': {2}",
							source, regExp.getFlags(), ex.getMessage());
				}
			}
		}
		cacheCompiled(cacheKey, new Compiled(regex, namedGroups, multiplexNamedGroups, customLookbehind, topLevelAlternatives));
	}

	// True if `translated` contains a lookbehind ("(?<=...)"/"(?<!...)",
	// at any nesting depth) whose body has a capturing group sitting
	// inside a quantifier permitting more than one repetition - either the
	// group's OWN trailing quantifier ("(\w){3}") or an enclosing one
	// ("(?:(\w)){3}") - the shape that triggers Joni's native-lookbehind
	// forward-iteration-order bug described where this is called.
	private static boolean lookbehindHasRepeatedCapture(String translated) {
		for (int[] bodyRange : computeLookbehindBodyRanges(translated)) {
			String bodyText = translated.substring(bodyRange[0], bodyRange[1]);
			Map<Integer, int[]> groups = computeGroupSpans(bodyText);
			if (groups.isEmpty()) continue;
			java.util.List<int[]> repeatable = computeRepeatableRanges(bodyText);
			for (int[] span : groups.values()) {
				if (isInAnyRange(repeatable, span[0])) return true;
			}
		}
		return false;
	}

	// Recognizes the narrow pattern shape this custom-lookbehind path
	// supports: an optional PREFIX (empty, a bare ".*", or - via
	// tryGenericPrefixMatchAt's bounded-range search - arbitrary
	// Joni-native-compilable content), then EXACTLY one top-level
	// "(?<=BODY)"/"(?<!BODY)", then an arbitrary SUFFIX running to the end
	// of the pattern.
	// outerGroupOffset: how many capturing groups exist BEFORE `translated`
	// in the overall pattern - 0 for the normal top-level call and for the
	// nested-suffix recursive self-call (both number their own result
	// LOCALLY, with any further external shift applied by the CALLER - see
	// cl.suffixGroupOffset's own external use). Only trySetUpTopLevelAlternatives()
	// passes a nonzero value, since named-group population below is the
	// ONE place this method's own LOCAL numbering leaks out permanently
	// (into the shared, instance-level `namedGroups` map) rather than
	// being shifted later like every other group-number use in the
	// returned CustomLookbehind.
	private CustomLookbehind trySetUpCustomLookbehind(String translated, int options, String source, RuntimeException originalException, int outerGroupOffset) {
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

		// How many capturing groups appear in PREFIX, textually before this
		// lookbehind - body's OWN groups are numbered right after these in
		// the ORIGINAL pattern's left-to-right numbering, so this is the
		// offset LookbehindReversal must number body's groups from (NOT
		// always 0 - a bare literal "\N" backreference inside body is only
		// unambiguous, e.g. distinguishable from body's OWN first group,
		// once body's groups are numbered starting AFTER prefix's own).
		int prefixGroupOffset = genericPrefixText != null ? countCapturingGroups(genericPrefixText) : 0;

		LookbehindReversal rev;
		LBNode bodyNode;
		try {
			rev = LookbehindReversal.reverse(body, prefixGroupOffset);
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

		if (!rev.externalGroupRefs.isEmpty() && genericPrefixRegex == null) {
			// A bare "\N" backreference in body reaches OUTSIDE body - only
			// resolvable if it's actually one of the PREFIX's own capturing
			// groups (only genericPrefixRegex - a real, separately-compiled
			// prefix - can have those; ".*"/empty prefixes never do).
			// Otherwise this is some other unsupported shape - bail exactly
			// as if bodyNode itself had failed to compile.
			return null;
		}

		CustomLookbehind cl = new CustomLookbehind();
		cl.negative = negative;
		cl.prefixDotStar = prefixDotStar;
		cl.genericPrefixRegex = genericPrefixRegex;
		cl.bodyNode = bodyNode;
		cl.bodyGroupSyntheticNames = rev.groupNumberToSyntheticName;
		cl.prefixGroupOffset = prefixGroupOffset;
		// Where suffix's own LOCAL group numbers (as SUFFIX's own
		// separately-compiled Regex sees them, always starting at 1) begin
		// in the ORIGINAL pattern's overall numbering: after BOTH prefix's
		// groups and body's own groups.
		cl.suffixGroupOffset = prefixGroupOffset + rev.groupCount;
		cl.compileOptions = options;
		cl.totalGroupCount = countCapturingGroups(translated);
		if (!rev.externalGroupRefs.isEmpty()) {
			cl.bodySourceForBridging = body;
			cl.bodyExternalGroupRefs = rev.externalGroupRefs;
		}

		for (Map.Entry<Integer, String> e : rev.groupNumberToOriginalName.entrySet()) {
			namedGroups.put(e.getValue(), e.getKey() + outerGroupOffset);
		}

		// cl.suffixGroupOffset (not the old bare rev.groupCount) so a
		// SUFFIX backreference crossing into PREFIX (not just body) is
		// also detected as needing bridging - see tryLookbehindAndSuffixAt's
		// own merge of prefixRegion into the bridging source map.
		boolean suffixCrossesIntoBody = suffixReferencesGroupsUpTo(suffix, cl.suffixGroupOffset, rev.groupNumberToOriginalName.values());
		if (suffixCrossesIntoBody) {
			cl.suffixSourceForBridging = suffix;
		} else {
			String renumberedSuffix = renumberSuffixBackreferences(suffix, cl.suffixGroupOffset);
			try {
				byte[] suffixBytes = toUtf16BEBytes(renumberedSuffix);
				cl.suffixRegex = new Regex(suffixBytes, 0, suffixBytes.length, options, joniEncoding, JS_SYNTAX);
			} catch (RuntimeException e) {
				// Plain Joni compile failed - SUFFIX may itself contain
				// another top-level lookbehind Joni can't compile natively
				// (e.g. the multiline "$" translation's own lookahead,
				// nested inside the user's own trailing "(?<=$)" - test262
				// lookBehind/start-of-line.js: "/(?<=^)\w+(?<=$)/gm").
				// Recurse: treat SUFFIX as its own self-contained
				// "(prefix)(lookbehind)(suffix)" pattern - the RAW suffix
				// text (fresh group numbering relative to itself), NOT the
				// outer-offset-renumbered one, since suffixGroupOffset
				// below is applied when READING its results instead,
				// exactly like the plain-suffixRegex case. Known narrow
				// limitation: a NAMED group inside this nested suffix
				// registers into `namedGroups` without this offset applied
				// - not exercised by the one test262 file this recursion
				// targets (no named groups anywhere in its suffix).
				CustomLookbehind nested = trySetUpCustomLookbehind(suffix, options, source, e, 0);
				if (nested == null) {
					return null;
				}
				cl.suffixCustomLookbehind = nested;
			}
			if (cl.suffixRegex != null) {
				extractNamedGroups(cl.suffixRegex, cl.suffixGroupOffset, namedGroups);
			}
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

	// Splits `s` on every DEPTH-0 "|" (outside any group/class), returning
	// the branch texts - or null if there's no depth-0 "|" at all (the
	// common case, meaning no top-level alternation exists and the
	// existing single-shape trySetUpCustomLookbehind() path applies
	// unchanged).
	private static java.util.List<String> findTopLevelAlternationBranches(String s) {
		java.util.List<String> branches = new java.util.ArrayList<>();
		int depth = 0;
		boolean inCC = false;
		int start = 0;
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
				depth++;
			} else if (c == ')') {
				depth--;
			} else if (c == '|' && depth == 0) {
				branches.add(s.substring(start, i));
				start = i + 1;
			}
		}
		branches.add(s.substring(start));
		return branches.size() >= 2 ? branches : null;
	}

	// Tries to handle a top-level-alternation pattern (see
	// topLevelAlternatives' own field comment) by setting up each branch
	// independently - either as a plain natively-Joni-compilable Regex, or
	// (mirroring the constructor's own forceCustomForRepeatedCapture
	// handling, per branch) via trySetUpCustomLookbehind(). Returns null -
	// meaning "give up, fall back to the existing single-shape path and
	// its ordinary error behavior" - if ANY branch fits NEITHER: this is
	// an all-or-nothing attempt, not a partial one, so a pattern this
	// can't fully handle degrades to today's pre-existing behavior rather
	// than a confusing partial success.
	private java.util.List<AltBranch> trySetUpTopLevelAlternatives(java.util.List<String> branches, int options, String source) {
		java.util.List<AltBranch> result = new java.util.ArrayList<>();
		int cumulativeGroups = 0;
		for (String branchText : branches) {
			if (branchText.isEmpty()) return null;
			AltBranch ab = new AltBranch();
			ab.groupOffset = cumulativeGroups;
			boolean forceCustom = lookbehindHasRepeatedCapture(branchText);
			RuntimeException nativeEx = null;
			if (!forceCustom) {
				try {
					byte[] bb = toUtf16BEBytes(branchText);
					ab.plainRegex = new Regex(bb, 0, bb.length, options, joniEncoding, JS_SYNTAX);
				} catch (RuntimeException e) {
					nativeEx = e;
				}
			}
			if (ab.plainRegex == null) {
				if (nativeEx == null) {
					// forceCustom fired without ever attempting native
					// compile - synthesize the same trigger message
					// trySetUpCustomLookbehind() itself requires, mirroring
					// the constructor's own forceCustomForRepeatedCapture
					// handling.
					nativeEx = new RuntimeException("invalid pattern in look-behind");
				}
				CustomLookbehind branchCl = trySetUpCustomLookbehind(branchText, options, source, nativeEx, cumulativeGroups);
				if (branchCl != null) {
					ab.cl = branchCl;
				} else if (forceCustom) {
					// Same graceful fallback as the constructor's own
					// forceCustomForRepeatedCapture path: the repeated-
					// capture detector fired but this branch doesn't fit
					// the narrow custom-lookbehind shape either - fall back
					// to native compile for THIS branch (known to succeed,
					// since forceCustom only SYNTHESIZED the failure)
					// rather than abandoning the whole alternation attempt.
					try {
						byte[] bb = toUtf16BEBytes(branchText);
						ab.plainRegex = new Regex(bb, 0, bb.length, options, joniEncoding, JS_SYNTAX);
					} catch (RuntimeException e2) {
						return null;
					}
				} else {
					return null;
				}
			}
			if (ab.plainRegex != null) {
				extractNamedGroups(ab.plainRegex, cumulativeGroups, namedGroups);
			}
			result.add(ab);
			cumulativeGroups += countCapturingGroups(branchText);
		}
		return result;
	}

	// Tries each top-level alternative branch, in order, at THIS ONE start
	// position s - mirroring plain regex alternation's own left-to-right,
	// same-position semantics (branch order is never reversed by
	// direction, per spec). The outer position-scanning loop
	// (execInternalCustomLookbehind) advances s only when EVERY branch
	// fails here. Each branch's own group numbers are LOCAL (as if it were
	// a standalone pattern) - shifted by its AltBranch.groupOffset here,
	// the one place that offset is ever applied.
	private CustomMatchResult tryTopLevelAlternativesAt(String str, int s) {
		byte[] bytes = getBytes(str);
		int byteS = charIndexToByteIndex(str, bytes, s);
		for (AltBranch ab : topLevelAlternatives) {
			if (ab.plainRegex != null) {
				Matcher m = ab.plainRegex.matcher(bytes, 0, bytes.length);
				int r = m.match(byteS, bytes.length, Option.NONE);
				if (r < 0) continue;
				Region region = m.getEagerRegion();
				CustomMatchResult result = new CustomMatchResult();
				result.overallStart = s;
				result.overallEnd = byteIndexToCharIndex(str, bytes, region.getEnd(0));
				for (int g = 1; g < region.getNumRegs(); g++) {
					int beg = region.getBeg(g);
					int end = region.getEnd(g);
					if (beg < 0) continue;
					result.groupSpans.put(g + ab.groupOffset, new int[]{
							byteIndexToCharIndex(str, bytes, beg), byteIndexToCharIndex(str, bytes, end)});
				}
				return result;
			} else {
				CustomMatchResult r = tryCustomMatchAt(ab.cl, str, s);
				if (r == null) continue;
				CustomMatchResult result = new CustomMatchResult();
				result.overallStart = r.overallStart;
				result.overallEnd = r.overallEnd;
				for (Map.Entry<Integer, int[]> e : r.groupSpans.entrySet()) {
					result.groupSpans.put(e.getKey() + ab.groupOffset, e.getValue());
				}
				return result;
			}
		}
		return null;
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
		// See neutralizeUnsetForwardBackreferences()'s own comment - term
		// reversal can turn an ordinary backward reference in the original
		// source into one that textually precedes its own target here,
		// which Joni's native (Perl-style) semantics would then fail to
		// match instead of matching empty as spec requires.
		fragmentText = neutralizeUnsetForwardBackreferences(fragmentText);
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

	// A numbered backreference "\N" is, per spec (22.2.2.9 Backreference),
	// ALWAYS successful (matching an empty string) when group N's capture
	// hasn't been SET yet at the point the backreference is evaluated -
	// unlike Joni/Oniguruma's own Perl-style semantics, where an unset
	// backreference FAILS to match instead (confirmed empirically against
	// plain Joni: /\1(A)/.exec("AA") returns no match at all, where
	// spec/V8 want \1 to match empty so (A) can then match "A"). This
	// matters not just for a literal source-level forward reference like
	// "\1(A)" but also for a SELF-reference like "(abc\1)" (group 1 hasn't
	// captured a value until its OWN closing paren is reached) and for
	// cases LookbehindReversal's term-reordering CREATES: reversing
	// "(\w+)\1" (an ordinary, always-valid backward reference in the
	// original pattern) swaps term order to "\1(?<g1>\w+)" in the compiled
	// flat text, making the backreference textually precede its own
	// target there even though it didn't in the original source.
	//
	// Finds every "\N" in `text` that's PROVABLY unset - N's own capturing
	// group (numbered the same way JONI itself numbers groups in THIS
	// text - left-to-right positional order - not any original,
	// pre-reversal numbering) hasn't CLOSED yet by this position, in EVERY
	// possible execution of `text` - and replaces it with an empty
	// non-capturing group "(?:)" (the same zero-width, always-succeeds
	// effect spec requires), leaving every other backreference (a
	// genuinely backward one, safely resolved by Joni's own native
	// backreference support) untouched.
	//
	// "Provably" requires the backreference's OWN position to sit outside
	// any quantifier permitting more than one repetition: within such a
	// loop, a LATER iteration can legitimately see an EARLIER iteration's
	// already-set capture, even for a backreference that textually
	// precedes its target's definition (see computeRepeatableRanges()).
	// Alternation backtracking does NOT have this hazard - per spec, each
	// Alternative starts fresh from the Disjunction's own entry state, so
	// a failed sibling branch's captures never leak forward (22.2.2.3) -
	// so only quantifiers need this guard.
	//
	// Also skips any backreference that falls INSIDE a "(?<=...)"/"(?<!...)"
	// lookbehind body: per spec, that content is evaluated with direction
	// -1 (right-to-left), so "textually precedes" no longer implies "not
	// yet evaluated" the way it does for ordinary direction/+1 content -
	// e.g. in "(?<=\1(\w+))c", \1 textually precedes "(\w+)" but, read
	// right-to-left, the GROUP is what direction -1 actually visits first.
	// That content gets its own, direction-aware handling elsewhere: either
	// this same method re-applied to the reversed body text
	// (compileLBFragment() calls it again on ALREADY-reversed, so already
	// direction-normalized, text), or - for a body Joni compiles natively -
	// left as-is (a separate, not-yet-addressed gap; see
	// docs/GaltaJS/KnownGaps.md).
	private static String neutralizeUnsetForwardBackreferences(String text) {
		Map<Integer, int[]> groupSpans = computeGroupSpans(text);
		Map<String, int[]> namedGroupSpans = computeNamedGroupSpans(text);
		if (groupSpans.isEmpty() && namedGroupSpans.isEmpty()) return text;
		java.util.List<int[]> repeatable = computeRepeatableRanges(text);
		java.util.List<int[]> lookbehindBodies = computeLookbehindBodyRanges(text);

		StringBuilder out = new StringBuilder(text.length());
		int i = 0;
		int n = text.length();
		boolean inCC = false;
		while (i < n) {
			char c = text.charAt(i);
			if (c == '\\' && i + 1 < n) {
				char next = text.charAt(i + 1);
				if (!inCC && next >= '1' && next <= '9') {
					int j = i + 1;
					while (j < n && Character.isDigit(text.charAt(j))) j++;
					int num = Integer.parseInt(text.substring(i + 1, j));
					int[] span = groupSpans.get(num);
					if (span != null && i < span[1] && !isInAnyRange(repeatable, i)
							&& !isInAnyRange(lookbehindBodies, i)) {
						out.append("(?:)");
						i = j;
						continue;
					}
				}
				// \k<name> - same "provably unset" forward-reference check as
				// bare \N above, just keyed by name instead of position. Also
				// sidesteps Joni's own native compile-time rejection of a
				// \k<name> that textually precedes EVERY "(?<name>" in the
				// pattern ("undefined name <name> reference") - per spec a
				// named backreference is valid as long as some group with
				// that name exists ANYWHERE in the pattern, forward or not.
				if (!inCC && next == 'k' && i + 2 < n && text.charAt(i + 2) == '<') {
					int nameEnd = text.indexOf('>', i + 3);
					if (nameEnd > 0) {
						String name = text.substring(i + 3, nameEnd);
						int[] span = namedGroupSpans.get(name);
						if (span != null && i < span[1] && !isInAnyRange(repeatable, i)
								&& !isInAnyRange(lookbehindBodies, i)) {
							out.append("(?:)");
							i = nameEnd + 1;
							continue;
						}
					}
				}
				out.append(c).append(next);
				i += 2;
				continue;
			}
			if (c == '[') inCC = true;
			else if (c == ']') inCC = false;
			out.append(c);
			i++;
		}
		return out.toString();
	}

	// Name -> [openPos, closePos) of the FIRST "(?<name>" group with that
	// name (a forward \k<name> reference textually preceding even the
	// earliest definition is unset regardless of which duplicate-name
	// alternative eventually matches - see neutralizeUnsetForwardBackreferences()).
	private static Map<String, int[]> computeNamedGroupSpans(String text) {
		Map<String, int[]> spans = new java.util.HashMap<>();
		int i = 0;
		int n = text.length();
		java.util.ArrayDeque<int[]> stack = new java.util.ArrayDeque<>(); // [openPos] or NON_CAPTURING_MARKER
		java.util.ArrayDeque<String> nameStack = new java.util.ArrayDeque<>();
		boolean inCC = false;
		while (i < n) {
			char c = text.charAt(i);
			if (c == '\\') { i += 2; continue; }
			if (inCC) {
				if (c == ']') inCC = false;
				i++;
				continue;
			}
			if (c == '[') { inCC = true; i++; continue; }
			if (c == '(') {
				// "" (never a valid group name) stands in for "unnamed" -
				// ArrayDeque (unlike a JDK List-backed Stack) rejects null
				// elements outright.
				String name = "";
				boolean capturing;
				if (i + 1 < n && text.charAt(i + 1) == '?') {
					char k = i + 2 < n ? text.charAt(i + 2) : '\0';
					capturing = (k == '<' && i + 3 < n && text.charAt(i + 3) != '=' && text.charAt(i + 3) != '!');
					if (capturing) {
						int nameEnd = text.indexOf('>', i + 3);
						if (nameEnd > 0) name = text.substring(i + 3, nameEnd);
					}
				} else {
					capturing = true;
				}
				if (capturing) {
					stack.push(new int[]{i});
					nameStack.push(name);
				} else {
					stack.push(NON_CAPTURING_MARKER);
					nameStack.push("");
				}
				i++;
				continue;
			}
			if (c == ')') {
				int[] top = stack.isEmpty() ? null : stack.pop();
				String name = nameStack.isEmpty() ? "" : nameStack.pop();
				if (top != null && top != NON_CAPTURING_MARKER && !name.isEmpty()) {
					spans.putIfAbsent(name, new int[]{top[0], i + 1});
				}
				i++;
				continue;
			}
			i++;
		}
		return spans;
	}

	// Half-open [start,end) ranges of `text` spanning the BODY (content
	// strictly between the "(?<=" / "(?<!" and its matching ")") of every
	// lookbehind found, at any nesting depth - see
	// neutralizeUnsetForwardBackreferences()'s comment on why that method
	// must not apply its forward-reference reasoning inside one. A NESTED
	// lookbehind's own body is automatically covered by its ENCLOSING
	// lookbehind's excluded range, so this doesn't need to recurse.
	private static java.util.List<int[]> computeLookbehindBodyRanges(String text) {
		java.util.List<int[]> ranges = new java.util.ArrayList<>();
		int i = 0;
		int n = text.length();
		boolean inCC = false;
		while (i < n) {
			char c = text.charAt(i);
			if (c == '\\') { i += 2; continue; }
			if (inCC) {
				if (c == ']') inCC = false;
				i++;
				continue;
			}
			if (c == '[') { inCC = true; i++; continue; }
			if (c == '(' && i + 3 < n && text.charAt(i + 1) == '?' && text.charAt(i + 2) == '<'
					&& (text.charAt(i + 3) == '=' || text.charAt(i + 3) == '!')) {
				int close;
				try {
					close = LookbehindReversal.scanGroup(text, i);
				} catch (RuntimeException e) {
					break;
				}
				ranges.add(new int[]{i + 4, close - 1});
				i = close;
				continue;
			}
			i++;
		}
		return ranges;
	}

	private static boolean isInAnyRange(java.util.List<int[]> ranges, int pos) {
		for (int[] r : ranges) {
			if (pos >= r[0] && pos < r[1]) return true;
		}
		return false;
	}

	// 1-based POSITIONAL group number (Joni's own numbering: left-to-right
	// order of capturing "(" / "(?<name>" opens, ignoring "(?:"/"(?="/
	// "(?!"/"(?<="/"(?<!") -> [openPos, closePos) - the SAME numbering
	// Joni itself uses to resolve a bare "\N", which is why this (not any
	// ORIGINAL, pre-reversal numbering) is what
	// neutralizeUnsetForwardBackreferences() needs to match against.
	private static final int[] NON_CAPTURING_MARKER = new int[0];

	private static Map<Integer, int[]> computeGroupSpans(String text) {
		Map<Integer, int[]> spans = new java.util.HashMap<>();
		int i = 0;
		int n = text.length();
		int num = 0;
		// [num, openPos]; NON_CAPTURING_MARKER placeholder for a non-capturing
		// group, just to keep push/pop balanced with '(' / ')' - ArrayDeque
		// doesn't permit null elements.
		java.util.ArrayDeque<int[]> stack = new java.util.ArrayDeque<>();
		boolean inCC = false;
		while (i < n) {
			char c = text.charAt(i);
			if (c == '\\') { i += 2; continue; }
			if (inCC) {
				if (c == ']') inCC = false;
				i++;
				continue;
			}
			if (c == '[') { inCC = true; i++; continue; }
			if (c == '(') {
				boolean capturing;
				if (i + 1 < n && text.charAt(i + 1) == '?') {
					char k = i + 2 < n ? text.charAt(i + 2) : '\0';
					capturing = (k == '<' && i + 3 < n && text.charAt(i + 3) != '=' && text.charAt(i + 3) != '!');
				} else {
					capturing = true;
				}
				if (capturing) {
					num++;
					stack.push(new int[]{num, i});
				} else {
					stack.push(NON_CAPTURING_MARKER);
				}
				i++;
				continue;
			}
			if (c == ')') {
				int[] top = stack.isEmpty() ? null : stack.pop();
				if (top != null && top != NON_CAPTURING_MARKER) {
					spans.put(top[0], new int[]{top[1], i + 1});
				}
				i++;
				continue;
			}
			i++;
		}
		return spans;
	}

	// Half-open [start,end) ranges of `text` that lie within a group
	// subject to a quantifier permitting MORE than one repetition (*, +,
	// {n,} for any n, or {n,m} with m>1) - see
	// neutralizeUnsetForwardBackreferences() for why only the
	// backreference's OWN position (checked against these ranges) matters,
	// not its target group's.
	private static java.util.List<int[]> computeRepeatableRanges(String text) {
		java.util.List<int[]> ranges = new java.util.ArrayList<>();
		int i = 0;
		int n = text.length();
		while (i < n) {
			char c = text.charAt(i);
			if (c == '\\') { i += 2; continue; }
			if (c == '[') {
				int j = i + 1;
				if (j < n && text.charAt(j) == '^') j++;
				if (j < n && text.charAt(j) == ']') j++;
				while (j < n && text.charAt(j) != ']') {
					if (text.charAt(j) == '\\') j++;
					j++;
				}
				i = j + 1;
				continue;
			}
			if (c == '(') {
				int close;
				try {
					close = LookbehindReversal.scanGroup(text, i);
				} catch (RuntimeException e) {
					break; // malformed - bail out conservatively (no ranges found)
				}
				if (isRepeatingQuantifierAt(text, close) > close) {
					ranges.add(new int[]{i, close});
				}
				i++; // keep scanning INSIDE too, to find nested repeatable groups
				continue;
			}
			i++;
		}
		return ranges;
	}

	// Index just past a quantifier at `i` that permits MORE than one
	// repetition (*, +, {n,}, {n,m} with m>1 or unbounded) - or `i`
	// unchanged if there's no quantifier there, or one bounded to at most
	// 1 repetition (?, {0,1}, {1}).
	private static int isRepeatingQuantifierAt(String s, int i) {
		if (i >= s.length()) return i;
		char c = s.charAt(i);
		if (c == '*' || c == '+') {
			int j = i + 1;
			if (j < s.length() && s.charAt(j) == '?') j++;
			return j;
		}
		if (c == '{') {
			int close = s.indexOf('}', i);
			if (close < 0) return i;
			String inside = s.substring(i + 1, close);
			if (!inside.matches("\\d+(,\\d*)?")) return i;
			int max;
			int comma = inside.indexOf(',');
			if (comma < 0) {
				max = Integer.parseInt(inside);
			} else {
				String maxPart = inside.substring(comma + 1);
				max = maxPart.isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(maxPart);
			}
			if (max <= 1) return i;
			int j = close + 1;
			if (j < s.length() && s.charAt(j) == '?') j++;
			return j;
		}
		return i;
	}

	// B.1.4's "QuantifiableAssertion Quantifier" (a lookahead "(?=...)"/
	// "(?!...)" directly followed by a quantifier, e.g. "(?=.)*") is a
	// sloppy-mode-only Annex B extension - under the "u"/"v" flag it must be
	// an early SyntaxError instead (confirmed via
	// unicode_restricted_quantifiable_assertion.js). A lookbehind
	// ("(?<=...)"/"(?<!...)") is never quantifiable, in EITHER mode - that's
	// the base Term grammar, not part of the Annex B extension - so it is
	// checked regardless of unicodeMode (e.g. "/.(?<=.)?/" is a SyntaxError).
	// Joni itself accepts both syntaxes unconditionally (it's how the
	// sloppy-mode lookahead case keeps working, e.g. lookahead-quantifier-
	// match-groups.js), so this is purely an extra source-level rejection,
	// not a translation change.
	private void validateNoQuantifiableAssertions(String source, boolean unicodeMode) {
		int len = source.length();
		boolean inClass = false;
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
			} else if (c == '(' && i + 2 < len && source.charAt(i + 1) == '?'
					&& ((unicodeMode && (source.charAt(i + 2) == '=' || source.charAt(i + 2) == '!'))
						|| (i + 3 < len && source.charAt(i + 2) == '<'
							&& (source.charAt(i + 3) == '=' || source.charAt(i + 3) == '!')))) {
				int close;
				try {
					close = LookbehindReversal.scanGroup(source, i);
				} catch (RuntimeException e) {
					return; // malformed - let Joni's own compile surface the real error
				}
				if (hasQuantifierAt(source, close)) {
					throw RuntimeUtil.syntaxError("Assertion is not quantifiable");
				}
			}
		}
	}

	// True iff SOME Quantifier (any bound, including a trailing lazy "?")
	// starts at `i` - unlike isRepeatingQuantifierAt() above, this also
	// matches single-repetition forms ("?", "{1}", "{0,1}"): B.1.4's ban on
	// quantifying an assertion applies to every Quantifier, not just ones
	// permitting more than one repetition.
	private static boolean hasQuantifierAt(String s, int i) {
		if (i >= s.length()) return false;
		char c = s.charAt(i);
		if (c == '*' || c == '+' || c == '?') return true;
		if (c == '{') {
			int close = s.indexOf('}', i);
			if (close < 0) return false;
			String inside = s.substring(i + 1, close);
			return inside.matches("\\d+(,\\d*)?");
		}
		return false;
	}

	// Narrow fix for ECMA-262's RepeatMatcher step 2.b (a min=0-quantified
	// iteration that matched ZERO-LENGTH has its captures discarded, and
	// the iteration is treated as never having happened) - Joni's own
	// native RepeatMatcher opcode doesn't implement this JS-specific rule
	// at all; general support would need a change to Joni's own bytecode-
	// level repeat-matching loop (out of scope for a targeted patch - see
	// KnownGaps.md). This handles the one NARROW, STATICALLY-DETECTABLE
	// case actually exercised by test262 (lookahead-quantifier-match-
	// groups.js): a group whose ENTIRE content is EXACTLY one lookaround
	// assertion ("(?=...)"/"(?!...)"/"(?<=...)"/"(?<!...)" - nothing else,
	// no other atom possible in ANY branch), quantified with a min=0
	// quantifier ("?", "*", "{0,N}"). Such a group can ONLY EVER produce a
	// zero-length match, so spec's own veto ALWAYS fires - the "attempt
	// the group" branch is OBSERVABLY UNREACHABLE, equivalent to the
	// group never being attempted at all. Rewriting the group to start
	// with "(?!)" (an always-failing assertion) achieves exactly that:
	// group numbering is preserved (Joni still allocates a slot for any
	// capturing groups inside, syntactically), but they're never actually
	// entered, matching spec's own discarded-capture outcome. A
	// quantified body that CAN also consume characters in some branch
	// (e.g. nullable-quantifier.js's "(a?b??)*") isn't touched by this -
	// whether THAT kind of iteration is zero-length is a per-ATTEMPT
	// dynamic property, not something a static rewrite can determine, so
	// it's correctly left to Joni's native (spec-incomplete) semantics.
	private static String neutralizeAlwaysEmptyOptionalLookaround(String s) {
		StringBuilder out = new StringBuilder(s.length());
		int i = 0;
		while (i < s.length()) {
			char c = s.charAt(i);
			if (c == '\\') {
				out.append(c);
				if (i + 1 < s.length()) out.append(s.charAt(i + 1));
				i += 2;
				continue;
			}
			if (c == '[') {
				int j = i + 1;
				if (j < s.length() && s.charAt(j) == '^') j++;
				if (j < s.length() && s.charAt(j) == ']') j++;
				while (j < s.length() && s.charAt(j) != ']') {
					if (s.charAt(j) == '\\') j++;
					j++;
				}
				j = Math.min(j + 1, s.length());
				out.append(s, i, j);
				i = j;
				continue;
			}
			if (c == '(') {
				int groupEnd;
				try {
					groupEnd = LookbehindReversal.scanGroup(s, i);
				} catch (RuntimeException e) {
					out.append(c);
					i++;
					continue;
				}
				// scanGroup() doesn't THROW for an unclosed "(" (e.g. the
				// malformed source new RegExp("(", "u")) - it falls
				// through its own scan loop and returns s.length() as a
				// bare fallback, with NO actual ")" at that position. Bail
				// out exactly like the exception-catch case above (copy
				// the "(" through literally and let Joni's own native
				// compile surface the real SyntaxError) rather than
				// trusting groupEnd and substring()-ing past the end of
				// any real group - confirmed via
				// unicode_restricted_brackets.js: this previously threw an
				// uncaught StringIndexOutOfBoundsException instead of the
				// expected SyntaxError (this pass runs BEFORE the
				// constructor's own try/catch that wraps native-compile
				// failures as a proper SyntaxError).
				if (groupEnd > s.length() || groupEnd == 0 || s.charAt(groupEnd - 1) != ')') {
					out.append(c);
					i++;
					continue;
				}
				if (i + 1 < s.length() && s.charAt(i + 1) == '?') {
					char k2 = i + 2 < s.length() ? s.charAt(i + 2) : '\0';
					boolean isLookaround = (k2 == '=' || k2 == '!')
							|| (k2 == '<' && i + 3 < s.length() && (s.charAt(i + 3) == '=' || s.charAt(i + 3) == '!'));
					if (isLookaround) {
						// A lookaround itself - not a group this pass
						// rewrites at ITS OWN level (JS doesn't allow
						// directly quantifying one outside sloppy AnnexB
						// mode, and even there the shape this pass targets
						// is a WRAPPING group, not the assertion itself) -
						// but recurse into its own body, unchanged
						// otherwise, to catch a candidate group nested
						// inside it.
						String inner = s.substring(i + 3, groupEnd - 1);
						String recursed = neutralizeAlwaysEmptyOptionalLookaround(inner);
						out.append(s, i, i + 3).append(recursed).append(')');
						i = groupEnd;
						continue;
					}
					int innerStart;
					if (k2 == ':') {
						innerStart = i + 3;
					} else if (k2 == '<') {
						int nameEnd = s.indexOf('>', i + 3);
						if (nameEnd < 0 || nameEnd >= groupEnd) {
							// Not actually a well-formed named group -
							// leave it entirely to Joni's own error
							// reporting rather than guess.
							out.append(s, i, groupEnd);
							i = groupEnd;
							continue;
						}
						innerStart = nameEnd + 1;
					} else {
						// Unrecognized "(?X" construct - e.g. an inline
						// MODIFIER group "(?i:...)"/"(?i-m:...)" (the
						// regexp-modifiers proposal, which GaltaJS
						// supports - see regexp-modifiers/*.js). This pass
						// doesn't understand modifier-group grammar, so
						// copy the WHOLE group through unchanged rather
						// than guess at where its content starts -
						// confirmed via regexp-modifiers/add-ignoreCase.js:
						// treating "(?i:" as a named group's "(?<" search
						// for a '>' that doesn't exist, corrupting the
						// pattern (StringIndexOutOfBounds).
						out.append(s, i, groupEnd);
						i = groupEnd;
						continue;
					}
					String prefix = s.substring(i, innerStart);
					String inner = s.substring(innerStart, groupEnd - 1);
					String recursedInner = neutralizeAlwaysEmptyOptionalLookaround(inner);
					int qEnd = scanZeroAllowingQuantifierEnd(s, groupEnd);
					boolean minZero = qEnd > groupEnd;
					if (minZero && isSoleLookaround(recursedInner)) {
						out.append(prefix).append("(?!)").append(recursedInner).append(')');
					} else {
						out.append(prefix).append(recursedInner).append(')');
					}
					out.append(s, groupEnd, qEnd);
					i = qEnd;
					continue;
				}
				// A plain "(...)" capturing group.
				String inner = s.substring(i + 1, groupEnd - 1);
				String recursedInner = neutralizeAlwaysEmptyOptionalLookaround(inner);
				int qEnd = scanZeroAllowingQuantifierEnd(s, groupEnd);
				boolean minZero = qEnd > groupEnd;
				if (minZero && isSoleLookaround(recursedInner)) {
					out.append('(').append("(?!)").append(recursedInner).append(')');
				} else {
					out.append('(').append(recursedInner).append(')');
				}
				out.append(s, groupEnd, qEnd);
				i = qEnd;
				continue;
			}
			out.append(c);
			i++;
		}
		return out.toString();
	}

	// True iff `inner` (a group's own already-recursed content) is EXACTLY
	// one lookaround assertion spanning the whole string - nothing before
	// or after it at the same depth.
	private static boolean isSoleLookaround(String inner) {
		if (inner.length() < 4 || inner.charAt(0) != '(' || inner.charAt(1) != '?') return false;
		char k = inner.charAt(2);
		boolean isLookaround = (k == '=' || k == '!')
				|| (k == '<' && inner.length() > 3 && (inner.charAt(3) == '=' || inner.charAt(3) == '!'));
		if (!isLookaround) return false;
		int end;
		try {
			end = LookbehindReversal.scanGroup(inner, 0);
		} catch (RuntimeException e) {
			return false;
		}
		return end == inner.length();
	}

	// Index just past a quantifier at `i` that allows ZERO repetitions
	// ("?", "*", "{0}", "{0,}", "{0,N}", each optionally followed by a
	// lazy "?") - or `i` unchanged if there's no quantifier there, or one
	// requiring at least 1 repetition.
	private static int scanZeroAllowingQuantifierEnd(String s, int i) {
		if (i >= s.length()) return i;
		char c = s.charAt(i);
		if (c == '?' || c == '*') {
			int j = i + 1;
			if (j < s.length() && s.charAt(j) == '?') j++;
			return j;
		}
		if (c == '{') {
			int close = s.indexOf('}', i);
			if (close < 0) return i;
			String inside = s.substring(i + 1, close);
			if (!inside.matches("\\d+(,\\d*)?")) return i;
			int comma = inside.indexOf(',');
			int min = comma < 0 ? Integer.parseInt(inside) : Integer.parseInt(inside.substring(0, comma));
			if (min != 0) return i;
			int j = close + 1;
			if (j < s.length() && s.charAt(j) == '?') j++;
			return j;
		}
		return i;
	}

	// A ClassEscape (\d \D \s \S \w \W) directly adjacent to a "-" inside a
	// character class, where that "-" reads as forming a range (i.e. it's
	// not a leading/trailing literal dash), is only valid via Annex B's
	// non-unicode leniency (e.g. legacy "[\d-a]") - under the "u" flag it's
	// a required SyntaxError instead (confirmed via
	// unicode_restricted_character_class_escape.js). A Unicode property
	// escape (\p{...}/\P{...}) is a CharacterClassEscape too, so it is
	// rejected the same way on either side of the "-" (e.g. "[\p{Hex}-a]",
	// "[--\p{Hex}]").
	private void validateNoClassEscapeInRange(String source) {
		int len = source.length();
		boolean inCC = false;
		int classStart = -1; // index of the first ClassAtom (just past "[" or "[^")
		for (int i = 0; i < len; i++) {
			char c = source.charAt(i);
			if (c == '\\') {
				i++;
				continue;
			}
			if (!inCC) {
				if (c == '[') {
					inCC = true;
					classStart = i + 1;
					if (classStart < len && source.charAt(classStart) == '^') classStart++;
				}
				continue;
			}
			if (c == ']') {
				inCC = false;
				continue;
			}
			if (c == '-' && i > classStart && i + 1 < len && source.charAt(i + 1) != ']') {
				boolean leadingEscape = (i >= 2 && source.charAt(i - 2) == '\\'
						&& isClassEscapeLetter(source.charAt(i - 1)))
						|| endsWithPropertyEscape(source, i);
				boolean trailingEscape = i + 2 < len && source.charAt(i + 1) == '\\'
						&& (isClassEscapeLetter(source.charAt(i + 2))
							|| source.charAt(i + 2) == 'p' || source.charAt(i + 2) == 'P');
				if (leadingEscape || trailingEscape) {
					throw RuntimeUtil.syntaxError("Invalid character class range containing a CharacterClassEscape");
				}
			}
		}
	}

	// True iff the class atom ending just before `end` is a "\p{...}"/
	// "\P{...}" property escape (its backslash not itself escaped).
	private static boolean endsWithPropertyEscape(String source, int end) {
		if (end < 1 || source.charAt(end - 1) != '}') return false;
		int brace = source.lastIndexOf('{', end - 1);
		if (brace < 2 || source.indexOf('}', brace) != end - 1) return false;
		char p = source.charAt(brace - 1);
		if ((p != 'p' && p != 'P') || source.charAt(brace - 2) != '\\') return false;
		int backslashes = 0;
		for (int k = brace - 2; k >= 0 && source.charAt(k) == '\\'; k--) backslashes++;
		return (backslashes & 1) == 1;
	}

	private static boolean isClassEscapeLetter(char c) {
		return c == 'd' || c == 'D' || c == 's' || c == 'S' || c == 'w' || c == 'W';
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

	// Per Annex B.1.2, "\k" is only ever parsed as a real GroupNameBackreference
	// when the PATTERN AS A WHOLE contains at least one real named group
	// (GroupSpecifier, i.e. "(?<name>" - not a lookbehind's "(?<=" / "(?<!",
	// which look similar but aren't a GroupName at all) - if it doesn't, "\k"
	// (regardless of what happens to follow it, even something shaped like
	// "<name>") falls through to IdentityEscape, matching a bare literal 'k'
	// character. This only matters in non-unicode mode; unicode-mode "\k" is
	// always a real backreference attempt regardless. Mirrors
	// countCapturingGroups()'s identical named-group detection, but only
	// needs a yes/no answer.
	private static boolean hasNamedGroup(String source) {
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
			} else if (c == '(' && !inCC && i + 3 < source.length() && source.charAt(i + 1) == '?'
					&& source.charAt(i + 2) == '<' && source.charAt(i + 3) != '=' && source.charAt(i + 3) != '!') {
				return true;
			}
		}
		return false;
	}

	private String translatePattern(String source) {
		boolean unicodeMode = regExp.isUnicode() || regExp.isUnicodeSets();
		boolean hasNamedGroup = hasNamedGroup(source);

		StringBuilder result = new StringBuilder(source.length());
		boolean inCC = false;
		// Tracks whether the class atom immediately before the CURRENT loop
		// position was one of the multi-character CharacterClassEscape
		// shorthands (\d \D \w \W \s \S) - needed by the '-' case below to
		// implement Annex B.1.4's CharacterRangeOrUnion: a '-' adjacent to a
		// class escape (which never contains "exactly one character") must be
		// a LITERAL '-', not a range operator, unlike Joni's own native class
		// parser which throws ("char-class value at end of range") on seeing
		// one. See annexB/language/literals/regexp/non-empty-class-ranges(-no-
		// dash).js.
		boolean lastWasClassEscape = false;
		// Tracks whether the current position is inside a GroupSpecifier's
		// name (between "(?<" and its closing ">") - '$' is a valid
		// IdentifierStart character in a group name (e.g. "(?<$foo>...)")
		// and must be passed through literally there, not misread as an
		// end-of-string/multiline-$ assertion (confirmed via
		// Symbol.replace/named-groups.js: a group literally named "$..."
		// regressed into being treated as a "$" assertion once raw '$'
		// passthrough was replaced with an explicit \\z in the non-multiline
		// case below).
		boolean inGroupName = false;
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
		// Same story for the ES2025 RegExp modifiers proposal's "s"/"m"
		// letters - a local (?s:...)/(?-m:...) override of dotAll/multiline
		// must be visible to '.'/'^'/'$' translation (see their handling
		// below), which - like \\w/\\b above - can't rely on Joni's own
		// compile-time Option.MULTILINE (Joni's own "dot matches newline"
		// flag, fixed for the whole pattern).
		java.util.ArrayDeque<Boolean> multilineStack = new java.util.ArrayDeque<>();
		multilineStack.push(regExp.isMultiline());
		java.util.ArrayDeque<Boolean> dotAllStack = new java.util.ArrayDeque<>();
		dotAllStack.push(regExp.isDotAll());

		for (int i = 0; i < source.length(); i++) {
			char c = source.charAt(i);
			boolean prevWasClassEscape = lastWasClassEscape;
			lastWasClassEscape = false;
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
						// throw, not silently match literal "x". Emit the BARE
						// literal character (no backslash) rather than passing
						// "\\x" through to Joni - Joni's own \\x is a byte-oriented
						// hex escape (OP_ESC_X_HEX2) that gets first crack at
						// whatever digit(s) happen to follow (e.g. "\\xa" - one
						// valid hex digit) and fails to compile ("too short
						// multibyte code string") instead of leaving "a" alone as
						// a separate literal the way JS's IdentityEscape requires
						// (confirmed via annexB/built-ins/RegExp/
						// incomplete_hex_unicode_escape.js's "\\xa"/"\\ua" cases -
						// same rationale already applied to the analogous
						// incomplete "\\u" case in translateUnicodeEscape()).
						if (unicodeMode) {
							throw RuntimeUtil.syntaxError("Invalid escape sequence: \\x");
						}
						result.append(next);
						i++;
					} else if (next == 'u') {
						i++;
						i = translateUnicodeEscape(source, i, result, unicodeMode, inGroupName, inCC, ignoreCaseStack.peek());
					} else if (next == 'c') {
						char after = (i + 2 < source.length()) ? source.charAt(i + 2) : '\0';
						boolean validControlLetter = (after >= 'a' && after <= 'z') || (after >= 'A' && after <= 'Z');
						if (!validControlLetter && inCC && !unicodeMode && (after == '_' || (after >= '0' && after <= '9'))) {
							// Annex B.1.2 ClassEscape :: [~U] c ClassControlLetter
							// (ClassControlLetter :: DecimalDigit | _) - legal only
							// inside a character class in non-unicode mode. Per spec,
							// d = the character whose value is ch's character value
							// modulo 32, where ch is the ClassControlLetter itself
							// (the digit/underscore character, NOT an actual control
							// letter) - e.g. \\c0 -> '0' is 0x30, 0x30 % 32 = 0x10.
							appendUnicodeEscape(result, after % 32);
							i += 2;
							break;
						}
						if (!validControlLetter && unicodeMode) {
							// \c not followed by a ControlLetter is an early
							// SyntaxError in unicode mode (IdentityEscape doesn't
							// cover 'c' there) - isJsRegexEscape() below would
							// otherwise let this fall through as an unchecked
							// passthrough regardless of what follows the 'c'.
							throw RuntimeUtil.syntaxError("Invalid escape sequence: \\c");
						}
						if (!validControlLetter) {
							// Annex B.1.2 CharacterEscape/ClassEscape - when 'c' is not
							// followed by a valid ControlLetter (and, inside a class,
							// not a ClassControlLetter either - handled above), the
							// escape doesn't apply at all: the backslash itself is a
							// literal SourceCharacter, and the following 'c' is a
							// separate, ordinary atom. Emitting raw "\\c" here would
							// have Joni parse ITS OWN control-escape syntax (which,
							// unlike JS, doesn't require a following letter) instead of
							// two independent literal characters - confirmed via
							// class-escape.js's "\\c0" outside a class matching Joni's
							// \\c-as-control-code-mod-32 when it must not match at all.
							// A doubled backslash is Joni's own escape for a literal
							// backslash; 'c' needs no escaping as an ordinary literal.
							result.append('\\').append('\\').append(next);
							i++;
							break;
						}
						result.append('\\').append(next);
						i++;
					} else if (next == 'k') {
						boolean validNamedBackref = i + 2 < source.length() && source.charAt(i + 2) == '<';
						if (!unicodeMode && !hasNamedGroup) {
							// Annex B.1.2: the pattern as a whole has no real
							// named group anywhere, so "\k" - regardless of what
							// follows, even something shaped like "<name>" -
							// falls through to IdentityEscape (a bare literal
							// 'k', not a backreference attempt at all). See
							// hasNamedGroup()'s own comment.
							result.append('k');
							i++;
						} else {
							if (!validNamedBackref) {
								// Same story as \c above: \k not followed by a
								// GroupName's opening "<" is an early SyntaxError in
								// unicode mode, and in a non-unicode pattern that
								// has a named group (Annex B's IdentityEscape
								// excludes 'k' there) - isJsRegexEscape() below
								// would otherwise let it fall through as an
								// unchecked passthrough.
								throw RuntimeUtil.syntaxError("Invalid escape sequence: \\k");
							}
							result.append('\\').append(next);
							i++;
						}
					} else if (next == 'd' || next == 'D') {
						// Replaces Joni's own \\d/\\D entirely: Joni's native \\d
						// is Unicode decimal-digit-category (Nd) based, far wider
						// than JS's actual digit set (confirmed empirically: Joni's
						// \\d matches U+0660 ARABIC-INDIC DIGIT ZERO, U+0966
						// DEVANAGARI DIGIT ZERO, and U+FF10 FULLWIDTH DIGIT ZERO,
						// none of which JS's \\d should match - see
						// character-class-digit-class-escape-negative-cases.js /
						// character-class-non-digit-class-escape-positive-cases.js).
						// VClassParser's own DIGIT_RANGES already gets this right
						// for "v" mode - this mirrors that same ASCII-only [0-9].
						result.append(buildDigitClass(next == 'D', inCC, unicodeMode));
						i++;
						// A CharacterClassEscape (see '-' case's lastWasClassEscape
						// comment above) - only meaningful while inCC, but harmless to
						// set unconditionally (a '-' is only ever inspected while inCC).
						lastWasClassEscape = true;
					} else if (next == 'w' || next == 'W') {
						// Replaces Joni's own \\w/\\W entirely (not just under a
						// modifier scope) - see buildWordCharClass()'s comment for
						// why: Joni's own \\w is Unicode-category-based, far wider
						// than JS's actual word-char set, independent of ignoreCase.
						result.append(buildWordCharClass(ignoreCaseStack.peek(), next == 'W', inCC));
						i++;
						lastWasClassEscape = true;
					} else if (next == 's' || next == 'S') {
						// Replaces Joni's own \\s/\\S entirely: Joni's native
						// whitespace class includes U+0085 (NEXT LINE) - a
						// Unicode "control" whitespace-ish character that is
						// simply NOT part of JS's own WhiteSpace/LineTerminator
						// productions at all (confirmed via
						// character-class-escape-non-whitespace.js: \\S must
						// MATCH U+0085, i.e. \\s must NOT).
						result.append(buildWhiteSpaceClass(next == 'S', inCC, unicodeMode));
						i++;
						lastWasClassEscape = true;
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
							String dataTableFragment = translatePropertyFromDataTable(propExpr, negated, inCC, ignoreCaseStack.peek());
							if (dataTableFragment != null) {
								result.append(dataTableFragment);
							} else {
								// A binary-property-of-strings name (\p{RGI_Emoji},
								// \p{Basic_Emoji}, ...) is only meaningful as a
								// standalone atom under the "v" flag.
								String stringPropertyFragment = regExp.isUnicodeSets()
										? translateStringPropertyEscape(propExpr, negated)
										: null;
								if (stringPropertyFragment != null) {
									result.append(stringPropertyFragment);
								} else {
									// The data table holds every property name and
									// value ECMA-262 allows (its tables of General
									// Category values, scripts and binary
									// properties, with their aliases), matched
									// exactly: anything else - loose matching
									// ("\p{ Lu }", "\p{lu}"), an "In"/"Is" prefix,
									// a script without "Script=", a Unicode
									// property the spec does not expose
									// (Other_Alphabetic, Hyphen) - is an early
									// SyntaxError, where Joni's own lookup would
									// accept it.
									throw RuntimeUtil.syntaxError("Invalid Unicode property escape: \\{0}'{'{1}'}'", String.valueOf(next), propExpr);
								}
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
						// Non-unicode: IdentityEscape - per spec this means
						// "match this SourceCharacter", i.e. the backslash is
						// simply dropped, NOT "pass \\<letter> through to Joni
						// and trust it to mean the same bare literal". Some
						// letters are meaningful ONLY to Joni/Oniguruma, not to
						// JS (e.g. "\\a" is Joni's own alarm/BEL escape,
						// matching U+0007 - not tracked in isJsRegexEscape()
						// since it isn't a JS escape at all), so re-emitting the
						// backslash silently changed what the pattern matched
						// (confirmed via annexB/built-ins/RegExp/named-groups/
						// non-unicode-malformed.js: "/(?<a>\\a)/.test('a')" must
						// match the literal letter 'a', not Joni's BEL).
						result.append(next);
						i++;
					} else if (unicodeMode && !isRegexSyntaxCharacter(next) && next != '/' && !isJsRegexEscape(next)) {
						// isJsRegexEscape(next) excludes e.g. "\\b" reaching
						// here from INSIDE a character class: outside a
						// class \\b/\\B is already handled above (as the
						// word-boundary Assertion, guarded by !inCC), but
						// inside one it's a genuinely valid, different
						// escape (backspace, U+0008) that Joni's own class
						// parser already handles correctly when passed
						// through unchanged - not an invalid identity
						// escape at all (confirmed via
						// unicode_character_class_backspace_escape.js:
						// `/[\\b]/u` regressed into wrongly throwing once
						// this check started rejecting anything not in
						// isRegexSyntaxCharacter()).
						// AtomEscape[U] :: CharacterEscape[?U] ::
						// IdentityEscape[?U] only covers SyntaxCharacter and
						// "/" in unicode mode - anything else reaching this
						// final fallback (whitespace, punctuation outside
						// the syntax-character set, etc.) is an early
						// SyntaxError there, not a legacy Annex B identity
						// escape (confirmed via
						// unicode_restricted_identity_escape.js: `\ ` (escaped
						// space) must throw under the "u" flag).
						throw RuntimeUtil.syntaxError("Invalid escape sequence: \\{0}", String.valueOf(next));
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
							inGroupName = true;
						}
						ModifierGroupFlags mf = parseModifierGroup(source, i,
								ignoreCaseStack.peek(), multilineStack.peek(), dotAllStack.peek());
						ignoreCaseStack.push(mf != null ? mf.ignoreCase : ignoreCaseStack.peek());
						multilineStack.push(mf != null ? mf.multiline : multilineStack.peek());
						dotAllStack.push(mf != null ? mf.dotAll : dotAllStack.peek());
					}
					result.append(c);
					break;
				case ')':
					// All three stacks are always pushed/popped together (see
					// the '(' case above) - one size check covers all of them.
					if (!inCC && ignoreCaseStack.size() > 1) {
						ignoreCaseStack.pop();
						multilineStack.pop();
						dotAllStack.pop();
					}
					result.append(c);
					break;
				case '-':
					if (inCC) {
						// Annex B.1.4 CharacterRangeOrUnion: in non-unicode mode, if
						// EITHER side of what looks like a range isn't "exactly one
						// character" (a CharacterClassEscape like \\d/\\D/\\w/\\W/\\s/\\S
						// is a whole CharSet, never a single character), the '-' is
						// NOT a range operator at all - it's just another literal
						// member of the resulting (unioned) CharSet. Joni's own class
						// parser has no such fallback: it always tries to build a
						// range and throws ("char-class value at end of range" /
						// "unmatched range specifier") when the adjacent atom isn't a
						// single character - so escape the '-' here to force Joni to
						// treat it as a literal instead. See annexB/language/literals/
						// regexp/non-empty-class-ranges(-no-dash).js.
						boolean nextIsClassEscape = i + 2 < source.length() && source.charAt(i + 1) == '\\'
								&& isClassEscapeShorthandLetter(source.charAt(i + 2));
						if (prevWasClassEscape || nextIsClassEscape) {
							result.append("\\-");
							break;
						}
					}
					result.append(c);
					break;
				case '[':
					if (!inCC && regExp.isUnicodeSets()) {
						// 'v'-mode ("unicodeSets") character classes support set
						// operations (&&, --, nested [...]) that Joni's own class
						// parser has no notion of - see VClassParser's class
						// comment for the full approach. The whole bracketed
						// expression is consumed here in one shot (recursing for
						// any nested class), fully resolved to a flat, already-
						// evaluated codepoint range list, and emitted as an
						// ordinary Joni class - so the old per-character inCC
						// state machine below never sees v-mode class content at
						// all.
						VClassParser.Result vr = new VClassParser(ignoreCaseStack.peek()).parseClassAt(source, i);
						result.append(vr.joniText);
						i = vr.endIndex - 1; // outer loop's own i++ covers the rest
						break;
					}
					inCC = true;
					result.append(c);
					break;
				case ']':
					if (!inCC) {
						// A ']' with no matching open '[' is only valid as an
						// Annex B PatternCharacter (legacy sloppy-mode
						// leniency) - unicode mode drops that extension
						// entirely (confirmed via
						// unicode_restricted_brackets.js: RegExp("]","u")
						// must throw).
						if (unicodeMode) {
							throw RuntimeUtil.syntaxError("Lone quantifier brackets");
						}
					} else {
						inCC = false;
					}
					result.append(c);
					break;
				case '>':
					inGroupName = false;
					result.append(c);
					break;
				case '{':
					if (!inCC) {
						int consumed = tryClampQuantifier(source, i, result);
						if (consumed > 0) {
							i += consumed - 1; // -1: outer loop's own i++ covers one
							break;
						}
						if (unicodeMode) {
							// Same Annex B leniency drop as ']' above - an
							// incomplete/malformed "{" that isn't a real
							// Quantifier is only a legacy PatternCharacter.
							throw RuntimeUtil.syntaxError("Lone quantifier brackets");
						}
					}
					result.append(c);
					break;
				case '}':
					if (!inCC && unicodeMode) {
						// A standalone "}" (not consumed as part of a "{...}"
						// Quantifier by the '{' case above) is likewise only
						// valid as a legacy PatternCharacter.
						throw RuntimeUtil.syntaxError("Lone quantifier brackets");
					}
					result.append(c);
					break;
				case '.':
					if (!inCC) {
						// Never pass a raw '.' through to Joni: Joni's own
						// native dot is governed by a single PATTERN-WIDE
						// compile-time option, which can't reflect a LOCAL
						// (?s:...)/(?-s:...) override - so both branches are
						// spelled out as explicit character classes instead,
						// each independent of that option.
						result.append(dotAllStack.peek() ? DOT_ANY : DOT_REPLACEMENT);
					} else {
						result.append(c);
					}
					break;
				case '^':
					// Raw '^' passthrough is safe in the non-multiline case:
					// Joni's Option.SINGLELINE is set UNCONDITIONALLY for the
					// whole pattern (see its own comment above), so a raw '^'
					// always means "start of string only" regardless of local
					// scope - no compile-time-option conflict like '.' has.
					if (!inCC && multilineStack.peek()) {
						result.append(CARET_MULTILINE);
					} else {
						result.append(c);
					}
					break;
				case '$':
					if (inGroupName) {
						// '$' is a valid IdentifierStart character in a
						// GroupSpecifier name (e.g. "(?<$foo>...)") - literal
						// there, not an end-of-string/multiline assertion.
						result.append(c);
					} else if (!inCC && multilineStack.peek()) {
						result.append(DOLLAR_MULTILINE);
					} else if (!inCC) {
						// Unlike '^', a raw '$' is NOT safe to pass through
						// even under the always-on Option.SINGLELINE: Joni
						// (Perl/Oniguruma heritage) still treats raw '$' as
						// matching just before a FINAL trailing line
						// terminator, not only at the true end of string -
						// confirmed via /c$/.test("c\n") wrongly matching.
						// JS's non-multiline '$' has no such exception, so
						// it's spelled out as an explicit absolute-end-of-
						// string assertion instead.
						result.append("\\z");
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

	// Ground-truth codepoint data (the same unicode-properties.txt table built
	// for the JDK engine from test262's own generated property-escapes suite -
	// see UnicodePropertyData/UnicodeProperties in the jdk package) takes
	// precedence over Joni's native \p{...} support: Joni's bundled Unicode
	// property tables are close but not byte-for-byte exact matches for every
	// alias test262 exercises (e.g. Script_Extensions), and this table is
	// exact by construction. Returns null (falling back to Joni's native
	// \p{...}/\P{...} support) when the exact property expression isn't in
	// the table.
	private static String translatePropertyFromDataTable(String propExpr, boolean negated, boolean insideCharClass, boolean ignoreCase) {
		int[] ranges = UnicodePropertyData.getRanges(propExpr);
		if (ranges == null) {
			return null;
		}

		if (ignoreCase) {
			// Spec's CharacterSetMatcher applies a case-fold CLOSURE to the
			// TARGET set (already negated, if "\P{}") when ignoreCase is
			// active: target ∪ {c : Canonicalize(c) ∈ target} - NOT a
			// per-codepoint comparison at match time, and NOT something
			// Joni's own native \p{}/\P{} compiler does at all (confirmed
			// via test262 regexp-modifiers/add-ignoreCase-affects-slash-
			// upper-p.js: under a LOCAL "(?i:...)" override, "\P{Lu}" must
			// match "A" - Canonicalize('A') folds to 'a', which is NOT in
			// Lu, so 'A' joins Lu's COMPLEMENT's closure - Joni's own
			// property compiler doesn't consult the enclosing modifier
			// scope for this at all). Emitted as an explicit, already-
			// fully-expanded POSITIVE range list, sidestepping Joni's
			// native property compiler (and the nested-negation
			// restriction below) entirely - negated is not applied a
			// second time.
			int[] target = negated ? complementRanges(ranges) : ranges;
			int[] closure = computeCaseFoldClosure(target);
			StringBuilder csb = new StringBuilder();
			csb.append('[');
			for (int i = 0; i < closure.length; i += 2) {
				appendRangeSplitAtSurrogateBoundary(csb, closure[i], closure[i + 1]);
			}
			csb.append(']');
			return insideCharClass ? csb.substring(1, csb.length() - 1) : csb.toString();
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

	// Standalone \p{...}/\P{...} atom (not inside a "[...]" - VClassParser
	// handles the in-class case) whose name is a binary-property-of-STRINGS
	// (RGI_Emoji, Basic_Emoji, ...) - only meaningful under the "v" flag
	// (callers must already have checked regExp.isUnicodeSets()). Returns
	// null when propExpr isn't a recognized string-valued property name, so
	// the caller falls back to its existing Joni-passthrough behavior
	// (which correctly rejects both an unrecognized name and, via the same
	// path, u-mode's use of a string-valued name at all).
	private static String translateStringPropertyEscape(String propExpr, boolean negated) {
		int[][] sequences = UnicodeStringPropertyData.getSequences(propExpr);
		if (sequences == null) {
			return null;
		}
		if (negated) {
			// UnicodePropertyValueExpression's own early error: a
			// property-of-strings can never be negated, in or out of a
			// class (spec: "It is a Syntax Error if MayContainStrings of
			// the UnicodePropertyValueExpression is true" for \\P{...}).
			throw RuntimeUtil.syntaxError(
					"A Unicode property of strings cannot be negated: \\P'{'{0}'}'", propExpr);
		}
		List<int[]> multiCodepointSeqs = new ArrayList<>();
		int[] singleCodepointRanges = splitOutSingleCodepointRanges(sequences, multiCodepointSeqs);
		return buildStringSetAlternation(multiCodepointSeqs, singleCodepointRanges);
	}

	// Splits a string-valued Unicode property's sequences into the
	// single-codepoint members (some properties, e.g. Basic_Emoji, mix
	// single- and multi-codepoint members for the same base character - one
	// entry with an emoji-presentation selector, one without) and the
	// genuinely multi-codepoint sequences, since the two need entirely
	// different pattern-syntax representations (a plain class vs an
	// alternation branch). Shared by VClassParser's in-class operand
	// handling and translateStringPropertyEscape() above.
	static int[] splitOutSingleCodepointRanges(int[][] sequences, List<int[]> multiCodepointSeqsOut) {
		List<Integer> singles = new ArrayList<>();
		for (int[] seq : sequences) {
			if (seq.length == 1) {
				singles.add(seq[0]);
				singles.add(seq[0]);
			} else {
				multiCodepointSeqsOut.add(seq);
			}
		}
		int[] out = new int[singles.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = singles.get(i);
		}
		return out;
	}

	// A character class can only ever match ONE character, so a set
	// containing multi-codepoint STRING members (from a string-valued
	// \p{...} property, or a \q{...} alternative longer than one codepoint)
	// can't compile to a Joni "[...]" class at all - the pattern position
	// becomes an alternation instead: "(?:seq1|seq2|...|[remainingRanges])",
	// a non-capturing group so it doesn't disturb capture-group numbering
	// and splices into the surrounding pattern text exactly like any other
	// atom (quantifiers, etc. all apply to it the same way they would to a
	// class). `sequences` must be non-empty (callers only take this path
	// once they know there's at least one string member); `remainingRanges`
	// (any ordinary single-codepoint members of the same set) may be empty,
	// in which case the trailing "[...]" fallback branch is omitted
	// entirely. Alternatives are emitted longest-first: not required for
	// correctness (JS regexp backtracks, so any order eventually finds a
	// matching decomposition against a full-string-anchored test), but a
	// free pragmatic tie-break for the (real, e.g. Basic_Emoji) case where
	// one member is itself a prefix of another.
	static String buildStringSetAlternation(List<int[]> sequences, int[] remainingRanges) {
		List<int[]> sorted = new ArrayList<>(sequences);
		sorted.sort((a, b) -> b.length - a.length);
		StringBuilder sb = new StringBuilder();
		sb.append("(?:");
		boolean first = true;
		for (int[] seq : sorted) {
			if (!first) sb.append('|');
			first = false;
			for (int cp : seq) {
				appendLiteralCodepointOutsideClass(sb, cp);
			}
		}
		if (remainingRanges != null && remainingRanges.length > 0) {
			if (!first) sb.append('|');
			sb.append('[');
			for (int i = 0; i < remainingRanges.length; i += 2) {
				appendRangeSplitAtSurrogateBoundary(sb, remainingRanges[i], remainingRanges[i + 1]);
			}
			sb.append(']');
		}
		sb.append(')');
		return sb.toString();
	}

	// Emits a codepoint as a literal character OUTSIDE a class (unlike
	// appendEscapedCodePoint, which escapes for IN-class syntax) - escapes
	// it first if it would otherwise be read as pattern syntax (a
	// RegExpDefined SyntaxCharacter). Only used by buildStringSetAlternation
	// above; every codepoint here comes from Unicode string-property/\q{...}
	// data, never from the original source text, so nothing upstream has
	// already escaped it.
	private static void appendLiteralCodepointOutsideClass(StringBuilder sb, int cp) {
		if (cp < 128 && "^$\\.*+?()[]{}|/".indexOf(cp) >= 0) {
			sb.append('\\');
		}
		sb.appendCodePoint(cp);
	}

	// Computes the complement of a set of codepoint ranges over the full
	// [0, 0x10FFFF] Unicode range. Sorts and merges overlapping/adjacent
	// ranges first rather than assuming unicode-properties.txt's own ranges
	// are already in that shape - cheap insurance against a data-table
	// entry that isn't, since a wrong complement here would silently match
	// the wrong set rather than fail loudly.
	static int[] complementRanges(int[] ranges) {
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

	// Sorts and merges an arbitrary (possibly unsorted, possibly
	// overlapping) range list into ascending, non-overlapping pairs -
	// needed before binary-searching it (see isInSortedRanges() /
	// computeCaseFoldClosure() below), mirroring complementRanges()'s own
	// defensive sort-and-merge pass but returning the POSITIVE set itself
	// rather than its complement.
	private static int[] normalizeRanges(int[] ranges) {
		int pairCount = ranges.length / 2;
		Integer[] order = new Integer[pairCount];
		for (int i = 0; i < pairCount; i++) {
			order[i] = i;
		}
		java.util.Arrays.sort(order, (a, b) -> Integer.compare(ranges[a * 2], ranges[b * 2]));

		java.util.List<Integer> out = new java.util.ArrayList<>();
		int curStart = -1, curEnd = -2;
		for (int idx : order) {
			int start = ranges[idx * 2];
			int end = ranges[idx * 2 + 1];
			if (start > curEnd + 1) {
				if (curStart >= 0) {
					out.add(curStart);
					out.add(curEnd);
				}
				curStart = start;
				curEnd = end;
			} else if (end > curEnd) {
				curEnd = end;
			}
		}
		if (curStart >= 0) {
			out.add(curStart);
			out.add(curEnd);
		}
		int[] result = new int[out.size()];
		for (int i = 0; i < result.length; i++) {
			result[i] = out.get(i);
		}
		return result;
	}

	private static boolean isInSortedRanges(int cp, int[] sortedRanges) {
		int lo = 0, hi = sortedRanges.length / 2 - 1;
		while (lo <= hi) {
			int mid = (lo + hi) >>> 1;
			int start = sortedRanges[mid * 2];
			int end = sortedRanges[mid * 2 + 1];
			if (cp < start) {
				hi = mid - 1;
			} else if (cp > end) {
				lo = mid + 1;
			} else {
				return true;
			}
		}
		return false;
	}

	// Spec's Canonicalize-closure for a character set under ignoreCase:
	// target ∪ {c : Canonicalize(c) ∈ target}. Canonicalize is
	// approximated via Character.toLowerCase(int) - the JDK exposes no
	// API for Unicode's own official CaseFolding.txt simple-folding
	// table, but toLowerCase() agrees with it for every script exercised
	// by test262's own regexp-modifiers/*.js and u-case-mapping.js
	// coverage (both fold toward the same lowercase representative for
	// ordinary cased scripts - the difference only shows up for a
	// handful of special-cased codepoints like U+0130/U+0131 Turkish
	// dotless/dotted I, not exercised here). One-time, compile-time cost
	// (iterates every codepoint once) - only paid when a "\p{}"/"\P{}"
	// atom is actually reached under an active ignoreCase (global or a
	// LOCAL "(?i:...)" override), never at match time.
	private static int[] computeCaseFoldClosure(int[] targetRanges) {
		int[] sorted = normalizeRanges(targetRanges);
		java.util.List<Integer> out = new java.util.ArrayList<>();
		int start = -1;
		for (int cp = 0; cp <= Character.MAX_CODE_POINT; cp++) {
			boolean in = isInSortedRanges(cp, sorted);
			if (!in) {
				int folded = Character.toLowerCase(cp);
				if (folded != cp) {
					in = isInSortedRanges(folded, sorted);
				}
			}
			if (in) {
				if (start < 0) {
					start = cp;
				}
			} else if (start >= 0) {
				out.add(start);
				out.add(cp - 1);
				start = -1;
			}
		}
		if (start >= 0) {
			out.add(start);
			out.add(Character.MAX_CODE_POINT);
		}
		int[] result = new int[out.size()];
		for (int i = 0; i < result.length; i++) {
			result[i] = out.get(i);
		}
		return result;
	}

	// Truncates a sorted range list to the BMP (drops anything entirely
	// above 0xFFFF, clips a range that straddles the boundary) - see
	// buildWhiteSpaceClass()'s own comment for why a non-unicode-mode
	// complement must never reach into astral territory at all.
	private static int[] capRangesAtBmp(int[] ranges) {
		java.util.List<Integer> out = new java.util.ArrayList<>();
		for (int i = 0; i < ranges.length; i += 2) {
			int start = ranges[i];
			int end = ranges[i + 1];
			if (start > 0xFFFF) break;
			out.add(start);
			out.add(Math.min(end, 0xFFFF));
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
	static void appendRangeSplitAtSurrogateBoundary(StringBuilder sb, int start, int end) {
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

	// Like appendUnicodeEscape(), but additionally implements Annex B's own
	// non-unicode-mode Canonicalize carve-out (22.2.2.9's real algorithm,
	// step 3.d/e): "if ch's codepoint >= 128 and toUppercase(ch)'s single
	// codepoint is < 128, return ch unchanged (no folding)" - specifically
	// there to stop a character OUTSIDE the ASCII range from case-folding
	// INTO it (e.g. U+212A KELVIN SIGN, whose toUpperCase() is itself 'K'
	// U+004B - Joni's own native IGNORECASE case-fold table doesn't
	// implement this Annex B nuance at all, unconditionally treating
	// U+212A as equivalent to 'k'/'K' regardless of mode - confirmed via
	// test262 u-case-mapping.js: "/\\u212a/i.test('k')" must be false).
	// Only reachable in non-unicode mode with ignoreCase active and NOT
	// inside a character class - wraps the literal in a "(?-i:...)"
	// inline-modifier group (GaltaJS/Joni already natively support the
	// regexp-modifiers proposal's syntax, used here purely as an internal
	// mechanism, not exposed to the user's own pattern) to force exactly
	// this one atom to match case-SENSITIVELY, leaving everything else's
	// ignoreCase behavior - and quantifiers applied to this atom, which
	// still see it as a single atom either way - untouched. Narrow scope:
	// only the two "\\uHHHH"/"\\u{HHHH}" escape sites test262 actually
	// exercises this through; a bare non-ASCII literal character written
	// directly in source (not through an escape) isn't covered.
	private static void appendCaseAwareUnicodeEscape(StringBuilder result, int charValue, boolean unicodeMode, boolean inCC, boolean ignoreCase) {
		if (!unicodeMode && ignoreCase && !inCC && charValue >= 128 && hasNoAnnexBFoldPartner(charValue)) {
			result.append("(?-i:");
			appendUnicodeEscape(result, charValue);
			result.append(')');
			return;
		}
		appendUnicodeEscape(result, charValue);
	}

	// Non-unicode Canonicalize (spec 22.2.2.9's non-unicode branch):
	// uppercase ch (as a full String, to detect a multi-character
	// mapping like German lowercase "ß" -> "SS", which per spec leaves
	// ch UNCHANGED rather than folding), then apply the ASCII-crossing
	// guard (a char >= 128 whose uppercase form is ASCII stays as
	// itself).
	private static int annexBCanonicalize(int ch) {
		String upper = new String(Character.toChars(ch)).toUpperCase(java.util.Locale.ROOT);
		if (upper.codePointCount(0, upper.length()) != 1) {
			return ch;
		}
		int cu = upper.codePointAt(0);
		if (ch >= 128 && cu < 128) {
			return ch;
		}
		return cu;
	}

	// True iff no OTHER codepoint Canonicalizes (see annexBCanonicalize()
	// above) to the same value ch does - i.e. ch's own equivalence class
	// under the real non-unicode Canonicalize algorithm is the SINGLETON
	// {ch}, meaning ignoreCase should behave identically to an ordinary
	// case-sensitive match for this one character. Checks ch's own
	// lowercase AND uppercase forms - the only realistic "other member"
	// candidates, since sharing a Canonicalize target is overwhelmingly a
	// simple 2-element case pair (e.g. 'k'/'K' both -> 'K'). For most
	// ordinary letters, one of those two checks finds the real partner
	// (returns false). U+212A KELVIN SIGN is the motivating counter-
	// example: its own lowercase form IS 'k' (an ordinary Unicode
	// case mapping), but Canonicalize('k') = 'K' (U+004B) - NOT back to
	// U+212A - so U+212A has no real Annex-B fold partner at all, even
	// though a naive "does ch have a differently-cased form" check would
	// wrongly say it does.
	private static boolean hasNoAnnexBFoldPartner(int ch) {
		int cf = annexBCanonicalize(ch);
		int lower = Character.toLowerCase(ch);
		if (lower != ch && annexBCanonicalize(lower) == cf) {
			return false;
		}
		int upper = Character.toUpperCase(ch);
		if (upper != ch && annexBCanonicalize(upper) == cf) {
			return false;
		}
		return true;
	}

	private int translateUnicodeEscape(String source, int i, StringBuilder result, boolean unicodeMode, boolean inGroupName, boolean inCC, boolean ignoreCase) {
		// i points to 'u' (already consumed backslash)
		// A GroupSpecifier's RegExpIdentifierName is always parsed using the
		// full RegExpUnicodeEscapeSequence grammar (braced form included),
		// REGARDLESS of whether the regexp itself has a "u"/"v" flag -
		// unlike an ordinary pattern-body "\\u{...}" escape, which is only
		// valid unicode-mode syntax (confirmed via
		// named-groups/non-unicode-property-names-valid.js: a braced escape
		// inside a group name must decode even with no "u" flag at all).
		boolean bracedFormAllowed = unicodeMode || inGroupName;
		if (bracedFormAllowed && i + 1 < source.length() && source.charAt(i + 1) == '{') {
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
				if (inGroupName) {
					// Joni's own group-NAME parser does NOT interpret a
					// "\\uHHHH" escape the way it does inside the pattern
					// BODY - it takes it as 6 literal characters ('\','u',
					// hex digits...), not one decoded character. A GroupSpecifier
					// name has already been validated as containing only
					// real IdentifierStart/IdentifierPart characters (see
					// validateGroupName()), so it can never coincide with a
					// regex metacharacter needing escaping - emitting the
					// actual decoded character directly is always safe here
					// (confirmed via named-groups/unicode-property-names.js:
					// "(?<\\u{03C0}>a)" must produce a group literally named
					// "π", not the 6-character text "\\u03c0").
					result.appendCodePoint(codePoint);
				} else {
					appendCaseAwareUnicodeEscape(result, codePoint, unicodeMode, inCC, ignoreCase);
				}
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
				if ((unicodeMode || inGroupName) && Character.isHighSurrogate((char) val)
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
				if (inGroupName) {
					// Same rationale as the braced "\\u{...}" case above.
					result.appendCodePoint(val);
				} else {
					appendCaseAwareUnicodeEscape(result, val, unicodeMode, inCC, ignoreCase);
				}
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
	private static final class ModifierGroupFlags {
		final boolean ignoreCase, multiline, dotAll;
		ModifierGroupFlags(boolean ignoreCase, boolean multiline, boolean dotAll) {
			this.ignoreCase = ignoreCase;
			this.multiline = multiline;
			this.dotAll = dotAll;
		}
	}

	// Joni's own Lexer rejects any {n}/{n,m} quantifier bound above this
	// value ("too big number for repeat range" - see org.joni.Config
	// .MAX_REPEAT_NUM, default 100000) - but per spec, an arbitrarily large
	// DecimalDigits (JS allows up to 2**53-1, e.g. Number.MAX_SAFE_INTEGER)
	// is valid QUANTIFIER SYNTAX regardless of magnitude; it just describes
	// a repeat count no real string could ever satisfy. Clamping down to
	// Joni's own max preserves identical externally-observable behavior for
	// every input actually reachable in practice (no string is long enough
	// to tell 100000 apart from 2**53-1 repeats of a fixed-width atom) while
	// avoiding the internal error entirely (confirmed via
	// quantifier-integer-limit.js: `new RegExp("b{" + Number.MAX_SAFE_INTEGER
	// + "}", "u")` must compile, not throw).
	private static final long MAX_REPEAT_NUM = 100000;

	// Tries to parse a Quantifier's "{...}" bound starting at `openBrace`
	// (the '{' itself) as one of "{N}"/"{N,}"/"{N,M}" (DecimalDigits only,
	// per the QuantifierPrefix grammar) - on success, appends the same
	// shape to `result` with any bound clamped to MAX_REPEAT_NUM, and
	// returns the number of source characters consumed (including both
	// braces). Returns 0 (appending nothing) for anything else - a
	// non-quantifier "{" (either malformed, per Annex B treated as a
	// literal PatternCharacter, or simply not this production at all) is
	// left for the caller's own default passthrough.
	private static int tryClampQuantifier(String source, int openBrace, StringBuilder result) {
		int i = openBrace + 1;
		int start1 = i;
		while (i < source.length() && Character.isDigit(source.charAt(i))) i++;
		if (i == start1) return 0; // no leading DecimalDigits at all
		int end1 = i;
		if (i < source.length() && source.charAt(i) == '}') {
			appendClampedDecimal(result.append('{'), source, start1, end1).append('}');
			return i + 1 - openBrace;
		}
		if (i >= source.length() || source.charAt(i) != ',') return 0;
		i++;
		int start2 = i;
		while (i < source.length() && Character.isDigit(source.charAt(i))) i++;
		int end2 = i;
		if (i >= source.length() || source.charAt(i) != '}') return 0;
		appendClampedDecimal(result.append('{'), source, start1, end1).append(',');
		if (end2 > start2) {
			appendClampedDecimal(result, source, start2, end2);
		}
		result.append('}');
		return i + 1 - openBrace;
	}

	private static StringBuilder appendClampedDecimal(StringBuilder sb, String source, int start, int end) {
		// A DecimalDigits run this long is already >= 10**18, far past
		// MAX_REPEAT_NUM - parsing it as a long could still overflow for a
		// sufficiently pathological input, so the digit COUNT alone is
		// enough to decide to clamp without ever parsing such a run.
		if (end - start > 18) {
			return sb.append(MAX_REPEAT_NUM);
		}
		long value = Long.parseLong(source.substring(start, end));
		return sb.append(Math.min(value, MAX_REPEAT_NUM));
	}

	private static ModifierGroupFlags parseModifierGroup(String source, int openParen,
			boolean currentIgnoreCase, boolean currentMultiline, boolean currentDotAll) {
		int i = openParen + 1;
		if (i >= source.length() || source.charAt(i) != '?') return null;
		i++;
		if (i >= source.length()) return null;
		char first = source.charAt(i);
		// Not an attempt at Modifiers syntax at all - one of the other
		// established "(?" continuations (lookahead/neg-lookahead/
		// lookbehind/named-group, or the ordinary non-capturing "(?:"),
		// which the caller/main loop already handles on its own.
		if (first == '=' || first == '!' || first == '<' || first == ':') return null;

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
		// Past this point, nothing else in the JS regex grammar could have
		// produced this exact prefix (a leading character that's neither
		// "=","!","<",":" nor a valid "ims"/"-" run) - every remaining
		// problem is a genuine early SyntaxError, per the Modifiers
		// production's own static semantics, not a silent fall-through to
		// "ordinary capturing group".
		if (i >= source.length() || source.charAt(i) != ':') {
			throw RuntimeUtil.syntaxError("Invalid regular expression modifiers in '{0}'", source);
		}
		if (addStart == addEnd && (!hasDash || removeStart == removeEnd)) {
			throw RuntimeUtil.syntaxError("Regular expression modifiers must not both be empty in '{0}'", source);
		}
		validateModifierLetters(source, addStart, addEnd);
		if (hasDash) {
			validateModifierLetters(source, removeStart, removeEnd);
			for (int k = addStart; k < addEnd; k++) {
				char m = source.charAt(k);
				for (int j = removeStart; j < removeEnd; j++) {
					if (source.charAt(j) == m) {
						throw RuntimeUtil.syntaxError(
								"Regular expression modifier '{0}' cannot be both added and removed in '{1}'",
								String.valueOf(m), source);
					}
				}
			}
		}
		boolean ignoreCase = currentIgnoreCase, multiline = currentMultiline, dotAll = currentDotAll;
		for (int k = addStart; k < addEnd; k++) {
			char m = source.charAt(k);
			if (m == 'i') ignoreCase = true;
			else if (m == 'm') multiline = true;
			else if (m == 's') dotAll = true;
		}
		for (int k = removeStart; k < removeEnd; k++) {
			char m = source.charAt(k);
			if (m == 'i') ignoreCase = false;
			else if (m == 'm') multiline = false;
			else if (m == 's') dotAll = false;
		}
		return new ModifierGroupFlags(ignoreCase, multiline, dotAll);
	}

	// It is a SyntaxError for a single RegularExpressionFlags run (either
	// side of the optional "-") to contain any code point other than "i",
	// "m", or "s", or the same one more than once.
	private static void validateModifierLetters(String source, int start, int end) {
		boolean seenI = false, seenM = false, seenS = false;
		for (int k = start; k < end; k++) {
			char m = source.charAt(k);
			if (m == 'i') {
				if (seenI) throw RuntimeUtil.syntaxError("Duplicate regular expression modifier 'i' in '{0}'", source);
				seenI = true;
			} else if (m == 'm') {
				if (seenM) throw RuntimeUtil.syntaxError("Duplicate regular expression modifier 'm' in '{0}'", source);
				seenM = true;
			} else if (m == 's') {
				if (seenS) throw RuntimeUtil.syntaxError("Duplicate regular expression modifier 's' in '{0}'", source);
				seenS = true;
			}
		}
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
	static int[] wordCharRanges(boolean ignoreCaseActive) {
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

	// CharacterClassEscape :: d, per spec: exactly ASCII 0-9 - NOT Joni's own
	// Unicode Nd (decimal digit) category, which also includes e.g. Arabic-
	// Indic, Devanagari, and fullwidth digits. Same ASCII-only range
	// VClassParser's own DIGIT_RANGES uses for "v" mode.
	private static final int[] DIGIT_RANGES = { '0', '9' };

	private static String buildDigitClass(boolean negated, boolean insideCharClass, boolean unicodeMode) {
		int[] ranges = DIGIT_RANGES;
		if (insideCharClass && negated) {
			// \\D's complement spans nearly the entire codepoint space - same
			// BMP-boundary-escaping/astral-capping rationale as \\S's
			// negated-nested case in buildWhiteSpaceClass() above.
			ranges = complementRanges(ranges);
			if (!unicodeMode) {
				ranges = capRangesAtBmp(ranges);
			}
			negated = false;
		}
		StringBuilder sb = new StringBuilder();
		sb.append(negated ? "[^" : "[");
		for (int i = 0; i < ranges.length; i += 2) {
			appendWhiteSpaceRangeMember(sb, ranges[i], ranges[i + 1]);
		}
		sb.append(']');
		if (insideCharClass) {
			return sb.substring(1, sb.length() - 1);
		}
		return sb.toString();
	}

	// JS's WhiteSpace + LineTerminator productions (the exact set CharacterClassEscape
	// :: s matches), as codepoint ranges - TAB/VT/FF/SP/NBSP/ZWNBSP plus every
	// other WhiteSpace, and LF/CR/LS/PS as LineTerminator. Joni's own native
	// \\s is Unicode-"space-ish"-category-based (includes e.g. U+0085 NEXT
	// LINE, a control character JS's own \\s must NOT match), so - same
	// rationale as wordCharRanges()/buildWordCharClass() above - it's
	// replaced entirely rather than patched.
	static final int[] WHITESPACE_RANGES = {
		0x0009, 0x000D, // TAB,LF,VT,FF,CR
		0x0020, 0x0020, // SP
		0x00A0, 0x00A0, // NBSP
		0x1680, 0x1680,
		0x2000, 0x200A,
		0x2028, 0x2029, // LS,PS
		0x202F, 0x202F,
		0x205F, 0x205F,
		0x3000, 0x3000,
		0xFEFF, 0xFEFF, // ZWNBSP
	};

	private static String buildWhiteSpaceClass(boolean negated, boolean insideCharClass, boolean unicodeMode) {
		int[] ranges = WHITESPACE_RANGES;
		if (insideCharClass && negated) {
			// \\S's complement spans nearly the entire codepoint space, so
			// (unlike the small \\w-class ranges) a raw-literal BMP range
			// boundary here can land right next to another one with no
			// escape-derived separator at all between them - confirmed via
			// character-class-escape-non-whitespace.js/S15.10.2.8_A3_T19.js:
			// Joni's own char-class parser misreads such a pair as "empty
			// range in char class" for at least one BMP boundary pairing
			// (0xFEFE immediately followed by 0xFF00). Emitting each BMP
			// endpoint as an explicit "\\uHHHH" escape (exactly how an
			// ordinary \\u{...} pattern escape already emits a BMP
			// codepoint - see appendUnicodeEscape()) sidesteps this
			// entirely, since escape sequences have unambiguous
			// boundaries; only a genuinely astral (>0xFFFF) endpoint still
			// needs the raw-literal-pair form (same surrogate-pair-as-
			// range-boundary rationale as \\u{...} uses).
			//
			// In NON-unicode mode, Joni compiles under
			// LenientUTF16BECodeUnitEncoding (code UNITS, surrogate halves
			// NOT combined into one atom - see the constructor's own
			// isUnicode()-gated choice of encoding) - a raw-literal astral
			// range boundary would then be read as two independent BMP
			// "characters" (its own high/low surrogate halves), splitting
			// what's meant to be one range into a bogus, often-REVERSED
			// sub-range (confirmed: a non-unicode /[\\S]/ crashed with
			// "empty range in char class" this exact way). Capping the
			// complement at 0xFFFF sidesteps the astral case entirely,
			// which also happens to match actual sloppy-mode semantics -
			// non-unicode regexes already operate per CODE UNIT, not per
			// codepoint (e.g. "." only ever matches one code unit there),
			// so \\S has no business claiming to match "characters" beyond
			// the BMP in that mode anyway.
			ranges = complementRanges(ranges);
			if (!unicodeMode) {
				ranges = capRangesAtBmp(ranges);
			}
			negated = false;
		}
		StringBuilder sb = new StringBuilder();
		sb.append(negated ? "[^" : "[");
		for (int i = 0; i < ranges.length; i += 2) {
			appendWhiteSpaceRangeMember(sb, ranges[i], ranges[i + 1]);
		}
		sb.append(']');
		if (insideCharClass) {
			return sb.substring(1, sb.length() - 1);
		}
		return sb.toString();
	}

	private static void appendWhiteSpaceRangeMember(StringBuilder sb, int start, int end) {
		if (end <= 0xFFFF) {
			appendUnicodeEscape(sb, start);
			if (end != start) {
				sb.append('-');
				appendUnicodeEscape(sb, end);
			}
			return;
		}
		if (start <= 0xFFFF) {
			appendWhiteSpaceRangeMember(sb, start, 0xFFFF);
			appendWhiteSpaceRangeMember(sb, 0x10000, end);
			return;
		}
		appendRangeSplitAtSurrogateBoundary(sb, start, end);
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

	// SyntaxCharacter, per the grammar - the only characters (besides "/")
	// a unicode-mode IdentityEscape may legally target.
	private static boolean isRegexSyntaxCharacter(char c) {
		switch (c) {
			case '^': case '$': case '\\': case '.': case '*': case '+':
			case '?': case '(': case ')': case '[': case ']': case '{':
			case '}': case '|':
				return true;
			default:
				return false;
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

	// The letter of a CharacterClassEscape (\d \D \w \W \s \S) - used by the
	// '-' case in translatePattern() to decide whether a following escape is
	// a whole CharSet (never "exactly one character") for Annex B.1.4's
	// CharacterRangeOrUnion, as opposed to any OTHER escape (\n, \t, \cX, ...)
	// which always denotes a single character and so still participates in an
	// ordinary range.
	private static boolean isClassEscapeShorthandLetter(char c) {
		switch (c) {
			case 'd': case 'D': case 'w': case 'W': case 's': case 'S':
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
	// combines surrogate pairs) never pay for it. Amortized once across every
	// iteration of a global/exec loop against the same String instance.
	// PRECONDITION: caller must have just called getBytes(str) for the same
	// String identity, so lastString == str (keeps lastBytes/lastChars paired).
	private char[] getChars(String str) {
		if (lastChars == null) lastChars = str.toCharArray();
		return lastChars;
	}

	// Matcher over the main regex for `str`, whose bytes come from
	// getBytes(str). Under the fixed-width code-unit encoding (no u/v flag),
	// the String and a parallel char[] are passed as sidecars: hot opcodes
	// read chars[s >> 1] directly and exact-string searches go through
	// String.indexOf (a HotSpot intrinsic) - see Regex.matcher(byte[],
	// char[], String, int, int).
	private Matcher newMatcher(String str, byte[] bytes) {
		return joniEncoding.isFixedWidth2()
				? regex.matcher(bytes, getChars(str), str, 0, bytes.length)
				: regex.matcher(bytes, 0, bytes.length);
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

	// Reports a successful match to the legacy static properties (RegExp.$1,
	// lastMatch, ...) - see RegExp.updateLegacyStaticProperties().
	private void recordLegacyMatch(String str, Region region) {
		int n = region.getNumRegs();
		int[] spans = new int[2 * n];
		for (int k = 0; k < n; k++) {
			int beg = region.getBeg(k);
			spans[2 * k] = beg < 0 ? -1 : beg / 2;
			spans[2 * k + 1] = beg < 0 ? -1 : region.getEnd(k) / 2;
		}
		regExp.updateLegacyStaticProperties(str, spans);
	}

	private boolean execInternal(String str) {
		return execInternal(str, true);
	}

	// needRegion false (test() only): on success, skip building the Region
	// (getEagerRegion() allocates one when the pattern has no capture group)
	// and leave lastRegion stale - only the exec()/replace()/... callers that
	// just ran execInternal(str) read it. lastMatchStart/lastMatchEnd and
	// lastIndex are still updated from the matcher's overall bounds.
	private boolean execInternal(String str, boolean needRegion) {
		boolean global = regExp.isGlobal();
		boolean sticky = regExp.isSticky();
		int strLen = str.length();

		// Per RegExpBuiltinExec, step 4's "lastIndex = ToLength(Get(R,
		// "lastIndex"))" is UNCONDITIONAL - even when neither global nor
		// sticky is set (where the read value is immediately discarded by
		// step 6, "If global is false and sticky is false, set lastIndex to
		// 0"). A poisoned lastIndex (e.g. `{valueOf(){...}}`) must still
		// have its valueOf() invoked exactly once even though the numeric
		// result goes unused - confirmed via exec/success-lastindex-access.js
		// and exec/failure-lastindex-access.js, which count Get/ToNumber
		// invocations and require exactly 1 regardless of global/sticky.
		int rawLastIndex = regExp.getLastIndex();
		int charIndex = 0;
		if (global || sticky) {
			charIndex = rawLastIndex;
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

		Matcher matcher = newMatcher(str, bytes);
		int result;
		if (sticky) {
			result = matcher.match(byteStart, bytes.length, Option.NONE);
		} else {
			result = matcher.search(byteStart, bytes.length, Option.NONE);
		}

		if (result >= 0) {
			if (needRegion) {
				Region region = matcher.getEagerRegion();
				lastRegion = region;
				lastMatchStart = byteIndexToCharIndex(str, bytes, region.getBeg(0));
				lastMatchEnd = byteIndexToCharIndex(str, bytes, region.getEnd(0));
				recordLegacyMatch(str, region);
			} else {
				lastMatchStart = byteIndexToCharIndex(str, bytes, matcher.getBegin());
				lastMatchEnd = byteIndexToCharIndex(str, bytes, matcher.getEnd());
				// A pattern without capture groups has no Region at all
				Region region = matcher.getRegion();
				if (region != null) {
					recordLegacyMatch(str, region);
				} else {
					regExp.updateLegacyStaticProperties(str, new int[]{lastMatchStart, lastMatchEnd});
				}
			}

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
			// Region.newRegion() backs onto a plain `int[]`, which the JVM
			// zero-initializes - but Joni's own convention (relied on by
			// getGroupValue() etc. throughout this class) is that an
			// UNPARTICIPATED group's beg/end is REGION_NOTPOS (-1), not 0.
			// Left at the array default, a group this custom-lookbehind
			// engine never wrote into (e.g. one inside a NEGATIVE lookbehind,
			// which never records captures, or an untaken alternative branch)
			// reads back as beg=0/end=0 - an erroneous EMPTY-STRING capture -
			// instead of undefined. Every other region-producing path in this
			// class gets this for free from Joni's own match(); this is the
			// only place that builds a Region by hand, so it's the only place
			// that needs the explicit reset (confirmed via
			// lookBehind/captures-negative.js: `"abcdef".match(/(?<!(^|[ab]))\w{2}/)`
			// group 1 must be undefined, not "").
			for (int g = 1; g <= customLookbehind.totalGroupCount; g++) {
				region.setBeg(g, -1);
				region.setEnd(g, -1);
			}
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
			recordLegacyMatch(str, region);
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
		if (topLevelAlternatives != null) {
			return tryTopLevelAlternativesAt(str, s);
		}
		return tryCustomMatchAt(customLookbehind, str, s);
	}

	// Parameterized on `cl` instead of implicitly reading the instance field
	// `customLookbehind` - needed so tryLookbehindAndSuffixAt() below can
	// recurse into a NESTED CustomLookbehind (cl.suffixCustomLookbehind, set
	// when SUFFIX itself contains another top-level lookbehind Joni can't
	// compile natively - see trySetUpCustomLookbehind()'s own doc). The
	// top-level, non-recursive entry points (tryCustomMatchAt(String,int)
	// above, execInternalCustomLookbehind()) are unchanged - they still
	// always operate on `customLookbehind`, via the single-arg overload.
	private CustomMatchResult tryCustomMatchAt(CustomLookbehind cl, String str, int s) {
		int strLen = str.length();
		if (cl.genericPrefixRegex != null) {
			return tryGenericPrefixMatchAt(cl, str, s);
		}
		if (!cl.prefixDotStar) {
			// No prefix at all (empty) - no prefix capturing groups exist to
			// bridge, so cl.bodyExternalGroupRefs can never be set here (see
			// trySetUpCustomLookbehind()'s own bail-out).
			return tryLookbehindAndSuffixAt(cl, str, s, s, null);
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
			// A bare ".*" prefix has no capturing groups either - same as
			// the empty-prefix case above.
			CustomMatchResult r = tryLookbehindAndSuffixAt(cl, str, s, p, null);
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
	private CustomMatchResult tryGenericPrefixMatchAt(CustomLookbehind cl, String str, int s) {
		int strLen = str.length();
		byte[] bytes = getBytes(str);
		int startByte = charIndexToByteIndex(str, bytes, s);
		for (int candidateEnd = strLen; candidateEnd >= s; candidateEnd--) {
			int candidateEndByte = charIndexToByteIndex(str, bytes, candidateEnd);
			Matcher m = cl.genericPrefixRegex.matcher(bytes, 0, bytes.length);
			// Joni's own match() refuses to even ATTEMPT a match when given
			// a zero-length window (candidateEndByte==startByte) - always
			// returning failure, even for a genuinely zero-width prefix
			// (e.g. a bare lookahead assertion, "(?=(\w))") that would
			// never need to consume any of that window anyway. Give it the
			// full remaining buffer as the range instead in that one case -
			// the match's own natural end (checked right below, exactly
			// like every other candidateEnd) still correctly constrains
			// the result to genuinely zero-width, so this doesn't let a
			// CONSUMING prefix match too much; it only unblocks the
			// zero-width case Joni's own empty-range refusal was hiding.
			int matchRangeByte = candidateEndByte == startByte ? bytes.length : candidateEndByte;
			int r = m.match(startByte, matchRangeByte, Option.NONE);
			if (r < 0) continue;
			Region region = m.getEagerRegion();
			if (byteIndexToCharIndex(str, bytes, region.getEnd(0)) != candidateEnd) continue;
			CustomMatchResult res = tryLookbehindAndSuffixAt(cl, str, s, candidateEnd, region);
			if (res != null) return res;
		}
		return null;
	}

	// Checks the lookbehind assertion holds at char position p (the
	// position right after any ".*" prefix), then - only if it does - that
	// SUFFIX matches starting exactly at p. On success, returns a result
	// spanning [overallStart, suffix's match end) with body's and suffix's
	// captures both present, correctly numbered to the ORIGINAL pattern
	// (via cl.suffixGroupOffset).
	private CustomMatchResult tryLookbehindAndSuffixAt(CustomLookbehind cl, String str, int overallStart, int p, Region prefixRegion) {
		LookbehindCheckResult lb = checkLookbehind(cl, str, p, prefixRegion);
		if (lb == null) return null;

		byte[] bytes = getBytes(str);
		int byteP = charIndexToByteIndex(str, bytes, p);

		if (cl.suffixCustomLookbehind != null) {
			// SUFFIX is itself a nested custom-lookbehind pattern (see
			// trySetUpCustomLookbehind()'s own doc and
			// CustomLookbehind.suffixCustomLookbehind's field comment) -
			// match it starting exactly at p, the same way the top-level
			// entry point treats its own search position when there's no
			// ".*"/generic prefix (tryCustomMatchAt() above). The nested
			// structure's own group numbers are relative to ITSELF -
			// remap by cl.suffixGroupOffset before merging into this
			// level's result, exactly like the plain-suffixRegex case
			// below remaps its own local group numbers.
			CustomMatchResult nestedResult = tryCustomMatchAt(cl.suffixCustomLookbehind, str, p);
			if (nestedResult == null) return null;

			CustomMatchResult result = new CustomMatchResult();
			result.overallStart = overallStart;
			result.overallEnd = nestedResult.overallEnd;
			mergePrefixGroups(result.groupSpans, prefixRegion, str);
			if (!cl.negative) {
				result.groupSpans.putAll(lb.groupSpans);
			}
			for (Map.Entry<Integer, int[]> e : nestedResult.groupSpans.entrySet()) {
				result.groupSpans.put(e.getKey() + cl.suffixGroupOffset, e.getValue());
			}
			return result;
		}

		Regex suffixRegex = cl.suffixRegex;
		Region suffixRegion;
		if (suffixRegex == null) {
			// Cross-boundary backreference bridging: SUFFIX's own source
			// references a group defined inside BODY (or, now that
			// cl.suffixGroupOffset accounts for prefixGroupOffset too,
			// possibly PREFIX), which Joni can't resolve across separately-
			// compiled Regex objects - so substitute the already-known
			// captured text as a literal (escaped) run and compile+match
			// that per attempt instead. bridgeSource merges PREFIX's own
			// captures (already matched by this point) on top of body's -
			// bridgeBackreferences() itself just does a flat group-number
			// lookup, so it resolves either origin transparently.
			LookbehindCheckResult bridgeSource = lb;
			if (prefixRegion != null) {
				bridgeSource = new LookbehindCheckResult();
				bridgeSource.groupSpans.putAll(lb.groupSpans);
				mergePrefixGroups(bridgeSource.groupSpans, prefixRegion, str);
			}
			String bridged = bridgeBackreferences(cl.suffixSourceForBridging, bridgeSource, str);
			byte[] suffixBytes = toUtf16BEBytes(bridged);
			try {
				suffixRegex = new Regex(suffixBytes, 0, suffixBytes.length, cl.compileOptions,
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
		mergePrefixGroups(result.groupSpans, prefixRegion, str);
		if (!cl.negative) {
			result.groupSpans.putAll(lb.groupSpans);
		}
		for (int localGroup = 1; localGroup < suffixRegion.getNumRegs(); localGroup++) {
			int beg = suffixRegion.getBeg(localGroup);
			int end = suffixRegion.getEnd(localGroup);
			if (beg < 0) continue;
			int origGroup = localGroup + cl.suffixGroupOffset;
			result.groupSpans.put(origGroup, new int[]{
					byteIndexToCharIndex(str, bytes, beg), byteIndexToCharIndex(str, bytes, end)});
		}
		return result;
	}

	// Merges a genericPrefixRegex match's OWN capturing groups into a
	// CustomMatchResult's groupSpans - PREFIX's own group numbers coincide
	// exactly with the ORIGINAL pattern's numbering (prefix groups are
	// always numbered first, before body's/suffix's), so no offset is
	// needed, unlike body's/suffix's own remapping elsewhere in this class.
	// Unconditional (not gated on cl.negative) - PREFIX sits OUTSIDE the
	// lookbehind assertion entirely, so its own captures always apply
	// regardless of the lookbehind's own polarity or match outcome. Was
	// previously never called at all - genericPrefixRegex's own capturing
	// groups (if the prefix pattern text has any) were silently dropped
	// from every match result; unreachable before this session's own
	// prefix-references-body backreference fix (see
	// CustomLookbehind.bodyExternalGroupRefs), since no prior test
	// exercised a genericPrefixRegex prefix that itself had a capturing
	// group whose VALUE was checked.
	private void mergePrefixGroups(Map<Integer, int[]> target, Region prefixRegion, String str) {
		if (prefixRegion == null) return;
		byte[] bytes = getBytes(str);
		for (int g = 1; g < prefixRegion.getNumRegs(); g++) {
			int beg = prefixRegion.getBeg(g);
			int end = prefixRegion.getEnd(g);
			if (beg < 0) continue;
			target.put(g, new int[]{byteIndexToCharIndex(str, bytes, beg), byteIndexToCharIndex(str, bytes, end)});
		}
	}

	// The lookbehind's own captures (original group number -> [charBeg,
	// charEnd) in `str`), plus whether the assertion held at all.
	private static final class LookbehindCheckResult {
		Map<Integer, int[]> groupSpans = new java.util.HashMap<>();
	}

	private LookbehindCheckResult checkLookbehind(CustomLookbehind cl, String str, int p, Region prefixRegion) {
		LBNode bodyNode = cl.bodyNode;
		Map<Integer, String> bodyGroupSyntheticNames = cl.bodyGroupSyntheticNames;
		if (cl.bodyExternalGroupRefs != null) {
			// body itself references a group captured by the PREFIX (which
			// has already matched by this point) - rebuild body fresh for
			// THIS attempt, substituting each such reference with the
			// prefix's actual captured text as a literal, then re-reverse
			// and re-compile (mirrors bridgeBackreferences()'s existing
			// suffix-references-body handling, opposite direction).
			String bridgedBody = bridgeExternalBodyBackreferences(cl, str, prefixRegion);
			LookbehindReversal freshRev;
			LBNode freshNode;
			try {
				freshRev = LookbehindReversal.reverse(bridgedBody, cl.prefixGroupOffset);
				freshNode = compileLBFragment(freshRev.reversedText, cl.compileOptions,
						new java.util.HashSet<>(freshRev.groupNumberToSyntheticName.values()));
			} catch (RuntimeException e) {
				return null;
			}
			if (freshNode == null) return null;
			bodyNode = freshNode;
			bodyGroupSyntheticNames = freshRev.groupNumberToSyntheticName;
		}

		LBBuffers bufs = new LBBuffers(str);
		LBTransform t = new LBTransform(-1, str.length());
		int anchor = t.anchorFor(p);
		Map<String, Integer> syntheticToOriginal = new java.util.HashMap<>();
		for (Map.Entry<Integer, String> e : bodyGroupSyntheticNames.entrySet()) {
			syntheticToOriginal.put(e.getValue(), e.getKey());
		}
		LBMatchResult m = matchLBNode(bodyNode, bufs, anchor, t, syntheticToOriginal);
		boolean matched = m != null;

		if (cl.negative) {
			return matched ? null : new LookbehindCheckResult();
		}
		if (!matched) return null;

		LookbehindCheckResult lb = new LookbehindCheckResult();
		lb.groupSpans.putAll(m.groupSpans);
		return lb;
	}

	// Substitutes each of cl.bodyExternalGroupRefs' bare "\N" occurrences in
	// cl.bodySourceForBridging with the PREFIX's actual captured text for
	// group N (escaped as literal \\uHHHH runs, matching nothing if that
	// group didn't participate - same convention as bridgeBackreferences()).
	// prefixRegion is null only when there's genuinely no way to reach here
	// with a non-empty bodyExternalGroupRefs (see trySetUpCustomLookbehind's
	// bail-out when genericPrefixRegex is null) - guarded defensively anyway.
	private String bridgeExternalBodyBackreferences(CustomLookbehind cl, String str, Region prefixRegion) {
		String bodyText = cl.bodySourceForBridging;
		StringBuilder out = new StringBuilder(bodyText.length());
		for (int i = 0; i < bodyText.length(); i++) {
			char c = bodyText.charAt(i);
			if (c == '\\' && i + 1 < bodyText.length()
					&& bodyText.charAt(i + 1) >= '1' && bodyText.charAt(i + 1) <= '9'
					&& cl.bodyExternalGroupRefs.contains(bodyText.charAt(i + 1) - '0')) {
				int n = bodyText.charAt(i + 1) - '0';
				if (prefixRegion != null && n < prefixRegion.getNumRegs()) {
					int beg = prefixRegion.getBeg(n);
					int end = prefixRegion.getEnd(n);
					if (beg >= 0) {
						byte[] bytes = getBytes(str);
						int charBeg = byteIndexToCharIndex(str, bytes, beg);
						int charEnd = byteIndexToCharIndex(str, bytes, end);
						for (int k = charBeg; k < charEnd; k++) {
							appendUnicodeEscape(out, str.charAt(k));
						}
					}
					// else: group didn't participate - matches empty string,
					// append nothing, per spec.
				}
				i++;
			} else {
				out.append(c);
			}
		}
		return out.toString();
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
		return fromUtf16BEBytes(bytes, beg, end - beg);
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
		// execInternal() already applies RegExpBuiltinExec's own conditional
		// lastIndex reset on failure (only when global||sticky - see its own
		// comment) - an unconditional reset here would incorrectly clobber
		// lastIndex for a non-global, non-sticky regexp too (confirmed via
		// exec/failure-lastindex-access.js: a failed match with neither flag
		// set must leave lastIndex completely untouched, not overwrite it
		// with a converted 0).
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
		JSObject groupsObj = JSObject.createWithPrototype(env, null);
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
		JSObject groupsObj = JSObject.createWithPrototype(env, null);
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
		return execInternal(str, false);
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
		Matcher matcher = newMatcher(str, bytes);

		// Spec's own algorithm (21.2.5.11) tracks TWO distinct positions - p
		// (the start of the next chunk to emit, i.e. the last real split
		// point) and q (the sticky-splitter search cursor) - and only
		// records a split when the match's end (e) differs from p; a match
		// found exactly where the last split point already is (e==p, the
		// case for a pattern that matches empty at every position, e.g.
		// /(?:)/) is a genuine no-op that must advance q WITHOUT touching p.
		// A single conflated position variable (this method's own previous
		// implementation) can't represent "search moved past a no-op empty
		// match but no split recorded yet" and silently dropped the
		// characters between such no-op positions (confirmed via
		// Symbol.split/str-adv-thru-empty-match.js: `/(?:)/.split('abc')`
		// must produce ["a","b","c"], not four empty strings). `matchStart`
		// (not `q`) is used for the emitted "before" text since a forward
		// `matcher.search` can jump straight to a genuine match without
		// walking every intermediate position one at a time - equivalent to
		// the spec's own sticky-per-position walk (which would have kept
		// returning null and incrementing q by ones until landing on the
		// exact same matchStart anyway), just without the redundant work.
		int p = 0; // byte position of the last real split point
		int q = 0; // byte position of the search cursor
		int resultCount = 0;

		// Spec's outer loop guards on "q < size" (step 18), NOT on the
		// match result - once q reaches the end of the string, the loop
		// exits unconditionally and the trailing remainder is appended
		// below, WITHOUT ever attempting a match at position q==size.
		// Attempting a search there anyway (this method's own earlier
		// draft) can find a spurious zero-width match at end-of-string
		// (e.g. /\s*(?:;|$)\s*/ matching "" at the very end) that reads
		// as a no-op (matchEnd==p) with nowhere left to advance q to,
		// which incorrectly discarded the trailing chunk instead of
		// falling through to append it (confirmed via
		// SplitTest.js:41's `/\s*(?:;|$)\s*/` case - the final "" element
		// went missing until this guard was added).
		while ((limit <= 0 || resultCount < limit) && q < bytes.length) {
			int searchResult = matcher.search(q, bytes.length, Option.NONE);
			if (searchResult < 0) break;

			Region region = matcher.getEagerRegion();
			int matchStart = region.getBeg(0);
			int matchEnd = region.getEnd(0);

			// Per spec, the sticky per-position test is only ever performed
			// for q in [0, size) - position `size` itself is NEVER tested
			// (the outer "while q < size" guard above stops the loop first).
			// matcher.search() jumps straight to the next real match instead
			// of walking one position at a time, so it can return a
			// zero-width match landing exactly AT the end of the string
			// (e.g. /$/ matching the empty string at position `size`) even
			// though no spec-conformant q value would ever reach it. Treat
			// that exactly like "no match found" (confirmed via
			// Symbol.split/separator-regexp.js: "x".split(/$/) must stay
			// ["x"], not ["x", ""]).
			if (matchStart >= bytes.length) break;
			recordLegacyMatch(str, region);

			if (matchEnd == p) {
				// No progress since the last split point - a no-op match,
				// not a real split. Advance the search cursor by one JS
				// char unit (always exactly 2 bytes in UTF-16BE) and retry.
				q += 2;
				continue;
			}

			// Add substring before match
			String before = fromUtf16BEBytes(bytes, p, matchStart - p);
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

			p = matchEnd;
			q = p;
		}

		if ((limit <= 0 || resultCount < limit) && p <= bytes.length) {
			result.arrayAdd(fromUtf16BEBytes(bytes, p, bytes.length - p));
		}

		return result;
	}

	@Override
	public JSArray match(JSRuntimeContext context, String str) {
		if (!regExp.isGlobal()) {
			return exec(context, str);
		}

		byte[] bytes = getBytes(str);
		Matcher matcher = newMatcher(str, bytes);
		JSArray a = JSArray.create(env);

		int pos = 0;
		while (true) {
			int searchResult = matcher.search(pos, bytes.length, Option.NONE);
			if (searchResult < 0) break;

			Region region = matcher.getEagerRegion();
			int matchStart = region.getBeg(0);
			int matchEnd = region.getEnd(0);
			recordLegacyMatch(str, region);

			String matched = fromUtf16BEBytes(bytes, matchStart, matchEnd - matchStart);
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
		Matcher matcher = newMatcher(str, bytes);
		// Per spec, Symbol.search resets lastIndex to 0 then calls the
		// GENERIC RegExpExec, which honors the regexp's own sticky ("y")
		// flag exactly as exec() does - a sticky regex must only match
		// anchored at position 0, not scan forward for the first match
		// anywhere (confirmed via Symbol.search/y-fail-return.js:
		// /a/y[Symbol.search]('ba') must be -1, not 1 - "ba" has no 'a' at
		// position 0, and sticky forbids scanning ahead to position 1).
		int searchResult = regExp.isSticky()
				? matcher.match(0, bytes.length, Option.NONE)
				: matcher.search(0, bytes.length, Option.NONE);
		if (searchResult >= 0) {
			Region region = matcher.getEagerRegion();
			recordLegacyMatch(str, region);
			return byteIndexToCharIndex(str, bytes, region.getBeg(0));
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
		if (replace instanceof Callable cb0 && cb0.isCallable()) {
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
				result.append(fromUtf16BEBytes(bytes, start, matchStartByte - start));
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
		byte[] bytes = getBytes(str);
		result.append(fromUtf16BEBytes(bytes, start, bytes.length - start));

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
					result.append(fromUtf16BEBytes(bytes, 0, matchStartByte));
					break;
				case '\'':
					// Text after match
					int matchEndByte = region.getEnd(0);
					result.append(fromUtf16BEBytes(bytes, matchEndByte, bytes.length - matchEndByte));
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
				case '0': case '1': case '2': case '3': case '4': case '5':
				case '6': case '7': case '8': case '9':
					// A leading '0' is never itself a valid ONE-digit reference
					// (n=0 below correctly fails the n>0 check) but IS a valid
					// leading digit of a TWO-digit reference ($01,$02,...,$09) -
					// GetSubstitution tries the 2-digit interpretation first
					// regardless of what the first digit is (confirmed via
					// Symbol.replace/subst-capture-idx-2.js: `$01$02$03` must
					// resolve to groups 1/2/3, not fall through to literal text
					// the moment the first digit happens to be '0').
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
