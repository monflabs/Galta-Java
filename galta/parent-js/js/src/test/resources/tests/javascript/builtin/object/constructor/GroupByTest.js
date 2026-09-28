const inventory = [
  {name: 'apples', type: 'vegetables', quantity: 5},
  {name: 'bananas',  type: 'fruit', quantity: 0},
  {name: 'goat', type: 'meat', quantity: 23},
  {name: 'cherries', type: 'fruit', quantity: 5},
  {name: 'fish', type: 'meat', quantity: 22}
];

const expected = {
	vegetables: [ 
		{ name: "apples", type: "vegetables", quantity: 5 } 
	],
	fruit: [
		{ name: "bananas", type: "fruit", quantity: 0 },
		{ name: "cherries", type: "fruit", quantity: 5 }
	], 
	meat: [
		{ name: "goat", type: "meat", quantity: 23 },
		{ name: "fish", type: "meat", quantity: 22 }
	] 
}

assertEquals( expected, Object.groupBy( inventory, (o) => o.type ) );

Object.groupBy([10,22], function (v,i) {
	assertEquals(i==0?10:22,v);
});
