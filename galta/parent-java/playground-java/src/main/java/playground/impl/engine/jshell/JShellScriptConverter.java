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
package playground.impl.engine.jshell;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Converts a JShell script into the source of a Java class, for a runtime without JShell
 * (the jdk.jshell module), like CheerpJ in the browser: the imports are hoisted, the
 * declared methods and classes become static members, and the statements the body of a
 * static main(String[]) method.
 * <p>
 * It covers the scripts of the playground, not the whole of JShell: the top level
 * variables are local variables of main() (a lambda can't capture one that is assigned
 * again, or declare a parameter of the same name), and a variable can't be declared twice.
 */
public class JShellScriptConverter {

	// The imports of a JShell session
	private static final String[] DEFAULT_IMPORTS = {
		"java.io.*", "java.math.*", "java.net.*", "java.nio.file.*", "java.util.*",
		"java.util.concurrent.*", "java.util.function.*", "java.util.prefs.*",
		"java.util.regex.*", "java.util.stream.*"
	};

	private static final Set<String> STATEMENT_KEYWORDS = Set.of(
		"if", "for", "while", "do", "try", "switch", "synchronized", "return", "throw",
		"new", "else", "yield", "assert", "break", "continue");

	// A type declaration: modifiers, then class, interface, enum or record
	private static final Pattern TYPE_DECLARATION = Pattern.compile(
		"^(?:(?:public|protected|private|static|final|abstract|sealed|non-sealed|strictfp)\\s+)*(?:class|interface|enum|record|@interface)\\s");
	// A method declaration: modifiers, type parameters, a return type, then a name and '('
	private static final Pattern METHOD_DECLARATION = Pattern.compile(
		"^(?:(?:public|protected|private|static|final|abstract|synchronized|native|strictfp|default)\\s+)*(?:<[^>]*>\\s*)?[\\w$.]+(?:\\s*<[^()]*>)?(?:\\s*\\[\\s*\\])*\\s+([\\w$]+)\\s*\\(",
		Pattern.DOTALL);

	private JShellScriptConverter() {
	}

	/**
	 * The source of a class named className, running the script in its main() method.
	 */
	public static String toJavaClass(String script, String className) {
		StringBuilder imports = new StringBuilder();
		for(String i: DEFAULT_IMPORTS) {
			imports.append("import ").append(i).append(";\n");
		}
		StringBuilder members = new StringBuilder();
		StringBuilder statements = new StringBuilder();
		for(String unit: split(script)) {
			String code = stripLeadingComments(unit);
			if(code.isEmpty()) {
				continue;
			}
			if(code.startsWith("import ")) {
				imports.append(unit.strip()).append('\n');
			} else if(TYPE_DECLARATION.matcher(code).find()) {
				members.append(addStatic(unit, code)).append("\n\n");
			} else if(isMethodDeclaration(code)) {
				members.append(addStatic(unit, code)).append("\n\n");
			} else {
				String s = unit.strip();
				statements.append(s);
				// A last expression may come without its semicolon
				char last = s.charAt(s.length()-1);
				if(last!=';' && last!='}') {
					statements.append(';');
				}
				statements.append('\n');
			}
		}
		return imports
			+ "\npublic class " + className + " {\n\n"
			+ members
			+ "public static void main(String[] args) throws Throwable {\n"
			+ statements
			+ "}\n}\n";
	}

	private static boolean isMethodDeclaration(String code) {
		// A method has a body: the unit ends with its closing brace
		if(!code.endsWith("}")) {
			return false;
		}
		String firstWord = code.split("[^\\w$]", 2)[0];
		if(STATEMENT_KEYWORDS.contains(firstWord)) {
			return false;
		}
		var m = METHOD_DECLARATION.matcher(code);
		return m.find() && !STATEMENT_KEYWORDS.contains(m.group(1));
	}

	// Adds the static modifier to a member declaration (code is the unit without its
	// leading comments)
	private static String addStatic(String unit, String code) {
		if(Pattern.compile("^(?:\\w+\\s+)*static\\s").matcher(code).find()) {
			return unit.strip();
		}
		int at = unit.indexOf(code);
		return (unit.substring(0, at) + "static " + unit.substring(at)).strip();
	}

	private static String stripLeadingComments(String unit) {
		String s = unit.strip();
		while(true) {
			if(s.startsWith("//")) {
				int nl = s.indexOf('\n');
				s = nl<0 ? "" : s.substring(nl+1).strip();
			} else if(s.startsWith("/*")) {
				int end = s.indexOf("*/");
				s = end<0 ? "" : s.substring(end+2).strip();
			} else {
				return s;
			}
		}
	}

	/**
	 * Splits a script into its top level units: an import, a declaration or a statement.
	 * A unit ends with a semicolon outside of any parenthesis, bracket or brace, or with
	 * the closing brace of a block (unless the statement goes on: else, catch, finally,
	 * the while of a do, or an expression after an anonymous class).
	 */
	static List<String> split(String script) {
		List<String> units = new ArrayList<>();
		int start = 0;
		int depth = 0;	// (, [ and {
		int len = script.length();
		int i = 0;
		while(i<len) {
			char c = script.charAt(i);
			if(c=='/' && i+1<len && script.charAt(i+1)=='/') {
				int nl = script.indexOf('\n', i);
				i = nl<0 ? len : nl+1;
				continue;
			}
			if(c=='/' && i+1<len && script.charAt(i+1)=='*') {
				int end = script.indexOf("*/", i+2);
				i = end<0 ? len : end+2;
				continue;
			}
			if(c=='"' && script.startsWith("\"\"\"", i)) {
				int end = i+3;
				while(true) {
					end = script.indexOf("\"\"\"", end);
					if(end<0) {
						end = len;
						break;
					}
					if(script.charAt(end-1)!='\\') {
						end += 3;
						break;
					}
					end++;
				}
				i = end;
				continue;
			}
			if(c=='"' || c=='\'') {
				i++;
				while(i<len && script.charAt(i)!=c) {
					if(script.charAt(i)=='\\') {
						i++;
					}
					i++;
				}
				i++;
				continue;
			}
			if(c=='(' || c=='[' || c=='{') {
				depth++;
			} else if(c==')' || c==']') {
				depth--;
			} else if(c=='}') {
				depth--;
				if(depth==0 && !continuesAfterBlock(script, i+1)) {
					units.add(script.substring(start, i+1));
					start = i+1;
				}
			} else if(c==';' && depth==0) {
				units.add(script.substring(start, i+1));
				start = i+1;
			}
			i++;
		}
		if(start<len && !script.substring(start).isBlank()) {
			units.add(script.substring(start));
		}
		return units;
	}

	// Whether the unit goes on after a closing brace at the top level
	private static boolean continuesAfterBlock(String script, int from) {
		int i = from;
		int len = script.length();
		while(i<len && Character.isWhitespace(script.charAt(i))) {
			i++;
		}
		if(i>=len) {
			return false;
		}
		char c = script.charAt(i);
		if(c==';' || c==')' || c==',' || c=='.' || c==']') {
			return true;
		}
		for(String k: new String[] {"else", "catch", "finally", "while"}) {
			if(script.startsWith(k, i) && (i+k.length()>=len || !Character.isJavaIdentifierPart(script.charAt(i+k.length())))) {
				// A while after a block ends a do-while; a while statement after another
				// block starts a new unit
				return !k.equals("while") || isDoBlock(script, from);
			}
		}
		return false;
	}

	// Whether the block closing just before 'from' is the body of a do statement
	private static boolean isDoBlock(String script, int from) {
		int depth = 0;
		for(int i=from-1; i>=0; i--) {
			char c = script.charAt(i);
			if(c=='}') {
				depth++;
			} else if(c=='{') {
				depth--;
				if(depth==0) {
					String before = script.substring(0, i).stripTrailing();
					return before.endsWith("do") && (before.length()==2 || !Character.isJavaIdentifierPart(before.charAt(before.length()-3)));
				}
			}
		}
		return false;
	}
}
