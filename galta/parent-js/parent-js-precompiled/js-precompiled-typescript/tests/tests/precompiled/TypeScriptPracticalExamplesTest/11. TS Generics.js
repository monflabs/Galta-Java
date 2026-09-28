"use strict";
// 11.1 Basic Generic Usage
const myFunc = (arg) => {
    return arg;
};
const myObj2 = (arg) => {
    return arg;
};
// console.log(myObj2({ name: "Riyad" })) - Have to provide id property as extending from HasID
console.log(myObj2({ id: 23, name: "Riyad" }));
// 11.2 Advance Usage
const getUserProp = (users, key) => {
    return users.map((user) => user[key]);
};
const usersArray = [
    {
        id: 1,
        name: "Leanne Graham",
        username: "Bret",
        email: "Sincere@april.biz",
        address: {
            street: "Kulas Light",
            suite: "Apt. 556",
            city: "Gwenborough",
            zipcode: "92998-3874",
            geo: {
                lat: "-37.3159",
                lng: "81.1496",
            },
        },
        phone: "1-770-736-8031 x56442",
        website: "hildegard.org",
        company: {
            name: "Romaguera-Crona",
            catchPhrase: "Multi-layered client-server neural-net",
            bs: "harness real-time e-markets",
        },
    },
    {
        id: 2,
        name: "Ervin Howell",
        username: "Antonette",
        email: "Shanna@melissa.tv",
        address: {
            street: "Victor Plains",
            suite: "Suite 879",
            city: "Wisokyburgh",
            zipcode: "90566-7771",
            geo: {
                lat: "-43.9509",
                lng: "-34.4618",
            },
        },
        phone: "010-692-6593 x09125",
        website: "anastasia.net",
        company: {
            name: "Deckow-Crist",
            catchPhrase: "Proactive didactic contingency",
            bs: "synergize scalable supply-chains",
        },
    },
];
const allEmails = getUserProp(usersArray, "email"); // It's showing suggetions based on its dynamic arguments
// 11.3 Generics in Class
class Country {
    resources;
    constructor(resources) {
        this.resources = resources;
    }
    get data() {
        return this.resources;
    }
    set data(arg) {
        this.resources = arg;
        return;
    }
}
const Bangladesh = new Country("DB"); // const Bangladesh: Country<string>
console.log(Bangladesh.data);
Bangladesh.data = "USA";
// Bangladesh.data = 12 - Type 'number' is not assignable to type 'string'
const myCountry = new Country("Canada");
myCountry.data = 42;
console.log(myCountry.data);
