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

import org.monflabs.galtajs.external.org_joni.encoding.Config;
import org.monflabs.galtajs.external.org_joni.encoding.IntHolder;
import org.monflabs.galtajs.external.org_joni.encoding.ascii.AsciiTables;
import org.monflabs.galtajs.external.org_joni.encoding.unicode.UnicodeEncoding;

// A UTF-16BE encoding for Joni that never reports a byte position as an
// invalid character. jcodings' own UTF16BEEncoding.length() returns
// CHAR_INVALID (-1) for an unpaired surrogate half - correct for real
// Unicode text, but wrong for JS strings, which are raw UTF-16 code-unit
// sequences that may legally contain lone surrogates (and JS regex
// operations may start a search/match at ANY code-unit index, including
// one that splits what would otherwise be a valid pair - e.g. a global
// regex resuming from a lastIndex that lands on a low surrogate).
//
// This matters beyond correctness: Joni's own Matcher advances scan
// positions via `p += enc.length(bytes, p, end)` (see Matcher.search()).
// A length() of CHAR_INVALID (-1) makes that add negative, so the scan
// position can stop making forward progress - an actual infinite loop,
// not just a wrong-answer bug. Treating every unpaired surrogate half as
// its own standalone 2-byte "character" (matching its raw code-unit value,
// exactly as JS itself treats it) keeps length() always positive and keeps
// mbcToCode() consistent with whatever length() reported for that position.
//
// UTF16BEEncoding is final, so this reimplements it rather than extending
// it; every method below other than length()/mbcToCode() is copied
// verbatim from jcodings' UTF16BEEncoding.
public final class LenientUTF16BEEncoding extends UnicodeEncoding {

	public static final LenientUTF16BEEncoding INSTANCE = new LenientUTF16BEEncoding();

	private LenientUTF16BEEncoding() {
		super("UTF-16BE", 2, 4, UTF16EncLen);
	}

	@Override
	public int length(byte[] bytes, int p, int end) {
		int b = bytes[p] & 0xff;
		if (!isSurrogate(b)) {
			return end - p >= 2 ? 2 : missing(1);
		}
		if (isSurrogateFirst(b) && end - p >= 4 && isSurrogateSecond(bytes[p + 2] & 0xff)) {
			return 4;
		}
		// Lone surrogate half - either a trailing surrogate on its own, a
		// leading surrogate with no valid trailing surrogate after it, or a
		// leading surrogate simply at/near the end of the buffer. jcodings'
		// own UTF16BEEncoding treats that last case as "need more bytes"
		// (missing(1..3)), a sentinel meant for a STREAMING source that
		// might still deliver the completing byte(s) later - but every
		// buffer here (translatePattern()'s translated.getBytes(UTF_16BE),
		// or a short internal buffer like OptExactInfo's per-class-member
		// encode-then-rescan round trip) is complete and final; there is no
		// "more data coming" to wait for. A missing(N) return here is worse
		// than CHAR_INVALID: it's a NEGATIVE value, and at least one Joni
		// caller (OptExactInfo.concatStr, used while building the pattern
		// compiler's own "expected literal" optimization hint) does
		// `for (...; p < end && ...) { len = enc.length(...); ...
		// for (j=0; j<len && p<end; j++) { p++; } }` with no check for a
		// negative/non-positive len - the inner loop's `j<len` is
		// immediately false for a negative len, so p and the outer loop's
		// condition never change and it spins forever. Reporting this
		// position as its own standalone, complete 2-byte unit (matching
		// the "confirmed no valid pair" case just above) avoids that by
		// construction - every length() return here is a positive 2 or 4,
		// never a missing()/negative sentinel.
		return 2;
	}

	@Override
	public int mbcToCode(byte[] bytes, int p, int end) {
		int b = bytes[p] & 0xff;
		// Mirror length()'s pairing check exactly: only combine into a
		// supplementary code point when there really is a valid trailing
		// surrogate in range - otherwise this position's "character" is
		// just the lone surrogate's own raw code-unit value.
		if (isSurrogateFirst(b) && end - p >= 4 && isSurrogateSecond(bytes[p + 2] & 0xff)) {
			return (((((bytes[p] & 0xff) << 8) + (bytes[p + 1] & 0xff)) & 0x03ff) << 10) +
					((((bytes[p + 2] & 0xff) << 8) + (bytes[p + 3] & 0xff)) & 0x03ff) + 0x10000;
		}
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
		if (s <= p) return s;

		if ((s - p) % 2 == 1) s--;

		if (isSurrogateSecond(bytes[s] & 0xff) && s > p + 1) s -= 2;

		return s;
	}

	@Override
	public boolean isReverseMatchAllowed(byte[] bytes, int p, int end) {
		return false;
	}

	// Identical table to UTF16BEEncoding.UTF16EncLen (private there): 2 bytes for
	// every leading byte value except the surrogate range (0xd8-0xdb), which is 4.
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

	private static boolean isSurrogateFirst(int c) {
		return (c & 0xfc) == 0xd8;
	}

	private static boolean isSurrogateSecond(int c) {
		return (c & 0xfc) == 0xdc;
	}

	private static boolean isSurrogate(int c) {
		return (c & 0xf8) == 0xd8;
	}

}
