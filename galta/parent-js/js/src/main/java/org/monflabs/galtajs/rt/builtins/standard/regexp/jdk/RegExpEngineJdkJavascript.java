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
package org.monflabs.galtajs.rt.builtins.standard.regexp.jdk;

import java.util.function.BiFunction;
import java.util.regex.Matcher;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.node.literal.ASTLiteral;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.standard.regexp.RegExp;
import org.monflabs.galtajs.rt.builtins.standard.regexp.RegExpEngine;

/**
 * RegExp using the Java engine emulating the JavaScript one, as best as it can.
 */
public class RegExpEngineJdkJavascript extends RegExpEngineJdk {
	
	private static BiFunction<JSEnvironment,RegExp,RegExpEngine> factory =
			(env,regexp) -> new RegExpEngineJdkJavascript(env, regexp);
	public static BiFunction<JSEnvironment,RegExp,RegExpEngine> factory() {
		return factory;
	}
	
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    // whitespace character class - also gets bom ufeff
    private static final String wscc = "\\u0009-\\u000d\\u0020\\u0085\\u00a0\\u1680\\u180e\\u2000\\u2028\\u2029\\u202f\\u205f\\u3000\\ufeff";

    // JavaScript requires capture groups to be cleared when inside
    // a repeating atom.  JUR will only overwrite groups if they match again
    // JUR also (maybe bug) matches capture groups in a negative lookahead
    // To fix this, we look at capture groups and even if JUR has a match,
    // we return null if we detect that capture index is before previous,
    // or within neg lookahead
//    private boolean[] validCapture;
    // bitset showing which capture groups are in neg lookahead
    private long negLookCapBs;

    
    public RegExpEngineJdkJavascript(JSEnvironment env, RegExp regExp) {
    	super(env,regExp);

    	// Unicode Sets mode ('v' flag) requires special character class parsing
    	// that is not yet fully implemented
    	if (regExp.isUnicodeSets()) {
    	    throw org.monflabs.galtajs.rt.RuntimeUtil.syntaxError(
    	        "Unicode Sets mode (v flag) is not yet fully implemented. " +
    	        "Please use the u flag for unicode mode.");
    	}
    }
    
    @Override
	public boolean isValidGroup(Matcher matcher, int group) {
    	// PHIL: this doesn't seem to be valid anymore in Java 17?
    	// This implementation fails the Rhino ecma_2 tests 
        return true;
//        // remove any capture groups that were not cleared in a repetition,
//        // also remove any groups that are inside neglookaheads
//        if (validCapture == null) {
//            validCapture = new boolean[matcher.groupCount() + 1];
//            validCapture[0] = true;
//            int lastCaptureStart = 0;
//            for (int i = 1; i < validCapture.length; i++) {
//                if ((negLookCapBs & (1 << i)) == 0
//                        && matcher.start(i) >= lastCaptureStart) {
//                    validCapture[i] = true;
//                    lastCaptureStart = matcher.start(i);
//                }
//            }
//        }
//        return validCapture[group];
    }

    @Override
	public String preProcessRegExp(String source, int flags) {
        // Translate regexp from js to java.
        // Some symbols have different translations depending on whether
        // they are used as regular characters (c), or inside a character
        // class in either positive (cc+) or negated (cc-) form.
        // 1.      octal => 'u00hh' (forbidden in unicode mode)
        // 2       '\v'  => '\x0b'
        // 3       '[^]' => '[\s\S]'
        //         '[]'  => '[^\s\S]'
        // 4.      '\b':
        //   c)          => '\b'
        //   cc+-)       => '\x08'
        // 5.      '\s':
        //   c)          => '[u...]'
        //   cc+-)       => 'u...'
        // 6.      '\S':
        //   c)          => '[^u...]
        //   cc+)        => '&&[^u...]'
        //   cc-)        => '[u...]'
        // 7.      '[':
        //   cc+-)       => '\['
        // 8.      '{'   => '\{' if not matching '{n}', '{n,}' or '{n,n}'

        // Fast path: a source with none of the syntax characters that this
        // switch actually transforms is byte-for-byte identical between JS
        // and Java regex, so skip the char-by-char rewrite entirely. Any of
        // '\\ [ ] ( ) { ^ $ .' can trigger a translation (escapes, character
        // classes, capture bookkeeping, quantifier disambiguation, anchors,
        // dot handling); anything else falls through to a plain append.
        if (isTrivialSource(source)) {
            return source;
        }

        boolean unicodeMode = getRegExp().isUnicode() || getRegExp().isUnicodeSets();
        boolean dotAllMode = getRegExp().isDotAll();
        boolean multilineMode = getRegExp().isMultiline();

        StringBuilder javaUtilRegex = new StringBuilder();
        boolean inCC = false; // inside character class
        boolean negCC = false; // negated character class
        boolean inNegLook = false; // negative lookahead

        int negLookBrackets = 0;
        int captureCount = 0;

        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            switch (c) {
	            case '\\': // escape-sequence
	                if (source.length() <= i) { // dangling slash at LOOP_COUNT of regexp
	                    javaUtilRegex.append('\\');
	                    break;
	                }
	                c = source.charAt(++i);
	                switch (c) {
		                case '0': case '1': case '2': case '3': case '4':
		                case '5': case '6': case '7': case '8': case '9':
		                    // it is tricky to detect difference between backref and oct
		                    // if c between 1 and 'captureCount' it is a bref
		                    // if c > '7', it is literal '\\c'
		                    // else octal escape.  Parse 1 or 2 more octal chars
		                    int cdec = c - '0';
		                    // backref
		                    if ((cdec >= 1 && cdec <= 9/*captureCount*/)) {
		                        // 6. Remove invalid backreferences to neg lookaheads
		                        if (inNegLook || (negLookCapBs & (1 << cdec)) == 0) {
		                        	// PHIL: an unset (not matching) capturing group is treated as null in Java while an empty string in JavaScript
		                        	// So we instead use (?:\1|) instead of \1
		                            // javaUtilRegex.append("(?:\\").append(c).append("|)");
		                        	// Doesn't see to work all the time though
		                            javaUtilRegex.append('\\').append(c);
		                        }

		                    // literal '\\c'
		                    } else if (cdec > 7) {
		                        javaUtilRegex.append('\\').append('\\').append(c);

		                    // octal   => 'u00hh' (but forbidden in unicode mode)
		                    } else {
		                        // In unicode mode, octal escapes (except \0 not followed by digit) are forbidden
		                        if (unicodeMode) {
		                            // \0 followed by non-digit is allowed
		                            if (c == '0' && (i + 1 >= source.length() || !Character.isDigit(source.charAt(i + 1)))) {
		                                javaUtilRegex.append("\\u0000");
		                            } else {
		                                throw RuntimeUtil.syntaxError("Octal escapes are not allowed in unicode mode");
		                            }
		                        } else {
			                        int num = cdec;
			                        // if first char 0, 1, 2, 3, parse 2 more, else 1 more
			                        int oneOrTwo = c > '3' ? 1 : 2;
			                        for (int octIndex = 0; octIndex < oneOrTwo
			                                && i+1 < source.length(); octIndex++) {

			                            cdec = source.charAt(++i) - '0';
			                            // if non-octal char, then just use what we have
			                            if (cdec < 0 || cdec > 7) {
			                                i--; // backup main index 'i'
			                                break;
			                            }
			                            num = (num << 3) | cdec;
			                        }
			                        // convert num to unicode escape
			                        char[] u4 = new char[4];
			                        for (int u4pos = 3; u4pos >= 0; u4pos--) {
			                            u4[u4pos] = HEX[num & 0xf];
			                            num >>= 4;
			                        }
			                        javaUtilRegex.append("\\u").append(u4);
		                        }
		                    }
		                    break;
		                case 'u': // Unicode escape
		                    if (unicodeMode && i + 1 < source.length() && source.charAt(i + 1) == '{') {
		                        // \ u{...} syntax in unicode mode
		                        i += 2; // skip 'u{'
		                        //int startBrace = i;
		                        StringBuilder hexDigits = new StringBuilder();
		                        while (i < source.length() && source.charAt(i) != '}') {
		                            char hexChar = source.charAt(i);
		                            if (ASTLiteral.hexval(hexChar) < 0) {
		                                throw RuntimeUtil.syntaxError("Invalid unicode escape \\u{...}");
		                            }
		                            hexDigits.append(hexChar);
		                            i++;
		                        }
		                        if (i >= source.length() || hexDigits.length() == 0) {
		                            throw RuntimeUtil.syntaxError("Invalid unicode escape \\u{...}");
		                        }
		                        // Parse the code point
		                        int codePoint = Integer.parseInt(hexDigits.toString(), 16);
		                        if (codePoint > 0x10FFFF) {
		                            throw RuntimeUtil.syntaxError("Unicode code point out of range");
		                        }
		                        // Convert to Java regex format
		                        if (codePoint <= 0xFFFF) {
		                            javaUtilRegex.append(String.format("\\u%04x", codePoint));
		                        } else {
		                            // Use supplementary characters
		                            String chars = new String(Character.toChars(codePoint));
		                            for (char ch : chars.toCharArray()) {
		                                javaUtilRegex.append(String.format("\\u%04x", (int)ch));
		                            }
		                        }
		                    } else if (i + 4 < source.length()) {
		                        // \ uXXXX syntax
								int c1 = ASTLiteral.hexval(source.charAt(i + 1));
								int c2 = ASTLiteral.hexval(source.charAt(i + 2));
								int c3 = ASTLiteral.hexval(source.charAt(i + 3));
								int c4 = ASTLiteral.hexval(source.charAt(i + 4));
								if(c1>=0 && c2>=0 && c3>=0 && c4>=0) {
				                    javaUtilRegex.append("\\u");
				                    break;
								} else if (unicodeMode) {
								    // In unicode mode, invalid \ u is an error
								    throw RuntimeUtil.syntaxError("Invalid unicode escape");
								}
								// Without unicode mode, treat as literal 'u'
		                        javaUtilRegex.append("u");
							} else {
							    if (unicodeMode) {
							        // In unicode mode, incomplete \ u is an error
							        throw org.monflabs.galtajs.rt.RuntimeUtil.syntaxError("Invalid unicode escape");
							    }
							    // Without unicode mode, treat as literal 'u'
		                        javaUtilRegex.append("u");
							}
		                    break;
		                case 'v': // 2.* '\v' => '\x0b'
		                    javaUtilRegex.append("\\x0b");
		                    break;
		                case 'b':
		                    if (inCC) { // 4.cc+- '\b' => '\x08'
		                        javaUtilRegex.append("\\x08");
		                    } else {    // 4.c    '\b' => '\b'
		                        javaUtilRegex.append("\\b");
		                    }
		                    break;
		                case 's':
		                    if (inCC) { // 5.cc+-) '\s' => 'u...'
		                        javaUtilRegex.append(wscc);
		                    } else { // 5.c) '\s' => '[u...]'
		                        javaUtilRegex.append('[').append(wscc).append(']');
		                    }
		                    break;
		                case 'S':
		                    if (inCC) {
		                        if (negCC) { // 6.cc-) '\S' => '[u...]'
		                            javaUtilRegex.append('[').append(wscc).append(']');
		                        } else { // 6.cc+) '\S' => '&&[^u...]'
		                            javaUtilRegex.append("[^").append(wscc).append(']');
		                        }
		                    } else { // 6.c) '\S' => '[^u...]'
		                        javaUtilRegex.append('[').append('^').append(wscc).append(']');
		                    }
		                    break;
		                case 'p': case 'P':
		                    if (unicodeMode && i + 1 < source.length() && source.charAt(i + 1) == '{') {
		                        i += 2; // skip past '{'
		                        int braceStart = i;
		                        while (i < source.length() && source.charAt(i) != '}') {
		                            i++;
		                        }
		                        if (i >= source.length()) {
		                            throw RuntimeUtil.syntaxError("Invalid Unicode property escape");
		                        }
		                        String propName = source.substring(braceStart, i);
		                        boolean negated = (c == 'P');
		                        String translated = UnicodeProperties.translate(propName, negated, inCC);
		                        if (translated == null) {
		                            throw RuntimeUtil.syntaxError("Invalid Unicode property escape: \\{0}'{'{1}'}'", String.valueOf(c), propName);
		                        }
		                        javaUtilRegex.append(translated);
		                    } else if (unicodeMode) {
		                        throw RuntimeUtil.syntaxError("Invalid Unicode property escape");
		                    } else {
		                        // In non-unicode mode, \p and \P are identity escapes for 'p'/'P'
		                        javaUtilRegex.append(c);
		                    }
		                    break;
		                default:
		                    if (Character.isLetter(c) && !isJsRegexEscape(c)) {
		                        if (unicodeMode) {
		                            throw RuntimeUtil.syntaxError("Invalid escape sequence: \\{0}", String.valueOf(c));
		                        }
		                        // Non-unicode: identity escape - output literal character
		                        javaUtilRegex.append(c);
		                    } else {
		                        javaUtilRegex.append('\\').append(c);
		                    }
		                }
	                break;
	            case '[':
	                if (inCC) { // 7.cc+- '[' => '\['
	                    javaUtilRegex.append("\\[");
	                    break;
	                }
	
	                // start of character class, check if negated
	                if (source.length() > i+1 && source.charAt(i+1) == '^') {
	                    // check for special match of '[^]'
	                    if (source.length() > i+2 && source.charAt(i+2) == ']') {
	                        // 3.* '[^]' => '[\s\S]'
	                        javaUtilRegex.append("[\\s\\S]");
	                        i += 2;
	                        break;
	                    }
	                    negCC = true;
	                } else if (source.length() > i+1 && source.charAt(i+1) == ']') {
	                    // 3.* '[]' => '[^\s\S]'
	                    javaUtilRegex.append("[^\\s\\S]");
	                    i += 1;
	                    break;
	                }
	                inCC = true;
	                javaUtilRegex.append('[');
	                break;
	            case ']':
	                // LOOP_COUNT of character class
	                inCC = false;
	                negCC = false;
	                javaUtilRegex.append(']');
	                break;
	            case '(':
	                if (i+1 < source.length() && source.charAt(i+1) == '?') {
	                    if (i+2 < source.length() && source.charAt(i+2) == '!') {
	                        inNegLook = true;
	                        negLookBrackets++;
	                    }
	                } else {
	                    captureCount++;
	                    if (inNegLook) {
	                        // set neg look bitset, only support 63 captures
	                        if (captureCount < 64) {
	                            negLookCapBs |= (1L << captureCount);
	                        }
	                        negLookBrackets++;
	                    }
	                }
	                javaUtilRegex.append('(');
	                break;
	            case ')':
	                if (inNegLook) {
	                    negLookBrackets--;
	                    inNegLook = negLookBrackets > 0;
	                }
	                javaUtilRegex.append(')');
	                break;
	            case '{':
	                // 8.      '{'   => '\{' if not matching '{n}', '{n,}' or '{n,n}'
	                if (inCC) {
	                    javaUtilRegex.append(c); // leave cc as-is
	                    break;
	                }
	
	                int j = i + 1;
	                int numDigits = numDigits(source,j);
	                j += numDigits; // j points to char after digits
	                if (numDigits == 0 || j >= source.length()) {
	                    // no digits, or at LOOP_COUNT of source - escape
	                    javaUtilRegex.append("\\{");
	                    break;
	                }
	
	                if (source.charAt(j) == '}') {
	                    javaUtilRegex.append(c); // valid format '{n}'
	                    break;
	                } else if (source.charAt(j) != ',') {
	                    // bad format - escape
	                    javaUtilRegex.append("\\{");
	                    break;
	                }
	
	                // we have parsed '{n,' - now read zero or more digits and '}'
	                j++; // move past ','
	                j += numDigits(source,j); // j points to char after digits
	                if (j < source.length() && source.charAt(j) == '}') {
	                    javaUtilRegex.append('{'); // valid format '{n,n}'
	                } else {
	                    // at LOOP_COUNT of source, or not closing bracket - escape
	                    javaUtilRegex.append("\\{");
	                }
	
	                break;
	            case '^':
	            	if(inCC) {
	            		javaUtilRegex.append('^');
	            	} else if(multilineMode) {
	            		javaUtilRegex.append("(?:(?<=\\n|\\r|\\u2028|\\u2029)|\\A)");
	            	} else {
	            		javaUtilRegex.append("\\A");
	            	}
	                break;
	            case '$':
	            	if(inCC) {
	            		javaUtilRegex.append('$');
	            	} else if(multilineMode) {
	            		javaUtilRegex.append("(?=\\r\\n|[\\n\\r\\u2028\\u2029]|\\z)");
	            	} else {
	            		javaUtilRegex.append("\\z");
	            	}
	                break;
	            case '.':
	            	if(inCC) {
	            		javaUtilRegex.append('.');
	            	} else if(dotAllMode) {
	            		javaUtilRegex.append("[\\s\\S]");
	            	} else {
	            		javaUtilRegex.append("[^\\n\\r\\u2028\\u2029]");
	            	}
	                break;
	            default:
	                javaUtilRegex.append(c);
            }
        }
        return javaUtilRegex.toString();
    }

    // A source containing none of these characters cannot exercise any
    // branch of the JS-to-Java translator, so it can be handed to
    // Pattern.compile unchanged. Cheap linear scan; the common case in
    // hot code (e.g. the SunSpider `string-unpack-code` benchmark's
    // `\\b<id>\\b` patterns are NOT trivial because of the '\\'). Also
    // catches most user-authored plain-text patterns.
    private static boolean isTrivialSource(String source) {
        for (int i = 0, n = source.length(); i < n; i++) {
            char c = source.charAt(i);
            switch (c) {
                case '\\': case '[': case ']': case '(': case ')':
                case '{':  case '^': case '$': case '.':
                    return false;
                default:
            }
        }
        return true;
    }

    private int numDigits(String source, int i) {
        int start = i;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c < '0' || c > '9') {
                break;
            }
            i++;
        }
        return i - start;
    }

    private static boolean isJsRegexEscape(char c) {
        // Letters that are valid JavaScript regex escapes (per ECMA-262)
        // Note: s,S,b,v,u,0-9 are handled by explicit cases before default
        switch (c) {
            case 'd': case 'D': case 'w': case 'W': // character classes
            case 'n': case 'r': case 't': case 'f': // control chars
            case 'c': // control escape \cX
            case 'x': // hex escape \xHH
            case 'k': // named backreference \k<name>
            case 'B': // non-word-boundary
                return true;
            default:
                return false;
        }
    }
}