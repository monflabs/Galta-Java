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
package tests.json.jsonreference;

import static org.junit.Assert.assertArrayEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.json.jsonpath.JsonValues;
import org.monflabs.json.jsonreference.JsonReference;
import org.monflabs.json.stringifier.JsonStringifier;
import org.monflabs.util.Console;

import tests.ProjectTestCase;

public class JsonReferenceTest extends ProjectTestCase {
	
	public static class TestResolver extends JsonReference.Resolver {
		private Map<String,String> externalDocs; 
		public TestResolver(Object root, Map<String,String> externalDocs) {
			super(root);
			this.externalDocs = externalDocs;
		}
		@Override
		public Object apply(JsonFactory t, String u) {
			if(externalDocs!=null && externalDocs.containsKey(u)) {
				return JsonFactory.get().parse(externalDocs.get(u));
			}
			return super.apply(t, u);
		}
	}
	
	public void testExample1() { 
		if(!JsonFactory.get().supportsReferences()) {
			return;
		}
		checkResolve(
"""
{
  "definitions": {
    "User": {
      "type": "object",
      "properties": {
        "id": { "type": "string" },
        "name": { "type": "string" }
      }
    }
  },
  "type": "object",
  "properties": {
    "author": { "$ref": "#/definitions/User" }
  }
}
""",
"""
{
  "definitions": {
    "User": {
      "type": "object",
      "properties": {
        "id": { "type": "string" },
        "name": { "type": "string" }
      }
    }
  },
  "type": "object",
  "properties": {
    "author": {
      "type": "object",
      "properties": {
        "id": { "type": "string" },
        "name": { "type": "string" }
      }
    }
  }
}
"""
		);
	}
	
	public void testExample2() { 
		if(!JsonFactory.get().supportsReferences()) {
			return;
		}
		// The other members of a reference object are ignored: the whole object is replaced
		checkResolve(
"""
{
  "definitions": {
    "entities": {
      "user": {
        "fields": {
          "id": { "type": "string" }
        }
      }
    }
  },
  "$ref": "#/definitions/entities/user/fields/id"
}""",
"""
{ "type": "string" }"""
		);
	}
	
	public void testExample3() { 
		if(!JsonFactory.get().supportsReferences()) {
			return;
		}
		checkResolve(
"""
{
  "title": "string",
  "author": {
    "$ref": "./user.json#/definitions/User"
  }
}""",
"""
{
  "title": "string",
  "author": {
    "type": "object",
    "properties": {
      "name": { "type": "string" }
    }
  }
}""",
Map.of("./user.json",
"""
{
  "definitions": {
    "User": {
      "type": "object",
      "properties": {
        "name": { "type": "string" }
      }
    }
  }
}"""
));
	}
	
	public void testExample4() { 
		if(!JsonFactory.get().supportsReferences()) {
			return;
		}
		checkResolve(
"""
{
  "$ref": "https://example.com/schemas/common.json#/Date"
}""",
"""
{
  "type": "string",
  "format": "date"
}""",
Map.of("https://example.com/schemas/common.json",
"""
{
  "Date": {
    "type": "string",
    "format": "date"
  }
}"""
));
	}
	
	public void testExample5() { 
		if(!JsonFactory.get().supportsReferences()) {
			return;
		}
		checkResolve(
"""
{
  "$ref": "https://example.com/schemas/address.json"
}""",
"""
{
  "type": "object",
  "properties": {
    "street": { "type": "string" },
    "city":   { "type": "string" }
  }
}""",
Map.of("https://example.com/schemas/address.json",
"""
{
  "type": "object",
  "properties": {
    "street": { "type": "string" },
    "city":   { "type": "string" }
  }
}"""
));
	}
	
	public void testExampleF1() {
		if(!JsonFactory.get().supportsReferences()) {
			return;
		}
		try {
			checkResolve(
"""
{
  "$ref": "https://example.com/schemas/address.json"
}""",
"""
"""
			);
			fail();
		} catch(JsonException e) {
			// Normal...
		}
	}
	
	public void testExampleSerialization() throws Exception {
		if(!JsonFactory.get().supportsReferences()) {
			return;
		}
		
		String source = 
		"""
			{ "$ref": "https://example.com/schemas/address.json"}
		""";
		String incl = 
		"""
{
  "type": "object",
  "properties": {
    "street": { "type": "string" },
    "city":   { "type": "string" }
  }
}""";
		
		Object resolved = resolve(source, Map.of("https://example.com/schemas/address.json",incl));
		
		JsonStringifier.StringSerializer w1 = new JsonStringifier.StringSerializer();
		String s1 = w1.stringify(resolved);
		support.assertJsonEquals(resolved, s1);
		
		JsonStringifier.StringSerializer w2 = new JsonStringifier.StringSerializer();
		w2.setOutputReferences(true);
		String s2 = w2.stringify(resolved);
		support.assertJsonEquals(source, s2);
		
	}

	public void testBrowse() throws Exception {
		if(!JsonFactory.get().supportsReferences()) {
			return;
		}
		
		JsonObject o = JsonObject.parse(
"""
{
  a: {
    b: {
    }
  },
  c: {
    b: {
    }
  }	
}		
"""
				);
		JsonValues v = JsonValues.of(o);
		v.find("a").rawForEach( j -> ((JsonObject)j).setReference("https://A") );
		v.find("b").rawForEach( j -> ((JsonObject)j).setReference("https://B") );
		v.find("c").rawForEach( j -> ((JsonObject)j).setReference("https://C") );
		
		List<String> l = new ArrayList<>();
		JsonReference.findReferences(o, (r,u) -> l.add(u) );
		
		assertArrayEquals(new Object[] {"https://A", "https://B", "https://C", "https://B"}, l.toArray());
	}
	
	
	private void checkResolve(String source, String expected) {
		checkResolve(source, expected, null);
	}
	private void checkResolve(String source, String expected, Map<String,String> externalDocs) {
		JsonObject jsonExpected = JsonObject.parse(expected);
		Object res = resolve(source, externalDocs);
		support.assertJsonEquals(jsonExpected, res);
	}
	
	private Object resolve(String source,  Map<String,String> externalDocs) {
		JsonObject jsonSource = JsonObject.parse(source);
		
		TestResolver resolver = new TestResolver(jsonSource,externalDocs);
		Object res = JsonReference.resolve(JsonFactory.get(), jsonSource, resolver, true);
		
		Console.log(JsonFactory.get().stringify(res,false));
		
		return res;
	}
}
