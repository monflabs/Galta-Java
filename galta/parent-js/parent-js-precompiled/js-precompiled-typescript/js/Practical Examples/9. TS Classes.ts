// 9.1 Basic Class__________
class Individual {
  //   name: string;
  //   age: number;
  //   occupation: string;
  //   exp: number;

  // Don't need to write explicitly in the above if you define the type like following
  constructor(
    public readonly name: string,
    readonly age: number,
    protected exp: number, // accessible within class & subclasses
    private occupation: string // accessible within class
  ) {
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
  constructor(
    name: string,
    age: number,
    occupation: string,
    exp: number,
    readonly lang: string = "TypeScript" // Default Value
  ) {
    super(name, age, exp, occupation);
    this.lang = lang;
  }

  getExp(): string {
    return `I've ${this.exp} years of exp`;
  }
}

const SadikEngeer = new Engineer("Sadik", 25, "Std Engeer", 2);

console.log(SadikEngeer.getExp());

// 9.3 Interface__________
interface Player {
  name: string;
  age: number;
  play(action: string): string;
}

class Cricketer implements Player {
  constructor(public name: string, public readonly age: number) {
    this.name = name;
    this.age = age;
  }

  play(action: string): string {
    return `${this.name} is ${action}`;
  }
}

const Mashrafee = new Cricketer("Mashrafee", 32);
console.log(Mashrafee.play("bowling")); // Mashrafee is bowling

// 9.5 Static____________
class People {
  static count: number = 0;
  static getCount(): number {
    return People.count;
  }

  public id: number;

  constructor(public name: string) {
    this.name = name;
    this.id = ++People.count; // accessable because of static type
  }
}

const John = new People("John");
const Steve = new People("Steve");
const Amy = new People("Amy");

console.log(John.id); // 1
console.log(Steve.id); // 2
console.log(Amy.id); // 3
console.log(People.count); // 3

// 9.6 Setter & Getter___________
class School {
  private students: string[];
  constructor() {
    this.students = [];
  }

  public get student(): string[] {
    return this.students;
  }

  public set student(data: string[]) {
    this.students = data;
  }
}

const MySchool = new School();
MySchool.student = ["Neil Young", "Led Zep"];
console.log(MySchool.student);
MySchool.student = [...MySchool.student, "ZZ Top"];
console.log(MySchool.student);
// MySchool.student = ['Van Halen', 5150] - must be string data