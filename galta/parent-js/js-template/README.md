# GaltaJS Templates

[![Maven Central](https://img.shields.io/maven-central/v/org.monflabs.galta/js-template?label=Maven%20Central)](https://central.sonatype.com/artifact/org.monflabs.galta/js-template)

A small text-template layer on top of the GaltaJS engine, with a JSP-like
syntax: `<% ... %>` runs a piece of script and `<%= ... %>` emits the value of an
expression in place. A template is compiled once into a script, then rendered
against a global context whose variables it can use. Templates are rendered
synchronously, so the context's expression executor is enough.

## Usage

```xml
<dependency>
  <groupId>org.monflabs.galta</groupId>
  <artifactId>js-template</artifactId>
</dependency>
```

The version comes from the `galta-bom` (see [Modules](../../../README.md#modules)),
or declare `<version>` directly.

```java
JSEnvironment env = JavaScriptEnvironment.create();
TemplateEngine engine = new JspTemplateEngine(env);
Template template = engine.compile("Hello <%= name %>!");
InterpretedGlobalRuntimeContext ctx = new InterpretedGlobalRuntimeContext(env, env.createExpressionExecutor());
ctx.getGlobalThis().setOwnProperty("name", "Ada");
String text = template.execute(ctx);
```

`TemplateEngine.execute(context, source)` compiles and renders in one call.

## Contents

Package `org.monflabs.galtajs.template`:

- `TemplateEngine` - compiles a template source (`compile`), or compiles and renders it (`execute`)
- `Template` - a compiled template, rendered with `execute(InterpretedGlobalRuntimeContext)`
- `engines.JspTemplateEngine` - the `<% %>` / `<%= %>` syntax; a single line break after a statement tag is not emitted
- `templates.ScriptTemplate` - a template compiled to a script; `templates.IdentityTemplate` - a template with no tag, returned as is

## Documentation

- [Companion Modules - js-template](../../../docs/GaltaJS/UserGuide/CompanionModules.md#js-template) ([online](https://monflabs.github.io/Galta-Java/#/GaltaJS/UserGuide/CompanionModules?id=js-template))
- [Executing Code](../../../docs/GaltaJS/UserGuide/ExecutingCode.md) ([online](https://monflabs.github.io/Galta-Java/#/GaltaJS/UserGuide/ExecutingCode)) - global contexts and executors
- [API reference](https://monflabs.github.io/Galta-Java/#/API)
