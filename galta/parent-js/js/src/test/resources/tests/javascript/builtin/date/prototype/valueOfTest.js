const JavaDate = Java.type("java.util.Date");

const unixTimeZero = new Date('01 Jan 1970 00:00:00 GMT');
assertEquals( 0, unixTimeZero.valueOf() )

const javaScriptRelease = new Date('04 Dec 1995 00:12:00 GMT');
assertEquals( JavaDate.parse('04 Dec 1995 00:12:00 GMT'), javaScriptRelease.valueOf() )
