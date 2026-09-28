// 12.1 Partial
interface student {
  name: string;
  roll: number;
  age: number;
  dept?: string;
}

const personalInfo: Partial<student> = { name: "Riyad", age: 22 };

const updateStInfo = (student: student, newInfo: Partial<student>): student => {
  return { ...student, ...newInfo };
};

const sadikInfo: student = {
  name: "Sadik",
  roll: 22,
  age: 24,
};

const sadikUpdated = updateStInfo(sadikInfo, { dept: "CSE" });

// 12.2 Required & Readonly
const storeStudent = (student: Required<student>): student => {
  return student;
};

const jkInfo: student = {
  name: "Sadik",
  roll: 22,
  age: 24,
};

const jk: Readonly<student> = storeStudent({ ...jkInfo, dept: "CSE" }); // For Required- have to pass optional prop
const jkAge = jk.age;
// jk.age = 22 - Cannot assign to 'age' because it is a read-only property

// 12.3 Record
const income: Record<string, number> = {
  sadik: 15000,
  jk: 20000,
  shorif: 50000,
};

type Hero = "Superman" | "Spiderman" | "Hulk";
type Power = "spider-net" | "stronger" | "much-power";

const superHeroes: Record<Hero, Power> = {
  Superman: "much-power",
  Spiderman: "spider-net",
  Hulk: "much-power",
};

// 12.4 Pick & Omit
type personalInfo = Pick<student, "name" | "age">;
type dept = Omit<student, "age" | "roll">;

const student1: personalInfo = { name: "someone", age: 23 };
const student2: dept = { name: "someone", dept: 'sth' };

// 12.5 Exclude & Extract
type serial = Exclude<Hero, "age">
type age = Extract<Power, "age" | "dept">

// 12.6 NonNullable
type AllPossibleGrades = 'Dave' | 'John' | null | undefined
type NamesOnly = NonNullable<AllPossibleGrades>

// 12.7 ReturnType
const createNewAssign = (title: string, points: number) => {
    return { title, points }
}

type NewAssign = ReturnType<typeof createNewAssign> // can define types with the help of another func

const tsAssign: NewAssign = createNewAssign("Utility Types", 100)

// 12.8 Parameters
type paramType = Parameters<typeof createNewAssign> // type paramType = [title: string, points: number]

const assignArgs: paramType = ["Generics", 100]
const tsAssign2: NewAssign = createNewAssign(...assignArgs)

// 12.9 Awaited
interface User {
    id: number,
    name: string,
    username: string,
    email: string,
}

const fetchUsers = async (): Promise<User[]> => {

    const data = await fetch(
        'https://jsonplaceholder.typicode.com/users'
    ).then(res => {
        return res.json()
    }).catch(err => {
        if (err instanceof Error) console.log(err.message)
    })
    return data
}

type FetchUsersReturnType = Awaited<ReturnType<typeof fetchUsers>>

fetchUsers().then(users => console.log(users))