let myObj = {};
myObj = anArr;

myObj = {
  name: "Riyad",
  age: 22,
};

const alAmin: {
  name: string;
  age: number;
  premeum?: boolean;
} = {
  name: "Jobayer",
  age: 26,
  premeum: true,
};

type Person = {
  name: string;
  age: number;
  premeum?: boolean;
};

/*
We also can use 'interface' instead of 'type'. Syntax: interface Person {} interface is more preferrable on object & class.
*/

let jobayer: Person = {
  name: "Jobayer",
  age: 26,
  premeum: true,
};

let sadik: Person = {
  name: "Sadik",
  age: 24,
};

/* type/interface in Function */
const greetPerson = (personObj: Person): string => {
  return `Hello ${personObj.name}`;
};

const personIsPremium = (personObj: Person): string => {
  let result: string;
  /*
    X - result = personObj.premeum.toString()
    'personObj.premeum' is possibly 'undefined'
  */

  result = personObj.name + personObj.premeum;
  return result;
};
