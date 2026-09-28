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
package tests.json.jsonpointer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.jsonpointer.JsonPointer;

import tests.ProjectTestCase;

public class JsonPointerTest extends ProjectTestCase {
	
	public void testParsing() { 
		assertArrayEquals(array(),JsonPointer.of(null).getParts());
		assertArrayEquals(array(),JsonPointer.of("").getParts());
		// RFC 6901: "/" is the member "", and a trailing '/' is an empty member name
		assertArrayEquals(array(""),JsonPointer.of("/").getParts());
		assertArrayEquals(array("",""),JsonPointer.of("//").getParts());
		assertArrayEquals(array("a"),JsonPointer.of("a").getParts());
		assertArrayEquals(array("a"),JsonPointer.of("/a").getParts());
		assertArrayEquals(array("a",""),JsonPointer.of("a/").getParts());
		assertArrayEquals(array("a",""),JsonPointer.of("/a/").getParts());
		assertArrayEquals(array("a","b"),JsonPointer.of("a/b").getParts());
		assertArrayEquals(array("a","b"),JsonPointer.of("/a/b").getParts());
		assertArrayEquals(array("a","b",""),JsonPointer.of("a/b/").getParts());
		assertArrayEquals(array("a","b",""),JsonPointer.of("/a/b/").getParts());

		assertArrayEquals(array("~a"),JsonPointer.of("/~0a").getParts());
		assertArrayEquals(array("~a","~b"),JsonPointer.of("/~0a/~0b").getParts());
		assertArrayEquals(array("/a"),JsonPointer.of("/~1a").getParts());

		assertArrayEquals(array("\\a\n"),JsonPointer.of("\\a\n").getParts());
		
		assertEquals(0, JsonPointer.of("").size());
		assertEquals(1, JsonPointer.of("a").size());
		assertEquals(2, JsonPointer.of("a/b").size());

		assertEquals("a", JsonPointer.of("a/b/2").getPart(0));
		assertEquals("b", JsonPointer.of("a/b/2").getPart(1));
		assertEquals(2, JsonPointer.of("a/b/2").getPart(2));
		
		assertArrayEquals(new Object[] {}, JsonPointer.of("").getParts());
		assertArrayEquals(new Object[] {"a"}, JsonPointer.of("a").getParts());
		assertArrayEquals(new Object[] {"a","b"}, JsonPointer.of("a/b").getParts());
		assertArrayEquals(new Object[] {2}, JsonPointer.of("2").getParts());
		assertArrayEquals(new Object[] {"a",3}, JsonPointer.of("a/3").getParts());
	}

	public void testJsonPathParsing() { 
		assertArrayEquals(array(),JsonPointer.ofJsonPath(null).getParts());
		assertArrayEquals(array(),JsonPointer.ofJsonPath("").getParts());
		assertArrayEquals(array(),JsonPointer.ofJsonPath("$").getParts());
		assertArrayEquals(array("a"),JsonPointer.ofJsonPath("$.a").getParts());
		assertArrayEquals(array("a","b"),JsonPointer.ofJsonPath("$.a.b").getParts());
		assertArrayEquals(array(0),JsonPointer.ofJsonPath("$[0]").getParts());
		assertArrayEquals(array("a",0),JsonPointer.ofJsonPath("$.a[0]").getParts());
		assertArrayEquals(array("a",0,"b","c"),JsonPointer.ofJsonPath("$.a[0].b['c']").getParts());
		
		assertThrows(JsonException.class, () -> JsonPointer.ofJsonPath("abc") );
		assertThrows(JsonException.class, () -> JsonPointer.ofJsonPath("$..1") );
		assertThrows(JsonException.class, () -> JsonPointer.ofJsonPath("$.$.1") );
		assertThrows(JsonException.class, () -> JsonPointer.ofJsonPath("$.a[abc]") );
		assertThrows(JsonException.class, () -> JsonPointer.ofJsonPath("$.a.[1]") );
	}
	
	public void testEquality() { 
		assertEquals(JsonPointer.of(""), JsonPointer.of(""));
		assertEquals(JsonPointer.of("a"), JsonPointer.of("a"));
		assertEquals(JsonPointer.of("a/b"), JsonPointer.of("/a/b"));
		assertEquals(JsonPointer.of("a/-"), JsonPointer.of("/a/-"));
		assertEquals(JsonPointer.of("a/1/b"), JsonPointer.of("/a/1/b"));
		
		assertNotEquals(JsonPointer.of(""), JsonPointer.of("b"));
		assertNotEquals(JsonPointer.of("a"), JsonPointer.of("b"));
		assertNotEquals(JsonPointer.of("-1"), JsonPointer.of("-"));

		assertEquals(JsonPointer.of(""), JsonPointer.ofJsonPath(""));
		assertEquals(JsonPointer.of("a"), JsonPointer.ofJsonPath("$.a"));
		assertEquals(JsonPointer.of("a/b"), JsonPointer.ofJsonPath("$.a.b"));
		assertEquals(JsonPointer.of("a/-1"), JsonPointer.ofJsonPath("$.a[-1]"));
		assertEquals(JsonPointer.of("a/-1"), JsonPointer.ofJsonPath("$.a['-1']"));
		assertEquals(JsonPointer.of("a/1/b"), JsonPointer.ofJsonPath("$.a[1].b"));
		assertEquals(JsonPointer.of("a/1/b"), JsonPointer.ofJsonPath("$.a[1]['b']"));
		assertEquals(JsonPointer.of("a/1"), JsonPointer.ofJsonPath("$.a[1]"));
		assertEquals(JsonPointer.of("a/1"), JsonPointer.ofJsonPath("$.a['1']"));
		assertEquals(JsonPointer.of("a/-1"), JsonPointer.ofJsonPath("$.a[-1]"));
		assertEquals(JsonPointer.of("a/-1"), JsonPointer.ofJsonPath("$.a['-1']"));
		assertEquals(JsonPointer.of("a/-2"), JsonPointer.ofJsonPath("$.a[-2]"));
		assertEquals(JsonPointer.of("a/-2"), JsonPointer.ofJsonPath("$.a['-2']"));
	}
	
	public void testContains() {
		assertTrue(JsonPointer.of("").contains(JsonPointer.of("")));		
		assertTrue(JsonPointer.of("a").contains(JsonPointer.of("a")));		
		assertTrue(JsonPointer.of("0").contains(JsonPointer.of("0")));		
		assertTrue(JsonPointer.of("0/a/1").contains(JsonPointer.of("0/a")));		
		assertTrue(JsonPointer.of("0/a/1").contains(JsonPointer.of("0/a/1")));		
		assertTrue(JsonPointer.of("a/b").contains(JsonPointer.of("a")));		
		assertTrue(JsonPointer.of("a/b").contains(JsonPointer.of("a/b")));		
		assertTrue(JsonPointer.of("a/b/c").contains(JsonPointer.of("a/b")));
		
		assertFalse(JsonPointer.of("").contains(JsonPointer.of("a")));		
		assertFalse(JsonPointer.of("a").contains(JsonPointer.of("b")));		
		assertFalse(JsonPointer.of("a").contains(JsonPointer.of("a/b")));		
		assertFalse(JsonPointer.of("a/c").contains(JsonPointer.of("a/b")));		
		assertFalse(JsonPointer.of("a/c/b").contains(JsonPointer.of("a/b")));		
	}

	public void testSerialization() { 
		assertEquals("/a",JsonPointer.of("a").toString());
		assertEquals("/a/b",JsonPointer.of("a/b").toString());
		assertEquals("/a/b/c",JsonPointer.of("a/b/c").toString());
		assertEquals("/a/0/b/1/c/99",JsonPointer.of("a/0/b/1/c/99").toString());
	}

	public void testJsonPathSerialization() { 
		assertEquals("$.a",JsonPointer.of("a").toJsonPathString());
		assertEquals("$.a.b",JsonPointer.of("a/b").toJsonPathString());
		assertEquals("$.a.b.c",JsonPointer.of("a/b/c").toJsonPathString());
		assertEquals("$.a[0].b[1].c[99]",JsonPointer.of("a/0/b/1/c/99").toJsonPathString());
	}

	public void testExecute() { 
		JsonObject json = JsonObject.parse(
"""
{
	a: {
		aa: {
			aa1: '1',
			aa2: 2
		}
	},
	b: [
		'1',
		2,
		[
			'3',
			false
		]
	],
	c: {
		aa: [
			'5',
			6,
			{
				aa1: "777"
			}
		]
	},
	d: {
		"/a/": "AA",
		"a~": "BB"
	}
}
"""			
		);

		// "Regular" JSON pointer
		assertEquals(json, JsonPointer.of("").read(json));
		assertEquals(null, JsonPointer.of("abc").read(json));
		assertEquals(null, JsonPointer.of("/").read(json)); // the "" member
		assertEquals(null, JsonPointer.of("/abc").read(json));
		assertEquals(null, JsonPointer.of("abc").read(json));
		assertEquals(null, JsonPointer.of("a/bc").read(json));

		assertEquals("1", JsonPointer.of("a/aa/aa1").read(json));
		assertEquals(2, JsonPointer.of("a/aa/aa2").read(json));

		assertEquals("1", JsonPointer.of("b/0").read(json));
		assertEquals(2, JsonPointer.of("b/1").read(json));
		// "-" is the element after the last one: it never exists
		assertEquals(null, JsonPointer.of("b/-/0").read(json));
		assertEquals(null, JsonPointer.of("b/-/-").read(json));
		assertFalse(JsonPointer.of("b/-").exists(json));
		assertEquals("3", JsonPointer.of("b/2/0").read(json));
		assertEquals(false, JsonPointer.of("b/2/1").read(json));

		assertEquals(JsonArray.of("3",false), JsonPointer.of("b/-1").read(json));
		assertEquals(2, JsonPointer.of("b/-2").read(json));
		assertEquals("1", JsonPointer.of("b/-3").read(json));

		assertEquals("5", JsonPointer.of("c/aa/0").read(json));
		assertEquals(6, JsonPointer.of("c/aa/1").read(json));
		assertEquals("777", JsonPointer.of("c/aa/2/aa1").read(json));
		assertEquals(JsonObject.of("aa1","777"), JsonPointer.of("c/aa/2").read(json));

		assertEquals("AA", JsonPointer.of("d/~1a~1").read(json));
		assertEquals("BB", JsonPointer.of("d/a~0").read(json));
		
		// JsonPath Pointer
		assertEquals(json, JsonPointer.ofJsonPath("").read(json));
		assertEquals(json, JsonPointer.ofJsonPath("$").read(json));
		assertEquals(null, JsonPointer.ofJsonPath("$.abc").read(json));
		assertEquals(null, JsonPointer.ofJsonPath("$.a.bc").read(json));

		assertEquals("1", JsonPointer.ofJsonPath("$.a.aa.aa1").read(json));
		assertEquals(2, JsonPointer.ofJsonPath("$.a.aa.aa2").read(json));

		assertEquals("1", JsonPointer.ofJsonPath("$.b[0]").read(json));
		assertEquals(2, JsonPointer.ofJsonPath("$.b[1]").read(json));
		assertEquals("3", JsonPointer.ofJsonPath("$.b[-1][0]").read(json));
		assertEquals(false, JsonPointer.ofJsonPath("$.b[-1][-1]").read(json));
		assertEquals("3", JsonPointer.ofJsonPath("$.b[2][0]").read(json));
		assertEquals(false, JsonPointer.ofJsonPath("$.b[2][1]").read(json));

		assertEquals("5", JsonPointer.ofJsonPath("$.c.aa[0]").read(json));
		assertEquals(6, JsonPointer.ofJsonPath("$.c.aa[1]").read(json));
		assertEquals("777", JsonPointer.ofJsonPath("$.c.aa[2].aa1").read(json));
		assertEquals(JsonObject.of("aa1","777"), JsonPointer.ofJsonPath("$.c.aa[2]").read(json));

		assertEquals("AA", JsonPointer.ofJsonPath("$.d['/a/']").read(json));
		assertEquals("BB", JsonPointer.ofJsonPath("$.d['a~']").read(json));
	}
	
	public void testAdd() {
		JsonObject json = JsonObject.parse(
"""
{
	a: {
		aa: {
			aa1: '1',
			aa2: 2
		},
		bb: 'BB'
	},
	b: [
		22,33
	]
}
""" );
		
		assertTrue(JsonPointer.of("a/aa/aa2").add(json, 44));
		assertTrue(JsonPointer.of("a/aa/aa").add(json, 55));
		assertTrue(JsonPointer.of("b/0").add(json, 66));
		assertTrue(JsonPointer.of("b/-").add(json, 99));
		assertTrue(JsonPointer.of("a/bb").add(json, "BBC"));

		assertFalse(JsonPointer.of("a/xx/cc").replace(json, "CCA"));
		assertFalse(JsonPointer.of("a/aa/0").replace(json, "CCA"));
		assertFalse(JsonPointer.of("b/5").replace(json, 99));
		assertFalse(JsonPointer.of("5").replace(json, 999));

		JsonObject json2 = JsonObject.parse(
"""
{
	a: {
		aa: {
			aa1: '1',
			aa2: 44,
			aa: 55,
		},
		bb: 'BBC'
	},
	b: [
		66,22,33,99
	]
}
""" );
		support.assertJsonEquals(json2, json);
	}

	
	public void testReplace() {
		JsonObject json = JsonObject.parse(
"""
{
	a: {
		aa: {
			aa1: '1',
			aa2: 2
		},
		bb: 'BB'
	},
	b: [
		66,33
	]
}
""" );
		
		assertTrue(JsonPointer.of("a/aa/aa2").replace(json, 44));
		assertTrue(JsonPointer.of("a/bb").replace(json, "BBC"));
		assertTrue(JsonPointer.of("b/0").replace(json, 66));

		assertFalse(JsonPointer.of("a/aa/cc").replace(json, "CCA"));
		assertFalse(JsonPointer.of("a/aa/0").replace(json, "CCA"));
		assertFalse(JsonPointer.of("b/5").replace(json, 99));
		assertFalse(JsonPointer.of("5").replace(json, 99));

		JsonObject json2 = JsonObject.parse(
"""
{
	a: {
		aa: {
			aa1: '1',
			aa2: 44
		},
		bb: 'BBC'
	},
	b: [
		66,33
	]
}
""" );
		support.assertJsonEquals(json2, json);
	}

	
	public void testRemove() {
		JsonObject json = JsonObject.parse(
"""
{
	a: {
		aa: {
			aa1: '1',
			aa2: 2
		},
		bb: 'BB'
	},
	b: [
		66,33
	]
}
""" );
		
		assertTrue(JsonPointer.of("a/aa/aa2").remove(json));
		assertTrue(JsonPointer.of("a/bb").remove(json));
		assertTrue(JsonPointer.of("b/0").remove(json));

		assertFalse(JsonPointer.of("a/aa/cc").remove(json));
		assertFalse(JsonPointer.of("b/5").remove(json));
		assertFalse(JsonPointer.of("5").remove(json));

		JsonObject json2 = JsonObject.parse(
"""
{
	a: {
		aa: {
			aa1: '1'
		}
	},
	b: [
		33
	]
}
""" );
		support.assertJsonEquals(json2, json);
	}

	
	public void testSetValue() {
		JsonObject json = JsonObject.parse(
"""
{
	a: {
		aa: {
			aa1: '1',
			aa2: 2
		},
		bb: 'BB'
	},
	b: [
		66,33
	]
}
""" );
		
		assertTrue(JsonPointer.of("a/aa/aa2").setValue(json, 44));
		assertTrue(JsonPointer.of("a/bb").setValue(json, "BBC"));
		assertTrue(JsonPointer.of("b/0").setValue(json, 66));

		assertTrue(JsonPointer.of("a/aa/cc").setValue(json, "CCA"));
		assertTrue(JsonPointer.of("a/aa/0").setValue(json, "CCA"));
		// An index can only address an item or append (no padding with nulls)
		assertFalse(JsonPointer.of("b/5").setValue(json, 99));
		assertTrue(JsonPointer.of("b/2").setValue(json, 99));
		assertTrue(JsonPointer.of("b/3/0").setValue(json, 77));
		assertTrue(JsonPointer.of("b/-/A").setValue(json, 88));
		assertTrue(JsonPointer.of("5").setValue(json, 99));

		JsonObject json2 = JsonObject.parse(
"""
{
	a: {
		aa: {
			aa1: '1',
			aa2: 44,
			cc: "CCA",
			"0": "CCA",
		},
		bb: 'BBC'
	},
	b: [
		66,33,99,[77],{A:88}
	],
	"5": 99,
}
""" );
		support.assertJsonEquals(json2, json);
	}

	
	private Object[] array(Object...values) {
		return values;
	}
}
