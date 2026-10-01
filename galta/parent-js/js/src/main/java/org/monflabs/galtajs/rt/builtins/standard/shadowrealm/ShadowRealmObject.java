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
package org.monflabs.galtajs.rt.builtins.standard.shadowrealm;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.modules.ModuleUtil;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.JSRuntimeException;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.BasePrototype;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.NativeObject;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.BaseStandardConstructor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.rt.builtins.standard.promise.BuiltinPromise;

/**
 * ShadowRealm (proposal): a new realm whose values only cross the boundary
 * as primitives or wrapped functions.
 */
public class ShadowRealmObject extends NativeObject {

	public static final String CLASSNAME = "ShadowRealm";

	private final JSEnvironment realm;
	// The realm's %eval%, whatever its global object becomes
	private final Object realmEval;

	public ShadowRealmObject(JSEnvironment env, JSEnvironment realm) {
		super(env);
		this.realm = realm;
		this.realmEval = realm.getStandardObjects().getProperty("eval");
	}

	public JSEnvironment getRealm() {
		return realm;
	}

	@Override
	public String getClassName() {
		return CLASSNAME;
	}

	@Override
	protected Object getDefaultPrototype() {
		return Prototype.get(getEnvironment());
	}

	private static ShadowRealmObject validate(Object o) {
		if(!(o instanceof ShadowRealmObject r)) {
			throw RuntimeUtil.typeError("{0}","ShadowRealm method called on incompatible receiver");
		}
		return r;
	}

	private static boolean isCallable(JSEnvironment env, Object v) {
		return "function".equals(RuntimeUtil.typeof(env,v));
	}

	// GetWrappedValue(realm, value)
	static Object getWrappedValue(JSEnvironment realm, Object value) {
		JSEnvironment env = JSEnvironment.getEnvironment();
		if(value!=null && value!=RuntimeUtil.UNDEFINED && RuntimeUtil.isObject(env,value)) {
			if(!isCallable(env,value)) {
				throw RuntimeUtil.typeError("{0}","ShadowRealm: cannot pass a non-callable object across the realm boundary");
			}
			return WrappedFunction.create(realm,value);
		}
		return value;
	}

	public static class Prototype extends BasePrototype {

		public static Prototype get(JSEnvironment env) {
			Prototype proto = (Prototype)env.getRegisteredPrototype(Prototype.class);
			if(proto==null) {
				proto = new Prototype(env);
				env.registerPrototype(Prototype.class,proto);
			}
			return proto;
		}

		private Prototype(JSEnvironment env) {
			super(env);
			setOwnMethod(new Method(env,"evaluate",1));
			setOwnMethod(new Method(env,"importValue",2));
			setOwnProperty(Symbol.TO_STRING_TAG,CLASSNAME,PropertyDescriptor.DESC_PROP_TOSTRINGTAG);
		}
	}

	private static final class Method extends BaseMethod {

		private final String name;

		Method(JSEnvironment env, String name, int length) {
			super(env,name,length);
			this.name = name;
		}

		@Override
		protected Object invoke(Object obj, Object[] args) {
			ShadowRealmObject r = validate(obj);
			if(name.equals("evaluate")) {
				Object source = param(args,0,RuntimeUtil.UNDEFINED);
				if(!(source instanceof CharSequence) || RuntimeUtil.isBoxedString(getEnvironment(),source)) {
					throw RuntimeUtil.typeError("{0}","ShadowRealm.prototype.evaluate: the source must be a string");
				}
				return r.evaluate(getEnvironment(),source.toString());
			}
			String specifier = RuntimeUtil.toString(getEnvironment(),param(args,0,RuntimeUtil.UNDEFINED));
			Object exportName = param(args,1,RuntimeUtil.UNDEFINED);
			if(!(exportName instanceof CharSequence) || RuntimeUtil.isBoxedString(getEnvironment(),exportName)) {
				throw RuntimeUtil.typeError("{0}","ShadowRealm.prototype.importValue: the export name must be a string");
			}
			return r.importValue(getEnvironment(),specifier,exportName.toString());
		}
	}

	// PerformShadowRealmEval: an indirect eval in the shadow realm (its
	// lexical declarations don't outlive the call). Syntax errors are
	// reported in the caller realm, anything thrown becomes a TypeError.
	private Object evaluate(JSEnvironment callerRealm, String source) {
		try {
			realm.createEvalScript(source,"<ShadowRealm>");
		} catch(Throwable t) {
			RuntimeUtil.rethrowIfUncatchable(t);
			throw RuntimeUtil.syntaxError("{0}",String.valueOf(t.getMessage()));
		}
		Object result;
		try {
			result = RuntimeUtil.call(realm,realmEval,RuntimeUtil.UNDEFINED,new Object[] {source});
		} catch(Throwable t) {
			RuntimeUtil.rethrowIfUncatchable(t);
			throw RuntimeUtil.typeError("{0}","ShadowRealm.prototype.evaluate: an error was thrown in the ShadowRealm: "+t.getMessage());
		}
		return getWrappedValue(callerRealm,result);
	}

	// ShadowRealmImportValue: loads the module in the shadow realm (resolved
	// relative to the calling script)
	private Object importValue(JSEnvironment callerRealm, String specifier, String exportName) {
		String resolved = specifier;
		JSRuntimeContext ctx = JSRuntimeContext.get();
		if(ctx!=null && ctx.getMainContext()!=null && ctx.getMainContext().getScriptUnit()!=null) {
			try {
				resolved = ModuleUtil.resolvePath(ctx.getMainContext().getScriptUnit().getDescriptor().getName(),specifier);
			} catch(RuntimeException e) {
				resolved = specifier;
			}
		}
		BuiltinPromise result = new BuiltinPromise(callerRealm);
		Object inner;
		try {
			String quoted = jsQuote(resolved);
			inner = realm.createScript("import("+quoted+")","<ShadowRealm>").executeWithContext(realm.getRealmContext());
		} catch(Throwable t) {
			RuntimeUtil.rethrowIfUncatchable(t);
			result.reject(JSRuntimeException.exceptionObject(RuntimeUtil.typeError("{0}","ShadowRealm.prototype.importValue: cannot import "+specifier)));
			return result;
		}
		BaseMethod onFulfilled = new BaseMethod(callerRealm,"",1) {
			@Override
			protected Object invoke(Object obj, Object[] args) {
				Object exports = param(args,0,RuntimeUtil.UNDEFINED);
				try {
					JSEnvironment env = getEnvironment();
					if(!RuntimeUtil.hasProperty(env,exports,exportName)) {
						throw RuntimeUtil.typeError("{0}","ShadowRealm.prototype.importValue: "+exportName+" is not exported");
					}
					Object value = RuntimeUtil.getProperty(env,exports,exportName);
					result.resolvePromise(getWrappedValue(callerRealm,value));
				} catch(Throwable t) {
					RuntimeUtil.rethrowIfUncatchable(t);
					result.reject(JSRuntimeException.exceptionObject(t));
				}
				return RuntimeUtil.UNDEFINED;
			}
		};
		BaseMethod onRejected = new BaseMethod(callerRealm,"",1) {
			@Override
			protected Object invoke(Object obj, Object[] args) {
				result.reject(JSRuntimeException.exceptionObject(RuntimeUtil.typeError("{0}","ShadowRealm.prototype.importValue: the import failed")));
				return RuntimeUtil.UNDEFINED;
			}
		};
		Object then = RuntimeUtil.getProperty(realm,inner,"then");
		RuntimeUtil.call(realm,then,inner,new Object[] {onFulfilled,onRejected});
		return result;
	}

	private static String jsQuote(String s) {
		StringBuilder b = new StringBuilder("\"");
		for(int i=0; i<s.length(); i++) {
			char c = s.charAt(i);
			if(c=='"' || c=='\\') {
				b.append('\\').append(c);
			} else if(c<0x20 || c>0x7e) {
				b.append(String.format("\\u%04x",(int)c));
			} else {
				b.append(c);
			}
		}
		return b.append('"').toString();
	}

	public static class ConstructorImpl extends BaseStandardConstructor {

		public ConstructorImpl(JSEnvironment env) {
			super(env,CLASSNAME,Prototype.get(env),0);
		}

		@Override
		public Class<?> getNativeClass() {
			return ShadowRealmObject.class;
		}

		@Override
		public Object constructObject(Object[] parameters, Constructor topConstructor) {
			// The new realm's objects must be reachable from this one (the
			// realms are linked when its root context is created)
			JSEnvironment realm = getEnvironment().createRealm();
			realm.getRealmContext();
			return applyNewTargetPrototype(new ShadowRealmObject(getEnvironment(),realm),topConstructor);
		}

		@Override
		public Object call(Object _this, Object[] parameters) {
			throw RuntimeUtil.typeError("Constructor ShadowRealm requires 'new'");
		}
	}

	// A function wrapped across the realm boundary
	static final class WrappedFunction extends BaseMethod {

		private final Object target;

		private WrappedFunction(JSEnvironment realm, Object target) {
			super(realm,"",0);
			this.target = target;
		}

		// WrappedFunctionCreate(callerRealm, target)
		static WrappedFunction create(JSEnvironment realm, Object target) {
			WrappedFunction f = new WrappedFunction(realm,target);
			try {
				JSEnvironment env = JSEnvironment.getEnvironment();
				Object length = 0;
				if(RuntimeUtil.getOwnPropertyDescriptor(env,target,"length")!=null) {
					Object targetLen = RuntimeUtil.getProperty(env,target,"length");
					if(targetLen instanceof Number n && !(n instanceof java.math.BigInteger) && !(n instanceof java.math.BigDecimal)) {
						double d = n.doubleValue();
						if(d==Double.POSITIVE_INFINITY) {
							length = Double.POSITIVE_INFINITY;
						} else if(d==Double.NEGATIVE_INFINITY || Double.isNaN(d)) {
							length = 0;
						} else {
							double i = d<0 ? Math.ceil(d) : Math.floor(d);
							i = Math.max(i,0);
							length = i==(int)i ? (Object)Integer.valueOf((int)i) : (Object)Double.valueOf(i);
						}
					}
				}
				Object targetName = RuntimeUtil.getProperty(env,target,"name");
				String name = targetName instanceof CharSequence cs && !RuntimeUtil.isBoxedString(env,targetName) ? cs.toString() : "";
				f.setOwnProperty("length",length,PropertyDescriptor.DESC_PROP_READONLY_CONFIGURABLE);
				f.setOwnProperty("name",name,PropertyDescriptor.DESC_PROP_READONLY_CONFIGURABLE);
			} catch(Throwable t) {
				RuntimeUtil.rethrowIfUncatchable(t);
				throw RuntimeUtil.typeError("{0}","ShadowRealm: cannot wrap the function: "+t.getMessage());
			}
			return f;
		}

		@Override
		protected Object invoke(Object thisArgument, Object[] args) {
			JSEnvironment callerRealm = getEnvironment();
			JSEnvironment targetRealm = RuntimeUtil.getFunctionRealm(callerRealm,target);
			Object[] wrappedArgs = new Object[args!=null ? args.length : 0];
			for(int i=0; i<wrappedArgs.length; i++) {
				wrappedArgs[i] = getWrappedValue(targetRealm,args[i]);
			}
			Object wrappedThis = getWrappedValue(targetRealm,thisArgument);
			Object result;
			try {
				result = RuntimeUtil.call(callerRealm,target,wrappedThis,wrappedArgs);
			} catch(Throwable t) {
				RuntimeUtil.rethrowIfUncatchable(t);
				throw RuntimeUtil.typeError("{0}","ShadowRealm: the wrapped function threw: "+t.getMessage());
			}
			return getWrappedValue(callerRealm,result);
		}
	}
}
