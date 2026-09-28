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

import java.util.HashMap;
import java.util.Map;

// Reverses a Joni-syntax pattern fragment so that matching it FORWARD
// against a REVERSED copy of the string is equivalent to matching the
// ORIGINAL fragment BACKWARD - the standard technique for implementing
// variable-length lookbehind on an engine (Joni/Oniguruma) whose own
// compiled lookbehind opcode only accepts fixed-length (or same-length
// alternatives) content. See RegExpEngineJoniCustom's lookbehind handling for
// how this is used: extract the (?<=BODY)/(?<!BODY) content, reverse it
// with this class, compile the result as an ordinary (no length
// restriction) Joni pattern, and match it against a reversed copy of the
// string prefix.
//
// The key identity this relies on: reversing an ENTIRE pattern (both the
// text order of its terms AND the string it's matched against) preserves
// matching semantics UNCHANGED for ordinary content, but a NESTED
// lookahead/lookbehind assertion is special - per spec (Assertion ::
// (?=Disjunction) always compiles its content with direction +1;
// Assertion :: (?<=Disjunction) always with direction -1, regardless of
// the ENCLOSING direction) a nested assertion's content is evaluated
// against the ORIGINAL (non-reversed) string orientation relative to
// whatever position the outer walk has reached. Reversing the OUTER body
// but leaving a nested lookahead's content untouched would silently read
// the wrong buffer at match time; instead, this SWAPS assertion polarity
// under reversal - lookahead becomes lookbehind and vice versa (negation
// preserved) - and recursively reverses ITS content too, so the whole
// thing keeps matching against the SAME single reversed buffer. A
// variable-length nested assertion becomes fixed-side-swapped too: e.g. a
// nested (?<!a*) (unbounded, would itself be rejected by Joni as a real
// lookbehind) becomes (?!a*) after the outer reversal - an ordinary
// lookAHEAD, which Joni supports with no length restriction at all, so
// nested variable-length assertions "cancel out" through this transform
// rather than needing their own recursive workaround.
final class LookbehindReversal {

	// Maps each capturing group's ORIGINAL (pre-reversal) 1-based number,
	// continuing from whatever offset the caller supplies, to the synthetic
	// name ("g" + number) this class rewrites its opening paren to use in
	// the OUTPUT text - reversal reorders where a group's opening paren
	// falls in the text, which would otherwise reorder Joni's own
	// (left-to-right, text-order) automatic group numbering too. Giving
	// every group (whether the original was a bare "(...)" or a named
	// "(?<name>...)") a unique synthetic name sidesteps that entirely: the
	// reversed regex's OWN group numbers don't need to match the original
	// pattern's numbering at all, since results are looked up by this
	// synthetic name instead (see RegExpEngineJoniCustom's use of
	// Regex.namedBackrefIterator()/NameEntry to build the same kind of
	// name->number map it already builds for ordinary named groups).
	final Map<Integer, String> groupNumberToSyntheticName = new HashMap<>();
	// Original name (if the source used "(?<realName>...)"), keyed by
	// original group number - needed so the caller can still populate its
	// own name->number map for user-visible named-group access (`.groups`).
	final Map<Integer, String> groupNumberToOriginalName = new HashMap<>();

	private final String text;
	private final int groupNumberOffset;
	private int nextGroupNumber;
	// Named groups whose name is already a synthetic "gN" marker minted by
	// an ENCLOSING reverse() call (see RegExpEngineJoniCustom's recursive
	// lookbehind-body compiler, which peels a lookbehind Joni still can't
	// compile after one reversal by re-running LookbehindReversal on just
	// its own nested (?<=X)/(?<!X) content) - such a name is a pass-through
	// marker, not a fresh group to (re)number, so it's left untouched here
	// instead of being renumbered/rewrapped by this second pass. Passed in
	// explicitly by the caller (rather than detected by matching "gN" as a
	// naming convention) because a real GaltaJS source pattern could
	// legitimately name its own group "g1" - only names THIS SPECIFIC
	// caller minted are safe to treat as pass-through.
	private final java.util.Set<String> preserveNames;

	// numberGroupsPass must run (via countAndAssignGroupNumbers) before
	// reverse() - group numbers are assigned by a left-to-right PRE-PASS
	// over the ORIGINAL text (independent of the reversal transform, which
	// reorders where each paren ends up in the OUTPUT), keyed by each
	// group's starting TEXT POSITION so the recursive reversal can look up
	// "which original number does the group starting here have" no matter
	// what order it visits positions in.
	private final Map<Integer, Integer> positionToGroupNumber = new HashMap<>();

	private LookbehindReversal(String text, int groupNumberOffset, java.util.Set<String> preserveNames) {
		this.text = text;
		this.groupNumberOffset = groupNumberOffset;
		this.preserveNames = preserveNames;
	}

	// Reverses `body` (already-translated Joni-syntax pattern text - the
	// exact content between "(?<=" / "(?<!" and its matching ")"), whose
	// capturing groups are numbered starting at groupNumberOffset+1 in the
	// ORIGINAL (containing) pattern. Returns the reversed text plus the
	// group-number/name bookkeeping described above.
	static LookbehindReversal reverse(String body, int groupNumberOffset) {
		return reverse(body, groupNumberOffset, java.util.Collections.emptySet());
	}

	static LookbehindReversal reverse(String body, int groupNumberOffset, java.util.Set<String> preserveNames) {
		LookbehindReversal r = new LookbehindReversal(body, groupNumberOffset, preserveNames);
		r.assignGroupNumbers(body, 0, body.length());
		String reversed = r.reverseDisjunction(body, 0, body.length());
		r.reversedText = reversed;
		r.groupCount = r.nextGroupNumber;
		return r;
	}

	String reversedText;
	// Total number of capturing groups found in `body` (both plain and
	// named) - the caller uses this to offset SUFFIX's own group numbers
	// (which continue right after body's) and to tell whether a SUFFIX
	// backreference crosses into body (target number <= groupNumberOffset +
	// groupCount) and needs special bridging rather than Joni's own native
	// backreference resolution (body and suffix are separately-compiled
	// Regex objects, so Joni can't resolve a cross-object reference itself).
	int groupCount;

	// First pass: assign each capturing group (plain "(" or named
	// "(?<name>") its original left-to-right number, purely by TEXT
	// POSITION - completely independent of the later reversal transform.
	private void assignGroupNumbers(String s, int from, int to) {
		int i = from;
		boolean inCC = false;
		while (i < to) {
			char c = s.charAt(i);
			if (c == '\\') {
				i += 2;
				continue;
			}
			if (c == '[' ) {
				inCC = true;
				i++;
				continue;
			}
			if (c == ']') {
				inCC = false;
				i++;
				continue;
			}
			if (c == '(' && !inCC) {
				if (i + 1 < to && s.charAt(i + 1) == '?') {
					char k = i + 2 < to ? s.charAt(i + 2) : '\0';
					if (k == '<' && i + 3 < to && s.charAt(i + 3) != '=' && s.charAt(i + 3) != '!') {
						// named capturing group (?<name>...)
						int nameEnd = s.indexOf('>', i + 3);
						String name = s.substring(i + 3, nameEnd);
						if (!preserveNames.contains(name)) {
							int num = groupNumberOffset + (++nextGroupNumber);
							positionToGroupNumber.put(i, num);
							groupNumberToOriginalName.put(num, name);
						}
					}
					// (?: (?= (?! (?<= (?<! - not capturing, no number
				} else {
					int num = groupNumberOffset + (++nextGroupNumber);
					positionToGroupNumber.put(i, num);
				}
			}
			i++;
		}
	}

	// Splits `s[from,to)` on top-level "|" and reverses each branch's own
	// term SEQUENCE (branch order itself is NOT reversed - alternatives are
	// still tried left-to-right, per spec, regardless of direction).
	private String reverseDisjunction(String s, int from, int to) {
		java.util.List<int[]> branches = new java.util.ArrayList<>();
		int depth = 0;
		boolean inCC = false;
		int start = from;
		int i = from;
		while (i < to) {
			char c = s.charAt(i);
			if (c == '\\') {
				i += 2;
				continue;
			}
			if (inCC) {
				if (c == ']') inCC = false;
				i++;
				continue;
			}
			if (c == '[') {
				inCC = true;
			} else if (c == '(') {
				depth++;
			} else if (c == ')') {
				depth--;
			} else if (c == '|' && depth == 0) {
				branches.add(new int[]{start, i});
				start = i + 1;
			}
			i++;
		}
		branches.add(new int[]{start, to});

		StringBuilder out = new StringBuilder();
		for (int b = 0; b < branches.size(); b++) {
			if (b > 0) out.append('|');
			int[] span = branches.get(b);
			out.append(reverseSequence(s, span[0], span[1]));
		}
		return out.toString();
	}

	// Reverses the ORDER of terms in a single alternation branch. Each term
	// is "one atom plus its trailing quantifier, if any" - the quantifier
	// travels with its atom, only the overall term ORDER flips.
	private String reverseSequence(String s, int from, int to) {
		java.util.List<String> terms = new java.util.ArrayList<>();
		int i = from;
		while (i < to) {
			int termStart = i;
			int atomEnd = scanAtom(s, i, to);
			int quantEnd = scanQuantifier(s, atomEnd, to);
			terms.add(reverseTerm(s, termStart, atomEnd, quantEnd));
			i = quantEnd;
		}
		StringBuilder out = new StringBuilder();
		for (int t = terms.size() - 1; t >= 0; t--) {
			out.append(terms.get(t));
		}
		return out.toString();
	}

	// Reverses a single term (atom + optional quantifier). For most atom
	// kinds the term's OWN text is emitted unchanged (only its position in
	// the sequence moved) - group contents and swapped assertions are the
	// exceptions, recursively reversed here.
	private String reverseTerm(String s, int termStart, int atomEnd, int quantEnd) {
		String quantifier = s.substring(atomEnd, quantEnd);
		char c = s.charAt(termStart);

		if (c == '^') return "\\z" + quantifier;
		if (c == '$') return "\\A" + quantifier;

		if (c == '\\' && atomEnd == termStart + 2) {
			char e = s.charAt(termStart + 1);
			if (e == 'A') return "\\z" + quantifier;
			if (e == 'z' || e == 'Z') return "\\A" + quantifier;
		}

		if (c == '(') {
			return reverseGroup(s, termStart, atomEnd) + quantifier;
		}

		// Literal char, escape, character class, etc. - unchanged content,
		// only its position in the sequence moves (handled by the caller).
		return s.substring(termStart, atomEnd) + quantifier;
	}

	private String reverseGroup(String s, int start, int end) {
		// start points to '(', end points just past the matching ')'.
		int innerStart;
		int innerEnd = end - 1; // just before ')'
		String prefix; // what to re-emit before the reversed inner content
		String suffix = ")";

		if (s.charAt(start + 1) == '?') {
			char k = s.charAt(start + 2);
			if (k == ':') {
				innerStart = start + 3;
				prefix = "(?:";
			} else if (k == '=') {
				// (?=X) -> (?<=reverse(X))
				innerStart = start + 3;
				prefix = "(?<=";
			} else if (k == '!') {
				// (?!X) -> (?<!reverse(X))
				innerStart = start + 3;
				prefix = "(?<!";
			} else if (k == '<' && s.charAt(start + 3) == '=') {
				// (?<=X) -> (?=reverse(X))
				innerStart = start + 4;
				prefix = "(?=";
			} else if (k == '<' && s.charAt(start + 3) == '!') {
				// (?<!X) -> (?!reverse(X))
				innerStart = start + 4;
				prefix = "(?!";
			} else {
				// (?<name>X) - named capturing group
				int nameEnd = s.indexOf('>', start + 3);
				innerStart = nameEnd + 1;
				String name = s.substring(start + 3, nameEnd);
				if (preserveNames.contains(name)) {
					// Pass-through marker from an enclosing reverse() call
					// (see the preserveNames field comment) - keep the same
					// name, only its content gets (recursively) reversed.
					prefix = "(?<" + name + ">";
				} else {
					int num = positionToGroupNumber.get(start);
					String synthetic = "g" + num;
					groupNumberToSyntheticName.put(num, synthetic);
					prefix = "(?<" + synthetic + ">";
				}
			}
		} else {
			// plain capturing group
			innerStart = start + 1;
			int num = positionToGroupNumber.get(start);
			String synthetic = "g" + num;
			groupNumberToSyntheticName.put(num, synthetic);
			prefix = "(?<" + synthetic + ">";
		}

		String innerReversed = reverseDisjunction(s, innerStart, innerEnd);
		return prefix + innerReversed + suffix;
	}

	// Scans one atom starting at i (a "(" group, a "[" character class, a
	// "\" escape, or a single ordinary character), returning the index just
	// past it.
	// Finds the index just past the ")" matching the "(" at openParen -
	// exposed for RegExpEngineJoniCustom's own pattern-shape detection (finding
	// where a top-level lookbehind group ends, to split out its SUFFIX).
	static int scanGroup(String s, int openParen) {
		return scanAtom(s, openParen, s.length());
	}

	private static int scanAtom(String s, int i, int to) {
		char c = s.charAt(i);
		if (c == '\\') {
			return Math.min(i + 2, to);
		}
		if (c == '[') {
			int j = i + 1;
			if (j < to && s.charAt(j) == '^') j++;
			if (j < to && s.charAt(j) == ']') j++; // a leading ] is literal
			while (j < to && s.charAt(j) != ']') {
				if (s.charAt(j) == '\\') j++;
				j++;
			}
			return Math.min(j + 1, to);
		}
		if (c == '(') {
			int depth = 0;
			int j = i;
			boolean inCC = false;
			while (j < to) {
				char d = s.charAt(j);
				if (d == '\\') {
					j += 2;
					continue;
				}
				if (inCC) {
					if (d == ']') inCC = false;
					j++;
					continue;
				}
				if (d == '[') {
					inCC = true;
				} else if (d == '(') {
					depth++;
				} else if (d == ')') {
					depth--;
					if (depth == 0) return j + 1;
				}
				j++;
			}
			return to;
		}
		return i + 1;
	}

	private static int scanQuantifier(String s, int i, int to) {
		if (i >= to) return i;
		char c = s.charAt(i);
		int j = i;
		if (c == '*' || c == '+' || c == '?') {
			j++;
		} else if (c == '{') {
			int close = s.indexOf('}', i);
			if (close < 0 || close >= to) return i;
			// Only treat as a quantifier if the braced content looks like
			// {n} / {n,} / {n,m} - otherwise it's a literal '{'.
			String inside = s.substring(i + 1, close);
			if (!inside.matches("\\d+(,\\d*)?")) return i;
			j = close + 1;
		} else {
			return i;
		}
		if (j < to && s.charAt(j) == '?') j++; // lazy quantifier
		return j;
	}
}
