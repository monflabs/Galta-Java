const p1="P1", p2="P2"

const s1 = ``
const s2 = `str`
const s3 = `${p1}str`
const s4 = `str${p2}`
const s5 = `${p1}str${p2}`
const s6 = `A${p1}str${p2}`
const s7 = `${p1}str${p2}B`
const s8 = `A${p1}str${p2}B`
const s9 = `${p1}`
const s10 = `${p1}${p2}`
const s11 = `${p1}{`
const s12 = `${p1+'{'}`

const m1 = `s
z`

assertEquals("",s1);
assertEquals("str",s2);
assertEquals("P1str",s3);
assertEquals("strP2",s4);
assertEquals("P1strP2",s5);
assertEquals("AP1strP2",s6);
assertEquals("P1strP2B",s7);
assertEquals("AP1strP2B",s8);
assertEquals("P1",s9);
assertEquals("P1P2",s10);
assertEquals("P1{",s11);
assertEquals("P1{",s12);
assertEquals("s\nz",_n(m1));

assertEquals("",``);
assertEquals("str",`str`);
assertEquals("P1str",`${p1}str`);
assertEquals("strP2",`str${p2}`);
assertEquals("P1strP2",`${p1}str${p2}`);
assertEquals("AP1strP2",`A${p1}str${p2}`);
assertEquals("P1strP2B",`${p1}str${p2}B`);
assertEquals("AP1strP2B",`A${p1}str${p2}B`);
assertEquals("P1",`${p1}`);
assertEquals("P1P2",`${p1}${p2}`);
assertEquals("P1{",`${p1}{`);
assertEquals("P1{",`${p1+'{'}`);


assertEquals("a=1",`a=${1}`);
assertEquals("a=3",`a=${1+2}`);

assertEquals("a=true",`a=${true}`);
assertEquals("a=str",`a=${"str"}`);

assertEquals("\tSTR\t",`\tSTR\t`);
assertEquals("\tSTR\t",`\t${"STR"}\t`);
assertEquals("\tSTR\t",`\t${'STR'}\t`);
assertEquals("\tS\tTR\t",`\t${'S\tTR'}\t`);

assertEquals("a=$1",`a=$${1}`);


function t1(strings, ...values) {
  let a = new Array();
  a = a.concat(strings)
  a = a.concat(values)
  return "=>"+a.join(',')
}
assertEquals("=>ABC",t1`ABC`);
assertEquals("=>A,BC,0",t1`A${0}BC`);
assertEquals("=>A,B,C,0,1",t1`A${0}B${1}C`);


// From: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Template_literals#tagged_templates

//
// #1
//
const person = "Mike";
const age = 28;

function myTag(strings, personExp, ageExp) {
  const str0 = strings[0]; // "That "
  const str1 = strings[1]; // " is a "
  const str2 = strings[2]; // "."

  const ageStr = ageExp > 99 ? "centenarian" : "youngster";

  // We can even return a string built using a template literal
  return `${str0}${personExp}${str1}${ageStr}${str2}`;
}

const output = myTag`That ${person} is a ${age}.`;
assertEquals("That Mike is a youngster.", output)

const output2 = myTag`That ${'Joe'} is a ${102}.`;
assertEquals("That Joe is a centenarian.", output2)


//
// #2
//
function template(strings, ...keys) {
  return (...values) => {
    const dict = values[values.length - 1] || {};
    const result = [strings[0]];
    keys.forEach((key, i) => {
      const value = Number.isInteger(key) ? values[key] : dict[key];
      result.push(value, strings[i + 1]);
    });
    return result.join("");
  };
}

const t1Closure = template`${0}${1}${0}!`;
// const t1Closure = template(["","","","!"],0,1,0);
assertEquals("YAY!", t1Closure("Y", "A") )

const t2Closure = template`${0} ${"foo"}!`;
// const t2Closure = template([""," ","!"],0,"foo");
assertEquals("Hello World!", t2Closure("Hello", { foo: "World" })  )

const t3Closure = template`I'm ${"name"}. I'm almost ${"age"} years old.`;
// const t3Closure = template(["I'm ", ". I'm almost ", " years old."], "name", "age");
assertEquals("I'm MDN. I'm almost 30 years old.", t3Closure("foo", { name: "MDN", age: 30 }) )
assertEquals("I'm MDN. I'm almost 30 years old.", t3Closure({ name: "MDN", age: 30 }) )


//
// Test raw string
function raw(strings, ...substitutions) { // Polyfill for String.raw
    let result = '';
    for (let i = 0; i < strings.length; i++) {
      result += strings.raw[i];
      if (i < substitutions.length) {
        result += substitutions[i];
      }
    }
    return result;
}
assertEquals("Hello\\nWorld!", raw`Hello\nWorld!` )

const rawhost = {raw}
assertEquals("Hello\\nWorld!", rawhost.raw`Hello\nWorld!` )

// String.raw
assertEquals("Hello\\nWorld!", String.raw`Hello\nWorld!`  )


// Even more tests
const firstName = "John", lastName = "Doe";
const a=3, b=4, c=5, d=6;
assertEquals("Hello World", `Hello World` );
assertEquals("Hello John Doe", `Hello ${firstName} ${lastName}` );
assertEquals("The sum is 7 and the product is 12", `The sum is ${a + b} and the product is ${a * b}` );
assertEquals("Status: Active", `Status: ${true ? 'Active' : 'Inactive'}` );
assertEquals("Level 1: Level 2: Level 3: John", `Level 1: ${`Level 2: ${true?`Level 3: ${firstName}`:`Aa` }`}` );
assertEquals("Outer: Middle: Inner: -7", `Outer: ${`Middle: ${`Inner: ${(a + b) * (c - d)}`}`}` )


//
// Chained tagged templates (each tag call's return value becomes the next tag)
//
{
  let callCount = 0;
  const expected = ['x', 'y', 'z'];
  const chainTag = function(strings) {
    assertEquals(expected[callCount], strings[0]);
    callCount++;
    return chainTag;
  };
  const chainResult = chainTag`x``y``z`;
  assertEquals(3, callCount);
  assertSame(chainTag, chainResult);
}

//
// Tagged template "this" binding: a plain (non-member) tag gets undefined "this";
// a tag reached through a member access gets the base object as "this".
//
{
  let plainThis = "unset";
  function plainTag() { plainThis = this; return "";  }
  plainTag``;
  assertUndefined(plainThis);

  const obj = {
    seenThis: "unset",
    method() { obj.seenThis = this; return ""; }
  };
  obj.method``;
  assertSame(obj, obj.seenThis);
}

//
// `new` binds tighter than a tagged template call: `new tag`x`` is `new (tag`x`)`.
//
{
  function Ctor(x) { this.arg = x; }
  let seenTemplateObject = null;
  const ctorTag = function(strings) {
    seenTemplateObject = strings;
    return Ctor;
  };
  const instance1 = new ctorTag`first`;
  assertTrue(instance1 instanceof Ctor);
  assertEquals("first", seenTemplateObject[0]);
  assertUndefined(instance1.arg);

  const instance2 = new ctorTag`second`('ctor-arg');
  assertEquals("second", seenTemplateObject[0]);
  assertEquals("ctor-arg", instance2.arg);
}

//
// Invalid escape sequences are legal in tagged templates: the cooked value for that
// chunk is undefined, but the raw text is preserved. In untagged templates the same
// sequences are a SyntaxError.
//
{
  const escTag = (strings) => strings;
  assertUndefined(escTag`\01`[0]);
  assertEquals("\\01", escTag`\01`.raw[0]);
  assertUndefined(escTag`\9`[0]);
  assertEquals("\\9", escTag`\9`.raw[0]);
  assertUndefined(escTag`\xg`[0]);
  assertEquals("\\xg", escTag`\xg`.raw[0]);
  assertUndefined(escTag`\u{110000}`[0]);
  // Lone \0 (not followed by a digit) is still a legal NUL escape in templates.
  assertEquals(String.fromCharCode(0) + "ok", escTag`\0ok`[0]);

  assertParseError("`\\01`");
  assertParseError("`\\9`");
}

//
// The template object (and its "raw" array) passed to a tag is frozen.
//
{
  const frozenTag = (strings) => strings;
  const templateObject = frozenTag`a${1}b`;
  assertTrue(Object.isFrozen(templateObject));
  assertTrue(Object.isFrozen(templateObject.raw));
  assertThrows(TypeError, () => { templateObject[0] = "changed"; });
  assertThrows(TypeError, () => { templateObject.raw[0] = "changed"; });
  assertThrows(TypeError, () => { templateObject.extra = true; });
}

//
// A template literal containing an escaped backslash (\\) must not corrupt
// lexing of a LATER, separate template literal elsewhere in the same script -
// the lexer's catch-all character class must exclude "\\" so it's only ever
// matched through the dedicated escape-sequence path.
//
{
  const a = `\\`;
  const b = `\b`;
  assertEquals(1, a.length);
  assertEquals(1, b.length);
}



//
// Unescaped line terminators inside a template literal are all normalized to
// LF in the cooked value - CRLF and lone CR alike.
//
{
  const s = `

`;
  assertEquals(3, s.length);
  assertEquals("\n\n\n", s);
}



//
// A substitution inside a function EXPRESSION (method shorthand, function
// expression, arrow, nested declaration) nested in another function must
// resolve its identifiers against that inner function's own frame - not one
// hop too far, into the enclosing function's slot array (a silently wrong
// value when that array happens to be long enough, an IndexOutOfBounds when
// it isn't - test262 built-ins/Iterator/zip*/*-iteration.js).
//
{
  function outerNoParams() {
    return { m(k) { return `${k}`; } };
  }
  assertEquals("z", outerNoParams().m("z"));

  function outerTwoParams(a, b) {
    return { m(x, k) { return `${k}`; } };
  }
  assertEquals("z", outerTwoParams(1, 2).m("q", "z"));

  function fnExpr() {
    return { m: function(k) { return `${String(k)}`; } };
  }
  assertEquals("z", fnExpr().m("z"));

  function arrow() {
    return (k) => `${k}`;
  }
  assertEquals("z", arrow()("z"));

  function nestedDecl() {
    function g(k) { return `${k}`; }
    return g;
  }
  assertEquals("z", nestedDecl()("z"));

  function withLocal() {
    return { m(k) { var y = k; return `${y}`; } };
  }
  assertEquals("z", withLocal().m("z"));

  const o = { outer() { return { m(k) { return `${k}`; } }; } };
  assertEquals("z", o.outer().m("z"));

  // and an outer-frame reference from inside the nested function still works
  function closure(name) {
    return { m(k) { return `${name}::${k}`; } };
  }
  assertEquals("n::z", closure("n").m("z"));
}
