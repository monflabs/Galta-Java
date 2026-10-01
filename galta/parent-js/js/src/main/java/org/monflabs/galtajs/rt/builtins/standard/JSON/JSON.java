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
package org.monflabs.galtajs.rt.builtins.standard.JSON;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.jsonfactory.JSObject.DESC_CHECK;
import org.monflabs.galtajs.jsonfactory.internal.JSObjectInternal;
import org.monflabs.galtajs.rt.JSRuntimeException;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.NativeObject;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.rt.builtins.standard.function.BuiltinFunction;
import org.monflabs.galtajs.rt.builtins.standard.proxy.BuiltinProxy;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.parser.JsonParser;
import org.monflabs.json.stringifier.JsonStringifier;
import org.monflabs.json.stringifier.JsonStringifier.Replacer;
import org.monflabs.json.stringifier.JsonStringifier.ReplacerRawJSON;
import org.monflabs.util.StringUtil;

/**
 * JSON handling.
 */
public class JSON extends NativeObject {

	public static final String OBJECTNAME = "JSON";

	public JSON(JSEnvironment env) {
		super(env);
		setOwnProperty(Symbol.TO_STRING_TAG,OBJECTNAME,PropertyDescriptor.DESC_PROP_TOSTRINGTAG);
		
		setOwnMethod(new Method(env,MethodId.parse,2));
		setOwnMethod(new Method(env,MethodId.stringify,3));
		setOwnMethod(new Method(env,MethodId.isRawJSON,1));
		setOwnMethod(new Method(env,MethodId.rawJSON,1));
	}
	
	@Override
	public String getClassName() {
		return OBJECTNAME;
	}
	
	private static enum MethodId {
		parse,
		stringify,
		isRawJSON,
		rawJSON,
	}
	
	private static final class Method extends BaseMethod {
		private MethodId methodId;
		
		private Method(JSEnvironment env, MethodId methodId, int length) {
			super(env,methodId.name(),length);
			this.methodId = methodId;
		}
		
	    @Override
		protected Object invoke(final Object obj, final Object[] args) {
	        switch(methodId) {
	        
            	case parse -> {
        			Object arg0 = param(args, 0, RuntimeUtil.UNDEFINED);
            		if(arg0!=RuntimeUtil.UNDEFINED ) {
            			String json = RuntimeUtil.toString(getEnvironment(),arg0);
            			Object arg1 = param(args, 1, RuntimeUtil.UNDEFINED);
            			JsonParser.StringParser p = new JsonParser.StringParser(getEnvironment().getJsonFactory());
        				p.setStrict(true); // we could relax this...
        				Callable reviverFct = arg1 instanceof Callable c && c.isCallable() ? c : null;
        				// context.source (json-parse-with-source proposal) needs each
        				// primitive literal's ORIGINAL source text, which only exists
        				// during this single pass over the character stream - a
        				// lightweight "recording" reviver captures it (keyed by
        				// container identity + key, matching how InternalizeJSONProperty
        				// re-visits the exact same live container/key pair below)
        				// without altering the tree at all. The originally-parsed VALUE
        				// is recorded alongside its source text (not just the text) so a
        				// forward modification (an earlier sibling's reviver replacing
        				// this not-yet-visited position, e.g. `this[1] = 42` from index
        				// 0's reviver - test262 reviver-forward-modifies-object.js)
        				// invalidates the recorded source: per spec, context.source is
        				// only ever provided when the live value re-fetched below is
        				// STILL the exact value that was originally parsed there.
        				Map<Object,Map<String,SourceRecord>> sourceMap = reviverFct!=null ? new IdentityHashMap<>() : null;
        				if(reviverFct!=null) {
        					p.setReviver( (cont,key,value,ctx) -> {
        						if(ctx!=null) {
        							sourceMap.computeIfAbsent(cont, k -> new HashMap<>()).put(key,new SourceRecord(value,ctx));
        						}
        						return value;
        					});
        				}
        				Object unfiltered;
        				try {
        					unfiltered = p.parse(json);
        				} catch(Exception e) {
        					if(e instanceof JSRuntimeException je) {
            					throw je;
        					}
           					throw RuntimeUtil.syntaxError("Error while parsing JSON");
        				}
        				if(reviverFct==null) {
        					return unfiltered;
        				}
        				// Per spec (JSON.parse step 7 / InternalizeJSONProperty): the
        				// reviver walk is a SEPARATE pass over the ALREADY-fully-parsed
        				// value tree - not something done inline while parsing - so an
        				// earlier sibling's reviver call mutating a not-yet-visited
        				// position (e.g. `this[1] = someProxy` from within index 0's
        				// reviver) is correctly observed when index 1 is visited, via a
        				// live Get() at that moment. GaltaJsJsonFactory's
        				// createObject()/createArray() already produce real, live
        				// GaltaJS JSObject/JSArray instances (no separate "parsed tree"
        				// vs "live object" bridging needed), so the parser's raw result
        				// can be walked directly with ordinary property-access machinery.
        				JSObject root = JSObject.of(getEnvironment(), "", unfiltered);
        				Map<String,SourceRecord> rootSource = sourceMap.remove(null);
        				if(rootSource!=null) {
        					sourceMap.put(root, rootSource);
        				}
        				return internalizeJSONProperty(root, "", reviverFct, sourceMap);
            		}
					throw RuntimeUtil.syntaxError("Error while parsing JSON");
            	}
            	
            	case stringify -> {
        			Object arg0 = param(args, 0, RuntimeUtil.UNDEFINED);
            		if(arg0==RuntimeUtil.UNDEFINED) {
                		return RuntimeUtil.UNDEFINED;
            		}
            		if(arg0 instanceof Symbol && RuntimeUtil.isPrimitiveValue(getEnvironment(), arg0)) {
                		return RuntimeUtil.UNDEFINED;
            		}
        			Object arg1 = param(args, 1, RuntimeUtil.UNDEFINED);
        			Object arg2 = param(args, 2, RuntimeUtil.UNDEFINED);

    				JsonStringifier.StringSerializer w = new JsonStringifier.StringSerializer();
    				// A Proxy is never itself a JsonObject/JsonArray - the
    				// JsonStringifier this delegates to walks a container via
    				// plain Java Map/List calls (get/entrySet/size), with no
    				// notion of a trap - so a Proxy value is "materialized"
    				// into a fresh, real JSObject/JSArray via ordinary
    				// trap-aware property access (see materializeProxy()) the
    				// first time it's encountered anywhere in the tree. This
    				// cache makes every later encounter of the SAME Proxy
    				// instance, within this one stringify() call, resolve to
    				// the SAME materialized container, so a genuinely circular
    				// Proxy structure is still caught by JsonStringifier's own
    				// identity-based circular-reference check instead of
    				// recursing forever.
    				Map<Object,Object> proxyCache = new IdentityHashMap<>();
    				// A raw `instanceof Callable` is true for EVERY BuiltinProxy
    				// regardless of its target (Proxy always implements Callable at
    				// the Java level) - isCallable() is the real, target-aware check
    				// (spec's IsCallable), needed so a Proxy wrapping a non-callable
    				// array (the array-form replacer case, e.g. replacer-array-
    				// proxy.js/replacer-array-abrupt.js) doesn't get wrongly treated
    				// as a function replacer.
    				if(arg1 instanceof Callable c && c.isCallable()) {
    					// Per spec (JSON.stringify steps 9-10): the replacer function's
    					// `this` for the top-level call is a synthetic "wrapper" object
    					// - ObjectCreate(%ObjectPrototype%) with a single own "" data
    					// property holding the root value - not `undefined`/`null`
    					// (which sloppy-mode OrdinaryCallBindThis would otherwise
    					// substitute with the global object). setOwnProperty(), not a
    					// plain property set, so a hostile Object.prototype[""] setter
    					// can't observe/hijack this (test262
    					// replacer-function-wrapper.js installs exactly such a setter).
    					JSObject wrapper = JSObject.create(getEnvironment());
    					getEnvironment().getAccessor(wrapper).setOwnProperty(wrapper, "", arg0, PropertyDescriptor.DESC_DEFAULT, DESC_CHECK.NO_EXCEPTION, wrapper);
    					w.setRootContainer(wrapper);
        				w.setReplacer( (cont,key,value) -> {
        					// Per spec (SerializeJSONProperty), toJSON runs BEFORE the
        					// replacer function - the replacer sees toJSON's result, not
        					// the original value - so this must be applied here rather
        					// than left to standardSerializer() alone, which would only
        					// reach toJSON AFTER the replacer already ran.
        					// Get(holder, key) on a property that no longer EXISTS (e.g.
    						// deleted by an earlier sibling property's own getter, as a side
    						// effect, before this key's turn - see test262
    						// replacer-function-object-deleted-property.js) must yield
    						// `undefined`, not JS `null` - but the value already fetched by
    						// JsonStringifier's outObjectLiteral() (the `value` parameter
    						// here) is a raw Java `null` in both cases, since the underlying
    						// Map.get() can't distinguish "missing" from "present with a null
    						// value". Re-check membership here (still through the same
    						// container, cheap) to recover that distinction before the
    						// replacer function ever observes the value.
        					Object rv = (value==null && cont instanceof JsonObject jo && !jo.containsKey(key)) ? RuntimeUtil.UNDEFINED : value;
        					Object v = c.call(cont,new Object[] {key,applyToJSON(rv,key)});
    						// finalizeValue(), NOT standardSerializer() - toJSON already
    						// had its one chance above; giving the replacer's own result
    						// a second toJSON lookup here would double-invoke it (observable
    						// when e.g. BigInt.prototype.toJSON is set and the replacer
    						// returns a raw BigInt). Delegate undefined/function/primitive-
    						// symbol handling to finalizeValue() too, rather than a manual
    						// pre-check here: finalizeValue() correctly returns `null` (not
    						// Replacer.IGNORE) when `cont` is an array (an array element
    						// becomes JSON `null`, only an object property or the top-level
    						// result is actually dropped) - the removed unconditional-IGNORE
    						// guard bypassed that distinction, wrongly dropping array elements
    						// too (test262 replacer-function-result-undefined.js).
    						return finalizeValue(cont,v,proxyCache);
        				});
    				} else if(RuntimeUtil.isArray(arg1)) {
        				// Per spec (JSON.stringify step 4.b, "PropertyList"): the
        				// replacer array is walked ONCE, up front, before any
        				// serialization starts - a String/Number element (or a
        				// boxed String/Number wrapper) is ToString-coerced and
        				// appended if not already present (de-duplicated, in
        				// first-seen order). Array access itself (length, each
        				// index) goes through the generic accessor system so a
        				// Proxy-backed replacer's traps fire (and any abrupt
        				// completion from them propagates) exactly like a plain
        				// property Get would.
        				List<String> propertyList = new ArrayList<>();
        				long len = RuntimeUtil.toLength(getEnvironment(), RuntimeUtil.getProperty(getEnvironment(), arg1, "length"));
        				for(long i=0; i<len; i++) {
        					Object v = RuntimeUtil.getProperty(getEnvironment(), arg1, i);
        					if(v instanceof CharSequence || v instanceof Number) {
        						String item = RuntimeUtil.toString(getEnvironment(), v);
        						if(!propertyList.contains(item)) {
        							propertyList.add(item);
        						}
        					}
        				}
        				w.setPropertyList(propertyList);
        				w.setReplacer((cont,key,value) -> standardSerializer(cont,key,value,proxyCache));
    				} else {
        				w.setReplacer((cont,key,value) -> standardSerializer(cont,key,value,proxyCache) );
    				}
    				// Per spec, a boxed Number/String `space` argument (has [[NumberData]]/
    				// [[StringData]]) must go through ToNumber/ToString first - which
    				// respects an overridden valueOf/toString - before the raw-primitive
    				// handling below; a plain (non-Number/String) object is left alone.
    				Object space = arg2;
    				if(space instanceof Number && RuntimeUtil.isBoxedNumber(getEnvironment(),space)) {
    					space = RuntimeUtil.toNumber(getEnvironment(),space);
    				} else if(space instanceof CharSequence && RuntimeUtil.isBoxedString(getEnvironment(),space)) {
    					space = RuntimeUtil.toString(getEnvironment(),space);
    				}
    				// The gap: a Number space is clamped to [0,10] spaces; a String
    				// space is truncated to its first 10 code units; an empty gap
    				// must leave the output compact (no indent AND no newlines).
    				String gap = null;
    				// Only a Number is a count of spaces: a BigInt space is ignored (compact output)
    				if(space instanceof Number n && !(n instanceof java.math.BigInteger) && !(n instanceof java.math.BigDecimal)) {
    					int sp = (int)Math.max(0, Math.min(10, n.longValue()));
    					gap = " ".repeat(sp);
    				} else if(space instanceof CharSequence) {
    					String s = space.toString();
    					gap = s.length()>10 ? s.substring(0,10) : s;
    				}
    				if(gap!=null && !gap.isEmpty()) {
    					w.setCompact(false);
    					w.setIndentString(gap);
    				}
    				try {
    					// An empty result string can only happen when the root value
    					// (possibly after toJSON/replacer) was undefined/a function/a
    					// symbol - none of those ever produce real JSON text, even a
    					// literal "" is 2 characters (the quotes) - so JSON.stringify
    					// itself must return undefined (not the empty string) here.
        				String result = w.stringify(arg0);
        				return result.isEmpty() ? RuntimeUtil.UNDEFINED : result;
    				} catch(JsonException ex) {
    					throw RuntimeUtil.typeError(ex.getLocalizedMessage());
    				} catch(IOException ex) {
    					throw RuntimeUtil.wrap(ex);
    				}
            	}
            	
            	case isRawJSON -> {
        			Object arg0 = param(args, 0, RuntimeUtil.UNDEFINED);
        			return arg0 instanceof ReplacerRawJSON;
            	}
            	
            	case rawJSON -> {
        			Object arg0 = param(args, 0, RuntimeUtil.UNDEFINED);
        			if(arg0!=RuntimeUtil.UNDEFINED) {
        				// Spec step 1: ToString(text), for objects too (their toString()/valueOf())
        				{
		    				String s = RuntimeUtil.toString(getEnvironment(), arg0);
		    				// Spec 25.5.7 JSON.rawJSON step 2: throw if jsonString is
		    				// empty, or its first/last code unit is TAB/LF/CR/SPACE -
		    				// checked explicitly rather than relying on the JSON
		    				// parser alone, since a plain parse() would happily
		    				// ACCEPT (and silently ignore) insignificant leading/
		    				// trailing whitespace that the spec specifically forbids
		    				// here (test262 built-ins/JSON/rawJSON/illegal-empty-and-
		    				// start-end-chars.js).
		    				if(!StringUtil.isEmpty(s) && !isIllegalRawJSONEdgeChar(s.charAt(0)) && !isIllegalRawJSONEdgeChar(s.charAt(s.length()-1))) {
			    				try {
			    					Object o = getEnvironment().getJsonFactory().parse(s); // May throw a Syntax error
			    					if(RuntimeUtil.isPrimitiveType(o)) {
			    	   					return new RawJSON(getEnvironment(),s);
			    					}
			    				} catch(Exception ex) {}
		    				}
        				}
        			}
					throw RuntimeUtil.syntaxError("Invalid raw JSON value '{0}'", arg0);
            	}
	            
	            default -> {
	    		    throw new IllegalStateException(); // Should never be here 
	            }
	        }
	    }
	    // The 4 code units JSON.rawJSON forbids as the first/last character
	    // of its argument (spec 25.5.7 step 2): TAB, LF, CR, SPACE.
	    private static boolean isIllegalRawJSONEdgeChar(char c) {
	    	return c=='\t' || c=='\n' || c=='\r' || c==' ';
	    }
	    // InternalizeJSONProperty (json-parse-with-source proposal / spec's
	    // JSON.parse reviver algorithm): recurses depth-first into `holder[name]`
	    // BEFORE calling the reviver on it - each child is re-fetched via a LIVE
	    // Get() at the moment it's visited (not pre-snapshotted), so a sibling's
	    // earlier reviver call mutating a not-yet-visited position is observed,
	    // exactly like a real ordinary-object walk. `sourceMap` supplies
	    // context.source for a primitive value, captured during the original
	    // parse pass (see the `parse` case above).
	    private Object internalizeJSONProperty(Object holder, String name, Callable reviver, Map<Object,Map<String,SourceRecord>> sourceMap) {
	    	Object val = RuntimeUtil.getProperty(getEnvironment(), holder, name);
	    	if(RuntimeUtil.isObject(getEnvironment(), val)) {
	    		if(RuntimeUtil.isArray(val)) {
	    			long len = RuntimeUtil.toLength(getEnvironment(), RuntimeUtil.getProperty(getEnvironment(), val, "length"));
	    			for(long i=0; i<len; i++) {
	    				internalizeJSONPropertyChild(val, Long.toString(i), reviver, sourceMap);
	    			}
	    		} else {
	    			// Own enumerable string keys are snapshotted BEFORE recursing -
	    			// a reviver mutating val's OWN property set mid-walk must not
	    			// change which keys get visited (only the per-key VALUE is
	    			// re-fetched live, via the Get() at the top of each recursive
	    			// call).
	    			List<String> keys = new ArrayList<>();
	    			for(var it = getEnvironment().getAccessor(val).ownStringEntries(val,true); it.hasNext(); ) {
	    				keys.add(it.next().getKey());
	    			}
	    			for(String key : keys) {
	    				internalizeJSONPropertyChild(val, key, reviver, sourceMap);
	    			}
	    		}
	    	}
	    	Map<String,SourceRecord> forHolder = sourceMap.get(holder);
	    	SourceRecord record = forHolder!=null ? forHolder.get(name) : null;
	    	// The recorded source is only still valid if `val` (just re-fetched
	    	// live, above) is STILL the exact value originally parsed at this
	    	// position - an earlier sibling's reviver forward-modifying this
	    	// not-yet-visited slot (test262 reviver-forward-modifies-object.js)
	    	// must yield an undefined source, not the stale original text.
	    	String source = (record!=null && RuntimeUtil.eqSameValue(getEnvironment(), val, record.value())) ? record.source() : null;
	    	JSObject context = JSObject.create(getEnvironment());
	    	if(source!=null) {
	    		context.setOwnProperty("source", source);
	    	}
	    	return reviver.call(holder, new Object[] {name, val, context});
	    }
	    private void internalizeJSONPropertyChild(Object val, String key, Callable reviver, Map<Object,Map<String,SourceRecord>> sourceMap) {
	    	Object newElement = internalizeJSONProperty(val, key, reviver, sourceMap);
	    	if(newElement==RuntimeUtil.UNDEFINED) {
	    		RuntimeUtil.deleteProperty(getEnvironment(), val, key);
	    	} else {
	    		// CreateDataProperty (spec) is a SILENT [[DefineOwnProperty]] - unlike
	    		// CreateDataPropertyOrThrow, an incompatible redefinition (e.g. an
	    		// already non-configurable property) must be silently dropped, not
	    		// throw - NO_EXCEPTION is the check mode that validates but never
	    		// throws, regardless of the ambient calling context's strictness.
	    		getEnvironment().getAccessor(val).setOwnProperty(val, key, newElement, PropertyDescriptor.DESC_DEFAULT, DESC_CHECK.NO_EXCEPTION, val);
	    	}
	    }
	    // The value originally parsed at a given (container,key) position,
	    // alongside its raw source text - see internalizeJSONProperty()'s use
	    // of eqSameValue() against `value` to detect a forward modification.
	    private record SourceRecord(Object value, String source) {}
	    // Just the toJSON-lookup half of standardSerializer()'s logic below
	    // (mirrors both its BigInt/wrapper-object check and its general-object
	    // check exactly, minus the final undefined/NaN/primitive normalization),
	    // extracted so a function replacer can see toJSON's result per spec
	    // step order, without duplicating standardSerializer()'s own later
	    // toJSON re-check running twice observably (a toJSON result generally
	    // has no toJSON of its own, so the harmless re-check inside
	    // standardSerializer() after the replacer runs is a no-op in practice).
	    // Everything standardSerializer() does AFTER its toJSON checks, minus
	    // the SECOND toJSON check for plain objects - used specifically after
	    // a function replacer has already run (spec: toJSON only ever runs
	    // ONCE per SerializeJSONProperty call, strictly before the replacer;
	    // calling standardSerializer() itself here would incorrectly give a
	    // replacer's result a second toJSON chance - observable when the
	    // replacer returns a raw BigInt whose [[Prototype]] carries a toJSON,
	    // e.g. `BigInt.prototype.toJSON = ...` - it must hit the same
	    // "Type(value) is BigInt -> throw" step unconditionally instead).
	    private Object finalizeValue(Object cont, Object value, Map<Object,Object> proxyCache) {
	    	if(value instanceof java.math.BigInteger) {
	    		throw RuntimeUtil.typeError("Do not know how to serialize a BigInt");
	    	}
	    	value = unwrapBoxedPrimitive(value);
	    	if(value==RuntimeUtil.UNDEFINED) {
	    		if(cont==null || cont instanceof JSObjectInternal) {
	    			return Replacer.IGNORE;
	    		}
	    		return null;
	    	} else if(value instanceof Double d) {
	    		if(d.isInfinite() || d.isNaN()) {
	    			return null;
	    		}
	    	} else if(value instanceof Float f) {
	    		if(f.isInfinite() || f.isNaN()) {
	    			return null;
	    		}
	    	} else if(value instanceof Symbol) {
	    		if(RuntimeUtil.isPrimitiveValue(getEnvironment(), value)) {
	    			if(cont==null || cont instanceof JSObjectInternal) {
	    				return Replacer.IGNORE;
	    			}
	    			return null;
	    		}
	    		return RuntimeUtil.getPrimitiveObject(getEnvironment(), value);
	    	} else if(value instanceof Callable c && c.isCallable()) {
	    		// A raw `instanceof Callable` is true for EVERY BuiltinProxy
	    		// regardless of its target (see the matching comment on the
	    		// function-replacer detection above) - isCallable() is the
	    		// real, target-aware check, needed so a non-callable Proxy
	    		// (the ordinary array/object-of-a-Proxy case) falls through to
	    		// materializeProxy() below instead of being wrongly dropped/
	    		// nulled here as if it were itself a function.
	    		if(cont==null || cont instanceof JSObjectInternal) {
	    			return Replacer.IGNORE;
	    		}
	    		return null;
	    	}
	    	return materializeProxy(value, proxyCache);
	    }
	    // Per spec (SerializeJSONProperty step 4), a boxed Number/String/Boolean
	    // wrapper finalizes differently per type - NOT a single generic
	    // ToPrimitive(default) call: Number -> ToNumber (hint number, tries valueOf
	    // first); String -> ToString (hint string, tries toString FIRST - the
	    // default hint would wrongly try valueOf first instead); Boolean -> the raw
	    // [[BooleanData]] value directly, with NO valueOf/toString consultation at
	    // all. Must run BEFORE any NaN/Infinite (or similar primitive-shaped) check
	    // on the caller's side, since a boxed Double must not be treated as if
	    // already a raw double - its own valueOf/toString override needs a chance
	    // to run (and possibly throw) instead of the stale stored value being
	    // silently reused. (A boxed BigInt never reaches here - callers reject it
	    // via `instanceof BigInteger` first.)
	    private Object unwrapBoxedPrimitive(Object value) {
	    	if(RuntimeUtil.hasPropertyMap(getEnvironment(),value)) {
	    		if(value instanceof CharSequence) {
	    			return RuntimeUtil.toString(getEnvironment(),value);
	    		} else if(value instanceof Boolean b) {
	    			return RuntimeUtil.objectAsPrimitive(getEnvironment(),b);
	    		} else if(value instanceof Number) {
	    			return RuntimeUtil.toNumber(getEnvironment(),value);
	    		}
	    	}
	    	return value;
	    }
	    private Object applyToJSON(Object value, String key) {
	    	if(value instanceof java.math.BigInteger || (RuntimeUtil.hasPropertyMap(getEnvironment(),value) && !(value instanceof Symbol))) {
	    		JSAccessor wrapperAcc = getEnvironment().getAccessor(value);
	    		Object toJson = wrapperAcc.getProperty(value,"toJSON",RuntimeUtil.NOT_AVAILABLE);
	    		if(toJson instanceof Callable toJsonFct && toJsonFct.isCallable()) {
	    			return toJsonFct.call(value,new Object[] {key});
	    		}
	    		return value;
	    	}
	    	if(value==RuntimeUtil.UNDEFINED || value instanceof Double || value instanceof Float
	    			|| value instanceof Symbol || RuntimeUtil.isPrimitiveType(value)
	    			|| (value instanceof Callable c && c.isCallable())) {
	    		// (see standardSerializer()'s matching comment for why a raw
	    		// `instanceof Callable` alone would wrongly match every
	    		// non-callable Proxy too, skipping its toJSON lookup below)
	    		return value;
	    	}
	    	JSAccessor acc = getEnvironment().getAccessor(value);
	    	Object fct = acc.getProperty(value,"toJSON",RuntimeUtil.NOT_AVAILABLE);
	    	if(fct instanceof Callable toJson && toJson.isCallable()) {
	    		return toJson.call(value,new Object[] {key});
	    	}
	    	return value;
	    }
	    private Object standardSerializer(Object cont, String key, Object value, Map<Object,Object> proxyCache) {
	    	// Per spec, SerializeJSONProperty checks toJSON first for anything
	    	// whose Type is Object OR BigInt - this includes a boxed Number/
	    	// String/Boolean wrapper object, which the isPrimitiveType() branch
	    	// below would otherwise unwrap directly without ever consulting a
	    	// toJSON the wrapper instance may carry. A raw BigInt with no
	    	// toJSON is not otherwise serializable, so it throws directly.
	    	if(value instanceof java.math.BigInteger || (RuntimeUtil.hasPropertyMap(getEnvironment(),value) && !(value instanceof Symbol))) {
	    		JSAccessor wrapperAcc = getEnvironment().getAccessor(value);
	    		Object toJson = wrapperAcc.getProperty(value,"toJSON",RuntimeUtil.NOT_AVAILABLE);
	    		if(toJson instanceof Callable toJsonFct && toJsonFct.isCallable()) {
	    			return standardSerializer(cont, key, toJsonFct.call(value,new Object[] {key}), proxyCache);
	    		}
	    		if(value instanceof java.math.BigInteger) {
	    			throw RuntimeUtil.typeError("Do not know how to serialize a BigInt");
	    		}
	    	}
	    	// Must run BEFORE the NaN/Infinite check below - a boxed Double must not
	    	// be treated as if already a raw double (see unwrapBoxedPrimitive()'s
	    	// comment for the full rationale).
	    	value = unwrapBoxedPrimitive(value);
	    	if(value==RuntimeUtil.UNDEFINED) {
    			if(cont==null || cont instanceof JSObjectInternal) {
    				return Replacer.IGNORE;
    			}
    			return null;
	    	} else if(value instanceof Double d) {
    			if(d.isInfinite() || d.isNaN()) {
    				return null;
    			}
	    	} else if(value instanceof Float f) {
    			if(f.isInfinite() || f.isNaN()) {
    				return null;
    			}
	    	} else if(value instanceof Symbol) {
	    		if(RuntimeUtil.isPrimitiveValue(getEnvironment(), value)) {
	    			if(cont==null || cont instanceof JSObjectInternal) {
	    				return Replacer.IGNORE;
	    			}
	    			return null;
	    		}
	    		// Just a regular object
	    		return RuntimeUtil.getPrimitiveObject(getEnvironment(), value);
	    	} else if(value instanceof Callable c && c.isCallable()) {
	    		// A raw `instanceof Callable` is true for EVERY BuiltinProxy
	    		// regardless of its target (see the matching comment on the
	    		// function-replacer detection above, near the top of this
	    		// class) - isCallable() is the real, target-aware check,
	    		// needed so a non-callable Proxy (e.g. one wrapping a plain
	    		// array/object - value-array-proxy.js/value-object-proxy.js)
	    		// falls through to the toJSON lookup and materializeProxy()
	    		// below instead of being wrongly dropped/nulled here as if it
	    		// were itself a function.
    			if(cont==null || cont instanceof JSObjectInternal) {
    				return Replacer.IGNORE;
    			}
	    		return null;
	    	} else if(RuntimeUtil.isPrimitiveType(value)) {
	    		// null, or an already-unwrapped-if-boxed String/Number/Boolean - the
	    		// toJSON lookup below is only meaningful for a genuine object.
	    		return value;
	    	}
	    	JSAccessor acc = getEnvironment().getAccessor(value);
	    	Object fct = acc.getProperty(value,"toJSON",RuntimeUtil.NOT_AVAILABLE);
	    	if(fct!=RuntimeUtil.NOT_AVAILABLE) {
		    	if(fct instanceof Callable toJson && toJson.isCallable()) {
		    		// The toJSON result is itself a JS value straight from user code
		    		// (could be undefined, another object with its own toJSON, a
		    		// function, NaN, ...) - it must be re-run through this same
		    		// serialization logic, not returned as if already JSON-safe.
		    		return standardSerializer(cont, key, toJson.call(value,new Object[] {key}), proxyCache);
		    	}
	    	}

	    	return materializeProxy(value, proxyCache);
	    }
	    // SerializeJSONProperty step 10 / SerializeJSONArray / SerializeJSONObject:
	    // a Proxy is never itself a `org.monflabs.json.JsonObject`/`JsonArray` -
	    // the JsonStringifier this whole class delegates to walks a container
	    // via plain Java Map/List calls (get()/entrySet()/size()), with no
	    // notion of a trap at all, unlike GaltaJS's own JSObjectImpl/
	    // JSArrayImpl (which directly ARE a JsonObject/JsonArray, so a plain
	    // object/array never needs any of this). So a Proxy reaching here (no
	    // toJSON of its own) is "materialized" into a fresh, real JSObject/
	    // JSArray, populated through the SAME ordinary, trap-aware property-
	    // access machinery for-in/Object.keys/spread already use elsewhere in
	    // this engine (RuntimeUtil.isArray/getProperty/toLength,
	    // JSAccessor.ownStringEntries) - so every trap (ownKeys,
	    // getOwnPropertyDescriptor, get, and - via IsArray - the revoked
	    // check) fires exactly once, in spec order, and any abrupt completion
	    // (a throwing trap, or ToLength/ToNumber failing on a non-numeric
	    // "length") propagates naturally. The populated container holds RAW
	    // values (not yet toJSON/replacer-processed) - JsonStringifier then
	    // walks it exactly like any other container, invoking the replacer
	    // for each entry, so a Proxy nested arbitrarily deep (including a
	    // Proxy-of-a-Proxy) re-enters this same method normally. `proxyCache`
	    // gives every recurrence of the SAME Proxy instance, within one
	    // JSON.stringify() call, the SAME materialized container back, so a
	    // genuinely circular Proxy structure is still caught by
	    // JsonStringifier's own identity-based circular-reference check,
	    // instead of recursing forever.
	    private Object materializeProxy(Object value, Map<Object,Object> proxyCache) {
	    	if(!(value instanceof BuiltinProxy)) {
	    		return value;
	    	}
	    	Object cached = proxyCache.get(value);
	    	if(cached!=null) {
	    		return cached;
	    	}
	    	// IsArray (spec 7.2.2) is also, precisely, where a revoked proxy
	    	// (anywhere in a chain of proxies-of-proxies) must throw a
	    	// TypeError - regardless of which branch (array or object) it
	    	// would otherwise have taken (value-array-proxy-revoked.js/
	    	// value-object-proxy-revoked.js).
	    	if(RuntimeUtil.isArray(value)) {
	    		JSArray arr = JSArray.create(getEnvironment());
	    		proxyCache.put(value, arr);
	    		long len = RuntimeUtil.toLength(getEnvironment(), RuntimeUtil.getProperty(getEnvironment(), value, "length"));
	    		for(long i=0; i<len; i++) {
	    			arr.setOwnProperty(i, RuntimeUtil.getProperty(getEnvironment(), value, i));
	    		}
	    		return arr;
	    	}
	    	JSObject obj = JSObject.create(getEnvironment());
	    	proxyCache.put(value, obj);
	    	for(var it = getEnvironment().getAccessor(value).ownStringEntries(value,true); it.hasNext(); ) {
	    		var e = it.next();
	    		obj.setOwnProperty(e.getKey(), e.getValue());
	    	}
	    	return obj;
	    }
	}
}