const d = new Date(2020,8,27)

const JavaDate = Java.type("java.util.Date")
const d1 = new JavaDate(2020-1900,8,27)

assertEquals( d1.getTime(), d.getTime())
