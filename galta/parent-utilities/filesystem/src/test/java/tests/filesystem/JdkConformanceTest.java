/*
 * Copyright (c) 2019-2026 Philippe Riand
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tests.filesystem;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.DELETE_ON_CLOSE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.monflabs.filesystem.delegate.PathDelegatingFileSystem;
import org.monflabs.filesystem.file.FileFileSystem;
import org.monflabs.filesystem.memory.MemoryFileSystem;
import org.monflabs.filesystem.path.PathFileSystem;
import org.monflabs.util.FileUtil;

import tests.ProjectTestCase;

/**
 * Runs the same operations against the JDK's default filesystem (in a temporary folder)
 * and against each writable filesystem of the module, and compares the outcomes: the
 * returned value, or the exception type (the JDK's type or a subclass of it).
 * <p>
 * The scenarios are the ones a conformance probe found wrong, and their neighbours.
 * They are deterministic and fast. The JDK reference is a Unix one: the test does nothing
 * on Windows.
 */
public class JdkConformanceTest extends ProjectTestCase {

    interface Scenario {
        Object run(Function<String, Path> p) throws Exception;
    }

    /** An environment: a filesystem and how "/x" maps to a path of it. */
    private static final class Env implements Closeable {
        final String name;
        final Function<String, Path> p;
        final Closeable closer;
        Env(String name, Function<String, Path> p, Closeable closer) {
            this.name = name;
            this.p = p;
            this.closer = closer;
        }
        @Override
        public void close() throws IOException {
            if (closer != null) {
                closer.close();
            }
        }
    }

    private static final String[] ENVS = { "jdk", "memory", "file", "pathfs", "delegate", "delegate-memory" };

    private File tempRoot;
    private int counter;

    @Override
    public void setUp() throws IOException {
        tempRoot = new File(support.getTargetTempDirectory(), "JdkConformanceTest");
        FileUtil.deleteFile(tempRoot);
        tempRoot.mkdirs();
    }

    @Override
    public void tearDown() throws IOException {
        FileUtil.deleteFile(tempRoot);
    }

    private static boolean unixReference() {
        return "/".equals(FileSystems.getDefault().getSeparator());
    }

    private Path newFolder() throws IOException {
        Path dir = new File(tempRoot, "env" + (counter++)).toPath();
        Files.createDirectories(dir);
        return dir.toRealPath();
    }

    private Env env(String name) throws IOException {
        switch (name) {
            case "jdk": {
                Path r = newFolder();
                return new Env(name, s -> s.equals("/") ? r : s.startsWith("/") ? r.resolve(s.substring(1)) : r.getFileSystem().getPath(s), null);
            }
            case "memory": {
                MemoryFileSystem fs = MemoryFileSystem.newBuilder().build();
                return new Env(name, fs::getPath, fs);
            }
            case "file": {
                FileFileSystem fs = FileFileSystem.newBuilder().root(newFolder().toFile()).build();
                return new Env(name, fs::getPath, fs);
            }
            case "pathfs": {
                PathFileSystem fs = PathFileSystem.newBuilder().root(newFolder()).build();
                return new Env(name, fs::getPath, fs);
            }
            case "delegate": {
                PathDelegatingFileSystem fs = PathDelegatingFileSystem.newBuilder().root(newFolder()).build();
                return new Env(name, fs::getPath, fs);
            }
            case "delegate-memory": {
                MemoryFileSystem m = MemoryFileSystem.newBuilder().build();
                Files.createDirectories(m.getPath("/r"));
                PathDelegatingFileSystem fs = PathDelegatingFileSystem.newBuilder().root(m.getPath("/r")).build();
                return new Env(name, fs::getPath, () -> {
                    fs.close();
                    m.close();
                });
            }
            default:
                throw new IllegalArgumentException(name);
        }
    }

    private static Object outcome(Scenario sc, Function<String, Path> p) {
        try {
            return String.valueOf(sc.run(p));
        } catch (Throwable t) {
            return t.getClass();
        }
    }

    // The JDK's exception type, or a more precise subclass of it (NotDirectoryException
    // for the JDK's FileSystemException "Not a directory")
    private static boolean same(Object ref, Object got) {
        if (ref instanceof Class && got instanceof Class) {
            return ((Class<?>) ref).isAssignableFrom((Class<?>) got);
        }
        return Objects.equals(ref, got);
    }

    private static String show(Object o) {
        return o instanceof Class ? "!" + ((Class<?>) o).getSimpleName() : String.valueOf(o);
    }

    private void check(Map<String, Scenario> scenarios) throws IOException {
        List<String> diffs = new ArrayList<>();
        for (Map.Entry<String, Scenario> sc : scenarios.entrySet()) {
            Object ref = null;
            for (String n : ENVS) {
                try (Env e = env(n)) {
                    Object o = outcome(sc.getValue(), e.p);
                    if (n.equals("jdk")) {
                        ref = o;
                    } else if (!same(ref, o)) {
                        diffs.add(sc.getKey() + " [" + n + "] jdk=" + show(ref) + " got=" + show(o));
                    }
                }
            }
        }
        assertEquals(String.join("\n", diffs), Collections.emptyList(), diffs);
    }

    // --- helpers used by the scenarios ---

    private static void w(Function<String, Path> p, String s, String content) throws IOException {
        Path x = p.apply(s);
        if (x.getParent() != null) {
            Files.createDirectories(x.getParent());
        }
        Files.writeString(x, content);
    }

    private static void d(Function<String, Path> p, String s) throws IOException {
        Files.createDirectories(p.apply(s));
    }

    private static String r(Function<String, Path> p, String s) throws IOException {
        return Files.readString(p.apply(s));
    }

    private static String tree(Function<String, Path> p) throws IOException {
        Path root = p.apply("/");
        try (Stream<Path> st = Files.walk(root)) {
            return st.map(x -> root.relativize(x).toString() + (Files.isDirectory(x) ? "/" : ""))
                .sorted().collect(Collectors.toList()).toString();
        }
    }

    public void testFilesOperations() throws IOException {
        if (!unixReference()) {
            return;
        }
        Map<String, Scenario> sc = new LinkedHashMap<>();
        sc.put("createFile", p -> { Files.createFile(p.apply("/f")); return Files.exists(p.apply("/f")); });
        sc.put("createFile-exists", p -> { w(p, "/f", "x"); Files.createFile(p.apply("/f")); return 1; });
        sc.put("createFile-noparent", p -> { Files.createFile(p.apply("/nodir/f")); return 1; });
        sc.put("createFile-parentIsFile", p -> { w(p, "/f", "x"); Files.createFile(p.apply("/f/g")); return 1; });
        sc.put("createDirectory-noparent", p -> { Files.createDirectory(p.apply("/x/y")); return 1; });
        sc.put("createDirectory-exists-file", p -> { w(p, "/f", "x"); Files.createDirectory(p.apply("/f")); return 1; });
        sc.put("createDirectories-existing", p -> { d(p, "/a/b"); Files.createDirectories(p.apply("/a/b")); return tree(p); });
        sc.put("delete-missing", p -> { Files.delete(p.apply("/nope")); return 1; });
        sc.put("deleteIfExists-missing", p -> Files.deleteIfExists(p.apply("/nope")));
        sc.put("delete-nonempty", p -> { w(p, "/d/f", "x"); Files.delete(p.apply("/d")); return 1; });
        sc.put("delete-file", p -> { w(p, "/f", "x"); Files.delete(p.apply("/f")); return Files.exists(p.apply("/f")); });
        sc.put("copy-file", p -> { w(p, "/f", "x"); Files.copy(p.apply("/f"), p.apply("/g")); return r(p, "/g"); });
        sc.put("copy-exists", p -> { w(p, "/f", "x"); w(p, "/g", "y"); Files.copy(p.apply("/f"), p.apply("/g")); return 1; });
        sc.put("copy-replace", p -> { w(p, "/f", "x"); w(p, "/g", "y"); Files.copy(p.apply("/f"), p.apply("/g"), REPLACE_EXISTING); return r(p, "/g"); });
        sc.put("copy-replace-nonemptydir", p -> { w(p, "/f", "x"); w(p, "/d/z", "y"); Files.copy(p.apply("/f"), p.apply("/d"), REPLACE_EXISTING); return 1; });
        sc.put("copy-dir-shallow", p -> { w(p, "/d/f", "x"); Files.copy(p.apply("/d"), p.apply("/e")); return tree(p); });
        sc.put("copy-self", p -> { w(p, "/f", "x"); Files.copy(p.apply("/f"), p.apply("/f")); return r(p, "/f"); });
        sc.put("copy-missing", p -> { Files.copy(p.apply("/nope"), p.apply("/g")); return 1; });
        sc.put("copy-noparent", p -> { w(p, "/f", "x"); Files.copy(p.apply("/f"), p.apply("/x/g")); return 1; });
        sc.put("copy-atomic", p -> { w(p, "/f", "x"); Files.copy(p.apply("/f"), p.apply("/g"), ATOMIC_MOVE); return 1; });
        sc.put("copy-attrs", p -> { w(p, "/f", "x"); Files.setLastModifiedTime(p.apply("/f"), FileTime.fromMillis(1000000000000L)); Files.copy(p.apply("/f"), p.apply("/g"), java.nio.file.StandardCopyOption.COPY_ATTRIBUTES); return Files.getLastModifiedTime(p.apply("/g")).toMillis(); });
        sc.put("move-file", p -> { w(p, "/f", "x"); Files.move(p.apply("/f"), p.apply("/g")); return tree(p); });
        sc.put("move-exists", p -> { w(p, "/f", "x"); w(p, "/g", "y"); Files.move(p.apply("/f"), p.apply("/g")); return 1; });
        sc.put("move-replace", p -> { w(p, "/f", "x"); w(p, "/g", "y"); Files.move(p.apply("/f"), p.apply("/g"), REPLACE_EXISTING); return tree(p) + r(p, "/g"); });
        sc.put("move-dir-tree", p -> { w(p, "/d/s/f", "x"); Files.move(p.apply("/d"), p.apply("/e")); return tree(p); });
        sc.put("move-replace-nonemptydir", p -> { w(p, "/f", "x"); w(p, "/d/z", "y"); Files.move(p.apply("/f"), p.apply("/d"), REPLACE_EXISTING); return 1; });
        sc.put("move-keeps-mtime", p -> { w(p, "/f", "x"); Files.setLastModifiedTime(p.apply("/f"), FileTime.fromMillis(1000000000000L)); Files.move(p.apply("/f"), p.apply("/g")); return Files.getLastModifiedTime(p.apply("/g")).toMillis(); });
        sc.put("move-openchannel", p -> {
            try (SeekableByteChannel ch = Files.newByteChannel(p.apply("/f"), CREATE, WRITE)) {
                ch.write(ByteBuffer.wrap("ab".getBytes()));
                Files.move(p.apply("/f"), p.apply("/g"));
                ch.write(ByteBuffer.wrap("cd".getBytes()));
            }
            return r(p, "/g");
        });
        sc.put("fileKey-same-after-move", p -> {
            w(p, "/f", "x");
            Object k = Files.readAttributes(p.apply("/f"), BasicFileAttributes.class).fileKey();
            Files.move(p.apply("/f"), p.apply("/g"));
            return Objects.equals(k, Files.readAttributes(p.apply("/g"), BasicFileAttributes.class).fileKey());
        });
        sc.put("ch-read-missing", p -> { Files.newByteChannel(p.apply("/nope")).close(); return 1; });
        sc.put("ch-read+create-missing", p -> { Files.newByteChannel(p.apply("/nope"), READ, CREATE).close(); return Files.exists(p.apply("/nope")); });
        sc.put("ch-append+read", p -> { w(p, "/f", "x"); Files.newByteChannel(p.apply("/f"), READ, APPEND).close(); return 1; });
        sc.put("ch-append+truncate", p -> { w(p, "/f", "x"); Files.newByteChannel(p.apply("/f"), APPEND, TRUNCATE_EXISTING).close(); return r(p, "/f"); });
        sc.put("ch-createnew-exists", p -> { w(p, "/f", "x"); Files.newByteChannel(p.apply("/f"), CREATE_NEW, WRITE).close(); return 1; });
        sc.put("ch-truncate-readonly", p -> { w(p, "/f", "hello"); Files.newByteChannel(p.apply("/f"), READ, TRUNCATE_EXISTING).close(); return r(p, "/f"); });
        sc.put("ch-sparse", p -> {
            try (SeekableByteChannel ch = Files.newByteChannel(p.apply("/f"), CREATE, WRITE)) {
                ch.position(5);
                ch.write(ByteBuffer.wrap("x".getBytes()));
            }
            return Arrays.toString(Files.readAllBytes(p.apply("/f")));
        });
        sc.put("ch-pos-beyond-write0", p -> {
            try (SeekableByteChannel ch = Files.newByteChannel(p.apply("/f"), CREATE, WRITE)) {
                ch.position(5);
                ch.write(ByteBuffer.allocate(0));
                return ch.size();
            }
        });
        sc.put("ch-read-beyond", p -> { w(p, "/f", "abc"); try (SeekableByteChannel ch = Files.newByteChannel(p.apply("/f"))) { ch.position(10); return ch.read(ByteBuffer.allocate(4)) + "," + ch.position(); } });
        sc.put("ch-read-emptybuf-atEOF", p -> { w(p, "/f", "abc"); try (SeekableByteChannel ch = Files.newByteChannel(p.apply("/f"))) { ch.position(3); return ch.read(ByteBuffer.allocate(0)); } });
        sc.put("ch-truncate-pos", p -> { w(p, "/f", "abcdef"); try (SeekableByteChannel ch = Files.newByteChannel(p.apply("/f"), WRITE)) { ch.position(5); ch.truncate(2); return ch.position() + "," + ch.size(); } });
        sc.put("ch-append-content", p -> { w(p, "/f", "abc"); try (SeekableByteChannel ch = Files.newByteChannel(p.apply("/f"), APPEND)) { ch.position(0); ch.write(ByteBuffer.wrap("Z".getBytes())); } return r(p, "/f"); });
        sc.put("ch-closed-read", p -> { w(p, "/f", "abc"); SeekableByteChannel ch = Files.newByteChannel(p.apply("/f")); ch.close(); ch.read(ByteBuffer.allocate(1)); return 1; });
        sc.put("ch-write-readonlych", p -> { w(p, "/f", "abc"); try (SeekableByteChannel ch = Files.newByteChannel(p.apply("/f"))) { ch.write(ByteBuffer.wrap("x".getBytes())); } return 1; });
        sc.put("ch-delete-on-close", p -> { w(p, "/f", "abc"); Files.newByteChannel(p.apply("/f"), READ, DELETE_ON_CLOSE).close(); return Files.exists(p.apply("/f")); });
        sc.put("newOutputStream-default", p -> { w(p, "/f", "abcdef"); try (java.io.OutputStream os = Files.newOutputStream(p.apply("/f"))) { os.write('Z'); } return r(p, "/f"); });
        sc.put("size-file", p -> { w(p, "/f", "hello"); return Files.size(p.apply("/f")); });
        sc.put("attrs-missing", p -> { Files.readAttributes(p.apply("/nope"), BasicFileAttributes.class); return 1; });
        sc.put("attrs-map", p -> { w(p, "/f", "hello"); return new TreeMap<>(Files.readAttributes(p.apply("/f"), "size,isRegularFile,isDirectory")); });
        sc.put("setTimes", p -> { w(p, "/f", "hello"); Files.setLastModifiedTime(p.apply("/f"), FileTime.fromMillis(1234000)); return Files.getLastModifiedTime(p.apply("/f")).toMillis(); });
        sc.put("isSymbolicLink-file", p -> { w(p, "/f", "x"); return Files.isSymbolicLink(p.apply("/f")); });
        sc.put("exists-nofollow", p -> { w(p, "/f", "x"); return Files.exists(p.apply("/f"), LinkOption.NOFOLLOW_LINKS); });
        sc.put("isExecutable-dir", p -> { d(p, "/d"); return Files.isExecutable(p.apply("/d")); });
        sc.put("list", p -> { w(p, "/d/a", "x"); w(p, "/d/b", "x"); d(p, "/d/c"); try (Stream<Path> s = Files.list(p.apply("/d"))) { return s.map(x -> x.getFileName().toString()).sorted().collect(Collectors.toList()); } });
        sc.put("list-entries-resolve-against-dir", p -> { w(p, "/d/a", "x"); Path dir = p.apply("/d/../d"); try (Stream<Path> s = Files.list(dir)) { return s.map(x -> x.equals(dir.resolve("a"))).collect(Collectors.toList()); } });
        sc.put("list-file", p -> { w(p, "/f", "x"); Files.list(p.apply("/f")).close(); return 1; });
        sc.put("list-missing", p -> { Files.list(p.apply("/nope")).close(); return 1; });
        sc.put("dirstream-glob", p -> {
            w(p, "/d/a.txt", "x"); w(p, "/d/b.java", "x"); w(p, "/d/c.TXT", "x");
            List<String> l = new ArrayList<>();
            try (java.nio.file.DirectoryStream<Path> ds = Files.newDirectoryStream(p.apply("/d"), "*.{txt,java}")) {
                for (Path x : ds) {
                    l.add(x.getFileName().toString());
                }
            }
            Collections.sort(l);
            return l;
        });
        sc.put("walk-tree", p -> { w(p, "/a/b/c.txt", "x"); w(p, "/a/d.txt", "y"); d(p, "/e"); return tree(p); });
        sc.put("isSameFile-dotdot", p -> { w(p, "/d/f", "x"); return Files.isSameFile(p.apply("/d/f"), p.apply("/d/../d/f")); });
        sc.put("isSameFile-different", p -> { w(p, "/f", "x"); w(p, "/g", "x"); return Files.isSameFile(p.apply("/f"), p.apply("/g")); });
        sc.put("isSameFile-missing", p -> Files.isSameFile(p.apply("/nope"), p.apply("/nope2")));
        sc.put("isSameFile-missing-same", p -> Files.isSameFile(p.apply("/nope"), p.apply("/nope")));
        sc.put("isHidden-dot", p -> { w(p, "/.h", "x"); return Files.isHidden(p.apply("/.h")); });
        sc.put("toRealPath-missing", p -> { p.apply("/nope").toRealPath(); return 1; });
        sc.put("filestore-missing", p -> { Files.getFileStore(p.apply("/nope")); return 1; });
        sc.put("filestore-usable", p -> { w(p, "/f", "x"); return Files.getFileStore(p.apply("/f")).getUsableSpace() >= 0; });
        sc.put("checkAccess-missing", p -> { p.apply("/").getFileSystem().provider().checkAccess(p.apply("/nope")); return 1; });
        sc.put("unicode-name", p -> { w(p, "/é ü #%.txt", "x"); try (Stream<Path> s = Files.list(p.apply("/"))) { return s.map(x -> x.getFileName().toString()).collect(Collectors.toList()); } });
        sc.put("lines-utf8", p -> { Files.write(p.apply("/f"), List.of("é", "b"), StandardCharsets.UTF_8); return Files.readAllLines(p.apply("/f")); });
        check(sc);
    }

    public void testPathSyntax() throws IOException {
        if (!unixReference()) {
            return;
        }
        String[] inputs = { "", "/", ".", "..", "a", "a/", "a//b", "/a/b/", "/a/./b/../c", "a/..", "../a", "/..", "/../a",
            "./a", "a/b/c", "é/ü", "a\\b", "x:", "a b", "a/.", ".hidden", "a/../.." };
        String[] others = { "", "a", "a/b", "/a", "/a/b", "..", "b", "a\\b" };
        List<String> ops = new ArrayList<>(List.of("toString", "getFileName", "getParent", "getRoot", "getNameCount",
            "isAbsolute", "normalize", "names", "subpath01", "getName0"));
        for (String o : others) {
            for (String op : new String[] { "resolve", "relativize", "startsWith", "endsWith", "resolveSibling", "compareTo" }) {
                ops.add(op + ":" + o);
            }
        }
        Map<String, Scenario> sc = new LinkedHashMap<>();
        for (String in : inputs) {
            for (String op : ops) {
                sc.put("\"" + in + "\" " + op, p -> pathOp(p.apply("/").getFileSystem(), in, op));
            }
        }
        check(sc);
    }

    private static Object pathOp(FileSystem fs, String s, String op) {
        Path p = fs.getPath(s);
        int colon = op.indexOf(':');
        if (colon > 0) {
            Path q = fs.getPath(op.substring(colon + 1));
            switch (op.substring(0, colon)) {
                case "resolve": return p.resolve(q);
                case "relativize": return p.relativize(q);
                case "startsWith": return p.startsWith(q);
                case "endsWith": return p.endsWith(q);
                case "resolveSibling": return p.resolveSibling(q);
                case "compareTo": return Integer.signum(p.compareTo(q));
                default: throw new IllegalArgumentException(op);
            }
        }
        switch (op) {
            case "toString": return p.toString();
            case "getFileName": return p.getFileName();
            case "getParent": return p.getParent();
            case "getRoot": return p.getRoot();
            case "getNameCount": return p.getNameCount();
            case "isAbsolute": return p.isAbsolute();
            case "normalize": return "'" + p.normalize() + "'";
            case "names": {
                List<String> l = new ArrayList<>();
                for (Path x : p) {
                    l.add("'" + x + "'");
                }
                return l;
            }
            case "subpath01": return p.subpath(0, 1);
            case "getName0": return p.getName(0);
            default: throw new IllegalArgumentException(op);
        }
    }

    /**
     * For any two paths of the same type, p.resolve(p.relativize(q)).normalize() is
     * q.normalize() - whenever relativize() succeeds.
     */
    public void testRelativizeResolvesBack() throws IOException {
        String[] paths = { "", ".", "..", "a", "a/b", "a/..", "./a", "../a", "a/./b/../c", "a/b/c/d", "b/../../c" };
        String[] absolute = { "/", "/a", "/a/b", "/a/./b/../c", "/..", "/../a", "/x/y/z" };
        for (String n : ENVS) {
            try (Env e = env(n)) {
                FileSystem fs = e.p.apply("/").getFileSystem();
                for (String[] set : new String[][] { paths, absolute }) {
                    for (String a : set) {
                        for (String b : set) {
                            Path p = fs.getPath(a);
                            Path q = fs.getPath(b);
                            Path rel;
                            try {
                                rel = p.relativize(q);
                            } catch (IllegalArgumentException ex) {
                                continue;
                            }
                            assertEquals(n + ": " + a + " -> " + b, q.normalize(), p.resolve(rel).normalize());
                        }
                    }
                }
            }
        }
    }

    public void testForeignPaths() throws IOException {
        if (!unixReference()) {
            return;
        }
        Map<String, Scenario> sc = new LinkedHashMap<>();
        Path foreign = MemoryFileSystem.newBuilder().build().getPath("/x");
        Path foreignDefault = java.nio.file.Paths.get("/x");
        sc.put("resolve-foreign", p -> p.apply("/").resolve(foreign(p, foreign, foreignDefault)));
        sc.put("relativize-foreign", p -> p.apply("/").relativize(foreign(p, foreign, foreignDefault)));
        sc.put("compareTo-foreign", p -> p.apply("/").compareTo(foreign(p, foreign, foreignDefault)));
        sc.put("startsWith-foreign", p -> p.apply("/").startsWith(foreign(p, foreign, foreignDefault)));
        sc.put("nul-character", p -> p.apply("/").getFileSystem().getPath("a\0b"));
        check(sc);
    }

    // A path of another provider than p's
    private static Path foreign(Function<String, Path> p, Path memory, Path defaultPath) {
        return p.apply("/").getFileSystem() == FileSystems.getDefault() ? memory : defaultPath;
    }

    public void testPathMatchers() throws IOException {
        if (!unixReference()) {
            return;
        }
        Map<String, Scenario> sc = new LinkedHashMap<>();
        sc.put("glob-empty", p -> p.apply("/").getFileSystem().getPathMatcher("glob:").matches(p.apply("/").getFileSystem().getPath("")));
        sc.put("glob-empty-vs-name", p -> p.apply("/").getFileSystem().getPathMatcher("glob:").matches(p.apply("/").getFileSystem().getPath("a")));
        sc.put("regex-empty", p -> p.apply("/").getFileSystem().getPathMatcher("regex:").matches(p.apply("/").getFileSystem().getPath("")));
        sc.put("glob-class-slash", p -> p.apply("/").getFileSystem().getPathMatcher("glob:a[/]b").matches(p.apply("/").getFileSystem().getPath("a/b")));
        sc.put("glob-negated-class-slash", p -> p.apply("/").getFileSystem().getPathMatcher("glob:a[!x]b").matches(p.apply("/").getFileSystem().getPath("a/b")));
        sc.put("glob-star", p -> p.apply("/").getFileSystem().getPathMatcher("glob:*.txt").matches(p.apply("/").getFileSystem().getPath("a.txt")));
        sc.put("glob-star-dir", p -> p.apply("/").getFileSystem().getPathMatcher("glob:*").matches(p.apply("/").getFileSystem().getPath("a/b")));
        sc.put("glob-starstar", p -> p.apply("/").getFileSystem().getPathMatcher("glob:**/*.txt").matches(p.apply("/").getFileSystem().getPath("a/b/c.txt")));
        sc.put("glob-backslash-name", p -> p.apply("/").getFileSystem().getPathMatcher("glob:a\\\\b").matches(p.apply("/").getFileSystem().getPath("a\\b")));
        sc.put("matcher-no-colon", p -> p.apply("/").getFileSystem().getPathMatcher("x"));
        sc.put("matcher-bad-syntax", p -> p.apply("/").getFileSystem().getPathMatcher("foo:x"));
        check(sc);
    }
}
