const sym = Symbol('symb')
const obj = {
  get foo() {},
  set foo(v) {},
  get [sym]() {},
  set [sym](v) {}
};

assertEquals( "get foo", obj.__lookupGetter__("foo").name );
assertEquals( "set foo", obj.__lookupSetter__("foo").name );
assertEquals( "get [symb]", obj.__lookupGetter__(sym).name );
assertEquals( "set [symb]", obj.__lookupSetter__(sym).name );


const obj2 = {}
Object.defineProperty(obj2, "foo", {
  get() {},
  set() {}
});
assertEquals( "get", Object.getOwnPropertyDescriptor(obj2, "foo").get.name );
assertEquals( "set", Object.getOwnPropertyDescriptor(obj2, "foo").set.name );

Object.defineProperty(obj, "baz", {
  set: function mySetter(v) {}
});

assertEquals( "mySetter", Object.getOwnPropertyDescriptor(obj, "baz").set.name );


class C {
  get value() {}
  set value(v) {}
}

assertEquals( "get value", Object.getOwnPropertyDescriptor(C.prototype, "value").get.name);
assertEquals( "set value", Object.getOwnPropertyDescriptor(C.prototype, "value").set.name);