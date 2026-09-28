const o = [
	{a: 1, b:2},
	{a: 1, c:3},
	{b: 2, c:3},
]

// This changes the value of 'a' every where
o..a = 20
console.log("Make a=20\n",o,"\n")

// This sets a value 'a' to every objects, including the ones that don't have this property yet
o.*.a = 10
console.log("Make a=10\n",o,"\n")

// Doubles tha value of b where it exists
o..b *= 2
console.log("Double b\n",o,"\n")
