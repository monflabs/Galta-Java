class Employee{
    name = "John";
    static #employerName="Github"

    static #getEmployerName() {
      return Employee.#employerName;
    }

    static getEmployerName() {
      return Employee.#getEmployerName();
    }
  }
  const employee = new Employee();
  employee.emp = "Jack";
  console.log(Employee.getEmployerName()); // Github - read through a public static method