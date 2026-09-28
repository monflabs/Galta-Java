// Support for Java number types with specific suffixes

const n_int = 16;
console.log(n_int, typeof n_int,n_int.$getClass().getName())
const n_int2 = 4_294_967_296;
console.log(n_int2, typeof n_int2,n_int2.$getClass().getName())

const n_long = 16L;
console.log(n_long, typeof n_long,n_long.$getClass().getName())
const n_long2 = 4_611_686_018_427_387_904L;
console.log(n_long2, typeof n_long2,n_long2.$getClass().getName())
const n_float = 16.0f;
console.log(n_float, typeof n_float,n_float.$getClass().getName())

const n_double = 16.0;
console.log(n_double, typeof n_double,n_double.$getClass().getName())

const n_bigint = 16n;
console.log(n_bigint, typeof n_bigint,n_bigint.$getClass().getName())

const n_bigdecimal = 16m;
console.log(n_bigdecimal, typeof n_bigdecimal,n_bigdecimal.$getClass().getName() )
