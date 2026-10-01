package org.monflabs.json.parser;

import org.monflabs.json.JsonException;
import org.monflabs.json.parser.SourceCode.LineCol;

/**
 * ParseException explains why and where the error occurs in source JSON text.
 *
 * @author Uriel Chemouni &lt;uchemouni@gmail.com&gt;
 */
public class ParseException extends JsonException {

	private static final long serialVersionUID = 8879024178584091857L;

	/** An unexpected character, the unexpected object is the Character */
	public static final int ERROR_UNEXPECTED_CHAR = 0;
	/** An unexpected word (like "nul"), the unexpected object is the word */
	public static final int ERROR_UNEXPECTED_TOKEN = 1;
	// public static final int ERROR_UNEXPECTED_EXCEPTION = 2;
	/** The input ends where more content is expected, at the input length */
	public static final int ERROR_UNEXPECTED_EOF = 3;
	/** An invalid \\u or \\x escape, the unexpected object is the invalid hexadecimal digit */
	public static final int ERROR_UNEXPECTED_UNICODE = 4;
	/** A lenient only construct in strict mode, the unexpected object describes it */
	public static final int ERROR_UNEXPECTED_STRICT = 5;
	/**
	 * Another error (nesting too deep, number literal too long or invalid...), the
	 * unexpected object is the description
	 */
	public static final int ERROR_SYNTAX = 6;

	/** The number of characters displayed on each side of the error in a source line */
	static final int SOURCE_CONTEXT_COLUMNS = 80;
	/** The longest unexpected word or literal included as is in a message */
	private static final int MAX_TOKEN_LENGTH = 40;

	private int errorType;
	private Object unexpectedObject;
	private int position;

	public ParseException(JsonParser parser, int errorType, int position, Object unexpectedObject) {
		this(parser, errorType, position, unexpectedObject, null);
	}

	public ParseException(JsonParser parser, int errorType, int position, Object unexpectedObject, Throwable cause) {
		super(cause,toMessage(parser, errorType, position, unexpectedObject));
		this.position = position;
		this.errorType = errorType;
		this.unexpectedObject = unexpectedObject;
	}

	public int getErrorType() {
		return errorType;
	}

	/**
	 * @return The character position (starting with 0) of the input where the
	 *         error occurs. The end of the input is reported at the input length.
	 */
	public int getPosition() {
		return position;
	}

	/**
	 * @return the Character for ERROR_UNEXPECTED_CHAR and ERROR_UNEXPECTED_UNICODE, the
	 *         word for ERROR_UNEXPECTED_TOKEN, the description of the construct for
	 *         ERROR_UNEXPECTED_STRICT and of the error for ERROR_SYNTAX, null for
	 *         ERROR_UNEXPECTED_EOF
	 */
	public Object getUnexpectedObject() {
		return unexpectedObject;
	}

	@Override
	public String toString() {
		return getMessage();
	}

	/**
	 * Clip a text included in a message: a huge token must not make a huge message.
	 */
	static String clip(String s) {
		if(s!=null && s.length()>MAX_TOKEN_LENGTH) {
			return s.substring(0, MAX_TOKEN_LENGTH)+"...";
		}
		return s;
	}

	private static String toMessage(JsonParser parser, int errorType, int position, Object unexpectedObject) {
		StringBuilder sb = new StringBuilder();

		if (errorType == ERROR_UNEXPECTED_CHAR) {
			// The parser reports some characters as their int code: display them as characters
			if(unexpectedObject instanceof Integer i && i>=0 && i<=Character.MAX_VALUE) {
				unexpectedObject = Character.valueOf((char)i.intValue());
			}
			sb.append("JsonParser: Unexpected character '");
			// A null unexpected object must not turn a parse error into a NullPointerException
			sb.append(clip(String.valueOf(unexpectedObject)));
			sb.append("' (");
			sb.append(unexpectedObject instanceof Character ch ? Integer.toString(ch) : clip(String.valueOf(unexpectedObject)));
			sb.append(") at position ");
			sb.append(position);
			sb.append(".");
		} else if (errorType == ERROR_UNEXPECTED_TOKEN) {
			sb.append("JsonParser: Unexpected token '");
			sb.append(clip(String.valueOf(unexpectedObject)));
			sb.append("' at position ");
			sb.append(position);
			sb.append(".");
		} else if (errorType == ERROR_UNEXPECTED_EOF) {
			sb.append("JsonParser: Unexpected end of input at position ");
			sb.append(position);
			sb.append(".");
		} else if (errorType == ERROR_UNEXPECTED_UNICODE) {
			sb.append("JsonParser: Invalid hexadecimal digit '");
			sb.append(String.valueOf(unexpectedObject));
			sb.append("' in an escape sequence at position ");
			sb.append(position);
			sb.append(".");
		} else if (errorType == ERROR_UNEXPECTED_STRICT) {
			sb.append("JsonParser: ");
			sb.append(unexpectedObject!=null ? unexpectedObject : "This construct");
			sb.append(" is not allowed in strict mode, at position ");
			sb.append(position);
			sb.append(".");
		} else if (errorType == ERROR_SYNTAX) {
			sb.append("JsonParser: ");
			sb.append(unexpectedObject);
			sb.append(", at position ");
			sb.append(position);
			sb.append(".");
		} else {
			sb.append("JsonParser: Unknown error at position ");
			sb.append(position);
			sb.append(".");
		}

		// Display the source around the error, when the parser owns the source text.
		// A Reader given by the caller is never rewound nor read further: that consumed
		// the caller's stream (and could load a huge one in memory).
		String source = parser!=null ? parser.getFullSourceText() : null;
		if(source!=null) {
			SourceCode s = new SourceCode(source);
			LineCol lc = s.findCodePosition(position);
			sb.append("\n");
			// The lines are clipped around the error column: a huge line (a minified
			// document) must not make a huge message
			s.extractSourceCode(sb, 5, lc, SourceCode.OPT_LINENUMBER|SourceCode.OPT_LINEERRPTR, SOURCE_CONTEXT_COLUMNS);
		}

		return sb.toString();
	}
}
