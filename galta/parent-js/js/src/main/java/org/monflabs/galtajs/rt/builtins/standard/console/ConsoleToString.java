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
package org.monflabs.galtajs.rt.builtins.standard.console;

import java.util.Date;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.internal.JSObjectInternal;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.ClassPrototype;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.primitives.object.BuiltinObjectPrototype;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.rt.builtins.standard.date.DateUtil;
import org.monflabs.galtajs.rt.builtins.standard.function.BuiltinFunction;
import org.monflabs.galtajs.rt.builtins.standard.map.BuiltinMap;
import org.monflabs.galtajs.rt.builtins.standard.regexp.RegExp;
import org.monflabs.galtajs.rt.builtins.standard.set.BuiltinSet;
import org.monflabs.galtajs.rt.util.NumberFormatting;
import org.monflabs.json.JsonUtil;
import org.monflabs.util.StringFormat;
import org.monflabs.util.StringUtil;

/**
 *  Utilities to convert Objects to strong for the console.
 */
public class ConsoleToString {
	
	private static final int COMPACT_THRESHOLD = 30;
	private static final int MAX_DISPLAY_PROP = 30;
	
	@SuppressWarnings("serial")
	private static final class MaxLengthException extends RuntimeException {
	}
	private static final MaxLengthException mxException = new MaxLengthException();
	
	private static final class LimitingBuilder {
		
		StringBuilder b = new StringBuilder();
		boolean compact;
		int indent;
		int maxLength;
		int maxDepth;
		boolean lineStart = true;
		
		private LimitingBuilder(boolean compact, int maxLength,int maxDepth) {
			this.compact = compact; 
			this.maxLength = maxLength;
			this.maxDepth = maxDepth;
		}
		@Override
		public String toString() {
			return b.toString();
		}
		public boolean isCompact() {
			return compact;
		}
		public void incIndent() {
			indent++;
		}
		public void decIndent() {
			indent--;
		}
		public boolean prepareValue(boolean prepared) {
			if(prepared) {
				append(',');
				append('\n');
			}
			return true;
		}
		public void append(String s, Object...p) {
			append(StringFormat.format(s, p));
		}
		public void append(String s) {
			int len = s.length();
			for(int i=0; i<len; i++) {
				append(s.charAt(i));
			}
		}
		public void append(char c) {
			if(b.length() < maxLength) {
				if(lineStart) {
					lineStart = false;
					indent();
					append(c);
				} else {
					if(c=='\n') {
						lineStart = true;
						b.append(newLine());
					} else {
						b.append(c);
					}
				}
			} else {
				if(b.length()==maxLength) {
					b.append("...");
					throw mxException;
				}
			}
		}
		protected char newLine() {
			return compact ? ' ' : '\n';
		}
		protected void indent() {
			if(indent>0 && !compact) {
				for(int i=0; i<indent; i++) {
					append(' ');
					append(' ');
				}
			}
		}
	}
	
	public static String typeString(JSEnvironment env, Object o) {
    	if(o instanceof JSObjectInternal jo) {
    		Object p = jo.getPrototype();
    		if(p==null || p==RuntimeUtil.NOT_AVAILABLE || p==BuiltinObjectPrototype.get(env)) {
    			return null; // just regular object
    		}
    		if(p instanceof ClassPrototype cp) {
				return cp.getClassName();
    		}
    		if(p instanceof JSObjectInternal jp) {
    			Object ctor = jp.getProperty(Constructor.CONSTRUCTOR);
    			if(ctor instanceof BuiltinFunction bf) {
    				Object fctName = bf.getProperty("name");
    				if(fctName instanceof String n) {
    					return n;
    				}
    			}
    			if(ctor instanceof Constructor c) {
    				return c.getClassName();
    			}
    		}
			JSAccessor a = env.getAccessor(p);
			return a.getClassName(p);
    	}
    	String clazz = BuiltinObjectPrototype.toString(env,o);
		if(clazz.startsWith("[") && clazz.endsWith("]")) {
			int p = clazz.indexOf(' ');
			if(p>=0) {
				clazz = clazz.substring(p+1,clazz.length()-1);
			}
		}
		return clazz;
	}

	public static String toString(JSEnvironment env, Object v) {
		return toString(env,v,5000,10); // Guard rails
	}
	public static String toString(JSEnvironment env, Object v, int maxLength,int maxDepth) {
		LimitingBuilder b = new LimitingBuilder(false,maxLength,maxDepth);
		try {
			output(env,b,new IdentityHashMap<>(),v,0);
		} catch(MaxLengthException ex) {}
		return b.toString();
	}
	
	
	private static void output(JSEnvironment env, LimitingBuilder b, Map<Object,Void> processed, Object o, int depth) {
		if(depth>=b.maxDepth) {
			return;
		}
		if(o==null) {
			b.append("null");
			return;
		}
		if(o==RuntimeUtil.UNDEFINED) {
			b.append("undefined");
			return;
		}
		if(RuntimeUtil.isPrimitiveType(o)) {
			if(RuntimeUtil.hasPropertyMap(env,o)) {
				if(o instanceof CharSequence) {
					b.append("String ");
				} else if(o instanceof Number n) {
					b.append("[Number: "+NumberFormatting.numberToString(n,10,true)+"] ");
				} else if(o instanceof Boolean bb) {
					b.append("[Boolean: "+(bb ? "true" : "false")+"] ");
				} else if(o instanceof Symbol sy) {
					b.append("[Symbol: "+sy.toStringDebug()+"] ");
				}
				outputObject(env, b, processed, RuntimeUtil.getPrimitiveObject(env, o), depth);
			} else {
				if(o instanceof CharSequence s) {
					b.append(JsonUtil.encodeString(s.toString(),'\''));
				} else if(o instanceof Number n) {
					b.append(NumberFormatting.numberToString(n,10,true));
					return;
				} else if(o instanceof Boolean bb) {
					b.append(bb ? "true" : "false");
				} else if(o instanceof Symbol sy) {
					b.append(sy.toStringDebug());
				}
			}
			return;
		}
		if(o instanceof Date d) {
			b.append(DateUtil.toISOString(d));
			return;
		}
		if(o instanceof RegExp r) {
			b.append("/"+r.getSource()+"/");
			return;
		}
		//if(o instanceof JSObjectNotArray jo) {
			outputObject(env, b, processed, o, depth);
		//	return;
		//}
		//if(o instanceof JSArray ja) {
		//	outputArray(env, b, processed, ja, depth);
		//	return;
		//}
		//b.append(o.getClass().getName());
	}
	
	private static final void outputObject(JSEnvironment env, LimitingBuilder b, Map<Object,Void> processed, Object jo, int depth) {
		if(!b.isCompact() && COMPACT_THRESHOLD>0) {
			String compact = outputJson(env, processed, jo);
			if(compact!=null) {
				b.append(compact);
				return;
			}
		}
		
		String type = typeString(env, jo);
		if(processed.containsKey(jo)) {
			b.append("[Circular] {0}", type);
			return;
		}
		processed.put(jo, null);
		
		boolean prepared = false;
		if(jo instanceof BuiltinSet set) {
			b.append("{0}({1}) {\n",type,set.size());
			b.incIndent();
			for(Object v: set) {
				prepared = b.prepareValue(prepared);
				output(env, b, processed, v, depth);
			}
			b.decIndent();
		} else if(jo instanceof BuiltinMap map) {
			b.append("{0}({1}) {\n",type,map.size());
			b.incIndent();
			for(Map.Entry<Object,Object> e: map.entrySet()) {
				prepared = b.prepareValue(prepared);
				output(env, b, processed, e.getKey(), depth);
				b.append(" => ");
				output(env, b, processed, e.getValue(), depth);
			}
			b.decIndent();
		} else {
			if(jo instanceof Callable) {
				b.append("\u0192 ");
			}
			if(StringUtil.isNotEmpty(type)) {
				b.append("{0} {\n", type);
			} else {
				b.append("{\n");
			}
		}
		// Dump properties
		int propCount = 0;
		JSAccessor acc = env.getAccessor(jo);
		b.incIndent();
		for(Iterator<Map.Entry<String, Object>> it=acc.ownStringEntries(jo, true); it.hasNext(); ) {
			if(propCount>=MAX_DISPLAY_PROP) {
				b.append("...");
				break;
			}
			propCount++;
			Map.Entry<String, Object> e = it.next();
			prepared = b.prepareValue(prepared);
			String key = stringOrNull(e.getKey());
			if(JsonUtil.isIdentifier(key)) {
				b.append(key);
			} else {
				b.append(JsonUtil.encodeString(key,'\''));
			}
			b.append(": ");
			output(env, b, processed, e.getValue(), depth);
		}
		if(propCount<MAX_DISPLAY_PROP) {
			
		}
		if(propCount<MAX_DISPLAY_PROP) {
			for(Iterator<Map.Entry<Symbol, Object>> it=acc.ownSymbolEntries(jo, true); it.hasNext(); ) {
				if(propCount>=MAX_DISPLAY_PROP) {
					b.append("...");
					break;
				}
				propCount++;
				Map.Entry<Symbol, Object> e = it.next();
				prepared = b.prepareValue(prepared);
				b.append("["+e.getKey().toString()+"]");
				b.append(": ");
				output(env, b, processed, e.getValue(), depth);
			}
		}
		b.append("\n");
		b.decIndent();
		b.append("}");
	}
		
//	private static final void outputArray(JSEnvironment env, LimitingBuilder b, Map<Object,Void> processed, JSArray ja, int depth) {
//		if(!b.isCompact() && COMPACT_THRESHOLD>0) {
//			String compact = outputJson(env, processed, ja);
//			if(compact!=null) {
//				b.append(compact);
//				return;
//			}
//		}
//
//		if(processed.containsKey(ja)) {
//			b.append("[Circular] Array");
//			return;
//		}
//
//		boolean prepared = false;
//
//		processed.put(ja,null);
//    	SparseList<Object> sp = ja.getSparseList();
//		b.append("[");
//		if(sp!=null) {
//			final int finalDepth = depth;
//			b.append(sp.toStringList( (v) -> {
//				LimitingBuilder bb = new LimitingBuilder(false,b.maxLength,b.maxDepth-finalDepth);
//				output(env,bb,processed,v,finalDepth+1);
//				return bb.toString();
//			}));
//		} else {
//			depth++;
//			b.incIndent();
//			for(Iterator<Object> it=ja.jsValues(true); it.hasNext();  ) {
//				Object v=it.next();
//				prepared = b.prepareValue(prepared);
//				output(env,b,processed,v,depth);
//			}
//		}
//		JSObject jo = ja.getMembers(false);
//		if(jo!=null) {
//			boolean first = ja.jsSize()==0;
//			for(Iterator<Map.Entry<String, Object>> it=jo.jsEntries(false); it.hasNext(); ) {
//				Map.Entry<String, Object> e = it.next();
//				prepared = b.prepareValue(prepared);
//				if(!first) {
//					b.append(", ");
//				} else {
//					first = false;
//				}
//				if(JsonUtil.isIdentifier(e.getKey())) {
//					b.append(e.getKey());
//				} else {
//					b.append(JsonUtil.encodeString(e.getKey(),'\''));
//				}
//				b.append(": ");
//				output(env, b, processed, e.getValue(), depth);
//			}
//		}
//		depth--;
//		b.append("\n");
//		b.decIndent();
//		b.append("]");
//		return;
//	}

	private static String stringOrNull(Object v) {
		if(v==null) {
			return "null";
		}
		return v.toString();
	}
	
	private static final String outputJson(JSEnvironment env, Map<Object,Void> processed, Object jsonValue) {
		LimitingBuilder b = new LimitingBuilder(true,20, 2);
		try {
			IdentityHashMap<Object,Void> p = new IdentityHashMap<>(processed);
			output(env, b, p, jsonValue, 0);
			processed.putAll(p);
			return b.toString();
		} catch(MaxLengthException e) {
			return null;
		}
	}

}