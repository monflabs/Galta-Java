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
package org.monflabs.galtajs.rt;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.builtins.standard.console.ConsoleToString;

/**
 * Runtime debug utilities.
 */
public final class DebugUtil {
	
	private static final int TRUNCATE_LENGTH = 128;
	public static String truncate(String s) {		
		if(s.length()>TRUNCATE_LENGTH) {
			return s.substring(0, TRUNCATE_LENGTH)+"...";
		}
		return s;
	}

	public static String jsLiteral(JSEnvironment env, Object o) {
		return jsLiteral(env, o, -1);
	}
	
	public static String jsLiteral(JSEnvironment env, Object o, int maxLength) {
		if(o==null) {
			return encode("<null>", maxLength);
		}
		return encode(toSource(env,o),maxLength);
	}
	
	public static String toSource(JSEnvironment env, Object o, int maxChars) {
		return ConsoleToString.toString(env,o,maxChars,Integer.MAX_VALUE);
	}
	public static String toSource(JSEnvironment env, Object o) {
		return ConsoleToString.toString(env,o);
	}
	
	private static String encode(String s, int maxLength) {
		//s = s.replace("\"","\\\"")
		s = s.replace("\b","\\b")
			 .replace("\t","\\t")
			 .replace("\r","\\r")
			 .replace("\n","\\n");
		return maxLength>0 && s.length()>maxLength ? s.substring(0,maxLength) : s;
	}
}