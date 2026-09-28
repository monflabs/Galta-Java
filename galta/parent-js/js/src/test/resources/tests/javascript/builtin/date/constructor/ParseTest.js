const JavaDate = Java.type("java.util.Date");

const unixTimeZero = Date.parse('01 Jan 1970 00:00:00 GMT');
assertEquals( 0, unixTimeZero )

const javaScriptRelease = Date.parse('04 Dec 1995 00:12:00 GMT');
assertEquals( JavaDate.parse('04 Dec 1995 00:12:00 GMT'), javaScriptRelease )

const ISO1 = Date.parse('2020-10-23T08:24:36Z');
assertEquals( JavaDate.parse('23 Oct 2020 08:24:36 GMT'), ISO1 )

const ISO2 = Date.parse('2020-10-23T08:24:36-08:00');
assertEquals( JavaDate.parse('23 Oct 2020 08:24:36 PST'), ISO2 )

const inv1 = Date.parse('');
assertEquals( NaN, inv1 )

const inv2 = Date.parse('caca boundin');
assertEquals( NaN, inv2 )
