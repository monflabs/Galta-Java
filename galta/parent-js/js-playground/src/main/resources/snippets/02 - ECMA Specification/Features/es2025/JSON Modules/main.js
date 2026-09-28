import config from "./config.json" with { type: "json" };

console.log(config.name, "v" + config.version);
console.log(config.features.join(", "));
