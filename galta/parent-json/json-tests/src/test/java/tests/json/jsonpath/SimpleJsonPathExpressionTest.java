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
package tests.json.jsonpath;

import org.monflabs.json.JsonObject;
import org.monflabs.json.jsonpath.ExprNode;
import org.monflabs.json.jsonpath.JsonPathParser;
import org.monflabs.util.Console;

import tests.ProjectTestCase;

public class SimpleJsonPathExpressionTest extends ProjectTestCase {
	
	public void testParser() {
		assertEquals("", parse(""));
		
		assertEquals("1", parse("1"));
		assertEquals("1==2", parse("1 == 2"));
		assertEquals("(1==2) && (3==4)", parse("1==2&&3==4"));
		assertEquals("(1==2) && (3==4)", parse("1 == 2 && 3 == 4"));
		assertEquals("((1==2) && (2==3)) || (4==5)", parse("1==2 && 2==3 || 4==5"));
		assertEquals("((1==2) && (2==3)) || (4==5)", parse("(1==2 && 2==3) || 4==5"));
		assertEquals("(1==2) && ((2==3) || (4==5))", parse("1==2 && (2==3 || 4==5)"));
	}
	private String parse(String expr) {
		JsonPathParser parser = new JsonPathParser(expr);
		ExprNode node = parser.readExpression();
		if(!parser.isEmpty()) {
			Console.log("Expression is unfinished\n{0}\n{1}",expr,expr.substring(parser.getPtr()));
			fail(); // Expression must be finished
		}
		return node!=null ? node.toString() : "";
	}

	public void testOne() {
		assertEquals(true, execute("1.1>=1.1"));
	}

	public void testEquals() {
		assertEquals(true, execute("1==1"));
		assertEquals(false, execute("1==2"));
		assertEquals(false, execute("1!=1"));
		assertEquals(true, execute("1!=2"));
		
		assertEquals(true, execute("1.1==1.1"));
		assertEquals(false, execute("1.1==1.2"));
		assertEquals(false, execute("1.1!=1.1"));
		assertEquals(true, execute("1.1!=1.2"));

		assertEquals(true, execute("true==true"));
		assertEquals(true, execute("false==false"));
		assertEquals(false, execute("false==true"));
		assertEquals(false, execute("true==false"));
		assertEquals(false, execute("true!=true"));
		assertEquals(false, execute("false!=false"));
		assertEquals(true, execute("false!=true"));
		assertEquals(true, execute("true!=false"));
		
		assertEquals(true, execute("'abc'=='abc'"));
		assertEquals(false, execute("'abc'=='xyz'"));
		assertEquals(true, execute("'abc'!='xyz'"));
		assertEquals(false, execute("'abc'!='abc'"));
	}
	public void testCompare() {
		assertEquals(true, execute("1.1>=1.1"));
		assertEquals(true, execute("1.2>=1.1"));
		assertEquals(false, execute("1.1>=1.2"));
		
		assertEquals(false, execute("1.1>1.1"));
		assertEquals(true, execute("1.2>1.1"));
		assertEquals(false, execute("1.1>1.2"));
		
		assertEquals(true, execute("1.1<=1.1"));
		assertEquals(false, execute("1.2<=1.1"));
		assertEquals(true, execute("1.1<=1.2"));
		
		assertEquals(false, execute("1.1<1.1"));
		assertEquals(false, execute("1.2<1.1"));
		assertEquals(true, execute("1.1<1.2"));

		assertEquals(true, execute("'a'>='a'"));
		assertEquals(true, execute("'b'>='a'"));
		assertEquals(false, execute("'a'>='b'"));
		
		assertEquals(false, execute("'a'>'a'"));
		assertEquals(true, execute("'b'>'a'"));
		assertEquals(false, execute("'a'>'b'"));
		
		assertEquals(true, execute("'a'<='a'"));
		assertEquals(false, execute("'b'<='a'"));
		assertEquals(true, execute("'a'<='b'"));
		
		assertEquals(false, execute("'a'<'a'"));
		assertEquals(false, execute("'b'<'a'"));
		assertEquals(true, execute("'a'<'b'"));
	}
	public void testPath() {
		assertEquals(true, execute("$.a==10"));
		assertEquals(false, execute("$.a==11"));

		assertEquals(true, execute("@.x==20"));
		assertEquals(false, execute("@.x==21"));

		assertEquals(true, execute("$.a==10 && @.x==20"));
		assertEquals(false, execute("$.a==10 && @.x==21"));
		assertEquals(false, execute("$.a==11 && @.x==20"));

		assertEquals(true, execute("($.a==10 && @.x==21) || @.y==21"));
		assertEquals(false, execute("($.a==10 && @.x==21) || @.y==20"));
		assertEquals(false, execute("$.a==11 && (@.x==21 || @.y==20)"));
		assertEquals(false, execute("$.a==10 && (@.x==21 || @.y==20)"));
		assertEquals(true, execute("$.a==10 && (@.x==20 || @.y==20)"));
		assertEquals(true, execute("$.a==10 && (@.x==20 || @.y==21)"));
		assertEquals(true, execute("$.a==10 && (@.x==21 || @.y==21)"));
	}
	private Object execute(String expr) {
		JsonPathParser parser = new JsonPathParser(expr.trim());
		ExprNode node = parser.readExpression();
		assertTrue(parser.isEmpty()); // all should be consumed
		if(node!=null) {
			JsonObject root = JsonObject.of("a",10,"b",11);
			JsonObject filter = JsonObject.of("x",20,"y",21);
			return node.execute(root, filter);
		}
		return null;
	}
}
