// 10.1 Index Signature & keyof Assertion______________
interface income {
  salary: number;
  sidehustle: number;
  passiveIncome: number;
}

const riyadIncome: income = {
  salary: 2000,
  sidehustle: 100,
  passiveIncome: 50,
};

const prop: string = "salary";
// console.log(riyadIncome[prop]) - Error

for (const key in riyadIncome) {
  console.log(`${key}: ${riyadIncome[key as keyof income]}`); // keyof Assertion (1st way)
}

const getValue = (obj: income, key: keyof income) => {
  console.log(`${key}: ${obj[key]}`);
};

Object.keys(riyadIncome).map((key) => {
  console.log(riyadIncome[key as keyof typeof riyadIncome]); // keyof Assertions (2nd way)
});

interface income2 {
  [index: string]: number; // To access dynamically
  salary: number;
  sidehustle: number;
  passiveIncome: number;
}

const riyadIncome2: income2 = {
  salary: 2000,
  sidehustle: 100,
  passiveIncome: 50,
};

const prop2: string = "salary";
console.log(riyadIncome2[prop2]);
console.log(riyadIncome2["hello"]); // Undefined

// 10.2 Record______________

// interface Incomes {
//     [key: string]: number
// }

type Streams = "salary" | "bonus" | "sidehustle";

type Incomes = Record<Streams, number>;
/* Similar to-
type Incomes = {
    salary: number;
    sidehustle: number;
    bonus: number;
} */

const monthlyIncomes: Incomes = {
  salary: 500,
  bonus: 100,
  sidehustle: 250,
};

const prop3: string = "bonus";
console.log(monthlyIncomes["bonus"]);
// console.log(monthlyIncomes[prop3]) Can't do that

for (const revenue in monthlyIncomes) {
  console.log(monthlyIncomes[revenue as keyof Incomes]);
}
