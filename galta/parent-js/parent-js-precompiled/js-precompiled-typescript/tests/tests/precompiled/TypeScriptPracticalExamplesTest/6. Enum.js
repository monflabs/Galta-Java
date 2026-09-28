"use strict";
var Alphabet;
(function (Alphabet) {
    Alphabet[Alphabet["A"] = 1] = "A";
    Alphabet[Alphabet["B"] = 2] = "B";
    Alphabet[Alphabet["C"] = 3] = "C";
    Alphabet[Alphabet["D"] = 4] = "D";
    Alphabet[Alphabet["E"] = 5] = "E";
})(Alphabet || (Alphabet = {}));
// If not assigned the first value- enum Alphabet { A , B, C, D, E }
console.log(Alphabet.C); // 0
// If assigned the first value
console.log(Alphabet.C); // 3
