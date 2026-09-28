//Export Statement
// module "my-module.js"

const PI = Math.PI;

function add(...args) {
    return args.reduce((num, tot) => tot + num);
}

export function multiply(...args) {
    return args.reduce((num, tot) => tot * num);
}

export { PI, multiply, add };

// Default exports
export default function addvalue_2(v) {
    return v+2;
}
