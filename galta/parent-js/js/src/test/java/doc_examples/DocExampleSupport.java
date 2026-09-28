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
package doc_examples;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.environments.GaltaJSEnvironment;
import org.monflabs.galtajs.environments.JavaScriptEnvironment;
import org.monflabs.galtajs.rt.JSGlobalContext;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;

/**
 * Small helpers shared by the documentation samples. Everything here is
 * plain public API - the helpers only exist to keep the samples short.
 */
public final class DocExampleSupport {

	private DocExampleSupport() {}

	/** Plain ECMAScript environment: StandardLibrary only, no GaltaJS extensions. */
	public static JSEnvironment jsEnv() {
		return JavaScriptEnvironment.create();
	}

	/** GaltaJS environment: extensions enabled, StandardLibrary + JavaLibrary registered. */
	public static JSEnvironment galtaEnv() {
		return GaltaJSEnvironment.create();
	}

	/**
	 * Copies a JavaScript array (a {@code JSArray}, which is also a
	 * {@code java.util.List}) into a plain list so it can be compared with
	 * {@code List.of(...)} in assertions. A JavaScript array uses identity
	 * equality, like in JavaScript, so it never equals a plain list directly.
	 */
	public static List<Object> list(Object jsArray) {
		return new ArrayList<Object>((List<?>)jsArray);
	}

	/** Result of running a script while capturing what it printed with console.log(). */
	public record Captured(Object value, String output) {}

	/**
	 * Runs {@code script} in a fresh global context whose output stream is
	 * redirected, so that console.log() output can be asserted on.
	 */
	public static Captured captureOutput(JSEnvironment env, String script) {
		return captureOutput(env, ctx -> env.createScript(script, "sample.js").executeWithContext(ctx));
	}

	/**
	 * Same as above but lets the caller drive the context (for instance to
	 * install globals before running).
	 */
	public static Captured captureOutput(JSEnvironment env, Function<JSGlobalContext,Object> body) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
		JSGlobalContext ctx = new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor());
		ctx.setOutStream(out);
		ctx.setErrStream(out);
		Object value = body.apply(ctx);
		out.flush();
		return new Captured(value, bytes.toString(StandardCharsets.UTF_8));
	}

	/** Joins lines with '\n' and a trailing newline, the way console.log() prints them. */
	public static String lines(String... lines) {
		return String.join("\n", lines) + "\n";
	}
}
