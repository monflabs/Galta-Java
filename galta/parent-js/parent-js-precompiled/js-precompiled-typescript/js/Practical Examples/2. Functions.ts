// 2.1 Optionl & Default param
const sum = (
  a: number,
  b: number,
  c?: string, // Optional param
  d: number = 0 // Default param
): number | string => {
  let result: string | number;
  result = a + b + d;
  if (c) {
    result = a + b + c;
  }
  return result;
};

// 2.2 Function Signature
type mathFunc = (a: number, b: number) => number;

const multiply: mathFunc = (num1, num2) => {
  return num1 * num2;
};

const subtract = (num1: number = 10, num2?: number) => {
  if (typeof num2 !== "undefined") {
    return num1 - num2;
  }
  return num1;
};

subtract(undefined, 5); // To avoid sending param use undefined

// 2.3 Rest param
const sumAll = (a: number, ...numbers: number[]): number => {
  return a + numbers.reduce((prev, curr) => prev + curr);
};

// 2.4 'never' return type
const errorMsg = (msg: string): never => {
  // By default return type is - never
  throw new Error(msg);
};

// 2.5 Custom type guard
const isNumber = (a: any): boolean => {
  return typeof a === "number" ? true : false;
};

// 2.6 Usage of 'never' return type
const showStr = (a: number | string): string => {
  if (typeof a === "number") return "Number";
  if (typeof a === "string") return "String";
  return errorMsg("This should not happen");
  // 'never' return type preventing to compile error as this func return type is 'string'
};
