var r = 1
assertEquals(1,r)

!(function () {
	r = 3;
}())
assertEquals(3,r)
