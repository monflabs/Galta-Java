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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.standard.regexp.jdk.UnicodePropertyData;
import org.monflabs.galtajs.rt.builtins.standard.regexp.jdk.UnicodeStringPropertyData;

// Recursive-descent parser + evaluator for a 'v'-mode (unicodeSets)
// character class body - the ClassContents grammar of ECMA-262 22.2.2.6/
// 22.2.4, i.e. everything between a class's "[" ("[^" if negated) and its
// matching "]". Unlike a "u"-mode (or flagless) class - which is just an
// opaque substring Joni's own class parser reads directly, character-by-
// character, in RegExpEngineJoni's main translation loop - a "v"-mode
// class supports real SET ALGEBRA (union, "&&" intersection, "--"
// subtraction, arbitrarily nested "[...]" operands) that Joni has no
// native notion of at all. This class evaluates that algebra itself,
// down to a single flat, already-resolved (ranges, strings) pair, then
// hands back Joni-compatible pattern text for RegExpEngineJoni to splice
// in - so nothing past this parser ever needs to know "v"-mode classes are
// special.
//
// Scope: every operand that resolves to a set of individual codepoints -
// literal characters/escapes, ranges, \d \D \s \S \w \W, \p{...}/\P{...}
// (via the same ground-truth UnicodePropertyData table RegExpEngineJoni's
// own "u"-mode path uses), and nested "[...]" - is fully supported, at any
// nesting/operator depth. A \p{...} or \q{...} whose value is
// STRING-valued (a binary-property-of-strings like \p{RGI_Emoji}, or a
// \q{...} alternative longer than one codepoint) is likewise supported: a
// class can only ever match ONE character, so a set containing string
// members can't compile to a plain Joni "[...]" - see this parser's
// `strings` bookkeeping (parallel to `ranges` throughout) and
// RegExpEngineJoni.buildStringSetAlternation(), which rewrites the whole
// class into a "(?:seq1|seq2|...|[remaining single-codepoint ranges])"
// alternation instead, ONLY at the outermost class boundary (parseClassAt)
// - every nested "[...]"/"&&"/"--" operand below that just carries its own
// (ranges, strings) pair through the algebra like any other set, since
// single-codepoint and multi-codepoint members are inherently disjoint
// categories that never interact under union/intersect/subtract (a range
// only ever contains length-1 members, a string-property/\\q{...} sequence
// contributing to `strings` is always length != 1) - so applying each set
// operation independently, elementwise, to the two categories is exactly
// equivalent to applying it to the true (mixed) set.
//
// A negated class ("[^...]") may not contain any string-valued member -
// spec early error (22.2.1.1: "It is a Syntax Error if MayContainStrings
// of ClassContents is true"). MayContainStrings is a STATIC/syntactic
// property (not "is the actual computed strings set non-empty" - the two
// only diverge for "&&"/"--" chains where an operand's nominal type allows
// strings but its concrete contribution happens to cancel out), computed
// structurally per spec: union is true if ANY operand may contain strings;
// intersection ("&&") is true only if ALL chained operands may (an
// intersection with a non-string-valued operand can never itself contain
// strings, regardless of the other operand's actual contents); subtraction
// ("--") tracks only the leftmost (minuend) operand, since subtracting
// FROM a non-string set can never introduce strings. This is tracked
// alongside `ranges`/`strings` as a separate `mayContainStrings` boolean
// (see Operand/ParsedClass) used ONLY for this negation check - never
// conflated with whether the materialized `strings` list is actually
// empty.
final class VClassParser {

	static final class Result {
		String joniText; // ready to splice into the Joni pattern text in place of the whole "[...]"
		int endIndex; // index in the ORIGINAL source just past the matching ']'
	}

	private static final int MAX_CODE_POINT = 0x10FFFF;
	private static final int[] DIGIT_RANGES = {'0', '9'};
	private static final List<int[]> NO_STRINGS = List.of();

	private final boolean ignoreCaseActive;

	VClassParser(boolean ignoreCaseActive) {
		this.ignoreCaseActive = ignoreCaseActive;
	}

	// Entry point: `source.charAt(start)` must be '['. Parses the whole
	// class (recursing for any nested class), evaluates its set algebra,
	// and returns the equivalent Joni pattern text plus the index just past
	// the class's closing ']'.
	Result parseClassAt(String source, int start) {
		ParsedClass pc = parseClassContents(source, start);
		Result r = new Result();
		r.endIndex = pc.endIndex;
		r.joniText = pc.strings.isEmpty()
				? emitJoniClass(pc.ranges)
				: RegExpEngineJoni.buildStringSetAlternation(pc.strings, pc.ranges);
		return r;
	}

	private static String emitJoniClass(int[] ranges) {
		StringBuilder sb = new StringBuilder();
		if (ranges.length == 0) {
			// A class whose set algebra resolves to the empty set can never
			// match any character - spelled out as the negation of the full
			// codepoint range (a genuinely empty Joni class "[]" is not valid
			// syntax) rather than some other always-fail construct, reusing
			// the exact same range-emission machinery as every other case
			// here for consistency.
			sb.append("[^");
			RegExpEngineJoni.appendRangeSplitAtSurrogateBoundary(sb, 0, MAX_CODE_POINT);
			sb.append(']');
			return sb.toString();
		}
		sb.append('[');
		for (int i = 0; i < ranges.length; i += 2) {
			RegExpEngineJoni.appendRangeSplitAtSurrogateBoundary(sb, ranges[i], ranges[i + 1]);
		}
		sb.append(']');
		return sb.toString();
	}

	// ------------------------------------------------------------------
	// ClassContents: [^]? then a ClassUnion, ClassIntersection ("&&"-
	// chain), or ClassSubtraction ("--"-chain) - never a mix of operators
	// at the same nesting level (that requires an explicit nested "[...]"
	// per spec, e.g. "[[a&&b]--c]").
	// ------------------------------------------------------------------

	private static final class ParsedClass {
		int[] ranges; // normalized (sorted, merged) - negation already applied
		List<int[]> strings; // deduped; empty (never null) if this class has no string-valued members
		boolean mayContainStrings; // static/syntactic - see class header
		int endIndex;
	}

	private ParsedClass parseClassContents(String source, int start) {
		int n = source.length();
		int i = start + 1; // past '['
		boolean negated = false;
		if (i < n && source.charAt(i) == '^') {
			negated = true;
			i++;
		}

		if (i < n && source.charAt(i) == ']') {
			return finish(negated ? new int[]{0, MAX_CODE_POINT} : new int[0], NO_STRINGS, false, i + 1);
		}

		Operand first = parseOperand(source, i);
		i = first.endIndex;

		int[] resultRanges;
		List<int[]> resultStrings;
		boolean resultMayContainStrings;

		if (matchesTwoChar(source, i, '&', '&')) {
			int[] accRanges = first.ranges;
			List<int[]> accStrings = first.strings;
			boolean accMay = first.mayContainStrings;
			while (matchesTwoChar(source, i, '&', '&')) {
				i += 2;
				Operand next = parseOperand(source, i);
				accRanges = intersect(accRanges, next.ranges);
				accStrings = intersectStrings(accStrings, next.strings);
				accMay = accMay && next.mayContainStrings; // spec: MayContainStrings of a ClassIntersection is the AND of every chained operand
				i = next.endIndex;
			}
			resultRanges = accRanges;
			resultStrings = accStrings;
			resultMayContainStrings = accMay;
		} else if (matchesTwoChar(source, i, '-', '-')) {
			int[] accRanges = first.ranges;
			List<int[]> accStrings = first.strings;
			boolean accMay = first.mayContainStrings; // spec: MayContainStrings of a ClassSubtraction tracks only the leftmost (minuend) operand
			while (matchesTwoChar(source, i, '-', '-')) {
				i += 2;
				Operand next = parseOperand(source, i);
				accRanges = subtract(accRanges, next.ranges);
				accStrings = subtractStrings(accStrings, next.strings);
				i = next.endIndex;
			}
			resultRanges = accRanges;
			resultStrings = accStrings;
			resultMayContainStrings = accMay;
		} else {
			List<int[]> pieces = new ArrayList<>();
			List<int[]> stringPieces = new ArrayList<>();
			boolean mayContainStrings = false;
			Operand cur = first;
			while (true) {
				if (cur.isChar && i < n && source.charAt(i) == '-'
						&& i + 1 < n && source.charAt(i + 1) != ']' && source.charAt(i + 1) != '-') {
					// Candidate ClassSetRange: "cur - X". Speculatively parse X;
					// if it isn't itself a plain character (e.g. \d), the '-' was
					// never a range operator at all - just a literal hyphen
					// member, immediately followed by X as its own union member.
					Operand second = parseOperand(source, i + 1);
					if (second.isChar) {
						if (cur.codepoint > second.codepoint) {
							throw RuntimeUtil.syntaxError("Character class range out of order");
						}
						pieces.add(new int[]{cur.codepoint, second.codepoint});
					} else {
						pieces.add(new int[]{cur.codepoint, cur.codepoint});
						pieces.add(new int[]{'-', '-'});
						addAll(pieces, second.ranges);
						stringPieces.addAll(second.strings);
						mayContainStrings |= second.mayContainStrings;
					}
					i = second.endIndex;
				} else {
					addAll(pieces, cur.ranges);
					stringPieces.addAll(cur.strings);
					mayContainStrings |= cur.mayContainStrings;
				}
				if (i < n && source.charAt(i) == ']') break;
				if (i >= n) throw RuntimeUtil.syntaxError("Unterminated character class");
				cur = parseOperand(source, i);
				i = cur.endIndex;
			}
			resultRanges = unionAll(pieces);
			resultStrings = dedupStrings(stringPieces);
			resultMayContainStrings = mayContainStrings;
		}

		if (i >= n || source.charAt(i) != ']') {
			throw RuntimeUtil.syntaxError("Unterminated character class");
		}
		i++; // consume ']'

		if (negated) {
			if (resultMayContainStrings) {
				throw RuntimeUtil.syntaxError("A negated character class may not contain a string-valued member");
			}
			resultRanges = RegExpEngineJoni.complementRanges(resultRanges);
		}
		return finish(resultRanges, resultStrings, resultMayContainStrings, i);
	}

	private static ParsedClass finish(int[] ranges, List<int[]> strings, boolean mayContainStrings, int endIndex) {
		ParsedClass pc = new ParsedClass();
		pc.ranges = normalize(ranges);
		pc.strings = strings;
		pc.mayContainStrings = mayContainStrings;
		pc.endIndex = endIndex;
		return pc;
	}

	private static void addAll(List<int[]> pieces, int[] ranges) {
		for (int k = 0; k < ranges.length; k += 2) {
			pieces.add(new int[]{ranges[k], ranges[k + 1]});
		}
	}

	private static boolean matchesTwoChar(String s, int i, char a, char b) {
		return i + 1 < s.length() && s.charAt(i) == a && s.charAt(i + 1) == b;
	}

	// ------------------------------------------------------------------
	// ClassSetOperand: a single character, a predefined class escape
	// (\d \D \s \S \w \W), a Unicode property escape (\p{...}/\P{...}),
	// a class string disjunction (\q{...}), or a nested class - anything
	// that contributes a resolved set of codepoints/strings. `isChar`
	// additionally marks the narrower case (a single literal/escaped
	// character) that's eligible to be a ClassSetRange endpoint.
	// ------------------------------------------------------------------

	private static final class Operand {
		boolean isChar;
		int codepoint;
		int[] ranges;
		List<int[]> strings = NO_STRINGS;
		boolean mayContainStrings;
		int endIndex;
	}

	private static Operand charOperand(int cp, int endIndex) {
		Operand o = new Operand();
		o.isChar = true;
		o.codepoint = cp;
		o.ranges = new int[]{cp, cp};
		o.endIndex = endIndex;
		return o;
	}

	private static Operand setOperand(int[] ranges, int endIndex) {
		return setOperand(ranges, NO_STRINGS, false, endIndex);
	}

	private static Operand setOperand(int[] ranges, List<int[]> strings, boolean mayContainStrings, int endIndex) {
		Operand o = new Operand();
		o.isChar = false;
		o.ranges = normalize(ranges);
		o.strings = strings;
		o.mayContainStrings = mayContainStrings;
		o.endIndex = endIndex;
		return o;
	}

	private Operand parseOperand(String source, int i) {
		int n = source.length();
		if (i >= n) throw RuntimeUtil.syntaxError("Unterminated character class");
		char c = source.charAt(i);

		if (c == '[') {
			ParsedClass nested = parseClassContents(source, i);
			return setOperand(nested.ranges, nested.strings, nested.mayContainStrings, nested.endIndex);
		}

		if (c == '\\') {
			if (i + 1 >= n) throw RuntimeUtil.syntaxError("Trailing backslash in character class");
			char next = source.charAt(i + 1);
			switch (next) {
				case 'd': return setOperand(DIGIT_RANGES.clone(), i + 2);
				case 'D': return setOperand(RegExpEngineJoni.complementRanges(DIGIT_RANGES), i + 2);
				case 's': return setOperand(RegExpEngineJoni.WHITESPACE_RANGES.clone(), i + 2);
				case 'S': return setOperand(RegExpEngineJoni.complementRanges(RegExpEngineJoni.WHITESPACE_RANGES), i + 2);
				case 'w': return setOperand(RegExpEngineJoni.wordCharRanges(ignoreCaseActive), i + 2);
				case 'W': return setOperand(RegExpEngineJoni.complementRanges(RegExpEngineJoni.wordCharRanges(ignoreCaseActive)), i + 2);
				case 'p': case 'P': return parsePropertyEscape(source, i, next);
				case 'q': return parseClassStringDisjunction(source, i);
				default: return parseSingleCharEscape(source, i);
			}
		}

		int cp = source.codePointAt(i);
		return charOperand(cp, i + Character.charCount(cp));
	}

	private Operand parsePropertyEscape(String source, int i, char next) {
		int n = source.length();
		if (i + 2 >= n || source.charAt(i + 2) != '{') {
			throw RuntimeUtil.syntaxError("Invalid Unicode property escape");
		}
		int braceStart = i + 3;
		int j = braceStart;
		while (j < n && source.charAt(j) != '}') j++;
		if (j >= n) throw RuntimeUtil.syntaxError("Invalid Unicode property escape");
		String propExpr = source.substring(braceStart, j);
		int[] ranges = UnicodePropertyData.getRanges(propExpr);
		if (ranges != null) {
			if (next == 'P') ranges = RegExpEngineJoni.complementRanges(ranges);
			return setOperand(ranges, j + 1);
		}
		int[][] sequences = UnicodeStringPropertyData.getSequences(propExpr);
		if (sequences == null) {
			// Genuinely unrecognized property name - the ground-truth table
			// (built from test262's own generated property-escapes suite,
			// both the codepoint and string-valued halves) is exact by
			// construction for every name test262 exercises.
			throw RuntimeUtil.syntaxError(
					"Unsupported Unicode property escape in 'v'-mode character class: \\{0}'{'{1}'}'",
					String.valueOf(next), propExpr);
		}
		if (next == 'P') {
			// Same early error as the standalone-atom case (see
			// RegExpEngineJoni.translateStringPropertyEscape) - a
			// property-of-strings can never be negated.
			throw RuntimeUtil.syntaxError(
					"A Unicode property of strings cannot be negated: \\P'{'{0}'}'", propExpr);
		}
		List<int[]> multiCodepointSeqs = new ArrayList<>();
		int[] singleCodepointRanges = RegExpEngineJoni.splitOutSingleCodepointRanges(sequences, multiCodepointSeqs);
		return setOperand(singleCodepointRanges, multiCodepointSeqs, true, j + 1);
	}

	// \q{alt1|alt2|...} - a "class string disjunction". Each alternative
	// that decodes to EXACTLY one codepoint contributes that codepoint to
	// the operand's `ranges`, same as an ordinary union member; an
	// alternative of zero or 2+ codepoints is a genuine string-valued set
	// member, contributing to `strings` instead (see class header for how
	// that's ultimately compiled).
	private Operand parseClassStringDisjunction(String source, int i) {
		int n = source.length();
		if (i + 2 >= n || source.charAt(i + 2) != '{') {
			throw RuntimeUtil.syntaxError("Invalid class string disjunction");
		}
		int j = i + 3;
		List<int[]> alternatives = new ArrayList<>();
		List<Integer> current = new ArrayList<>();
		while (true) {
			if (j >= n) throw RuntimeUtil.syntaxError("Unterminated \\q{...}");
			char c = source.charAt(j);
			if (c == '}') {
				alternatives.add(toIntArray(current));
				j++;
				break;
			} else if (c == '|') {
				alternatives.add(toIntArray(current));
				current = new ArrayList<>();
				j++;
			} else if (c == '\\') {
				Operand op = parseSingleCharEscape(source, j);
				current.add(op.codepoint);
				j = op.endIndex;
			} else {
				int cp = source.codePointAt(j);
				current.add(cp);
				j += Character.charCount(cp);
			}
		}

		List<int[]> singles = new ArrayList<>();
		List<int[]> multis = new ArrayList<>();
		for (int[] alt : alternatives) {
			if (alt.length == 1) {
				singles.add(new int[]{alt[0], alt[0]});
			} else {
				multis.add(alt);
			}
		}
		int[] ranges = unionAll(singles);
		return setOperand(ranges, multis, !multis.isEmpty(), j);
	}

	private static int[] toIntArray(List<Integer> list) {
		int[] out = new int[list.size()];
		for (int i = 0; i < out.length; i++) out[i] = list.get(i);
		return out;
	}

	// A single-character escape: \n \r \t \f \v \b \0 \cX \xHH \\uHHHH
	// \\u'{'H+'}', or an escaped ClassSetSyntaxCharacter/reserved punctuator
	// (treated as that literal character). `i` points at the '\\'.
	private Operand parseSingleCharEscape(String source, int i) {
		int n = source.length();
		char next = source.charAt(i + 1);
		switch (next) {
			case 'n': return charOperand('\n', i + 2);
			case 'r': return charOperand('\r', i + 2);
			case 't': return charOperand('\t', i + 2);
			case 'f': return charOperand('\f', i + 2);
			case 'v': return charOperand(0x0B, i + 2);
			case 'b': return charOperand(0x08, i + 2); // backspace - class-only meaning
			case '0':
				if (i + 2 < n && Character.isDigit(source.charAt(i + 2))) {
					throw RuntimeUtil.syntaxError("Octal escapes are not allowed in a 'v'-mode character class");
				}
				return charOperand(0, i + 2);
			case 'c': {
				char after = (i + 2 < n) ? source.charAt(i + 2) : '\0';
				if ((after >= 'a' && after <= 'z') || (after >= 'A' && after <= 'Z')) {
					return charOperand(after % 32, i + 3);
				}
				throw RuntimeUtil.syntaxError("Invalid escape sequence: \\c");
			}
			case 'x': {
				if (i + 3 < n) {
					try {
						int val = Integer.parseInt(source.substring(i + 2, i + 4), 16);
						return charOperand(val, i + 4);
					} catch (NumberFormatException e) {
						// fall through to error below
					}
				}
				throw RuntimeUtil.syntaxError("Invalid escape sequence: \\x");
			}
			case 'u': {
				int[] endOut = new int[1];
				int cp = decodeUnicodeEscape(source, i + 2, endOut);
				return charOperand(cp, endOut[0]);
			}
			default:
				if (Character.isLetter(next) || Character.isDigit(next)) {
					throw RuntimeUtil.syntaxError("Invalid escape sequence: \\{0}", String.valueOf(next));
				}
				// ClassSetSyntaxCharacter / reserved punctuator, escaped -> literal.
				return charOperand(next, i + 2);
		}
	}

	// `i` is the index right after "\\u". Handles both "\\uHHHH" and
	// "\\u{H+}"; for the bare "\\uHHHH" form, also combines an immediately
	// following high/low surrogate pair (two adjacent \\u escapes) into
	// one supplementary codepoint - same rationale as
	// RegExpEngineJoni.translateUnicodeEscape()'s handling of a literal
	// astral character used as a class-range boundary.
	private static int decodeUnicodeEscape(String source, int i, int[] endOut) {
		int n = source.length();
		if (i < n && source.charAt(i) == '{') {
			int start = i + 1;
			int end = start;
			while (end < n && source.charAt(end) != '}') end++;
			if (end >= n || end == start) throw RuntimeUtil.syntaxError("Invalid unicode escape");
			int cp;
			try {
				cp = Integer.parseInt(source.substring(start, end), 16);
			} catch (NumberFormatException e) {
				throw RuntimeUtil.syntaxError("Invalid unicode escape");
			}
			if (cp > MAX_CODE_POINT) throw RuntimeUtil.syntaxError("Unicode code point out of range");
			endOut[0] = end + 1;
			return cp;
		}
		if (i + 4 > n) throw RuntimeUtil.syntaxError("Invalid unicode escape");
		int val;
		try {
			val = Integer.parseInt(source.substring(i, i + 4), 16);
		} catch (NumberFormatException e) {
			throw RuntimeUtil.syntaxError("Invalid unicode escape");
		}
		int end = i + 4;
		if (val >= 0xD800 && val <= 0xDBFF && end + 6 <= n
				&& source.charAt(end) == '\\' && source.charAt(end + 1) == 'u') {
			try {
				int low = Integer.parseInt(source.substring(end + 2, end + 6), 16);
				if (low >= 0xDC00 && low <= 0xDFFF) {
					endOut[0] = end + 6;
					return 0x10000 + (val - 0xD800) * 0x400 + (low - 0xDC00);
				}
			} catch (NumberFormatException e) {
				// not a valid low-surrogate escape - fall through, treat val alone
			}
		}
		endOut[0] = end;
		return val;
	}

	// ------------------------------------------------------------------
	// Codepoint-range set algebra: sorted, merged [lo,hi] inclusive pairs.
	// ------------------------------------------------------------------

	private static int[] normalize(int[] ranges) {
		int pairCount = ranges.length / 2;
		if (pairCount == 0) return ranges;
		Integer[] order = new Integer[pairCount];
		for (int i = 0; i < pairCount; i++) order[i] = i;
		Arrays.sort(order, (a, b) -> Integer.compare(ranges[a * 2], ranges[b * 2]));
		List<Integer> out = new ArrayList<>();
		int curStart = -2, curEnd = -2;
		for (int idx : order) {
			int s = ranges[idx * 2], e = ranges[idx * 2 + 1];
			if (s > e) continue;
			if (curStart == -2) {
				curStart = s;
				curEnd = e;
			} else if (s <= curEnd + 1) {
				curEnd = Math.max(curEnd, e);
			} else {
				out.add(curStart);
				out.add(curEnd);
				curStart = s;
				curEnd = e;
			}
		}
		if (curStart != -2) {
			out.add(curStart);
			out.add(curEnd);
		}
		return toIntArray(out);
	}

	private static int[] unionAll(List<int[]> pieces) {
		int total = 0;
		for (int[] p : pieces) total += 2;
		int[] flat = new int[total];
		int idx = 0;
		for (int[] p : pieces) {
			flat[idx++] = p[0];
			flat[idx++] = p[1];
		}
		return normalize(flat);
	}

	private static int[] intersect(int[] a, int[] b) {
		a = normalize(a);
		b = normalize(b);
		List<Integer> out = new ArrayList<>();
		int i = 0, j = 0;
		while (i < a.length && j < b.length) {
			int lo = Math.max(a[i], b[j]);
			int hi = Math.min(a[i + 1], b[j + 1]);
			if (lo <= hi) {
				out.add(lo);
				out.add(hi);
			}
			if (a[i + 1] < b[j + 1]) i += 2; else j += 2;
		}
		return normalize(toIntArray(out));
	}

	private static int[] subtract(int[] a, int[] b) {
		a = normalize(a);
		b = normalize(b);
		List<Integer> out = new ArrayList<>();
		for (int i = 0; i < a.length; i += 2) {
			int cursor = a[i];
			int end = a[i + 1];
			for (int j = 0; j < b.length && cursor <= end; j += 2) {
				int bs = b[j], be = b[j + 1];
				if (be < cursor) continue;
				if (bs > end) break;
				if (bs > cursor) {
					out.add(cursor);
					out.add(bs - 1);
				}
				cursor = be + 1;
			}
			if (cursor <= end) {
				out.add(cursor);
				out.add(end);
			}
		}
		return normalize(toIntArray(out));
	}

	// ------------------------------------------------------------------
	// String-sequence set algebra: each member is a whole codepoint
	// sequence (int[], length != 1); compared by exact content equality.
	// Union/intersect/subtract here are ordinary set operations - unlike
	// the range algebra above, there's no adjacency/merging concept for
	// strings, just membership.
	// ------------------------------------------------------------------

	private static String seqKey(int[] seq) {
		StringBuilder sb = new StringBuilder();
		for (int cp : seq) {
			sb.append(cp).append(',');
		}
		return sb.toString();
	}

	private static List<int[]> dedupStrings(List<int[]> pieces) {
		if (pieces.isEmpty()) return NO_STRINGS;
		Set<String> seen = new HashSet<>();
		List<int[]> out = new ArrayList<>();
		for (int[] seq : pieces) {
			if (seen.add(seqKey(seq))) out.add(seq);
		}
		return out;
	}

	private static List<int[]> intersectStrings(List<int[]> a, List<int[]> b) {
		if (a.isEmpty() || b.isEmpty()) return NO_STRINGS;
		Set<String> bKeys = new HashSet<>();
		for (int[] s : b) bKeys.add(seqKey(s));
		Set<String> seen = new HashSet<>();
		List<int[]> out = new ArrayList<>();
		for (int[] s : a) {
			String k = seqKey(s);
			if (bKeys.contains(k) && seen.add(k)) out.add(s);
		}
		return out;
	}

	private static List<int[]> subtractStrings(List<int[]> a, List<int[]> b) {
		if (a.isEmpty()) return NO_STRINGS;
		if (b.isEmpty()) return dedupStrings(a);
		Set<String> bKeys = new HashSet<>();
		for (int[] s : b) bKeys.add(seqKey(s));
		Set<String> seen = new HashSet<>();
		List<int[]> out = new ArrayList<>();
		for (int[] s : a) {
			String k = seqKey(s);
			if (!bKeys.contains(k) && seen.add(k)) out.add(s);
		}
		return out;
	}
}
