const set3 = new Set([1, 3, 9]);

const a = []
for(const v of set3) {
	a.push(v)
}

assertEquals([1,3,9],a);
