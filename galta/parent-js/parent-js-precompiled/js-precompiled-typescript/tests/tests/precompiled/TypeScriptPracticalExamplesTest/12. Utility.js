"use strict";
const personalInfo = { name: "Riyad", age: 22 };
const updateStInfo = (student, newInfo) => {
    return { ...student, ...newInfo };
};
const sadikInfo = {
    name: "Sadik",
    roll: 22,
    age: 24,
};
const sadikUpdated = updateStInfo(sadikInfo, { dept: "CSE" });
// 12.2 Required & Readonly
const storeStudent = (student) => {
    return student;
};
const jkInfo = {
    name: "Sadik",
    roll: 22,
    age: 24,
};
const jk = storeStudent({ ...jkInfo, dept: "CSE" }); // For Required- have to pass optional prop
const jkAge = jk.age;
// jk.age = 22 - Cannot assign to 'age' because it is a read-only property
// 12.3 Record
const income = {
    sadik: 15000,
    jk: 20000,
    shorif: 50000,
};
const superHeroes = {
    Superman: "much-power",
    Spiderman: "spider-net",
    Hulk: "much-power",
};
const student1 = { name: "someone", age: 23 };
const student2 = { name: "someone", dept: 'sth' };
// 12.7 ReturnType
const createNewAssign = (title, points) => {
    return { title, points };
};
const tsAssign = createNewAssign("Utility Types", 100);
const assignArgs = ["Generics", 100];
const tsAssign2 = createNewAssign(...assignArgs);
const fetchUsers = async () => {
    const data = await fetch('https://jsonplaceholder.typicode.com/users').then(res => {
        return res.json();
    }).catch(err => {
        if (err instanceof Error)
            console.log(err.message);
    });
    return data;
};
fetchUsers().then(users => console.log(users));
