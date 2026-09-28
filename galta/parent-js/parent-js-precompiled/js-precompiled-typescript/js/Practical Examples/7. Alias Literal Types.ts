// 7.1 Type Alias
type numberOrString = number | string;
type stringOrNumArr = (string | number)[];

let job_id: numberOrString;

type Programmer = {
  name: string;
  age: number;
  devices: stringOrNumArr; // Can be used in another alias
};

// 7.2 Literal Type
let myName: "Riyad";
let userName: "Riyad" | "Jobayer" | "Sadik" | "Al-amin";
userName = "Jobayer";
