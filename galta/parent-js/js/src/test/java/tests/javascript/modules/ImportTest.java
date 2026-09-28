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

import java.util.stream.Stream;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSException;
import org.monflabs.galtajs.JSModule;
import org.monflabs.galtajs.JSModuleDescriptor;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.modules.AbstractModuleResolver;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.modules.JSNativeModule;
import org.monflabs.galtajs.modules.NativeModuleDescriptor;
import org.monflabs.galtajs.modules.NativeModuleResolver;
import org.monflabs.galtajs.rt.JSGlobalContext;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;

import tests.javascript.JavaScriptStrictTestCase;


/**
 * @author Philippe Riand
 * 
 * FIXME: test this with the compiler as well!
 */
public class ImportTest extends JavaScriptStrictTestCase {
	
	static abstract class TestModuleResolver extends NativeModuleResolver {
		TestModuleResolver() {
		}
		@Override
		protected JSModuleDescriptor findModule(String name) {
			return new NativeModuleDescriptor(name) {
				@Override
				public JSModule loadModule(JSGlobalContext globalContext) {
					return TestModuleResolver.this.loadModule(globalContext, this);
				}
			};
		}
		@Override
		public Stream<JSModuleDescriptor> getModules() {
			return Stream.empty();
		}	
		public abstract JSModule loadModule(JSGlobalContext globalContext, NativeModuleDescriptor d);
	}

	
	public void testImportParser() throws Exception {
		// See: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Statements/import
		compileStatement("import defaultExport from \"module-name\"");
		compileStatement("import * as name from \"module-name\"");
		compileStatement("import { export1 } from \"module-name\"");
		compileStatement("import { export1 as alias1 } from \"module-name\"");
		compileStatement("import { default as alias } from \"module-name\"");
		compileStatement("import { export1, export2 } from \"module-name\"");
		compileStatement("import { export1, export2 as alias2 } from \"module-name\"");
		compileStatement("import { \"string name\" as alias } from \"module-name\"");
		compileStatement("import defaultExport, { export1, export2 } from \"module-name\"");
		compileStatement("import defaultExport, * as name from \"module-name\"");
		compileStatement("import \"module-name\"");

		// Others
		compileStatement("import \"imp\"");
		compileStatement("import def from \"imp\"");
		compileStatement("import {a,b} from \"imp\"");
		compileStatement("import {a as A,b as B} from \"imp\"");
		compileStatement("import def, {a,b} from \"imp\"");
		compileStatement("import def, {a as A,b as B} from \"imp\"");
		compileStatement("import * as aa from \"imp\"");
		// NamedImports may be EMPTY (`import {}` - side-effect-only import
		// with an explicit-but-empty clause) and may carry a trailing comma
		// after the last entry, regardless of whether that entry itself has
		// an "as" alias - both valid per spec, and both exercised directly
		// by test262 (module-code/eval-rqstd-once.js, .../
		// instn-named-bndng-trlng-comma.js) - previously wrongly rejected here.
		compileStatement("import {} from \"imp\"");
		compileStatement("import {a as a,} from \"imp\"");

		assertThrows( JSException.class, () -> compileStatement("import * from \"imp\"") );
		assertThrows( JSException.class, () -> compileStatement("import * aa from \"imp\"") );
		assertThrows( JSException.class, () -> compileStatement("import de def from \"imp\"") );
		assertThrows( JSException.class, () -> compileStatement("import {a as} from \"imp\"") );

		assertThrows( JSException.class, () -> compileStatement("{import \"imp\"}") );
	}		
	private void compileStatement(String text) {
		getEnvironment().createScript(text,"ImportTest");
	}

	public void testImportEnvironmentRuntime() throws Exception {
		AbstractModuleResolver map = new TestModuleResolver() {
			@Override
			public JSModule loadModule(JSGlobalContext globalContext, NativeModuleDescriptor d) {
				JSEnvironment env = globalContext.getEnvironment();
				return switch(d.getName()) {
					case "A" -> new JSNativeModule( env, d, JSObject.of(env, "a",1), null); 
					case "B" -> new JSNativeModule( env, d, JSObject.of(env, "b",2), null); 
					case "C" -> new JSNativeModule( env, d, JSObject.of(env, "c",3), JSObject.of(env, "v1",11,"v2",12)); 
					default ->	null;
				};
			}
		};
		
		// Test a module resolver added to the environment
		JSEnvironment env = createEnvironment( (b) -> {
			b.addModuleResolver(map);
		}).build();
		
		evaluate(env, "import d from 'A'; assertEquals(1,d.a)");
		evaluate(env, "import d from 'B'; assertEquals(2,d.b)");
		evaluate(env, "import {v1} from 'C'; assertEquals(11,v1);");
		evaluate(env, "import {v1} from 'C'; assertEquals(11,v1); assertEquals('undefined',typeof v2)");
		evaluate(env, "import {v1,v2} from 'C'; assertEquals(11,v1); assertEquals(12,v2)");
		evaluate(env, "import {v1 as x1} from 'C'; assertEquals(11,x1);");
		evaluate(env, "import {v2 as x2, v1 as x1} from 'C'; assertEquals(11,x1); assertEquals(12,x2);");

		evaluate(env, "import * as T from 'C'; assertEquals(11,T.v1); assertEquals(12,T.v2);");

		assertThrows( JSException.class, () -> evaluate(env, "import {v3 as x1} from 'C';" ));
	}	
	public void testImportContextRuntime() throws Exception {
		AbstractModuleResolver moduleResolver = new TestModuleResolver() {
			@Override
			public JSModule loadModule(JSGlobalContext globalContext, NativeModuleDescriptor d) {
				JSEnvironment env = globalContext.getEnvironment();
				return switch(d.getName()) {
					case "A" -> new JSNativeModule( env, d, JSObject.of(env, "a",1), null); 
					case "B" -> new JSNativeModule( env, d, JSObject.of(env, "b",2), null); 
					case "C" -> new JSNativeModule( env, d, JSObject.of(env, "c",3), JSObject.of(env, "v1",11,"v2",12)); 
					default ->	null;
				};
			}
		};
		
		// Test a module resolver added to the context
		JSEnvironment env = createEnvironment( (b) -> {
			b.addModuleResolver(moduleResolver);
		}).build();
		
		evaluate(env, "import d from 'A'; assertEquals(1,d.a)");
		evaluate(env, "import d from 'B'; assertEquals(2,d.b)");
		assertThrows( JSException.class, () -> evaluate(env, "import {v3 as x1} from 'C';" ));
	}	
	private Object evaluate(JSEnvironment env, String text) {
		InterpretedGlobalRuntimeContext ctx = new InterpretedGlobalRuntimeContext(env,env.createProgramExecutor());
		JSInterpretedUnit expr = ctx.getEnvironment().createScript(text,"ImportTest");
		Object result = expr.executeWithContext(ctx);
		return result;
	}
}
