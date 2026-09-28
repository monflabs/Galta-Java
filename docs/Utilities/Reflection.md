# Reflection

`org.monflabs.util.model` gives dynamic, name-based access to plain Java objects: read and write members, index arrays and lists, call overloaded methods and construct objects, with argument conversion. It is the reflection layer under GaltaJS's Java interop, and it can be used directly.

| Class | Role |
|---|---|
| `ModelAccessor` | The interface: `getMember`/`putMember` by name or index, `call`, `constructObject`, `constructArray` |
| `PojoAccessor` | The implementation for POJOs, arrays, `List` and `Map` |
| `ClassMetadata` | A cache of the public members of each class, overload resolution and value conversion |
| `ClassMetadata.AccessManager` | Filters which member names are visible |
| `ModelException` | The unchecked exception thrown on errors |

## Reading and writing members

`PojoAccessor.getMember(instance, name)` looks the name up among the public members of the instance's class. A `Map` is accessed by key instead.

| Priority | Member | Read | Write |
|---|---|---|---|
| 1 | Property: a public, non-static getter `getName()`/`isName()` with no parameter and a non-void result | calls the getter | calls `setName(value)` if there is one whose parameter type is exactly the getter's return type; otherwise throws `ModelException` |
| 2 | Public field `name` | reads it | writes it |

So a property hides a field with the same name. When a value is written, it is converted to the target type first (see Conversions below).

Sample: `doc_examples/util/ReflectionExamples.java` (`Account`, `testMembers`)

```java
public static class Account {
    public String owner = "ann";
    public double balance;           // shadowed by the property below
    private double cents = 1050;

    public double getBalance() {     // property "balance"
        return cents / 100;
    }
    public void setBalance(double value) {
        cents = value * 100;
    }

    public String getId() {          // read-only property "id"
        return "A-1";
    }

    public String describe() {
        return "account";
    }
    public String describe(int level) {
        return "int " + level;
    }
    public String describe(double level) {
        return "double " + level;
    }
    public String describe(String prefix) {
        return prefix + owner;
    }
}

PojoAccessor accessor = new PojoAccessor();
Account account = new Account();

assertEquals("ann", accessor.getMember(account, "owner"));        // public field
assertEquals(10.5, accessor.getMember(account, "balance"));        // the getter wins over the field
assertTrue(accessor.putMember(account, "balance", 20));            // Integer converted to double
assertEquals(20.0, account.getBalance());
assertEquals(0.0, account.balance);                                // the field was not touched

assertSame(ModelAccessor.UNHANDLED, accessor.getMember(account, "missing"));
assertFalse(accessor.putMember(account, "missing", 1));
```

When a member does not exist, `getMember` returns the marker `ModelAccessor.UNHANDLED` and `putMember` returns `false`. A member that exists but cannot be read or written, such as a read-only property or a getter that throws, always throws a `ModelException`; a getter's or setter's exception is its cause.

Sample: `doc_examples/util/ReflectionExamples.java` (`testReadOnlyProperty`)

```java
PojoAccessor accessor = new PojoAccessor();
assertEquals("A-1", accessor.getMember(new Account(), "id"));
// A member that exists but cannot be written throws, even without setUseExceptions(true)
ModelException e = assertThrows(ModelException.class, () -> accessor.putMember(new Account(), "id", "B-2"));
assertTrue(e.getMessage().startsWith("Error while setting read-only property id"));
```

`setUseExceptions(true)` makes the "not found" cases throw as well:

Sample: `doc_examples/util/ReflectionExamples.java` (`testExceptions`)

```java
PojoAccessor accessor = new PojoAccessor();
accessor.setUseExceptions(true);       // throw instead of returning UNHANDLED/false
ModelException e = assertThrows(ModelException.class, () -> accessor.getMember(new Account(), "missing"));
assertEquals("Invalid member missing for class " + Account.class.getName(), e.getMessage());
```

## Calling methods

`call(instance, name, args)` picks a public method among the overloads with that name, then converts the arguments and invokes it. A field or property with the same name does not hide the method. The resolution works on the runtime classes of the arguments (primitive parameters count as their wrapper classes):

1. Overloads with a different number of parameters are discarded (varargs are not supported).
2. For each argument: the same class as the parameter is an exact match; an assignable class, a `String`/`Character` pair, or any `Number` for a `Number` parameter is a possible match; anything else rejects the overload. A `null` argument matches any non-primitive parameter exactly.
3. An overload where every argument matches exactly is chosen immediately.
4. Otherwise the most specific of the possible overloads is chosen. When two candidates are unrelated, for example `long` and `BigDecimal` for an `Integer`, the call throws a `ModelException` "Ambiguity between ...".

The result of the resolution is cached per argument-class shape, so a repeated call does not scan the overloads again. When the method itself throws, `call` returns `UNHANDLED`, or with `useExceptions` set throws a `ModelException` whose cause is the method's exception. Note that `call` (like `constructObject`) converts the arguments in place, in the array it receives.

Sample: `doc_examples/util/ReflectionExamples.java` (`testOverloads`)

```java
PojoAccessor accessor = new PojoAccessor();
Account account = new Account();
assertEquals("account", accessor.call(account, "describe", new Object[] {}));
assertEquals("int 3", accessor.call(account, "describe", new Object[] {3}));          // exact match
assertEquals("double 2.5", accessor.call(account, "describe", new Object[] {2.5}));
assertEquals("Dear ann", accessor.call(account, "describe", new Object[] {"Dear "}));
```

Sample: `doc_examples/util/ReflectionExamples.java` (`Scale`, `testOverloadConversions`)

```java
public static class Scale {
    public String of(long value) {
        return "long " + value;
    }
    public String of(BigDecimal value) {
        return "decimal " + value;
    }
}

PojoAccessor accessor = new PojoAccessor();
// No exact match for an Integer: every Number overload is a candidate, and two unrelated
// candidates are reported as an ambiguity
ModelException e = assertThrows(ModelException.class, () -> accessor.call(new Scale(), "of", new Object[] {7}));
assertTrue(e.getMessage().startsWith("Ambiguity between of("));

// With a single candidate the argument is converted
assertEquals("long 7", accessor.call(new Scale(), "of", new Object[] {7L}));
assertEquals("decimal 7", accessor.call(new Scale(), "of", new Object[] {new BigDecimal("7")}));
```

## Arrays, lists and maps

`getMember(instance, index)` and `putMember(instance, index, value)` work on arrays and `List`s. An index outside the bounds is "not found" (`UNHANDLED`/`false`): a list is updated with `set()` and never grows. A value written into an array is converted to the component type.

Sample: `doc_examples/util/ReflectionExamples.java` (`testIndexedAccess`)

```java
PojoAccessor accessor = new PojoAccessor();
int[] numbers = {1, 2, 3};
assertEquals(2, accessor.getMember(numbers, 1));
assertTrue(accessor.putMember(numbers, 1, 20.7));         // converted to the component type
assertEquals(20, numbers[1]);
assertSame(ModelAccessor.UNHANDLED, accessor.getMember(numbers, 3));   // out of bounds

List<String> list = new ArrayList<>(List.of("a", "b"));
assertTrue(accessor.putMember(list, 0, "z"));
assertFalse(accessor.putMember(list, 2, "c"));           // set() only, lists do not grow

Map<String, Object> map = new HashMap<>();
accessor.putMember(map, "k", 1);                         // maps are accessed by key
assertEquals(1, accessor.getMember(map, "k"));
assertSame(ModelAccessor.UNHANDLED, accessor.getMember(map, "other"));
```

## Constructing objects

`constructObject(className, args)` loads the class with the accessor's class loader (`getClassLoader()`, overridable) and calls a public constructor chosen like a method overload, converting the arguments. With no arguments it uses the no-argument constructor. `constructArray(className, size)` creates an array of that component class; since the class is loaded by name, primitive names such as `int` are not found.

Sample: `doc_examples/util/ReflectionExamples.java` (`Point`, `testConstruct`)

```java
public static class Point {
    public final int x;
    public final int y;
    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }
}

PojoAccessor accessor = new PojoAccessor();
Point p = (Point) accessor.constructObject(Point.class.getName(), new Object[] {1, 2});
assertEquals(2, p.y);
Point converted = (Point) accessor.constructObject(Point.class.getName(), new Object[] {1.9, 2L});
assertEquals(1, converted.x);   // arguments converted like for call()

// constructArray() loads the component class by name, so primitive names are not found
assertSame(ModelAccessor.UNHANDLED, accessor.constructArray("int", 3));
String[] names = (String[]) accessor.constructArray("java.lang.String", 3);
assertEquals(3, names.length);
```

## Conversions

`ClassMetadata.convertObject(value, targetClass)` converts a value for a member, parameter or array element. It returns the value unchanged when it has no conversion for it, so the error, if any, comes from the reflective call.

| Target | Strict (default) | Permissive adds |
|---|---|---|
| `byte`, `short`, `int`, `long`, `float`, `double` and wrappers | any `Number`, by Java narrowing | a `String` (parsed), a `Boolean` (1/0) |
| `boolean`/`Boolean` | `Boolean` | a `Number` (non-zero is `true`), a `String` (`Boolean.parseBoolean`) |
| `char`/`Character` | a `Number` (code point), a one-character `String` | |
| `String` | `String` | anything, with `toString()` |
| `BigInteger`, `BigDecimal` | any `Number`, via `TypeUtil` | a `String`, a `Boolean` |

`convertObjectPermissive(value, targetClass)` applies the permissive rules; `new PojoAccessor(metadata, true)` makes an accessor use them.

Sample: `doc_examples/util/ReflectionExamples.java` (`testConvertObject`)

```java
assertEquals(3, ClassMetadata.convertObject(3.9, int.class));
assertEquals("abc", ClassMetadata.convertObject("abc", int.class));        // not convertible: unchanged
assertEquals(42, ClassMetadata.convertObjectPermissive("42", int.class));  // permissive parses strings
assertEquals(true, ClassMetadata.convertObjectPermissive(1, boolean.class));
assertEquals('A', ClassMetadata.convertObject(65, char.class));
```

## ClassMetadata and the AccessManager

`ClassMetadata` caches, per class, the members found by name (`ClassInfoCache`): `getProperty`, `getField`, `getMethod`, `getValueAccessor`, `getConstructors`, and the name sets `getValueAccessors()` (public fields), `getMethods()` and `getAllMembers()`. Only public members are considered (`getMethods()`, `getFields()`, `getConstructors()`), inherited ones included. The cache is thread-safe and grows with every class seen, so share one `ClassMetadata` rather than creating one per call. `new PojoAccessor()` creates its own; `PojoAccessor.getDataAccessor()` returns a shared instance.

Sample: `doc_examples/util/ReflectionExamples.java` (`testClassMetadata`)

```java
ClassMetadata metadata = new ClassMetadata(null);
ClassInfoCache info = metadata.getClassInfoCache(Account.class);
assertEquals(Set.of("owner", "balance"), info.getValueAccessors());   // public fields
assertTrue(info.getMethods().contains("describe"));
assertNotNull(info.getProperty("balance").getSetter());
assertNotNull(info.getField("balance"));
```

The `AccessManager` given to the `ClassMetadata` constructor filters member names: `acceptField(name)`, `acceptMethod(name)` and `acceptProperty(name)` decide whether a name is looked up at all. It also declares `canLoadClass`, `canCreateObject`, `canCreateArray`, `canProxy` and `canAccessMember`, which `PojoAccessor` does not call; they are checked by GaltaJS's `JavaLibrary` (see [Java Interop](/GaltaJS/UserGuide/JavaInterop)). To restrict classes with `PojoAccessor`, override its `acceptClass(Class)`.

Sample: `doc_examples/util/ReflectionExamples.java` (`testAccessManager`)

```java
AccessManager noOwner = new AccessManager() {
    @Override
    public boolean acceptField(String name) {
        return !name.equals("owner");
    }
};
PojoAccessor accessor = new PojoAccessor(new ClassMetadata(noOwner));
assertSame(ModelAccessor.UNHANDLED, accessor.getMember(new Account(), "owner"));
assertEquals(10.5, accessor.getMember(new Account(), "balance"));
```
