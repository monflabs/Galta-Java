const paragraph = 'The quick brown fox jumps over the lazy dog. It barked.';
assertEquals(["T", "I"], paragraph.match(/[A-Z]/g))
const pr = paragraph.match("[A-Z]")
assertEquals(["T"], pr)
assertEquals(1, pr.length)
assertEquals("T", pr[0])
assertEquals(0, pr.index)
assertEquals("The quick brown fox jumps over the lazy dog. It barked.", pr.input)
//assertEquals("[\"T\", index: 0, input: \"The quick brown fox jumps over the lazy dog. It barked.\"]", paragraph.match("[A-Z]") )

const str = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz';
assertEquals(['A', 'B', 'C', 'D', 'E', 'a', 'b', 'c', 'd', 'e'], str.match(/[A-E]/gi))
const sr = str.match("[A-E]")
assertEquals(["A"], sr)
assertEquals(1, sr.length)
assertEquals("A", sr[0])
assertEquals(0, sr.index)
assertEquals("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz", sr.input)
//assertEquals("[\"A\", index: 0, input: \"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz\"]", str.match("[A-E]"))

// A non-RegExp object with a Symbol.match method must be dispatched to
// generically (not just genuine RegExp instances).
const Matcher1 = {
	[Symbol.match]: function(string) {
		return `matched:${string}`;
	}
}
assertEquals('matched:hello', 'hello'.match(Matcher1))
