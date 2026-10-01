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
package tests.ui;

import tests.ProjectTestCase;

/**
 * The AST description of the GaltaJS playground.
 */
public class AstDescriptionTest extends ProjectTestCase {

	enum Color { RED }
	record Point(int x, int y) {}
	static class Node {
		String name;
		Node next;
		Color color = Color.RED;
		Point point = new Point(1, 2);
		java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger(5);
		Node(String name) { this.name = name; }
	}

	public void testAstDescriptionCyclesAndOpaqueTypes() {
		Node a = new Node("a"), b = new Node("b");
		a.next = b;
		b.next = a;	// a cycle: used to recurse until a StackOverflowError
		org.monflabs.util.TextBuilder tb = new org.monflabs.util.TextBuilder();
		playground.impl.GaltaJSPlaygroundFrame.readObject(tb, a);
		String s = tb.toString();
		assertTrue(s, s.contains("<cycle>"));
		// enums, records and JDK types print themselves (no reflective access to JDK internals)
		assertTrue(s, s.contains("color=RED"));
		assertTrue(s, s.contains("point=Point[x=1, y=2]"));
		assertTrue(s, s.contains("counter=5"));
	}

}
