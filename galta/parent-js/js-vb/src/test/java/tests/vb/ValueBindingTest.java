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
package tests.vb;

import org.monflabs.galtajs.JSException;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;
import org.monflabs.galtajs.vb.ValueBinding;
import org.monflabs.galtajs.vb.ValueBindingFactory;
import org.monflabs.galtajs.vb.impl.IdentityValueBinding;

import tests.TestEnvironment;

public class ValueBindingTest extends ValueBindingTestCase {
	
	class TestExpression extends ValueBinding {
		TestExpression(String expression) {
			super(expression);
		}
		@Override
		public boolean isConstant() {
			return true;
		}
		@Override
		public Object evaluate(InterpretedGlobalRuntimeContext context) {
			return "<<<"+getExpression()+">>>";
		}
	}

	class TestFactory extends ValueBindingFactory {
		TestFactory() {
			super(TestEnvironment.create(),"${", "}");
		}
		@Override
		public ValueBinding createExpression(String expression) {
			return new TestExpression(expression);
		}
	}
	
	class TestFactory2 extends ValueBindingFactory {
		TestFactory2() {
			super(TestEnvironment.create(),"{{", "}}");
		}
		@Override
		public ValueBinding createExpression(String expression) {
			return new TestExpression(expression);
		}
	}
	
	public void testMultipartValueBinding() throws Exception {
		TestFactory f = new TestFactory();
		
		assertFalse(f.isValueBinding(""));
		assertFalse(f.isValueBinding("xyz"));
		assertTrue(f.isValueBinding("${xyz"));
		assertFalse(f.isValueBinding("xyz}"));
		assertTrue(f.isValueBinding("${xyz}"));
		assertTrue(f.isValueBinding("  ${xyz}  "));
		assertTrue(f.isValueBinding("  ${xyz} abc "));
		assertTrue(f.isValueBinding("  ${xyz} abc ${uvr}"));
		assertTrue(f.isValueBinding("  ${xyz} abc ${uvr} efg"));
		
		// Simple expressions
		checkExpression(f,
				"",
				"",IdentityValueBinding.class);
		checkExpression(f,
				"xyz",
				"xyz",IdentityValueBinding.class);
		checkExpression(f,
				"xyz}",
				"xyz}",IdentityValueBinding.class);
		checkExpression(f,
				"${xyz}",
				"<<<xyz>>>",TestExpression.class);
		checkExpression(f,
				"  ${xyz}  ",
				"  <<<xyz>>>  ",ValueBindingFactory.MultiPartScriptExpression.class);
		checkExpression(f,
				"  ${xyz} abc ",
				"  <<<xyz>>> abc ",ValueBindingFactory.MultiPartScriptExpression.class);
		checkExpression(f,
				"  ${xyz} abc ${uvr}",
				"  <<<xyz>>> abc <<<uvr>>>",ValueBindingFactory.MultiPartScriptExpression.class);
		checkExpression(f,
				"  ${xyz} abc ${uvr} efg",
				"  <<<xyz>>> abc <<<uvr>>> efg",ValueBindingFactory.MultiPartScriptExpression.class);
		
		// Expressions with quotes
		checkExpression(f,
				"${a''b\"\"c}",
				"<<<a''b\"\"c>>>",TestExpression.class);
		checkExpression(f,
				"${a'\"'b}",
		  		"<<<a'\"'b>>>",TestExpression.class);
		checkExpression(f,
				"${a'}'b}",
		  		"<<<a'}'b>>>",TestExpression.class);
		checkExpression(f,
				"${a\"}\"b}",
		  		"<<<a\"}\"b>>>",TestExpression.class);
		
		// Expressions with blocks
		checkExpression(f,
				"${a{b}c}",
				"<<<a{b}c>>>",TestExpression.class);
		checkExpression(f,
				"${a{b{c}d}}",
				"<<<a{b{c}d}>>>",TestExpression.class);
		checkExpression(f,
				"${a'{b'c}",
				"<<<a'{b'c>>>",TestExpression.class);
		
		
		// Failing expressions
		try {
			assertTrue(f.createValueBinding("${xyz") instanceof IdentityValueBinding);
			fail();
		} catch(JSException e) {}
		try {
			assertTrue(f.createValueBinding("${'aaa}") instanceof IdentityValueBinding);
			fail();
		} catch(JSException e) {}
		try {
			assertTrue(f.createValueBinding("${'aaa}") instanceof IdentityValueBinding);
			fail();
		} catch(JSException e) {}
		try {
			assertTrue(f.createValueBinding("${'aaa}") instanceof IdentityValueBinding);
			fail();
		} catch(JSException e) {}
		try {
			assertTrue(f.createValueBinding("${\"aaa}") instanceof IdentityValueBinding);
			fail();
		} catch(JSException e) {}
		try {
			assertTrue(f.createValueBinding("${ab{cd}") instanceof IdentityValueBinding);
			fail();
		} catch(JSException e) {}
		try {
			assertTrue(f.createValueBinding("${ab{c{d}}") instanceof IdentityValueBinding);
			fail();
		} catch(JSException e) {}
		try {
			assertTrue(f.createValueBinding("${ab{'}c'd}") instanceof IdentityValueBinding);
			fail();
		} catch(JSException e) {}
	}
	
	public void testMulticharBinding() throws Exception {
		TestFactory2 f = new TestFactory2();
		checkExpression(f,
				"",
				"",IdentityValueBinding.class);
		checkExpression(f,
				"xyz",
				"xyz",IdentityValueBinding.class);
		checkExpression(f,
				"xyz}",
				"xyz}",IdentityValueBinding.class);
		checkExpression(f,
				"{{xyz}}",
				"<<<xyz>>>",TestExpression.class);
		checkExpression(f,
				"{{x{}z}}",
				"<<<x{}z>>>",TestExpression.class);
		checkExpression(f,
				"{{x{{}}z}}",
				"<<<x{{}}z>>>",TestExpression.class);
		checkExpression(f,
				"{{${expr}}}",
				"<<<${expr}>>>",TestExpression.class);
		checkExpression(f,
				"  {{xyz}}  ",
				"  <<<xyz>>>  ",ValueBindingFactory.MultiPartScriptExpression.class);
		checkExpression(f,
				"  {{xyz}} abc ",
				"  <<<xyz>>> abc ",ValueBindingFactory.MultiPartScriptExpression.class);
	}
	
	private void checkExpression(ValueBindingFactory f, String expr, String expected, Class<? extends ValueBinding> clazz) {
		ValueBinding sc = f.createValueBinding(expr);
		assertTrue(clazz.isAssignableFrom(sc.getClass()));
		String result = (String)sc.evaluate(null);
		assertEquals(expected,result);
	}
}

