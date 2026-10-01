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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;

import java.util.Iterator;

import org.monflabs.json.JsonException;
import org.monflabs.json.jsonpath.JsonPath;
import org.monflabs.json.jsonpath.JsonPathFactory;
import org.monflabs.json.jsonpath.JsonPathParser;
import org.monflabs.json.jsonpath.PathNode;
import org.monflabs.util.iterators.Iterators;

import tests.ProjectTestCase;

public class SimpleJsonPathCompilerTest extends ProjectTestCase {
	
	private static Object[] parts(JsonPath p) {
		if(p==null) {
			return new Object[0];
		}
		Object[] parts = new Object[Iterators.size(p.nodeIterator())];
		int i = 0;
		for(Iterator<PathNode> it=p.nodeIterator(); it.hasNext(); ) {
			PathNode o = it.next();
			parts[i++] = o.toString();
		}
		return parts;
	}
	
	private static JsonPathFactory simpleFactory = new JsonPathFactory();
	
	public void testCompile() {
		assertArrayEquals(array(),parts(simpleFactory.getJsonPath(null)));
		assertArrayEquals(array(),parts(simpleFactory.getJsonPath("")));
		assertArrayEquals(array(),parts(simpleFactory.getJsonPath("$")));
		assertArrayEquals(array(".a"),parts(simpleFactory.getJsonPath("$.a")));
		if(JsonPathParser.RELAXED_SYNTAX) {
			assertArrayEquals(array(".a"),parts(simpleFactory.getJsonPath("$.'a'")));
			assertArrayEquals(array(".a"),parts(simpleFactory.getJsonPath("$.\"a\"")));
		}
		assertArrayEquals(array(".ab"),parts(simpleFactory.getJsonPath("$.ab")));
		assertArrayEquals(array(".a","[*]"),parts(simpleFactory.getJsonPath("$.a.*")));
		assertArrayEquals(array(".a","[*]"),parts(simpleFactory.getJsonPath("$.a[*]")));
		assertArrayEquals(array(".abcdefg"),parts(simpleFactory.getJsonPath("$.abcdefg")));
		assertArrayEquals(array(".a",".b"),parts(simpleFactory.getJsonPath("$.a.b")));
		assertArrayEquals(array(".a",".bd"),parts(simpleFactory.getJsonPath("$.a.bd")));
		assertArrayEquals(array(".ab",".c"),parts(simpleFactory.getJsonPath("$.ab.c")));
		assertArrayEquals(array(".a",".b",".c"),parts(simpleFactory.getJsonPath("$.a.b.c")));
		
		assertArrayEquals(array(".a",".b"),parts(simpleFactory.getJsonPath("$.a['b']")));
		assertArrayEquals(array(".ab",".c"),parts(simpleFactory.getJsonPath("$.ab['c']")));
		assertArrayEquals(array(".a",".bc"),parts(simpleFactory.getJsonPath("$.a['bc']")));
		assertArrayEquals(array("['a','b']"),parts(simpleFactory.getJsonPath("$['a','b']")));
		assertArrayEquals(array("['a','b','c']"),parts(simpleFactory.getJsonPath("$['a','b','c']")));
		
		assertArrayEquals(array(".a","[0]"),parts(simpleFactory.getJsonPath("$.a[0]")));
		assertArrayEquals(array(".a","[12]"),parts(simpleFactory.getJsonPath("$.a[12]")));
		assertArrayEquals(array(".a","[1,2]"),parts(simpleFactory.getJsonPath("$.a[1,2]")));
		assertArrayEquals(array(".a","[1,2,3]"),parts(simpleFactory.getJsonPath("$.a[ 1 , 2 , 3 ]")));
		assertArrayEquals(array(".a","[1:2:]"),parts(simpleFactory.getJsonPath("$.a[1:2]")));
		assertArrayEquals(array(".a","[1:2:-1]"),parts(simpleFactory.getJsonPath("$.a[1:2:-1]")));
		assertArrayEquals(array(".a","[1:2:3]"),parts(simpleFactory.getJsonPath("$.a[1:2:3]")));
		assertArrayEquals(array(".a","[1:2:]"),parts(simpleFactory.getJsonPath("$.a[1:2:]")));
		assertArrayEquals(array(".a","[1:2:,3]"),parts(simpleFactory.getJsonPath("$.a[ 1 : 2 , 3]")));
		assertArrayEquals(array(".a","[1:2:,3:4:]"),parts(simpleFactory.getJsonPath("$.a[ 1 : 2 , 3:4]")));

		assertArrayEquals(array(".a","[:2:]"),parts(simpleFactory.getJsonPath("$.a[:2]")));
		assertArrayEquals(array(".a","[1::]"),parts(simpleFactory.getJsonPath("$.a[1:]")));
		assertArrayEquals(array(".a","[::]"),parts(simpleFactory.getJsonPath("$.a[:]")));
		assertArrayEquals(array(".a","[::4]"),parts(simpleFactory.getJsonPath("$.a[::4]")));
		assertArrayEquals(array(".a","[:2:1]"),parts(simpleFactory.getJsonPath("$.a[:2:1]")));
		assertArrayEquals(array(".a","[:2:1]"),parts(simpleFactory.getJsonPath("$.a[:2:1]")));
		
		assertArrayEquals(array(".a","[0]","[1]"),parts(simpleFactory.getJsonPath("$.a[0][1]")));
		assertArrayEquals(array(".a","[0]",".b",".c","[45]",".d",".e"),parts(simpleFactory.getJsonPath("$.a[0]['b'].c[45].d.e")));
		if(JsonPathParser.RELAXED_SYNTAX) {
			assertArrayEquals(array(".a","[0]",".b",".c"),parts(simpleFactory.getJsonPath("$.a[0]['b'].c")));
			assertArrayEquals(array(".a","[0]",".b",".c"),parts(simpleFactory.getJsonPath("$.a.0.'b'.c")));
		}
		assertArrayEquals(array("..",".ab"),parts(simpleFactory.getJsonPath("$..ab")));
		assertArrayEquals(array(".a","..",".b"),parts(simpleFactory.getJsonPath("$.a..b")));

		assertArrayEquals(array(".a","[0]",".b",".c","[45]",".d",".e"),parts(simpleFactory.getJsonPath("$ . a [ 0   ] [   'b' ] . c [ 45 ]  . d . e ")));

		assertArrayEquals(array("..",".a"), parts(simpleFactory.getJsonPath("$..a")) );
		assertArrayEquals(array("..","[*]"), parts(simpleFactory.getJsonPath("$..*")) );
		assertArrayEquals(array("..",".a","..",".b"), parts(simpleFactory.getJsonPath("$..a..b")) );
		
		assertArrayEquals(array("[*]"), parts(simpleFactory.getJsonPath("$.*")) );
		assertArrayEquals(array("[*]"), parts(simpleFactory.getJsonPath("$[*]")) );
	}
	
	public void testCompilePartial() {
		assertArrayEquals(array(),parts(simpleFactory.getPartialJsonPath(null,0)));
		assertArrayEquals(array(),parts(simpleFactory.getPartialJsonPath("",0)));
		assertArrayEquals(array(),parts(simpleFactory.getPartialJsonPath("$",0)));
		assertArrayEquals(array(".a"),parts(simpleFactory.getPartialJsonPath("$.a",0)));
		assertArrayEquals(array(".ab"),parts(simpleFactory.getPartialJsonPath("$.ab",0)));
		assertArrayEquals(array(".abcdefg"),parts(simpleFactory.getPartialJsonPath("$.abcdefg",0)));
		assertArrayEquals(array(".a",".b"),parts(simpleFactory.getPartialJsonPath("$.a.b",0)));
		assertArrayEquals(array(".a",".bd"),parts(simpleFactory.getPartialJsonPath("$.a.bd",0)));
		assertArrayEquals(array(".ab",".c"),parts(simpleFactory.getPartialJsonPath("$.ab.c",0)));
		assertArrayEquals(array(".a",".b",".c"),parts(simpleFactory.getPartialJsonPath("$.a.b.c",0)));
		assertArrayEquals(array(".a",".b"),parts(simpleFactory.getPartialJsonPath("$.a['b']",0)));
		assertArrayEquals(array(".ab",".c"),parts(simpleFactory.getPartialJsonPath("$.ab['c']",0)));
		assertArrayEquals(array(".a",".bc"),parts(simpleFactory.getPartialJsonPath("$.a['bc']",0)));
		assertArrayEquals(array(".a","[12]"),parts(simpleFactory.getPartialJsonPath("$.a[12]",0)));
		assertArrayEquals(array(".a","[0]"),parts(simpleFactory.getPartialJsonPath("$.a[0]",0)));
		assertArrayEquals(array(".a","[12]"),parts(simpleFactory.getPartialJsonPath("$.a[12]",0)));
		assertArrayEquals(array(".a","[0]","[1]"),parts(simpleFactory.getPartialJsonPath("$.a[0][1]",0)));
		assertArrayEquals(array(".a","[0]",".b",".c"),parts(simpleFactory.getPartialJsonPath("$.a[0]['b'].c",0)));
		assertArrayEquals(array(".a","[0]",".b",".c","[45]",".d",".e"),parts(simpleFactory.getPartialJsonPath("$.a[0]['b'].c[45].d.e",0)));

		assertArrayEquals(array(),parts(simpleFactory.getPartialJsonPath("!",0)));
		assertEquals("",simpleFactory.getPartialJsonPath("!",0).getJsonPath());
		assertArrayEquals(array(".a"),parts(simpleFactory.getPartialJsonPath("$.a",0)));
		assertEquals("$.a",simpleFactory.getPartialJsonPath("$.a",0).getJsonPath());
		assertArrayEquals(array(".a"),parts(simpleFactory.getPartialJsonPath("$.a!",0)));
		assertEquals("$.a",simpleFactory.getPartialJsonPath("$.a!",0).getJsonPath());
		assertArrayEquals(array(".a",".b"),parts(simpleFactory.getPartialJsonPath("$.a.b",0)));
		assertEquals("$.a.b",simpleFactory.getPartialJsonPath("$.a.b",0).getJsonPath());
		assertArrayEquals(array(".a",".b"),parts(simpleFactory.getPartialJsonPath("$.a.b#",0)));
		assertEquals("$.a.b",simpleFactory.getPartialJsonPath("$.a.b#",0).getJsonPath());

		assertArrayEquals(array(".a",".b"),parts(simpleFactory.getPartialJsonPath("XYZ$.a.b",3)));
		assertEquals("$.a.b",simpleFactory.getPartialJsonPath("XYZ$.a.b",3).getJsonPath());
		assertArrayEquals(array(".a",".b$try"),parts(simpleFactory.getPartialJsonPath("XYZ$.a.b$try",3)));
		assertEquals("$.a.b$try",simpleFactory.getPartialJsonPath("XYZ$.a.b$try",3).getJsonPath());
		assertArrayEquals(array(".a","['/b/%']"),parts(simpleFactory.getPartialJsonPath("XYZ$.a['/b/%']",3)));
		assertEquals("$.a['/b/%']",simpleFactory.getPartialJsonPath("XYZ$.a['/b/%']",3).getJsonPath());
	}

	public void testJsonPathError() {
		assertError("|");
		assertError("$. .a");
		assertError("$.a.");
		assertError("$.a[");
		assertError("$.a[]");
		//assertError("$.a[1,'a']");
		assertError("$.a[ ]");
		assertError("$.a[,]");
		//assertError("$.a[1,'2']");
		assertError("$.a[,:2]");
		assertError("$.a[:2,]");
		assertError("$.a[abc]");
		assertError("$.a[5].");
		if(!JsonPathParser.RELAXED_SYNTAX) {
			assertError("$.['key]");
			assertError("$..['key]");
		}
	}
	private void assertError(String path) {
		try {
			simpleFactory.getJsonPath(path);
			fail();
		} catch(JsonException e) {
			// Desired exception
		}
	}

	public void testPathDefinite() {
		assertTrue(simpleFactory.createJsonPath("$").isDefinite());
		assertTrue(simpleFactory.createJsonPath("$.a").isDefinite());
		assertTrue(simpleFactory.createJsonPath("$.a.b").isDefinite());
		assertTrue(simpleFactory.createJsonPath("$.a[0]").isDefinite());
		assertTrue(simpleFactory.createJsonPath("$.a['b']").isDefinite());

		assertFalse(simpleFactory.createJsonPath("$.*").isDefinite());
		assertFalse(simpleFactory.createJsonPath("$.a[*]").isDefinite());
		assertFalse(simpleFactory.createJsonPath("$..a").isDefinite());
		assertFalse(simpleFactory.createJsonPath("$['a','b']").isDefinite());
		if(JsonPathParser.RELAXED_SYNTAX) {
			assertFalse(simpleFactory.createJsonPath("$.['a','b']").isDefinite());
		}
	}

	public void testPathPointer() {
		assertEquals( "", simpleFactory.createJsonPath("$").toJsonPointer().toJsonPointerString());
		assertEquals( "/a", simpleFactory.createJsonPath("$.a").toJsonPointer().toJsonPointerString());
		assertEquals( "/a/b", simpleFactory.createJsonPath("$.a.b").toJsonPointer().toJsonPointerString());
		assertEquals( "/0", simpleFactory.createJsonPath("$[0]").toJsonPointer().toJsonPointerString());
		assertEquals( "/a/1", simpleFactory.createJsonPath("$.a[1]").toJsonPointer().toJsonPointerString());
		assertEquals( "/a/2/b", simpleFactory.createJsonPath("$.a[2].b").toJsonPointer().toJsonPointerString());
		assertEquals( "/a/2/b/3", simpleFactory.createJsonPath("$.a[2].b[3]").toJsonPointer().toJsonPointerString());

		assertThrows( JsonException.class, () -> simpleFactory.createJsonPath("$.*").toJsonPointer() );
		assertThrows( JsonException.class, () -> simpleFactory.createJsonPath("$..c").toJsonPointer() );
	}
	

	public void testPathSerialization() {
		assertEquals("$", simpleFactory.createJsonPath("$").canonicalPath() );
		assertEquals("$.a", simpleFactory.createJsonPath("$.a").canonicalPath() );
		assertEquals("$.a.b", simpleFactory.createJsonPath("$.a.b").canonicalPath() );
		assertEquals("$.a.b", simpleFactory.createJsonPath("$['a'][\"b\"]").canonicalPath() );
		assertEquals("$.a[0].b", simpleFactory.createJsonPath("$.a[0].b").canonicalPath() );
		assertEquals("$.a..b", simpleFactory.createJsonPath("$.a..b").canonicalPath() );
		assertEquals("$.a[*].b", simpleFactory.createJsonPath("$.a[*].b").canonicalPath() );
		assertEquals("$.a[*].b", simpleFactory.createJsonPath("$.a.*.b").canonicalPath() );
		assertEquals("$['123']", simpleFactory.createJsonPath("$['123']").canonicalPath() );
		assertEquals("$.abcd", simpleFactory.createJsonPath("$['abcd']").canonicalPath() );
		assertEquals("$['ab%cd']", simpleFactory.createJsonPath("$['ab%cd']").canonicalPath() );
	}



	private Object[] array(Object...values) {
		return values;
	}
}
