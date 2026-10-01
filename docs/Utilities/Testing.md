# Test Support

The `test` module (`org.monflabs.galta:test`, package `org.monflabs.tests`) is the test toolkit shared by the Galta modules: a JUnit 3 base class, helpers to locate project folders, golden-file ("template") assertions for text and JSON, reflective access to private members, and a resource-leak detector. Add it with `test` scope. It declares JUnit as `provided`, so the project brings its own `junit:junit` (4.x); it also pulls the Galta `json` and `json-config` modules and Byte Buddy.

## The base test case

`__BaseTestCase` extends `junit.framework.TestCase`. It provides:

| Member | Purpose |
|---|---|
| `support` | A `UnitTestSupport` bound to the test class: folders, templates, JSON helpers |
| `sleep()`, `sleep(ms)` | `Thread.sleep` without the checked exception (10 ms by default) |
| `setUp()` / `tearDown()` | Set and restore the default time zone, start and check resource-leak tracking when the leak agent is installed (see below); call `super` when overriding |
| `shouldTrackResources()` | Override to return `false` to skip leak checks for a class |
| `shouldReportLeak(LeakInfo)` | Override to ignore some leaks; by default `ResourceTracker.shouldReport()`, which ignores the leaks on a jar file (a context ending with `.jar`, or an entry `.jar!/...`) and the streams on `FileDescriptor.in`/`out`/`err` |

`setUp()` sets the JVM default time zone to `America/New_York` (`__BaseTestCase.TEST_TIME_ZONE`), so date and time results are the same on every machine, and `tearDown()` restores the previous one. Code that runs before `setUp()` (field initializers, constructors, static blocks) still sees the machine's time zone.

Galta modules usually add a one-line `ProjectTestCase extends __BaseTestCase` in their `tests` package and extend that.

## Project folders

`UnitTestSupport` resolves everything against the working directory, which Maven sets to the module being tested:

| Method | Folder |
|---|---|
| `getProjectRoot()` | The working directory (`user.dir`) |
| `getProjectDirectory(path)` | A path under the project root |
| `getMainJavaDirectory()`, `getMainResourcesDirectory()` | `src/main/java`, `src/main/resources` |
| `getTestJavaDirectory()`, `getTestResourcesDirectory()` | `src/test/java`, `src/test/resources` |
| `getTargetDirectory()`, `getTargetClassesDirectory()` | `target`, `target/classes` |
| `getTargetTempDirectory()` | `target/temp`, created if missing |
| `getTargetTempDirectory(sub, empty)` | `target/temp/<sub>`, created, and emptied when `empty` is `true` |
| `getTestResultsDirectory()` | `tests`, where templates are stored |
| `getUserMonflabsDirectory()` | The user's `.monflabs` folder |

Sample: `doc_examples/testing/TestingExamples.java` (`testProjectDirectories`)

```java
// Everything is relative to the working directory, i.e. the module being tested
File root = support.getProjectRoot();                  // new File(System.getProperty("user.dir"))
support.getTestJavaDirectory();                        // <root>/src/test/java
support.getTargetDirectory();                          // <root>/target

// A scratch folder under target/temp, created and emptied on demand
File work = support.getTargetTempDirectory("doc-examples", true);   // <root>/target/temp/doc-examples, empty
```

`loadClassResourceText(name)` reads a class-path resource as UTF-8, resolved like `Class.getResourceAsStream()`: relative to the test class package unless the name starts with `/`. It throws when the resource is missing, unless `nullIfNotExist` is `true`:

Sample: `doc_examples/testing/TestingExamples.java` (`testClassResources`)

```java
// Resolved like Class.getResourceAsStream(): relative to the test class package
support.loadClassResourceText("welcome.txt");           // "Welcome!\n"
support.loadClassResourceText("missing.txt", true);     // null
```

## `UnitTestSupport` and `__BaseTestCase`

`UnitTestSupport` is a plain helper object, not a test class: folders, templates, JSON comparisons, reflective access. `__BaseTestCase` is the JUnit 3 base class; it creates one `UnitTestSupport` per test class (its `support` field) and adds the time zone and leak checks around each test. A JUnit 4 test, or a class that cannot extend `__BaseTestCase`, creates its own `new UnitTestSupport(getClass())`.

## Golden-file templates

A template assertion compares a result with a file saved by an earlier run. A missing template fails the assertion, with a message naming the file. To create it from the current result, run once with the system property `monflabs.tests.saveTemplates=missing` (`mvn test -Dmonflabs.tests.saveTemplates=missing`), or call `support.setSaveMissingTemplates(true)`. Review a new template before committing it: it is the expected result from then on.

Both `saveTemplates` modes make every check pass whatever the result, so they are refused when the `CI` environment variable is set (as on GitHub Actions): the check throws an `IllegalStateException` instead.

Templates are stored under the project's `tests` folder, in a folder named after the test class: `tests/<package path>/<ClassName>/<templateName>`. They are meant to be committed with the tests.

Sample: `doc_examples/testing/TestingExamples.java` (`testTextTemplate`)

```java
String report = "Total: 3\nFailed: 0\n";
// Compares with tests/doc_examples/testing/TestingExamples/report.txt (line breaks normalized)
// created from the result by a run with -Dmonflabs.tests.saveTemplates=missing
support.assertTextResult(report, "report.txt");

File template = support.resourceFile(getClass(), "report.txt", false);
// <root>/tests/doc_examples/testing/TestingExamples/report.txt
```

| Method | Compares |
|---|---|
| `assertTextResult(text, name)` | Text, after normalizing `\r\n` and `\r` to `\n`, and removing a byte order mark and the trailing line breaks |
| `assertJsonTemplate(json, name)` | JSON, after parsing both sides and printing them with sorted properties and fixed indentation |
| `checkTextResult(...)`, `checkJsonTemplate(...)` | Same, returning a `boolean` instead of failing |

For JSON, the value is a JSON-library value (`JsonObject`, `JsonArray`, a primitive) or a `String` holding JSON, which is parsed first. The template is saved pretty-printed. Property order does not matter, array order does:

Sample: `doc_examples/testing/TestingExamples.java` (`testJsonTemplate`)

```java
JsonObject person = JsonObject.of("name", "Ada", "languages", JsonArray.of("en", "fr"));
// Compares with tests/doc_examples/testing/TestingExamples/person.json
support.assertJsonTemplate(person, "person.json");

// Properties are sorted before comparing: their order does not matter
JsonObject reordered = JsonObject.of("languages", JsonArray.of("en", "fr"), "name", "Ada");
support.checkJsonTemplate(reordered, "person.json");    // true

// A String is parsed as JSON first
support.checkJsonTemplate("{\"name\":\"Ada\",\"languages\":[\"en\",\"fr\"]}", "person.json");   // true
```

A plain `java.util.Map` is not a JSON-library value: it is written with its `toString()` (`"{name=Ada, ...}"`), so build `JsonObject`s or pass a JSON string.

When a comparison fails, the assertion message names the template file and gives a compact diff: the line (and column) of the first difference, two lines of context, then the differing lines, `-` for the expected text and `+` for the actual one (long lines are clipped around the difference). `UnitTestSupport.compactDiff(expected, actual)` builds it. The complete expected and actual texts are also printed to standard output (reformatted too when they are JSON), with the position of the first difference marked by `@@@@`.

```
Error in checking text value: Result does not match template d.txt (<root>/tests/.../d.txt), first difference at line 3 (expected 5 lines, actual 5 lines):
  line1
  line2
- expected
+ actual
```

To regenerate templates after an intended change, run once with `-Dmonflabs.tests.saveTemplates=all`: every template checked is rewritten from the current result. `support.setForceTemplateSave(true)` does the same for one `UnitTestSupport` instance only. Alternatively, delete the template files and run with `saveTemplates=missing`. A template name starting with `*` (or a `null` name) disables the check: the assertion always passes. `getTemplateClass()` can be overridden to store the templates under another class's folder; it is used both to save and to load them.

### Direct comparisons

`assertJsonEquals(expected, actual)` compares two JSON values without a file, with the same normalization; strings are parsed. `isJsonEquals` returns a `boolean`. `assertNormalizedTextEquals` and `isNormalizedTextEquals` compare text with normalized line breaks.

Sample: `doc_examples/testing/TestingExamples.java` (`testJsonEquals`)

```java
support.assertJsonEquals("{\"a\":1,\"b\":[true,null]}", JsonObject.of("b", JsonArray.of(true, null), "a", 1));
support.isJsonEquals("{\"a\":1}", "{\"a\":2}");           // false
support.isNormalizedTextEquals("a\nb", "a\r\nb");         // true
```

## Private members

`support.getObjectAccessor(object)` returns a `JavaAccessor` that reads fields and calls methods whatever their visibility; `getClassAccessor(clazz).newObject(...)` calls a constructor, whatever its visibility. Typed variants (`getInt`, `callInt`, `getBoolean`...) check the result type.

Sample: `doc_examples/testing/TestingExamples.java` (`testPrivateAccess`)

```java
static class Counter {
    private int count = 41;
    private int increment(int by) { return count += by; }
}

JavaAccessor acc = support.getObjectAccessor(new Counter());
acc.getInt("count");            // 41
acc.callInt("increment", 1);    // 42
acc.getInt("count");            // 42
```

## Resource-leak detection

The leak detector (package `org.monflabs.tests.leaks`) records every tracked resource opened during a test and reports those still open at the end, with the stack trace of where each was opened. It is a Java agent: `ResourceLeakAgent.install()` attaches Byte Buddy to the running JVM and instruments JDK classes, once per JVM. Installing it appends a helper to the bootstrap class path, which makes the JVM print `Sharing is only supported for boot loader classes because bootstrap classpath has been appended`, and connects that helper to the tracker; `install()` throws an `IllegalStateException` when it cannot, rather than leaving a detector that detects nothing.

Attaching an agent at runtime makes JDK 21+ print a warning (JEP 451) unless the JVM runs with `-XX:+EnableDynamicAgentLoading`. The shared `monflabs-parent` passes it to the surefire forks (`<argLine>@{argLine} -XX:+EnableDynamicAgentLoading ...</argLine>`, `@{argLine}` keeping the JaCoCo agent); a module that sets its own `argLine` should keep both. While nothing is tracked, an instrumented constructor or `close()` only checks a flag. The design notes are in `org/monflabs/tests/leaks/README.md`, next to the sources. `ResourceLeakAgent.setTraceLevel(1)` (or `2`) prints what the detector does.

| Tracked | Reported type | Switch (`ResourceLeakAgent`) |
|---|---|---|
| `FileInputStream`, `FileOutputStream` (and so `FileReader`, `FileWriter`) | `FileInputStream`, `FileOutputStream` | `INSTRUMENT_FILE` (on) |
| `RandomAccessFile` | `RandomAccessFile` | `INSTRUMENT_FILE` (on) |
| File channels, which back `Files.newInputStream()`, `Files.newOutputStream()`, `Files.newBufferedReader()`... | `FileChannelImpl` | `INSTRUMENT_PATH` (on) |
| `ZipFile` | `ZipFile` | `INSTRUMENT_ZIP` (on) |
| Sockets | | `INSTRUMENT_SOCKET` (off: not reliable) |
| JDBC connections, statements, result sets | | `INSTRUMENT_JDBC` (off: not reliable) |

Wrappers such as buffered streams, readers and writers are not tracked themselves: the file stream or channel they wrap is. The switches are static fields, read by `install()`.

`ResourceTracker.getInstance()` is the JVM-wide tracker: `startTracking()` clears it and starts recording, `detectLeaks()` lists what is still open, `stopTracking()` stops recording and releases the tracked resources (they are held strongly while tracking). `startTracking(true)` only records the resources opened by the calling thread, so resources opened concurrently by other threads are not reported. Each `LeakInfo` gives the resource type, a context (the file name when the constructor received one) and `formatReport()`.

Sample: `doc_examples/testing/TestingExamples.java` (`testLeakTracking`)

```java
File file = new File(support.getTargetTempDirectory("doc-examples-leaks", true), "data.txt");
Files.writeString(file.toPath(), "data");

ResourceLeakAgent.install();                 // once per JVM; instruments JDK classes
ResourceTracker tracker = ResourceTracker.getInstance();
tracker.startTracking();
try {
    FileInputStream in = new FileInputStream(file);
    List<ResourceTracker.LeakInfo> leaks = tracker.detectLeaks();
    leaks.size();                                              // 1
    leaks.get(0).getAllocationInfo().getResourceType();        // "FileInputStream"
    leaks.get(0).getAllocationInfo().getContext();             // ends with "data.txt"

    in.close();
    tracker.detectLeaks().isEmpty();                           // true
} finally {
    tracker.stopTracking();
}
```

### In a JUnit 3 test

`__BaseTestCase` checks for leaks around every test as soon as the agent is installed: `setUp()` starts tracking and `tearDown()` fails the test with a report of each leak. Installing the agent in a static block of the test class (or of a common base class) is enough to turn it on:

Sample: `doc_examples/testing/TestingExamples.java` (`LeakyCase`, `testBaseTestCaseChecksLeaks`)

```java
public static class LeakyCase extends __BaseTestCase {
    static {
        ResourceLeakAgent.install();
    }

    private File file;

    @Override
    public void setUp() throws Exception {
        file = new File(support.getTargetTempDirectory("doc-examples-leaks", true), "leaky.txt");
        Files.writeString(file.toPath(), "data");
        super.setUp();   // starts tracking: create fixtures before, or they are tracked too
    }

    public void testForgetsToClose() throws Exception {
        new FileInputStream(file).read();   // never closed
    }
}
// Fails in tearDown():
//   Resource leaks detected:
//   Resource Leak Detected!
//     Resource Type: FileInputStream
//     ...
```

`__BaseTestCase` tracks the resources opened by the test thread only (`startTracking(true)`). Because the agent and the tracker are JVM-wide, once one class installs the agent, every later `__BaseTestCase` in the same JVM is checked too. To install the agent for every `__BaseTestCase` without touching the test classes, run with `-Dmonflabs.tests.trackLeaks=true` (read once, into `__BaseTestCase.TRACK_LEAKS`). The `monflabs-parent` passes the Maven property `monflabs.tests.trackLeaks` to the tests: the Galta JSON and utilities modules set it to `true` in `parent-json/pom.xml` and `parent-utilities/pom.xml` (except `utilities-tests`, see its pom), and `mvn test -Dmonflabs.tests.trackLeaks=false` turns it off.

### In a JUnit 4 test

`ResourceLeakRule` does the same for JUnit 4, with the same thread and report filter as `__BaseTestCase`. It installs the agent on first use; when the test itself also fails, the leak report is added as a suppressed exception.

```java
public class LeakRuleExample {

    @Rule
    public ResourceLeakRule leaks = new ResourceLeakRule();   // installs the agent on first use

    @Test
    public void readsAndCloses() throws Exception {
        Path file = Files.createTempFile("leak-rule", ".txt");
        try (var in = Files.newInputStream(file)) {   // closed: the rule is satisfied
            in.read();
        }
        new File(file.toString()).delete();
    }
}
```

A test that leaves `Files.newInputStream(file)` open fails with `RESOURCE LEAK DETECTION FAILED!`, `Detected 1 leaked resource(s)` and `Resource Type: FileChannelImpl`.

Sample: `doc_examples/testing/LeakRuleExample.java`, `doc_examples/testing/TestingExamples.java` (`testRuleFailsLeakingTest`)

## Suite completeness

Surefire runs only the `All*Tests` suites listed in each module's pom: a test class missing from them never runs, silently. `SuiteGuard` catches that. Add it to the main suite of the module, giving it every suite surefire runs for the module:

```java
public static TestSuite suite() throws Exception {
    TestSuite suite = new TestSuite();
    suite.addTestSuite(MyFeatureTest.class);
    // ...
    // Fails when a test class of the module is missing from its suites
    suite.addTest(SuiteGuard.newTest(AllUtilTests.class, AllUtilDocExamplesTests.class));
    return suite;
}
```

The guard scans the test classes folder (`target/test-classes`) of the first suite for test classes: concrete `TestCase` subclasses with `test*` methods, and classes with JUnit 4 `@Test` methods. It fails when one of them is not in the suites (a class also counts as included when one of its subclasses is), and when the suites run no test at all. A class deliberately left out, such as a benchmark or a test run by another test, is annotated with `@SuiteGuard.NotInSuite("reason")`.
