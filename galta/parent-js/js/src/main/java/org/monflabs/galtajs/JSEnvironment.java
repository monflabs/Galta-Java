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
package org.monflabs.galtajs;

import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNull;
import org.monflabs.galtajs.jsonfactory.GaltaJsJsonFactory;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.library.CustomLibraries;
import org.monflabs.galtajs.library.java.JSJavaLibrary;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.modules.JSModuleResolver;
import org.monflabs.galtajs.modules.StaticScriptDescriptor;
import org.monflabs.galtajs.node.ASTProgram;
import org.monflabs.galtajs.node.control.ASTFunctionDecl;
import org.monflabs.galtajs.optimizer.ScriptOptimizer;
import org.monflabs.galtajs.parser.JSParser;
import org.monflabs.galtajs.parser.ParseException;
import org.monflabs.galtajs.parser.ParserContextImpl;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.parser.TokenMgrError;
import org.monflabs.galtajs.rt.JSUnitContext;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.AccessorFactory;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.NullAccessor;
import org.monflabs.galtajs.rt.builtins.primitives.UndefinedAccessor;
import org.monflabs.galtajs.rt.builtins.primitives.array.BuiltinArrayConstructor;
import org.monflabs.galtajs.rt.builtins.primitives.array.JavaListAccessor;
import org.monflabs.galtajs.rt.builtins.primitives.bool.BooleanAccessor;
import org.monflabs.galtajs.rt.builtins.primitives.bool.BuiltinBooleanConstructor;
import org.monflabs.galtajs.rt.builtins.primitives.number.BuiltinNumberConstructor;
import org.monflabs.galtajs.rt.builtins.primitives.number.NumberAccessor;
import org.monflabs.galtajs.rt.builtins.primitives.object.BuiltinObjectConstructor;
import org.monflabs.galtajs.rt.builtins.primitives.object.ObjectAccessor;
import org.monflabs.galtajs.rt.builtins.primitives.string.BuiltinStringConstructor;
import org.monflabs.galtajs.rt.builtins.primitives.string.StringAccessor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.BuiltinSymbolConstructor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.SymbolAccessor;
import org.monflabs.galtajs.rt.builtins.standard.bigdecimal.BigDecimalAccessor;
import org.monflabs.galtajs.rt.builtins.standard.bigdecimal.BuiltinBigDecimalConstructor;
import org.monflabs.galtajs.rt.builtins.standard.bigint.BigIntAccessor;
import org.monflabs.galtajs.rt.builtins.standard.bigint.BuiltinBigIntConstructor;
import org.monflabs.galtajs.rt.builtins.standard.date.DateAccessor;
import org.monflabs.galtajs.rt.builtins.standard.date.DateConstructor;
import org.monflabs.galtajs.rt.builtins.standard.function.BuiltinFunctionConstructor;
import org.monflabs.galtajs.rt.builtins.standard.global.StandardObjects;
import org.monflabs.galtajs.rt.builtins.standard.iterator.BuiltinIteratorConstructor;
import org.monflabs.galtajs.rt.builtins.standard.map.BuiltinMapConstructor;
import org.monflabs.galtajs.rt.builtins.standard.map.MapAccessor;
import org.monflabs.galtajs.rt.builtins.standard.promise.BuiltinPromiseConstructor;
import org.monflabs.galtajs.rt.builtins.standard.proxy.BuiltinProxyConstructor;
import org.monflabs.galtajs.rt.builtins.standard.regexp.RegExp;
import org.monflabs.galtajs.rt.builtins.standard.regexp.RegExpConstructor;
import org.monflabs.galtajs.rt.builtins.standard.regexp.RegExpEngine;
import org.monflabs.galtajs.rt.builtins.standard.set.BuiltinSetConstructor;
import org.monflabs.galtajs.rt.builtins.standard.set.SetAccessor;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.arraybuffer.ArrayBufferConstructor;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.bigint64.BigInt64ArrayConstructor;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.biguint64.BigUint64ArrayConstructor;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.dataview.DataViewConstructor;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.float16.Float16ArrayConstructor;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.float32.Float32ArrayConstructor;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.float64.Float64ArrayConstructor;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.int16.Int16ArrayConstructor;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.int32.Int32ArrayConstructor;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.int8.Int8ArrayConstructor;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.sharedarraybuffer.SharedArrayBufferConstructor;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.uint16.Uint16ArrayConstructor;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.uint32.Uint32ArrayConstructor;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.uint8.Uint8ArrayConstructor;
import org.monflabs.galtajs.rt.builtins.standard.typedarrays.uint8clamped.Uint8ClampedArrayConstructor;
import org.monflabs.galtajs.rt.builtins.standard.disposablestack.BuiltinAsyncDisposableStackConstructor;
import org.monflabs.galtajs.rt.builtins.standard.disposablestack.BuiltinDisposableStackConstructor;
import org.monflabs.galtajs.rt.builtins.standard.weakmap.BuiltinWeakMapConstructor;
import org.monflabs.galtajs.rt.builtins.standard.finalizationregistry.BuiltinFinalizationRegistryConstructor;
import org.monflabs.galtajs.rt.builtins.standard.weakref.BuiltinWeakRefConstructor;
import org.monflabs.galtajs.rt.builtins.standard.weakref.WeakRefAccessor;
import org.monflabs.galtajs.rt.builtins.standard.weakset.BuiltinWeakSetConstructor;
import org.monflabs.galtajs.rt.executors.JSAsyncExecutor;
import org.monflabs.galtajs.rt.executors.JSExecutor;
import org.monflabs.galtajs.rt.executors.JSExpressionExecutor;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.rt.util.PrimitivePropertyMap;
import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonObject;
import org.monflabs.util.ObjectBuilder;
import org.monflabs.util.StringFormat;
import org.monflabs.util.cache.CacheProvider;
import org.monflabs.util.cache.LRUCache;



/**
 * GaltaJS Environment.
 */
public final class JSEnvironment implements JSConfiguration {
	
	// To execute in an environment with a minimal context
	public <R> R with(Supplier<R> callable) {
		return new EnvContext(this).with(callable);
	}
	public void run(Runnable runnable) {
		new EnvContext(this).run(runnable);
	}

	private static class EnvContext implements JSContext {
		private JSEnvironment env;
		public EnvContext(JSEnvironment env) {
			this.env = env;
		}
		@Override
		public JSEnvironment getEnvironment() {
			return env;
		}
	}
	

	public static JSEnvironment getEnvironment() {
		JSContext ctx = JSContext.get();
		if(realmOverrides) {
			RealmOverride o = REALM_OVERRIDE.get();
			if(o!=null && o.context==ctx) {
				return o.realm;
			}
		}
		return ctx.getEnvironment();
	}
	public static JSEnvironment getEnvironmentUnchecked() {
		JSContext ctx = JSContext.getUnchecked();
		if(ctx!=null && realmOverrides) {
			RealmOverride o = REALM_OVERRIDE.get();
			if(o!=null && o.context==ctx) {
				return o.realm;
			}
		}
		return ctx!=null ? ctx.getEnvironment() : null;
	}

	// The current realm while a built-in function of another realm runs
	// (ECMA-262 10.3 [[Call]] of a built-in function: its realm becomes the
	// current Realm Record): objects it creates, the errors it throws above
	// all, belong to its own realm. Tied to the JSContext at entry, so a
	// JavaScript function the built-in calls back (which runs in its own
	// context) is unaffected.
	private record RealmOverride(JSContext context, JSEnvironment realm, RealmOverride previous) {}
	private static final ThreadLocal<RealmOverride> REALM_OVERRIDE = new ThreadLocal<>();
	// Set once a built-in has been called from another realm: until then
	// getEnvironment() does not even look at the override
	private static volatile boolean realmOverrides;
	private static final RealmOverride SAME_REALM = new RealmOverride(null, null, null);

	/**
	 * Makes realm the current realm, when it is not already, until
	 * {@link #exitRealm(Object)} is called with the returned token.
	 */
	public static Object enterRealm(JSEnvironment realm) {
		JSContext ctx = JSContext.getUnchecked();
		if(ctx==null) {
			return SAME_REALM;
		}
		JSEnvironment current = ctx.getEnvironment();
		RealmOverride o = null;
		if(realmOverrides) {
			o = REALM_OVERRIDE.get();
			if(o!=null && o.context==ctx) {
				current = o.realm;
			}
		}
		if(current==realm) {
			return SAME_REALM;
		}
		realmOverrides = true;
		RealmOverride entered = new RealmOverride(ctx, realm, o);
		REALM_OVERRIDE.set(entered);
		return entered;
	}

	public static void exitRealm(Object token) {
		if(token!=SAME_REALM) {
			REALM_OVERRIDE.set(((RealmOverride)token).previous);
		}
	}
	
	public static class Builder extends ObjectBuilder<JSEnvironment> {

		private ConfigurationImpl configuration = new ConfigurationImpl();
		private SharedData sharedData;
		
		protected Builder() {}
		
		private void checkBuilder() {
			if(sharedData!=null) {
				throw new IllegalStateException("Builder has been used and cannot be updated");
			}
		}
		
		public Builder enableGaltaJSExtensions() {
			checkBuilder();
			this.configuration.strictMode = true;
			this.configuration.deprecatedApis = true;
			this.configuration.mustDeclareAllVariables = true;
			this.configuration.supportIdentifierAtSign = true;
			this.configuration.supportReturnOutsideFunction = true;
			this.configuration.supportImportExportInScripts = true;
			this.configuration.supportLongPromotion = true;
			this.configuration.supportBigIntPromotion = false;
			this.configuration.supportBigDecimal = true;
			this.configuration.supportBigDecimalLiteral = true;
			this.configuration.supportMixedBigNumber = true;
			this.configuration.supportBigNumberMath = true;
			this.configuration.supportSequenceExtensions = true;
			this.configuration.isOptimizedSetAndMapCtor = true;
			this.configuration.supportParseIntOctal = false;
			this.configuration.supportJavaNative = true;
			this.configuration.supportTypeHints = true;
			this.configuration.supportTopLevelObjectLiteral = true;
			return this;
		}

		public Builder strictMode(boolean strictMode) {
			checkBuilder();
			this.configuration.strictMode = strictMode;
			this.configuration.mustDeclareAllVariables = strictMode;
			this.configuration.deprecatedApis = !strictMode;
			return this;
		}
		public Builder debug(boolean debug) {
			checkBuilder();
			this.configuration.debug = debug;
			return this;
		}
		public Builder addModuleResolver(JSModuleResolver moduleResolver) {
			checkBuilder();
			if(this.configuration.moduleResolvers==null) {
				this.configuration.moduleResolvers = new ArrayList<>();
			}
			this.configuration.moduleResolvers.add(moduleResolver);
			return this;
		}
		public final Builder scriptOptimizer(ScriptOptimizer scriptOptimizer) {
			checkBuilder();
			this.configuration.scriptOptimizer = scriptOptimizer;
			return this;
		}
		public final Builder registerLibrary(JSLibrary library) {
			checkBuilder();
			if(this.configuration.libraries==null) {
				this.configuration.libraries = new CustomLibraries();
			}
			this.configuration.libraries.addLibrary(library);
			return this;
		}
		public Builder putProperty(String key, Object value) {
			checkBuilder();
			if(this.configuration.properties==null) {
				this.configuration.properties = new HashMap<>();
			}
			this.configuration.properties.put(key,value);
			return this;
		}
		public final Builder classLoader(ClassLoader classLoader) {
			checkBuilder();
			this.configuration.classLoader = classLoader;
			return this;
		}
		
		public Builder deprecatedApis(boolean deprecatedApis) {
			checkBuilder();
			this.configuration.deprecatedApis = deprecatedApis;
			return this;
		}
		public Builder supportJavaNative(boolean supportJavaNative) {
			checkBuilder();
			this.configuration.supportJavaNative = supportJavaNative;
			return this;
		}
		public Builder supportFloat16Array(boolean supportFloat16Array) {
			checkBuilder();
			this.configuration.supportFloat16Array = supportFloat16Array;
			return this;
		}
		public Builder supportGlobalAlias(boolean supportGlobalAlias) {
			checkBuilder();
			this.configuration.supportGlobalAlias = supportGlobalAlias;
			return this;
		}
		public Builder optimizedSetAndMapCtor(boolean isOptimizedSetAndMapCtor) {
			checkBuilder();
			this.configuration.isOptimizedSetAndMapCtor = isOptimizedSetAndMapCtor;
			return this;
		}

		public Builder mustDeclareAllVariables(boolean mustDeclareAllVariables) {
			checkBuilder();
			this.configuration.mustDeclareAllVariables = mustDeclareAllVariables;
			return this;
		}
		public Builder supportIdentifierAtSign(boolean supportIdentifierAtSign) {
			checkBuilder();
			this.configuration.supportIdentifierAtSign = supportIdentifierAtSign;
			return this;
		}
		
		public Builder supportImportExportInScripts(boolean supportImportExportInScripts) {
			checkBuilder();
			this.configuration.supportImportExportInScripts = supportImportExportInScripts;
			return this;
		}

		public Builder supportReturnOutsideFunction(boolean supportReturnOutsideFunction) {
			checkBuilder();
			this.configuration.supportReturnOutsideFunction = supportReturnOutsideFunction;
			return this;
		}

		public Builder supportTypeHints(boolean supportTypeHints) {
			checkBuilder();
			this.configuration.supportTypeHints = supportTypeHints;
			return this;
		}

		public Builder supportTopLevelObjectLiteral(boolean supportTopLevelObjectLiteral) {
			checkBuilder();
			this.configuration.supportTopLevelObjectLiteral = supportTopLevelObjectLiteral;
			return this;
		}

		public Builder supportLongPromotion(boolean supportLongPromotion) {
			checkBuilder();
			this.configuration.supportLongPromotion = supportLongPromotion;
			return this;
		}
		public Builder supportBigIntPromotion(boolean supportBigIntPromotion) {
			checkBuilder();
			this.configuration.supportBigIntPromotion = supportBigIntPromotion;
			return this;
		}
		public Builder supportBigDecimal(boolean supportBigDecimal) {
			checkBuilder();
			this.configuration.supportBigDecimal = supportBigDecimal;
			return this;
		}
		public Builder supportBigDecimalLiteral(boolean supportBigDecimalLiteral) {
			checkBuilder();
			this.configuration.supportBigDecimalLiteral = supportBigDecimalLiteral;
			return this;
		}
		public Builder supportBigDecimalPromotion(boolean supportBigDecimalPromotion) {
			checkBuilder();
			this.configuration.supportBigDecimalPromotion = supportBigDecimalPromotion;
			return this;
		}
		public Builder supportBigNumberMath(boolean supportBigNumberMath) {
			checkBuilder();
			this.configuration.supportBigNumberMath = supportBigNumberMath;
			return this;
		}
		public Builder forceBigDecimalOperations(boolean forceBigDecimalOperations) {
			checkBuilder();
			this.configuration.forceBigDecimalOperations = forceBigDecimalOperations;
			return this;
		}
		public Builder supportMixedBigNumber(boolean supportMixedBigNumber) {
			checkBuilder();
			this.configuration.supportMixedBigNumber = supportMixedBigNumber;
			return this;
		}
		public Builder supportParseIntOctal(boolean supportParseIntOctal) {
			checkBuilder();
			this.configuration.supportParseIntOctal = supportParseIntOctal;
			return this;
		}
		
		public Builder supportSequenceExtensions(boolean supportSequenceExtensions) {
			checkBuilder();
			this.configuration.supportSequenceExtensions = supportSequenceExtensions;
			return this;
		}
		
		public Builder scriptCacheSize(int scriptCacheSize) {
			checkBuilder();
			this.configuration.scriptCacheSize = scriptCacheSize;
			return this;
		}
		public Builder evalCacheSize(int evalCacheSize) {
			checkBuilder();
			this.configuration.evalCacheSize = evalCacheSize;
			return this;
		}
		public Builder regexpCacheSize(int regExpCacheSize) {
			checkBuilder();
			this.configuration.regExpCacheSize = regExpCacheSize;
			return this;
		}
		public Builder regexpEngineFactory(BiFunction<JSEnvironment,RegExp,RegExpEngine> regExpEngineFactory) {
			checkBuilder();
			this.configuration.regExpEngineFactory = regExpEngineFactory;
			return this;
		}

		public Builder mathContext(MathContext mathContext, String mathContextTranspiler) {
			checkBuilder();
			this.configuration.mathContext = mathContext;
			this.configuration.mathContextTranspiler = mathContextTranspiler;
			return this;
		}

		// Convenience for the fluid patter
		public Builder configure(Consumer<JSEnvironment.Builder> configurator) {
			checkBuilder();
			configurator.accept(this);
			return this;
		}

		@Override
		protected synchronized JSEnvironment _build() {
			if(sharedData==null) {
				SharedData sharedData = new SharedData(configuration);
				if(configuration.libraries!=null) {
					configuration.libraries.configureEnvironment(this);
				}
				this.sharedData = sharedData;
			}
			return new JSEnvironment(sharedData);
		}
	}
	
	public static Builder newBuilder() {
		return new Builder();
	}

	// A new realm with the same configuration and libraries (ShadowRealm)
	public JSEnvironment createRealm() {
		return new JSEnvironment(sharedData);
	}

	
	
	final static class SharedData {
		
		private ConfigurationImpl configuration;
		
		private LRUCache<String,ASTProgram> scriptCache;
		private LRUCache<String,ASTProgram> evalCache;
		private LRUCache<String,Pattern> regExpCache;

		// Runtime properties for objects
		
		private SharedData(ConfigurationImpl configuration) {
			this.configuration = configuration;
		}
		
		public JSConfiguration getConfiguration() {
			return configuration;
		}
		
		public synchronized LRUCache<String,ASTProgram> getScriptCache() {
			if(scriptCache==null && configuration.getScriptCacheSize()>0) {
				scriptCache = new LRUCache<>(configuration.getScriptCacheSize());
			}
			return scriptCache;
		}
		public synchronized LRUCache<String,ASTProgram> getEvalCache() {
			if(evalCache==null && configuration.getEvalCacheSize()>0) {
				evalCache = new LRUCache<>(configuration.getEvalCacheSize());
			}
			return evalCache;
		}
		public synchronized LRUCache<String,Pattern> getRegExpCache() {
			if(regExpCache==null && configuration.getRegExpCacheSize()>0) {
				regExpCache = new LRUCache<>(configuration.getRegExpCacheSize());
			}
			return regExpCache;
		}
		
		public boolean supportBigDecimal() {
			return configuration.supportBigDecimal();
		}
	}
	

	
	/////////////////////////////////////////////////////////////////////////
	// Access to the MathContext used when dealing with bigDecinal
	/////////////////////////////////////////////////////////////////////////

	private SharedData sharedData;
	private ConfigurationImpl configuration;
	private GaltaJsJsonFactory jsonFactory;
	private NullAccessor nullAccessor;
	private Map<Class<?>,JSAccessor> accessorsCache = new ConcurrentHashMap<>(); // No Null keys
	private StandardObjects standardObjects;
	// Filled lazily, possibly by several threads sharing the environment
	private Map<Class<? extends Object>,Object> prototypes = new ConcurrentHashMap<>();
	// Expando side tables: properties (and prototype/extensibility state)
	// attached to values that have no storage of their own - boxed primitives
	// (`new Number(5)`, `Object("x")`, sloppy-mode `this` coercion) and Java
	// objects that don't implement PropertiesHolder (java.util.Date, HashMap,
	// any host object).
	//
	// Deliberately SPLIT BY KEY TYPE rather than one shared map, because a
	// null map is what several hot guards test to take their fast path -
	// notably ASTArithmeticOp/ASTComparisonOp's int/int and double/double
	// inline caches, and RuntimeUtil's operator fast paths. With one shared
	// map, a single `hostDate.tag = 1` or `Object("x")` flipped it non-null
	// and disabled interpreted arithmetic for the rest of the environment's
	// life (measured 2.5x on an arithmetic loop). Split, traffic on one kind
	// of key can no longer deoptimize the guards that consult another: a
	// number guard reads numberProperties only, which stays null unless a
	// NUMBER is actually boxed.
	//
	// volatile because creation is lazy and an environment may be shared
	// across threads - a stale null read would report a boxed primitive as a
	// raw primitive (wrong instanceof, ToPrimitive skipped). Reads are on the
	// hot path but a volatile read of an already-published reference is a
	// plain load on the platforms this runs on.
	private volatile PrimitivePropertyMap objectProperties;
	private volatile PrimitivePropertyMap stringProperties;
	private volatile PrimitivePropertyMap numberProperties;
	private volatile PrimitivePropertyMap booleanProperties;
	private volatile PrimitivePropertyMap symbolProperties;
	// Annex B.3.7 [[IsHTMLDDA]] internal slot: at most one such object exists
	// per realm in practice (mirrors browsers' `document.all` - test262's
	// $262.IsHTMLDDA is the only real-world source of one). Identity-based:
	// RuntimeUtil.typeof/toBoolean/eq special-case this exact object.
	private Object htmlddaObject;
	
	public static final boolean CHECK_FOR_DEBUG = false;


	JSEnvironment(SharedData sharedData) {
		this.standardObjects = new StandardObjects(this);
		this.sharedData = sharedData;
		this.configuration = sharedData.configuration;
		this.jsonFactory = new GaltaJsJsonFactory(this);
		this.nullAccessor = new NullAccessor(this);
		
		buildstandardObjects();
	}

	protected void buildstandardObjects() {
		standardObjects.setOwnProperty("undefined", RuntimeUtil.UNDEFINED,PropertyDescriptor.DESC_READONLY_HIDDEN_PROP);
		standardObjects.setOwnProperty("NaN",Double.NaN,PropertyDescriptor.DESC_READONLY_HIDDEN_PROP);
		standardObjects.setOwnProperty("Infinity",Double.POSITIVE_INFINITY,PropertyDescriptor.DESC_READONLY_HIDDEN_PROP);

		standardObjects.setOwnProperty(BuiltinObjectConstructor.CLASSNAME,new BuiltinObjectConstructor(this),PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(BuiltinBooleanConstructor.CLASSNAME,new BuiltinBooleanConstructor(this),PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(BuiltinNumberConstructor.CLASSNAME,new BuiltinNumberConstructor(this),PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(BuiltinStringConstructor.CLASSNAME,new BuiltinStringConstructor(this),PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(BuiltinSymbolConstructor.CLASSNAME,new BuiltinSymbolConstructor(this),PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(BuiltinArrayConstructor.CLASSNAME,new BuiltinArrayConstructor(this),PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(BuiltinFunctionConstructor.CLASSNAME,new BuiltinFunctionConstructor(this),PropertyDescriptor.DESC_METHOD);

		standardObjects.setOwnProperty(BuiltinBigIntConstructor.CLASSNAME,new BuiltinBigIntConstructor(this),PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(BuiltinBigDecimalConstructor.CLASSNAME,new BuiltinBigDecimalConstructor(this),PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(DateConstructor.CLASSNAME,new DateConstructor(this),PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(RegExpConstructor.CLASSNAME,new RegExpConstructor(this),PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(BuiltinIteratorConstructor.CLASSNAME,new BuiltinIteratorConstructor(this),PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(BuiltinMapConstructor.CLASSNAME,new BuiltinMapConstructor(this),PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(BuiltinSetConstructor.CLASSNAME,new BuiltinSetConstructor(this),PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(BuiltinWeakMapConstructor.CLASSNAME,new BuiltinWeakMapConstructor(this),PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(BuiltinWeakSetConstructor.CLASSNAME,new BuiltinWeakSetConstructor(this),PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(BuiltinWeakRefConstructor.CLASSNAME,new BuiltinWeakRefConstructor(this),PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(BuiltinFinalizationRegistryConstructor.CLASSNAME,new BuiltinFinalizationRegistryConstructor(this),PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(BuiltinProxyConstructor.CLASSNAME,new BuiltinProxyConstructor(this),PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(BuiltinPromiseConstructor.CLASSNAME,new BuiltinPromiseConstructor(this),PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(BuiltinDisposableStackConstructor.CLASSNAME,new BuiltinDisposableStackConstructor(this),PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(BuiltinAsyncDisposableStackConstructor.CLASSNAME,new BuiltinAsyncDisposableStackConstructor(this),PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(org.monflabs.galtajs.rt.builtins.standard.shadowrealm.ShadowRealmObject.CLASSNAME,new org.monflabs.galtajs.rt.builtins.standard.shadowrealm.ShadowRealmObject.ConstructorImpl(this),PropertyDescriptor.DESC_METHOD);

		standardObjects.setOwnProperty(ArrayBufferConstructor.CLASSNAME,new ArrayBufferConstructor(this),PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(SharedArrayBufferConstructor.CLASSNAME,new SharedArrayBufferConstructor(this),PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(DataViewConstructor.CLASSNAME,new DataViewConstructor(this),PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(Uint8ArrayConstructor.CLASSNAME,new Uint8ArrayConstructor(this),PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(Uint8ClampedArrayConstructor.CLASSNAME,new Uint8ClampedArrayConstructor(this),PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(Int8ArrayConstructor.CLASSNAME,new Int8ArrayConstructor(this),PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(Int16ArrayConstructor.CLASSNAME,new Int16ArrayConstructor(this),PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(Uint16ArrayConstructor.CLASSNAME,new Uint16ArrayConstructor(this),PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(Int32ArrayConstructor.CLASSNAME,new Int32ArrayConstructor(this),PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(Uint32ArrayConstructor.CLASSNAME,new Uint32ArrayConstructor(this),PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(BigInt64ArrayConstructor.CLASSNAME,new BigInt64ArrayConstructor(this),PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(BigUint64ArrayConstructor.CLASSNAME,new BigUint64ArrayConstructor(this),PropertyDescriptor.DESC_METHOD);
		if(supportFloat16Array()) {
			standardObjects.setOwnProperty(Float16ArrayConstructor.CLASSNAME,new Float16ArrayConstructor(this),PropertyDescriptor.DESC_METHOD);
		}
		standardObjects.setOwnProperty(Float32ArrayConstructor.CLASSNAME,new Float32ArrayConstructor(this),PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(Float64ArrayConstructor.CLASSNAME,new Float64ArrayConstructor(this),PropertyDescriptor.DESC_METHOD);

		// Errors (exceptions)
		standardObjects.setOwnProperty(org.monflabs.galtajs.rt.builtins.errors.Error.ConstructorImpl.CLASSNAME,new org.monflabs.galtajs.rt.builtins.errors.Error.ConstructorImpl(this),PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(org.monflabs.galtajs.rt.builtins.errors.InternalError.ConstructorImpl.CLASSNAME,new org.monflabs.galtajs.rt.builtins.errors.InternalError.ConstructorImpl(this),PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(org.monflabs.galtajs.rt.builtins.errors.EvalError.ConstructorImpl.CLASSNAME,new org.monflabs.galtajs.rt.builtins.errors.EvalError.ConstructorImpl(this),PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(org.monflabs.galtajs.rt.builtins.errors.RangeError.ConstructorImpl.CLASSNAME,new org.monflabs.galtajs.rt.builtins.errors.RangeError.ConstructorImpl(this),PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(org.monflabs.galtajs.rt.builtins.errors.ReferenceError.ConstructorImpl.CLASSNAME,new org.monflabs.galtajs.rt.builtins.errors.ReferenceError.ConstructorImpl(this),PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(org.monflabs.galtajs.rt.builtins.errors.SyntaxError.ConstructorImpl.CLASSNAME,new org.monflabs.galtajs.rt.builtins.errors.SyntaxError.ConstructorImpl(this),PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(org.monflabs.galtajs.rt.builtins.errors.TypeError.ConstructorImpl.CLASSNAME,new org.monflabs.galtajs.rt.builtins.errors.TypeError.ConstructorImpl(this),PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(org.monflabs.galtajs.rt.builtins.errors.URIError.ConstructorImpl.CLASSNAME,new org.monflabs.galtajs.rt.builtins.errors.URIError.ConstructorImpl(this),PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(org.monflabs.galtajs.rt.builtins.errors.AggregateError.ConstructorImpl.CLASSNAME,new org.monflabs.galtajs.rt.builtins.errors.AggregateError.ConstructorImpl(this),PropertyDescriptor.DESC_METHOD);
		standardObjects.setOwnProperty(org.monflabs.galtajs.rt.builtins.errors.SuppressedError.ConstructorImpl.CLASSNAME,new org.monflabs.galtajs.rt.builtins.errors.SuppressedError.ConstructorImpl(this),PropertyDescriptor.DESC_METHOD);
		
		if(configuration.libraries!=null) {
			configuration.libraries.configureStandardObjects(this, standardObjects);
		}
		standardObjects.captureIntrinsics();
	}

	public JSExecutor createExpressionExecutor() {
		// Shared instance...
		return JSExpressionExecutor.get();
	}

	public JSExecutor createProgramExecutor() {
		// Specific instance
		return new JSAsyncExecutor(this);
	}
	
	public GaltaJsJsonFactory getJsonFactory() {
		return jsonFactory;
	}
	public StandardObjects getStandardObjects() {
		return standardObjects;
	}
	
	public @NonNull JSAccessor getAccessor(@NonNull Object instance) {
		if(instance==null) {
			return nullAccessor;
		}
		JSAccessor o = accessorsCache.get(instance.getClass());
		if(o==null) {
			synchronized (this) {
				o = accessorsCache.get(instance.getClass());
				if(o==null) {
					o = findAccessor(instance);
					accessorsCache.put(instance.getClass(), o);
				}
			}
		}
		return o;
	}
	// Called by getAccessor(), under its lock
	private JSAccessor findAccessor(@NonNull Object instance) {
		if(instance instanceof AccessorFactory f)  {
	        return f.createAccessor(this);
		}

		if(instance==RuntimeUtil.UNDEFINED) {
			return new UndefinedAccessor(this);
		}
		if(instance instanceof Number) {
			if(instance instanceof BigInteger) {
		        return new BigIntAccessor(this);
			}
			if(instance instanceof BigDecimal) {
				if(supportBigDecimal()) {
					return new BigDecimalAccessor(this);
				}
				// Just java object...
			} else {
				return new NumberAccessor(this);
			}
		}
		if(instance instanceof Boolean) {
	        return new BooleanAccessor(this);
		}
		if(instance instanceof CharSequence) {
	        return new StringAccessor(this);
		}
		if(instance instanceof Symbol) {
	        return new SymbolAccessor(this);
		}

		if(instance instanceof JSObject) {
	        return new ObjectAccessor(this);
		}

		if(instance instanceof org.monflabs.galtajs.rt.builtins.Closure) {
			// A with-scope-resolved function value gets wrapped in a Closure
			// (see InterpretedWithRuntimeContext.resolveOwnIdentifierEntry())
			// purely to override its `this`-binding when called directly -
			// any OTHER access (property lookup, prototype chain, etc.) must
			// behave transparently as if the wrapper didn't exist.
			return new org.monflabs.galtajs.rt.builtins.ClosureAccessor(this);
		}

		if(instance instanceof Date) {
	        return new DateAccessor(this);
		}
		if(instance instanceof WeakReference<?>) {
	        return new WeakRefAccessor(this);
		}
		if(instance instanceof Map) {
			if(instance instanceof JsonObject) {
				throw RuntimeUtil.typeError("JsonObject must be a JSObject as well - The global JsonFactory may be invalid");
			}
	        return new MapAccessor(this);
		}
		if(instance instanceof Set) {
	        return new SetAccessor(this);
		}
		if(instance instanceof List) {
			if(instance instanceof JsonArray) {
				throw RuntimeUtil.typeError("JsonArray must be a JSArray as well - The global JsonFactory may be invalid");
			}
	        return new JavaListAccessor(this);
		}

		// Or in the environment
		JSAccessor acc = getCustomLibraries().createAccessor(this, instance.getClass());
		if(acc!=null) {
	        return acc;
		}

		throw RuntimeUtil.typeError("Unknown object type {0}", instance.getClass());
	}
	
	public Object getRegisteredPrototype(Class<? extends Object> clazz) {
		return prototypes.get(clazz);
	}

	// See htmlddaObject's field comment (Annex B.3.7 [[IsHTMLDDA]]).
	public void setHTMLDDAObject(Object obj) {
		htmlddaObject = obj;
	}
	public boolean isHTMLDDAObject(Object obj) {
		return obj!=null && obj==htmlddaObject;
	}
	public void registerPrototype(Class<? extends Object> clazz, Object proto) {
		prototypes.put(clazz,proto);
	}
	
	// See the field declarations above for why these are split by key type.
	// Each returns null until something of that kind is actually boxed/
	// expanded - callers on a hot path test for null rather than looking up.
	//
	// Java objects with no storage of their own (java.util.Date, HashMap, any
	// host object) - written by ObjectWrapperAccessor, and by far the most
	// frequently populated of the five.
	// Java objects with no storage of their own (java.util.Date, HashMap, any
	// host object) - written by ObjectWrapperAccessor, and by far the most
	// frequently populated of the five.
	public PrimitivePropertyMap getObjectProperties() {
		// Linked realms: the map is needed to find their entries
		return realmGroup!=null ? getObjectProperties(true) : objectProperties;
	}
	public PrimitivePropertyMap getObjectProperties(boolean autoCreate) {
		PrimitivePropertyMap m = objectProperties;
		return m!=null || !(autoCreate || realmGroup!=null) ? m : createPropertyMap(PROPERTIES_OBJECT);
	}

	// Boxed Strings. Only a real java.lang.String is ever a key - a ConsString
	// (the concatenation rope) can never be boxed, so a caller holding a
	// CharSequence must narrow to String before consulting this.
	public PrimitivePropertyMap getStringProperties() {
		// Linked realms: the map is needed to find their entries
		return realmGroup!=null ? getStringProperties(true) : stringProperties;
	}
	public PrimitivePropertyMap getStringProperties(boolean autoCreate) {
		PrimitivePropertyMap m = stringProperties;
		return m!=null || !(autoCreate || realmGroup!=null) ? m : createPropertyMap(PROPERTIES_STRING);
	}

	// Boxed Numbers - every Number subtype (Integer, Double, Long, Byte,
	// Short, Float, BigInteger, BigDecimal). This is the one the arithmetic
	// and comparison inline caches consult, so keeping it null matters most.
	public PrimitivePropertyMap getNumberProperties() {
		// Linked realms: the map is needed to find their entries
		return realmGroup!=null ? getNumberProperties(true) : numberProperties;
	}
	public PrimitivePropertyMap getNumberProperties(boolean autoCreate) {
		PrimitivePropertyMap m = numberProperties;
		return m!=null || !(autoCreate || realmGroup!=null) ? m : createPropertyMap(PROPERTIES_NUMBER);
	}

	// Boxed Booleans - `new Boolean(false)`/`Object(true)`. Almost never
	// populated in practice.
	public PrimitivePropertyMap getBooleanProperties() {
		// Linked realms: the map is needed to find their entries
		return realmGroup!=null ? getBooleanProperties(true) : booleanProperties;
	}
	public PrimitivePropertyMap getBooleanProperties(boolean autoCreate) {
		PrimitivePropertyMap m = booleanProperties;
		return m!=null || !(autoCreate || realmGroup!=null) ? m : createPropertyMap(PROPERTIES_BOOLEAN);
	}

	// Boxed Symbols - `Object(sym)`. Almost never populated in practice.
	public PrimitivePropertyMap getSymbolProperties() {
		// Linked realms: the map is needed to find their entries
		return realmGroup!=null ? getSymbolProperties(true) : symbolProperties;
	}
	public PrimitivePropertyMap getSymbolProperties(boolean autoCreate) {
		PrimitivePropertyMap m = symbolProperties;
		return m!=null || !(autoCreate || realmGroup!=null) ? m : createPropertyMap(PROPERTIES_SYMBOL);
	}

	// The map of the given kind (PROPERTIES_*), null if not created yet
	private PrimitivePropertyMap propertyMap(int kind) {
		return switch(kind) {
			case PROPERTIES_OBJECT -> objectProperties;
			case PROPERTIES_STRING -> stringProperties;
			case PROPERTIES_NUMBER -> numberProperties;
			case PROPERTIES_BOOLEAN -> booleanProperties;
			case PROPERTIES_SYMBOL -> symbolProperties;
			default -> throw new IllegalArgumentException("Invalid property map kind "+kind);
		};
	}
	// The map of the given kind, created if needed
	private synchronized PrimitivePropertyMap createPropertyMap(int kind) {
		PrimitivePropertyMap m = propertyMap(kind);
		if(m==null) {
			m = new PrimitivePropertyMap(this, kind);
			switch(kind) {
				case PROPERTIES_OBJECT -> objectProperties = m;
				case PROPERTIES_STRING -> stringProperties = m;
				case PROPERTIES_NUMBER -> numberProperties = m;
				case PROPERTIES_BOOLEAN -> booleanProperties = m;
				default -> symbolProperties = m;
			}
		}
		return m;
	}

	@Override
	public ClassLoader getClassLoader() {
		return configuration.classLoader;
	}


	/////////////////////////////////////////////////////////////////////////
	// Configuration
	/////////////////////////////////////////////////////////////////////////
	/// 
	@Override
	public final boolean isStrictMode() {
		return configuration.isStrictMode();
	}
	@Override
	public final boolean isDebugEnabled() {
		return configuration.isDebugEnabled();
	}
	@Override
	public final boolean isSharedStandardObjects() {
		return configuration.isSharedStandardObjects();
	}
	@Override
	public List<JSModuleResolver> getModuleResolvers() {
		return configuration.getModuleResolvers();
	}
	@Override
	public final ScriptOptimizer getScriptOptimizer() {
		return configuration.getScriptOptimizer();
	}
	@Override
	public CustomLibraries getCustomLibraries() {
		return configuration.getCustomLibraries();
	}
	@Override
	public Map<String, Object> getProperties() {
		return configuration.getProperties();
	}
	@Override
	public final boolean isDeprecatedApis() {
		return configuration.isDeprecatedApis();
	}
	@Override
	public final boolean supportJavaNative() {
		return configuration.supportJavaNative();
	}
	@Override
	public final boolean supportFloat16Array() {
		return configuration.supportFloat16Array();
	}
	@Override
	public final boolean supportGlobalAlias() {
		return configuration.supportGlobalAlias();
	}
	@Override
	public final boolean isOptimizedSetAndMapCtor() {
		return configuration.isOptimizedSetAndMapCtor();
	}
	@Override
	public final boolean mustDeclareAllVariables() {
		return configuration.mustDeclareAllVariables();
	}
	@Override
	public final boolean supportIdentifierAtSign() {
		return configuration.supportIdentifierAtSign();
	}
	@Override
	public final boolean supportReturnOutsideFunction() {
		return configuration.supportReturnOutsideFunction();
	}
	@Override
	public final boolean supportImportExportInScripts() {
		return configuration.supportImportExportInScripts();
	}
	@Override
	public final boolean supportTypeHints() {
		return configuration.supportTypeHints();
	}
	@Override
	public final boolean supportTopLevelObjectLiteral() {
		return configuration.supportTopLevelObjectLiteral();
	}
	@Override
	public final boolean supportLongPromotion() {
		return configuration.supportLongPromotion();
	}
	@Override
	public final boolean supportBigIntPromotion() {
		return configuration.supportBigIntPromotion();
	}
	@Override
	public final boolean supportBigDecimal() {
		return configuration.supportBigDecimal();
	}
	@Override
	public final boolean supportBigDecimalLiteral() {
		return configuration.supportBigDecimalLiteral();
	}
	@Override
	public final boolean supportBigDecimalPromotion() {
		return configuration.supportBigDecimalPromotion();
	}
	@Override
	public final boolean supportBigNumberMath() {
		return configuration.supportBigNumberMath();
	}
	@Override
	public final boolean forceBigDecimalOperations() {
		return configuration.forceBigDecimalOperations();
	}
	@Override
	public final boolean supportMixedBigNumber() {
		return configuration.supportMixedBigNumber();
	}
	@Override
	public final boolean supportParseIntOctal() {
		return configuration.supportParseIntOctal();
	}
	@Override
	public final boolean supportSequenceExtensions() {
		return configuration.supportSequenceExtensions();
	}
	@Override
	public final int getScriptCacheSize() {
		return configuration.getScriptCacheSize();
	}
	@Override
	public final int getEvalCacheSize() {
		return configuration.getEvalCacheSize();
	}
	@Override
	public final int getRegExpCacheSize() {
		return configuration.getRegExpCacheSize();
	}
	@Override
	public BiFunction<JSEnvironment,RegExp,RegExpEngine> getRegexpEngineFactory() {
		return configuration.getRegexpEngineFactory();
	}
	@Override
	public final MathContext getMathContext() {
		return configuration.getMathContext();
	}
	@Override
	public final String getMathContextTranspiler() {
		return configuration.getMathContextTranspiler();
	}
	
	/////////////////////////////////////////////////////////////////////////
	// Script properties
	/////////////////////////////////////////////////////////////////////////

	public Object getProperty(String key) {
		return getProperty(key,null);
	}
	public Object getProperty(String key, Object def) {
		Map<String,Object> properties = configuration.properties;
		Object v = properties!=null ? properties.get(key) : null;
		return v!=null ? v : def;
	}

	public boolean getPropertyBoolean(String key) {
		return getPropertyBoolean(key,false);
	}
	public boolean getPropertyBoolean(String key, boolean def) {
		Object v = getProperty(key);
		if(v instanceof Boolean b) {
			return b;
		}
		return def;
	}

	public int getPropertyInt(String key) {
		return getPropertyInt(key,0);
	}
	public int getPropertyInt(String key, int def) {
		Object v = getProperty(key);
		if(v instanceof Number n) {
			return n.intValue();
		}
		return def;
	}

	public String getPropertyString(String key) {
		return getPropertyString(key,null);
	}
	public String getPropertyString(String key, String def) {
		Object v = getProperty(key);
		if(v instanceof CharSequence) {
			return v.toString();
		}
		return def;
	}

	public void resetProperties() {
		configuration.properties = null;
	}


	/////////////////////////////////////////////////////////////////////////
	// Regular Expression
	/////////////////////////////////////////////////////////////////////////

	// The cache has its own lock (and compiles outside of it)
	public Pattern getRegExp(String expr, Function<String,Pattern> factory) {
		CacheProvider<String,Pattern> regExpCache = sharedData.getRegExpCache();
		if(regExpCache!=null) {
			return regExpCache.get(expr, factory);
		}
		return factory.apply(expr);
	}
	public RegExpEngine createRegExpEngine(RegExp regExp) {
		return configuration.getRegexpEngineFactory().apply(this, regExp);
	}

	

	/////////////////////////////////////////////////////////////////////////
	// Script creation
	/////////////////////////////////////////////////////////////////////////

	public static final int SCRIPT_ADDTOCACHE 	= 0x0001;
	public static final int SCRIPT_COMMONJS 	= 0x0002;
	// Set by a module resolver loading genuine ES module source (never by an
	// ordinary script or CommonJS-wrapped file compile) - see ASTProgram.
	// isModule()'s own doc comment for what this gates.
	public static final int SCRIPT_MODULE 		= 0x0004;
	// Set only for genuine eval'd source (createEvalScript's own callers) -
	// see ASTProgram.isEval()'s own doc comment for what this gates. Unlike
	// isModule(), this is NOT a reliable "is this eval code" signal on its
	// own inverse - ordinary (non-eval, non-module) scripts also have
	// isModule()==false, so a check needs THIS flag specifically, not
	// !isModule().
	public static final int SCRIPT_EVAL 		= 0x0008;

	public JSInterpretedUnit createExpression(String text) {
		return createScript(text, JSUnitContext.DEFAULT_EXPRESSION_NAME, SCRIPT_ADDTOCACHE);
	}
	public JSInterpretedUnit createExpression(String text, int flags) {
		return createScript(text, JSUnitContext.DEFAULT_EXPRESSION_NAME, flags);
	}

	public JSInterpretedUnit createScript(String text, String moduleName) {
		return createScript(text, moduleName, SCRIPT_ADDTOCACHE);
	}
	public JSInterpretedUnit createScript(String text, String moduleName, int flags) {
		return createScript(text, moduleName, flags, false);
	}
	protected JSInterpretedUnit createScript(String text, String moduleName, int flags, boolean forceStrict) {
		return createScript(text, moduleName, flags, forceStrict, true, true, true);
	}
	// callerHasNewTarget/callerIsMethod/callerIsDerivedCtor: caller-derived facts
	// (PerformEval's inFunc/inMethod/inDerivedCtor) used to decide whether
	// `new.target`/`super()`/`super.prop` appearing in this text are an early
	// SyntaxError - see ASTProgram.checkEvalCallerRestrictions(). All-true is the
	// permissive "don't check" default (an ordinary, non-eval script compile, or
	// any caller not yet updated to compute real facts): each check is gated on
	// its own fact being false, so all-true trivially finds nothing to reject.
	protected JSInterpretedUnit createScript(String text, String moduleName, int flags, boolean forceStrict, boolean callerHasNewTarget, boolean callerIsMethod, boolean callerIsDerivedCtor) {
		return createScript(text, moduleName, flags, forceStrict, callerHasNewTarget, callerIsMethod, callerIsDerivedCtor, false);
	}
	// callerInParameterExpressionScope: true when the direct-eval call site
	// sits lexically inside the nearest enclosing function's own default
	// parameter-value expression (evaluated in a scope distinct from - and
	// never granting access to - that function's own "arguments" binding),
	// rather than its body. Declaring a var named "arguments" via such an
	// eval is always a SyntaxError, regardless of any other "arguments"
	// binding existing elsewhere (a same-scope preceding/following parameter
	// literally named "arguments", or a body-level var/let/function) - see
	// ASTProgram.checkParameterExpressionArgumentsRestriction(). False (the
	// permissive default) for every non-eval script compile and for an
	// indirect eval (which never depends on the caller's lexical position at
	// all).
	protected JSInterpretedUnit createScript(String text, String moduleName, int flags, boolean forceStrict, boolean callerHasNewTarget, boolean callerIsMethod, boolean callerIsDerivedCtor, boolean callerInParameterExpressionScope) {
		return createScript(text, moduleName, flags, forceStrict, callerHasNewTarget, callerIsMethod, callerIsDerivedCtor, callerInParameterExpressionScope, false);
	}
	// callerInFieldInitializer: true when the direct-eval call site sits
	// lexically inside a class field's own Initializer expression (through
	// any number of transparent arrow functions, but NOT through a nested
	// non-arrow function, which establishes its own genuine "arguments"
	// scope) - see ASTProgram.checkFieldInitializerArgumentsRestriction().
	// A field initializer never has its own "arguments" binding (it isn't a
	// function body at all), so the eval'd text REFERENCING "arguments"
	// anywhere within it (not just declaring it, unlike the parameter-
	// expression case above) is always a SyntaxError. False (permissive)
	// for every non-eval script compile and for an indirect eval.
	protected JSInterpretedUnit createScript(String text, String moduleName, int flags, boolean forceStrict, boolean callerHasNewTarget, boolean callerIsMethod, boolean callerIsDerivedCtor, boolean callerInParameterExpressionScope, boolean callerInFieldInitializer) {
		// No caller-private-names argument here: every caller of this overload
		// is an ordinary (non-eval) compile, which must always enforce
		// AllPrivateNamesValid with an empty starting set - see
		// PrivateNameValidator's class doc.
		return createScript(text,moduleName,flags,forceStrict,callerHasNewTarget,callerIsMethod,callerIsDerivedCtor,callerInParameterExpressionScope,callerInFieldInitializer,java.util.Collections.emptySet());
	}
	// callerPrivateNames: see ASTProgram.__init()'s matching parameter - the
	// set of private names visible from a direct eval's call site, or null to
	// skip the AllPrivateNamesValid check entirely (a direct eval from a
	// TRANSPILED caller only - see PrivateNameValidator's class doc).
	protected JSInterpretedUnit createScript(String text, String moduleName, int flags, boolean forceStrict, boolean callerHasNewTarget, boolean callerIsMethod, boolean callerIsDerivedCtor, boolean callerInParameterExpressionScope, boolean callerInFieldInitializer, Set<String> callerPrivateNames) {
		boolean addToCache = (flags & SCRIPT_ADDTOCACHE)!=0;
		boolean commonJS = (flags & SCRIPT_COMMONJS)!=0;
		boolean callerFactsPermissive = callerHasNewTarget && callerIsMethod && callerIsDerivedCtor && !callerInParameterExpressionScope && !callerInFieldInitializer && callerPrivateNames!=null && callerPrivateNames.isEmpty();
		// A forced-strict parse - or one whose accept/reject outcome for
		// new.target/super depends on the calling context (non-permissive caller
		// facts) - must never be cached under the shared text-only key: the exact
		// same source text must parse differently depending on the calling
		// context, and the cache has no way to distinguish the two - so bypass it
		// in that case.
		if(addToCache && !commonJS && !forceStrict && callerFactsPermissive) {
			CacheProvider<String,ASTProgram> cache = getProgramCache(flags);
			if(cache!=null) {
				ASTProgram program = cache.get(text, (t) -> compileProgram(t,moduleName,flags));
				return createUnit(program,moduleName);
			}
		}
		ASTProgram program = compileProgram(text,moduleName,flags,forceStrict,callerHasNewTarget,callerIsMethod,callerIsDerivedCtor,callerInParameterExpressionScope,callerInFieldInitializer,callerPrivateNames);
		return createUnit(program,moduleName);
	}


	// The program compiled from a text depends on the flags, not only on the
	// text: eval code has its own cache, module code is never cached.
	private CacheProvider<String,ASTProgram> getProgramCache(int flags) {
		if((flags & SCRIPT_MODULE)!=0) {
			return null;
		}
		return (flags & SCRIPT_EVAL)!=0 ? sharedData.getEvalCache() : sharedData.getScriptCache();
	}

	////
	// That should be protected
	public JSInterpretedUnit createEvalScript(String text, String moduleName) {
		return createEvalScript(text, moduleName, false);
	}
	public JSInterpretedUnit createEvalScript(String text, String moduleName, boolean forceStrict) {
		return createScript(text, moduleName, SCRIPT_ADDTOCACHE | SCRIPT_EVAL, forceStrict);
	}
	public JSInterpretedUnit createEvalScript(String text, String moduleName, boolean forceStrict, boolean callerHasNewTarget, boolean callerIsMethod, boolean callerIsDerivedCtor) {
		return createEvalScript(text, moduleName, forceStrict, callerHasNewTarget, callerIsMethod, callerIsDerivedCtor, false);
	}
	public JSInterpretedUnit createEvalScript(String text, String moduleName, boolean forceStrict, boolean callerHasNewTarget, boolean callerIsMethod, boolean callerIsDerivedCtor, boolean callerInParameterExpressionScope) {
		return createEvalScript(text, moduleName, forceStrict, callerHasNewTarget, callerIsMethod, callerIsDerivedCtor, callerInParameterExpressionScope, false);
	}
	public JSInterpretedUnit createEvalScript(String text, String moduleName, boolean forceStrict, boolean callerHasNewTarget, boolean callerIsMethod, boolean callerIsDerivedCtor, boolean callerInParameterExpressionScope, boolean callerInFieldInitializer) {
		return createScript(text, moduleName, SCRIPT_ADDTOCACHE | SCRIPT_EVAL, forceStrict, callerHasNewTarget, callerIsMethod, callerIsDerivedCtor, callerInParameterExpressionScope, callerInFieldInitializer);
	}
	// callerPrivateNames: see ASTProgram.__init()'s matching parameter - the
	// set of private names visible from a direct eval's call site (collected
	// via JSRuntimeContext.collectEnclosingPrivateNames()), or null to skip
	// the AllPrivateNamesValid check entirely (a direct eval from a
	// TRANSPILED caller only - see PrivateNameValidator's and
	// StandardLibrary's eval case comments).
	public JSInterpretedUnit createEvalScript(String text, String moduleName, boolean forceStrict, boolean callerHasNewTarget, boolean callerIsMethod, boolean callerIsDerivedCtor, boolean callerInParameterExpressionScope, boolean callerInFieldInitializer, Set<String> callerPrivateNames) {
		return createScript(text, moduleName, SCRIPT_ADDTOCACHE | SCRIPT_EVAL, forceStrict, callerHasNewTarget, callerIsMethod, callerIsDerivedCtor, callerInParameterExpressionScope, callerInFieldInitializer, callerPrivateNames);
	}
	public JSInterpretedUnit createEvalScript(String text, String moduleName, int flags) {
		return createScript(text, moduleName, flags | SCRIPT_EVAL);
	}
	public ASTFunctionDecl createFunction(String text) {
		return compileFunction(text);
	}
	////

	
	protected JSInterpretedUnit createUnit(ASTProgram program, String moduleName) {
		return new StaticScriptDescriptor(moduleName,program).loadScript(this);
	}

	protected ASTProgram compileProgram(String text, String moduleName, int flags) {
		return compileProgram(text,moduleName,flags,false);
	}

	protected ASTProgram compileProgram(String text, String moduleName, int flags, boolean forceStrict) {
		return compileProgram(text,moduleName,flags,forceStrict,true,true,true,false);
	}

	protected ASTProgram compileProgram(String text, String moduleName, int flags, boolean forceStrict, boolean callerHasNewTarget, boolean callerIsMethod, boolean callerIsDerivedCtor) {
		return compileProgram(text,moduleName,flags,forceStrict,callerHasNewTarget,callerIsMethod,callerIsDerivedCtor,false);
	}

	protected ASTProgram compileProgram(String text, String moduleName, int flags, boolean forceStrict, boolean callerHasNewTarget, boolean callerIsMethod, boolean callerIsDerivedCtor, boolean callerInParameterExpressionScope) {
		return compileProgram(text,moduleName,flags,forceStrict,callerHasNewTarget,callerIsMethod,callerIsDerivedCtor,callerInParameterExpressionScope,false);
	}

	protected ASTProgram compileProgram(String text, String moduleName, int flags, boolean forceStrict, boolean callerHasNewTarget, boolean callerIsMethod, boolean callerIsDerivedCtor, boolean callerInParameterExpressionScope, boolean callerInFieldInitializer) {
		// No caller-private-names argument here: every caller of this overload
		// is an ordinary (non-eval) compile, which must always enforce
		// AllPrivateNamesValid with an empty starting set - see
		// PrivateNameValidator's class doc.
		return compileProgram(text,moduleName,flags,forceStrict,callerHasNewTarget,callerIsMethod,callerIsDerivedCtor,callerInParameterExpressionScope,callerInFieldInitializer,java.util.Collections.emptySet());
	}

	// callerPrivateNames: see ASTProgram.__init()'s matching parameter.
	protected ASTProgram compileProgram(String text, String moduleName, int flags, boolean forceStrict, boolean callerHasNewTarget, boolean callerIsMethod, boolean callerIsDerivedCtor, boolean callerInParameterExpressionScope, boolean callerInFieldInitializer, Set<String> callerPrivateNames) {
        boolean module = (flags & SCRIPT_MODULE)!=0;
        JSParser parser=new JSParser(this,text,isDebugEnabled(),module);
        return new ParserContextImpl(this).with( () -> {
	        ASTProgram program = null;
	        try {
	            program= parser.MainProgram(text);
	            if(module) {
	            	parser.checkModuleCode();
	            }
	            program.setYieldLabelledStatements(parser.getYieldLabelledStatements());
	            program.__init(this,(flags & SCRIPT_COMMONJS)!=0,(flags & SCRIPT_MODULE)!=0,(flags & SCRIPT_EVAL)!=0,forceStrict,callerHasNewTarget,callerIsMethod,callerIsDerivedCtor,callerInParameterExpressionScope,callerInFieldInitializer,callerPrivateNames);
	            ScriptOptimizer scriptOptimizer = getScriptOptimizer();
	            if(scriptOptimizer!=null) {
	            	scriptOptimizer.optimize(JSEnvironment.this,program);
	            }
	            return program;
	    		//return new StaticModuleDescriptor(moduleName,program).loadModule(null);
	        } catch(TokenMgrError ex) {
	        	//ex.printStackTrace();
	        	String msg = ex.getLocalizedMessage();
	        	int line = extractNumber(msg, "line (\\d+)");
	        	int col = extractNumber(msg, "column (\\d+)");
	        	if(line>0) {
	            	StringBuilder builder = new StringBuilder();
	            	builder.append(StringFormat.format("TokenMgrError while parsing script\n{0}\nat line {1}, column {2}\n",ex.getLocalizedMessage(),line, col));
	            	JSException.extractSourceCode(builder, JSException.EXTRACT_LINES, text, line, col);
	            	String err = builder.toString();
	            	//GlobalObject.rawLog(text);
	            	throw new JSParseException(ex,null,err).setSourceNode(program);
	        	}
	            throw new JSParseException(ex,null,"TokenMgrError while parsing script").setSourceNode(program);
	        } catch(ParseException ex) {
	        	//ex.printStackTrace();
	        	Token tf = ex.currentToken;
	        	int line = tf.beginLine;
	        	int col = tf.beginColumn;
	        	StringBuilder builder = new StringBuilder();
	        	builder.append(StringFormat.format("Error while parsing script\nEncountered {0}\nat line {1}, column {2}\n", tf.next, line, col));
	        	JSException.extractSourceCode(builder, JSException.EXTRACT_LINES, text, line, col);
	        	String err = builder.toString();
	        	//GlobalObject.rawLog(err);
	        	throw new JSParseException(ex,null,err).setSourceNode(program);
	        } catch(JSParseException t) {
	            throw t.setSourceNode(program);
	        } catch(Throwable t) {
	        	//t.printStackTrace();
	            throw new JSParseException(t,null,"Error while parsing script\n{0}",t.getMessage()).setSourceNode(program);
	        }
        });
	}

	protected ASTFunctionDecl compileFunction(String text) {
        JSParser parser=new JSParser(this,text,isDebugEnabled());
        return new ParserContextImpl(this).with( () -> {
	        try {
	            ASTFunctionDecl function= parser.MainFunction(text);
	            function.__init(this);
	            ScriptOptimizer scriptOptimizer = getScriptOptimizer();
	            if(scriptOptimizer!=null) {
	            	scriptOptimizer.optimize(JSEnvironment.this,function);
	            }
	    		return function;
	        } catch(TokenMgrError ex) {
	        	String msg = ex.getLocalizedMessage();
	        	int line = extractNumber(msg, "line (\\d+)");
	        	int col = extractNumber(msg, "column (\\d+)");
	        	if(line>0) {
	            	StringBuilder builder = new StringBuilder();
	            	builder.append(StringFormat.format("TokenMgrError while parsing script\n{0}\nat line {1}, column {2}\n",ex.getLocalizedMessage(),line, col));
	            	JSException.extractSourceCode(builder, JSException.EXTRACT_LINES, text, line, col);
	            	String err = builder.toString();
	            	//GlobalObject.rawLog(text);
	            	throw new JSParseException(ex,null,err);
	        	}
	            throw new JSParseException(ex,null,"TokenMgrError while parsing script");
	        } catch(ParseException ex) {
	        	Token tf = ex.currentToken;
	        	int line = tf.beginLine;
	        	int col = tf.beginColumn;
	        	StringBuilder builder = new StringBuilder();
	        	builder.append(StringFormat.format("Error while parsing script\nEncountered {0}\nat line {1}, column {2}\n", tf.next, line, col));
	        	JSException.extractSourceCode(builder, JSException.EXTRACT_LINES, text, line, col);
	        	String err = builder.toString();
	        	//GlobalObject.rawLog(err);
	        	throw new JSParseException(ex,null,err);
	        } catch(JSParseException t) {
	            throw t;
	        } catch(Throwable t) {
	            throw new JSParseException(t,null,"Error while parsing script\n{0}",t.getMessage());
	        }
        });
	}
	private static int extractNumber(String msg, String pattern) {
    	Matcher m = Pattern.compile(pattern).matcher(msg);
    	if(m.find()) {
    		String s = m.group(1);
    		return Integer.parseInt(s);
    	}
    	return -1;
	}


	/////////////////////////////////////////////////////////////////////////
	// Script execution
	// These are just shortcuts - more flexible code is available from the 
	// script units themselves.
	/////////////////////////////////////////////////////////////////////////

	
    public <T> T evaluateScript(String script) {
    	return evaluateScript(script,null);
    }
    public <T> T evaluateScript(String script, Object _this) {
		return evaluate(new InterpretedGlobalRuntimeContext(this,createProgramExecutor(),_this), script);
    }

	public <T> T evaluateExpression(String script) {
    	return evaluateExpression(script,null);
	}
	public <T> T evaluateExpression(String script, Object _this) {
		return evaluate(new InterpretedGlobalRuntimeContext(this,createExpressionExecutor(),_this), script);
	}

	// This environment's own top-level (global) runtime context, created on
	// first use and kept for the environment's lifetime - the realm's "root"
	// for host/cross-realm entry points that reach this environment WITHOUT a
	// caller context of their own already in it: a `Function` constructor
	// invoked from another realm (its new function must be created in THIS
	// realm), an indirect `eval` through this realm's own `eval` builtin, or a
	// test harness's `$262.createRealm().evalScript(...)`. Ordinary script
	// execution never touches this; it keeps creating its own contexts.
	private volatile InterpretedGlobalRuntimeContext realmContext;
	public InterpretedGlobalRuntimeContext getRealmContext() {
		InterpretedGlobalRuntimeContext ctx = realmContext;
		if(ctx==null) {
			synchronized(this) {
				ctx = realmContext;
				if(ctx==null) {
					ctx = realmContext = new InterpretedGlobalRuntimeContext(this, createProgramExecutor());
				}
			}
			// Asked from code running in another realm: the two realms now
			// share objects (a realm created by a script, a function created
			// in another realm) - see linkRealms()
			JSEnvironment creator = getEnvironmentUnchecked();
			if(creator!=null && creator!=this) {
				linkRealms(creator, this);
			}
		}
		return ctx;
	}

	// Kinds of PrimitivePropertyMap (see findInLinkedRealms())
	public static final int PROPERTIES_OBJECT = 0;
	public static final int PROPERTIES_STRING = 1;
	public static final int PROPERTIES_NUMBER = 2;
	public static final int PROPERTIES_BOOLEAN = 3;
	public static final int PROPERTIES_SYMBOL = 4;

	// Realms that exchange objects: a boxed primitive or a Java-backed value
	// (a Date...) keeps its state (prototype, properties) in the
	// PrimitivePropertyMap of the realm that created it, where the others
	// look for it. Null for an environment that is not linked to another.
	private volatile java.util.List<java.lang.ref.WeakReference<JSEnvironment>> realmGroup;

	public boolean hasLinkedRealms() {
		return realmGroup!=null;
	}

	/**
	 * Links two realms (and the realms already linked to either).
	 */
	public static void linkRealms(JSEnvironment a, JSEnvironment b) {
		synchronized(JSEnvironment.class) {
			java.util.List<java.lang.ref.WeakReference<JSEnvironment>> group = a.realmGroup!=null ? a.realmGroup
					: b.realmGroup!=null ? b.realmGroup : new java.util.concurrent.CopyOnWriteArrayList<>();
			for(JSEnvironment e: new JSEnvironment[] {a, b}) {
				if(e.realmGroup!=null && e.realmGroup!=group) {
					for(java.lang.ref.WeakReference<JSEnvironment> r: e.realmGroup) {
						JSEnvironment m = r.get();
						if(m!=null) {
							m.realmGroup = group;
							addToGroup(group, m);
						}
					}
				}
				e.realmGroup = group;
				addToGroup(group, e);
			}
		}
	}

	private static void addToGroup(java.util.List<java.lang.ref.WeakReference<JSEnvironment>> group, JSEnvironment e) {
		// Drop the realms that were garbage collected
		group.removeIf(r -> r.get()==null);
		for(java.lang.ref.WeakReference<JSEnvironment> r: group) {
			if(r.get()==e) {
				return;
			}
		}
		group.add(new java.lang.ref.WeakReference<>(e));
	}

	public org.monflabs.galtajs.jsonfactory.JSObjectImpl findInLinkedRealms(int kind, Object key) {
		java.util.List<java.lang.ref.WeakReference<JSEnvironment>> group = realmGroup;
		if(group==null) {
			return null;
		}
		for(java.lang.ref.WeakReference<JSEnvironment> r: group) {
			JSEnvironment other = r.get();
			if(other!=null && other!=this) {
				PrimitivePropertyMap m = other.propertyMap(kind);
				if(m!=null) {
					org.monflabs.galtajs.jsonfactory.JSObjectImpl v = m.getLocal(key);
					if(v!=null) {
						return v;
					}
				}
			}
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public <T> T evaluate(JSInterpretedRuntimeContext context, String script) {
		// Optimization: look for a simple variable, very simple expressions!
		Object v = context.getVariableValue(script,RuntimeUtil.NOT_AVAILABLE);
		if(v!=RuntimeUtil.NOT_AVAILABLE) {
			return (T)v;
		}

		// Else compile the expression and evaluate it
		JSInterpretedUnit ex = createExpression(script,0); // No cache
		return (T)ex.executeWithContext(context);
	}



	/////////////////////////////////////////////////////////////////////////
	// Library registration
	/////////////////////////////////////////////////////////////////////////

	public CustomLibraries getLibraries() {
		return configuration.libraries;
	}

	public JSJavaLibrary getJavaLibrary() {
		return configuration.libraries.getJavaLibrary();
	}
}
