console.log("external mode");

// ES5
var animal; // declaration
animal = "dog"; // assignmenr

var anotherAnimal = "cat";

// ES6
let animal2;
animal2 = "dog";

const animal3 = "cat";

// dynamically typed example
console.log("animal2", animal2);
animal2 = 12;
console.log("animal2", animal2);

// reference value
const myObject = {
    name: "charlie",
    key2: 28,
    key3: true,
};

console.log("myObject.name", myObject.name);
myObject.name = "alice";
console.log("myObject.name", myObject.name);

const myArray = [1, true, "charlie"];
console.log("myArray[2]", myArray[2]);
myArray[2] = "alice";
console.log("myArray[2]", myArray[2]);

// function
function introduceYourself(name) {
    console.log("name", name);
};

introduceYourself("peter");

// function expression
const introduceYourself2 = function (name) {
    console.log("name", name);
};

introduceYourself2("peter2");

// arrow function
const addTwoNumber = (num1, num2) => {
    console.log("num1 + num2", num1 + num2);
};

addTwoNumber(2, 3);

// IIFE
(function () {
    console.log("IIFE");
})();

(() => {
    console.log("IIFE2");
})();

// callback function
// f1()
// f2(f1)

// f1 callback function
// f2 higher order function

const bark = (sound) => {
    sound();
}

const cat = () => console.log("miao");
const dog = () => console.log("wang");

bark(cat);
bark(dog);