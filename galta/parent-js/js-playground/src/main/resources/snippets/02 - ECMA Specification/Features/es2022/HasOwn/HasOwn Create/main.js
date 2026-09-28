const user = Object.create(null);
user.age = 35;
console.log(Object.hasOwn(user, 'age')); // true - works even though user has no hasOwnProperty method