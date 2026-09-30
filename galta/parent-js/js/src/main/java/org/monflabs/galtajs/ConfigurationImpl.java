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

import java.math.MathContext;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.monflabs.galtajs.library.CustomLibraries;
import org.monflabs.galtajs.modules.JSModuleResolver;
import org.monflabs.galtajs.optimizer.ScriptOptimizer;
import org.monflabs.galtajs.rt.builtins.standard.regexp.RegExp;
import org.monflabs.galtajs.rt.builtins.standard.regexp.RegExpEngine;
import org.monflabs.galtajs.rt.builtins.standard.regexp.joni.RegExpEngineJoni;



/**
 * Configuraton of the environment.
 */
public final class ConfigurationImpl implements JSConfiguration {
	
	// Default values
	// The customized Joni copy is part of this module, so it is always the
	// default; RegExpEngineJdkJavascript is an explicit opt-in.
	private static final BiFunction<JSEnvironment,RegExp,RegExpEngine> defaultRegExpEngineFactory = RegExpEngineJoni.factory();
	
	
	
	boolean strictMode=false;
	boolean debug=false;
	boolean sharedStandardObjects=false;
	List<JSModuleResolver> moduleResolvers;
	ScriptOptimizer scriptOptimizer = ScriptOptimizer.defaultOptimizer();
	CustomLibraries libraries;
	Map<String,Object> properties;
	ClassLoader classLoader = ConfigurationImpl.class.getClassLoader();

	boolean deprecatedApis=true;
	boolean supportJavaNative=false;
	boolean supportFloat16Array=false;
	boolean supportGlobalAlias=false;
	boolean isOptimizedSetAndMapCtor=false; // Should removed
	
	boolean mustDeclareAllVariables=false;
	boolean supportIdentifierAtSign=false;
	
	boolean supportReturnOutsideFunction=false;

	boolean supportTypeHints=false;

	boolean supportTopLevelObjectLiteral=false;

	boolean supportLongPromotion=false;
	boolean supportBigIntPromotion=false;
	boolean supportBigDecimal=false;
	boolean supportBigDecimalLiteral=false;
	boolean supportBigDecimalPromotion=false;
	boolean supportBigNumberMath=false;
	boolean forceBigDecimalOperations=false;
	boolean supportMixedBigNumber=false;
	boolean supportParseIntOctal=true;
	
	boolean supportSequenceExtensions=false;

	int scriptCacheSize;
	int evalCacheSize;
	int regExpCacheSize;

	BiFunction<JSEnvironment,RegExp,RegExpEngine> regExpEngineFactory = defaultRegExpEngineFactory;
	MathContext mathContext = MathContext.DECIMAL128;
	String mathContextTranspiler = "java.math.MathContext.DECIMAL128";
	
	protected ConfigurationImpl() {
	}
	
	@Override
	public final boolean isStrictMode() {
		return strictMode;
	}
	@Override
	public final boolean isDebugEnabled() {
		return debug;
	}
	@Override
	public final boolean isSharedStandardObjects() {
		return sharedStandardObjects;
	}
	@Override
	public List<JSModuleResolver> getModuleResolvers() {
		return moduleResolvers;
	}
	@Override
	public final ScriptOptimizer getScriptOptimizer() {
		return scriptOptimizer;
	}
	@Override
	public CustomLibraries getCustomLibraries() {
		return libraries;
	}
	@Override
	public Map<String,Object> getProperties() {
		return properties;
	}
	@Override
	public ClassLoader getClassLoader() {
		return classLoader;
	}

	
	//
	// Global Objects
	//
	@Override
	public final boolean isDeprecatedApis() {
		return deprecatedApis;
	}
	@Override
	public final boolean supportJavaNative() {
		return supportJavaNative;
	}
	@Override
	public final boolean supportFloat16Array() {
		return supportFloat16Array;
	}
	@Override
	public final boolean supportGlobalAlias() {
		return supportGlobalAlias;
	}
	@Override
	public final boolean isOptimizedSetAndMapCtor() {
		return isOptimizedSetAndMapCtor;
	}

	//
	// Variables
	//
	@Override
	public final boolean mustDeclareAllVariables() {
		return mustDeclareAllVariables;
	}
	@Override
	public final boolean supportIdentifierAtSign() {
		return supportIdentifierAtSign;
	}
	
	//
	// Return statement
	//
	@Override
	public final boolean supportReturnOutsideFunction() {
		return supportReturnOutsideFunction;
	}

	//
	// Type hints
	//
	@Override
	public final boolean supportTypeHints() {
		return supportTypeHints;
	}
	@Override
	public final boolean supportTopLevelObjectLiteral() {
		return supportTopLevelObjectLiteral;
	}

	//
	// Numbers
	//
	@Override
	public final boolean supportLongPromotion() {
		return supportLongPromotion;
	}
	@Override
	public final boolean supportBigIntPromotion() {
		return supportBigIntPromotion;
	}
	@Override
	public final boolean supportBigDecimal() {
		return supportBigDecimal;
	}
	@Override
	public final boolean supportBigDecimalLiteral() {
		return supportBigDecimalLiteral;
	}
	@Override
	public final boolean supportBigDecimalPromotion() {
		return supportBigDecimalPromotion;
	}
	@Override
	public final boolean supportBigNumberMath() {
		return supportBigNumberMath;
	}
	@Override
	public final boolean forceBigDecimalOperations() {
		return forceBigDecimalOperations;
	}
	@Override
	public final boolean supportMixedBigNumber() {
		return supportMixedBigNumber;
	}
	@Override
	public final boolean supportParseIntOctal() {
		return supportParseIntOctal;
	}

	//
	// Json Path
	//
	@Override
	public final boolean supportSequenceExtensions() {
		return supportSequenceExtensions;
	}

	//
	// Cache sizes
	//
	@Override
	public final int getScriptCacheSize() {
		return scriptCacheSize;
	}
	@Override
	public final int getEvalCacheSize() {
		return evalCacheSize;
	}
	@Override
	public final int getRegExpCacheSize() {
		return regExpCacheSize;
	}

	//
	// Regexp
	//
	@Override
	public BiFunction<JSEnvironment,RegExp,RegExpEngine> getRegexpEngineFactory() {
		return regExpEngineFactory;
	}

	//
	// Decinal options
	//
	@Override
	public final MathContext getMathContext() {
		return mathContext;
	}
	@Override
	public final String getMathContextTranspiler() {
		return mathContextTranspiler;
	}
}
