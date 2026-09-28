const map3 = new Map([
  [1, "one"],
  [2, "two"],
  [3, "three"],
]);

const a = []
for(const v of map3) {
	a.push(v)
}

assertEquals([[1,"one"],[2,"two"],[3,"three"]],a);
