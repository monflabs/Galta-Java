const a = []

assertEquals(NaN, (a[1] +=2) );
assertEquals(NaN, (a[2] -=2) );
assertEquals(NaN, (a[3] *=2) );
assertEquals(NaN, (a[4] /=2) );
assertEquals(NaN, (a[5] %=2) );

assertEquals(0, (a[6] &=1) );
assertEquals(1, (a[7] |=1) );
assertEquals(1, (a[8] ^=1) );

assertEquals(undefined, (a[9] &&=true) );
assertEquals(true, (a[10] ||=true) );

assertEquals(true, (a[11] ??=true) );
assertEquals(false, (a[12] ??=false) );

assertEquals(NaN, (a[13]++) );
assertEquals(NaN, (a[14]--) );
assertEquals(NaN, (++a[15]) );
assertEquals(NaN, (--a[16]) );
