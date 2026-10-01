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
package org.monflabs.galtajs.node.literal;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.MessageFormat;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSException;
import org.monflabs.galtajs.JSParseException;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.jsonfactory.internal.JSObjectInternal;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.optimizer.JSOptimizerContext;
import org.monflabs.galtajs.parser.ParseException;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.transpiler.JSTranspilerOptions;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.galtajs.util.JavaBuilder;
import org.monflabs.json.JsonUtil;
import org.monflabs.util.DtoA;
import org.monflabs.util.StringFormat;
import org.monflabs.util.StringUtil;


/**
 * Constant Node.
 */
public class ASTLiteral extends ASTNode {

	private Object value;
	private String rawImage;
	private boolean legacyOctalLiteral;

	public ASTLiteral(Token t, Object value) {
		super(t);
		this.value = value;
		if(value instanceof CharSequence cs) {
			this.value = cs.toString();
		}
		this.rawImage = t!=null ? t.image : null;
	}

	// Set by the parser when this literal's source text is a
	// LegacyOctalIntegerLiteral/NonOctalDecimalIntegerLiteral (e.g. "017",
	// "019") - see ASTLiteral.parseInteger()'s legacyOctalOut parameter.
	public void setLegacyOctalLiteral(boolean legacyOctalLiteral) {
		this.legacyOctalLiteral = legacyOctalLiteral;
	}

	@Override
	protected void init(InitContext initContext) {
		// Early SyntaxError, per spec: a LegacyOctalIntegerLiteral/
		// NonOctalDecimalIntegerLiteral is forbidden in strict-mode code.
		// isGenuinelyStrict() (never blended with JSEnvironment's parse-
		// dialect toggle) is the right signal here - LiteralTest.js relies on
		// octal literals still working under that toggle alone (no real
		// "use strict"), only a genuine directive (or, for eval'd text, the
		// calling context's genuine strictness) should reject this syntax.
		if(legacyOctalLiteral && initContext.isGenuinelyStrict()) {
			throw new JSParseException(null,this,value instanceof String ? "Octal escape sequences are not allowed in strict mode" : "Octal literals are not allowed in strict mode");
		}
		super.init(initContext);
	}

	// Whether a string literal's source text has a LegacyOctalEscapeSequence
	// or a NonOctalDecimalEscapeSequence ("\\1", "\\08", "\\8"): an early
	// SyntaxError in strict mode code (see init()).
	public static boolean hasLegacyEscape(String image) {
		for(int i=0; i<image.length()-1; i++) {
			if(image.charAt(i)=='\\') {
				char c = image.charAt(i+1);
				if((c>='1' && c<='9') || (c=='0' && i+2<image.length() && Character.isDigit(image.charAt(i+2)))) {
					return true;
				}
				i++;
			}
		}
		return false;
	}

	// A Directive Prologue entry ("use strict" being the only one GaltaJS
	// acts on) must be written verbatim - no escape sequences or line
	// continuations - even though those would evaluate to the same string
	// value. Checked against the raw source token text, not getValue().
	public boolean isLiteralDirectiveText(String text) {
		if(rawImage==null || rawImage.length()!=text.length()+2) {
			return false;
		}
		char q = rawImage.charAt(0);
		if((q!='"' && q!='\'') || rawImage.charAt(rawImage.length()-1)!=q) {
			return false;
		}
		return rawImage.regionMatches(1,text,0,text.length());
	}
	
	// Only called from unary expression
	public boolean astPlus() {
		if(value instanceof Number n) {
			if(n instanceof BigInteger || n instanceof BigDecimal) {
				return false;
			}
			if(n instanceof Integer v && v==0) {
				value = +0.0;
			} else if(n instanceof Long v && v==0L) {
				value = +0.0;
			}
			return true;
		}
		return false;
	}
	public boolean astMinus() {
		if(value instanceof Number n) {
			if(n instanceof Integer v && v==0) {
				value = -0.0;
			} else if(n instanceof Long v && v==0L) {
				value = -0.0;
			} else {
				value = RuntimeUtil.minus(n);
			}
			return true;
		}
		return false;
	}	
	
	public Object getValue() {
		return value;
	}

	public void setValue(Object value) {
		this.value = value;
	}
	
	@Override
	public String getNodeString() {
		if(value instanceof String s && s.length()>64) {
			return encodeJavaLiteral(s.substring(0,64)+"...");
		}
		return encodeJavaLiteral(value);
	}

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		result.setValue(value);
		return Signal.NONE;
	}

	@Override
	public Object evaluateValue(JSInterpretedRuntimeContext context, JSResult result) {
		return value;
	}

	@Override
	public boolean isConstant(JSOptimizerContext context) {
		return true;
	}	

	@Override
	public STATEMENT_TYPE getStatementType() {
		return STATEMENT_TYPE.LITERAL;
	}	

    @Override
	public JSType getReturnedType() {
		if(value==null) {
			return JSType.NULL;
		} else if(value instanceof Boolean) {
			return JSType.BOOLEAN;
		} else if(value instanceof Byte) {
			return JSType.BYTE;
		} else if(value instanceof Short) {
			return JSType.SHORT;
		} else if(value instanceof Integer) {
			return JSType.INT;
		} else if(value instanceof Long) {
			return JSType.LONG;
		} else if(value instanceof Float) {
			return JSType.FLOAT;
		} else if(value instanceof Double) {
			return JSType.DOUBLE;
		} else if(value instanceof BigInteger) {
			return JSType.BIGINTEGER;
		} else if(value instanceof BigDecimal) {
			return JSType.BIGDECIMAL;
		} else if(value instanceof String) {
			return JSType.STRING;
		}
		return JSType.UNKNOWN;
    }
    

	////////////////////////////////////////////////////////////////////////////////////////
	// Integer Parsing
	////////////////////////////////////////////////////////////////////////////////////////

	public static Number parseInteger(JSEnvironment env, String s) throws ParseException {
		return parseInteger(env, s, null);
	}

	// legacyOctalOut[0] (if non-null) is set to true when the source text is a
	// LegacyOctalIntegerLiteral/NonOctalDecimalIntegerLiteral (a bare leading
	// "0" followed by another digit, e.g. "017"/"019") - a syntax form that's
	// an early SyntaxError in strict-mode code (checked by ASTLiteral.init()),
	// regardless of the zeroOctal/env-dialect gate below, which only controls
	// how the NUMERIC VALUE is computed (kept unchanged for compatibility).
	public static Number parseInteger(JSEnvironment env, String s, boolean[] legacyOctalOut) throws ParseException {
		boolean zeroOctal = !RuntimeUtil.isStrictMode();
		// Integer literals (decimal, hex, octal, binary) never have an exponent
		// part, so "e"/"E" next to a separator is just a hex digit (e.g. 0xe_e),
		// not an exponent marker - only true decimal/float literals need that check.
		s = removeNumericSeparator(s);
		// Check for a suffix
		char lastChar = s.charAt(s.length()-1);
		if(lastChar=='i' || lastChar=='I') {
			s = s.substring(0,s.length()-1);
			if(s.startsWith("0x") || s.startsWith("0X")) {
				return Integer.parseInt(s.substring(2),16);
			}
			if(s.startsWith("0b") || s.startsWith("0B")) {
				return Integer.parseInt(s.substring(2),2);
			}
			if(s.startsWith("0o") || s.startsWith("0O")) {
				return Integer.parseInt(s.substring(2),8);
			}
			if(s.length()>1 && s.startsWith("0")) {
				if(legacyOctalOut!=null) {
					legacyOctalOut[0] = true;
				}
				if(zeroOctal) {
					return Integer.parseInt(s.substring(1),8);
				}
			}
			return Integer.parseInt(s);
		}
		if(lastChar=='l' || lastChar=='L') {
			s = s.substring(0,s.length()-1);
			if(s.startsWith("0x") || s.startsWith("0X")) {
				return Long.parseLong(s.substring(2),16);
			}
			if(s.startsWith("0b") || s.startsWith("0B")) {
				return Long.parseLong(s.substring(2),2);
			}
			if(s.startsWith("0o") || s.startsWith("0O")) {
				return Long.parseLong(s.substring(2),8);
			}
			if(s.length()>1 && s.startsWith("0")) {
				if(legacyOctalOut!=null) {
					legacyOctalOut[0] = true;
				}
				if(zeroOctal) {
					return Long.parseLong(s.substring(1),8);
				}
			}
			return Long.parseLong(s);
		}
		if(lastChar=='n' || lastChar=='N') {
			s = s.substring(0,s.length()-1);
			if(s.startsWith("0x") || s.startsWith("0X")) {
				return new BigInteger(s.substring(2),16);
			}
			if(s.startsWith("0b") || s.startsWith("0B")) {
				return new BigInteger(s.substring(2),2);
			}
			if(s.startsWith("0o") || s.startsWith("0O")) {
				return new BigInteger(s.substring(2),8);
			}
			if(s.length()>1 && s.startsWith("0")) {
				// A BigInt literal never has a legacy octal (or leading 0) form
				throw new JSException(null,"Invalid BigInt literal {0}n", s);
			}
			return new BigInteger(s);
		}

		// We use the factory
		if(s.startsWith("0x") || s.startsWith("0X")) {
			return env.getJsonFactory().parseInteger(s.substring(2),16);
		}
		if(s.startsWith("0b") || s.startsWith("0B")) {
			return env.getJsonFactory().parseInteger(s.substring(2),2);
		}
		if(s.startsWith("0o") || s.startsWith("0O")) {
			return env.getJsonFactory().parseInteger(s.substring(2),8);
		}
		if(s.length()>1 && s.startsWith("0")) {
			if(legacyOctalOut!=null) {
				legacyOctalOut[0] = true;
			}
			if(zeroOctal) {
				// 077 = 63
				// 079 = 79
				try {
					return env.getJsonFactory().parseInteger(s.substring(1),8);
				} catch(Exception e) {
					return env.getJsonFactory().parseInteger(s.substring(1),10);
				}
			}
		}
		return env.getJsonFactory().parseInteger(s);
	}
	
	private static boolean isSeparatedDigit(char c, boolean hex) {
		return (c>='0' && c<='9') || (hex && ((c>='a' && c<='f') || (c>='A' && c<='F')));
	}

	private static String removeNumericSeparator(String s) {
		if(s.indexOf('_')>=0) {
			// A NumericLiteralSeparator sits between two digits of the same
			// digit sequence: not after a 0x/0o/0b prefix, not next to "."/"e"/a
			// suffix, not doubled, and never in a literal with a leading 0
			// (legacy octal "0_7", "00_0", or "0_1").
			boolean prefixed = s.length()>1 && s.charAt(0)=='0' && "xXoObB".indexOf(s.charAt(1))>=0;
			boolean hex = prefixed && (s.charAt(1)=='x' || s.charAt(1)=='X');
			boolean leadingZero = !prefixed && s.length()>1 && s.charAt(0)=='0' && (s.charAt(1)=='_' || Character.isDigit(s.charAt(1)));
			for(int i=0; i<s.length(); i++) {
				if(s.charAt(i)=='_') {
					boolean ok = !leadingZero && i>(prefixed ? 2 : 0) && i+1<s.length()
							&& isSeparatedDigit(s.charAt(i-1), hex) && isSeparatedDigit(s.charAt(i+1), hex);
					if(!ok) {
						throw new JSException(null,"Invalid numeric separator position");
					}
				}
			}
			StringBuilder b = new StringBuilder();
			int l = s.length();
			for(int i=0; i<l; i++) {
				char c = s.charAt(i);
				if(c!='_') {
					b.append(c);
				}
			}
			s = b.toString();
		}
		return s;
	}

	
	////////////////////////////////////////////////////////////////////////////////////////
	// Decimal Parsing
	////////////////////////////////////////////////////////////////////////////////////////

	public static Number parseDecimal(JSEnvironment env, String s) throws ParseException {
		s = removeNumericSeparator(s);
		// Check for a suffix
		char lastChar = s.charAt(s.length()-1);
		if(lastChar=='d' || lastChar=='D') {
			s = s.substring(0,s.length()-1);
			return Double.parseDouble(s);
		} else if(lastChar=='f' || lastChar=='F') {
			s = s.substring(0,s.length()-1);
			return Float.parseFloat(s);
		} else if(lastChar=='m' || lastChar=='M') {
			if(env.supportBigDecimalLiteral()) {
				s = s.substring(0,s.length()-1);
				return new BigDecimal(s,env.getMathContext());
			} else {
				throw RuntimeUtil.syntaxError("Decimal syntax is not supported");
			}
		}
		
		Number n = env.getJsonFactory().parseDecimal(s);
		// Could happen when the number has exponents
		if(n instanceof Double) {
			if((double)n.intValue()==n.doubleValue()) {
				return n.intValue();
			}
			if(env.supportLongPromotion()) {
				if((double)n.longValue()==n.doubleValue()) {
					return n.longValue();
				}
			}
		}
		return n;
	}


	////////////////////////////////////////////////////////////////////////////////////////
	// Boolean Parsing
	////////////////////////////////////////////////////////////////////////////////////////

	public static Boolean parseBoolean(String s) throws ParseException {
		if(StringUtil.equals(s, "true")) {
			return Boolean.TRUE;
		}
		if(StringUtil.equals(s, "false")) {
			return Boolean.FALSE;
		}
		throw new ParseException(MessageFormat.format("Invalid Boolean value {0}",s));
	}
	
	
	////////////////////////////////////////////////////////////////////////////////////////
	// String Parsing
	////////////////////////////////////////////////////////////////////////////////////////

	public static int hexval(char c) {
		switch (c) {
		case '0':
			return 0;
		case '1':
			return 1;
		case '2':
			return 2;
		case '3':
			return 3;
		case '4':
			return 4;
		case '5':
			return 5;
		case '6':
			return 6;
		case '7':
			return 7;
		case '8':
			return 8;
		case '9':
			return 9;

		case 'a':
		case 'A':
			return 10;
		case 'b':
		case 'B':
			return 11;
		case 'c':
		case 'C':
			return 12;
		case 'd':
		case 'D':
			return 13;
		case 'e':
		case 'E':
			return 14;
		case 'f':
		case 'F':
			return 15;
		}
		return -1;// must never reach this point
	}

	private static int octval(char c) {
		switch (c) {
			case '0':
				return 0;
			case '1':
				return 1;
			case '2':
				return 2;
			case '3':
				return 3;
			case '4':
				return 4;
			case '5':
				return 5;
			case '6':
				return 6;
			case '7':
				return 7;
		}

		return -1;// must never reach this point
	}
	
	public static String parseString(String s) {
		return _parseString(s, true, !RuntimeUtil.isStrictMode());
	}
	public static String parseString(String s, boolean removeQuotes) {
		return _parseString(s, removeQuotes, !RuntimeUtil.isStrictMode());
	}

	public static String parseLiteralString(String s) {
		return _parseString(s, true, true);
	}
	public static String parseLiteralString(String s, boolean removeQuotes) {
		return _parseString(s, removeQuotes, true);
	}
	
	/**
	 * Decodes an IdentifierName token: its only escapes are the lexer's
	 * backslash-u XXXX and backslash-u {...} escapes, and each must denote a code point
	 * allowed at its position (ID_Start, "$" or "_" first; ID_Continue, "$",
	 * ZWNJ or ZWJ after), otherwise the name is a SyntaxError at parse time.
	 */
	public static String parseIdentifier(String s) {
		if(s.indexOf('\\')<0) {
			return s;
		}
		int length = s.length();
		StringBuilder sb = new StringBuilder(length);
		for (int i=0; i<length; ) {
			char c = s.charAt(i);
			if(c!='\\') {
				sb.append(c);
				i++;
				continue;
			}
			int cp = -1;
			int end = i;
			if(i+1<length && s.charAt(i+1)=='u') {
				if(i+2<length && s.charAt(i+2)=='{') {
					int j = i+3;
					long v = 0;
					while(j<length && hexval(s.charAt(j))>=0) {
						v = Math.min(v*16 + hexval(s.charAt(j)), 0x110000);
						j++;
					}
					if(j>i+3 && j<length && s.charAt(j)=='}') {
						cp = (int)v;
						end = j+1;
					}
				} else if(i+6<=length) {
					int v = 0;
					int j = i+2;
					for(; j<i+6 && hexval(s.charAt(j))>=0; j++) {
						v = v*16 + hexval(s.charAt(j));
					}
					if(j==i+6) {
						cp = v;
						end = j;
					}
				}
			}
			if(cp<0 || cp>Character.MAX_CODE_POINT) {
				throw RuntimeUtil.syntaxError("Invalid Unicode escape sequence in identifier {0}", s);
			}
			if(!(sb.length()==0 ? isIdentifierStart(cp) : isIdentifierPart(cp))) {
				throw RuntimeUtil.syntaxError("Invalid identifier character U+{0} in {1}", String.format("%04X", cp), s);
			}
			sb.appendCodePoint(cp);
			i = end;
		}
		return sb.toString();
	}

	/**
	 * {@link #parseIdentifier(String)} for an IdentifierReference, a
	 * BindingIdentifier or a LabelIdentifier: a reserved word cannot be
	 * spelled with escapes to use it there (the lexer only recognizes the
	 * unescaped keyword, so the escaped spelling reaches here as a name).
	 */
	public static String parseBindingIdentifier(String s) {
		String id = parseIdentifier(s);
		if(id!=s && ESCAPE_FREE_RESERVED_WORDS.contains(id)) {
			throw RuntimeUtil.syntaxError("Keyword must not contain escaped characters: {0}", s);
		}
		return id;
	}

	private static final java.util.Set<String> ESCAPE_FREE_RESERVED_WORDS = java.util.Set.of(
			"break", "case", "catch", "class", "const", "continue", "debugger", "default", "delete",
			"do", "else", "enum", "export", "extends", "false", "finally", "for", "function", "if",
			"import", "in", "instanceof", "new", "null", "return", "super", "switch", "this", "throw",
			"true", "try", "typeof", "var", "void", "while", "with");

	// Java's identifier predicates follow UAX #31 like ECMAScript's ID_Start/
	// ID_Continue, except that they keep U+2E2F VERTICAL TILDE (a Pattern_Syntax
	// character, excluded from both) and that isUnicodeIdentifierPart() also
	// accepts the "ignorable" format and control characters.
	private static final int VERTICAL_TILDE = 0x2E2F;

	// A code point the JDK's Unicode version does not assign yet is accepted:
	// newer Unicode versions (test262 has 15.1 to 17.0 identifier tests) keep
	// adding letters, and the lexer is equally permissive for them.
	private static boolean isIdentifierStart(int cp) {
		return cp=='$' || cp=='_' || (cp!=VERTICAL_TILDE && (Character.isUnicodeIdentifierStart(cp) || isUnassigned(cp)));
	}

	private static boolean isIdentifierPart(int cp) {
		// U+30FB and U+FF65 (katakana middle dots) joined Other_ID_Continue in Unicode 15.1
		return cp=='$' || cp==0x200C || cp==0x200D || cp==0x30FB || cp==0xFF65
				|| (cp!=VERTICAL_TILDE && ((Character.isUnicodeIdentifierPart(cp) && !Character.isIdentifierIgnorable(cp)) || isUnassigned(cp)));
	}

	private static boolean isUnassigned(int cp) {
		return Character.getType(cp)==Character.UNASSIGNED;
	}

	
	private static String _parseString(String s, boolean removeQuotes, boolean allowOctal) {
		int start = 0;
		int length = s.length();
		if(removeQuotes) {
			if ((s.startsWith("\"") && s.endsWith("\""))
					|| (s.startsWith("'") && s.endsWith("'"))) {
				start++; length--;
			}
		}
		StringBuilder sb = new StringBuilder(length);
		for (int i=start; i<length; i++) {
			char c = s.charAt(i);
			if ((c == '\\') && (i + 1 < length)) {
				i++;
				c = s.charAt(i);
				if (c == 'n')
					c = '\n';
				else if (c == 'b')
					c = '\b';
				else if (c == 'f')
					c = '\f';
				else if (c == 'r')
					c = '\r';
				else if (c == 't')
					c = '\t';
				else if (c == 'v')
					c = '\u000B';
				else if (c == 'x') {
					// A malformed HexEscapeSequence is a SyntaxError
					int c1 = i+2<length ? hexval(s.charAt(i + 1)) : -1;
					int c2 = i+2<length ? hexval(s.charAt(i + 2)) : -1;
					if(c1<0 || c2<0) {
						throw RuntimeUtil.syntaxError("Invalid hexadecimal escape sequence");
					}
					c = (char) (c1<< 4 | c2);
					i += 2;
				} else if (c == 'u' && i+1<length && s.charAt(i+1)=='{') {
					int j = i+2;
					long cp = 0;
					while(j<length && hexval(s.charAt(j))>=0) {
						cp = Math.min((cp<<4) | hexval(s.charAt(j)), 0x110000);
						j++;
					}
					if(j<length && s.charAt(j)=='}' && j>i+2 && cp<=Character.MAX_CODE_POINT) {
						sb.appendCodePoint((int)cp);
						i = j;
						continue;
					}
					throw RuntimeUtil.syntaxError("Invalid Unicode escape sequence");
				} else if (c == 'u') {
					// A malformed UnicodeEscapeSequence is a SyntaxError
					int c1 = i+4<length ? hexval(s.charAt(i + 1)) : -1;
					int c2 = i+4<length ? hexval(s.charAt(i + 2)) : -1;
					int c3 = i+4<length ? hexval(s.charAt(i + 3)) : -1;
					int c4 = i+4<length ? hexval(s.charAt(i + 4)) : -1;
					if(c1<0 || c2<0 || c3<0 || c4<0) {
						throw RuntimeUtil.syntaxError("Invalid Unicode escape sequence");
					}
					c = (char) (c1 << 12 | c2 << 8 | c3 << 4 | c4);
					i += 4;
				} else if (c >= '0' && c <= '7') {
					if(!allowOctal) {
						throw RuntimeUtil.syntaxError("Octal escape not valid in this context");
					}
					int cc = octval(s.charAt(i));
					if ((i+1<length) && (s.charAt(i + 1) >= '0') && (s.charAt(i + 1) <= '7')) {
						i++;
						int cc1 = octval(s.charAt(i));
						if ( cc<=3 && (i+1<length) && (s.charAt(i + 1) >= '0') && (s.charAt(i + 1) <= '7')) {
							i++;
							int cc2 = octval(s.charAt(i));
							cc = cc*64 + cc1*8 + cc2;
						} else {
							cc = cc*8 + cc1;
						}
					}
					c = (char)cc;
				// |	< #LINE_CONTINUATION:
				//	   "\\" ( "\r\n" | "\n\r" | "\r" | "\n" | "\u2028" |"\u2029")
				} else if(c=='\r') {
					// Just String continuation
					if(i+1<s.length() && s.charAt(i+1)=='\n') {
						i++;
					}
					continue;
				} else if(c=='\n') {
					// Just String continuation
					if(i+1<s.length() && s.charAt(i+1)=='\r') {
						i++;
					}
					continue;
				} else if (c=='\u2028' || c=='\u2029') {
					// Just String continuation
					continue;
				}
			}
			sb.append(c);
		}
		// We intern the literals to save memory and save hashcode computation
		// Since Java8, interned strings are in the heap, so this is not a problem anymore
		return sb.toString().intern();
	}

	// Cooked value of a template literal chunk (raw text between two `${`/backtick
	// boundaries), or null if it contains an escape sequence the spec forbids in
	// templates (any octal escape, \8, \9, or a malformed hex/unicode escape) -
	// unlike regular string literals, these never fall back to sloppy-mode octal
	// support. The caller decides what null means: an early SyntaxError for an
	// untagged template, or an undefined cooked value (raw text unaffected) for a
	// tagged one.
	public static String parseTemplateString(String s) {
		int length = s.length();
		StringBuilder sb = new StringBuilder(length);
		for (int i=0; i<length; i++) {
			char c = s.charAt(i);
			if (c=='\r') {
				// Unescaped LineTerminatorSequence: CR and CRLF both normalize to LF
				// (unlike an escaped line continuation, which is dropped entirely).
				if(i+1<length && s.charAt(i+1)=='\n') {
					i++;
				}
				sb.append('\n');
				continue;
			}
			if (c == '\\' && i+1 < length) {
				i++;
				c = s.charAt(i);
				if (c == 'n') c = '\n';
				else if (c == 'b') c = '\b';
				else if (c == 'f') c = '\f';
				else if (c == 'r') c = '\r';
				else if (c == 't') c = '\t';
				else if (c == 'v') c = '\u000B';
				else if (c == 'x') {
					if(i+2>=length) return null;
					int c1 = hexval(s.charAt(i+1));
					int c2 = hexval(s.charAt(i+2));
					if(c1<0 || c2<0) return null;
					c = (char)(c1<<4 | c2);
					i += 2;
				} else if (c == 'u' && i+1<length && s.charAt(i+1)=='{') {
					int j = i+2;
					int cp = 0;
					int digits = 0;
					while(j<length && s.charAt(j)!='}') {
						int v = hexval(s.charAt(j));
						if(v<0 || cp>0x10FFFF) return null;
						cp = cp*16 + v;
						digits++;
						j++;
					}
					if(digits==0 || cp>0x10FFFF || j>=length) return null;
					i = j;
					sb.appendCodePoint(cp);
					continue;
				} else if (c == 'u') {
					if(i+4>=length) return null;
					int c1 = hexval(s.charAt(i+1));
					int c2 = hexval(s.charAt(i+2));
					int c3 = hexval(s.charAt(i+3));
					int c4 = hexval(s.charAt(i+4));
					if(c1<0 || c2<0 || c3<0 || c4<0) return null;
					c = (char)(c1<<12 | c2<<8 | c3<<4 | c4);
					i += 4;
				} else if (c=='0' && !(i+1<length && s.charAt(i+1)>='0' && s.charAt(i+1)<='9')) {
					// Lone \0 (not followed by a digit) is the one octal-looking escape
					// templates still allow: it's just the NUL character.
					c = '\0';
				} else if (c>='0' && c<='9') {
					// Legacy octal escapes (\0<digit>, \1-\7) and \8/\9 are always
					// invalid in templates, even in non-strict code.
					return null;
				} else if (c=='\r') {
					if(i+1<length && s.charAt(i+1)=='\n') i++;
					continue;
				} else if (c=='\n') {
					if(i+1<length && s.charAt(i+1)=='\r') i++;
					continue;
				} else if (c==' ' || c==' ') {
					continue;
				}
			}
			sb.append(c);
		}
		return sb.toString().intern();
	}

	// TRV needs the same LineTerminatorSequence -> LF normalization as TV, but no
	// escape decoding at all (the raw text keeps its backslashes literally).
	public static String normalizeRawTemplateString(String s) {
		int length = s.length();
		StringBuilder sb = new StringBuilder(length);
		for(int i=0; i<length; i++) {
			char c = s.charAt(i);
			if(c=='\r') {
				if(i+1<length && s.charAt(i+1)=='\n') {
					i++;
				}
				c = '\n';
			}
			sb.append(c);
		}
		return sb.toString();
	}


    @Override
	public String transpileJavaExpression(JSTranspilerGeneratorContext jsContext) {
    	String encodedValue = encodeLiteral(jsContext,value);
    	if(encodedValue==null) {
    		super.transpileJavaExpression(jsContext);
    	}
    	return encodedValue;
    }
    
	public static String encodeLiteral(JSTranspilerGeneratorContext jsContext, Object value) {
		if(value instanceof RawJavaExpression raw) {
			return raw.javaExpr();
		}
		if(value instanceof Number) {
			if(value instanceof Integer i) {
				if(i==0) {
					return "ZERO";
				}
				if(i==1) {
					return "ONE";
				}
				if(i==-1) {
					return "MINUS_ONE";
				}
		        if(i>=-128 && i<=127) { // The cache in Integer, although it can be 
		        	return StringFormat.format("Integer.valueOf({0})",i.toString());
		        }
				//return Integer.toString(i);
			} else if(value instanceof Long l) {
		        if (l >= -128 && l <= 127) { // The cache in Long
		        	return StringFormat.format("Long.valueOf({0})",l.toString());
		        }
			} else if(value instanceof Double d) {
				if(d==0.0) {
					return RuntimeUtil.isNegativeZero(d) ? "MINUS_ZERO_DOUBLE" : "ZERO_DOUBLE";
				}
			} else if(value instanceof Float f) {
				if(f==0.0) {
					return RuntimeUtil.isNegativeZero(f) ? "MINUS_ZERO_FLOAT" : "ZERO_FLOAT";
				}
			}
			String cst = jsContext.getConstantPool().createConstant(value);
			return StringFormat.format("{0}/*{1}*/",cst,encodeJavaLiteral(value));
		}
		if(value instanceof String s) {
			if(s.length()>JSTranspilerOptions.STRING_CONSTANT_THRESHOLD) {
				String cst = jsContext.getConstantPool().createConstant(value);
				String str = s;
				if(s.length()>=JSTranspilerOptions.STRING_CONSTANT_SAMPLE) {
					str = encodeJavaLiteral(s.substring(0,JSTranspilerOptions.STRING_CONSTANT_SAMPLE)) + "...";
				} else {
					str = encodeJavaLiteral(s);
				}
				if(str.startsWith("new ")) {
					str = str.substring(4);
				}
				// This preview is embedded in a Java block comment purely for
				// debugging/readability - the actual value comes from `cst`.
				// If the ORIGINAL string itself contains "*/" (e.g. JS source
				// text with a `/* comment */` in it, previewed here for a
				// Function.prototype.toString test), that sequence would
				// prematurely close the enclosing /*...*/ comment and corrupt
				// everything after it as raw (uncompilable) Java code -
				// confirmed via test262 built-ins/Function/prototype/toString/
				// {async-generator-declaration,async-generator-expression,
				// class-declaration-explicit-ctor,class-expression-explicit-ctor,
				// function-declaration-non-simple-parameter-list,
				// method-computed-property-name}.js. Break the sequence with a
				// space so it stays comment-safe without changing readability.
				str = str.replace("*/","* /");
				return StringFormat.format("{0}/*{1}*/",cst,str);
			}
		}
		return encodeJavaLiteral(value);
	}
	
	public static String encodeJavaLiteral(Object value) {
    	if(value==null) {
    		return "null";
    	} else if(value instanceof String s) {
    		if(s.length()<=JSTranspilerOptions.STRING_CONSTANT_CHUNKS) {
        		return encodeString(s);
    		}
    		StringBuilder b = new StringBuilder(s.length()+500);
    		b.append("largeString(");
    		int i = 0;
    		while(i<s.length()) {
    			if(i>0) {
            		b.append(",\n");
    			}
    			int len = Math.min(s.length()-i, JSTranspilerOptions.STRING_CONSTANT_CHUNKS);
        		b.append(encodeString(s.substring(i,i+len)));
        		i+=len;
    		}
    		b.append(")");
    		return b.toString();
    	} else if(value instanceof Boolean b) {
    		return b ? "true" : "false";
    	} else if(value instanceof Byte n) {
    		return StringFormat.format("((byte){0})",n.toString());
    	} else if(value instanceof Short n) {
    		return StringFormat.format("((short){0})",n.toString());
    	} else if(value instanceof Integer n) {
    		return n.toString();
    	} else if(value instanceof Long n) {
    		return StringFormat.format("{0}L",n.toString());
    	} else if(value instanceof Float n) {
    		if (n == Float.POSITIVE_INFINITY) {
        		return "Float.POSITIVE_INFINITY";
    		} else if (n == Float.NEGATIVE_INFINITY) {
        		return "Float.NEGATIVE_INFINITY";
    		} else if (Float.isNaN(n)) {
    			return "Float.NaN";
    		} else {
    			return StringFormat.format("{0}f",DtoA.toJavaLiteral(n));
    		}
    	} else if(value instanceof Double n) {
    		if (n == Double.POSITIVE_INFINITY) {
        		return "Double.POSITIVE_INFINITY";
    		} else if (n == Double.NEGATIVE_INFINITY) {
        		return "Double.NEGATIVE_INFINITY";
    		} else if (Double.isNaN(n)) {
        		return "Double.NaN";
    		} else if(n==0) {
    			boolean isNegativeZero = Double.doubleToRawLongBits(n) == Double.doubleToRawLongBits(-0.0);
    			if(isNegativeZero) {
    				return "-0.0";
    			}
    			return "0.0";
    		} else {
    			String s = DtoA.toJavaLiteral(n);
    			if(s.indexOf('.')<0 && s.indexOf('e')<0 && s.indexOf('E')<0) {
    				s += ".0";
    			}
    			return StringFormat.format("(double){0}",s);
    		}
    	} else if(value instanceof BigInteger n) {
    		if(n.equals(BigInteger.ZERO)) {
        		return "BigInteger.ZERO";
    		}
    		if(n.equals(BigInteger.ONE)) {
        		return "BigInteger.ONE";
    		}
    		if(n.equals(BigInteger.TWO)) {
        		return "BigInteger.TWO";
    		}
    		if(n.equals(BigInteger.TEN)) {
        		return "BigInteger.TEN";
    		}
    		return StringFormat.format("new BigInteger(\"{0}\")", n.toString());
    	} else if(value instanceof BigDecimal n) {
    		if(n.equals(BigDecimal.ZERO)) {
        		return "BigDecimal.ZERO";
    		}
    		if(n.equals(BigDecimal.ONE)) {
        		return "BigDecimal.ONE";
    		}
    		if(n.equals(BigDecimal.TEN)) {
        		return "BigDecimal.TEN";
    		}
    		JSEnvironment env = JSEnvironment.getEnvironment();
    		String mcs = env.getMathContextTranspiler();
    		return StringFormat.format("new BigDecimal(\"{0}\",{1})", n.toString(), mcs);
    	} else if(value instanceof JSObjectInternal) {
    		throw RuntimeUtil.illegalState();
    	} else if(value instanceof JSArray) {
    		throw RuntimeUtil.illegalState();
    	} else if(value instanceof String[] a) {
    		JavaBuilder b = new JavaBuilder();
    		b.println("new String[] {");
    		b.incIndent();
    		for(int i=0; i<a.length; i++) {
    			b.print(encodeString(a[i]));
    			if(i<a.length-1) {
            		b.append(",");
    			}
        		b.append("\n");
    		}
    		b.decIndent();
    		b.append("}");
    		return b.toString();
    	} else if(value instanceof boolean[] a) {
    		JavaBuilder b = new JavaBuilder();
    		b.println("new boolean[] {");
    		b.incIndent();
    		for(int i=0; i<a.length; i++) {
    			b.print(a[i] ? "true" : "false");
    			if(i<a.length-1) {
            		b.append(",");
    			}
        		b.append("\n");
    		}
    		b.decIndent();
    		b.append("}");
    		return b.toString();
    	}
		return null;
    }
    public static final StringBuilder encodeString(StringBuilder b, String s, char quote) {
        return JsonUtil.encodeString(b, s, quote);
    }
    public static final StringBuilder encodeString(StringBuilder b, String s) {
        return JsonUtil.encodeString(b, s, '\"');
    }
    public static final String encodeString(String s) {
        return JsonUtil.encodeString(s, '\"');
    }
    
    @Override
	public String decompileExpression() {
    	if(value==null) {
			return "null";
    	}
    	if(value==RuntimeUtil.UNDEFINED) {
			return "undefined";
    	}
    	if(value instanceof Number nn) {
    		boolean needsParens = isNegativeNumber(nn) && getParent() instanceof org.monflabs.galtajs.node.MemberNode;
    		if(value instanceof Float n) {
    			if (n == Float.POSITIVE_INFINITY) {
    				return "Number.POSITIVE_INFINITY";
    			} else if (n == Float.NEGATIVE_INFINITY) {
    				return "Number.NEGATIVE_INFINITY";
    			} else if (Float.isNaN(n)) {
    				return "Number.NaN";
    			} else {
    				return maybeParens(needsParens, StringFormat.format("{0}f",DtoA.toJavaLiteral(n)));
    			}
    		} else if(value instanceof Double n) {
    			if (n == Double.POSITIVE_INFINITY) {
    				return "Number.POSITIVE_INFINITY";
    			} else if (n == Double.NEGATIVE_INFINITY) {
    				return "Number.NEGATIVE_INFINITY";
    			} else if (Double.isNaN(n)) {
    				return "Number.NaN";
    			} else if(n==0) {
    				boolean isNegativeZero = Double.doubleToRawLongBits(n) == Double.doubleToRawLongBits(-0.0);
    				if(isNegativeZero) {
    					return maybeParens(needsParens, "-0.0d");
    				}
    				return "0.0d";
    			} else {
    				String s = DtoA.toJavaLiteral(n);
    				s += "d";
    				return maybeParens(needsParens, s);
    			}
        	} else if(value instanceof BigInteger n) {
        		return maybeParens(needsParens, StringFormat.format("{0}n", n.toString()));
        	} else if(value instanceof BigDecimal n) {
        		return maybeParens(needsParens, StringFormat.format("{0}m", n.toString()));
    		} else {
        		if(value instanceof Long) {
        			return maybeParens(needsParens, Long.toString(nn.longValue())+"L "); // add an extra space ex: 77 .method()
        		}
    			return maybeParens(needsParens, Integer.toString(nn.intValue())+" "); // add an extra space ex: 77 .method()
    		}
    	}
    	if(value instanceof String s) {
			return encodeString(s);
    	}
    	if(value instanceof Boolean b) {
			return b ? "true" : "false";
    	}
    	throw new IllegalStateException("Invalid primitive type");
    }

    private static boolean isNegativeNumber(Number n) {
    	if(n instanceof Integer i) return i < 0;
    	if(n instanceof Long l) return l < 0;
    	if(n instanceof Double d) return d < 0;
    	if(n instanceof Float f) return f < 0;
    	if(n instanceof BigInteger bi) return bi.signum() < 0;
    	if(n instanceof BigDecimal bd) return bd.signum() < 0;
    	return false;
    }

    private static String maybeParens(boolean needsParens, String s) {
    	return needsParens ? "(" + s.trim() + ")" : s;
    }
}
    