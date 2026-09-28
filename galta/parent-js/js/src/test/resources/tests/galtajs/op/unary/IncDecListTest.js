let b1 = [ {a: 10}, {b: 20}, {a: 30} ]
assertEquals( [10,NaN,30], b1.*.a++ );
assertEquals( [{a:11},{a:NaN,b:20},{a:31}], b1 );

let b2 = [ {a: 10}, {b: 20}, {a: 30} ]
assertEquals( [11,NaN,31], ++b2.*.a );
assertEquals( [{a:11},{a:NaN,b:20},{a:31}], b2 );

let b3 = [ {a: 10}, {b: 20}, {a: 30} ]
assertEquals( [9,NaN,29], --b3.*.a );
assertEquals( [{a:9},{a:NaN,b:20},{a:29}], b3 );

let b4 = [ {a: 10}, {b: 20}, {a: 30} ]
assertEquals( [10,NaN,30], b4.*.a-- );
assertEquals( [{a:9},{a:NaN,b:20},{a:29}], b4 );

let b5 = [ {a: 10}, {b: 20}, {a: 30} ]
assertEquals( [10,30], b5..a-- );
assertEquals( [{a:9},{b:20},{a:29}], b5 );

let b6 = [ {a: 10}, {b: 20}, {a: 30} ]
assertEquals( 10, b6[?(@.a==10)].a-- );
assertEquals( [{a:9},{b:20},{a:30}], b6 );
