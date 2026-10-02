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
package org.monflabs.galtajs.external.org_joni.encoding.exception;

import org.monflabs.galtajs.external.org_joni.encoding.util.IntHash;

public enum EncodingError {
    ERR_TYPE_BUG(ErrorMessages.ERR_TYPE_BUG, ErrorCodes.ERR_TYPE_BUG),

    ERR_TOO_BIG_WIDE_CHAR_VALUE(ErrorMessages.ERR_TOO_BIG_WIDE_CHAR_VALUE, ErrorCodes.ERR_TOO_BIG_WIDE_CHAR_VALUE),
    ERR_TOO_LONG_WIDE_CHAR_VALUE(ErrorMessages.ERR_TOO_LONG_WIDE_CHAR_VALUE, ErrorCodes.ERR_TOO_LONG_WIDE_CHAR_VALUE),

    ERR_INVALID_CHAR_PROPERTY_NAME(ErrorMessages.ERR_INVALID_CHAR_PROPERTY_NAME, ErrorCodes.ERR_INVALID_CHAR_PROPERTY_NAME),
    ERR_INVALID_CODE_POINT_VALUE(ErrorMessages.ERR_INVALID_CODE_POINT_VALUE, ErrorCodes.ERR_INVALID_CODE_POINT_VALUE),

    ERR_ENCODING_CLASS_DEF_NOT_FOUND(ErrorMessages.ERR_ENCODING_CLASS_DEF_NOT_FOUND, ErrorCodes.ERR_ENCODING_CLASS_DEF_NOT_FOUND),
    ERR_ENCODING_LOAD_ERROR(ErrorMessages.ERR_ENCODING_LOAD_ERROR, ErrorCodes.ERR_ENCODING_LOAD_ERROR),

    ERR_ENCODING_ALREADY_REGISTERED(ErrorMessages.ERR_ENCODING_ALREADY_REGISTERED, ErrorCodes.ERR_ENCODING_ALREADY_REGISTERED),
    ERR_ENCODING_ALIAS_ALREADY_REGISTERED(ErrorMessages.ERR_ENCODING_ALIAS_ALREADY_REGISTERED, ErrorCodes.ERR_ENCODING_ALIAS_ALREADY_REGISTERED),
    ERR_ENCODING_REPLICA_ALREADY_REGISTERED(ErrorMessages.ERR_ENCODING_REPLICA_ALREADY_REGISTERED, ErrorCodes.ERR_ENCODING_REPLICA_ALREADY_REGISTERED),
    ERR_NO_SUCH_ENCODNG(ErrorMessages.ERR_NO_SUCH_ENCODNG, ErrorCodes.ERR_NO_SUCH_ENCODNG),
    ERR_COULD_NOT_REPLICATE(ErrorMessages.ERR_COULD_NOT_REPLICATE, ErrorCodes.ERR_COULD_NOT_REPLICATE),

    // transcoder messages
    ERR_TRANSCODER_ALREADY_REGISTERED(ErrorMessages.ERR_TRANSCODER_ALREADY_REGISTERED, ErrorCodes.ERR_TRANSCODER_ALREADY_REGISTERED),
    ERR_TRANSCODER_CLASS_DEF_NOT_FOUND(ErrorMessages.ERR_TRANSCODER_CLASS_DEF_NOT_FOUND, ErrorCodes.ERR_TRANSCODER_CLASS_DEF_NOT_FOUND),
    ERR_TRANSCODER_LOAD_ERROR(ErrorMessages.ERR_TRANSCODER_LOAD_ERROR, ErrorCodes.ERR_TRANSCODER_LOAD_ERROR);

    EncodingError(String message, int code) {
        this.message = message;
        this.code = code;
    }

    private final String message;
    private final int code;

    public String getMessage() {
        return message;
    }

    public int getCode() {
        return code;
    }

    public static EncodingError fromCode(int code) {
        return CODE_TO_ERROR.get(code);
    }

    private static final IntHash<EncodingError> CODE_TO_ERROR = new IntHash<EncodingError>();
    static {
        for (EncodingError error : EncodingError.values()) {
            CODE_TO_ERROR.put(error.getCode(), error);
        }
    }
}
