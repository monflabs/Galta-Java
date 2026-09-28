// List actually maps to Java ArrayList

const en = Java.type("tests.classes.EnumAccess")

assertEquals( 1, en.VAL1.getVal() );
assertEquals( 2, en.VAL2.getVal() );
