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
package org.monflabs.ui.swing.components.grid;

import java.util.List;

/**
 * The text forms of the rows of a {@link VirtualTable}: CSV with a header row,
 * and a JSON object holding an array of rows.
 */
public final class TableExport {

	private TableExport() {
	}

	/** CSV (RFC 4180: quoted when needed, CRLF between the lines), the column names on the first line. */
	public static String toCsv(List<VirtualColumn> columns, List<Object[]> rows) {
		StringBuilder sb = new StringBuilder();
		for(int c=0; c<columns.size(); c++) {
			if(c>0) {
				sb.append(',');
			}
			csv(sb, columns.get(c).name());
		}
		for(Object[] row: rows) {
			sb.append("\r\n");
			for(int c=0; c<columns.size(); c++) {
				if(c>0) {
					sb.append(',');
				}
				Object v = c<row.length ? row[c] : null;
				if(v!=null) {
					csv(sb, v.toString());
				}
			}
		}
		return sb.toString();
	}

	private static void csv(StringBuilder sb, String s) {
		if(s.indexOf(',')<0 && s.indexOf('"')<0 && s.indexOf('\n')<0 && s.indexOf('\r')<0) {
			sb.append(s);
			return;
		}
		sb.append('"').append(s.replace("\"", "\"\"")).append('"');
	}

	/**
	 * <code>{"rows":[{"column":value,...},...]}</code>, indented. Numbers and
	 * booleans stay what they are, the rest is text, a missing value is null.
	 */
	public static String toJson(List<VirtualColumn> columns, List<Object[]> rows) {
		StringBuilder sb = new StringBuilder("{\n  \"rows\": [");
		for(int r=0; r<rows.size(); r++) {
			Object[] row = rows.get(r);
			sb.append(r>0 ? ",\n    {" : "\n    {");
			for(int c=0; c<columns.size(); c++) {
				sb.append(c>0 ? ", " : "");
				json(sb, columns.get(c).name());
				sb.append(": ");
				json(sb, c<row.length ? row[c] : null);
			}
			sb.append('}');
		}
		sb.append(rows.isEmpty() ? "]\n}" : "\n  ]\n}");
		return sb.toString();
	}

	private static void json(StringBuilder sb, Object v) {
		if(v==null) {
			sb.append("null");
		} else if(v instanceof Boolean || v instanceof Number n && isFinite(n)) {
			sb.append(v);
		} else {
			String s = v.toString();
			sb.append('"');
			for(int i=0; i<s.length(); i++) {
				char ch = s.charAt(i);
				switch(ch) {
				case '"' -> sb.append("\\\"");
				case '\\' -> sb.append("\\\\");
				case '\n' -> sb.append("\\n");
				case '\r' -> sb.append("\\r");
				case '\t' -> sb.append("\\t");
				default -> {
					if(ch<0x20) {
						sb.append(String.format("\\u%04x", (int)ch));
					} else {
						sb.append(ch);
					}
				}
				}
			}
			sb.append('"');
		}
	}

	private static boolean isFinite(Number n) {
		return !org.monflabs.util.TypeUtil.isNaN(n) && !org.monflabs.util.TypeUtil.isInfinite(n);
	}
}
