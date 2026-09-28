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
package org.monflabs.galtajs.rt.builtins.standard.typedarrays.uint8;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;

/**
 * Shared encode/decode logic behind Uint8Array.fromBase64/fromHex and
 * Uint8Array.prototype.toBase64/toHex/setFromBase64/setFromHex (the
 * "Uint8Array base64/hex" proposal) - kept as pure, receiver-agnostic
 * functions so the static (unbounded, build-a-fresh-array) and prototype
 * (bounded by an existing target's length, partial-write-before-error
 * visible) call sites can share one implementation, empirically validated
 * against every file under test262's built-ins/Uint8Array/{fromBase64,
 * fromHex,prototype/{toBase64,toHex,setFromBase64,setFromHex}}/.
 */
final class Base64HexCodec {

	private Base64HexCodec() {}

	enum Alphabet { BASE64, BASE64URL }
	enum LastChunkHandling { LOOSE, STRICT, STOP_BEFORE_PARTIAL }

	private static final String B64_STD = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
	private static final String B64_URL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

	// Called once per successfully-decoded output byte, in order - a target
	// (setFromBase64/setFromHex) writes straight into the receiver so bytes
	// from chunks decoded BEFORE a later SyntaxError stay visible even
	// though the exception unwinds past the decode call (test262's own
	// "writes-up-to-error"/"trailing-garbage" expectation); the static
	// factories (fromBase64/fromHex) just accumulate into a fresh buffer
	// and discard it on error, since no array has been exposed to script yet.
	interface ByteSink {
		void write(int byteValue);
	}

	private static boolean isAsciiWhitespace(char c) {
		return c==' ' || c=='\t' || c=='\n' || c=='\f' || c=='\r';
	}

	private static int decodeBase64Char(Alphabet alphabet, char c) {
		String table = alphabet==Alphabet.BASE64 ? B64_STD : B64_URL;
		if(c>127) {
			return -1;
		}
		return table.indexOf(c);
	}

	private static void emitChunk(ByteSink sink, int[] quartet, int len) {
		int b0 = (quartet[0]<<2) | (quartet[1]>>4);
		sink.write(b0 & 0xFF);
		if(len>=3) {
			int b1 = ((quartet[1]&0xF)<<4) | (quartet[2]>>2);
			sink.write(b1 & 0xFF);
		}
		if(len==4) {
			int b2 = ((quartet[2]&0x3)<<6) | quartet[3];
			sink.write(b2 & 0xFF);
		}
	}

	private static int chunkByteCount(int quartetLen) {
		return switch(quartetLen) {
			case 2 -> 1;
			case 3 -> 2;
			case 4 -> 3;
			default -> throw new IllegalStateException();
		};
	}

	/**
	 * FromBase64(string, alphabet, lastChunkHandling, maxLength): returns
	 * the number of INPUT characters consumed ("read"); decoded bytes are
	 * emitted incrementally to {@code sink} as each quartet (whether a full
	 * 4-sextet group or a padding-terminated 2/3-sextet final group)
	 * completes, and a completing chunk that would push the total emitted
	 * byte count past {@code maxLength} is never emitted at all - `read`
	 * simply stops at the last chunk boundary that fit, silently (no
	 * error), matching test262's target-buffer-too-small AND
	 * once-target-is-full-ignore-any-trailing-content behavior.
	 */
	static int decodeBase64(String s, Alphabet alphabet, LastChunkHandling mode, int maxLength, ByteSink sink) {
		if(maxLength==0) {
			return 0;
		}
		int n = s.length();
		int index = 0;
		int read = 0;
		int written = 0;
		int[] quartet = new int[4];
		int quartetLen = 0;

		while(index<n) {
			char c = s.charAt(index);
			if(isAsciiWhitespace(c)) {
				index++;
				continue;
			}
			if(c=='=') {
				if(quartetLen<2) {
					throw RuntimeUtil.syntaxError("Unexpected base64 padding character");
				}
				int needed = quartetLen==2 ? 2 : 1;
				int padSeen = 0;
				int pos = index;
				while(pos<n) {
					char pc = s.charAt(pos);
					if(isAsciiWhitespace(pc)) {
						pos++;
						continue;
					}
					if(pc=='=') {
						padSeen++;
						pos++;
						continue;
					}
					break;
				}
				if(padSeen>needed) {
					throw RuntimeUtil.syntaxError("Excess base64 padding");
				}
				if(padSeen<needed) {
					if(mode==LastChunkHandling.STOP_BEFORE_PARTIAL) {
						return read;
					}
					throw RuntimeUtil.syntaxError("Incomplete base64 padding");
				}
				if(mode==LastChunkHandling.STRICT) {
					int mask = quartetLen==2 ? 0xF : 0x3;
					if((quartet[quartetLen-1] & mask)!=0) {
						throw RuntimeUtil.syntaxError("Unexpected non-zero padding bits in base64 data");
					}
				}
				int chunkBytes = chunkByteCount(quartetLen);
				if(written+chunkBytes>maxLength) {
					return read;
				}
				emitChunk(sink, quartet, quartetLen);
				written += chunkBytes;
				read = pos;
				if(written==maxLength) {
					return read;
				}
				if(pos!=n) {
					throw RuntimeUtil.syntaxError("Unexpected data after base64 padding");
				}
				return read;
			}
			int v = decodeBase64Char(alphabet, c);
			if(v<0) {
				throw RuntimeUtil.syntaxError("Illegal base64 character");
			}
			quartet[quartetLen++] = v;
			index++;
			if(quartetLen==4) {
				if(written+3>maxLength) {
					return read;
				}
				emitChunk(sink, quartet, 4);
				written += 3;
				quartetLen = 0;
				read = index;
				if(written==maxLength) {
					return read;
				}
			}
		}

		if(quartetLen==1) {
			if(mode!=LastChunkHandling.STOP_BEFORE_PARTIAL) {
				throw RuntimeUtil.syntaxError("Incomplete base64 data");
			}
		} else if(quartetLen==2 || quartetLen==3) {
			if(mode==LastChunkHandling.STRICT) {
				throw RuntimeUtil.syntaxError("Missing base64 padding");
			}
			if(mode==LastChunkHandling.LOOSE) {
				int chunkBytes = chunkByteCount(quartetLen);
				if(written+chunkBytes<=maxLength) {
					emitChunk(sink, quartet, quartetLen);
					read = n;
				}
			}
		}
		return read;
	}

	static String encodeBase64(byte[] data, Alphabet alphabet, boolean omitPadding) {
		String table = alphabet==Alphabet.BASE64 ? B64_STD : B64_URL;
		StringBuilder sb = new StringBuilder(((data.length+2)/3)*4);
		int i = 0;
		int n = data.length;
		while(i+3<=n) {
			int b0 = data[i]&0xFF, b1 = data[i+1]&0xFF, b2 = data[i+2]&0xFF;
			sb.append(table.charAt(b0>>2));
			sb.append(table.charAt(((b0&0x3)<<4)|(b1>>4)));
			sb.append(table.charAt(((b1&0xF)<<2)|(b2>>6)));
			sb.append(table.charAt(b2&0x3F));
			i += 3;
		}
		int remaining = n-i;
		if(remaining==1) {
			int b0 = data[i]&0xFF;
			sb.append(table.charAt(b0>>2));
			sb.append(table.charAt((b0&0x3)<<4));
			if(!omitPadding) {
				sb.append("==");
			}
		} else if(remaining==2) {
			int b0 = data[i]&0xFF, b1 = data[i+1]&0xFF;
			sb.append(table.charAt(b0>>2));
			sb.append(table.charAt(((b0&0x3)<<4)|(b1>>4)));
			sb.append(table.charAt((b1&0xF)<<2));
			if(!omitPadding) {
				sb.append("=");
			}
		}
		return sb.toString();
	}

	private static int hexVal(char c) {
		if(c>='0' && c<='9') {
			return c-'0';
		}
		if(c>='a' && c<='f') {
			return c-'a'+10;
		}
		if(c>='A' && c<='F') {
			return c-'A'+10;
		}
		return -1;
	}

	/**
	 * FromHex(string, maxLength): unlike base64, hex has no whitespace
	 * tolerance and no partial-chunk ambiguity - a pair of hex digits is
	 * either valid or the whole operation is illegal. An odd-length input
	 * is rejected UP FRONT, before any byte is emitted at all (test262's
	 * own "when length is odd no data is written").
	 */
	static int decodeHex(String s, int maxLength, ByteSink sink) {
		int n = s.length();
		if(n%2!=0) {
			throw RuntimeUtil.syntaxError("Uint8Array.fromHex/setFromHex requires an even-length string");
		}
		int written = 0;
		int i = 0;
		while(i<n) {
			int v1 = hexVal(s.charAt(i));
			int v2 = hexVal(s.charAt(i+1));
			if(v1<0 || v2<0) {
				throw RuntimeUtil.syntaxError("Illegal hex character");
			}
			if(written+1>maxLength) {
				return i;
			}
			sink.write((v1<<4)|v2);
			written++;
			i += 2;
			if(written==maxLength) {
				return i;
			}
		}
		return i;
	}

	static String encodeHex(byte[] data) {
		StringBuilder sb = new StringBuilder(data.length*2);
		for(byte b: data) {
			int v = b & 0xFF;
			sb.append(Character.forDigit((v>>4)&0xF, 16));
			sb.append(Character.forDigit(v&0xF, 16));
		}
		return sb.toString();
	}

	// GetOptionsObject-style handling: undefined -> use every default below;
	// otherwise must be an actual object (not coerced). Reading each named
	// option is a single Get (side effects, e.g. a getter detaching a
	// buffer, are allowed to run and are observed by the caller afterwards -
	// see toBase64/detached-buffer.js) with NO further ToString/ToBoolean-ish
	// coercion beyond what's documented per option (alphabet/
	// lastChunkHandling must be an actual string primitive from the fixed
	// value list - test262's option-coercion.js confirms `Object("base64")`
	// is rejected rather than unwrapped).
	private static Object requireOptionsObject(JSEnvironment env, Object options) {
		if(options==RuntimeUtil.UNDEFINED) {
			return null;
		}
		if(!RuntimeUtil.isObject(env, options)) {
			throw RuntimeUtil.typeError("Options must be an object");
		}
		return options;
	}

	// A plain `.equals()` against a Java String literal isn't enough here:
	// GaltaJS represents a BOXED string (`new String("base64")`/
	// `Object("base64")`) as a distinct (non-interned) `java.lang.String`
	// instance registered by IDENTITY in the environment's
	// PrimitivePropertyMap (see RuntimeUtil.primitiveAsObject(CharSequence))
	// - it's `.equals()`-identical to the primitive but must still be
	// rejected as the wrong Type, exactly like any other object (confirmed
	// needed via fromBase64/option-coercion.js: `Object("base64")` must
	// throw TypeError, not be silently accepted as if it were the
	// primitive).
	static boolean isStringPrimitive(JSEnvironment env, Object v) {
		return v instanceof String && RuntimeUtil.isPrimitiveValue(env, v);
	}

	static Alphabet readAlphabetOption(JSEnvironment env, Object optionsArg) {
		Object options = requireOptionsObject(env, optionsArg);
		if(options==null) {
			return Alphabet.BASE64;
		}
		Object v = RuntimeUtil.getProperty(env, options, "alphabet");
		if(v==RuntimeUtil.UNDEFINED) {
			return Alphabet.BASE64;
		}
		if(isStringPrimitive(env, v)) {
			if("base64".equals(v)) {
				return Alphabet.BASE64;
			}
			if("base64url".equals(v)) {
				return Alphabet.BASE64URL;
			}
		}
		throw RuntimeUtil.typeError("alphabet must be \"base64\" or \"base64url\"");
	}

	static LastChunkHandling readLastChunkHandlingOption(JSEnvironment env, Object optionsArg) {
		Object options = requireOptionsObject(env, optionsArg);
		if(options==null) {
			return LastChunkHandling.LOOSE;
		}
		Object v = RuntimeUtil.getProperty(env, options, "lastChunkHandling");
		if(v==RuntimeUtil.UNDEFINED) {
			return LastChunkHandling.LOOSE;
		}
		if(isStringPrimitive(env, v)) {
			if("loose".equals(v)) {
				return LastChunkHandling.LOOSE;
			}
			if("strict".equals(v)) {
				return LastChunkHandling.STRICT;
			}
			if("stop-before-partial".equals(v)) {
				return LastChunkHandling.STOP_BEFORE_PARTIAL;
			}
		}
		throw RuntimeUtil.typeError("lastChunkHandling must be \"loose\", \"strict\" or \"stop-before-partial\"");
	}

	static boolean readOmitPaddingOption(JSEnvironment env, Object optionsArg) {
		Object options = requireOptionsObject(env, optionsArg);
		if(options==null) {
			return false;
		}
		Object v = RuntimeUtil.getProperty(env, options, "omitPadding");
		if(v==RuntimeUtil.UNDEFINED) {
			return false;
		}
		return RuntimeUtil.toBoolean(v);
	}
}
