class Employee  {
    name = "John";
    #age=35;
   constructor() {
    }

    #getAge() {
      return this.#age;
    }

    getAge() {
      return this.#getAge();
    }

 }

  const employee = new Employee();
  employee.name = "Jack";
  console.log(employee.getAge()); // 35 - read through a public method
  console.log(employee.name);     // Jack
  console.log(employee.age);      // undefined - #age is private, not accessible from outside