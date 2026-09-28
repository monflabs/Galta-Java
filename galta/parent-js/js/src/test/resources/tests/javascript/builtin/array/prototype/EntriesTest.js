const a = ['a', 'b', 'c'];
const it = a.entries();

assertEquals({value: [0,'a'], done: false},it.next())
assertEquals({value: [1,'b'], done: false},it.next())
assertEquals({value: [2,'c'], done: false},it.next())
assertEquals({value: undefined, done: true},it.next())
assertEquals({value: undefined, done: true},it.next())
