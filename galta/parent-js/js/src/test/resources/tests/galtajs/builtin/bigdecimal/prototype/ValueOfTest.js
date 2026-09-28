const bigdec = 123456789123456789.456m;

assertEquals(123456789123456789.456m, bigdec.valueOf());
assertSame(bigdec, bigdec.valueOf());
