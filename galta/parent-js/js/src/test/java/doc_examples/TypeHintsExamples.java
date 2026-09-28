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

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSParseException;
import org.monflabs.galtajs.environments.GaltaJSEnvironment;
import org.monflabs.galtajs.environments.JavaScriptEnvironment;
import org.monflabs.tests.__BaseTestCase;

/**
 * Samples for docs/GaltaJS/Extensions/TypeHints.md
 */
public class TypeHintsExamples extends __BaseTestCase {

	public void testAnnotationsAreParsedAndIgnored() {
		JSEnvironment env = GaltaJSEnvironment.create();   // supportTypeHints is on
		Object r = env.evaluateScript("""
			interface Named { name: string }
			type Id = string | number;

			function label(id: Id, named?: Named): string {
				return `${id}:${named?.name ?? 'anonymous'}`;
			}
			const shout = (s: string): string => s.toUpperCase();

			class Box<T> implements Named {
				name: string = 'box';
				constructor(private readonlyIsNotSupported = 0) {}
				wrap<U>(value: U): U[] { return [value]; }
			}
			const b = new Box();
			let count: number = 2;
			count = count * 2;
			[label(1), label('x', b), shout('ok'), b.wrap(count)[0]]
			""".replace("constructor(private readonlyIsNotSupported = 0) {}", "constructor() {}"));
		assertEquals("[ \"1:anonymous\", \"x:box\", \"OK\", 4 ]", r.toString());
	}

	public void testNoTypeChecking() {
		// Annotations carry no runtime meaning: a string flows into a `number` parameter
		JSEnvironment env = GaltaJSEnvironment.create();
		assertEquals("11", (Object)env.evaluateScript("function twice(n: number): number { return n + n } twice('1')"));
	}

	public void testTypeWordsRemainValidIdentifiers() {
		JSEnvironment env = GaltaJSEnvironment.create();
		assertEquals(11, (Object)env.evaluateScript("let type = 5; const interface = 6; type = type + interface; type"));
	}

	public void testDisabledByDefault() {
		try {
			JavaScriptEnvironment.create().evaluateScript("let x: number = 5;");
			fail();
		} catch(JSParseException e) {
			// plain ECMAScript: the annotation is a syntax error
		}
		// It can be enabled on its own
		JSEnvironment env = JavaScriptEnvironment.newBuilder().supportTypeHints(true).build();
		assertEquals(5, (Object)env.evaluateScript("let x: number = 5; x"));
	}

	public void testUnsupportedTypeScriptSyntax() {
		JSEnvironment env = GaltaJSEnvironment.create();
		for(String script : new String[] {
				"let x = 5 as number;",            // type assertions
				"enum Color { Red }",              // enums
				"class C { private x = 1 }",       // member modifiers
				"new Box<number>()",               // type arguments at call sites
		}) {
			try {
				env.evaluateScript(script);
				fail("expected a parse error for: " + script);
			} catch(JSParseException e) {
				// not part of the supported subset
			}
		}
	}
}
