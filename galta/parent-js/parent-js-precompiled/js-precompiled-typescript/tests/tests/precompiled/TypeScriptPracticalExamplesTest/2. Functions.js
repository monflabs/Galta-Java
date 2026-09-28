"use strict";
// 2.1 Optionl & Default param
const sum = (a, b, c, // Optional param
d = 0 // Default param
) => {
    let result;
    result = a + b + d;
    if (c) {
        result = a + b + c;
    }
    return result;
};
const multiply = (num1, num2) => {
    return num1 * num2;
};
const subtract = (num1 = 10, num2) => {
    if (typeof num2 !== "undefined") {
        return num1 - num2;
    }
    return num1;
};
subtract(undefined, 5); // To avoid sending param use undefined
// 2.3 Rest param
const sumAll = (a, ...numbers) => {
    return a + numbers.reduce((prev, curr) => prev + curr);
};
// 2.4 'never' return type
const errorMsg = (msg) => {
    // By default return type is - never
    throw new Error(msg);
};
// 2.5 Custom type guard
const isNumber = (a) => {
    return typeof a === "number" ? true : false;
};
// 2.6 Usage of 'never' return type
const showStr = (a) => {
    if (typeof a === "number")
        return "Number";
    if (typeof a === "string")
        return "String";
    return errorMsg("This should not happen");
    // 'never' return type preventing to compile error as this func return type is 'string'
};
