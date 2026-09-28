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
package tests.javascript.regression;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.library.GlobalLibrary;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.standard.global.StandardObjects;

import tests.javascript.JavaScriptStrictTestCase;

/**
 * console formatting: %s with a Symbol, %d with NaN/Infinity, timeEnd()
 * removing its timer. Output is captured through captureOutput().
 */
public class ConsoleFormatTest extends JavaScriptStrictTestCase {

	public void testFormat() throws Exception {
		executeCode("""
			captureOutput();
			console.log('%s!', Symbol('x'));
			assertEquals('Symbol(x)!', captureOutput().trim());
			console.log('%d/%d/%i', NaN, Infinity, 7.9);
			assertEquals('NaN/Infinity/7', captureOutput().trim());
			console.log('%f', Symbol('y'));
			assertEquals('NaN', captureOutput().trim());
			console.time('t'); console.timeEnd('t');
			captureOutput();
			console.time('t');
			assertEquals('', captureOutput().trim());
			console.timeEnd('t');
			""");
	}

	@Override
	protected JSEnvironment.Builder createEnvironment() {
		JSEnvironment.Builder builder = super.createEnvironment();
		builder.registerLibrary(new CaptureLibrary());
		return builder;
	}

	private static final class CaptureLibrary extends GlobalLibrary {
		private ByteArrayOutputStream captured;

		@Override
		public void configureStandardObjects(JSEnvironment env, StandardObjects standardObjects) {
			standardObjects.setOwnProperty("captureOutput", new BaseMethod(env, "captureOutput", 0) {
				@Override
				public Object call(Object obj, Object[] args) {
					if (captured == null) {
						captured = new ByteArrayOutputStream();
						JSRuntimeContext.get().getGlobalContext()
								.setOutStream(new PrintStream(captured, true, StandardCharsets.UTF_8));
					}
					String s = captured.toString(StandardCharsets.UTF_8);
					captured.reset();
					return s;
				}
			}, PropertyDescriptor.DESC_METHOD);
		}
	}
}
