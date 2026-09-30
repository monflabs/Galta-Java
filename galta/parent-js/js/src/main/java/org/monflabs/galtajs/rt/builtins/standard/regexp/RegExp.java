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

import java.util.Iterator;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.jsonfactory.JSObject.DESC_CHECK;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.NativeObject;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.util.StringUtil;


/**
 * Runtime regexp based on JRE Pattern implementation.
 */
public class RegExp extends NativeObject {

    private RegExpEngine regExpEngine;
    private RegExpConstructor legacyConstructor;
    private String source;
    private String flags;

	public RegExp(JSEnvironment env, RegExp regexp) {
		super(env);
		init(env,regexp.getSource(),regexp.getFlags());
	}

	public RegExp(JSEnvironment env, String regexp) {
		super(env);
		if(regexp.startsWith("/")) {
			int pos = regexp.lastIndexOf("/");
			String source = regexp.substring(1,pos);
			String flags = regexp.substring(pos+1);
			init(env,source,flags);
		} else {
			init(env,regexp,"");
		}
	}

	public RegExp(JSEnvironment env, String source, Object flags) {
		super(env);
		init(env,source,flags);
	}
	private void init(JSEnvironment env, String source, Object _flags) {
		this.source = source;
		// Per RegExpInitialize (22.2.3.2.2): only a MISSING/undefined flags
		// argument defaults to "" - an explicit `null` must still go through
		// ToString (-> the literal string "null"), which then correctly
		// fails flag validation below (matches S15.10.4.1_A5_T6.js).
		this.flags = _flags != RuntimeUtil.UNDEFINED ? RuntimeUtil.toString(env, _flags) : "";

		setOwnProperty("lastIndex",0,PropertyDescriptor.DESC_HIDDEN_PROP);

		// Validate the flags
		int lf = flags.length();
		boolean[] seenFlags = new boolean[128]; // ASCII flags
		boolean hasUnicode = false;
		boolean hasUnicodeSets = false;

		for(int i=0; i<lf; i++) {
			char c = flags.charAt(i);
			if("dgimsuvy".indexOf(c)<0) {
				throw RuntimeUtil.syntaxError("Invalid RegExp flag '{0}' in '{1}'", c, flags);
			}
			// Check for duplicate flags
			if(seenFlags[c]) {
				throw RuntimeUtil.syntaxError("Duplicate RegExp flag '{0}'", c);
			}
			seenFlags[c] = true;

			if(c == 'u') hasUnicode = true;
			if(c == 'v') hasUnicodeSets = true;
		}

		// Check for mutual exclusion of 'u' and 'v' flags
		if(hasUnicode && hasUnicodeSets) {
			throw RuntimeUtil.syntaxError("RegExp flags 'u' and 'v' are mutually exclusive");
		}

		// Validate the source by creating the engine
		this.regExpEngine = getEnvironment().createRegExpEngine(this);
	}
    
	@Override
	protected Object getDefaultPrototype() {
		return RegExpPrototype.get(getEnvironment());
	}
	
	@Override
	public String getClassName() {
		return RegExpConstructor.CLASSNAME;
	}
	
	@Override
	public String toString() {
		return "/" + getEscapedSource() + "/" + getFlagsSorted();
	}

	// Per spec, EscapeRegExpPattern (the escaping of "/" for round-trip
	// safety, and the "(?:)" empty-pattern fallback) belongs to the
	// "source" GETTER, not toString - toString is otherwise fully generic
	// (just "/" + Get(R,"source") + "/" + Get(R,"flags")), and relies on
	// "source" having already done this escaping.
	public String getEscapedSource() {
		String source = getSource();
		if(StringUtil.isEmpty(source)) {
			return "(?:)";
		}
		return escapeRegExpSource(source);
	}

	private String escapeRegExpSource(String source) {
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < source.length(); i++) {
			char c = source.charAt(i);
			if(c == '/') {
				// Escape forward slashes
				sb.append("\\/");
			} else if(c == '\\' && i + 1 < source.length() && source.charAt(i + 1) == '/') {
				// Already escaped forward slash, keep it
				sb.append("\\/");
				i++; // Skip next character
			} else if(c == '\n') {
				sb.append("\\n");
			} else if(c == '\r') {
				sb.append("\\r");
			} else if(c == ' ') {
				sb.append("\\u2028");
			} else if(c == ' ') {
				sb.append("\\u2029");
			} else if(c == '\\' && i + 1 < source.length() && isLineTerminator(source.charAt(i + 1))) {
				// Already escaped LineTerminator (e.g. a genuine "\<LF>"
				// two-char escape sequence in the pattern) - keep it as is,
				// same "don't double-escape" rule as the "/" case above.
				sb.append(c).append(source.charAt(i + 1));
				i++;
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	// Per EscapeRegExpPattern, a raw LineTerminator occurring in the pattern
	// must be escaped in the "source" string returned to script - a raw one
	// can't otherwise round-trip through "/" + source + "/" + flags (the
	// exact form RegExp.prototype.toString()/eval'd re-parsing relies on),
	// since a RegularExpressionLiteral's body may not contain one literally.
	private static boolean isLineTerminator(char c) {
		return c == '\n' || c == '\r' || c == ' ' || c == ' ';
	}
	
	public String getSource() {
		return source;
	}
	public String getFlags() {
		return flags;
	}

	public boolean isHasIndices() {
		return flags.indexOf('d')>=0;
	}
	public boolean isGlobal() {
		return flags.indexOf('g')>=0;
	}
	public boolean isIgnoreCase() {
		return flags.indexOf('i')>=0;
	}
	public boolean isMultiline() {
		return flags.indexOf('m')>=0;
	}
	public boolean isDotAll() {
		return flags.indexOf('s')>=0;
	}
	public boolean isUnicode() {
		return flags.indexOf('u')>=0;
	}
	public boolean isUnicodeSets() {
		return flags.indexOf('v')>=0;
	}
	public boolean isSticky() {
		return flags.indexOf('y')>=0;
	}

	public String getFlagsSorted() {
		StringBuilder b = new StringBuilder();
		if(isHasIndices()) b.append('d');
		if(isGlobal()) b.append('g');
		if(isIgnoreCase()) b.append('i');
		if(isMultiline()) b.append('m');
		if(isDotAll()) b.append('s');
		if(isUnicode()) b.append('u');
		if(isUnicodeSets()) b.append('v');
		if(isSticky()) b.append('y');
		return b.toString();
	}
	
	// Per spec (RegExpBuiltinExec), lastIndex is read via ToLength(Get(R,
	// "lastIndex")) - a plain JS-level assignment (`re.lastIndex = obj`)
	// stores the raw value unconverted, so a poisoned/coercible value (e.g.
	// `{valueOf(){return 12}}`) must still be handled here at READ time,
	// not assumed to already be a Number.
	public int getLastIndex() {
		double n = RuntimeUtil.toNumber(getEnvironment(),get("lastIndex")).doubleValue();
		if(Double.isNaN(n) || n<0) {
			return 0;
		}
		if(n>Integer.MAX_VALUE) {
			return Integer.MAX_VALUE;
		}
		return (int)n;
	}

	// Per spec (RegExpBuiltinExec steps 15.c.i/18), this is Set(R,
	// "lastIndex", value, true) - an UNCONDITIONAL throw-on-failure Set,
	// not a raw internal write - so a non-writable (or otherwise
	// Set-rejecting) "lastIndex" own property must throw TypeError instead
	// of silently failing to update, regardless of the caller's own
	// strict/sloppy-mode-ness (DESC_CHECK.STRICT, not the mode-dependent
	// CHECK a plain `=` assignment would use).
	public void setLastIndex(int lastIndex) {
		RuntimeUtil.setProperty(getEnvironment(), this, "lastIndex", lastIndex, DESC_CHECK.STRICT);
	}

    public JSArray exec(JSRuntimeContext context, String str) {
    	return regExpEngine.exec(context,str);
    }

    // Exposes the CURRENT engine instance, snapshotted at whatever moment the
    // caller reads it - needed by RegExp.prototype[Symbol.split]'s fast path,
    // which must read this BEFORE converting the `limit` argument (ToUint32
    // can run arbitrary user code, e.g. a poisoned valueOf() calling
    // this.compile(...), which replaces `regExpEngine` with a brand new
    // instance reflecting the NEW pattern/flags) - the spec's real algorithm
    // already constructs its own splitter object earlier, before that
    // conversion, so it's unaffected by a later mutation; capturing the
    // engine reference up front lets the fast path match that ordering
    // without paying for an actual splitter object. See
    // annexB/built-ins/RegExp/prototype/Symbol.split/
    // toint32-limit-recompiles-source.js.
    public RegExpEngine getRegExpEngine() {
    	return regExpEngine;
    }

    // RegExp.prototype.compile(pattern, flags) (Annex B.2.5.1): re-runs
    // RegExpInitialize on THIS existing instance (preserving its identity -
    // unlike `new RegExp(...)`, which builds a separate object). Mirrors
    // RegExpConstructor.constructObject()'s identical pattern-is-a-genuine-
    // RegExp-instance dispatch, EXCEPT here a genuine RegExp `pattern` with a
    // defined `flags` argument is a TypeError (constructObject instead just
    // lets the explicit flags override).
    //
    // RegExpInitialize's real step order matters, confirmed by two separate
    // test262 files: (1) an invalid pattern/flags SyntaxError must leave
    // THIS instance completely untouched (pattern-string-invalid.js/
    // flags-string-invalid.js) - validated first via a throwaway instance
    // (whose regExpEngine holds a back-reference to ITS OWN owner per
    // RegExpEngineJdk's "regExp" field, so its engine can't just be copied
    // over - a fresh one is built bound to `this` once source/flags are
    // committed). (2) "lastIndex" is reset LAST, via a THROWING Set (spec:
    // "Perform ? Set(obj,'lastIndex',0,true)") - if "lastIndex" happens to
    // be non-writable, source/flags/the matcher have ALREADY been committed
    // by that point and stay committed even though the throw propagates
    // (pattern-regexp-immutable-lastindex.js) - so this does NOT reuse
    // init()'s own non-throwing `setOwnProperty("lastIndex",...)` (correct
    // only for a BRAND NEW instance, where no pre-existing descriptor could
    // reject it), instead calling the existing throwing setLastIndex(0).
    public void compile(JSEnvironment env, Object pattern, Object flagsParam) {
    	String newSource;
    	Object newFlags;
    	if(pattern instanceof RegExp re) {
    		if(flagsParam!=RuntimeUtil.UNDEFINED) {
    			throw RuntimeUtil.typeError("Cannot supply flags when constructing one RegExp from another");
    		}
    		newSource = re.getSource();
    		newFlags = re.getFlags();
    	} else {
    		newSource = pattern==RuntimeUtil.UNDEFINED ? "" : RuntimeUtil.toString(env,pattern);
    		newFlags = flagsParam;
    	}
    	RegExp validated = new RegExp(env,newSource,newFlags); // validate only, discarded
    	this.source = validated.source;
    	this.flags = validated.flags;
    	this.regExpEngine = env.createRegExpEngine(this);
    	setLastIndex(0);
    }

    /**
     * Records a successful match for the legacy static properties
     * (RegExp.$1-$9, input, lastMatch, lastParen, leftContext, rightContext).
     * Called by the engines on every successful match.
     * @param subject the matched string
     * @param spans [start, end] of the whole match then of each capture, in
     *        UTF-16 code units, -1 for a capture that did not participate;
     *        owned by the callee afterwards
     */
    public void updateLegacyStaticProperties(String subject, int[] spans) {
    	RegExpConstructor ctor = legacyConstructor;
    	if(ctor==null) {
    		if(!(getEnvironment().getStandardObjects().getConstructor(RegExpConstructor.CLASSNAME) instanceof RegExpConstructor c)) {
    			return;
    		}
    		legacyConstructor = ctor = c;
    	}
    	ctor.updateLegacyStaticProperties(subject, spans);
    }

    public boolean test(JSRuntimeContext context, String str) {
    	return regExpEngine.test(context,str);
    }

    public JSArray split(JSRuntimeContext context, String str, int limit) {
    	return regExpEngine.split(context,str,limit);
    }
    
    public JSArray match(JSRuntimeContext context, String str) {
    	return regExpEngine.match(context,str);
    }
    
    public Iterator<JSArray> matchAll(JSRuntimeContext context, String str) {
    	return regExpEngine.matchAll(context,str);
    }
    
    public int search(JSRuntimeContext context, String str) {
    	return regExpEngine.search(context,str);
    }
    
    public String replace(JSRuntimeContext context, String str, Object replace) {
    	return regExpEngine.replace(context,str,replace);
    }
}
