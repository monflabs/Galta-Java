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
package org.monflabs.galtajs.rt.builtins.standard.regexp;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.jsonfactory.JSObject.DESC_CHECK;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.BasePrototype;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;

/**
 * RegExp prototype.
 */
public class RegExpPrototype extends BasePrototype {

	public static RegExpPrototype get(JSEnvironment env) {
		RegExpPrototype proto = (RegExpPrototype)env.getRegisteredPrototype(RegExpPrototype.class);
		if(proto==null) {
			proto = new RegExpPrototype(env);
			env.registerPrototype(RegExpPrototype.class,proto);
		}
		return proto;
	}
	
	// Kept to distinguish, by identity, "receiver has the genuine
	// unmodified RegExp.prototype.exec" from "receiver has a custom exec
	// override" - see the `search` fast path below.
	private final Method builtinExec;

	private RegExpPrototype(JSEnvironment env) {
		super(env);
		builtinExec = new Method(env,MethodId.exec,1);
		setOwnMethod(builtinExec);
		setOwnMethod(new Method(env,MethodId.test,1));
		setOwnMethod(new Method(env,MethodId.toString,0));
		
		setOwnMethod(new Method(env,MethodId.match,1));
		setOwnMethod(new Method(env,MethodId.matchall,1));
		setOwnMethod(new Method(env,MethodId.replace,2));
		setOwnMethod(new Method(env,MethodId.search,1));
		setOwnMethod(new Method(env,MethodId.split,2));
		
		if(env.isDeprecatedApis()) {
			setOwnMethod(new Method(env,MethodId.compile,2));
		}

		
		setOwnProperty("dotAll",true,false,
				(t,k) -> flagOrSpecialCase(t,"dotAll",RegExp::isDotAll),
				null);
		setOwnProperty("flags",true,false,
				(t,k) -> getFlagsGeneric(t),
				null);
		setOwnProperty("global",true,false,
				(t,k) -> flagOrSpecialCase(t,"global",RegExp::isGlobal),
				null);
		setOwnProperty("hasIndices",true,false,
				(t,k) -> flagOrSpecialCase(t,"hasIndices",RegExp::isHasIndices),
				null);
		setOwnProperty("ignoreCase",true,false,
				(t,k) -> flagOrSpecialCase(t,"ignoreCase",RegExp::isIgnoreCase),
				null);
		setOwnProperty("multiline",true,false,
				(t,k) -> flagOrSpecialCase(t,"multiline",RegExp::isMultiline),
				null);
		setOwnProperty("unicode",true,false,
				(t,k) -> flagOrSpecialCase(t,"unicode",RegExp::isUnicode),
				null);
		setOwnProperty("unicodeSets",true,false,
				(t,k) -> flagOrSpecialCase(t,"unicodeSets",RegExp::isUnicodeSets),
				null);
		setOwnProperty("source",true,false,
				(t,k) -> {
					if(t instanceof RegExp re) {
						return re.getEscapedSource();
					}
					// Per spec, RegExp.prototype (the receiver itself, having
					// no [[OriginalSource]] slot) is the ONE special case that
					// doesn't throw - it returns the literal "(?:)" rather
					// than undefined (unlike every other flag accessor here).
					if(t==RegExpPrototype.this) {
						return "(?:)";
					}
					throw RuntimeUtil.typeError("Property RegExp.prototype.source called on incompatible receiver {0}", t!=null?t.getClass():"null");
				},
				null);
		setOwnProperty("sticky",true,false,
				(t,k) -> flagOrSpecialCase(t,"sticky",RegExp::isSticky),
				null);
	}
//	jsPut("lastIndex",0,PropertyDescriptor.DESC_HIDDEN_PROP);
//    - compile
//    - constructor
//    - unicodeSets

	// Per spec, each individual flag accessor (global/ignoreCase/multiline/
	// dotAll/unicode/unicodeSets/sticky/hasIndices) requires `this` to be a
	// genuine RegExp UNLESS `this` is %RegExp.prototype% itself (no
	// [[OriginalFlags]] slot), in which case it returns undefined rather
	// than throwing - this lets the generic `flags` getter below (which
	// does a plain property Get for each of these) produce "" instead of
	// throwing when called on RegExp.prototype directly.
	private Object flagOrSpecialCase(Object t, String propName, java.util.function.Predicate<RegExp> flag) {
		if(t instanceof RegExp re) {
			return flag.test(re);
		}
		if(t==this) {
			return RuntimeUtil.UNDEFINED;
		}
		throw RuntimeUtil.typeError("Property RegExp.prototype.{0} called on incompatible receiver {1}", propName, t!=null?t.getClass():"null");
	}

	// get RegExp.prototype.flags: spec's algorithm is fully generic - it
	// works on ANY object with the right own properties (RequireObjectCoercible
	// + Get + ToBoolean for each flag letter, in this exact d/g/i/m/s/u/v/y
	// order), NOT restricted to genuine RegExp instances the way the
	// individual flag accessors are.
	private String getFlagsGeneric(Object t) {
		if(!RuntimeUtil.isObject(getEnvironment(),t)) {
			throw RuntimeUtil.typeError("RegExp.prototype.flags called on non-object receiver");
		}
		JSAccessor acc = getEnvironment().getAccessor(t);
		StringBuilder sb = new StringBuilder();
		if(RuntimeUtil.toBoolean(getEnvironment(),acc.getProperty(t,"hasIndices",RuntimeUtil.UNDEFINED))) sb.append('d');
		if(RuntimeUtil.toBoolean(getEnvironment(),acc.getProperty(t,"global",RuntimeUtil.UNDEFINED))) sb.append('g');
		if(RuntimeUtil.toBoolean(getEnvironment(),acc.getProperty(t,"ignoreCase",RuntimeUtil.UNDEFINED))) sb.append('i');
		if(RuntimeUtil.toBoolean(getEnvironment(),acc.getProperty(t,"multiline",RuntimeUtil.UNDEFINED))) sb.append('m');
		if(RuntimeUtil.toBoolean(getEnvironment(),acc.getProperty(t,"dotAll",RuntimeUtil.UNDEFINED))) sb.append('s');
		if(RuntimeUtil.toBoolean(getEnvironment(),acc.getProperty(t,"unicode",RuntimeUtil.UNDEFINED))) sb.append('u');
		if(RuntimeUtil.toBoolean(getEnvironment(),acc.getProperty(t,"unicodeSets",RuntimeUtil.UNDEFINED))) sb.append('v');
		if(RuntimeUtil.toBoolean(getEnvironment(),acc.getProperty(t,"sticky",RuntimeUtil.UNDEFINED))) sb.append('y');
		return sb.toString();
	}
	
	private static enum MethodId {
		compile,
		exec,
		test,
		toString,
		// Symbols
		match(Symbol.MATCH),
		matchall(Symbol.MATCH_ALL),
		replace(Symbol.REPLACE),
		search(Symbol.SEARCH),
		split(Symbol.SPLIT),
		;
		Object id;
		MethodId() {
			this.id = name();
		}
		MethodId(Symbol id) {
			this.id = id;
		}
	}
	
	private final class Method extends BaseMethod {
		private MethodId methodId;
		
		private Method(JSEnvironment env, MethodId methodId, int length) {
			super(env,methodId.id,length);
			this.methodId = methodId;
		}
		
		@Override
		protected Object invoke(final Object obj, final Object[] args) {
	    	// test/search/toString are spec-generic - they work on ANY
	    	// object (consulting a possibly-custom "exec"/"source"/"flags"
	    	// property via RegExpExec/Get), not just genuine RegExp
	    	// instances - handled before the RegExp-only gate below.
	    	switch(methodId) {
	    		case test: {
	    			if(!RuntimeUtil.isObject(getEnvironment(),obj)) {
	    				throw RuntimeUtil.typeError("Method RegExp.prototype.test called on incompatible receiver");
	    			}
	    			String s = RuntimeUtil.toString(getEnvironment(), param(args, 0, RuntimeUtil.UNDEFINED));
	    			return regExpExec(obj,s)!=null;
	    		}
	    		case search: {
	    			if(!RuntimeUtil.isObject(getEnvironment(),obj)) {
	    				throw RuntimeUtil.typeError("Method RegExp.prototype[Symbol.search] called on incompatible receiver");
	    			}
	    			String s = RuntimeUtil.toString(getEnvironment(), param(args, 0, RuntimeUtil.UNDEFINED));
	    			JSAccessor acc = getEnvironment().getAccessor(obj);
	    			// Fast path: no custom "exec" override, delegate to the
	    			// existing dedicated (stateless, one-shot) search engine
	    			// method directly, rather than through the generic
	    			// RegExpExec/exec() path below - exec()'s internal Matcher
	    			// is cached and only rebound to a new target string when
	    			// the regex is non-global/non-sticky OR the cached Matcher
	    			// is null, which search()'s one-shot semantics (unrelated
	    			// to any prior exec()/test() call's state) violates for a
	    			// global/sticky regex searched against a DIFFERENT string
	    			// than its last exec() call used.
	    			if(obj instanceof RegExp re && acc.getProperty(obj,"exec",RuntimeUtil.UNDEFINED)==builtinExec) {
	    				return re.search(JSRuntimeContext.get(), s);
	    			}
	    			Object previousLastIndex = acc.getProperty(obj,"lastIndex",RuntimeUtil.UNDEFINED);
	    			if(!RuntimeUtil.eqSameValue(getEnvironment(),previousLastIndex,0)) {
	    				acc.setOwnProperty(obj,"lastIndex",0,null,DESC_CHECK.STRICT,obj);
	    			}
	    			Object result = regExpExec(obj,s);
	    			Object currentLastIndex = acc.getProperty(obj,"lastIndex",RuntimeUtil.UNDEFINED);
	    			if(!RuntimeUtil.eqSameValue(getEnvironment(),currentLastIndex,previousLastIndex)) {
	    				acc.setOwnProperty(obj,"lastIndex",previousLastIndex,null,DESC_CHECK.STRICT,obj);
	    			}
	    			if(result==null) {
	    				return -1;
	    			}
	    			return acc.getProperty(result,"index",RuntimeUtil.UNDEFINED);
	    		}
	    		case toString: {
	    			if(!RuntimeUtil.isObject(getEnvironment(),obj)) {
	    				throw RuntimeUtil.typeError("Method RegExp.prototype.toString called on incompatible receiver");
	    			}
	    			JSAccessor acc = getEnvironment().getAccessor(obj);
	    			String pattern = RuntimeUtil.toString(getEnvironment(), acc.getProperty(obj,"source",RuntimeUtil.UNDEFINED));
	    			String flags = RuntimeUtil.toString(getEnvironment(), acc.getProperty(obj,"flags",RuntimeUtil.UNDEFINED));
	    			return "/"+pattern+"/"+flags;
	    		}
	    		case match: {
	    			if(!RuntimeUtil.isObject(getEnvironment(),obj)) {
	    				throw RuntimeUtil.typeError("Method RegExp.prototype[Symbol.match] called on incompatible receiver");
	    			}
	    			String s = RuntimeUtil.toString(getEnvironment(), param(args, 0, RuntimeUtil.UNDEFINED));
	    			JSAccessor acc = getEnvironment().getAccessor(obj);
	    			// Per the current spec text, a single Get(rx,"flags") is read
	    			// (as a string) and inspected via string containment checks -
	    			// NOT separate Get(rx,"global")/Get(rx,"unicode") reads (the
	    			// older ES2015 algorithm this superseded).
	    			String flags = RuntimeUtil.toString(getEnvironment(), acc.getProperty(obj,"flags",RuntimeUtil.UNDEFINED));
	    			boolean global = flags.indexOf('g')>=0;
	    			if(!global) {
	    				return regExpExec(obj,s);
	    			}
	    			boolean fullUnicode = flags.indexOf('u')>=0 || flags.indexOf('v')>=0;
	    			acc.setOwnProperty(obj,"lastIndex",0,null,DESC_CHECK.STRICT,obj);
	    			JSArray a = JSArray.create(getEnvironment());
	    			int n = 0;
	    			while(true) {
	    				Object result = regExpExec(obj,s);
	    				if(result==null) {
	    					return n==0 ? null : a;
	    				}
	    				JSAccessor resultAcc = getEnvironment().getAccessor(result);
	    				String matchStr = RuntimeUtil.toString(getEnvironment(), resultAcc.getProperty(result,"0",RuntimeUtil.UNDEFINED));
	    				a.arrayAdd(matchStr);
	    				if(matchStr.isEmpty()) {
	    					long lastIndex = RuntimeUtil.toLength(getEnvironment(), acc.getProperty(obj,"lastIndex",RuntimeUtil.UNDEFINED));
	    					acc.setOwnProperty(obj,"lastIndex",advanceStringIndex(s,lastIndex,fullUnicode),null,DESC_CHECK.STRICT,obj);
	    				}
	    				n++;
	    			}
	    		}
	    		case matchall: {
	    			// RegExp.prototype[@@matchAll] does NOT require the global
	    			// flag (a non-global regexp's iterator matches once, then
	    			// stops) - unlike String.prototype.matchAll, which checks
	    			// for it before ever calling this method.
	    			if(!RuntimeUtil.isObject(getEnvironment(),obj)) {
	    				throw RuntimeUtil.typeError("Method RegExp.prototype[Symbol.matchAll] called on incompatible receiver");
	    			}
	    			String s = RuntimeUtil.toString(getEnvironment(), param(args, 0, RuntimeUtil.UNDEFINED));
	    			JSAccessor acc = getEnvironment().getAccessor(obj);
	    			// A fresh clone (via SpeciesConstructor) is matched against,
	    			// not `obj` itself, so that iterating doesn't observably
	    			// mutate the original regexp's own lastIndex.
	    			Constructor defaultCtor = (Constructor)getEnvironment().getStandardObjects().getConstructor(RegExpConstructor.CLASSNAME);
	    			Constructor c = RuntimeUtil.speciesConstructor(getEnvironment(), obj, defaultCtor);
	    			String flags = RuntimeUtil.toString(getEnvironment(), acc.getProperty(obj,"flags",RuntimeUtil.UNDEFINED));
	    			Object matcher = c.constructObject(new Object[]{obj, flags}, c);
	    			JSAccessor matcherAcc = getEnvironment().getAccessor(matcher);
	    			double lastIndex = RuntimeUtil.toDouble(getEnvironment(), acc.getProperty(obj,"lastIndex",RuntimeUtil.UNDEFINED));
	    			matcherAcc.setOwnProperty(matcher,"lastIndex",lastIndex,null,DESC_CHECK.STRICT,matcher);
	    			boolean global = flags.indexOf('g')>=0;
	    			boolean fullUnicode = flags.indexOf('u')>=0 || flags.indexOf('v')>=0;
	    			return new BuiltinRegExpIterator(getEnvironment(), regExpStringIterator(matcher,s,global,fullUnicode));
	    		}
	    		case split: {
	    			if(!RuntimeUtil.isObject(getEnvironment(),obj)) {
	    				throw RuntimeUtil.typeError("Method RegExp.prototype[Symbol.split] called on incompatible receiver");
	    			}
	    			String s = RuntimeUtil.toString(getEnvironment(), param(args, 0, RuntimeUtil.UNDEFINED));
	    			JSAccessor acc = getEnvironment().getAccessor(obj);
	    			// Fast path: a genuine RegExp with the unmodified prototype's
	    			// "exec" bypasses the spec-generic sticky-splitter loop (which
	    			// does dozens of dynamic property reads/writes per iteration)
	    			// and delegates directly to RegExpEngine.split. Gated on:
	    			// - non-empty subject: the empty-subject case has a spec
	    			//   observable difference (returns [] iff the pattern matches
	    			//   "", else [""]) that RegExpEngine.split doesn't replicate.
	    			// - not /u or /v: empty-match advance in the engine's split
	    			//   uses "one UTF-16 unit", not "one code point", so splitting
	    			//   an empty-matching /u regexp across a surrogate pair could
	    			//   diverge from spec.
	    			// ALSO requires no own "constructor" property - the generic
	    			// path below consults SpeciesConstructor(rx,%RegExp%), which
	    			// this fast path skips entirely; that's only safe when
	    			// Get(rx,"constructor") would genuinely resolve to the real
	    			// %RegExp% constructor (the common case, when "constructor"
	    			// isn't shadowed as an own property - confirmed via
	    			// species-ctor.js/-err.js/-species-non-ctor.js: an own
	    			// "constructor" with a custom @@species must have its
	    			// species constructor actually invoked to build the
	    			// splitter, not be silently bypassed).
	    			// ALSO requires no own Symbol.match property - the generic
	    			// path's splitter construction (`new C(rx, newFlags)`, C
	    			// normally %RegExp%) runs the real RegExp constructor, whose
	    			// very first step is IsRegExp(rx) - a Get(rx, @@match) that
	    			// this fast path never performs at all. A getter there can
	    			// have an observable side effect (e.g. calling rx.compile(...)
	    			// to mutate rx's own source/flags mid-algorithm) that MUST
	    			// fire before the splitter's pattern/flags are read - fixes
	    			// annexB/built-ins/RegExp/prototype/Symbol.split/
	    			// Symbol.match-getter-recompiles-source.js, which was
	    			// splitting against the pre-mutation pattern since this fast
	    			// path skipped the trigger entirely.
	    			if(s.length()>0 && obj instanceof RegExp re
	    					&& !re.isUnicode() && !re.isUnicodeSets()
	    					&& acc.getProperty(obj,"exec",RuntimeUtil.UNDEFINED)==builtinExec
	    					&& acc.getOwnPropertyDescriptor(obj,"constructor")==null
	    					&& acc.getOwnPropertyDescriptor(obj,Symbol.MATCH)==null) {
	    				// Snapshot the engine BEFORE converting `limit` - the
	    				// spec's real algorithm constructs its splitter (from rx's
	    				// pattern/flags AS THEY ARE at that point) earlier, before
	    				// ToUint32(limit) ever runs, so a poisoned limit.valueOf()
	    				// that calls rx.compile(...) (replacing rx's engine with
	    				// one reflecting a NEW pattern) must not affect this split.
	    				RegExpEngine engineFast = re.getRegExpEngine();
	    				Object limitArgFast = param(args, 1, RuntimeUtil.UNDEFINED);
	    				long limFast = limitArgFast==RuntimeUtil.UNDEFINED ? 0xFFFFFFFFL : RuntimeUtil.toUInt32(getEnvironment(), limitArgFast);
	    				int limInt = limFast>Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)limFast;
	    				return engineFast.split(JSRuntimeContext.get(), s, limInt);
	    			}
	    			Constructor defaultCtor = (Constructor)getEnvironment().getStandardObjects().getConstructor(RegExpConstructor.CLASSNAME);
	    			Constructor c = RuntimeUtil.speciesConstructor(getEnvironment(), obj, defaultCtor);
	    			String flags = RuntimeUtil.toString(getEnvironment(), acc.getProperty(obj,"flags",RuntimeUtil.UNDEFINED));
	    			boolean unicodeMatching = flags.indexOf('u')>=0 || flags.indexOf('v')>=0;
	    			// The splitter always matches STICKY (lastIndex-anchored),
	    			// regardless of the original regexp's own flags - "y" is
	    			// force-added if not already present.
	    			String newFlags = flags.indexOf('y')>=0 ? flags : flags+"y";
	    			Object splitter = c.constructObject(new Object[]{obj, newFlags}, c);
	    			JSAccessor splitterAcc = getEnvironment().getAccessor(splitter);
	    			JSArray a = JSArray.create(getEnvironment());
	    			Object limitArg = param(args, 1, RuntimeUtil.UNDEFINED);
	    			long lim = limitArg==RuntimeUtil.UNDEFINED ? 0xFFFFFFFFL : RuntimeUtil.toUInt32(getEnvironment(), limitArg);
	    			if(lim==0) {
	    				return a;
	    			}
	    			int size = s.length();
	    			if(size==0) {
	    				if(regExpExec(splitter,s)==null) {
	    					a.arrayAdd(s);
	    				}
	    				return a;
	    			}
	    			int p = 0;
	    			int q = 0;
	    			while(q<size) {
	    				splitterAcc.setOwnProperty(splitter,"lastIndex",q,null,DESC_CHECK.STRICT,splitter);
	    				Object z = regExpExec(splitter,s);
	    				if(z==null) {
	    					q = (int)advanceStringIndex(s,q,unicodeMatching);
	    					continue;
	    				}
	    				JSAccessor zAcc = getEnvironment().getAccessor(z);
	    				int e = (int)Math.min(RuntimeUtil.toLength(getEnvironment(), splitterAcc.getProperty(splitter,"lastIndex",RuntimeUtil.UNDEFINED)), (long)size);
	    				if(e==p) {
	    					q = (int)advanceStringIndex(s,q,unicodeMatching);
	    					continue;
	    				}
	    				a.arrayAdd(s.substring(p,q));
	    				if(a.arrayLength()==lim) {
	    					return a;
	    				}
	    				p = e;
	    				int numberOfCaptures = (int)Math.max(RuntimeUtil.toLength(getEnvironment(), zAcc.getProperty(z,"length",RuntimeUtil.UNDEFINED))-1, 0);
	    				for(int i=1; i<=numberOfCaptures; i++) {
	    					a.arrayAdd(zAcc.getProperty(z,String.valueOf(i),RuntimeUtil.UNDEFINED));
	    					if(a.arrayLength()==lim) {
	    						return a;
	    					}
	    				}
	    				q = p;
	    			}
	    			a.arrayAdd(s.substring(p,size));
	    			return a;
	    		}
	    		case replace: {
	    			if(!RuntimeUtil.isObject(getEnvironment(),obj)) {
	    				throw RuntimeUtil.typeError("Method RegExp.prototype[Symbol.replace] called on incompatible receiver");
	    			}
	    			String s = RuntimeUtil.toString(getEnvironment(), param(args, 0, RuntimeUtil.UNDEFINED));
	    			int lengthS = s.length();
	    			Object replaceValueArg = param(args, 1, RuntimeUtil.UNDEFINED);
	    			boolean functionalReplace = replaceValueArg instanceof Callable;
	    			Callable replaceFn = functionalReplace ? (Callable)replaceValueArg : null;
	    			String replaceTemplate = functionalReplace ? null : RuntimeUtil.toString(getEnvironment(), replaceValueArg);

	    			JSAccessor acc = getEnvironment().getAccessor(obj);
	    			// Fast path: a genuine RegExp with the unmodified prototype's
	    			// "exec" and a non-Callable replacement can bypass the two-phase
	    			// spec-generic loop and go straight to RegExpEngine.replace,
	    			// which is a single matcher.find()-driven walk. Non-Callable is
	    			// required because the engine's replace does not honor named
	    			// group references ($<name>) in its substitution template - the
	    			// generic path below handles those via getSubstitution.
	    			// ALSO requires "global"/"unicode"/"unicodeSets" to be
	    			// unmodified (no own property shadowing the prototype's
	    			// accessor) - re.replace() consults the REGEXP'S OWN internal
	    			// flags directly, not a genuine Get(rx,"flags") read, so an
	    			// overridden "global" (confirmed via
	    			// Symbol.replace/coerce-global.js: `Object.defineProperty(r,
	    			// 'global',{writable:true}); r.global=undefined;` must make
	    			// replace non-global even though the regexp itself is still
	    			// internally /g) would otherwise be silently ignored by this
	    			// fast path.
	    			if(!functionalReplace && obj instanceof RegExp re
	    					&& acc.getProperty(obj,"exec",RuntimeUtil.UNDEFINED)==builtinExec
	    					&& acc.getOwnPropertyDescriptor(obj,"global")==null
	    					&& acc.getOwnPropertyDescriptor(obj,"unicode")==null
	    					&& acc.getOwnPropertyDescriptor(obj,"unicodeSets")==null
	    					&& replaceTemplate.indexOf("$<")<0) {
	    				return re.replace(JSRuntimeContext.get(), s, replaceTemplate);
	    			}
	    			// Per the current spec text, a single Get(rx,"flags") is
	    			// read (as a string) and inspected via containment checks -
	    			// NOT separate Get(rx,"global")/Get(rx,"unicode") reads (the
	    			// older ES2015 algorithm this superseded) - same fix shape
	    			// as Symbol.match above.
	    			String flags = RuntimeUtil.toString(getEnvironment(), acc.getProperty(obj,"flags",RuntimeUtil.UNDEFINED));
	    			boolean global = flags.indexOf('g')>=0;
	    			boolean fullUnicode = flags.indexOf('u')>=0 || flags.indexOf('v')>=0;
	    			if(global) {
	    				acc.setOwnProperty(obj,"lastIndex",0,null,DESC_CHECK.STRICT,obj);
	    			}

	    			// Phase 1: collect every match FIRST (advancing lastIndex as
	    			// we go for a global regexp) - substitution happens only
	    			// after every match has already been found, per spec.
	    			List<Object> results = new ArrayList<>();
	    			boolean done = false;
	    			while(!done) {
	    				Object result = regExpExec(obj,s);
	    				if(result==null) {
	    					done = true;
	    				} else {
	    					results.add(result);
	    					if(!global) {
	    						done = true;
	    					} else {
	    						JSAccessor resultAcc = getEnvironment().getAccessor(result);
	    						String matchStr = RuntimeUtil.toString(getEnvironment(), resultAcc.getProperty(result,"0",RuntimeUtil.UNDEFINED));
	    						if(matchStr.isEmpty()) {
	    							long thisIndex = RuntimeUtil.toLength(getEnvironment(), acc.getProperty(obj,"lastIndex",RuntimeUtil.UNDEFINED));
	    							acc.setOwnProperty(obj,"lastIndex",advanceStringIndex(s,thisIndex,fullUnicode),null,DESC_CHECK.STRICT,obj);
	    						}
	    					}
	    				}
	    			}

	    			// Phase 2: build the replacement string from the collected matches.
	    			StringBuilder accumulated = new StringBuilder();
	    			int nextSourcePosition = 0;
	    			for(Object result : results) {
	    				JSAccessor resultAcc = getEnvironment().getAccessor(result);
	    				long resultLength = RuntimeUtil.toLength(getEnvironment(), resultAcc.getProperty(result,"length",RuntimeUtil.UNDEFINED));
	    				int nCaptures = (int)Math.max(resultLength-1, 0);
	    				String matched = RuntimeUtil.toString(getEnvironment(), resultAcc.getProperty(result,"0",RuntimeUtil.UNDEFINED));
	    				int matchedLength = matched.length();
	    				int position = (int)RuntimeUtil.toDouble(getEnvironment(), resultAcc.getProperty(result,"index",RuntimeUtil.UNDEFINED));
	    				position = Math.max(0, Math.min(position, lengthS));
	    				List<Object> captures = new ArrayList<>();
	    				for(int n=1; n<=nCaptures; n++) {
	    					Object capN = resultAcc.getProperty(result,String.valueOf(n),RuntimeUtil.UNDEFINED);
	    					if(capN!=RuntimeUtil.UNDEFINED) {
	    						capN = RuntimeUtil.toString(getEnvironment(),capN);
	    					}
	    					captures.add(capN);
	    				}
	    				Object namedCaptures = resultAcc.getProperty(result,"groups",RuntimeUtil.UNDEFINED);
	    				String replacement;
	    				if(functionalReplace) {
	    					List<Object> replacerArgs = new ArrayList<>();
	    					replacerArgs.add(matched);
	    					replacerArgs.addAll(captures);
	    					replacerArgs.add(position);
	    					replacerArgs.add(s);
	    					if(namedCaptures!=RuntimeUtil.UNDEFINED) {
	    						replacerArgs.add(namedCaptures);
	    					}
	    					Object replValue = replaceFn.call(RuntimeUtil.UNDEFINED, replacerArgs.toArray());
	    					replacement = RuntimeUtil.toString(getEnvironment(), replValue);
	    				} else {
	    					// Per spec, a non-undefined namedCaptures is passed
	    					// through ToObject before GetSubstitution - ToObject
	    					// only throws for null/undefined (already excluded
	    					// above), NOT for other primitives (a string/number/
	    					// boolean is boxed, not rejected) - so e.g. a literal
	    					// `groups: null` (a real value, not simply absent)
	    					// must throw here, but `groups: "123"` must not (its
	    					// wrapper's own "length" becomes property-readable).
	    					if(namedCaptures==null) {
	    						throw RuntimeUtil.typeError("Cannot convert undefined or null to object");
	    					}
	    					replacement = getSubstitution(matched,s,position,captures,namedCaptures,replaceTemplate);
	    				}
	    				if(position>=nextSourcePosition) {
	    					accumulated.append(s,nextSourcePosition,position);
	    					accumulated.append(replacement);
	    					nextSourcePosition = position+matchedLength;
	    				}
	    			}
	    			if(nextSourcePosition>=lengthS) {
	    				return accumulated.toString();
	    			}
	    			return accumulated.append(s.substring(nextSourcePosition)).toString();
	    		}
	    		default: {
	    			// Fall through to the RegExp-only methods below.
	    		}
	    	}

	    	if(!(obj instanceof RegExp)) {
	    		throw RuntimeUtil.typeError("Method RegExp.prototype.{0} called on incompatible receiver", methodId.toString());
	    	}

	    	// Current Object
			final RegExp _this = (RegExp)obj;

	    	switch(methodId){
    			case compile: {
    				// A subclass instance's OWN [[Prototype]] is the subclass's
    				// own prototype object, not %RegExp.prototype% directly -
    				// compile() rejects it (this-subclass-instance.js), unlike
    				// every other RegExp.prototype method here (which only
    				// require `instanceof RegExp`, checked once above).
    				if(RuntimeUtil.getPrototype(getEnvironment(),_this)!=RegExpPrototype.get(getEnvironment())) {
    					throw RuntimeUtil.typeError("Method RegExp.prototype.compile called on incompatible receiver");
    				}
    				Object pattern = param(args, 0, RuntimeUtil.UNDEFINED);
    				Object flags = param(args, 1, RuntimeUtil.UNDEFINED);
    				_this.compile(getEnvironment(), pattern, flags);
    				return _this;
    			}
        		case exec: {
        			String s = RuntimeUtil.toString(getEnvironment(), param(args, 0, RuntimeUtil.UNDEFINED));
        			return _this.exec(JSRuntimeContext.get(), s);
        		}
	            default: {
	    		    throw new IllegalStateException(); // Should never be here
	            }
	        }
	    }

	    // RegExpExec(R, S): per spec, Get(R,"exec") is consulted first - if
	    // it's callable, invoke it (honoring a receiver's own possibly
	    // custom exec, e.g. a plain object with `{global:true,exec(){...}}`
	    // used as `this`), only falling back to the real internal
	    // [[RegExpMatcher]]-based implementation when R has no such
	    // override AND is a genuine RegExp instance.
	    private Object regExpExec(Object r, String s) {
	    	JSAccessor acc = getEnvironment().getAccessor(r);
	    	Object exec = acc.getProperty(r,"exec",RuntimeUtil.UNDEFINED);
	    	if(exec instanceof Callable cb) {
	    		Object result = cb.call(r, new Object[]{s});
	    		if(result!=null && !RuntimeUtil.isObject(getEnvironment(),result)) {
	    			throw RuntimeUtil.typeError("RegExp exec method returned something other than an object or null");
	    		}
	    		return result;
	    	}
	    	if(!(r instanceof RegExp re)) {
	    		throw RuntimeUtil.typeError("Method RegExp.prototype.exec called on incompatible receiver");
	    	}
	    	return re.exec(JSRuntimeContext.get(), s);
	    }

	    // AdvanceStringIndex(S, index, unicode): a plain +1 for non-Unicode-
	    // aware callers, but steps over a full surrogate pair (+2) when
	    // `index` lands on a lead surrogate immediately followed by its
	    // trail surrogate and Unicode mode is active.
	    private static long advanceStringIndex(String s, long index, boolean unicode) {
	    	if(!unicode || index+1>=s.length()) {
	    		return index+1;
	    	}
	    	int cp = s.codePointAt((int)index);
	    	return index + Character.charCount(cp);
	    }

	    // CreateRegExpStringIterator(R, S, global, fullUnicode)'s %next%
	    // algorithm: for a non-global iterator, [[Done]] is set BEFORE the one
	    // (and only) RegExpExec call is made, so it's called exactly once ever
	    // regardless of how many times .next() is subsequently invoked -
	    // mirrored here via `done` being latched independently of whether that
	    // one call found a match. Each JS-level `.next()` call maps to exactly
	    // one hasNext()+next() pair (see BuiltinIteratorHelperPrototype), so
	    // caching the computed result between them calls RegExpExec exactly
	    // once per JS .next() invocation, as required.
	    private Iterator<Object> regExpStringIterator(Object r, String s, boolean global, boolean fullUnicode) {
	    	return new Iterator<Object>() {
	    		private boolean done = false;
	    		private boolean computed = false;
	    		private Object pending = null;

	    		private void ensureComputed() {
	    			if(computed) {
	    				return;
	    			}
	    			computed = true;
	    			if(done) {
	    				pending = null;
	    				return;
	    			}
	    			if(!global) {
	    				done = true;
	    			}
	    			Object match = regExpExec(r,s);
	    			if(match==null) {
	    				done = true;
	    				pending = null;
	    				return;
	    			}
	    			if(global) {
	    				JSAccessor rAcc = getEnvironment().getAccessor(r);
	    				JSAccessor matchAcc = getEnvironment().getAccessor(match);
	    				String matchStr = RuntimeUtil.toString(getEnvironment(), matchAcc.getProperty(match,"0",RuntimeUtil.UNDEFINED));
	    				if(matchStr.isEmpty()) {
	    					long lastIndex = RuntimeUtil.toLength(getEnvironment(), rAcc.getProperty(r,"lastIndex",RuntimeUtil.UNDEFINED));
	    					rAcc.setOwnProperty(r,"lastIndex",advanceStringIndex(s,lastIndex,fullUnicode),null,DESC_CHECK.STRICT,r);
	    				}
	    			}
	    			pending = match;
	    		}

	    		@Override
	    		public boolean hasNext() {
	    			ensureComputed();
	    			return pending!=null;
	    		}

	    		@Override
	    		public Object next() {
	    			ensureComputed();
	    			if(pending==null) {
	    				throw new NoSuchElementException();
	    			}
	    			Object result = pending;
	    			pending = null;
	    			computed = false;
	    			return result;
	    		}
	    	};
	    }

	    // GetSubstitution(matched, str, position, captures, namedCaptures,
	    // replacementTemplate): expands $-patterns in a non-functional
	    // replacement string - $$ (literal $), $& (matched substring),
	    // $` / $' (text before/after the match), $n / $nn (capture group,
	    // preferring the 2-digit form when it refers to a valid capture),
	    // $<name> (named capture, only recognized at all when namedCaptures
	    // isn't undefined). Any $ not starting a recognized pattern is
	    // copied through literally.
	    private String getSubstitution(String matched, String str, int position, List<Object> captures, Object namedCaptures, String template) {
	    	int tailPos = position + matched.length();
	    	StringBuilder sb = new StringBuilder();
	    	int len = template.length();
	    	int i = 0;
	    	while(i<len) {
	    		char c = template.charAt(i);
	    		if(c!='$' || i+1>=len) {
	    			sb.append(c);
	    			i++;
	    			continue;
	    		}
	    		char next = template.charAt(i+1);
	    		if(next=='$') {
	    			sb.append('$');
	    			i += 2;
	    		} else if(next=='&') {
	    			sb.append(matched);
	    			i += 2;
	    		} else if(next=='`') {
	    			sb.append(str,0,position);
	    			i += 2;
	    		} else if(next=='\'') {
	    			sb.append(str.substring(Math.min(tailPos,str.length())));
	    			i += 2;
	    		} else if(Character.isDigit(next)) {
	    			int oneDigit = next-'0';
	    			int twoDigit = (i+2<len && Character.isDigit(template.charAt(i+2))) ? oneDigit*10+(template.charAt(i+2)-'0') : -1;
	    			if(twoDigit>=1 && twoDigit<=captures.size()) {
	    				appendCapture(sb,captures.get(twoDigit-1));
	    				i += 3;
	    			} else if(oneDigit>=1 && oneDigit<=captures.size()) {
	    				appendCapture(sb,captures.get(oneDigit-1));
	    				i += 2;
	    			} else {
	    				sb.append(c);
	    				i++;
	    			}
	    		} else if(next=='<' && namedCaptures!=RuntimeUtil.UNDEFINED) {
	    			int close = template.indexOf('>',i+2);
	    			if(close<0) {
	    				sb.append(c);
	    				i++;
	    			} else {
	    				String groupName = template.substring(i+2,close);
	    				JSAccessor ncAcc = getEnvironment().getAccessor(namedCaptures);
	    				appendCapture(sb, ncAcc.getProperty(namedCaptures,groupName,RuntimeUtil.UNDEFINED));
	    				i = close+1;
	    			}
	    		} else {
	    			sb.append(c);
	    			i++;
	    		}
	    	}
	    	return sb.toString();
	    }

	    // Only `undefined` is skipped (produces empty text) - a genuine JS
	    // `null` capture value (Java `null`) is a normal ToString-able value
	    // ("null"), same as any other non-undefined value.
	    private void appendCapture(StringBuilder sb, Object capture) {
	    	if(capture!=RuntimeUtil.UNDEFINED) {
	    		sb.append(RuntimeUtil.toString(getEnvironment(),capture));
	    	}
	    }
	}
}