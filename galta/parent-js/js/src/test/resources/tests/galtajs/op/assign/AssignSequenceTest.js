const v = [
	{ a: 10, b:20},
	{ c: 22 },
	{ a: 11, b:21},
]

const v1 = jsonDeepClone(v)
const r1 = v1.*.a = 33
assertEquals([{a:33, b:20},{a:33, c:22},{a:33, b:21}], v1);

const v2 = jsonDeepClone(v)
const r2 = v2.*.a += 5
assertEquals([{a:15, b:20},{a:NaN, c:22},{a:16, b:21}], v2);

const v3 = jsonDeepClone(v)
const r3 = v3..a += 50
assertEquals([{a:60, b:20},{c:22},{a:61, b:21}], v3);

const v4 = jsonDeepClone(v)
const r4 =v4[?(@.b==21)].a += 40
assertEquals([{a:10, b:20},{c:22},{a:51, b:21}], v4);
