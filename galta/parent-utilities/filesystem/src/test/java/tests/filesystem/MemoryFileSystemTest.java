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

import java.io.IOException;
import java.nio.file.FileSystem;
import java.util.HashMap;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;

import org.monflabs.filesystem.memory.MemoryFileSystem;

/**
 * Comprehensive test suite for MemoryFileSystem implementation.
 */
public class MemoryFileSystemTest extends AbstractFileSystemTest {
    
    @Override
    protected FileSystem createFileSystem(int index) throws IOException {
        return MemoryFileSystem.DEFAUT_PROVIDER.newFileSystem(MemoryFileSystem.DEFAULT_URI, new HashMap<>());
    }
    
    @Override
    protected void cleanupFileSystem(FileSystem fs) throws IOException {
        if (fs != null && fs.isOpen()) {
            fs.close();
        }
        // Memory is automatically cleaned up, no disk cleanup needed
    }
    
    @Override
    protected String getFileSystemName() {
        return "MemoryFileSystem";
    }

    public void testCopyDirectoryIsShallow() throws java.io.IOException {
        java.nio.file.Path src = fs.getPath("/src");
        java.nio.file.Files.createDirectories(src.resolve("sub"));
        java.nio.file.Files.write(src.resolve("sub/child.txt"), "x".getBytes());
        java.nio.file.Files.copy(src, fs.getPath("/tgt"));
        // Files.copy() of a directory creates an empty directory: the entries are not copied
        assertTrue(java.nio.file.Files.isDirectory(fs.getPath("/tgt")));
        assertFalse(java.nio.file.Files.exists(fs.getPath("/tgt/sub")));
        try (java.util.stream.Stream<java.nio.file.Path> list = java.nio.file.Files.list(fs.getPath("/tgt"))) {
            assertEquals(0, list.count());
        }
        // The source is untouched
        assertTrue(java.nio.file.Files.exists(src.resolve("sub/child.txt")));
    }
    
    public void testMoveNonEmptyDirectory() throws java.io.IOException {
        java.nio.file.Path src = fs.getPath("/mv");
        java.nio.file.Files.createDirectories(src.resolve("a/b"));
        java.nio.file.Files.write(src.resolve("a/b/f.txt"), "y".getBytes());
        java.nio.file.Files.move(src, fs.getPath("/moved"));
        assertFalse(java.nio.file.Files.exists(src));
        assertFalse(java.nio.file.Files.exists(fs.getPath("/mv/a/b/f.txt")));
        assertEquals("y", new String(java.nio.file.Files.readAllBytes(fs.getPath("/moved/a/b/f.txt"))));
    }
    
    public void testReplaceExistingRefusesNonEmptyDirectory() throws java.io.IOException {
        org.monflabs.filesystem.memory.MemoryFileSystem mfs = (org.monflabs.filesystem.memory.MemoryFileSystem) fs;
        java.nio.file.Files.createDirectories(fs.getPath("/old/deep"));
        java.nio.file.Files.write(fs.getPath("/old/deep/g.txt"), "z".getBytes());
        java.nio.file.Files.write(fs.getPath("/new.txt"), "n".getBytes());
        int before = mfs.getTotalNodeCount();
        // REPLACE_EXISTING never replaces a non-empty directory (it used to wipe the whole subtree)
        assertThrows(java.nio.file.DirectoryNotEmptyException.class, () -> java.nio.file.Files.copy(fs.getPath("/new.txt"), fs.getPath("/old"), java.nio.file.StandardCopyOption.REPLACE_EXISTING));
        assertThrows(java.nio.file.DirectoryNotEmptyException.class, () -> java.nio.file.Files.move(fs.getPath("/new.txt"), fs.getPath("/old"), java.nio.file.StandardCopyOption.REPLACE_EXISTING));
        assertEquals("z", new String(java.nio.file.Files.readAllBytes(fs.getPath("/old/deep/g.txt"))));
        assertTrue(java.nio.file.Files.exists(fs.getPath("/new.txt")));
        assertEquals(before, mfs.getTotalNodeCount());
        
        // An empty directory is replaced, and leaves no ghost entries
        java.nio.file.Files.createDirectories(fs.getPath("/empty"));
        java.nio.file.Files.copy(fs.getPath("/new.txt"), fs.getPath("/empty"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        assertTrue(java.nio.file.Files.isRegularFile(fs.getPath("/empty")));
        assertEquals(before + 1, mfs.getTotalNodeCount());
    }
    
    public void testCopyBetweenFileSystems() throws java.io.IOException {
        java.nio.file.FileSystem other = createSecondFileSystem();
        try {
            java.nio.file.Files.write(fs.getPath("/x.txt"), "cross".getBytes());
            java.nio.file.Files.copy(fs.getPath("/x.txt"), other.getPath("/y.txt"));
            assertTrue(java.nio.file.Files.exists(other.getPath("/y.txt")));
            assertFalse(java.nio.file.Files.exists(fs.getPath("/y.txt")));
            assertEquals("cross", new String(java.nio.file.Files.readAllBytes(other.getPath("/y.txt"))));
        } finally {
            other.close();
        }
    }
    
    public void testDotSegmentsNormalized() throws java.io.IOException {
        java.nio.file.Files.createDirectories(fs.getPath("/a"));
        java.nio.file.Files.write(fs.getPath("/a/../b.txt"), "b".getBytes());
        assertTrue(java.nio.file.Files.exists(fs.getPath("/b.txt")));
        assertTrue(java.nio.file.Files.exists(fs.getPath("/a/./../b.txt")));
        assertEquals("b", new String(java.nio.file.Files.readAllBytes(fs.getPath("/b.txt"))));
    }

    public void testMoveOntoSameFileIsNoOp() throws java.io.IOException {
        java.nio.file.Files.createDirectories(fs.getPath("/d"));
        java.nio.file.Files.write(fs.getPath("/a.txt"), "keep".getBytes());
        // The same file spelled differently: this used to delete it
        java.nio.file.Files.move(fs.getPath("/a.txt"), fs.getPath("/d/../a.txt"));
        assertEquals("keep", new String(java.nio.file.Files.readAllBytes(fs.getPath("/a.txt"))));
        java.nio.file.Files.move(fs.getPath("/a.txt"), fs.getPath("/./a.txt"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        assertEquals("keep", new String(java.nio.file.Files.readAllBytes(fs.getPath("/a.txt"))));
        java.nio.file.Files.copy(fs.getPath("/a.txt"), fs.getPath("/d/../a.txt"));
        assertEquals("keep", new String(java.nio.file.Files.readAllBytes(fs.getPath("/a.txt"))));
    }
    
    public void testMoveDirectoryIntoItselfRefused() throws java.io.IOException {
        java.nio.file.Files.createDirectories(fs.getPath("/p/q"));
        java.nio.file.Files.write(fs.getPath("/p/q/f.txt"), "f".getBytes());
        // This used to delete everything
        assertThrows(java.nio.file.FileSystemException.class, () -> java.nio.file.Files.move(fs.getPath("/p"), fs.getPath("/p/q/p2")));
        assertEquals("f", new String(java.nio.file.Files.readAllBytes(fs.getPath("/p/q/f.txt"))));
        assertFalse(java.nio.file.Files.exists(fs.getPath("/p/q/p2")));
        // A sibling sharing the name as a prefix is not inside
        java.nio.file.Files.move(fs.getPath("/p"), fs.getPath("/p2"));
        assertEquals("f", new String(java.nio.file.Files.readAllBytes(fs.getPath("/p2/q/f.txt"))));
    }
    
    public void testMoveKeepsTimes() throws java.io.IOException {
        java.nio.file.Path f = fs.getPath("/t.txt");
        java.nio.file.Files.write(f, "t".getBytes());
        java.nio.file.attribute.FileTime t = java.nio.file.attribute.FileTime.fromMillis(1_000_000L);
        java.nio.file.Files.setLastModifiedTime(f, t);
        java.nio.file.Files.move(f, fs.getPath("/u.txt"));
        assertEquals(t, java.nio.file.Files.getLastModifiedTime(fs.getPath("/u.txt")));
        // A copy gets new times unless COPY_ATTRIBUTES is given
        java.nio.file.Files.copy(fs.getPath("/u.txt"), fs.getPath("/v.txt"));
        assertFalse(t.equals(java.nio.file.Files.getLastModifiedTime(fs.getPath("/v.txt"))));
        java.nio.file.Files.copy(fs.getPath("/u.txt"), fs.getPath("/w.txt"), java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
        assertEquals(t, java.nio.file.Files.getLastModifiedTime(fs.getPath("/w.txt")));
    }
    
    public void testDriveLikeNameIsAnOrdinaryName() throws java.io.IOException {
        java.nio.file.Files.write(fs.getPath("/keep.txt"), "k".getBytes());
        // "x:" is a file name here, not a drive letter: creating it used to replace the root
        java.nio.file.Files.createDirectory(fs.getPath("/x:"));
        java.nio.file.Files.write(fs.getPath("/x:/f.txt"), "f".getBytes());
        assertTrue(java.nio.file.Files.isDirectory(fs.getPath("/x:")));
        assertEquals("f", new String(java.nio.file.Files.readAllBytes(fs.getPath("/x:/f.txt"))));
        assertEquals("k", new String(java.nio.file.Files.readAllBytes(fs.getPath("/keep.txt"))));
        try (java.util.stream.Stream<java.nio.file.Path> list = java.nio.file.Files.list(fs.getPath("/"))) {
            assertEquals(2, list.count());
        }
    }
    
    public void testChannelsHonourPermissions() throws java.io.IOException {
        org.monflabs.filesystem.memory.MemoryFileSystem mfs = (org.monflabs.filesystem.memory.MemoryFileSystem) fs;
        java.nio.file.Path f = fs.getPath("/perm.txt");
        java.nio.file.Files.write(f, "secret".getBytes());
        org.monflabs.filesystem.memory.MemoryFileNode node = mfs.getNode((org.monflabs.filesystem.memory.MemoryPath) f);
        node.setWritable(false);
        assertThrows(java.nio.file.AccessDeniedException.class, () -> java.nio.file.Files.write(f, "x".getBytes()));
        assertEquals("secret", new String(java.nio.file.Files.readAllBytes(f)));
        node.setReadable(false);
        assertThrows(java.nio.file.AccessDeniedException.class, () -> java.nio.file.Files.readAllBytes(f));
    }
    
    public void testConcurrentAppendsLoseNothing() throws Exception {
        java.nio.file.Path f = fs.getPath("/log.txt");
        java.nio.file.Files.createFile(f);
        int threads = 8, perThread = 500;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        try {
            java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
            for (int t = 0; t < threads; t++) {
                futures.add(pool.submit(() -> {
                    try (java.nio.channels.SeekableByteChannel ch = java.nio.file.Files.newByteChannel(f, java.nio.file.StandardOpenOption.APPEND)) {
                        for (int i = 0; i < perThread; i++) {
                            ch.write(java.nio.ByteBuffer.wrap("0123456789".getBytes()));
                        }
                    }
                    return null;
                }));
            }
            for (java.util.concurrent.Future<?> fu : futures) {
                fu.get();
            }
        } finally {
            pool.shutdown();
        }
        assertEquals(threads * perThread * 10L, java.nio.file.Files.size(f));
    }
    
    public void testChannelPositionBeyondIntRange() throws java.io.IOException {
        java.nio.file.Path f = fs.getPath("/big.txt");
        java.nio.file.Files.write(f, "abc".getBytes());
        try (java.nio.channels.SeekableByteChannel ch = java.nio.file.Files.newByteChannel(f, java.nio.file.StandardOpenOption.READ)) {
            // A position cast to int wrapped to a small (or negative) value
            ch.position(1L << 32);
            assertEquals(1L << 32, ch.position());
            assertEquals(-1, ch.read(java.nio.ByteBuffer.allocate(4)));
        }
        try (java.nio.channels.SeekableByteChannel ch = java.nio.file.Files.newByteChannel(f, java.nio.file.StandardOpenOption.WRITE)) {
            ch.position(1L << 32);
            assertThrows(java.io.IOException.class, () -> ch.write(java.nio.ByteBuffer.wrap("x".getBytes())));
        }
        assertEquals(3, java.nio.file.Files.size(f));
    }
    
    public void testWritePastEndZeroFills() throws java.io.IOException {
        java.nio.file.Path f = fs.getPath("/gap.bin");
        try (java.nio.channels.SeekableByteChannel ch = java.nio.file.Files.newByteChannel(f, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE)) {
            ch.write(java.nio.ByteBuffer.wrap(new byte[] {1, 2, 3, 4}));
            ch.truncate(1);
            ch.position(3);
            ch.write(java.nio.ByteBuffer.wrap(new byte[] {9}));
        }
        // Truncated bytes do not come back in the gap
        assertArrayEquals(new byte[] {1, 0, 0, 9}, java.nio.file.Files.readAllBytes(f));
    }
    
    public void testDeleteRootRefused() throws java.io.IOException {
        // Deleting "/" used to drop the root from the index
        assertThrows(java.nio.file.FileSystemException.class, () -> java.nio.file.Files.delete(fs.getPath("/")));
        java.nio.file.Files.write(fs.getPath("/still.txt"), "ok".getBytes());
        assertTrue(java.nio.file.Files.exists(fs.getPath("/still.txt")));
    }
}
