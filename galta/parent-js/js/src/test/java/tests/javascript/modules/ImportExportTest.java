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
package tests.javascript.modules;

import static org.junit.Assert.assertThrows;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSException;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.modules.JSMemoryModuleResolver;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;

import tests.javascript.JavaScriptStrictTestCase;

/**
 * @author Philippe Riand
 */
public class ImportExportTest extends JavaScriptStrictTestCase {
	
	public void testNamedExport() {
		JSMemoryModuleResolver modules = new JSMemoryModuleResolver()
				.put("mod1","const a=1; export {a}")
				.put("mod2","const a=1, b=2; export {a,b}")
				.put("mod3","export const a=1, b=2;")
				.put("mod4","export function f() { return 4; };")

				.put("mod5","function f() { return 3; }")
		;
		
		JSEnvironment env = createEnvironment( (b) -> {
			b.addModuleResolver(modules);
		}).build();
		
		evaluate(env, "import {a} from 'mod1'; assertEquals(1,a); assertEquals('undefined', typeof b)");
		evaluate(env, "import {a as AA} from 'mod1'; assertEquals(1,AA); assertEquals('undefined', typeof a)");
		evaluate(env, "import {a} from 'mod2'; assertEquals(1,a); assertEquals('undefined', typeof b)");
		evaluate(env, "import {a,b} from 'mod2'; assertEquals(1,a); assertEquals(b,2)");
		evaluate(env, "import {a,b} from 'mod3'; assertEquals(1,a); assertEquals(b,2)");
		evaluate(env, "import {f} from 'mod4'; assertEquals(4,f());");
		evaluate(env, "import {a as b,b as a} from 'mod2'; assertEquals(2,a); assertEquals(b,1)");

		assertThrows( JSException.class, () -> evaluate(env,"import a from 'mod1';"));
		assertThrows( JSException.class, () -> evaluate(env,"import {c} from 'mod1';"));
		assertThrows( JSException.class, () -> evaluate(env,"import {f} from 'mod5';"));
	}	
	
	public void testDefaultExport() {
		JSMemoryModuleResolver modules = new JSMemoryModuleResolver()
				.put("mod1","export default 1")
				.put("mod2","function f() { return 3; }; export default f;")
				.put("mod3","export default function f() { return 3; }")
				// We do console.log() to ensure that the proper global context is used
				.put("mod4","function f() { console.log('f()'); return 3}; export default function g() { console.log('g()'); return f(); }")
				.put("mod5","function f() { return 3; }")
		;
		
		JSEnvironment env = createEnvironment( (b) -> {
			b.addModuleResolver(modules);
		}).build();
		
		evaluate(env, "import a from 'mod1'; assertEquals(1,a); assertEquals('undefined', typeof b)");
		evaluate(env, "import b from 'mod1'; assertEquals(1,b); assertEquals('undefined', typeof a)");
		evaluate(env, "import ff from 'mod2'; ff()");
		evaluate(env, "import g from 'mod3'; g()");
		evaluate(env, "import g from 'mod4'; g()");
		
		assertThrows( JSException.class, () -> evaluate(env,"import g from 'mod5';"));
		assertThrows( JSException.class, () -> evaluate(env,"import {g} from 'mod3';"));
	}	
	
	private Object evaluate(JSEnvironment env, String text) {
		InterpretedGlobalRuntimeContext ctx = new InterpretedGlobalRuntimeContext(env,env.createProgramExecutor());
		JSInterpretedUnit expr = ctx.getEnvironment().createScript(text,"ImportTest");
		Object result = expr.executeWithContext(ctx);
		return result;
	}
}
