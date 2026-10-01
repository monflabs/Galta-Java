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
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import org.monflabs.filesystem.delegate.PathDelegatingFileSystem;
import org.monflabs.filesystem.file.FileFileSystem;
import org.monflabs.filesystem.path.PathFileSystem;
import org.monflabs.util.FileUtil;

import tests.ProjectTestCase;

/**
 * The three sandboxed providers must not let a path escape their root, whether through
 * "..", a sibling directory sharing the root's name as a prefix, or a symbolic link.
 */
public class SandboxTest extends ProjectTestCase {

    private File base;
    private File root;      // the sandbox root
    private File sibling;   // "<root>2": shares the root path as a string prefix
    private File outside;   // a directory outside the root, target of a symlink
    private boolean symlinks;

    @Override
    public void setUp() throws IOException {
        base = new File(support.getTargetTempDirectory(), "SandboxTest");
        FileUtil.deleteFile(base);
        root = new File(base, "sb");
        sibling = new File(base, "sb2");
        outside = new File(base, "outside");
        root.mkdirs(); sibling.mkdirs(); outside.mkdirs();
        Files.write(new File(sibling, "secret.txt").toPath(), "sibling".getBytes());
        Files.write(new File(outside, "secret.txt").toPath(), "outside".getBytes());
        Files.write(new File(root, "inside.txt").toPath(), "inside".getBytes());
        try {
            Files.createSymbolicLink(new File(root, "link").toPath(), outside.toPath());
            symlinks = true;
        } catch (Exception e) {
            symlinks = false; // platform without symlink support
        }
    }

    @Override
    public void tearDown() {
        FileUtil.deleteFile(base);
    }

    private void checkSandbox(FileSystem fs) throws IOException {
        // Sanity: the sandbox works for what is inside
        assertEquals("inside", new String(Files.readAllBytes(fs.getPath("/inside.txt"))));

        // ".." cannot climb above the root: it stays at the root (chroot semantics)
        assertFalse(Files.exists(fs.getPath("/../sb2/secret.txt")));
        assertFalse(Files.exists(fs.getPath("/../../outside/secret.txt")));
        assertTrue(Files.exists(fs.getPath("/../inside.txt")));
        assertTrue(Files.exists(fs.getPath("/a/../inside.txt")));

        // Writing through ".." must not create anything outside the root
        Files.write(fs.getPath("/../escaped.txt"), "x".getBytes(), StandardOpenOption.CREATE);
        assertFalse(new File(base, "escaped.txt").exists());
        assertTrue(new File(root, "escaped.txt").exists());

        // A sibling whose name starts with the root's name is not inside the root
        assertFalse(Files.exists(fs.getPath("/../sb2/secret.txt")));
        assertFalse(new File(root, "../sb2/x").getCanonicalFile().toPath().startsWith(root.toPath()));

        if (symlinks) {
            // A link inside the root that points outside must not be readable through it
            assertThrows(AccessDeniedException.class, () -> Files.readAllBytes(fs.getPath("/link/secret.txt")));
            assertThrows(AccessDeniedException.class, () -> Files.write(fs.getPath("/link/new.txt"), "n".getBytes()));
            assertFalse(new File(outside, "new.txt").exists());
        }
    }

    public void testFileFileSystemSandbox() throws IOException {
        try (FileSystem fs = FileFileSystem.newBuilder().root(root).build()) {
            checkSandbox(fs);
        }
    }

    public void testPathFileSystemSandbox() throws IOException {
        try (FileSystem fs = PathFileSystem.newBuilder().root(root.toPath()).build()) {
            checkSandbox(fs);
        }
    }

    public void testPathDelegatingFileSystemSandbox() throws IOException {
        try (FileSystem fs = PathDelegatingFileSystem.newBuilder().root(root.toPath()).build()) {
            checkSandbox(fs);
            // Validation happens before any side effect: nothing is created or truncated first
            Path target = fs.getPath("/missing-dir/../inside.txt");
            assertEquals("inside", new String(Files.readAllBytes(target)));
        }
    }

    public void testPathDelegatingMissingFile() throws IOException {
        try (FileSystem fs = PathDelegatingFileSystem.newBuilder().root(root.toPath()).build()) {
            assertThrows(NoSuchFileException.class, () -> Files.readAllBytes(fs.getPath("/nope.txt")));
        }
    }

    // ==================== Dangling links, odd names, real paths ====================
    
    private FileSystem[] sandboxes(File r) throws IOException {
        return new FileSystem[] {
            FileFileSystem.newBuilder().root(r).build(),
            PathFileSystem.newBuilder().root(r.toPath()).build(),
            PathDelegatingFileSystem.newBuilder().root(r.toPath()).build()
        };
    }
    
    private static void closeAll(FileSystem[] all) throws IOException {
        for (FileSystem f : all) {
            f.close();
        }
    }
    
    public void testDanglingLinkCannotCreateOutside() throws IOException {
        if (!symlinks) {
            return;
        }
        File target = new File(outside, "created-through-link.txt");
        Files.createSymbolicLink(new File(root, "dangling").toPath(), target.toPath());
        Files.createSymbolicLink(new File(root, "dangling-dir").toPath(), new File(outside, "nodir").toPath());
        FileSystem[] all = sandboxes(root);
        try {
            for (FileSystem fs : all) {
                String name = fs.getClass().getSimpleName();
                // getCanonicalFile() returned the link's own path when its target was missing,
                // so the check passed and CREATE then created the target outside the root
                assertThrows(name, AccessDeniedException.class, () -> Files.write(fs.getPath("/dangling"), "x".getBytes()));
                assertThrows(name, AccessDeniedException.class, () -> Files.createDirectory(fs.getPath("/dangling-dir")));
                assertThrows(name, AccessDeniedException.class, () -> Files.write(fs.getPath("/dangling-dir/f.txt"), "x".getBytes()));
                assertFalse(name, target.exists());
                assertFalse(name, new File(outside, "nodir").exists());
            }
        } finally {
            closeAll(all);
        }
    }
    
    public void testDanglingLinkInsideTheRootStillWorks() throws IOException {
        if (!symlinks) {
            return;
        }
        Files.createSymbolicLink(new File(root, "abs-in").toPath(), new File(root, "target-abs.txt").toPath());
        Files.createSymbolicLink(new File(root, "rel-in").toPath(), java.nio.file.Paths.get("target-rel.txt"));
        Files.createSymbolicLink(new File(root, "rel-up").toPath(), java.nio.file.Paths.get("../sb/target-up.txt"));
        FileSystem[] all = sandboxes(root);
        try {
            int i = 0;
            for (FileSystem fs : all) {
                String name = fs.getClass().getSimpleName();
                Files.write(fs.getPath("/abs-in"), ("a" + i).getBytes());
                assertEquals(name, "a" + i, new String(Files.readAllBytes(new File(root, "target-abs.txt").toPath())));
                Files.write(fs.getPath("/rel-in"), ("r" + i).getBytes());
                assertEquals(name, "r" + i, new String(Files.readAllBytes(new File(root, "target-rel.txt").toPath())));
                // ".." in a dangling link's target is refused rather than resolved by guesswork
                assertThrows(name, AccessDeniedException.class, () -> Files.write(fs.getPath("/rel-up"), "u".getBytes()));
                // Once the target exists the link is an ordinary link inside the root
                i++;
            }
        } finally {
            closeAll(all);
        }
    }
    
    public void testLinkLoopIsRefused() throws IOException {
        if (!symlinks) {
            return;
        }
        Files.createSymbolicLink(new File(root, "loop1").toPath(), new File(root, "loop2").toPath());
        Files.createSymbolicLink(new File(root, "loop2").toPath(), new File(root, "loop1").toPath());
        FileSystem[] all = sandboxes(root);
        try {
            for (FileSystem fs : all) {
                assertThrows(fs.getClass().getSimpleName(), IOException.class, () -> Files.write(fs.getPath("/loop1"), "x".getBytes()));
            }
        } finally {
            closeAll(all);
        }
    }
    
    public void testBackslashAndCaseTraversalStaysInside() throws IOException {
        FileSystem[] all = sandboxes(root);
        try {
            for (FileSystem fs : all) {
                String name = fs.getClass().getSimpleName();
                assertFalse(name, Files.exists(fs.getPath("/..\\..\\outside\\secret.txt")));
                assertFalse(name, Files.exists(fs.getPath("..\\sb2\\secret.txt")));
                assertFalse(name, Files.exists(fs.getPath("/../SB2/secret.txt")));
                if (fs.getSeparator().equals("\\") || File.separator.equals("\\")) {
                    // A backslash is a separator on Windows
                    assertTrue(name, Files.exists(fs.getPath("\\..\\inside.txt")));
                } else {
                    // On a "/" filesystem a backslash is an ordinary character, as for the
                    // JDK on Unix: "\..\inside.txt" is one file name, inside the root
                    Path literal = fs.getPath("\\..\\inside.txt");
                    assertEquals(name, 1, literal.getNameCount());
                    assertFalse(name, Files.exists(literal));
                    Files.write(literal, "x".getBytes());
                    assertTrue(name, new File(root, "\\..\\inside.txt").exists());
                    Files.delete(literal);
                }
            }
        } finally {
            closeAll(all);
        }
    }
    
    public void testDriveLikeNameMapsInsideTheRoot() throws IOException {
        if (File.separator.equals("\\")) {
            // A name like "x:" cannot exist on Windows: it is a drive, not a file name, and
            // java.nio.file.Path resolves it as one - there is nothing to map inside the root
            return;
        }
        FileSystem[] all = sandboxes(root);
        try {
            int i = 0;
            for (FileSystem fs : all) {
                String name = fs.getClass().getSimpleName();
                String dir = "x" + i + ":";
                // "/x:/f" used to lose its first name and map to "<root>/f"
                Files.createDirectory(fs.getPath("/" + dir));
                Files.write(fs.getPath("/" + dir + "/f.txt"), "f".getBytes());
                assertTrue(name, new File(new File(root, dir), "f.txt").exists());
                assertFalse(name, new File(root, "f.txt").exists());
                i++;
            }
        } finally {
            closeAll(all);
        }
        try (PathFileSystem pfs = PathFileSystem.newBuilder().root(root.toPath()).build()) {
            assertEquals(root.toPath().toAbsolutePath().resolve("x:").resolve("f"), pfs.toOSPath("/x:/f"));
        }
    }
    
    public void testToRealPathUnderLinkedRoot() throws IOException {
        if (!symlinks) {
            return;
        }
        // A root reached through a link (like /tmp on macOS): the real path used to come out
        // as "/../../<real root>/inside.txt"
        File linkedRoot = new File(base, "linked-root");
        Files.createSymbolicLink(linkedRoot.toPath(), root.toPath());
        FileSystem[] all = sandboxes(linkedRoot);
        try {
            for (FileSystem fs : all) {
                String name = fs.getClass().getSimpleName();
                assertEquals(name, fs.getPath("/inside.txt"), fs.getPath("/x/../inside.txt").toRealPath());
                assertEquals(name, fs.getPath("/"), fs.getPath("/").toRealPath());
                // Through a link pointing outside, there is no virtual path to return
                assertThrows(name, IOException.class, () -> fs.getPath("/link/secret.txt").toRealPath());
                assertThrows(name, NoSuchFileException.class, () -> fs.getPath("/missing.txt").toRealPath());
            }
        } finally {
            closeAll(all);
        }
    }
    
    public void testRelativeRoot() throws IOException {
        Path relativeRoot = java.nio.file.Paths.get("").toAbsolutePath().relativize(root.toPath().toAbsolutePath());
        assertFalse(relativeRoot.isAbsolute());
        try (PathFileSystem pfs = PathFileSystem.newBuilder().root(relativeRoot).build()) {
            // relativize() used to throw on the absolute OS entry, leaking the full OS path
            List<String> names = new ArrayList<>();
            try (java.nio.file.DirectoryStream<Path> ds = Files.newDirectoryStream(pfs.getPath("/"))) {
                for (Path p : ds) {
                    names.add(p.toString());
                }
            }
            assertTrue(names.toString(), names.contains("/inside.txt"));
            for (String n : names) {
                assertFalse(n, n.contains(base.getName()));
            }
            assertEquals(pfs.getPath("/inside.txt"), pfs.getPath("/inside.txt").toRealPath());
            assertThrows(IllegalArgumentException.class, () -> pfs.toVirtualPath(outside.toPath()));
        }
    }
    
    public void testFileSystemUriNeverClimbsAboveRoot() throws IOException {
        try (FileSystem fs = FileFileSystem.newBuilder().root(root).build()) {
            assertEquals("/x", fs.getPath("/../x").toUri().getPath());
            assertEquals("/inside.txt", fs.getPath("a/../../inside.txt").toUri().getPath());
        }
    }
    
    public void testDelegatingCreateDoesNotCreateParents() throws IOException {
        try (FileSystem fs = PathDelegatingFileSystem.newBuilder().root(root.toPath()).build()) {
            // CREATE used to run createDirectories() on the missing parents
            assertThrows(NoSuchFileException.class, () -> Files.write(fs.getPath("/nope/deep/f.txt"), "x".getBytes()));
            assertFalse(new File(root, "nope").exists());
        }
    }
}
