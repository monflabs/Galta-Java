/*
 * Part of JCodings, Copyright (c) JRuby Team, modified for GaltaJS.
 * Licensed under the MIT License (see META-INF/licenses/joni-jcodings-MIT.txt).
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.monflabs.galtajs.external.org_joni.encoding.util;

/**
 * ONIGENC macros from Ruby
 */
public class Macros {
    public static final int MBCLEN_INVALID = -1;

    // CONSTRUCT_MBCLEN_INVALID, ONIGENC_CONSTRUCT_MBCLEN_INVALID
    public static int CONSTRUCT_MBCLEN_INVALID() {
        return MBCLEN_INVALID;
    }

    // MBCLEN_NEEDMORE_P, ONIGENC_MBCLEN_NEEDMORE_P
    public static boolean MBCLEN_NEEDMORE_P(int r) {
        return r < -1;
    }

    // CONSTRUCT_MBCLEN_NEEDMORE, CONSTRUCT_ONIGENC_MBCLEN_NEEDMORE
    public static int CONSTRUCT_MBCLEN_NEEDMORE(int n) {
        return -1 - n;
    }

    // MBCLEN_NEEDMORE_LEN, ONIGENC_MBCLEN_NEEDMORE_LEN
    public static int MBCLEN_NEEDMORE_LEN(int r) {
        return -1 - r;
    }

    // MBCLEN_INVALID_P, ONIGENC_MBCLEN_INVALID_P
    public static boolean MBCLEN_INVALID_P(int r) {
        return r == MBCLEN_INVALID;
    }

    // MBCLEN_CHARFOUND_LEN, ONIGENC_MBCLEN_CHARFOUND_LEN
    public static int MBCLEN_CHARFOUND_LEN(int r) {
        return r;
    }

    // MBCLEN_CHARFOUND_P, ONIGENC_MBCLEN_CHARFOUND_P
    public static boolean MBCLEN_CHARFOUND_P(int r) {
        return 0 < r;
    }

    // CONSTRUCT_MBCLEN_CHARFOUND, ONIGENC_CONSTRUCT_MBCLEN_CHARFOUND
    public static int CONSTRUCT_MBCLEN_CHARFOUND(int n) {
        return n;
    }

    // UNICODE_VALID_CODEPOINT_P
    public static boolean UNICODE_VALID_CODEPOINT_P(int c) {
        return (Integer.compareUnsigned(c, 0x10ffff) <= 0) &&
            !((c) < 0x10000 && UTF16_IS_SURROGATE((c) >> 8));
    }

    // UTF16_IS_SURROGATE_FIRST
    public static boolean UTF16_IS_SURROGATE_FIRST(int c)  {
        return ((c) & 0xfc) == 0xd8;
    }

     // UTF16_IS_SURROGATE_SECOND
     public static boolean UTF16_IS_SURROGATE_SECOND(int c) {
        return ((c) & 0xfc) == 0xdc;
     }

     // UTF16_IS_SURROGATE
     public static boolean UTF16_IS_SURROGATE(int c) {
        return ((c) & 0xf8) == 0xd8;
     }
}
