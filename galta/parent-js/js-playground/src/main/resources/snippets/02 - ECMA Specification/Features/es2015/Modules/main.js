//Import Statement

// 1. Import an entire module's contents
import * as name from "./my-module";
console.log(name.add(1,2)); // 3

//2.Import a single export from a module
import { add } from "./my-module";
console.log(add(2,3)); // 5

//3.Import multiple exports from a module
import { multiply , PI } from "./my-module";
console.log(multiply(2,PI)); // 6.28...

//4.Import default export from a module
import add2 from "./my-module";
console.log(add2(6)); // 8

//5.Import an export with an alias
import { add as addValues } from "./my-module";
console.log(addValues(4,5)); // 9
