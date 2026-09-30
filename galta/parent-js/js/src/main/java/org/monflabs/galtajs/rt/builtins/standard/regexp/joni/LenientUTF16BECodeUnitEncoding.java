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

import org.monflabs.galtajs.external.org_joni.encoding.Config;
import org.monflabs.galtajs.external.org_joni.encoding.IntHolder;
import org.monflabs.galtajs.external.org_joni.encoding.ascii.AsciiTables;
import org.monflabs.galtajs.external.org_joni.encoding.unicode.UnicodeEncoding;

// Sibling of LenientUTF16BEEncoding for a regex WITHOUT the "u"/"v" flag.
//
// Without "u"/"v", JS regex operations work at the raw UTF-16 CODE-UNIT
// level: a surrogate pair in the subject string is two independent
// characters, not one combined supplementary code point, and a match may
// legally start (or a lone-surrogate pattern atom may legally match) at
// EITHER half of what looks like a valid pair - see test262's
// built-ins/RegExp/prototype/Symbol.match/builtin-infer-unicode.js:
// /\udf06/.exec("𝌆") (no "u" flag)
// must match the string's low-surrogate half directly, byte-for-byte,
// even though it sits right after a high surrogate that would otherwise
// pair with it.
//
// LenientUTF16BEEncoding (the "u"/"v"-mode encoding) intentionally treats
// a valid surrogate pair as one 4-byte "character" so Joni's search loop
// (which advances candidate match-start positions via
// `p += enc.length(bytes, p, end)`, see Matcher.search()) never tries to
// start a match strictly BETWEEN the two halves of a pair - correct for
// "u"/"v" mode, where a lone surrogate is never a valid match target on
// its own. That same combining is exactly what breaks the non-"u" case
// above: it makes the search loop skip straight over the position where
// the pattern's own lone low-surrogate atom needs to start matching. This
// encoding fixes that by simply never combining - every code unit
// (including either half of what would otherwise be a valid pair) is
// always treated as its own complete, independent 2-byte "character",
// exactly matching JS's own non-unicode string semantics.
public final class LenientUTF16BECodeUnitEncoding extends UnicodeEncoding {

	public static final LenientUTF16BECodeUnitEncoding INSTANCE = new LenientUTF16BECodeUnitEncoding();

	private LenientUTF16BECodeUnitEncoding() {
		super("UTF-16BE", 2, 4, UTF16EncLen);
	}

	@Override
	public int length(byte[] bytes, int p, int end) {
		return end - p >= 2 ? 2 : missing(1);
	}

	// This encoding always advances by 2 bytes per character (see length()
	// above - a raw UTF-16 code-unit sequence with no pair-combining). This
	// flag lets ByteCodeMachine/Matcher/Search fast-path the per-position
	// scan (replacing `s += enc.length(bytes, s, end)` with `s += 2`) - see
	// Encoding.isFixedWidth2() for the full contract.
	@Override
	public boolean isFixedWidth2() {
		return true;
	}

	@Override
	public int mbcToCode(byte[] bytes, int p, int end) {
		return (bytes[p] & 0xff) * 256 + (bytes[p + 1] & 0xff);
	}

	@Override
	public boolean isNewLine(byte[] bytes, int p, int end) {
		if (p + 1 < end) {
			if (bytes[p + 1] == (byte) 0x0a && bytes[p] == (byte) 0x00) return true;

			if (Config.USE_UNICODE_ALL_LINE_TERMINATORS) {
				if ((!Config.USE_CRNL_AS_LINE_TERMINATOR && bytes[p + 1] == (byte) 0x0d) ||
						bytes[p + 1] == (byte) 0x85 && bytes[p] == (byte) 0x00) return true;

				if (bytes[p] == (byte) 0x20 && (bytes[p + 1] == (byte) 0x29 || bytes[p + 1] == (byte) 0x28)) return true;
			}
		}
		return false;
	}

	@Override
	public int codeToMbcLength(int code) {
		return code > 0xffff ? 4 : 2;
	}

	@Override
	public int codeToMbc(int code, byte[] bytes, int p) {
		int p_ = p;
		if (code > 0xffff) {
			int high = (code >>> 10) + 0xd7c0;
			int low = (code & 0x3ff) + 0xdc00;
			bytes[p_++] = (byte) ((high >>> 8) & 0xff);
			bytes[p_++] = (byte) (high & 0xff);
			bytes[p_++] = (byte) ((low >>> 8) & 0xff);
			bytes[p_] = (byte) (low & 0xff);
			return 4;
		} else {
			bytes[p_++] = (byte) ((code & 0xff00) >>> 8);
			bytes[p_++] = (byte) (code & 0xff);
			return 2;
		}
	}

	@Override
	public int mbcCaseFold(int flag, byte[] bytes, IntHolder pp, int end, byte[] fold) {
		int p = pp.value;
		int foldP = 0;

		if (isAscii(bytes[p + 1] & 0xff) && bytes[p] == 0) {
			p++;

			if (Config.USE_UNICODE_CASE_FOLD_TURKISH_AZERI) {
				if ((flag & Config.CASE_FOLD_TURKISH_AZERI) != 0) {
					if (bytes[p] == (byte) 0x49) {
						fold[foldP++] = (byte) 0x01;
						fold[foldP] = (byte) 0x31;
						pp.value += 2;
						return 2;
					}
				}
			} // USE_UNICODE_CASE_FOLD_TURKISH_AZERI

			fold[foldP++] = 0;
			fold[foldP] = AsciiTables.ToLowerCaseTable[bytes[p] & 0xff];
			pp.value += 2;
			return 2;
		} else {
			return super.mbcCaseFold(flag, bytes, pp, end, fold);
		}
	}

	@Override
	public int[] ctypeCodeRange(int ctype, IntHolder sbOut) {
		sbOut.value = 0x00;
		return super.ctypeCodeRange(ctype);
	}

	@Override
	public int leftAdjustCharHead(byte[] bytes, int p, int s, int end) {
		// Every 2-byte-aligned position is already its own complete
		// character in this encoding (no pair-combining to back up out
		// of) - just realign to the nearest code-unit boundary.
		if (s <= p) return s;
		if ((s - p) % 2 == 1) s--;
		return s;
	}

	@Override
	public boolean isReverseMatchAllowed(byte[] bytes, int p, int end) {
		return false;
	}

	// Identical table to UTF16BEEncoding.UTF16EncLen (private there): 2 bytes for
	// every leading byte value except the surrogate range (0xd8-0xdb), which is 4.
	// (Only used by jcodings internals that consult the static table directly
	// rather than calling length() - this class's own length() above never
	// reads it, since it always returns a fixed 2.)
	private static final int[] UTF16EncLen = {
		2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
		2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
		2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
		2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
		2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
		2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
		2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
		2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
		2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
		2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
		2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
		2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
		2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
		2, 2, 2, 2, 2, 2, 2, 2, 4, 4, 4, 4, 2, 2, 2, 2,
		2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
		2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2
	};

}
