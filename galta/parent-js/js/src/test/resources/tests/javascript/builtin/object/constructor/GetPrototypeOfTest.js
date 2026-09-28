const o1 = Object.getPrototypeOf({});
const o2 = Object.getPrototypeOf({});
assertNotNull( o1 );
assertSame( o1, o2 );

