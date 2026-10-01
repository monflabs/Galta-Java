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

import static org.junit.Assert.assertThrows;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.ReadOnlyFileSystemException;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.monflabs.filesystem.delegate.PathDelegatingFileSystem;
import org.monflabs.filesystem.file.FileFileSystem;
import org.monflabs.filesystem.file.FileFileSystemProvider;
import org.monflabs.filesystem.memory.MemoryFileSystem;
import org.monflabs.filesystem.memory.MemoryFileSystemProvider;
import org.monflabs.filesystem.path.PathFileSystem;
import org.monflabs.filesystem.path.PathFileSystemProvider;
import org.monflabs.filesystem.resources.ResourceFileSystem;
import org.monflabs.filesystem.resources.ResourceFileSystemProvider;
import org.monflabs.filesystem.zip.ZipFileSystem;
import org.monflabs.filesystem.zip.ZipFileSystemProvider;
import org.monflabs.util.FileUtil;
import org.monflabs.util.path.FileSystemRuntimeException;

import tests.ProjectTestCase;

/**
 * Regression tests for the conformance review of the filesystems: closed filesystems,
 * memory moves and channel options, ZIP and resource edge cases, sandbox links, URIs,
 * attribute views.
 */
public class FileSystemFixesTest extends ProjectTestCase {

    private File base;

    @Override
    public void setUp() throws IOException {
        base = new File(support.getTargetTempDirectory(), "FileSystemFixesTest");
        FileUtil.deleteFile(base);
        base.mkdirs();
    }

    @Override
    public void tearDown() {
        FileUtil.deleteFile(base);
    }

    private File folder(String name) {
        File f = new File(base, name);
        f.mkdirs();
        return f;
    }

    private Path zip(String name, String... entriesAndContents) throws IOException {
        Path zip = new File(base, name).toPath();
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (int i = 0; i < entriesAndContents.length; i += 2) {
                out.putNextEntry(new ZipEntry(entriesAndContents[i]));
                out.write(entriesAndContents[i + 1].getBytes());
                out.closeEntry();
            }
        }
        return zip;
    }

    private static boolean posix() {
        return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    }

    // ==================== Closed filesystems ====================

    private void checkClosed(FileSystem fs, Path file, Path dir) throws IOException {
        fs.close();
        assertThrows(ClosedFileSystemException.class, () -> Files.exists(file));
        assertThrows(ClosedFileSystemException.class, () -> Files.readAllBytes(file));
        assertThrows(ClosedFileSystemException.class, () -> Files.newInputStream(file));
        assertThrows(ClosedFileSystemException.class, () -> Files.list(dir));
        assertThrows(ClosedFileSystemException.class, () -> Files.readAttributes(file, BasicFileAttributes.class));
        assertThrows(ClosedFileSystemException.class, () -> Files.delete(file));
        assertThrows(ClosedFileSystemException.class, () -> Files.write(file, "x".getBytes()));
        assertThrows(ClosedFileSystemException.class, () -> fs.getRootDirectories());
        assertThrows(ClosedFileSystemException.class, () -> fs.getFileStores());
        // Lexical path operations still work
        assertEquals(file, file.getParent().resolve(file.getFileName()));
    }

    public void testClosedFileSystemsRefuseOldPaths() throws IOException {
        MemoryFileSystem memory = MemoryFileSystem.newBuilder().build();
        Files.createDirectories(memory.getPath("/d"));
        Files.writeString(memory.getPath("/d/f"), "x");
        checkClosed(memory, memory.getPath("/d/f"), memory.getPath("/d"));

        File root = folder("closed");
        Files.writeString(new File(root, "f").toPath(), "x");
        FileFileSystem file = FileFileSystem.newBuilder().root(root).build();
        checkClosed(file, file.getPath("/f"), file.getPath("/"));
        PathFileSystem pathfs = PathFileSystem.newBuilder().root(root.toPath()).build();
        checkClosed(pathfs, pathfs.getPath("/f"), pathfs.getPath("/"));
        PathDelegatingFileSystem delegate = PathDelegatingFileSystem.newBuilder().root(root.toPath()).build();
        checkClosed(delegate, delegate.getPath("/f"), delegate.getPath("/"));

        ZipFileSystem zip = ZipFileSystem.newBuilder().zipFile(zip("closed.zip", "d/f", "x")).build();
        FileStore store = zip.getFileStores().iterator().next();
        checkClosed(zip, zip.getPath("/d/f"), zip.getPath("/d"));
        // Not the IllegalStateException of the closed ZipFile
        assertThrows(ClosedFileSystemException.class, () -> store.getTotalSpace());
    }

    // ==================== relativize ====================

    public void testRelativizeHandlesDots() throws IOException {
        try (FileSystem fs = MemoryFileSystem.newBuilder().build()) {
            assertEquals("..", fs.getPath("/a/./b/../c").relativize(fs.getPath("/a")).toString());
            assertEquals("a", fs.getPath(".").relativize(fs.getPath("a")).toString());
            assertEquals("b", fs.getPath("/../a").relativize(fs.getPath("/a/b")).toString());
            assertEquals("", fs.getPath("a/../..").relativize(fs.getPath("..")).toString());
            assertThrows(IllegalArgumentException.class, () -> fs.getPath("../a").relativize(fs.getPath("b")));
            assertThrows(IllegalArgumentException.class, () -> fs.getPath("/a").relativize(fs.getPath("b")));
        }
    }

    // ==================== Memory moves ====================

    public void testMemoryMoveRelinksTheNode() throws IOException {
        try (MemoryFileSystem fs = MemoryFileSystem.newBuilder().build()) {
            Files.createDirectories(fs.getPath("/d/sub"));
            Files.writeString(fs.getPath("/d/sub/f.txt"), "abc");
            Object key = Files.readAttributes(fs.getPath("/d/sub/f.txt"), BasicFileAttributes.class).fileKey();
            long used = fs.getTotalUsedSpace();
            try (SeekableByteChannel ch = Files.newByteChannel(fs.getPath("/d/sub/f.txt"), StandardOpenOption.APPEND)) {
                Files.move(fs.getPath("/d"), fs.getPath("/e"));
                // The open channel writes to the moved file
                ch.write(ByteBuffer.wrap("def".getBytes()));
            }
            assertEquals("abcdef", Files.readString(fs.getPath("/e/sub/f.txt")));
            assertEquals(key, Files.readAttributes(fs.getPath("/e/sub/f.txt"), BasicFileAttributes.class).fileKey());
            assertFalse(Files.exists(fs.getPath("/d")));
            assertEquals(used + 3, fs.getTotalUsedSpace());
            assertEquals(List.of("f.txt"), names(fs.getPath("/e/sub")));
            // Renamed in place
            Files.move(fs.getPath("/e/sub/f.txt"), fs.getPath("/e/sub/g.txt"));
            assertEquals(List.of("g.txt"), names(fs.getPath("/e/sub")));
            assertEquals("g.txt", fs.getPath("/e/sub/g.txt").getFileName().toString());
        }
    }

    private static List<String> names(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.map(p -> p.getFileName().toString()).sorted().collect(Collectors.toList());
        }
    }

    public void testMemoryMoveAcrossFileSystemsCopies() throws IOException {
        try (MemoryFileSystem a = MemoryFileSystem.newBuilder().build(); MemoryFileSystem b = MemoryFileSystem.newBuilder().build()) {
            Files.createDirectories(a.getPath("/d/s"));
            Files.writeString(a.getPath("/d/s/f"), "x");
            Files.move(a.getPath("/d"), b.getPath("/e"));
            assertEquals("x", Files.readString(b.getPath("/e/s/f")));
            assertFalse(Files.exists(a.getPath("/d")));
            assertEquals(0, a.getTotalUsedSpace());
            assertEquals(1, b.getTotalUsedSpace());
        }
    }

    // ==================== Memory "." and ".." against the actual nodes ====================

    public void testMemoryDotDotNeedsADirectory() throws IOException {
        try (MemoryFileSystem fs = MemoryFileSystem.newBuilder().build()) {
            Files.createDirectories(fs.getPath("/d/sub"));
            Files.writeString(fs.getPath("/d/x"), "file");
            assertThrows(FileSystemException.class, () -> Files.writeString(fs.getPath("/d/x/../y"), "y"));
            assertFalse(Files.exists(fs.getPath("/d/y")));
            assertThrows(FileSystemException.class, () -> Files.delete(fs.getPath("/d/x/..")));
            assertTrue(Files.isDirectory(fs.getPath("/d")));
            assertThrows(NoSuchFileException.class, () -> Files.writeString(fs.getPath("/d/missing/../y"), "y"));
            assertFalse(Files.exists(fs.getPath("/d/x/..")));
            // Through a real directory it works
            Files.writeString(fs.getPath("/d/sub/../y"), "y");
            assertEquals("y", Files.readString(fs.getPath("/d/y")));
            assertTrue(Files.isDirectory(fs.getPath("/d/sub/..")));
        }
    }

    // ==================== Memory channel options ====================

    public void testMemoryChannelOptions() throws IOException {
        try (MemoryFileSystem fs = MemoryFileSystem.newBuilder().build()) {
            Path f = fs.getPath("/f");
            Files.writeString(f, "abc");
            assertThrows(IllegalArgumentException.class, () -> Files.newByteChannel(f, StandardOpenOption.READ, StandardOpenOption.APPEND));
            assertThrows(IllegalArgumentException.class, () -> Files.newByteChannel(f, StandardOpenOption.APPEND, StandardOpenOption.TRUNCATE_EXISTING));
            assertThrows(NoSuchFileException.class, () -> Files.newByteChannel(fs.getPath("/new"), StandardOpenOption.READ, StandardOpenOption.CREATE));
            assertFalse(Files.exists(fs.getPath("/new")));
            assertThrows(UnsupportedOperationException.class, () -> Files.createFile(fs.getPath("/g"),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))));
            assertThrows(UnsupportedOperationException.class, () -> Files.createDirectory(fs.getPath("/dir"),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))));
            try (SeekableByteChannel ch = Files.newByteChannel(f, StandardOpenOption.WRITE)) {
                ch.position(10);
                ch.write(ByteBuffer.allocate(0));
                assertEquals(3, ch.size());
            }
            Files.newByteChannel(f, StandardOpenOption.READ, StandardOpenOption.DELETE_ON_CLOSE).close();
            assertFalse(Files.exists(f));
        }
    }

    // ==================== Memory accounting ====================

    public void testMemoryAccounting() throws IOException {
        try (MemoryFileSystem fs = MemoryFileSystem.newBuilder().build()) {
            Path f = fs.getPath("/f");
            Files.write(f, new byte[100_000]);
            assertEquals(100_000, fs.getTotalUsedSpace());
            try (SeekableByteChannel ch = Files.newByteChannel(f, StandardOpenOption.WRITE)) {
                ch.truncate(10);
            }
            assertEquals(10, fs.getTotalUsedSpace());
            Files.write(fs.getPath("/g"), new byte[5]);
            assertEquals(15, fs.getTotalUsedSpace());
            Files.delete(f);
            assertEquals(5, fs.getTotalUsedSpace());
            FileStore store = Files.getFileStore(fs.getPath("/g"));
            assertEquals(Runtime.getRuntime().maxMemory(), store.getTotalSpace());
            assertTrue(store.getUsableSpace() >= 0);
            assertTrue(store.supportsFileAttributeView(BasicFileAttributeView.class));
            assertTrue(store.supportsFileAttributeView("basic"));
            assertThrows(NoSuchFileException.class, () -> Files.getFileStore(fs.getPath("/missing")));
        }
    }

    public void testMemorySmallFixes() throws IOException {
        try (MemoryFileSystem fs = MemoryFileSystem.newBuilder().build()) {
            Files.createDirectory(fs.getPath("/d"));
            assertTrue(Files.isExecutable(fs.getPath("/d")));
            assertThrows(NoSuchFileException.class, () -> Files.isSameFile(fs.getPath("/a"), fs.getPath("/b")));
            assertTrue(Files.isSameFile(fs.getPath("/a"), fs.getPath("/a")));
            Files.writeString(fs.getPath("/f"), "x");
            assertThrows(UnsupportedOperationException.class, () -> Files.copy(fs.getPath("/f"), fs.getPath("/g"), StandardCopyOption.ATOMIC_MOVE));
            assertTrue(Files.isSameFile(fs.getPath("/f"), fs.getPath("/d/../f")));
            // A backslash is an ordinary character of a name on a "/" filesystem
            assertEquals(1, fs.getPath("a\\b").getNameCount());
            Files.writeString(fs.getPath("/a\\b"), "y");
            assertEquals(List.of("a\\b", "d", "f"), names(fs.getPath("/")));
            assertThrows(java.nio.file.InvalidPathException.class, () -> fs.getPath("a\0b"));
        }
    }

    // ==================== Directory entries are dir.resolve(name) ====================

    public void testRelativeListingStaysRelative() throws IOException {
        File root = folder("rel");
        Files.createDirectories(new File(root, "d").toPath());
        Files.writeString(new File(root, "d/a").toPath(), "x");
        try (MemoryFileSystem m = MemoryFileSystem.newBuilder().build();
             FileSystem f = FileFileSystem.newBuilder().root(root).build();
             FileSystem p = PathFileSystem.newBuilder().root(root.toPath()).build();
             FileSystem dl = PathDelegatingFileSystem.newBuilder().root(root.toPath()).build()) {
            Files.createDirectories(m.getPath("/d"));
            Files.writeString(m.getPath("/d/a"), "x");
            for (FileSystem fs : new FileSystem[] { m, f, p, dl }) {
                Path dir = fs.getPath("d");
                try (Stream<Path> s = Files.list(dir)) {
                    assertEquals(fs.toString(), List.of(fs.getPath("d/a")), s.collect(Collectors.toList()));
                }
                Path dotted = fs.getPath("/d/../d");
                try (Stream<Path> s = Files.list(dotted)) {
                    assertEquals(fs.toString(), List.of(dotted.resolve("a")), s.collect(Collectors.toList()));
                }
            }
        }
    }

    // ==================== URIs ====================

    public void testMemoryUriKeepsTheAuthority() throws IOException {
        MemoryFileSystemProvider provider = new MemoryFileSystemProvider(true);
        try (FileSystem t1 = provider.newFileSystem(URI.create("memory://tenant1/"), new HashMap<>());
             FileSystem t2 = provider.newFileSystem(URI.create("memory://tenant2/"), new HashMap<>())) {
            Files.writeString(t1.getPath("/secret.txt"), "one");
            URI uri = t1.getPath("/secret.txt").toUri();
            assertEquals("memory://tenant1/secret.txt", uri.toString());
            Path back = provider.getPath(uri);
            assertSame(t1, back.getFileSystem());
            assertEquals("one", Files.readString(back));
            assertSame(t2, provider.getPath(t2.getPath("/x").toUri()).getFileSystem());
        }
    }

    public void testUnsandboxedUrisUseTheProviderScheme() throws IOException {
        File root = folder("uris");
        File file = new File(root, "a b.txt");
        Files.writeString(file.toPath(), "x");
        FileFileSystemProvider fp = new FileFileSystemProvider(true);
        try (FileSystem fs = fp.newFileSystem(URI.create("file-impl:///"), new HashMap<>())) {
            Path p = fs.getPath(file.getAbsolutePath());
            URI uri = p.toUri();
            assertEquals("file-impl", uri.getScheme());
            assertEquals(p, fp.getPath(uri));
            assertEquals("x", Files.readString(fp.getPath(uri)));
        }
        PathFileSystemProvider pp = new PathFileSystemProvider(true);
        try (FileSystem fs = pp.newFileSystem(URI.create("pathfs:///"), new HashMap<>())) {
            Path p = fs.getPath(file.getAbsolutePath());
            URI uri = p.toUri();
            assertEquals("pathfs", uri.getScheme());
            assertEquals("x", Files.readString(pp.getPath(uri)));
        }
    }

    public void testZipUriNamesTheArchive() throws IOException {
        Path zip = zip("named.zip", "a/b.txt", "b");
        try (ZipFileSystem fs = ZipFileSystem.newBuilder().zipFile(zip).build()) {
            URI uri = fs.getPath("/a/b.txt").toUri();
            assertEquals("zip", uri.getScheme());
            assertEquals(zip.toAbsolutePath().toUri().getPath() + "!/a/b.txt", uri.getPath());
        }
        ZipFileSystemProvider registered = new ZipFileSystemProvider(true);
        try (ZipFileSystem fs = ZipFileSystem.newBuilder().provider(registered).zipFile(zip).build()) {
            Path back = registered.getPath(fs.getPath("/a/b.txt").toUri());
            assertEquals("b", Files.readString(back));
        }
    }

    // ==================== Resources ====================

    private ClassLoader resourceLoader(File dir, String manifest, String... files) throws IOException {
        dir.mkdirs();
        Files.writeString(new File(dir, "resources.manifest").toPath(), manifest);
        for (String f : files) {
            File file = new File(dir, f);
            file.getParentFile().mkdirs();
            Files.writeString(file.toPath(), "content of " + f);
        }
        return new URLClassLoader(new URL[] { dir.toURI().toURL() }, null);
    }

    public void testResourceUriRoundTrip() throws IOException {
        File dir = folder("res-uri");
        try (URLClassLoader cl = (URLClassLoader) resourceLoader(new File(dir, "root"), "a/b.txt\n", "a/b.txt")) {
            ResourceFileSystemProvider registered = new ResourceFileSystemProvider(true);
            try (ResourceFileSystem fs = ResourceFileSystem.newBuilder().provider(registered).classLoader(cl).build()) {
                URI uri = fs.getPath("/a/b.txt").toUri();
                assertEquals("resource", uri.getScheme());
                Path back = registered.getPath(uri);
                assertEquals(fs.getPath("/a/b.txt"), back);
                assertEquals("content of a/b.txt", Files.readString(back));
            }
        }
        try (URLClassLoader cl = new URLClassLoader(new URL[] { dir.toURI().toURL() }, null)) {
            ResourceFileSystemProvider registered = new ResourceFileSystemProvider(true);
            try (ResourceFileSystem fs = ResourceFileSystem.newBuilder().provider(registered).classLoader(cl).root("root").build()) {
                URI uri = fs.getPath("/a/b.txt").toUri();
                assertEquals("/root!/a/b.txt", uri.getPath());
                assertEquals("content of a/b.txt", Files.readString(registered.getPath(uri)));
            }
        }
    }

    public void testResourceManifestIsSanitized() throws IOException {
        File dir = folder("res-manifest");
        String manifest = "../evil.txt\n./dot.txt\na//b.txt\na/./c.txt\na/../d.txt\n/abs.txt\nok/x.txt\nback\\slash.txt\n";
        try (URLClassLoader cl = (URLClassLoader) resourceLoader(dir, manifest, "abs.txt", "ok/x.txt");
             ResourceFileSystem fs = ResourceFileSystem.newBuilder().classLoader(cl).build()) {
            List<String> all;
            try (Stream<Path> s = Files.walk(fs.getPath("/"))) {
                all = s.map(Path::toString).sorted().collect(Collectors.toList());
            }
            assertEquals(List.of("/", "/abs.txt", "/ok", "/ok/x.txt"), all);
            assertEquals(Set.of("abs.txt", "ok/x.txt"), fs.getResourcePaths());
        }
    }

    public void testResourceSizeLimitAndStreaming() throws IOException {
        File dir = folder("res-big");
        try (URLClassLoader cl = (URLClassLoader) resourceLoader(dir, "big.txt\n", "big.txt");
             ResourceFileSystem fs = ResourceFileSystem.newBuilder().classLoader(cl).maxEntrySize(5).build()) {
            Path big = fs.getPath("/big.txt");
            assertThrows(FileSystemException.class, () -> Files.readAllBytes(big));
            try (InputStream in = Files.newInputStream(big)) {
                assertEquals("content of big.txt", new String(in.readAllBytes()));
            }
            assertEquals(18, Files.size(big));
            // Normalized identity
            assertTrue(Files.isSameFile(big, fs.getPath("/x/../big.txt")));
            assertEquals(Files.readAttributes(big, BasicFileAttributes.class).fileKey(),
                Files.readAttributes(fs.getPath("/x/../big.txt"), BasicFileAttributes.class).fileKey());
            assertThrows(NoSuchFileException.class, () -> Files.isSameFile(big, fs.getPath("/nope")));
        }
    }

    // ==================== ZIP ====================

    public void testZipSizeLimitAndStreaming() throws IOException {
        Path zip = zip("big.zip", "big.txt", "0123456789");
        try (ZipFileSystem fs = ZipFileSystem.newBuilder().zipFile(zip).maxEntrySize(4).build()) {
            Path big = fs.getPath("/big.txt");
            FileSystemException e = assertThrows(FileSystemException.class, () -> Files.readAllBytes(big));
            assertTrue(e.getMessage(), e.getMessage().contains("newInputStream"));
            try (InputStream in = Files.newInputStream(big)) {
                assertEquals("0123456789", new String(in.readAllBytes()));
            }
            // Files.copy() streams too
            try (MemoryFileSystem m = MemoryFileSystem.newBuilder().build()) {
                Files.copy(big, m.getPath("/big.txt"));
                assertEquals("0123456789", Files.readString(m.getPath("/big.txt")));
            }
        }
        try (ZipFileSystem fs = ZipFileSystem.newBuilder().zipFile(zip).build()) {
            assertEquals(ZipFileSystemProvider.DEFAULT_MAX_ENTRY_SIZE, fs.getMaxEntrySize());
            assertEquals("0123456789", Files.readString(fs.getPath("/big.txt")));
        }
    }

    public void testZipDuplicateEntriesAreConsistent() throws IOException {
        // Two entries named "dup.txt": written as "dupA.txt"/"dupB.txt", then renamed in place
        Path zip = zip("dup.zip", "dupA.txt", "first", "dupB.txt", "second entry");
        byte[] bytes = Files.readAllBytes(zip);
        String s = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1).replace("dupB.txt", "dupA.txt");
        Files.write(zip, s.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        try (ZipFileSystem fs = ZipFileSystem.newBuilder().zipFile(zip).build()) {
            Path dup = fs.getPath("/dupA.txt");
            byte[] content = Files.readAllBytes(dup);
            // The attributes describe the content that is read
            assertEquals(content.length, Files.size(dup));
            try (InputStream in = Files.newInputStream(dup)) {
                assertTrue(Arrays.equals(content, in.readAllBytes()));
            }
            try (Stream<Path> st = Files.list(fs.getPath("/"))) {
                assertEquals(1, st.count());
            }
        }
    }

    public void testZipFileWithChildrenIsADirectory() throws IOException {
        Path zip = zip("fdir.zip", "f", "file", "f/child.txt", "child");
        try (ZipFileSystem fs = ZipFileSystem.newBuilder().zipFile(zip).build()) {
            Path f = fs.getPath("/f");
            assertTrue(Files.isDirectory(f));
            assertEquals(List.of("child.txt"), names(f));
            assertEquals("child", Files.readString(fs.getPath("/f/child.txt")));
            try (Stream<Path> s = Files.walk(fs.getPath("/"))) {
                assertEquals(List.of("/", "/f", "/f/child.txt"), s.map(Path::toString).sorted().collect(Collectors.toList()));
            }
        }
    }

    public void testZipIdentityIsNormalized() throws IOException {
        Path zip = zip("id.zip", "a/b.txt", "b", "c/", "");
        try (ZipFileSystem fs = ZipFileSystem.newBuilder().zipFile(zip).build()) {
            assertTrue(Files.isSameFile(fs.getPath("/a/b.txt"), fs.getPath("/c/../a/b.txt")));
            assertTrue(Files.isSameFile(fs.getPath("a/b.txt"), fs.getPath("/a/b.txt")));
            assertEquals(Files.readAttributes(fs.getPath("/a/b.txt"), BasicFileAttributes.class).fileKey(),
                Files.readAttributes(fs.getPath("/a/./b.txt"), BasicFileAttributes.class).fileKey());
            assertThrows(NoSuchFileException.class, () -> Files.isSameFile(fs.getPath("/a/b.txt"), fs.getPath("/nope")));
            assertFalse(Files.isSameFile(fs.getPath("/a"), fs.getPath("/c")));
        }
    }

    public void testZipToZipCopyIsReadOnly() throws IOException {
        Path z1 = zip("z1.zip", "a.txt", "a");
        Path z2 = zip("z2.zip", "b.txt", "b");
        ZipFileSystemProvider provider = new ZipFileSystemProvider(false);
        try (ZipFileSystem fs1 = ZipFileSystem.newBuilder().provider(provider).zipFile(z1).build();
             ZipFileSystem fs2 = ZipFileSystem.newBuilder().provider(provider).zipFile(z2).build()) {
            assertThrows(ReadOnlyFileSystemException.class, () -> Files.copy(fs1.getPath("/a.txt"), fs2.getPath("/a.txt"), StandardCopyOption.COPY_ATTRIBUTES));
            assertThrows(ReadOnlyFileSystemException.class, () -> Files.copy(fs1.getPath("/a.txt"), fs2.getPath("/a.txt")));
        }
    }

    // ==================== Sandbox: links not followed ====================

    public void testSandboxNoFollowOperations() throws IOException {
        File root = folder("sbx");
        File outside = folder("sbx-outside");
        Files.writeString(new File(outside, "secret.txt").toPath(), "secret");
        Files.writeString(new File(root, "in.txt").toPath(), "in");
        try {
            Files.createSymbolicLink(new File(root, "linkfile").toPath(), new File(outside, "secret.txt").toPath());
        } catch (Exception e) {
            return; // no symbolic links on this platform
        }
        FileSystem[] all = {
            FileFileSystem.newBuilder().root(root).build(),
            PathFileSystem.newBuilder().root(root.toPath()).build(),
            PathDelegatingFileSystem.newBuilder().root(root.toPath()).build()
        };
        try {
            for (FileSystem fs : all) {
                String name = fs.getClass().getSimpleName();
                Path link = fs.getPath("/linkfile");
                // Inspecting the link itself is fine...
                assertTrue(name, Files.isSymbolicLink(link));
                assertTrue(name, Files.readAttributes(link, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isSymbolicLink());
                assertTrue(name, Files.exists(link, LinkOption.NOFOLLOW_LINKS));
                // ...following it is not
                assertThrows(name, AccessDeniedException.class, () -> Files.readAllBytes(link));
                assertThrows(name, AccessDeniedException.class, () -> Files.readAttributes(link, BasicFileAttributes.class));
                // A stray link does not break a walk
                try (Stream<Path> s = Files.walk(fs.getPath("/"))) {
                    assertEquals(name, 3, s.count());
                }
                // It can be moved and deleted: the target outside is untouched
                Files.move(link, fs.getPath("/moved"));
                assertTrue(name, Files.isSymbolicLink(fs.getPath("/moved")));
                Files.delete(fs.getPath("/moved"));
                assertFalse(name, Files.exists(fs.getPath("/moved"), LinkOption.NOFOLLOW_LINKS));
                assertEquals(name, "secret", Files.readString(new File(outside, "secret.txt").toPath()));
                Files.createSymbolicLink(new File(root, "linkfile").toPath(), new File(outside, "secret.txt").toPath());
                // Copying the link as a link (NOFOLLOW_LINKS) is fine, copying its target is not
                assertThrows(name, AccessDeniedException.class, () -> Files.copy(link, fs.getPath("/copy")));
                Files.copy(link, fs.getPath("/copy"), LinkOption.NOFOLLOW_LINKS);
                assertTrue(name, Files.isSymbolicLink(fs.getPath("/copy")));
                Files.delete(fs.getPath("/copy"));
            }
        } finally {
            for (FileSystem fs : all) {
                fs.close();
            }
        }
    }

    public void testDelegateRealPathEscapeIsAccessDenied() throws IOException {
        File root = folder("rp");
        File outside = folder("rp-outside");
        try {
            Files.createSymbolicLink(new File(root, "out").toPath(), outside.toPath());
        } catch (Exception e) {
            return;
        }
        try (FileSystem fs = PathDelegatingFileSystem.newBuilder().root(root.toPath()).build()) {
            assertThrows(AccessDeniedException.class, () -> fs.getPath("/out").toRealPath());
            assertEquals("/b", fs.getPath("/a/../b").toUri().getPath());
        }
    }

    // ==================== Attribute views ====================

    public void testHostAttributeViewsPassThrough() throws IOException {
        if (!posix()) {
            return;
        }
        File root = folder("views");
        Files.writeString(new File(root, "f").toPath(), "x");
        try (MemoryFileSystem m = MemoryFileSystem.newBuilder().build();
             FileSystem f = FileFileSystem.newBuilder().root(root).build();
             FileSystem p = PathFileSystem.newBuilder().root(root.toPath()).build();
             FileSystem dl = PathDelegatingFileSystem.newBuilder().root(root.toPath()).build()) {
            for (FileSystem fs : new FileSystem[] { f, p, dl }) {
                String name = fs.getClass().getSimpleName();
                assertTrue(name, fs.supportedFileAttributeViews().contains("posix"));
                Path file = fs.getPath("/f");
                PosixFileAttributeView view = Files.getFileAttributeView(file, PosixFileAttributeView.class);
                assertNotNull(name, view);
                assertEquals(name, "posix", view.name());
                view.setPermissions(PosixFilePermissions.fromString("rw-r-----"));
                assertEquals(name, PosixFilePermissions.fromString("rw-r-----"), Files.getPosixFilePermissions(file));
                @SuppressWarnings("unchecked")
                Set<PosixFilePermission> perms = (Set<PosixFilePermission>) Files.readAttributes(file, "posix:permissions").get("permissions");
                assertEquals(name, PosixFilePermissions.fromString("rw-r-----"), perms);
                Files.setAttribute(file, "posix:permissions", PosixFilePermissions.fromString("rw-------"));
                assertEquals(name, PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(file));
                assertNotNull(name, Files.readAttributes(file, java.nio.file.attribute.PosixFileAttributes.class).permissions());
            }
            // The memory filesystem is basic only, and says so consistently
            Files.writeString(m.getPath("/f"), "x");
            assertEquals(Set.of("basic"), m.supportedFileAttributeViews());
            assertNull(Files.getFileAttributeView(m.getPath("/f"), PosixFileAttributeView.class));
            assertThrows(UnsupportedOperationException.class, () -> Files.readAttributes(m.getPath("/f"), "posix:permissions"));
            assertThrows(UnsupportedOperationException.class, () -> Files.readAttributes(m.getPath("/f"), java.nio.file.attribute.PosixFileAttributes.class));
            Files.setLastModifiedTime(m.getPath("/f"), FileTime.fromMillis(5000));
            assertEquals(5000, Files.getLastModifiedTime(m.getPath("/f")).toMillis());
        }
    }

    // ==================== Roots ====================

    public void testRootValidation() throws IOException {
        File file = new File(base, "regular.txt");
        Files.writeString(file.toPath(), "x");
        assertThrows(FileSystemRuntimeException.class, () -> PathFileSystem.newBuilder().root(file.toPath()).build());
        assertThrows(FileSystemRuntimeException.class, () -> FileFileSystem.newBuilder().root(file).build());
        // "~name" is a file name, not the home folder followed by "name"
        FileSystemRuntimeException e = assertThrows(FileSystemRuntimeException.class,
            () -> FileFileSystem.newBuilder().root(new File("~no-such-user-folder")).build());
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        assertFalse(cause.getMessage(), cause.getMessage().contains(System.getProperty("user.home")));
        Map<String, Object> env = new HashMap<>();
        env.put(FileFileSystemProvider.ROOT_PARAM, "~");
        try (FileSystem home = new FileFileSystemProvider(false).newFileSystem(URI.create("file-impl:///"), env)) {
            assertEquals(new File(System.getProperty("user.home")).getCanonicalFile(), ((FileFileSystem) home).getRoot());
        }
    }

    // ==================== Glob ====================

    public void testGlobEdgeCases() throws IOException {
        try (FileSystem fs = MemoryFileSystem.newBuilder().build()) {
            assertTrue(fs.getPathMatcher("glob:").matches(fs.getPath("")));
            assertThrows(java.util.regex.PatternSyntaxException.class, () -> fs.getPathMatcher("glob:a[/]b"));
            assertFalse(fs.getPathMatcher("glob:a[!x]b").matches(fs.getPath("a/b")));
            assertTrue(fs.getPathMatcher("glob:a[!x]b").matches(fs.getPath("ayb")));
        }
    }
}
