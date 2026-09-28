"use strict";
let myObj = {};
myObj = anArr;
myObj = {
    name: "Riyad",
    age: 22,
};
const alAmin = {
    name: "Jobayer",
    age: 26,
    premeum: true,
};
/*
We also can use 'interface' instead of 'type'. Syntax: interface Person {} interface is more preferrable on object & class.
*/
let jobayer = {
    name: "Jobayer",
    age: 26,
    premeum: true,
};
let sadik = {
    name: "Sadik",
    age: 24,
};
/* type/interface in Function */
const greetPerson = (personObj) => {
    return `Hello ${personObj.name}`;
};
const personIsPremium = (personObj) => {
    let result;
    /*
      X - result = personObj.premeum.toString()
      'personObj.premeum' is possibly 'undefined'
    */
    result = personObj.name + personObj.premeum;
    return result;
};
