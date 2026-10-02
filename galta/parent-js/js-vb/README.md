# GaltaJS Value Bindings

[![Maven Central](https://img.shields.io/maven-central/v/org.monflabs.galta/js-vb?label=Maven%20Central)](https://central.sonatype.com/artifact/org.monflabs.galta/js-vb)

JSF/EL-style value bindings evaluated with the GaltaJS engine: text such as
`Hello ${user.name}` mixes literal parts with `${...}` JavaScript expressions,
and evaluates against a global context. The delimiters are configurable
(`${` and `}` by default). A binding without any expression is a constant.

## Usage

```xml
<dependency>
  <groupId>org.monflabs.galta</groupId>
  <artifactId>js-vb</artifactId>
</dependency>
```

The version comes from the `galta-bom` (see [Modules](../../../README.md#modules)),
or declare `<version>` directly.

```java
JSEnvironment env = JavaScriptEnvironment.create();
ValueBindingFactory factory = new ValueBindingFactory(env);           // "${" ... "}"
InterpretedGlobalRuntimeContext ctx = new InterpretedGlobalRuntimeContext(env, env.createExpressionExecutor());
ctx.getGlobalThis().setOwnProperty("user", JSObject.of(env, "name", "Ada"));

factory.isValueBinding("Hello ${user.name}");                        // true
ValueBinding vb = factory.createValueBinding("Hello ${user.name}");  // literal text mixed with expressions
Object text = vb.evaluate(ctx);                                      // "Hello Ada"
factory.createValueBinding("plain text").isConstant();               // true: no expression inside
```

## Contents

Package `org.monflabs.galtajs.vb`:

- `ValueBindingFactory` - built with an environment and optional delimiters; `isValueBinding(text)`, `createValueBinding(text)` (literal text and expressions), `createExpression(text)` (a single JavaScript expression), `evaluate(context, text)`
- `ValueBinding` - a parsed binding: `getExpression()`, `isConstant()`, `evaluate(context)`
- `impl.GaltaJSValueBinding` - an expression compiled by the engine; `impl.IdentityValueBinding` - literal text

## Documentation

- [Companion Modules - js-vb](../../../docs/GaltaJS/UserGuide/CompanionModules.md#js-vb) ([online](https://monflabs.github.io/Galta-Java/#/GaltaJS/UserGuide/CompanionModules?id=js-vb))
- [Executing Code](../../../docs/GaltaJS/UserGuide/ExecutingCode.md) ([online](https://monflabs.github.io/Galta-Java/#/GaltaJS/UserGuide/ExecutingCode)) - global contexts and executors
- [API reference](https://monflabs.github.io/Galta-Java/#/API)
