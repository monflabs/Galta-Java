

// Test object as a thisArg
var thisArg = {
    elementAt: function (key) {
        return this[key];
    }
};
Array.prototype.push.apply(thisArg, ["c", "b"]);

found = ["a", "b", "c"].findLast(function (val, key) {
	return this[key] === val;
    //return this.elementAt(key) === val;
}, thisArg);
assertEquals("b", found);
