function sampleFunction() {
	assertSame( sampleFunction, arguments.callee )
}

sampleFunction();
