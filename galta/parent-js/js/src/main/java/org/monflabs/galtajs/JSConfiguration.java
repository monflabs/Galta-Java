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



/**
 * Configuraton of the environment.
 */
public interface JSConfiguration {
	
	public static int DEFAULT_SCRIPT_CACHE_SIZE 	= 64;
	public static int DEFAULT_EVAL_CACHE_SIZE   	= 64;
	public static int DEFAULT_REGEXP_CACHE_SIZE 	= 64;
	
	public boolean isStrictMode();
	public boolean isDebugEnabled();
	public boolean isSharedStandardObjects();
	public List<JSModuleResolver> getModuleResolvers();
	public ScriptOptimizer getScriptOptimizer();
	public CustomLibraries getCustomLibraries();
	public Map<String,Object> getProperties();
	public ClassLoader getClassLoader();

	
	//
	// Global Objects
	//
	public boolean isDeprecatedApis();
	public boolean supportJavaNative();
	public boolean supportFloat16Array();
	public boolean supportGlobalAlias();
	public boolean isOptimizedSetAndMapCtor();

	//
	// Variables
	//
	public boolean mustDeclareAllVariables();
	public boolean supportIdentifierAtSign();
	
	//
	// Return statement
	//
	public boolean supportReturnOutsideFunction();

	//
	// Type hints (TypeScript-style, parsed and discarded - no type checking)
	//
	public boolean supportTypeHints();

	//
	// A program whose entire text is an object literal ({}, {a: 1}, {"a": 1})
	// evaluates to that object instead of being read as a block/labeled
	// statement (REPL-style convenience)
	//
	public boolean supportTopLevelObjectLiteral();
	
	//
	// Numbers
	// 
	public boolean supportLongPromotion();
	public boolean supportBigIntPromotion();
	public boolean supportBigDecimal();
	public boolean supportBigDecimalLiteral();
	public boolean supportBigDecimalPromotion();
	public boolean supportBigNumberMath();
	public boolean forceBigDecimalOperations();
	public boolean supportMixedBigNumber();
	public boolean supportParseIntOctal();

	//
	// RegExp
	//
	public BiFunction<JSEnvironment,RegExp,RegExpEngine> getRegexpEngineFactory();

	//
	// Json Path
	//
	public boolean supportSequenceExtensions();

	//
	// Cache sizes
	//
	public int getScriptCacheSize();
	public int getEvalCacheSize();
	public int getRegExpCacheSize();
	
	//
	// Decinal options
	//
	public MathContext getMathContext();
	public String getMathContextTranspiler();
}
