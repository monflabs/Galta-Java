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

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext.VAR_TYPE;
import org.monflabs.galtajs.vb.ValueBinding;
import org.monflabs.galtajs.vb.impl.GaltaJSValueBinding;
import org.monflabs.galtajs.vb.impl.IdentityValueBinding;

import tests.TestEnvironment;

public class ConstantTest extends ValueBindingTestCase {

	
	public void testIdentityExpression() throws Exception {
		JSEnvironment env = TestEnvironment.create();

		ValueBinding e1 = new IdentityValueBinding("abc");
		assertTrue(e1.isConstant());
		assertEquals("abc",e1.evaluate(new InterpretedGlobalRuntimeContext(env,env.createProgramExecutor())));

		ValueBinding e2 = new GaltaJSValueBinding(env,"1+2");
		assertTrue(e2.isConstant());
		assertEquals(3,e2.evaluate(new InterpretedGlobalRuntimeContext(env,env.createProgramExecutor())));
	}
	
	public void testNonIdentityExpression() throws Exception {
		JSEnvironment env = TestEnvironment.create();
		
		ValueBinding e = new GaltaJSValueBinding(env,"abc");
		assertFalse(e.isConstant());
		InterpretedGlobalRuntimeContext ctx = new InterpretedGlobalRuntimeContext(env,env.createProgramExecutor());
		ctx.createVariable("abc",345,VAR_TYPE.CONST);
		assertEquals(345,e.evaluate(ctx));
	}
}
