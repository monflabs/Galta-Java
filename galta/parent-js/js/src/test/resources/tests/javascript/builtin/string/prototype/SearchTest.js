const paragraph = "I think Ruth's dog is cuter than your dog!";

const regex = /[^\w\s']/g;
assertEquals( 41, paragraph.search(regex));

const regex2 = /TOTO/g;
assertEquals( 4, "ABC TOTO DEF".search(regex2));
assertEquals( -1, "ABC TATA DEF".search(regex2));

// A non-RegExp object with a Symbol.search method must be dispatched to
// generically (not just genuine RegExp instances).
const Searcher1 = {
	[Symbol.search]: function(string) {
		return string.indexOf('dog');
	}
}
assertEquals(15, paragraph.search(Searcher1))
