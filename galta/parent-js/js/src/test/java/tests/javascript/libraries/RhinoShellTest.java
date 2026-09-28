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
package tests.javascript.libraries;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.library.GlobalLibrary;
import org.monflabs.galtajs.library.rhino.RhinoShellLibrary;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.standard.global.StandardObjects;

import tests.javascript.JavaScriptStrictTestCase;

/**
 * Tests for the Rhino shell library (print, version, options, gc, load).
 *
 * A ByteArrayOutputStream is redirected as the global out stream the first
 * time captureOutput() is called from JS, so print()'s side effect can be
 * asserted without touching the real System.out. quit() throws
 * JSRuntimeInterruptException; we do not exercise it here since it
 * terminates the script.
 */
public class RhinoShellTest extends JavaScriptStrictTestCase {

	public void testScript() throws Exception {
		execute();
	}

	@Override
	protected JSEnvironment.Builder createEnvironment() {
		JSEnvironment.Builder builder = super.createEnvironment();
		builder.registerLibrary(new RhinoShellLibrary());
		builder.registerLibrary(new CaptureLibrary());
		return builder;
	}

	/**
	 * Exposes a captureOutput() global that returns bytes written to the
	 * shell out stream since the last call. On first invocation it swaps
	 * the global context's out stream to a ByteArrayOutputStream so print()
	 * output is captured for the rest of the test run.
	 */
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
