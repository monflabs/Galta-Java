const user = {
    age: 35,
    hasOwnProperty: ()=> {
      return false;
    }
  };

  console.log(Object.hasOwn(user, 'age')); // true - not fooled by the overwritten hasOwnProperty