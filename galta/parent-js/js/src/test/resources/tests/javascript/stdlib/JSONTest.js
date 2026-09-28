//
// Parsing
//
const json = '{"result":true, "count":42}';
const obj = JSON.parse(json);

assertEquals(true, obj.result );
assertEquals(42, obj.count );

// Reviver
const rev1 = JSON.parse(
  '{"p": 5}',
  (key, value) =>
    typeof value === "number"
      ? value * 2 // return value * 2 for numbers
      : value, // return everything else unchanged
);
assertEquals( {p: 10}, rev1)
const rev2 = JSON.parse(
  '{"p": 5}',
  456
);
assertEquals( {p: 5}, rev2)
JSON.parse(
  '{"p": 123.4500}',
  (k,v,ctx) => {
	if(k) { // skip root
		assertEquals("p",k)
		assertEquals(123.45,v)
		assertEquals("123.4500",ctx.source)
	}
  }
);
JSON.parse(
  '{"p": "xyz"}',
  (k,v,ctx) => {
	if(k) { // skip root
		assertEquals("p",k)
		assertEquals("xyz",v)
		// context.source (json-parse-with-source proposal) is the exact raw
		// source text of the value, including the surrounding quotes - not
		// the decoded string value.
		assertEquals('"xyz"',ctx.source)
	}
  }
);




//
// Stringifying
//
const sobj = {'count':44};
assertEquals("{\"count\":44}", JSON.stringify(sobj));
assertEquals("{\n  \"count\": 44\n}", JSON.stringify(sobj,null,"  "));

// Invalid value
assertEquals( "[4,null,6]", JSON.stringify([4,undefined,6]) )
assertEquals( "[4,null,6]", JSON.stringify([4,function(){},6]) )
assertEquals( "[4,null,6]", JSON.stringify([4,() => 123,6]) )
assertEquals( "[4,null,6]", JSON.stringify([4,Symbol,6]) )
assertEquals( "[4,null,6]", JSON.stringify([4,Symbol(),6]) )

assertEquals( "{\"a\":1}", JSON.stringify({a:1,b:undefined}) )
assertEquals( "{\"a\":1}", JSON.stringify({a:1,b:function(){}}) )
assertEquals( "{\"a\":1}", JSON.stringify({a:1,b:() => 123} ) )
assertEquals( "{\"a\":1}", JSON.stringify({a:1,b:Symbol} ) )
assertEquals( "{\"a\":1}", JSON.stringify({a:1,b:Symbol()} ) )

const js1 = { a:1, toJSON() {return {a:3} } }
assertEquals( "{\"a\":3}", JSON.stringify(js1) )

// replacer
assertEquals( "{\"a\":1,\"c\":3}", JSON.stringify({a:1, b:2, c:3}, ["a", "c"]))
assertEquals( "{\"b\":2}", JSON.stringify({a:1, b:2, c:3}, (k,v) => !k || k=='b'?v:undefined ) )
// A replacer returning `undefined` for an array element becomes JSON
// `null` (like line 60's Symbol case), not an omitted element - arrays
// can't have holes in JSON output, unlike an object property.
assertEquals( "[null,null,6]", JSON.stringify([4,5,6], (k,v) => !k || k=='2'?v:undefined ) )
assertEquals( "[4,null,null]", JSON.stringify([4,5,6], (k,v) => !k || k=='0'?v:undefined ) )

// Circular ref
const a = {}
const b = {a}
a.b = b
assertThrows( () => JSON.stringify(a) )
assertThrows( () => JSON.stringify(b) )

// non-enumerable property is skipped
const pp1 = {};
Object.defineProperty(pp1, "hidden", {
  value: 42,
  enumerable: false
});
pp1.visible = 1;
assertEquals( "{\"visible\":1}", JSON.stringify(pp1) )

// Prototype properties are skipped
const proto = { inherited: 2 };
const pp2 = Object.create(proto);
pp2.visible = 1;
assertEquals( "{\"visible\":1}", JSON.stringify(pp2) )

// Symbol keys are skipped
const sym = Symbol("s");
const pp3 = { [sym]: 2 };
pp3.visible = 1;
assertEquals( "{\"visible\":1}", JSON.stringify(pp3) )
