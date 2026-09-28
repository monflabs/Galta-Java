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
package tests.javascript.util;


import static org.junit.Assert.assertThrows;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.monflabs.galtajs.JSException;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.jsonfactory.JSValue;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;

import tests.javascript.JavaScriptStrictTestCase;

public class JSValueTest extends JavaScriptStrictTestCase {
	
	public void testScriptWithContext() throws Exception {
		ExecutionResult r = execute();

		JSValue ac = JSValue.of(getEnvironment(),r.context().getVariableValue("aC",RuntimeUtil.UNDEFINED));
		assertEquals( "A", ac.get("va").value() );
		assertEquals( "C", ac.getOrDefault("fake", "C").value() );
		assertEquals( "A", ac.get("a").value() );
		assertEquals( "A", ac.get("a").value() );

		JSValue acc = JSValue.of(getEnvironment(),r.context().getVariableValue("aCC",RuntimeUtil.UNDEFINED));
		assertEquals( "A", acc.get("vb","va").value() );
		assertEquals( "A", acc.get("b","a").value() );
	}

	
	//
	// Access values
	//
	public void testNull() throws Exception {
		InterpretedGlobalRuntimeContext ctx = new InterpretedGlobalRuntimeContext(getEnvironment(),getEnvironment().createProgramExecutor(),null);
		JSValue v = ctx.value(null);

		assertEquals( true, v.isNull());
		assertEquals( false, v.isUndefined());
		assertEquals( true, v.isNullOrUndefined());
		assertEquals( false, v.isBoolean());
		assertEquals( false, v.isNumber());
		assertEquals( false, v.isString());
		assertEquals( false, v.isObject());
		assertEquals( false, v.isArray());

		assertThrows( JSException.class, () -> v.booleanValue());
		assertThrows( JSException.class, () -> v.numberValue());
		assertThrows( JSException.class, () -> v.intValue());
		assertThrows( JSException.class, () -> v.longValue());
		assertThrows( JSException.class, () -> v.doubleValue());
		assertThrows( JSException.class, () -> v.bigIntValue());
		assertThrows( JSException.class, () -> v.bigDecimalValue());
		assertThrows( JSException.class, () -> v.stringValue());
		assertThrows( JSException.class, () -> v.objectValue());
		assertThrows( JSException.class, () -> v.arrayValue());
	}
	public void testUndefined() throws Exception {
		InterpretedGlobalRuntimeContext ctx = new InterpretedGlobalRuntimeContext(getEnvironment(),getEnvironment().createProgramExecutor(),null);
		JSValue v = ctx.value(RuntimeUtil.UNDEFINED);

		assertEquals( false, v.isNull());
		assertEquals( true, v.isUndefined());
		assertEquals( true, v.isNullOrUndefined());
		assertEquals( false, v.isBoolean());
		assertEquals( false, v.isNumber());
		assertEquals( false, v.isString());
		assertEquals( false, v.isObject());
		assertEquals( false, v.isArray());

		assertThrows( JSException.class, () -> v.booleanValue());
		assertThrows( JSException.class, () -> v.numberValue());
		assertThrows( JSException.class, () -> v.intValue());
		assertThrows( JSException.class, () -> v.longValue());
		assertThrows( JSException.class, () -> v.doubleValue());
		assertThrows( JSException.class, () -> v.bigIntValue());
		assertThrows( JSException.class, () -> v.bigDecimalValue());
		assertThrows( JSException.class, () -> v.stringValue());
		assertThrows( JSException.class, () -> v.objectValue());
		assertThrows( JSException.class, () -> v.arrayValue());
	}
	public void testBoolean() throws Exception {
		InterpretedGlobalRuntimeContext ctx = new InterpretedGlobalRuntimeContext(getEnvironment(),getEnvironment().createProgramExecutor(),null);
		JSValue v = ctx.value(true);

		assertEquals( false, v.isNull());
		assertEquals( false, v.isUndefined());
		assertEquals( false, v.isNullOrUndefined());
		assertEquals( true, v.isBoolean());
		assertEquals( false, v.isNumber());
		assertEquals( false, v.isString());
		assertEquals( false, v.isObject());
		assertEquals( false, v.isArray());

		assertEquals( true, v.booleanValue());
		assertThrows( JSException.class, () -> v.numberValue());
		assertThrows( JSException.class, () -> v.intValue());
		assertThrows( JSException.class, () -> v.longValue());
		assertThrows( JSException.class, () -> v.doubleValue());
		assertThrows( JSException.class, () -> v.bigIntValue());
		assertThrows( JSException.class, () -> v.bigDecimalValue());
		assertThrows( JSException.class, () -> v.stringValue());
		assertThrows( JSException.class, () -> v.objectValue());
		assertThrows( JSException.class, () -> v.arrayValue());
	}
	public void testNumber() throws Exception {
		InterpretedGlobalRuntimeContext ctx = new InterpretedGlobalRuntimeContext(getEnvironment(),getEnvironment().createProgramExecutor(),null);
		JSValue v = ctx.value(1);

		assertEquals( false, v.isNull());
		assertEquals( false, v.isUndefined());
		assertEquals( false, v.isNullOrUndefined());
		assertEquals( false, v.isBoolean());
		assertEquals( true, v.isNumber());
		assertEquals( false, v.isString());
		assertEquals( false, v.isObject());
		assertEquals( false, v.isArray());

		assertThrows( JSException.class, () -> v.booleanValue());
		assertEquals( 1 , v.numberValue());
		assertEquals( 1, v.intValue());
		assertEquals( 1L, v.longValue());
		assertEquals( 1.0, v.doubleValue());
		assertEquals( BigInteger.ONE, v.bigIntValue());
		assertEquals( BigDecimal.ONE, v.bigDecimalValue());
		assertThrows( JSException.class, () -> v.stringValue());
		assertThrows( JSException.class, () -> v.objectValue());
		assertThrows( JSException.class, () -> v.arrayValue());
	}
	public void testString() throws Exception {
		InterpretedGlobalRuntimeContext ctx = new InterpretedGlobalRuntimeContext(getEnvironment(),getEnvironment().createProgramExecutor(),null);
		JSValue v = ctx.value("abc");

		assertEquals( false, v.isNull());
		assertEquals( false, v.isUndefined());
		assertEquals( false, v.isNullOrUndefined());
		assertEquals( false, v.isBoolean());
		assertEquals( false, v.isNumber());
		assertEquals( true, v.isString());
		assertEquals( false, v.isObject());
		assertEquals( false, v.isArray());

		assertThrows( JSException.class, () -> v.booleanValue());
		assertThrows( JSException.class, () -> v.numberValue());
		assertThrows( JSException.class, () -> v.intValue());
		assertThrows( JSException.class, () -> v.longValue());
		assertThrows( JSException.class, () -> v.doubleValue());
		assertThrows( JSException.class, () -> v.bigIntValue());
		assertThrows( JSException.class, () -> v.bigDecimalValue());
		assertEquals( "abc", v.stringValue());
		assertThrows( JSException.class, () -> v.objectValue());
		assertThrows( JSException.class, () -> v.arrayValue());
	}
	public void testObject() throws Exception {
		InterpretedGlobalRuntimeContext ctx = new InterpretedGlobalRuntimeContext(getEnvironment(),getEnvironment().createProgramExecutor(),null);
		JSValue v = ctx.value(JSObject.create(getEnvironment()));

		assertEquals( false, v.isNull());
		assertEquals( false, v.isUndefined());
		assertEquals( false, v.isNullOrUndefined());
		assertEquals( false, v.isBoolean());
		assertEquals( false, v.isNumber());
		assertEquals( false, v.isString());
		assertEquals( true, v.isObject());
		assertEquals( false, v.isArray());

		assertThrows( JSException.class, () -> v.booleanValue());
		assertThrows( JSException.class, () -> v.numberValue());
		assertThrows( JSException.class, () -> v.intValue());
		assertThrows( JSException.class, () -> v.longValue());
		assertThrows( JSException.class, () -> v.doubleValue());
		assertThrows( JSException.class, () -> v.bigIntValue());
		assertThrows( JSException.class, () -> v.bigDecimalValue());
		assertThrows( JSException.class, () -> v.stringValue());
		assertEquals( JSObject.create(getEnvironment()), v.objectValue());
		assertThrows( JSException.class, () -> v.arrayValue());
	}
	public void testArray() throws Exception {
		InterpretedGlobalRuntimeContext ctx = new InterpretedGlobalRuntimeContext(getEnvironment(),getEnvironment().createProgramExecutor(),null);
		JSValue v = ctx.value(JSArray.create(getEnvironment()));

		assertEquals( false, v.isNull());
		assertEquals( false, v.isUndefined());
		assertEquals( false, v.isNullOrUndefined());
		assertEquals( false, v.isBoolean());
		assertEquals( false, v.isNumber());
		assertEquals( false, v.isString());
		assertEquals( false, v.isObject());
		assertEquals( true, v.isArray());

		assertThrows( JSException.class, () -> v.booleanValue());
		assertThrows( JSException.class, () -> v.numberValue());
		assertThrows( JSException.class, () -> v.intValue());
		assertThrows( JSException.class, () -> v.longValue());
		assertThrows( JSException.class, () -> v.doubleValue());
		assertThrows( JSException.class, () -> v.bigIntValue());
		assertThrows( JSException.class, () -> v.bigDecimalValue());
		assertThrows( JSException.class, () -> v.stringValue());
		assertThrows( JSException.class, () -> v.objectValue());
		assertEquals( JSArray.create(getEnvironment()), v.arrayValue());
	}

}
