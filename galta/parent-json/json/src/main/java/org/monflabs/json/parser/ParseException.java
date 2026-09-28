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

	public static final int ERROR_UNEXPECTED_CHAR = 0;
	public static final int ERROR_UNEXPECTED_TOKEN = 1;
	// public static final int ERROR_UNEXPECTED_EXCEPTION = 2;
	public static final int ERROR_UNEXPECTED_EOF = 3;
	public static final int ERROR_UNEXPECTED_UNICODE = 4;
	public static final int ERROR_UNEXPECTED_STRICT = 5;

	private int errorType;
	private Object unexpectedObject;
	private int position;

	public ParseException(JsonParser parser, int errorType, int position, Object unexpectedObject) {
		super(null,toMessage(parser, errorType, position, unexpectedObject));
		this.position = position;
		this.errorType = errorType;
		this.unexpectedObject = unexpectedObject;
	}

	public int getErrorType() {
		return errorType;
	}

	/**
	 * @return The character position (starting with 0) of the input where the
	 *         error occurs.
	 */
	public int getPosition() {
		return position;
	}

	/**
	 * @return One of the following base on the value of errorType:
	 *         ERROR_UNEXPECTED_CHAR java.lang.Character ERROR_UNEXPECTED_TOKEN
	 *         ERROR_UNEXPECTED_EXCEPTION java.lang.Exception
	 */
	public Object getUnexpectedObject() {
		return unexpectedObject;
	}

	@Override
	public String toString() {
		return getMessage();
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
			sb.append(String.valueOf(unexpectedObject));
			sb.append("' (");
			sb.append(unexpectedObject instanceof Character ch ? Integer.toString(ch) : String.valueOf(unexpectedObject));
			sb.append(") at position ");
			sb.append(position);
			sb.append(".");
		} else if (errorType == ERROR_UNEXPECTED_TOKEN) {
			sb.append("JsonParser: Unexpected token ");
			sb.append(unexpectedObject);
			sb.append(" at position ");
			sb.append(position);
			sb.append(".");
		} else if (errorType == ERROR_UNEXPECTED_EOF) {
			sb.append("JsonParser: Unexpected End Of File position ");
			sb.append(position);
			sb.append(": ");
			sb.append(unexpectedObject);
		} else if (errorType == ERROR_UNEXPECTED_UNICODE) {
			sb.append("JsonParser: Unexpected unicode escape secance ");
			sb.append(unexpectedObject);
			sb.append(" at position ");
			sb.append(position);
			sb.append(".");
		} else if (errorType == ERROR_UNEXPECTED_STRICT) {
			sb.append("JsonParser: Parser is running is strict mode and this construct is not available");
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
			String code = s.extractSourceCode(5, lc);
			sb.append("\n");
			sb.append(code);
		}

		return sb.toString();
	}
}
