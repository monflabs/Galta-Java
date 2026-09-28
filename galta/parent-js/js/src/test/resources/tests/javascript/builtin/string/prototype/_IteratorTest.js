let s = "";
for (let key of "ABC"[Symbol.iterator]()) {
	s = s + key.toLowerCase() +";"
}
assertEquals("a;b;c;",s)

// Strings iterate by Unicode code point, not UTF-16 code unit - a surrogate
// pair must be yielded together as one step.
let astral = "a𝌆b"; // "a" + MUSICAL SYMBOL G CLEF (U+1D306) + "b"
let parts = [];
for (let ch of astral[Symbol.iterator]()) {
	parts.push(ch);
}
assertEquals(3, parts.length);
assertEquals("a", parts[0]);
assertEquals("𝌆", parts[1]);
assertEquals("b", parts[2]);
