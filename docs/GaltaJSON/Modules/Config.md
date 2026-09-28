# Configuration

The `json-config` module stores application settings in JSON resources and reads them through slash-separated keys (`server/port`). It adds typed reads with defaults, transactional updates with automatic saving, `$ref` links between resources and the encryption of selected values such as passwords.

```xml
<dependency>
    <groupId>org.monflabs.galta</groupId>
    <artifactId>json-config</artifactId>
</dependency>
```

A configuration implements `org.monflabs.util.config.Config`, the generic configuration interface of the utilities library, plus `JsonConfig.getContent()` for the underlying `JsonObject`. Two implementations are provided:

| Class | Resources live in |
|---|---|
| `JsonFileConfig` | Files under a folder. |
| `CustomJsonConfig` | Anywhere: you provide a reader and, optionally, a writer function. |

Both extend `AbstractJsonConfig`, which a custom storage can extend as well: it only has to implement `getResource(path)`, `setResource(path, save)` and `isReadOnly()`, then call `load()` from its constructor.

## Loading and reading

A `JsonFileConfig` reads one main file from a folder. The folder is created if it does not exist, and a missing file gives an empty configuration.

Sample: `doc_examples/config/ConfigExamples.java` (`testFileConfig`)

```java
// app.json
// {
//   "server": { "host": "localhost", "port": 8080, "secure": "true" },
//   "timeout": 2.5,
//   "tags": ["a", "b"]
// }
JsonFileConfig config = JsonFileConfig.newBuilder()
        .folder(folder)
        .fileName("app.json")
        .build();

config.getString("server/host");          // -> "localhost"
config.getInt("server/port");             // -> 8080
config.getBoolean("server/secure");       // -> true: strings are converted
config.getDouble("timeout");              // -> 2.5
config.getString("server/port");          // -> "8080": any value reads as a string
config.getInt("server/retries", 30);      // -> 30: default when missing
config.getString("server");               // -> null: a folder is not a value
config.getValue("tags");                  // -> null: neither is an array
config.getContent().getArray("tags");     // the raw JSON is still available
```

A key is a path of object property names separated by `/`. Objects are *folders*, and anything else except arrays is a *value*:

| Method | Returns |
|---|---|
| `getValue(key)` | The raw value (`String`, `Number`, `Boolean`), or `null` for a missing key, a folder or an array. |
| `getString(key[, default])` | The value's `toString()`. |
| `getInt`, `getLong`, `getDouble`, `getBoolean` `(key[, default])` | Numbers as is, strings parsed. Without a default, a missing key gives `0` / `false`. An unparsable string throws a `ConfigException`, except for booleans where anything but `"true"` (any case) is `false`. |
| `has(key)`, `isValue(key)`, `isFolder(key)` | What the key points to. |
| `keysOf(key[, ENUM_KEYS.ALL / VALUES / FOLDERS])` | An iterator over the child names of a folder. |
| `subConfig(key...)` | A view whose keys are relative to a folder; it reads and updates the same configuration. |

Sample: `doc_examples/config/ConfigExamples.java` (`testBrowse`)

```java
// { "server": { "host": "h", "port": 1, "tls": { "enabled": true } } }
config.isFolder("server/tls");                                   // -> true
Iterators.collect(config.keysOf("server"));                      // -> [host, port, tls]
Iterators.collect(config.keysOf("server", ENUM_KEYS.VALUES));    // -> [host, port]
Iterators.collect(config.keysOf("server", ENUM_KEYS.FOLDERS));   // -> [tls]

Config server = config.subConfig("server");
server.getBoolean("tls/enabled");                                // -> true
```

The empty key does not denote the root: `keysOf("")` is empty. Use `getContent().keySet()` to list the top-level keys.

## Updating

`updateValues()` runs a callback against a copy of the content. When the callback returns, the changes replace the content at once and, with auto-save on (the default), the configuration is saved. `cancel()` discards everything, and the method returns whether something changed.

Sample: `doc_examples/config/ConfigExamples.java` (`testUpdates`)

```java
boolean changed = config.updateValues(u -> {
    u.put("server/host", "example.com");     // folders are created
    u.put("server/port", 443);
    u.put("debug", false);
});
config.getInt("server/port");                // -> 443
// app.json -> {"server":{"host":"example.com","port":443},"debug":false}

config.updateValues(u -> {
    u.put("server/host", "other.com");
    u.cancel();                              // nothing is changed, nothing is saved
});
```

The typed `put()` overloads keep the JSON type: `put(key, 443)` writes the number `443` and `put(key, false)` the boolean `false`; `put(key, "443")` writes a string. The typed getters read either form. `put()` returns `false`, and changes nothing, when the key would replace a folder or go through an existing value (`debug/level` when `debug` is a value); `remove()` returns `false` for a missing key.

With `autoSave(false)`, updates stay in memory until `save()` is called:

Sample: `doc_examples/config/ConfigExamples.java` (`testNoAutoSave`)

```java
JsonFileConfig config = JsonFileConfig.newBuilder()
        .folder(folder)
        .fileName("app.json")
        .autoSave(false)
        .build();
config.updateValues(u -> u.put("a", "1"));   // app.json is not written
config.save();                               // now it is
```

A save failure is reported as a `ConfigException`, from `save()` or from the `updateValues()` call that triggered it.

## Read-only configurations

A read-only configuration refuses updates with a `ConfigException` (`Storage is readonly`), and `save()` returns `false`. A `JsonFileConfig` is read-only with `readOnly(true)`; a `CustomJsonConfig` is read-only when it has no writer.

Sample: `doc_examples/config/ConfigExamples.java` (`testReadOnly`, `testCustomConfig`)

```java
Map<String,byte[]> store = new HashMap<>();   // resource name -> bytes; null is the main one
store.put(null, "{ \"a\": \"1\" }".getBytes(StandardCharsets.UTF_8));

Function<String,InputStream> reader = path -> {
    byte[] b = store.get(path);
    return b != null ? new ByteArrayInputStream(b) : null;
};
BiFunction<String,Consumer<OutputStream>,InputStream> writer = (path, save) -> {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    save.accept(os);
    store.put(path, os.toByteArray());
    return null;
};

CustomJsonConfig readOnly = CustomJsonConfig.newBuilder()
        .resourceReader(reader)
        .build();                             // isReadOnly() -> true

CustomJsonConfig config = CustomJsonConfig.newBuilder()
        .resourceReader(reader)
        .resourceWriter(writer)
        .build();
config.updateValues(u -> u.put("b", "2"));    // saved through the writer
```

The reader receives `null` for the main resource and a name for the others; it returns `null` when a resource does not exist. The writer receives the resource name and a callback that writes the content to an `OutputStream`; its return value is ignored.

## Resources

Besides the main JSON content, a configuration can store named resources, like certificates or templates, with `setResource(path, content)` and `getResourceAsString(path)` (plus `InputStream`/`OutputStream` variants). For a `JsonFileConfig`, a path is relative to the folder and may contain `/`; missing sub-folders are created.

Sample: `doc_examples/config/ConfigExamples.java` (`testMissingFolder`)

```java
config.setResource("certs/ca.pem", "---");    // <folder>/certs/ca.pem
```

## `$ref`: splitting a configuration

An object made of a single `$ref` property is replaced, when loading, by the JSON object read from the named resource. Updating a value that came through a reference saves it back to the referenced resource, and the main resource keeps its `$ref`.

Sample: `doc_examples/config/ConfigExamples.java` (`testReferences`)

```java
// app.json: { "name": "demo", "db": { "$ref": "db.json" } }
// db.json:  { "host": "localhost", "port": 5432 }
config.getString("db/host");                          // -> "localhost"

config.updateValues(u -> u.put("db/host", "db.internal"));
// app.json -> {"name":"demo","db":{"$ref":"db.json"}}
// db.json  -> {"host":"db.internal","port":5432}
```

A `$ref` inside a referenced resource is followed too, and a local `#/...` reference there is relative to that resource. A reference may carry a JSON Pointer fragment (`common.json#/db`, see [Pointers](/GaltaJSON/Pointers)); such a value is read, but it is not written back when saving.

Sample: `doc_examples/config/ConfigExamples.java` (`testReferenceLimits`)

## Encrypting values

An encryptor encrypts selected values in the stored resources while the configuration holds them in clear. `KeyEncryptor` uses AES with a key derived from a passphrase, and a predicate that receives the full key path of each value as a `String[]` (`["db", "password"]`).

Sample: `doc_examples/config/ConfigExamples.java` (`testEncryption`)

```java
// app.json: { "user": "joe", "password": "top", "db": { "password": "s3cret", "host": "h" } }
KeyEncryptor encryptor = new KeyEncryptor("my-master-key",
        keys -> keys[keys.length - 1].equals("password"));

JsonFileConfig config = JsonFileConfig.newBuilder()
        .folder(folder)
        .fileName("app.json")
        .encryptor(encryptor)
        .build();

config.getString("db/password");       // -> "s3cret"
// app.json now holds "password":"[[...]]" at both levels; "user" and "host" are untouched
```

How it behaves:

- Only string values are encrypted. An encrypted value is written as `[[v2:` + Base64 + `]]`: the payload holds a random salt, a random IV and the AES-GCM ciphertext, the key being derived from the passphrase with PBKDF2-HMAC-SHA256. A value is recognized as encrypted only when it has that exact shape (or the legacy one below), so a plain value such as `[[x]]` is encrypted like any other value.
- When a *writable* configuration loads a resource with values that match the predicate but are still in clear, it saves the resource encrypted right away, whatever the auto-save setting. A read-only configuration decrypts but never writes. So a secret can be typed in clear in the file and gets encrypted by the next start of the application.
- Referenced (`$ref`) resources are decrypted and encrypted the same way. The predicate receives the full key path in the configuration: a `password` in `db.json`, referenced from `db`, is `["db", "password"]`.
- The encryption is randomized: encrypting the same value twice gives two different texts, and a modified or foreign value fails to decrypt (`ConfigException`). It protects secrets at rest, it is not a replacement for a secret vault.
- Values written by earlier versions (`[[` + Base64 + `]]`, AES-CBC with a fixed IV) are still decrypted, and are written in the current format the next time the resource is saved.

`KeyEncryptor` can also be used on its own, through `encryptValue()` / `decryptValue()`, or implement the `ValueEncryptor` interface to plug in another algorithm.

Sample: `doc_examples/config/ConfigExamples.java` (`testEncryptorStandalone`)

```java
KeyEncryptor encryptor = new KeyEncryptor("my-master-key", keys -> true);
String enc = encryptor.encryptValue("hello");   // "[[v2:...]]", different at each call
encryptor.decryptValue(enc);                    // -> "hello"
```

## `JsonFileConfig` folder layout

| Builder option | Meaning |
|---|---|
| `folder(Path)` | The folder holding the main file and the other resources. Created when missing. Without a folder, the configuration is empty and cannot be saved. |
| `fileName(String)` | The main file, relative to the folder. |
| `readOnly(boolean)` | Refuse updates (default `false`). |
| `autoSave(boolean)` | Save after each update (default `true`). |
| `encryptor(ValueEncryptor)` | Encrypt matching values in the files. |

For development, a second folder can hold files that are not part of the deployed configuration. When the `MONFLABS_USER_HOME` environment variable names an existing directory, or else when `~/monflabs-dev-settings/user-settings` exists, the *dev folder* is the sub-folder of that directory with the same name as the configuration folder. A resource is read from the configuration folder when it exists there, otherwise from the dev folder. When writing, an existing file is overwritten where it was found; a new file is created in the configuration folder.
