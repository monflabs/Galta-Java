assertParse("");
assertParse("a=1");
try {
	assertParse("a ab");
	fail();
} catch(e) {}

try {
	assertParseError("a=1");
	fail();
} catch(e) {}
assertParseError("a ab");
