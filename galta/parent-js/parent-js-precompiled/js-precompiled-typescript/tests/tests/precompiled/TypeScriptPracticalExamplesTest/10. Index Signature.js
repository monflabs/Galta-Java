"use strict";
const riyadIncome = {
    salary: 2000,
    sidehustle: 100,
    passiveIncome: 50,
};
const prop = "salary";
// console.log(riyadIncome[prop]) - Error
for (const key in riyadIncome) {
    console.log(`${key}: ${riyadIncome[key]}`); // keyof Assertion (1st way)
}
const getValue = (obj, key) => {
    console.log(`${key}: ${obj[key]}`);
};
Object.keys(riyadIncome).map((key) => {
    console.log(riyadIncome[key]); // keyof Assertions (2nd way)
});
const riyadIncome2 = {
    salary: 2000,
    sidehustle: 100,
    passiveIncome: 50,
};
const prop2 = "salary";
console.log(riyadIncome2[prop2]);
console.log(riyadIncome2["hello"]); // Undefined
/* Similar to-
type Incomes = {
    salary: number;
    sidehustle: number;
    bonus: number;
} */
const monthlyIncomes = {
    salary: 500,
    bonus: 100,
    sidehustle: 250,
};
const prop3 = "bonus";
console.log(monthlyIncomes["bonus"]);
// console.log(monthlyIncomes[prop3]) Can't do that
for (const revenue in monthlyIncomes) {
    console.log(monthlyIncomes[revenue]);
}
