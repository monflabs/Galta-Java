const i = 4; // int
const l = 4L; // long
const f = 4.0f; // float
const d = 4.0; // double
const bi = 4n; // Big Integer
const bd = 4.0m; // Big Decimal
(typeof i) + ": " + i.$getClass().getName();
(typeof l) + ": " + l.$getClass().getName();
(typeof f) + ": " + f.$getClass().getName();
(typeof d) + ": " + d.$getClass().getName();
(typeof bi) + ": " + bi.$getClass().getName();
(typeof bd) + ": " + bd.$getClass().getName();


const s = "Hello World"; // String
(typeof s) + ": " + s.$getClass().getName();


const b = true; // boolean
(typeof b) + ": " + b.$getClass().getName();
