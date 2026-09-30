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

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.standard.regexp.RegExp;
import org.monflabs.galtajs.rt.builtins.standard.regexp.RegExpEngine;
import org.monflabs.util.StringUtil;

/**
 * RegExp using the Java engine as is.
 */
public class RegExpEngineJdk implements RegExpEngine {
	
	private static BiFunction<JSEnvironment,RegExp,RegExpEngine> factory =
			(env,regexp) -> new RegExpEngineJdk(env, regexp);
	public static BiFunction<JSEnvironment,RegExp,RegExpEngine> factory() {
		return factory;
	}

	
	private JSEnvironment env;
	private RegExp regExp;
	
    private Pattern pattern;
    private Matcher matcher;
    
    public RegExpEngineJdk(JSEnvironment env, RegExp regExp) {
    	this.env = env;
    	this.regExp = regExp;
    	
        try {
            String sourcewithFlags = regExp.toString();
            pattern = env.getRegExp(sourcewithFlags, (ss) -> {
                final int flags =
               		 (regExp.isIgnoreCase() ? Pattern.CASE_INSENSITIVE : 0);
            	String source = regExp.getSource();
            	String javaSource = preProcessRegExp(source, flags);
            	return Pattern.compile(javaSource, flags);    
            });
        } catch (Exception ex) {
        	throw RuntimeUtil.syntaxError("Invalid RegExp '{0}'", regExp.toString());
        }
    }
    
    public JSEnvironment getEnvironment() {
    	return env;
    }
    
    public RegExp getRegExp() {
    	return regExp;
    }

	private void resetContext() {
        regExp.setLastIndex(0);
        matcher = null;
	}
	
    public boolean isValidGroup(Matcher matcher, int group) {
    	return true;
    }


    private Pattern getPattern() {
    	return pattern;
    }
    protected String preProcessRegExp(String source, int flags) {
    	return source;
    }
   

	protected boolean _exec(JSRuntimeContext context, String str) {
        boolean global = regExp.isGlobal();
        boolean sticky = regExp.isSticky();
    	if(matcher==null || !(sticky||global)) {
            matcher = getPattern().matcher(str);
    	}

    	// Per RegExpBuiltinExec, step 4's "lastIndex = ToLength(Get(R,
    	// "lastIndex"))" is UNCONDITIONAL - even when neither global nor
    	// sticky is set (where the read value is immediately discarded by
    	// step 6). A poisoned lastIndex (e.g. `{valueOf(){...}}`) must still
    	// have its valueOf() invoked exactly once even though the numeric
    	// result goes unused here - see the identical fix in RegExpEngineJoni.
    	int rawIndex = regExp.getLastIndex();
    	int index = 0;
        if (global || sticky) {
        	index = rawIndex;
        	if(index<0 || index>str.length()) {
        		regExp.setLastIndex(0);
        		return false;
        	}
        }

    	boolean ok;
    	//matcher.region(index,matcher.regionEnd());
        if(sticky) {
        	matcher.region(index,matcher.regionEnd());
           	ok = matcher.lookingAt();
        } else {
           	ok = matcher.find(index);
        }
        if(ok) {
            recordLegacyMatch(str, matcher);
            if(global || sticky) {
            	// Per RegExpBuiltinExec, lastIndex is set to the match's end
            	// index unconditionally - even for a zero-width match. The
            	// "advance past a zero-width match by one position" step is
            	// NOT part of exec() itself; it belongs exclusively to the
            	// CALLER (the match/matchAll/replace/split algorithms, via
            	// AdvanceStringIndex), which must apply it on top of exec()'s
            	// result. exec() previously did this advance internally too
            	// (to keep a naive `while(re.exec(s))` loop from spinning),
            	// but that made it double up with a spec-generic caller that
            	// (correctly) also performs its own advance.
            	regExp.setLastIndex(matcher.end());
            }
            return true;
        }

        // No match found...
        if (global || sticky) {
       		regExp.setLastIndex(0);
        }
        return false;
    }
    
    // Reports a successful match to the legacy static properties (RegExp.$1,
    // lastMatch, ...) - see RegExp.updateLegacyStaticProperties().
    private void recordLegacyMatch(String str, java.util.regex.MatchResult m) {
    	int n = m.groupCount() + 1;
    	int[] spans = new int[2 * n];
    	for (int k = 0; k < n; k++) {
    		spans[2 * k] = m.start(k);
    		spans[2 * k + 1] = m.end(k);
    	}
    	regExp.updateLegacyStaticProperties(str, spans);
    }

    @Override
	public JSArray exec(JSRuntimeContext context, String str) {
        try {
        	if(_exec(context,str)) {
                JSArray result = JSArray.create(getEnvironment());
                result.getMembers(true).setOwnProperty("index", matcher.start());
                result.getMembers(true).setOwnProperty("input", str);

                // Extract named groups
                Object namedGroups = extractNamedGroups(matcher);
                result.getMembers(true).setOwnProperty("groups", namedGroups);

                JSArray indices = null;
                if(regExp.isHasIndices()) {
                	indices = JSArray.create(getEnvironment());
                	indices.getMembers(true).setOwnProperty("groups", namedGroups != RuntimeUtil.UNDEFINED ? extractNamedGroupIndices(matcher) : RuntimeUtil.UNDEFINED);
                   	result.getMembers(true).setOwnProperty("indices", indices);
                }

                // Group 0 is the entire pattern
                // Group 1..n are the group
                int gc = matcher.groupCount();
                for(int j=0; j<=gc; j++) {
                	if(isValidGroup(matcher,j)) {
                		// A group should not be null but undefined
                		Object g = matcher.group(j);
	                    result.arrayAdd(j,g!=null?g:RuntimeUtil.UNDEFINED);
	                    if(indices!=null) {
	                    	if(g != null) {
		                    	JSArray a = JSArray.create(getEnvironment());
		                    	a.arrayAdd(matcher.start(j));
		                    	a.arrayAdd(matcher.end(j));
		                    	indices.arrayAdd(a);
	                    	} else {
	                    		indices.arrayAdd(RuntimeUtil.UNDEFINED);
	                    	}
	                    }
                	}
                }
                return result;
            }
        	// _exec() already applies RegExpBuiltinExec's own conditional
        	// lastIndex reset on failure (only when global||sticky) - calling
        	// the full resetContext() here would unconditionally clobber
        	// lastIndex for a non-global, non-sticky regexp too (see the
        	// identical fix/rationale in RegExpEngineJoni). Only the cached
        	// matcher needs clearing.
        	matcher = null;
            return null;
        } catch (Exception ex) {
        	throw RuntimeUtil.wrap(ex);
        }
    }

    private java.util.Map<String, Integer> getNamedGroups() {
        // Try to use Pattern.namedGroups() if available (Java 20+)
        // Otherwise parse the pattern to extract named groups
        try {
            java.lang.reflect.Method method = Pattern.class.getMethod("namedGroups");
            @SuppressWarnings("unchecked")
            java.util.Map<String, Integer> groups = (java.util.Map<String, Integer>) method.invoke(getPattern());
            return groups;
        } catch (Exception e) {
            // Method not available, parse the pattern manually
            return parseNamedGroups(regExp.getSource());
        }
    }

    private java.util.Map<String, Integer> parseNamedGroups(String pattern) {
        // Parse the pattern to find named capture groups: (?<name>...)
        java.util.Map<String, Integer> namedGroups = new java.util.LinkedHashMap<>();
        int groupCount = 0;

        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);

            // Skip escaped characters
            if (c == '\\' && i + 1 < pattern.length()) {
                i++;
                continue;
            }

            // Skip character classes
            if (c == '[') {
                i++;
                while (i < pattern.length()) {
                    if (pattern.charAt(i) == '\\' && i + 1 < pattern.length()) {
                        i += 2;
                        continue;
                    }
                    if (pattern.charAt(i) == ']') {
                        break;
                    }
                    i++;
                }
                continue;
            }

            // Look for opening parenthesis
            if (c == '(') {
                // Check if it's a capturing group
                if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '?') {
                    // Non-capturing or special group
                    if (i + 2 < pattern.length()) {
                        char next = pattern.charAt(i + 2);
                        if (next == '<') {
                            // Named capture group: (?<name>...)
                            i += 3; // Skip '(?<'
                            StringBuilder name = new StringBuilder();
                            while (i < pattern.length() && pattern.charAt(i) != '>') {
                                name.append(pattern.charAt(i));
                                i++;
                            }
                            if (i < pattern.length() && name.length() > 0) {
                                groupCount++;
                                namedGroups.put(name.toString(), groupCount);
                            }
                        } else if (next != ':' && next != '=' && next != '!') {
                            // Other special groups might still be capturing
                            groupCount++;
                        }
                        // else: non-capturing group (?:...), lookahead (?=...), negative lookahead (?!...)
                    }
                } else {
                    // Regular capturing group
                    groupCount++;
                }
            }
        }

        return namedGroups;
    }

    private Object extractNamedGroups(Matcher matcher) {
        try {
            // Java Pattern supports named groups with (?<name>...)
            // We need to extract them into a JavaScript object
            java.util.Map<String, Integer> namedGroups = getNamedGroups();
            if(namedGroups == null || namedGroups.isEmpty()) {
                return RuntimeUtil.UNDEFINED;
            }

            JSObject groupsObj = JSObject.createWithPrototype(getEnvironment(), null);

            for(java.util.Map.Entry<String, Integer> entry : namedGroups.entrySet()) {
                String name = entry.getKey();
                try {
                    String value = matcher.group(name);
                    groupsObj.setOwnProperty(name, value != null ? value : RuntimeUtil.UNDEFINED);
                } catch(IllegalArgumentException e) {
                    // Group name not found in matcher
                    groupsObj.setOwnProperty(name, RuntimeUtil.UNDEFINED);
                }
            }

            return groupsObj;
        } catch(Exception e) {
            return RuntimeUtil.UNDEFINED;
        }
    }

    private Object extractNamedGroupIndices(Matcher matcher) {
        try {
            java.util.Map<String, Integer> namedGroups = getNamedGroups();
            if(namedGroups == null || namedGroups.isEmpty()) {
                return RuntimeUtil.UNDEFINED;
            }

            JSObject groupsObj = JSObject.createWithPrototype(getEnvironment(), null);

            for(java.util.Map.Entry<String, Integer> entry : namedGroups.entrySet()) {
                String name = entry.getKey();
                try {
                    String value = matcher.group(name);
                    if(value != null) {
                        JSArray indices = JSArray.create(getEnvironment());
                        indices.arrayAdd(matcher.start(name));
                        indices.arrayAdd(matcher.end(name));
                        groupsObj.setOwnProperty(name, indices);
                    } else {
                        groupsObj.setOwnProperty(name, RuntimeUtil.UNDEFINED);
                    }
                } catch(IllegalArgumentException e) {
                    // Group name not found in matcher
                    groupsObj.setOwnProperty(name, RuntimeUtil.UNDEFINED);
                }
            }

            return groupsObj;
        } catch(Exception e) {
            return RuntimeUtil.UNDEFINED;
        }
    }

    @Override
	public boolean test(JSRuntimeContext context, String str) {
        try {
            boolean global = regExp.isGlobal();
            boolean sticky = regExp.isSticky();
        	if(matcher==null || !(sticky||global) ) {
	            matcher = getPattern().matcher(str);
        	}

            int index = (global || sticky) ? regExp.getLastIndex() : 0;
            // Allow matching at str.length() for empty patterns
            if(index<0 || index>str.length()) {
            	regExp.setLastIndex(0);
                matcher = null;
                return false;
            }

        	boolean ok;
            if(sticky) {
            	matcher.region(index,matcher.regionEnd());
               	ok = matcher.lookingAt();
            } else {
               	ok = matcher.find(index);
            }


            if(ok) {
                recordLegacyMatch(str, matcher);
                if(global || sticky) {
                	regExp.setLastIndex(matcher.end());
                }
                return true;
            } else {
            	regExp.setLastIndex(0);
            	// Reset the RegExp
            	matcher = null;
            }

            // No match found...
            return false;
        } catch (Exception ex) {
        	throw RuntimeUtil.wrap(ex);
        }
    }

    @Override
	public JSArray split(JSRuntimeContext context, String str, int limit) {
    	JSArray result = JSArray.create(getEnvironment());

    	// If limit is 0, return empty array
    	if(limit == 0) {
    		return result;
    	}

    	// Empty pattern - split into individual characters (or code points with unicode flag)
    	if(StringUtil.isEmpty(regExp.getSource())) {
    		if(regExp.isUnicode() || regExp.isUnicodeSets()) {
    			// Split by code points
    			int offset = 0;
    			int count = 0;
    			while(offset < str.length() && (limit <= 0 || count < limit)) {
    				int codePoint = str.codePointAt(offset);
    				result.arrayAdd(new String(Character.toChars(codePoint)));
    				offset += Character.charCount(codePoint);
    				count++;
    			}
    		} else {
    			// Split by code units
				int count = limit > 0 ? Math.min(limit, str.length()) : str.length();
	    		for(int i=0; i<count; i++) {
	    			result.arrayAdd(String.valueOf(str.charAt(i)));
	    		}
    		}
    		return result;
    	}

    	// Other patterns
        try {
            Matcher m = getPattern().matcher(str);
            int pos = 0;
            int resultCount = 0;
            boolean brokeEarly = false;

            while((limit <= 0 || resultCount < limit) && m.find()) {
            	recordLegacyMatch(str, m);
            	int start = m.start();
            	int end = m.end();

            	// Add the substring before the match
        		result.arrayAdd(str.substring(pos, start));
        		resultCount++;

        		// Add all capturing groups
        		if(limit <= 0 || resultCount < limit) {
	            	int gc = m.groupCount();
	            	for(int i = 1; i <= gc && (limit <= 0 || resultCount < limit); i++) {
	            		String g = m.group(i);
	            		result.arrayAdd(g != null ? g : RuntimeUtil.UNDEFINED);
	            		resultCount++;
	            	}
        		}

            	pos = end;

            	// Handle empty matches - advance by one character (or code point)
            	if(start == end) {
            		if(pos >= str.length()) {
            			// Don't add remaining substring after breaking from empty match at end
            			brokeEarly = true;
            			break;
            		}
            		if(regExp.isUnicode() || regExp.isUnicodeSets()) {
            			// Advance by code point
            			int codePoint = str.codePointAt(pos);
            			pos += Character.charCount(codePoint);
            		} else {
            			// Advance by code unit
            			pos++;
            		}
            		m.region(pos, str.length());
            	}
            }

        	// Add the remaining substring if we haven't hit the limit
        	// But not if we broke early from an empty match at the end
        	if(!brokeEarly && (limit <= 0 || resultCount < limit) && pos <= str.length()) {
        		result.arrayAdd(str.substring(pos));
        	}
        } catch (Exception ex) {
        	throw RuntimeUtil.wrap(ex);
        }
        return result;
    }
    
    @Override
	public JSArray match(JSRuntimeContext context, String str) {
        try {
            boolean global = regExp.isGlobal();
        	if(!global) {
        		JSArray r = exec(context,str);
                return r;
        	} else {
        		Matcher m = getPattern().matcher(str);
            	JSArray a = JSArray.create(getEnvironment());
            	while(m.find()) {
            		recordLegacyMatch(str, m);
            		a.arrayAdd(m.group());
            	}
            	return a.arrayLength()>0 ? a : null;
        	}
        } catch (Exception ex) {
        	throw RuntimeUtil.wrap(ex);
        }
    }
    
    @Override
	public Iterator<JSArray> matchAll(JSRuntimeContext context, String str) {
    	final boolean isGlobal = regExp.isGlobal();
    	return new Iterator<JSArray>() {
    		JSArray res = exec(context,str);

			@Override
			public boolean hasNext() {
				return res!=null;
			}
			@Override
			public JSArray next() {
				if(res!=null) {
					JSArray ret = res;

					// For non-global regexps, only match once
					if(!isGlobal) {
						res = null;
					} else {
						res = exec(context,str);
					}
					return ret;
				}
				throw new NoSuchElementException();
			}
		};
    }
    
    @Override
	public int search(JSRuntimeContext context, String str) {
        try {
            Matcher m = getPattern().matcher(str);
            // Per spec, Symbol.search uses the GENERIC RegExpExec, which
            // honors the regexp's own sticky ("y") flag - a sticky regex
            // must only match anchored at position 0, not scan forward for
            // the first match anywhere (see the identical fix/rationale in
            // RegExpEngineJoni).
            boolean ok = regExp.isSticky() ? m.lookingAt() : m.find();
            if(ok) {
            	recordLegacyMatch(str, m);
            	return m.start();
            } else {
            	return -1;
            }
        } catch (Exception ex) {
        	throw RuntimeUtil.wrap(ex);
        }
    }

    @Override
	public String replace(JSRuntimeContext context, String str, Object replace) {
    	this.matcher = null; // Make sure it is reset with the proper string
        if (regExp.isGlobal()) {    
        	regExp.setLastIndex(0);
        }
    	
        Callable cb = null;
        String newSubStr = ""; // to please null pointer check
        boolean substr_groups = false;
		if (replace instanceof Callable cb0) {
			cb = cb0;
		} else {
			newSubStr = RuntimeUtil.toString(getEnvironment(),replace);
			substr_groups = newSubStr.indexOf('$')>=0; // Could be even more precise!
		}

		StringBuilder result = new StringBuilder();
        int start = 0;
        while(true) {
        	boolean ok = _exec(context,str);
        	if(ok) {
                result.append(str, start, matcher.start());
                if(matcher.start()==matcher.end()) {
                	// matched an empty string, consume a character (or code point with unicode)
                	int nextIndex = matcher.end();
                	if(nextIndex < str.length()) {
	                	if(regExp.isUnicode() || regExp.isUnicodeSets()) {
	                		// Advance by code point
	                		int codePoint = str.codePointAt(nextIndex);
	                		nextIndex += Character.charCount(codePoint);
	                	} else {
	                		// Advance by code unit
	                		nextIndex++;
	                	}
                	} else {
                		nextIndex++;
                	}
                	regExp.setLastIndex(nextIndex);
                }
            	start = matcher.end();
                
    			if (cb!=null) {
    				int groupCount = matcher.groupCount();
                    final Object[] args = new Object[groupCount+3];
                    int idx = 0;
                    args[idx++] = str.substring(matcher.start(), matcher.end());
                    for(int i=1; i<=groupCount; i++) {
                    	String gi = matcher.group(i);
                    	// the conversion of \1 to (?:\1|) forces the string to be "" 
                        //args[idx++] = StringUtil.isNotEmpty(gi) ? gi : RuntimeUtil.UNDEFINED;
                        args[idx++] = gi!=null ? gi : RuntimeUtil.UNDEFINED;
                    }
                    args[idx++] = matcher.start();
                    args[idx++] = str;
                    Object res = cb.call(null,args);
                    result.append(RuntimeUtil.toString(getEnvironment(),res));
    			} else if(!substr_groups) {
                    result.append(newSubStr);
    			} else {
    				int subLength = newSubStr.length();
                    for (int i = 0; i < subLength;) {
                        char c = newSubStr.charAt(i++);
                        if (c != '$' || i==subLength) { // last $ is a regular character
                            result.append(c);
                            continue;
                        }

                        // special handling for: $$, $&, $`, $', $n, $nn
                        char after$ = newSubStr.charAt(i++);
                        switch (after$) {
    	                    case '$':
    	                        result.append('$');
    	                        break;
    	                    case '&':
    	                        result.append(matcher.group());
    	                        break;
    	                    case '`':
    	                        result.append(str, 0, matcher.start());
    	                        break;
    	                    case '\'':
    	                        result.append(str, matcher.end(), str.length());
    	                        break;
    	                    case '1':
    	                    case '2':
    	                    case '3':
    	                    case '4':
    	                    case '5':
    	                    case '6':
    	                    case '7':
    	                    case '8':
    	                    case '9':
    	                        int n = after$ - '0';
    	                        // check for $nn (up to $99)
    	                        if (newSubStr.length() > i) {
    	                            c = newSubStr.charAt(i);
    	                            if (c >= '0' && c <= '9') {
    	                                int nn = n * 10 + c - '0';
    	                                // Only consume second digit if nn is a valid group
    	                                if (nn > 0 && nn <= matcher.groupCount()) {
    	                                    n = nn;
    	                                    i++;
    	                                }
    	                            }
    	                        }
    	                        // substitute group (only if n is valid)
    	                        if (n > 0 && matcher.groupCount() >= n) {
    	                            String group = matcher.group(n);
    	                            if(group != null) {
    	                                result.append(group);
    	                            }
    	                            break;
    	                        }
    	                        // fall through if no group match
    	                    default:
    	                        // $0 and any other character after $ is treated literally
    	                        result.append('$').append(after$);
                        }
                    }
    			}
        	}
        	
            // break after 1 match if no global
            if (!ok || !regExp.isGlobal()) {
                break;
            }        
        }

        // copy from searchStart to LOOP_COUNT of string
        result.append(str, start, str.length());

        return result.toString();    
    }
    
    //@Override
	public String replace2(JSRuntimeContext context, String str, Object replace) {
		resetContext();
		matcher = getPattern().matcher(str);
        
        Callable cb = null;
        String newSubStr = ""; // to please null pointer check
        boolean substr_groups = false;
		if (replace instanceof Callable cb0) {
			cb = cb0;
		} else {
			newSubStr = RuntimeUtil.toString(getEnvironment(),replace);
			substr_groups = newSubStr.indexOf('$')>=0; // Could be even more precise!
		}
        
        StringBuilder result = new StringBuilder();
        int start = 0;
        while (matcher.find()) {
            recordLegacyMatch(str, matcher);
            // copy from searchStart to matchStart
            result.append(str, start, matcher.start());
            start = matcher.end();

            if(regExp.isGlobal() || regExp.isSticky() ) {
            	regExp.setLastIndex(matcher.end());
            }
            
			if (cb!=null) {
				int groupCount = matcher.groupCount();
                final Object[] args = new Object[groupCount+3];
                int idx = 0;
                args[idx++] = str.substring(matcher.start(), matcher.end());
                for(int i=1; i<=groupCount; i++) {
                	String gi = matcher.group(i);
                    args[idx++] = gi!=null ? gi : RuntimeUtil.UNDEFINED;
                }
                args[idx++] = matcher.start();
                args[idx++] = str;
                Object res = cb.call(null,args);
                result.append(RuntimeUtil.toString(getEnvironment(),res));
			} else if(!substr_groups) {
                result.append(newSubStr);
			} else {
				int subLength = newSubStr.length();
                for (int i = 0; i < subLength;) {
                    char c = newSubStr.charAt(i++);
                    if (c != '$' || i==subLength) { // last $ is a regular character
                        result.append(c);
                        continue;
                    }

                    // special handling for: $$, $&, $`, $', $n, $nn
                    char after$ = newSubStr.charAt(i++);
                    switch (after$) {
	                    case '$':
	                        result.append('$');
	                        break;
	                    case '&':
	                        result.append(matcher.group());
	                        break;
	                    case '`':
	                        result.append(str, 0, matcher.start());
	                        break;
	                    case '\'':
	                        result.append(str, matcher.end(), str.length());
	                        break;
	                    case '1':
	                    case '2':
	                    case '3':
	                    case '4':
	                    case '5':
	                    case '6':
	                    case '7':
	                    case '8':
	                    case '9':
	                        int n = after$ - '0';
	                        // check for $nn (up to $99)
	                        if (newSubStr.length() > i) {
	                            c = newSubStr.charAt(i);
	                            if (c >= '0' && c <= '9') {
	                                int nn = n * 10 + c - '0';
	                                // Only consume second digit if nn is a valid group
	                                if (nn > 0 && nn <= matcher.groupCount()) {
	                                    n = nn;
	                                    i++;
	                                }
	                            }
	                        }
	                        // substitute group (only if n is valid)
	                        if (n > 0 && matcher.groupCount() >= n) {
	                            String group = matcher.group(n);
	                            if(group != null) {
	                                result.append(group);
	                            }
	                            break;
	                        }
	                        // fall through if no group match
	                    default:
	                        // $0 and any other character after $ is treated literally
	                        result.append('$').append(after$);
                    }
                }
			}

            // break after 1 match if no global
            if (!regExp.isGlobal()) {
                break;
            }        
        }

        // copy from searchStart to LOOP_COUNT of string
        result.append(str, start, str.length());

        return result.toString();    
    }
}