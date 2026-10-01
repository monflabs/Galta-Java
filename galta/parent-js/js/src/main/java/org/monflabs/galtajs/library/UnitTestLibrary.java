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
package org.monflabs.galtajs.library;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;
import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.rt.DebugUtil;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.JSRuntimeException;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.errors.BaseError;
import org.monflabs.galtajs.rt.builtins.primitives.BaseStandardConstructor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.rt.builtins.standard.console.ConsoleToString;
import org.monflabs.galtajs.rt.builtins.standard.global.StandardObjects;
import org.monflabs.galtajs.rt.interpreter.VariableMap;
import org.monflabs.json.JsonContainer;
import org.monflabs.util.Console;
import org.monflabs.util.StringUtil;
import org.monflabs.util.function.TriPredicate;


/**
 * Unit test library.
 */
public class UnitTestLibrary extends GlobalLibrary {
	
	// assertDeclared/assertNotDeclared/assertDeclaredInScope/assertNotDeclaredInScope
	// predate the Temporal Dead Zone: their contract is "can I read a real
	// value here" - a let/const binding still in its TDZ throws a
	// ReferenceError from the very read paths these helpers use, which would
	// otherwise crash the assertion itself instead of letting it report a
	// clean true/false. Swallow just that one ReferenceError as "not
	// available", matching how these helpers already treat a genuinely
	// undeclared name - a TDZ-in-progress binding is deliberately reported
	// the same way here.
	private static boolean existsIgnoringTDZ(java.util.function.Supplier<Object> read) {
		try {
			return isBoundValue(read.get());
		} catch(JSRuntimeException ex) {
			return false;
		}
	}
	private static boolean isBoundValue(Object v) {
		return v!=RuntimeUtil.NOT_AVAILABLE && v!=RuntimeUtil.TDZ;
	}

	private static class SparseEmpty {
		private long size;
		public SparseEmpty(long size) {
			this.size = size;
		}
	}
	
	private static final String PROP_SUITETITLE = "monflabs.unittest.suite-title";
	private static final String PROP_TESTTITLE = "monflabs.unittest.test-title";
	
	static enum FunctionIndex {
		suite,
		test,
		fail,
		
		assertNull,
		assertNotNull,
		assertUndefined,
		assertNotUndefined,
		assertNullOrUndefined,
		assertNotNullOrUndefined,
		assertTrue,
		assertFalse,
		assertEquals,
		assertEqualsStrict,
		assertNotEquals,
		assertNotEqualsStrict,
		assertArrayEquals,
		assertSame,
		assertNotSame,
		assertParse,
		assertParseError,
		assertDeclared,
		assertNotDeclared,
		assertDeclaredInScope,
		assertNotDeclaredInScope,
		assertThrows,
		
		jsonDeepClone,
		_introspect,
		SPARSE,
		EMPTY,
		
		_n,
		_strictMode,
		
		loadTextResource,
		loadJsonResource,
	}

	public UnitTestLibrary() {
	}
	
	@Override
	public void configureStandardObjects(JSEnvironment env, StandardObjects standardObjects) {
		standardObjects.setOwnMethod(new GlobalFunction(env,"suite",FunctionIndex.test,1));
		standardObjects.setOwnMethod(new GlobalFunction(env,"test",FunctionIndex.test,1));
		standardObjects.setOwnMethod(new GlobalFunction(env,"fail",FunctionIndex.fail,0));
		standardObjects.setOwnMethod(new GlobalFunction(env,"assertNull",FunctionIndex.assertNull,1));
		standardObjects.setOwnMethod(new GlobalFunction(env,"assertNotNull",FunctionIndex.assertNotNull,1));
		standardObjects.setOwnMethod(new GlobalFunction(env,"assertUndefined",FunctionIndex.assertUndefined,1));
		standardObjects.setOwnMethod(new GlobalFunction(env,"assertNotUndefined",FunctionIndex.assertNotUndefined,1));
		standardObjects.setOwnMethod(new GlobalFunction(env,"assertNullOrUndefined",FunctionIndex.assertNullOrUndefined,1));
		standardObjects.setOwnMethod(new GlobalFunction(env,"assertNotNullOrUndefined",FunctionIndex.assertNotNullOrUndefined,1));
		standardObjects.setOwnMethod(new GlobalFunction(env,"assertTrue",FunctionIndex.assertTrue,1));
		standardObjects.setOwnMethod(new GlobalFunction(env,"assertFalse",FunctionIndex.assertFalse,1));
		standardObjects.setOwnMethod(new GlobalFunction(env,"assertEquals",FunctionIndex.assertEquals,2));
		standardObjects.setOwnMethod(new GlobalFunction(env,"assertEqualsStrict",FunctionIndex.assertEqualsStrict,2));
		standardObjects.setOwnMethod(new GlobalFunction(env,"assertNotEquals",FunctionIndex.assertNotEquals,2));
		standardObjects.setOwnMethod(new GlobalFunction(env,"assertNotEqualsStrict",FunctionIndex.assertNotEqualsStrict,2));
		standardObjects.setOwnMethod(new GlobalFunction(env,"assertArrayEquals",FunctionIndex.assertArrayEquals,2));
		standardObjects.setOwnMethod(new GlobalFunction(env,"assertSame",FunctionIndex.assertSame,2));
		standardObjects.setOwnMethod(new GlobalFunction(env,"assertNotSame",FunctionIndex.assertNotSame,2));
		standardObjects.setOwnMethod(new GlobalFunction(env,"assertParse",FunctionIndex.assertParse,0));
		standardObjects.setOwnMethod(new GlobalFunction(env,"assertParseError",FunctionIndex.assertParseError,0));
		standardObjects.setOwnMethod(new GlobalFunction(env,"assertDeclared",FunctionIndex.assertDeclared,2));
		standardObjects.setOwnMethod(new GlobalFunction(env,"assertNotDeclared",FunctionIndex.assertNotDeclared,2));
		standardObjects.setOwnMethod(new GlobalFunction(env,"assertDeclaredInScope",FunctionIndex.assertDeclaredInScope,2));
		standardObjects.setOwnMethod(new GlobalFunction(env,"assertNotDeclaredInScope",FunctionIndex.assertNotDeclaredInScope,2));
		standardObjects.setOwnMethod(new GlobalFunction(env,"assertThrows",FunctionIndex.assertThrows,1));

		standardObjects.setOwnMethod(new GlobalFunction(env,"jsonDeepClone",FunctionIndex.jsonDeepClone,1));
		standardObjects.setOwnMethod(new GlobalFunction(env,"_introspect",FunctionIndex._introspect,0));
		standardObjects.setOwnMethod(new GlobalFunction(env,"SPARSE",FunctionIndex.SPARSE,1));
		standardObjects.setOwnMethod(new GlobalFunction(env,"EMPTY",FunctionIndex.EMPTY,1));

		standardObjects.setOwnMethod(new GlobalFunction(env,"_n",FunctionIndex._n,1));
		standardObjects.setOwnMethod(new GlobalFunction(env,"_strictMode",FunctionIndex._n,0));

		standardObjects.setOwnMethod(new GlobalFunction(env,"loadTextResource",FunctionIndex.loadTextResource,1));
		standardObjects.setOwnMethod(new GlobalFunction(env,"loadJsonResource",FunctionIndex.loadJsonResource,1));
		
		// To run 262 test suite without loading their assert.js
		GlobalFunction a = new GlobalFunction(env,"assert",FunctionIndex.assertTrue,1); // assert => assertTrue
		standardObjects.setOwnMethod(a);
		a.setOwnProperty("sameValue", standardObjects.get("assertEquals"));
		a.setOwnProperty("notSameValue", standardObjects.get("assertNotEquals"));
		a.setOwnProperty("throws", standardObjects.get("assertThrows"));
		a.setOwnProperty("compareArray", standardObjects.get("assertArrayEquals"));
		
		a.setOwnProperty("Test262Error", standardObjects.getConstructor(org.monflabs.galtajs.rt.builtins.errors.Error.ConstructorImpl.CLASSNAME));
	}
	
	private final static class GlobalFunction extends BaseMethod {
		private FunctionIndex index;

		GlobalFunction(JSEnvironment env, String functionName, FunctionIndex index, int length) {
			super(env,functionName,length);
			this.index = index;
		}

		@Override
		protected Object invoke(Object _this, Object[] parameters) {
			switch(index) {
				case suite: {
                    String title = paramString(parameters, 0);
                    Object oldTitle = JSRuntimeContext.get().getGlobalContext().getProperty(PROP_SUITETITLE); 
                    JSRuntimeContext.get().getGlobalContext().putProperty(PROP_SUITETITLE, title);
                    try {
                    	JSRuntimeContext.get().getGlobalContext().putProperty(title, title);
	                    Callable function = paramCallableNotNull(parameters, 1);
	                    function.call(null, RuntimeUtil.EMPTY_PARAMS);
						return true;
                    } finally {
                    	JSRuntimeContext.get().getGlobalContext().putProperty(PROP_SUITETITLE, oldTitle);
                    }
				}
				case test: {
                    String title = paramString(parameters, 0);
                    Object oldTitle = JSRuntimeContext.get().getGlobalContext().getProperty(PROP_TESTTITLE); 
                    JSRuntimeContext.get().getGlobalContext().putProperty(PROP_TESTTITLE, title);
                    try {
                    	JSRuntimeContext.get().getGlobalContext().putProperty(title, title);
	                    Callable function = paramCallableNotNull(parameters, 1);
	                    function.call(null, RuntimeUtil.EMPTY_PARAMS);
						return true;
                    } finally {
                    	JSRuntimeContext.get().getGlobalContext().putProperty(PROP_TESTTITLE, oldTitle);
                    }
				}
				case fail: {
					Object p0 = param(parameters, 0, RuntimeUtil.NOT_AVAILABLE);
					JSRuntimeContext.get().dumpContext();
					Console.log(title()+"fail()");
					if(p0==RuntimeUtil.NOT_AVAILABLE) {
						throw RuntimeUtil.uncatchable(title()+"Assertion error, failure fail()");
					} else {
						throw RuntimeUtil.uncatchable(title()+"Assertion error, failure fail(), {0}", RuntimeUtil.toString(getEnvironment(), p0));
					}
				}
				case assertNull: {
					Object p0 = param(parameters, 0);
					if(p0!=null) {
						JSRuntimeContext.get().dumpContext();
						Console.log(title()+"assertNull({0})",DebugUtil.jsLiteral(getEnvironment(),p0));
						throw RuntimeUtil.uncatchable(title()+"Assertion error, isNull()\nWas: {0}",DebugUtil.jsLiteral(getEnvironment(),p0));
					}
					return true;
				}
				case assertNotNull: {
					Object p0 = param(parameters, 0);
					if(p0==null) {
						JSRuntimeContext.get().dumpContext();
						Console.log(title()+"assertNotNull({0})",DebugUtil.jsLiteral(getEnvironment(),p0));
						throw RuntimeUtil.uncatchable(title()+"Assertion error, isNotNull()\nWas: {0}",DebugUtil.jsLiteral(getEnvironment(),p0));
					}
					return true;
				}
				case assertUndefined: {
					Object p0 = param(parameters, 0);
					if(p0!=RuntimeUtil.UNDEFINED) {
						JSRuntimeContext.get().dumpContext();
						Console.log(title()+"assertUndefined({0})",DebugUtil.jsLiteral(getEnvironment(),p0));
						throw RuntimeUtil.uncatchable(title()+"Assertion error, isUndefined()\nWas: {0}",DebugUtil.jsLiteral(getEnvironment(),p0));
					}
					return true;
				}
				case assertNotUndefined: {
					Object p0 = param(parameters, 0);
					if(p0==RuntimeUtil.UNDEFINED) {
						JSRuntimeContext.get().dumpContext();
						Console.log(title()+"assertNotUndefined({0})",DebugUtil.jsLiteral(getEnvironment(),p0));
						throw RuntimeUtil.uncatchable(title()+"Assertion error, isNotUndefined()\nWas: {0}",DebugUtil.jsLiteral(getEnvironment(),p0));
					}
					return true;
				}
				case assertNullOrUndefined: {
					Object p0 = param(parameters, 0);
					if(RuntimeUtil.isNotNullOrUndefined(p0)) {
						JSRuntimeContext.get().dumpContext();
						Console.log(title()+"assertNullOrUndefined({0})",DebugUtil.jsLiteral(getEnvironment(),p0));
						throw RuntimeUtil.uncatchable(title()+"Assertion error, isNullOrUndefined()\nWas: {0}",DebugUtil.jsLiteral(getEnvironment(),p0));
					}
					return true;
				}
				case assertNotNullOrUndefined: {
					Object p0 = param(parameters, 0);
					if(RuntimeUtil.isNullOrUndefined(p0)) {
						JSRuntimeContext.get().dumpContext();
						Console.log(title()+"assertNullOrNotUndefined({0})",DebugUtil.jsLiteral(getEnvironment(),p0));
						throw RuntimeUtil.uncatchable(title()+"Assertion error, isNotNullOrUndefined()\nWas: {0}",DebugUtil.jsLiteral(getEnvironment(),p0));
					}
					return true;
				}
				case assertTrue: {
					Object p0 = param(parameters, 0);
					if(!RuntimeUtil.toBoolean(getEnvironment(), p0)) {
					//if(RuntimeUtil.ne(getEnvironment(), p0, Boolean.TRUE)) {
						JSRuntimeContext.get().dumpContext();
						Console.log(title()+"assertTrue({0})",DebugUtil.jsLiteral(getEnvironment(),p0));
						throw RuntimeUtil.uncatchable(title()+"Assertion error, assertTrue()\nWas: {0}",DebugUtil.jsLiteral(getEnvironment(),p0));
					}
					return true;
				}
				case assertFalse: {
					Object p0 = param(parameters, 0);
					//if(RuntimeUtil.ne(getEnvironment(), p0, Boolean.FALSE)) {
					if(RuntimeUtil.toBoolean(getEnvironment(), p0)) {
						JSRuntimeContext.get().dumpContext();
						Console.log(title()+"assertFalse({0})",DebugUtil.jsLiteral(getEnvironment(),p0));
						throw RuntimeUtil.uncatchable(title()+"Assertion error, assertFalse()\nWas: {0}",DebugUtil.jsLiteral(getEnvironment(),p0));
					}
					return true;
				}
				case assertEquals: {
					Object p0 = param(parameters, 0);
					Object p1 = param(parameters, 1);
					if(!equalsForAssert(getEnvironment(),p0, p1)) {
						JSRuntimeContext.get().dumpContext();
						Console.log(title()+"assertEquals({0},{1})",DebugUtil.jsLiteral(getEnvironment(),p0),DebugUtil.jsLiteral(getEnvironment(),p1));
						throw RuntimeUtil.uncatchable(title()+"Assertion error, assertEquals()\nExpected: {0}\nWas: {1}",DebugUtil.jsLiteral(getEnvironment(),p0),DebugUtil.jsLiteral(getEnvironment(),p1));
					}
					return true;
				}
				case assertEqualsStrict: {
					Object p0 = param(parameters, 0);
					Object p1 = param(parameters, 1);
					if(!equalsForAssertStrict(getEnvironment(),p0, p1)) {
						JSRuntimeContext.get().dumpContext();
						Console.log(title()+"assertEqualsStrict({0},{1})",DebugUtil.jsLiteral(getEnvironment(),p0),DebugUtil.jsLiteral(getEnvironment(),p1));
						throw RuntimeUtil.uncatchable(title()+"Assertion error, assertEqualsStrict()\nExpected: {0}\nWas: {1}",DebugUtil.jsLiteral(getEnvironment(),p0),DebugUtil.jsLiteral(getEnvironment(),p1));
					}
					return true;
				}
				case assertNotEquals: {
					Object p0 = param(parameters, 0);
					Object p1 = param(parameters, 1);
					if(equalsForAssert(getEnvironment(),p0, p1)) {
						JSRuntimeContext.get().dumpContext();
						Console.log(title()+"assertNotEquals({0},{1})",DebugUtil.jsLiteral(getEnvironment(),p0),DebugUtil.jsLiteral(getEnvironment(),p1));
						throw RuntimeUtil.uncatchable(title()+"Assertion error, assertNotEquals()\nExpected: {0}\nWas: {1}",DebugUtil.jsLiteral(getEnvironment(),p0),DebugUtil.jsLiteral(getEnvironment(),p1));
					}
					return true;
				}
				case assertNotEqualsStrict: {
					Object p0 = param(parameters, 0);
					Object p1 = param(parameters, 1);
					if(equalsForAssertStrict(getEnvironment(),p0, p1)) {
						JSRuntimeContext.get().dumpContext();
						Console.log(title()+"assertNotEqualsStrict({0},{1})",DebugUtil.jsLiteral(getEnvironment(),p0),DebugUtil.jsLiteral(getEnvironment(),p1));
						throw RuntimeUtil.uncatchable(title()+"Assertion error, assertNotEqualsStrict()\nExpected: {0}\nWas: {1}",DebugUtil.jsLiteral(getEnvironment(),p0),DebugUtil.jsLiteral(getEnvironment(),p1));
					}
					return true;
				}
				case assertArrayEquals: {
					JSArray a0 = RuntimeUtil.getArrayLike(getEnvironment(), param(parameters, 0));
					JSArray a1 = RuntimeUtil.getArrayLike(getEnvironment(), param(parameters, 1));
					return equalsJSArray(getEnvironment(), a0, a1, RuntimeUtil::eq);
				}
				case assertSame: {
					Object p0 = param(parameters, 0);
					Object p1 = param(parameters, 1);
					if(!RuntimeUtil.eqSameValue(getEnvironment(),p0,p1)) {
						JSRuntimeContext.get().dumpContext();
						Console.log(title()+"assertSame({0},{1})",DebugUtil.jsLiteral(getEnvironment(),p0),DebugUtil.jsLiteral(getEnvironment(),p1));
						throw RuntimeUtil.uncatchable(title()+"Assertion error, assertSame()\nExpected: {0}\nWas: {1}",DebugUtil.jsLiteral(getEnvironment(),p0),DebugUtil.jsLiteral(getEnvironment(),p1));
					}
					return true;
				}
				case assertNotSame: {
					Object p0 = param(parameters, 0);
					Object p1 = param(parameters, 1);
					if(RuntimeUtil.eqSameValue(getEnvironment(),p0,p1)) {
						JSRuntimeContext.get().dumpContext();
						Console.log(title()+"assertNotSame({0},{1})",DebugUtil.jsLiteral(getEnvironment(),p0),DebugUtil.jsLiteral(getEnvironment(),p1));
						throw RuntimeUtil.uncatchable(title()+"Assertion error, assertNotSame()\nExpected: {0}\nWas: {1}",DebugUtil.jsLiteral(getEnvironment(),p0),DebugUtil.jsLiteral(getEnvironment(),p1));
					}
					return true;
				}
				case assertParse: {
					String p0 = paramString(parameters, 0);
					try {
						getEnvironment().createScript(p0,"assertParse");
					} catch(Exception ex) {
						JSRuntimeContext.get().dumpContext();
						Console.log(title()+"assertParse({0})",DebugUtil.jsLiteral(getEnvironment(),p0));
						throw RuntimeUtil.uncatchable(title()+"Assertion error, assertParse()\nExpression is compiling with error(s).\n{0}",DebugUtil.truncate(p0));
					}
					return true;
				}
				case assertParseError: {
					String p0 = paramString(parameters, 0);
					try {
						getEnvironment().createScript(p0,"assertParseError");
						JSRuntimeContext.get().dumpContext();
						Console.log(title()+"assertParseError({0})",DebugUtil.jsLiteral(getEnvironment(),p0));
						throw RuntimeUtil.uncatchable(title()+"Assertion error, assertParseError()\nExpression is compiling without error.\n{0}",DebugUtil.truncate(p0));
					} catch(Exception ex) {
					}
					return true;
				}
				case assertDeclared: {
					String p0 = paramString(parameters, 0);
					boolean exists;
					if(parameters.length==2) {
						JSArray a = paramJsonArray(parameters, 1);
						exists = jsContains(a,p0);
					} else {
						exists = existsIgnoringTDZ(() -> JSRuntimeContext.get().getVariableValue(p0,RuntimeUtil.NOT_AVAILABLE));
					}
					if(!exists) {
						JSRuntimeContext.get().dumpContext();
						Console.log(title()+"assertDeclared({0})",DebugUtil.jsLiteral(getEnvironment(),p0));
						throw RuntimeUtil.uncatchable(title()+"Assertion error, assertDeclared()\nVariable {0} is not available.",p0);
					}
					return true;
				}
				case assertNotDeclared: {
					String p0 = paramString(parameters, 0);
					boolean exists;
					if(parameters.length==2) {
						JSArray a = paramJsonArray(parameters, 1);
						exists = jsContains(a,p0);
					} else {
						exists = existsIgnoringTDZ(() -> JSRuntimeContext.get().getVariableValue(p0,RuntimeUtil.NOT_AVAILABLE));
					}
					if(exists) {
						JSRuntimeContext.get().dumpContext();
						Console.log(title()+"assertNotDeclared({0})",DebugUtil.jsLiteral(getEnvironment(),p0));
						throw RuntimeUtil.uncatchable(title()+"Assertion error, assertNotDeclared()\nVariable {0} is available.",p0);
					}
					return true;
				}
				case assertDeclaredInScope: {
					String p0 = paramString(parameters, 0);
					boolean exists;
					if(parameters.length==2) {
						JSArray a = paramJsonArray(parameters, 1);
						exists = jsContains(a,p0);
					} else {
						VariableMap vars = JSRuntimeContext.get().getVariableMap();
						exists = vars!=null && isBoundValue(vars.getInScope(p0,RuntimeUtil.NOT_AVAILABLE));
						if(!exists) {
							exists = isBoundValue(JSRuntimeContext.get().resolveOwnIdentifierValue(p0,RuntimeUtil.NOT_AVAILABLE));
						}
					}
					if(!exists) {
						JSRuntimeContext.get().dumpContext();
						Console.log(title()+"assertDeclaredInScope({0})",DebugUtil.jsLiteral(getEnvironment(),p0));
						throw RuntimeUtil.uncatchable(title()+"Assertion error, assertDeclaredInScope()\nVariable {0} is not available in scope.",p0);
					}
					return true;
				}
				case assertNotDeclaredInScope: {
					String p0 = paramString(parameters, 0);
					boolean exists;
					if(parameters.length==2) {
						JSArray a = paramJsonArray(parameters, 1);
						exists = jsContains(a,p0);
					} else {
						VariableMap vars = JSRuntimeContext.get().getVariableMap();
						exists = vars!=null && isBoundValue(vars.getInScope(p0,RuntimeUtil.NOT_AVAILABLE));
						if(!exists) {
							exists = isBoundValue(JSRuntimeContext.get().resolveOwnIdentifierValue(p0,RuntimeUtil.NOT_AVAILABLE));
						}
					}
					if(exists) {
						JSRuntimeContext.get().dumpContext();
						Console.log(title()+"assertNotDeclaredInScope({0})",DebugUtil.jsLiteral(getEnvironment(),p0));
						throw RuntimeUtil.uncatchable(title()+"Assertion error, assertNotDeclaredInScope()\nVariable {0} is available in scope.",p0);
					}
					return true;
				}
				case assertThrows: {
					Callable function = null;
					Object type = RuntimeUtil.UNDEFINED;
					if(parameters.length>=2) {
						// Accept the parameters to be in reverse order to match Rhino test cases.
						// Uniquely is the second param is a constructor for a built-in error
						if(parameters[1] instanceof BaseStandardConstructor ctor && BaseError.class.isAssignableFrom(ctor.getNativeClass())) {
		                    function = paramCallable(parameters, 0, null);
							type = param(parameters, 1);
						} else {
							type = param(parameters, 0);
		                    function = paramCallable(parameters, 1, null);
						}
					} else {
						function = paramCallable(parameters, 0, null);
					}
                    if(function==null) {
    					throw RuntimeUtil.uncatchable(title()+"Assertion error, argument is not a callable");
                    }
                    try {
                    	function.call(null, RuntimeUtil.EMPTY_PARAMS);
                    } catch(Exception e) {
                        if(type!=RuntimeUtil.UNDEFINED) {
                        	Object ex = JSRuntimeException.exceptionObject(e);
                        	if(!RuntimeUtil.instanceOf(getEnvironment(), ex, type)) {
            					throw RuntimeUtil.uncatchable(e,title()+"Assertion error, invalid exception thrown {0} instead of {1}",
            							ex!=null?ex.getClass():"null",
            							type.getClass());
                        	}
                        }
                    	// normal...
						return true;
                    }
					throw RuntimeUtil.uncatchable(title()+"Assertion error, no exception thrown");
				}

				case jsonDeepClone: {
					Object o = param(parameters, 0);
					if(o instanceof JsonContainer c) {
						return c.deepClone();
					}
					return o;
				}

				case _introspect: {
					// Also useful to bput a breakpoint in a javacode amd look at the parameters
					for(int i=0; i<parameters.length; i++) {
						Console.log("#{0}: {1}",i,ConsoleToString.toString(getEnvironment(), parameters[i]));
					}
					return RuntimeUtil.UNDEFINED;
				}

				case SPARSE: {
					JSArray a = JSArray.create(getEnvironment());
					for(int i=0; i<parameters.length; i++) {
						Object o = parameters[i];
						if(o instanceof SparseEmpty e) {
							a.arrayAddLength(e.size);
						} else {
							a.arrayAdd(o);
						}
					}
					return a;
				}

				case EMPTY: {
					long size = paramLong(parameters, 0);
					return new SparseEmpty(size);
				}

				case _n: {
					String s = RuntimeUtil.toString(getEnvironment(), param(parameters, 0));
					return StringUtil.normalizeLineBreaks(s);
				}
				case _strictMode: {
					return RuntimeUtil.isStrictMode();
				}

				case loadTextResource: {
					String resourceName = paramString(parameters, 0);					
					return loadText( resourceName);
				}
				case loadJsonResource: {
					String resourceName = paramString(parameters, 0);					
					return getEnvironment().getJsonFactory().parse(loadText(resourceName));
				}
				default: {
				    throw new IllegalStateException(); // Should never be here 
				}
			}
		}
		
		private static boolean jsContains(JSArray a, Object o) {
			int sz = (int)a.arrayLength();
			for(int i=0; i<sz; i++) {
				if(a.getProperty(i).equals(o)) {
					return true;
				}
			}
			return false;
		}
	
		private final String title() {
        	StringBuilder b = new StringBuilder();
			String suite = (String)JSRuntimeContext.get().getGlobalContext().getProperty(PROP_SUITETITLE);
			String title = (String)JSRuntimeContext.get().getGlobalContext().getProperty(PROP_TESTTITLE);
	        if(StringUtil.isNotEmpty(suite) || StringUtil.isNotEmpty(title)) {
	        	b.append("[");
	            if(StringUtil.isNotEmpty(suite)) {
	            	b.append(suite);
	            }
	            if(StringUtil.isNotEmpty(title)) {
	            	b.append("::");
	            	b.append(suite);
	            }
	            b.append("]");
	        }
	        return b.toString();
		}
		
		private boolean equalsForAssert(JSEnvironment env, Object o1, Object o2) {
			return equals(env, o1, o2, RuntimeUtil::eq);
		}
		private boolean equalsForAssertStrict(JSEnvironment env, Object o1, Object o2) {
			return equals(env, o1, o2, RuntimeUtil::eqStrict);
		}
		
		private boolean equals(JSEnvironment env, Object o1, Object o2, TriPredicate<JSEnvironment,Object,Object> eqFunction) {
			if(o1==o2) {
				return true;
			}
			// Handle the fact the array can be sparse and recursively compare the properties using the same equals op
			if(o1 instanceof JSArray || o2 instanceof JSArray) {
				if(o1 instanceof JSArray a1 && o2 instanceof JSArray a2) {
					return equalsJSArray(env, a1, a2, eqFunction);
				}
				return false;
			}
			if(o1 instanceof JSObject || o2 instanceof JSObject) {
				if(o1 instanceof JSObject m1 && o2 instanceof JSObject m2) {
					return equalsJSObject(env, m1, m2, eqFunction);
				}
				return false;
			}
			if(o1 instanceof Map<?,?> || o2 instanceof Map<?,?>) {
				if(o1 instanceof Map<?,?> m1 && o2 instanceof Map<?,?> m2) {
					return equalsMap(env, m1, m2, eqFunction);
				}
				return false;
			}
			if(o1 instanceof List<?> || o2 instanceof List<?>) {
				if(o1 instanceof List<?> l1 && o2 instanceof List<?> l2) {
					return equalsList(env, l1, l2, eqFunction);
				}
				return false;
			}
			if(isNaN(o1)) {
				return isNaN(o2);
			}
			return eqFunction.test(env, o1, o2);
		}
		private boolean equalsJSObject(JSEnvironment env, @NonNull JSObject o1, @NonNull JSObject o2, TriPredicate<JSEnvironment,Object,Object> eqFunction) {
//			if(o1.getPrototype()!=o2.getPrototype()) {
//				return false;
//			}
			// Regular values
			for(Iterator<String> it=o1.ownPropertyKeys(false); it.hasNext(); ) {
				String k = it.next();
				if(!o2.hasProperty(k)) {
					return false;
				}
				if(!equals(env,o1.getProperty(k),o2.getProperty(k),eqFunction)) {
					return false;
				}
			}
			// Symbol values
			for(Iterator<Symbol> it=o1.ownPropertySymbols(false); it.hasNext(); ) {
				Symbol k = it.next();
				if(!o2.hasProperty(k)) {
					return false;
				}
				if(!equals(env,o1.getProperty(k),o2.getProperty(k),eqFunction)) {
					return false;
				}
			}
			return true;
		}
		private boolean equalsMap(JSEnvironment env, @NonNull Map<?,?> m1, @NonNull Map<?,?> m2, TriPredicate<JSEnvironment,Object,Object> eqFunction) {
			if(m1.size()!=m2.size()) {
				return false;
			}
			for(Map.Entry<?,?> e: m1.entrySet()) {
				if(!m2.containsKey(e.getKey())) {
					return false;
				}
				if(!equals(env, e.getValue(), m2.get(e.getKey()),eqFunction)) {
					return false;
				}
			}
			return true;
		}
		private boolean equalsJSArray(JSEnvironment relm, @NonNull JSArray a1, @NonNull JSArray a2, TriPredicate<JSEnvironment,Object,Object> eqFunction) {
//			if(a1.getPrototype(env)!=a2.getPrototype(env)) {
//				return false;
//			}
			if(a1.arrayLength()!=a2.arrayLength()) {
				return false;
			}
			
			long len = a1.arrayLength();
			for(long i=0; i<len; i++) {
				if(a1.arrayHas(i)) {
					if(!a2.arrayHas(i)) {
						return false;
					}
				} else {
					if(a2.arrayHas(i)) {
						return false;
					}
				}
				Object v1 = a1.getProperty(i);
				Object v2 = a2.getProperty(i);
				if(!equals(relm, v1, v2, eqFunction)) {
					return false;
				}
			}
			
			// Should check the array properties as well
//			JSObject o1 = a1.getMembers(false);
//			JSObject o2 = a2.getMembers(false);
//			return equals(context,o1,o2,eqFunction);
			return true;
		}
		private boolean equalsList(JSEnvironment env, @NonNull List<?> l1, @NonNull List<?> l2, TriPredicate<JSEnvironment,Object,Object> eqFunction) {
			if(l1.size()!=l2.size()) {
				return false;
			}
			int size = l1.size();
			for( int i=0; i<size; i++) {
				Object v1 = l1.get(i);
				Object v2 = l2.get(i);
				if(!eqFunction.test(env, v1, v2)) {
					return false;
				}
			}
			return true;
		}
		private boolean isNaN(Object n) {
			if(n instanceof Double d) {
				return Double.isNaN(d);
			}
			if(n instanceof Float f) {
				return Float.isNaN(f);
			}
			return false;
		}
		
		private String loadText(String resourceName) {
			try {
				InputStream is = JSEnvironment.getEnvironment().getClassLoader().getResourceAsStream(resourceName);
				try {
					return readString(is,StandardCharsets.UTF_8);
				} finally {
					is.close();
				}
			} catch(Exception e) {
				throw RuntimeUtil.error(e,"Error while loading resource '{0}'",resourceName);
			}
		}
		private String readString(InputStream is, Charset encoding) throws Exception {
			StringBuilder sb = new StringBuilder(8192);
			byte[] b = new byte[8192];
			int c;
			while( (c=is.read(b)) >=0 ) {
				String s = new String(b, 0, c, encoding);
				sb.append(s);
			}
			return sb.toString();
		}
	}
}