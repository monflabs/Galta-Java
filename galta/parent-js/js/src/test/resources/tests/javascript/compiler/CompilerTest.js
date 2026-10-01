// A failing assertParse()/assertParseError() is uncatchable from JavaScript,
// its failing path is covered by EngineRegression3JavaTest
assertParse("");
assertParse("a=1");
assertParseError("a ab");
assertParseError("a=");
