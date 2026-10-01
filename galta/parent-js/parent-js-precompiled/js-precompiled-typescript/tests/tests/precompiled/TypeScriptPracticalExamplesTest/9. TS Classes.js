"use strict";
// 9.1 Basic Class__________
class Individual {
    //   name: string;
    //   age: number;
    //   occupation: string;
    //   exp: number;
    // Don't need to write explicitly in the above if you define the type like following
    constructor(name, age, exp, // accessible within class & subclasses
    occupation // accessible within class
    ) {
        this.name = name;
        this.age = age;
        this.exp = exp;
        this.occupation = occupation;
        this.name = name;
        this.age = age;
        this.occupation = occupation;
        this.exp = exp;
    }
    getOccupation() {
        return `${this.name} is a ${this.occupation}`;
    }
}
const Riyad = new Individual("Riyad", 22, 2, "St Engineer");
console.log(Riyad.name);
console.log(Riyad.getOccupation());
// console.log(Riyad.occupation) - Property 'occupation' is private and only accessible within class 'Individual'
// console.log(Riyad.exp) - Property 'exp' is protected and only accessible within class 'Individual' and its subclasses
// 9.2 Subclass_________
class Engineer extends Individual {
    constructor(name, age, occupation, exp, lang = "TypeScript" // Default Value
    ) {
        super(name, age, exp, occupation);
        this.lang = lang;
        this.lang = lang;
    }
    getExp() {
        return `I've ${this.exp} years of exp`;
    }
}
const SadikEngeer = new Engineer("Sadik", 25, "Std Engeer", 2);
console.log(SadikEngeer.getExp());
class Cricketer {
    constructor(name, age) {
        this.name = name;
        this.age = age;
        this.name = name;
        this.age = age;
    }
    play(action) {
        return `${this.name} is ${action}`;
    }
}
const Mashrafee = new Cricketer("Mashrafee", 32);
console.log(Mashrafee.play("bowling")); // Mashrafee is bowling
// 9.5 Static____________
class People {
    static getCount() {
        return People.count;
    }
    constructor(name) {
        this.name = name;
        this.name = name;
        this.id = ++People.count; // accessable because of static type
    }
}
People.count = 0;
const John = new People("John");
const Steve = new People("Steve");
const Amy = new People("Amy");
console.log(John.id); // 1
console.log(Steve.id); // 2
console.log(Amy.id); // 3
console.log(People.count); // 3
// 9.6 Setter & Getter___________
class School {
    constructor() {
        this.students = [];
    }
    get student() {
        return this.students;
    }
    set student(data) {
        this.students = data;
    }
}
const MySchool = new School();
MySchool.student = ["Neil Young", "Led Zep"];
console.log(MySchool.student);
MySchool.student = [...MySchool.student, "ZZ Top"];
console.log(MySchool.student);
// MySchool.student = ['Van Halen', 5150] - must be string data
