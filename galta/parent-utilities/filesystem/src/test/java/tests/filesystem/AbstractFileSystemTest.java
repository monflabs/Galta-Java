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


import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.monflabs.util.path.FilesUtil;

import tests.ProjectTestCase;

/**
 * Abstract base test class for FileSystem implementations.
 * Contains all test methods - concrete subclasses provide filesystem setup/teardown.
 */
public abstract class AbstractFileSystemTest extends ProjectTestCase {
    
    protected FileSystem fs;
    protected String separator;  // Filesystem separator
    
    /**
     * Create and return a FileSystem instance for testing.
     * Called before each test.
     */
    protected final FileSystem createFileSystem() throws IOException {
        return createFileSystem(0);
    }    
	protected final FileSystem createSecondFileSystem() throws IOException {
        return createFileSystem(2);
    }    
     
    protected abstract FileSystem createFileSystem(int index) throws IOException;
    
    /**
     * Clean up the FileSystem after testing.
     * Called after each test.
     */
    protected abstract void cleanupFileSystem(FileSystem fs) throws IOException;
    
    /**
     * Get a descriptive name for the filesystem being tested.
     */
    protected abstract String getFileSystemName();
    
    /**
     * Helper method to construct paths with correct separator.
     * Converts "/" in input to the filesystem's separator.
     */
    protected String path(String pathString) {
        if (pathString == null || pathString.isEmpty()) {
            return separator;
        }
        // Convert / to filesystem separator
        return pathString.replace("/", separator);
    }
    
    /**
     * Helper method to get Path object with correct separator.
     */
    protected Path getPath(String first, String... more) {
        return fs.getPath(path(first), more);
    }
    
    
    @Override
	public void setUp() throws IOException {
        fs = createFileSystem();
        assertNotNull(fs);
        assertTrue(fs.isOpen());
        separator = fs.getSeparator();
    }
    
    @Override
	public void tearDown() throws IOException {
        if (fs != null && fs.isOpen()) {
            cleanupFileSystem(fs);
        }
    }

    
    // ==================== Basic FileSystem Tests ====================
    
    public void testFileSystemOpen() {
        assertTrue(fs.isOpen());
        assertFalse(fs.isReadOnly());
        assertEquals(separator, fs.getSeparator());
    }
    
    public void testFileSystemClose() throws IOException {
        fs.close();
        assertFalse(fs.isOpen());
        
        // Operations after close should fail
        assertThrows(ClosedFileSystemException.class, () -> 
        	fs.getPath(path("/test.txt")));
        assertThrows(ClosedFileSystemException.class, () -> 
            fs.getRootDirectories());
        assertThrows(ClosedFileSystemException.class, () -> 
            fs.getFileStores());
        assertThrows(ClosedFileSystemException.class, () -> 
            fs.getPathMatcher("glob:*.txt"));
        
        // These should still work
        assertFalse(fs.isOpen());
        assertEquals(separator, fs.getSeparator());
        assertNotNull(fs.provider());
    }
    
    public void testRootDirectories() {
        List<Path> roots = new ArrayList<>();
        fs.getRootDirectories().forEach(roots::add);
        
        assertEquals(1, roots.size());
        assertEquals(separator, roots.get(0).toString());
    }
    
    public void testFileStores() {
        List<FileStore> stores = new ArrayList<>();
        fs.getFileStores().forEach(stores::add);
        
        assertFalse("Should have at least one file store", stores.isEmpty());
        FileStore store = stores.get(0);
        assertNotNull(store.name());
        assertNotNull(store.type());
    }
    
    public void testMultipleFileSystems() throws IOException {
        FileSystem fs2 = createSecondFileSystem();
        try {
            Path file1 = getPath("/test.txt");
            Path file2 = fs2.getPath("/test.txt");
            
            Files.write(file1, "FS1".getBytes());
            Files.write(file2, "FS2".getBytes());
            
            assertEquals("FS1", new String(Files.readAllBytes(file1)));
            assertEquals("FS2", new String(Files.readAllBytes(file2)));
        } finally {
            cleanupFileSystem(fs2);
        }
    }
    
    // ==================== Path Tests ====================
    
    public void testPathCreation() {
        Path p1 = getPath("/");
        Path p2 = getPath("/test");
        Path p3 = getPath("/dir/file.txt");
        Path p4 = getPath("/", "dir", "file.txt");
        
        assertEquals(separator, p1.toString());
        assertEquals(path("/test"), p2.toString());
        assertEquals(path("/dir/file.txt"), p3.toString());
        assertEquals(path("/dir/file.txt"), p4.toString());
    }
    
    public void testAbsoluteRelativePaths() {
        Path absolute = getPath("/absolute");
        Path relative = fs.getPath("relative");
        
        assertTrue(absolute.isAbsolute());
        assertFalse(relative.isAbsolute());
        
        assertEquals(path("/relative"), relative.toAbsolutePath().toString());
    }
    
    public void testPathResolve() {
        Path base = getPath("/dir");
        
        assertEquals(path("/dir/file.txt"), base.resolve("file.txt").toString());
        assertEquals(path("/dir/sub/file.txt"), base.resolve("sub/file.txt").toString());
        assertEquals(path("/other"), base.resolve("/other").toString()); // Absolute overrides
        assertEquals(path("/dir"), base.resolve("").toString()); // Empty string
    }
    
    public void testPathNormalize() {
        assertEquals(path("/dir/file.txt"), 
            getPath("/dir/./file.txt").normalize().toString());
        assertEquals(path("/file.txt"), 
            getPath("/dir/../file.txt").normalize().toString());
        assertEquals(separator, 
            getPath("/dir/..").normalize().toString());
        assertEquals("..", 
            fs.getPath("..").normalize().toString());
    }
    
    public void testPathRelativize() {
        Path base = getPath("/a/b");
        Path target = getPath("/a/b/c/d");
        
        assertEquals(path("c/d"), base.relativize(target).toString());
        
        Path base2 = getPath("/a/b");
        Path target2 = getPath("/a/x");
        // From /a/b to /a/x: up one level (..) then to x
        assertEquals(path("../x"), base2.relativize(target2).toString());
        
        // Additional test cases
        Path base3 = getPath("/a/b/c");
        Path target3 = getPath("/a/x/y");
        // From /a/b/c to /a/x/y: up two levels (../..) then x/y
        assertEquals(path("../../x/y"), base3.relativize(target3).toString());
        
        Path base4 = getPath("/a/b");
        Path target4 = getPath("/a/b");
        // Same path should return empty or "."
        String result = base4.relativize(target4).toString();
        assertTrue(result.isEmpty() || result.equals("."));
    }
    
    public void testPathComponents() {
        Path path = getPath("/dir/subdir/file.txt");
        
        assertEquals(separator, path.getRoot().toString());
        assertEquals("file.txt", path.getFileName().toString());
        assertEquals(path("/dir/subdir"), path.getParent().toString());
        assertEquals(3, path.getNameCount());
        assertEquals("dir", path.getName(0).toString());
        assertEquals("subdir", path.getName(1).toString());
        assertEquals("file.txt", path.getName(2).toString());
    }
    
    public void testPathSubpath() {
        Path path = getPath("/a/b/c/d/e");
        
        // subpath(beginIndex, endIndex) - endIndex is exclusive
        assertEquals(path("a/b"), path.subpath(0, 2).toString());     // indices 0, 1
        assertEquals(path("b/c"), path.subpath(1, 3).toString());     // indices 1, 2
        assertEquals(path("c/d/e"), path.subpath(2, 5).toString());   // indices 2, 3, 4
        assertThrows(IllegalArgumentException.class, () -> path.subpath(-1, 2));
        assertThrows(IllegalArgumentException.class, () -> path.subpath(2, 10));
        assertThrows(IllegalArgumentException.class, () -> path.subpath(3, 3)); // beginIndex == endIndex
    }
    
    public void testPathStartsEndsWith() {
        Path path = getPath("/dir/subdir/file.txt");
        
        assertTrue(path.startsWith("/dir"));
        assertTrue(path.startsWith("/dir/subdir"));
        assertFalse(path.startsWith("/other"));
        
        assertTrue(path.endsWith("file.txt"));
        assertTrue(path.endsWith("subdir/file.txt"));
        assertFalse(path.endsWith("other.txt"));
    }
    
    public void testPathIterator() {
        Path path = getPath("/a/b/c");
        List<String> components = new ArrayList<>();
        
        for (Path component : path) {
            components.add(component.toString());
        }
        
        assertEquals(Arrays.asList("a", "b", "c"), components);
    }
    
    // ==================== File Creation and Deletion Tests ====================
    
    public void testCreateDeleteFile() throws IOException {
        Path file = getPath("/test.txt");
        
        assertFalse(Files.exists(file));
        
        Files.write(file, "content".getBytes());
        assertTrue(Files.exists(file));
        assertTrue(Files.isRegularFile(file));
        assertFalse(Files.isDirectory(file));
        
        Files.delete(file);
        assertFalse(Files.exists(file));
    }
    
    public void testCreateDeleteDirectory() throws IOException {
        Path dir = getPath("/testdir");
        
        assertFalse(Files.exists(dir));
        
        Files.createDirectory(dir);
        assertTrue(Files.exists(dir));
        assertTrue(Files.isDirectory(dir));
        assertFalse(Files.isRegularFile(dir));
        
        Files.delete(dir);
        assertFalse(Files.exists(dir));
    }
    
    public void testCreateDuplicateFile() throws IOException {
        Path file = getPath("/dup.txt");
        Files.write(file, "data".getBytes());
        
        assertThrows(FileAlreadyExistsException.class, () ->
            Files.write(file, "data".getBytes(), StandardOpenOption.CREATE_NEW));
    }
    
    public void testCreateDuplicateDirectory() throws IOException {
        Path dir = getPath("/dupdir");
        Files.createDirectory(dir);
        
        assertThrows(FileAlreadyExistsException.class, () ->
            Files.createDirectory(dir));
    }
    
    public void testDeleteNonExistent() throws IOException {
        Path file = getPath("/nonexistent.txt");
        
        assertThrows(NoSuchFileException.class, () -> Files.delete(file));
        
        // deleteIfExists should not throw
        assertFalse(Files.deleteIfExists(file));
    }
    
    public void testDeleteNonEmptyDirectory() throws IOException {
        Path dir = getPath("/nonempty");
        Path file = dir.resolve("file.txt");
        
        Files.createDirectory(dir);
        Files.write(file, "data".getBytes());
        
        assertThrows(DirectoryNotEmptyException.class, () -> Files.delete(dir));
        
        // Should succeed after deleting contents
        Files.delete(file);
        Files.delete(dir);
        assertFalse(Files.exists(dir));
    }
    
    public void testCreateNestedDirectories() throws IOException {
        Path deep = getPath("/a/b/c/d/e");
        
        Files.createDirectories(deep);
        assertTrue(Files.isDirectory(deep));
        assertTrue(Files.isDirectory(getPath("/a")));
        assertTrue(Files.isDirectory(getPath("/a/b")));
        assertTrue(Files.isDirectory(getPath("/a/b/c")));
    }
    
    // ==================== File Read/Write Tests ====================
    
    public void testReadWriteContent() throws IOException {
        Path file = getPath("/data.txt");
        String content = "Hello, FileSystem!";
        
        Files.write(file, content.getBytes());
        String read = new String(Files.readAllBytes(file));
        
        assertEquals(content, read);
    }
    
    public void testEmptyFile() throws IOException {
        Path file = getPath("/empty.txt");
        
        Files.write(file, new byte[0]);
        assertTrue(Files.exists(file));
        assertEquals(0, Files.size(file));
        assertArrayEquals(new byte[0], Files.readAllBytes(file));
    }
    
    public void testLargeFile() throws IOException {
        Path file = getPath("/large.bin");
        byte[] data = new byte[1024 * 1024]; // 1 MB
        new Random().nextBytes(data);
        
        Files.write(file, data);
        assertEquals(data.length, Files.size(file));
        assertArrayEquals(data, Files.readAllBytes(file));
    }
    
    public void testAppendToFile() throws IOException {
        Path file = getPath("/append.txt");
        
        Files.write(file, "Line 1\n".getBytes());
        Files.write(file, "Line 2\n".getBytes(), StandardOpenOption.APPEND);
        Files.write(file, "Line 3\n".getBytes(), StandardOpenOption.APPEND);
        
        String content = new String(Files.readAllBytes(file));
        assertEquals("Line 1\nLine 2\nLine 3\n", content);
    }
    
    public void testTruncateFile() throws IOException {
        Path file = getPath("/truncate.txt");
        
        Files.write(file, "Long content here".getBytes());
        assertEquals(17, Files.size(file));
        
        Files.write(file, "Short".getBytes());
        assertEquals(5, Files.size(file));
        assertEquals("Short", new String(Files.readAllBytes(file)));
    }
    
    public void testSeekableByteChannelRead() throws IOException {
        Path file = getPath("/channel.txt");
        Files.write(file, "0123456789".getBytes());
        
        try (SeekableByteChannel channel = Files.newByteChannel(file, StandardOpenOption.READ)) {
            assertEquals(10, channel.size());
            assertEquals(0, channel.position());
            
            // Read first 5 bytes
            ByteBuffer buffer = ByteBuffer.allocate(5);
            int read = channel.read(buffer);
            assertEquals(5, read);
            assertEquals(5, channel.position());
            assertEquals("01234", new String(buffer.array()));
            
            // Seek to position 7
            channel.position(7);
            assertEquals(7, channel.position());
            
            // Read remaining bytes
            buffer = ByteBuffer.allocate(10);
            read = channel.read(buffer);
            assertEquals(3, read);
            assertEquals("789", new String(buffer.array(), 0, 3));
        }
    }
    
    public void testSeekableByteChannelWrite() throws IOException {
        Path file = getPath("/write-channel.txt");
        
        try (SeekableByteChannel channel = Files.newByteChannel(file, 
                StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            
            ByteBuffer buffer = ByteBuffer.wrap("Hello".getBytes());
            channel.write(buffer);
            assertEquals(5, channel.position());
            
            // Seek and overwrite
            channel.position(0);
            buffer = ByteBuffer.wrap("HELLO".getBytes());
            channel.write(buffer);
        }
        
        assertEquals("HELLO", new String(Files.readAllBytes(file)));
    }
    
    public void testSeekableByteChannelTruncate() throws IOException {
        Path file = getPath("/truncate-channel.txt");
        Files.write(file, "0123456789".getBytes());
        
        try (SeekableByteChannel channel = Files.newByteChannel(file, 
                StandardOpenOption.WRITE)) {
            assertEquals(10, channel.size());
            
            channel.truncate(5);
            assertEquals(5, channel.size());
        }
        
        assertEquals("01234", new String(Files.readAllBytes(file)));
    }
    
    public void testAppendModeWithSeek() throws IOException {
        Path file = getPath("/append-seek.txt");
        
        // Create initial content
        Files.write(file, "AAAA".getBytes());
        assertEquals("AAAA", new String(Files.readAllBytes(file)));
        
        // Open in append mode
        try (SeekableByteChannel channel = Files.newByteChannel(file,
                StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            
            // Initial position should be at EOF
            assertEquals(4, channel.position());
            
            // Seek to beginning (seeking is allowed in append mode)
            channel.position(0);
            
            // Position query may return different values depending on implementation
            // Some implementations keep position at 0 until write, others reset to EOF
            // The key test is: WHERE does the write actually go?
            
            // Write - should append to EOF regardless of seek
            ByteBuffer buffer = ByteBuffer.wrap("BBBB".getBytes());
            int written = channel.write(buffer);
            assertEquals(4, written);
            
            // After write, position should be at new EOF
            assertEquals(8, channel.position());
        }
        
        // Verify content was appended, not overwritten
        String content = new String(Files.readAllBytes(file));
        assertEquals("AAAABBBB", content);
    }
    
    public void testMultipleAppendsWithSeek() throws IOException {
        Path file = getPath("/multi-append.txt");
        
        Files.write(file, "0".getBytes());
        
        try (SeekableByteChannel channel = Files.newByteChannel(file,
                StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            
            // Seek to various positions and write - all should append
            channel.position(0);
            channel.write(ByteBuffer.wrap("1".getBytes()));
            
            channel.position(0);
            channel.write(ByteBuffer.wrap("2".getBytes()));
            
            channel.position(5);  // Beyond current file
            channel.write(ByteBuffer.wrap("3".getBytes()));
            
            assertEquals(4, channel.position());
        }
        
        assertEquals("0123", new String(Files.readAllBytes(file)));
    }
    
    public void testAppendVsWriteMode() throws IOException {
        Path file1 = getPath("/write-mode.txt");
        Path file2 = getPath("/append-mode.txt");
        
        Files.write(file1, "XXXX".getBytes());
        Files.write(file2, "XXXX".getBytes());
        
        // WRITE mode (no APPEND): seeking allows overwriting
        try (SeekableByteChannel channel = Files.newByteChannel(file1,
                StandardOpenOption.WRITE)) {
            channel.position(0);
            channel.write(ByteBuffer.wrap("YYYY".getBytes()));
        }
        assertEquals("YYYY", new String(Files.readAllBytes(file1)));
        
        // APPEND mode: seeking does NOT allow overwriting
        try (SeekableByteChannel channel = Files.newByteChannel(file2,
                StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            channel.position(0);
            channel.write(ByteBuffer.wrap("YYYY".getBytes()));
        }
        assertEquals("XXXXYYYY", new String(Files.readAllBytes(file2)));
    }

    
    
    
    // ==================== Directory Listing Tests ====================
    
    public void testDirectoryListing() throws IOException {
        Path dir = getPath("/listtest");
        Files.createDirectory(dir);
        
        Files.write(dir.resolve("file1.txt"), "1".getBytes());
        Files.write(dir.resolve("file2.txt"), "2".getBytes());
        Files.createDirectory(dir.resolve("subdir"));
        
        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                names.add(entry.getFileName().toString());
            }
        }
        
        assertEquals(3, names.size());
        assertTrue(names.contains("file1.txt"));
        assertTrue(names.contains("file2.txt"));
        assertTrue(names.contains("subdir"));
    }
    
    public void testDirectoryFilter() throws IOException {
        Path dir = getPath("/filtertest");
        Files.createDirectory(dir);
        
        Files.write(dir.resolve("file1.txt"), "1".getBytes());
        Files.write(dir.resolve("file2.txt"), "2".getBytes());
        Files.write(dir.resolve("file.log"), "3".getBytes());
        
        List<String> txtFiles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.txt")) {
            for (Path entry : stream) {
                txtFiles.add(entry.getFileName().toString());
            }
        }
        
        assertEquals(2, txtFiles.size());
        assertTrue(txtFiles.contains("file1.txt"));
        assertTrue(txtFiles.contains("file2.txt"));
        assertFalse(txtFiles.contains("file.log"));
    }
    
    public void testWalkFileTree() throws IOException {
        Path root = getPath("/walktree");
        Files.createDirectories(root.resolve("a/b/c"));
        Files.createDirectories(root.resolve("a/d"));
        Files.write(root.resolve("file1.txt"), "1".getBytes());
        Files.write(root.resolve("a/file2.txt"), "2".getBytes());
        Files.write(root.resolve("a/b/file3.txt"), "3".getBytes());
        
        List<Path> allPaths = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                allPaths.add(file);
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                allPaths.add(dir);
                return FileVisitResult.CONTINUE;
            }
        });
        
        assertTrue(allPaths.size() >= 7); // root + 4 dirs + 3 files
    }
    
    public void testEmptyDirectoryListing() throws IOException {
        Path dir = getPath("/emptydir");
        Files.createDirectory(dir);
        
        List<Path> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            stream.forEach(entries::add);
        }
        
        assertTrue(entries.isEmpty());
    }
    
    // ==================== Copy and Move Tests ====================
    
    public void testCopyFile() throws IOException {
        Path source = getPath("/source.txt");
        Path target = getPath("/target.txt");
        
        Files.write(source, "content".getBytes());
        Files.copy(source, target);
        
        assertTrue(Files.exists(target));
        assertEquals("content", new String(Files.readAllBytes(target)));
        
        // Source should still exist
        assertTrue(Files.exists(source));
    }
    
    public void testCopyFileReplaceExisting() throws IOException {
        Path source = getPath("/src.txt");
        Path target = getPath("/tgt.txt");
        
        Files.write(source, "new content".getBytes());
        Files.write(target, "old content".getBytes());
        
        // Without REPLACE_EXISTING should fail
        assertThrows(FileAlreadyExistsException.class, () ->
            Files.copy(source, target));
        
        // With REPLACE_EXISTING should succeed
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        assertEquals("new content", new String(Files.readAllBytes(target)));
    }
    
    public void testMoveFile() throws IOException {
        Path source = getPath("/movesrc.txt");
        Path target = getPath("/movetgt.txt");
        
        Files.write(source, "move me".getBytes());
        Files.move(source, target);
        
        assertFalse(Files.exists(source));
        assertTrue(Files.exists(target));
        assertEquals("move me", new String(Files.readAllBytes(target)));
    }
    
    public void testCopyDirectoryRecursive() throws IOException {
        Path srcDir = getPath("/copydir");
        Path tgtDir = getPath("/copieddir");
        
        Files.createDirectories(srcDir.resolve("subdir"));
        Files.write(srcDir.resolve("file1.txt"), "1".getBytes());
        Files.write(srcDir.resolve("subdir/file2.txt"), "2".getBytes());
        
        // Manual recursive copy
        Files.walkFileTree(srcDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) 
                    throws IOException {
                Path targetDir = tgtDir.resolve(srcDir.relativize(dir));
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) 
                    throws IOException {
                Files.copy(file, tgtDir.resolve(srcDir.relativize(file)));
                return FileVisitResult.CONTINUE;
            }
        });
        
        assertTrue(Files.isDirectory(tgtDir.resolve("subdir")));
        assertTrue(Files.exists(tgtDir.resolve("file1.txt")));
        assertTrue(Files.exists(tgtDir.resolve("subdir/file2.txt")));
    }
    
    // ==================== File Attributes Tests ====================
    
    public void testBasicFileAttributes() throws IOException {
        Path file = getPath("/attrs.txt");
        Files.write(file, "data".getBytes());
        
        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
        
        assertNotNull(attrs);
        assertTrue(attrs.isRegularFile());
        assertFalse(attrs.isDirectory());
        assertFalse(attrs.isSymbolicLink());
        assertFalse(attrs.isOther());
        assertEquals(4, attrs.size());
        assertNotNull(attrs.creationTime());
        assertNotNull(attrs.lastModifiedTime());
        assertNotNull(attrs.lastAccessTime());
    }
    
    public void testDirectoryAttributes() throws IOException {
        Path dir = getPath("/attrdir");
        Files.createDirectory(dir);
        
        BasicFileAttributes attrs = Files.readAttributes(dir, BasicFileAttributes.class);
        
        assertFalse(attrs.isRegularFile());
        assertTrue(attrs.isDirectory());
        assertTrue("Directory size should be non-negative", attrs.size() >= 0);
        if (fs instanceof org.monflabs.filesystem.memory.MemoryFileSystem) {
            assertEquals(0, attrs.size());
        }
    }
    
    public void testModificationTime() throws IOException {
        Path file = getPath("/modtime.txt");
        Files.write(file, "initial".getBytes());
        
        FileTime time1 = Files.getLastModifiedTime(file);
        
        // Wait a bit to ensure time difference
        try { Thread.sleep(10); } catch (InterruptedException e) {}
        
        Files.write(file, "updated".getBytes());
        FileTime time2 = Files.getLastModifiedTime(file);
        
        assertTrue(time2.compareTo(time1) >= 0);
    }
    
    public void testFileSize() throws IOException {
        Path file = getPath("/sized.txt");
        
        Files.write(file, "12345".getBytes());
        assertEquals(5, Files.size(file));
        
        Files.write(file, "123456789".getBytes());
        assertEquals(9, Files.size(file));
    }
    
    public void testFileExists() throws IOException {
        Path file = getPath("/exists.txt");
        
        assertFalse(Files.exists(file));
        assertFalse(Files.exists(file, LinkOption.NOFOLLOW_LINKS));
        
        Files.write(file, "data".getBytes());
        
        assertTrue(Files.exists(file));
        assertTrue(Files.exists(file, LinkOption.NOFOLLOW_LINKS));
    }
    
    // ==================== Access Control Tests ====================
    
    public void testReadAccess() throws IOException {
        Path file = getPath("/readable.txt");
        Files.write(file, "data".getBytes());
        
        // File should be readable after creation
        assertTrue(Files.isReadable(file));
    }
    
    public void testWriteAccess() throws IOException {
        Path file = getPath("/writable.txt");
        Files.write(file, "data".getBytes());
        
        // File should be writable after creation
        assertTrue(Files.isWritable(file));
    }
    
    public void testExecuteAccess() throws IOException {
        Path file = getPath("/executable.txt");
        Files.write(file, "data".getBytes());
        
        // Just check that the query works - actual value is platform/implementation dependent
        Files.isExecutable(file); // Should not throw
    }
    
    public void testHiddenStatus() throws IOException {
        Path file = getPath("/visible.txt");
        Files.write(file, "data".getBytes());
        
        // Just check that the query works - actual value is platform/implementation dependent
        Files.isHidden(file); // Should not throw
    }
    
    // ==================== PathMatcher Tests ====================
    
    public void testGlobMatching() {
        PathMatcher txtMatcher = fs.getPathMatcher("glob:*.txt");
        PathMatcher javaMatcher = fs.getPathMatcher("glob:*.java");
        PathMatcher starMatcher = fs.getPathMatcher("glob:*");
        
        assertTrue(txtMatcher.matches(fs.getPath("file.txt")));
        assertFalse(txtMatcher.matches(fs.getPath("file.java")));
        
        assertTrue(javaMatcher.matches(fs.getPath("Main.java")));
        assertFalse(javaMatcher.matches(fs.getPath("Main.txt")));
        
        assertTrue(starMatcher.matches(fs.getPath("anything")));
    }
    
    public void testRegexMatching() {
        PathMatcher matcher = fs.getPathMatcher("regex:.*\\.txt");
        
        assertTrue(matcher.matches(fs.getPath("file.txt")));
        assertTrue(matcher.matches(fs.getPath("another.txt")));
        assertFalse(matcher.matches(fs.getPath("file.java")));
    }
    
    public void testComplexGlobPatching() {
        PathMatcher matcher1 = fs.getPathMatcher("glob:test?.txt");
        PathMatcher matcher2 = fs.getPathMatcher("glob:**/file.txt");
        PathMatcher matcher3 = fs.getPathMatcher("glob:**.txt");
        
        // Test single character wildcard (?)
        assertTrue(matcher1.matches(getPath("test1.txt")));
        assertTrue(matcher1.matches(getPath("testA.txt")));
        assertFalse(matcher1.matches(getPath("test.txt")));
        assertFalse(matcher1.matches(getPath("test12.txt")));
        
        // Test recursive wildcard (**/name) - matches in subdirectories only
        assertFalse(matcher2.matches(getPath("file.txt")));
        assertTrue(matcher2.matches(getPath("dir/file.txt")));
        assertTrue(matcher2.matches(getPath("a/b/c/file.txt")));
        assertFalse(matcher2.matches(getPath("other.txt")));
        
        // Test recursive wildcard (**) - matches at any level including root
        assertTrue(matcher3.matches(getPath("test.txt")));
        assertTrue(matcher3.matches(getPath("dir/test.txt")));
        assertTrue(matcher3.matches(getPath("a/b/test.txt")));
        assertFalse(matcher3.matches(getPath("test.dat")));
    }
    
    // ==================== FileStore Tests ====================
    
    public void testFileStoreInfo() throws IOException {
        Path file = getPath("/test.txt");
        Files.write(file, "data".getBytes());
        
        FileStore store = Files.getFileStore(file);
        
        assertNotNull(store);
        assertNotNull(store.name());
        assertNotNull(store.type());
        // Note: isReadOnly() can vary by implementation
    }
    
    public void testFileStoreSpace() throws IOException {
        FileStore store = Files.getFileStore(getPath("/"));
        
        long totalSpace = store.getTotalSpace();
        long usableSpace = store.getUsableSpace();
        long unallocatedSpace = store.getUnallocatedSpace();
        
        assertTrue("Total space should be positive", totalSpace > 0);
        assertTrue("Usable space should be non-negative", usableSpace >= 0);
        assertTrue("Unallocated space should be non-negative", unallocatedSpace >= 0);
        assertTrue("Usable space should not exceed total space", usableSpace <= totalSpace);
    }
    
    public void testSpaceUsageTracking() throws IOException {
        FileStore store = Files.getFileStore(getPath("/"));
        long initialSpace = store.getUsableSpace();
        
        // Create a 1000 byte file
        Path file = getPath("/large.bin");
        Files.write(file, new byte[1000]);
        
        long afterSpace = store.getUsableSpace();
        
        // Space should decrease (or stay same if not tracked)
        assertTrue("Space should not increase after creating file", afterSpace <= initialSpace);
        
        // Delete file
        Files.delete(file);
        long finalSpace = store.getUsableSpace();
        
        // Space should be restored (or stay same if not tracked)
        assertTrue("Space should not decrease after deleting file", finalSpace >= afterSpace);
    }
    
    // ==================== Edge Cases and Error Handling ====================
    
    public void testDeepDirectoryStructure() throws IOException {
        StringBuilder pathBuilder = new StringBuilder("/");
        for (int i = 0; i < 100; i++) {
            pathBuilder.append("level").append(i).append("/");
        }
        
        Path deep = fs.getPath(pathBuilder.toString());
        Files.createDirectories(deep);
        
        assertTrue(Files.isDirectory(deep));
        
        Path file = deep.resolve("deep-file.txt");
        Files.write(file, "deep content".getBytes());
        assertEquals("deep content", new String(Files.readAllBytes(file)));
    }
    
    public void testSpecialCharactersInFilenames() throws IOException {
        String[] specialNames = {
            "file with spaces.txt",
            "file_with_underscores.txt",
            "file-with-dashes.txt",
            "file.multiple.dots.txt",
            "file@special#chars.txt"
        };
        
        for (String name : specialNames) {
            Path file = getPath("/" + name);
            Files.write(file, name.getBytes());
            assertTrue(Files.exists(file));
            assertEquals(name, new String(Files.readAllBytes(file)));
        }
    }
    
    public void testConcurrentFileCreation() throws InterruptedException, ExecutionException {
        int threadCount = 10;
        int filesPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            futures.add(executor.submit(() -> {
                try {
                    for (int i = 0; i < filesPerThread; i++) {
                        Path file = getPath("/thread" + threadId + "-file" + i + ".txt");
                        Files.write(file, ("Data from thread " + threadId).getBytes());
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }));
        }
        
        // Wait for all threads
        for (Future<?> future : futures) {
            future.get();
        }
        executor.shutdown();
        
        // Verify all files were created
        List<Path> allFiles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(getPath("/"))) {
            stream.forEach(allFiles::add);
        } catch (IOException e) {
            fail("Failed to list directory: " + e.getMessage());
        }
        
        assertEquals(threadCount * filesPerThread, allFiles.size());
    }
    
    public void testConcurrentReadsWrites() throws Exception {
        Path file = getPath("/concurrent.txt");
        Files.write(file, "initial".getBytes());
        
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(20);
        
        // 10 readers, 10 writers
        for (int i = 0; i < 10; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < 100; j++) {
                        Files.readAllBytes(file);
                    }
                } catch (IOException e) {
                    // Ignore
                } finally {
                    latch.countDown();
                }
            });
        }
        
        for (int i = 0; i < 10; i++) {
            final int writerId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < 100; j++) {
                        Files.write(file, ("Writer " + writerId).getBytes());
                    }
                } catch (IOException e) {
                    // Ignore
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();
        
        // File should still exist and be readable
        assertTrue(Files.exists(file));
        assertNotNull(Files.readAllBytes(file));
    }
    
    public void testManySmallFiles() throws IOException {
    	manySmallFiles(1000);
    }
    protected void manySmallFiles(int fileCount) throws IOException {
        Path dir = getPath("/manyfiles");
        Files.createDirectory(dir);
        
        for (int i = 0; i < fileCount; i++) {
            Path file = dir.resolve("file" + i + ".txt");
            Files.write(file, String.valueOf(i).getBytes());
        }
        
        // Verify all files exist
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            stream.forEach(files::add);
        }
        
        assertEquals(fileCount, files.size());
    }
    
    public void testFileNotFoundErrors() {
        Path nonexistent = getPath("/nonexistent.txt");
        
        assertThrows(NoSuchFileException.class, () -> Files.readAllBytes(nonexistent));
        assertThrows(NoSuchFileException.class, () -> Files.size(nonexistent));
        assertThrows(NoSuchFileException.class, () -> 
            Files.readAttributes(nonexistent, BasicFileAttributes.class));
    }
    
    public void testNotDirectoryErrors() throws IOException {
        Path file = getPath("/notadir.txt");
        Files.write(file, "data".getBytes());
        
        assertThrows(NotDirectoryException.class, () -> 
            Files.newDirectoryStream(file));
    }
    
    public void testSameFile() throws IOException {
        Path file1 = getPath("/same.txt");
        Path file2 = getPath("/same.txt");
        Path file3 = getPath("/different.txt");
        
        Files.write(file1, "data".getBytes());
        Files.write(file3, "data".getBytes());
        
        assertTrue(Files.isSameFile(file1, file2));
        assertFalse(Files.isSameFile(file1, file3));
    }
    
    // ==================== Integration Tests ====================
    
    public void testCompleteFileLifecycle() throws IOException {
        // Create
        Path file = getPath("/lifecycle.txt");
        Files.write(file, "version 1".getBytes());
        assertTrue(Files.exists(file));
        
        // Read
        assertEquals("version 1", new String(Files.readAllBytes(file)));
        
        // Update
        Files.write(file, "version 2".getBytes());
        assertEquals("version 2", new String(Files.readAllBytes(file)));
        
        // Copy
        Path copy = getPath("/lifecycle-copy.txt");
        Files.copy(file, copy);
        assertTrue(Files.exists(copy));
        assertEquals("version 2", new String(Files.readAllBytes(copy)));
        
        // Move
        Path moved = getPath("/lifecycle-moved.txt");
        Files.move(copy, moved);
        assertFalse(Files.exists(copy));
        assertTrue(Files.exists(moved));
        
        // Delete
        Files.delete(file);
        Files.delete(moved);
        assertFalse(Files.exists(file));
        assertFalse(Files.exists(moved));
    }
    
    public void testDirectoryTreeOperations() throws IOException {
        // Create structure
        Path root = getPath("/tree");
        Files.createDirectories(root.resolve("a/b/c"));
        Files.createDirectories(root.resolve("a/d"));
        Files.createDirectories(root.resolve("e/f"));
        
        Files.write(root.resolve("root.txt"), "root".getBytes());
        Files.write(root.resolve("a/a.txt"), "a".getBytes());
        Files.write(root.resolve("a/b/b.txt"), "b".getBytes());
        Files.write(root.resolve("a/b/c/c.txt"), "c".getBytes());
        Files.write(root.resolve("e/e.txt"), "e".getBytes());
        
        // Count all files
        final int[] fileCount = {0};
        final int[] dirCount = {0};
        
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                fileCount[0]++;
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                dirCount[0]++;
                return FileVisitResult.CONTINUE;
            }
        });
        
        assertEquals(5, fileCount[0]); // 5 .txt files
        assertTrue(dirCount[0] >= 6); // root + a + b + c + d + e + f
        
        // Delete recursively
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) 
                    throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) 
                    throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
        
        assertFalse(Files.exists(root));
    }

    public void testRootDirectoryTreeOperations() throws IOException {
        Path root = getPath("/");
        FilesUtil.clearDirectory(root);
        Files.write(getPath("project-a.txt"), "Project A content".getBytes());
        Files.write(root.resolve("project-b.txt"), "Project B content".getBytes());
		try (Stream<Path> stream = Files.list(root)) {
		    Object[] list = stream.map(p -> p.toString()).sorted().toArray();
		    assertArrayEquals(new Object[] {path("/project-a.txt"),path("/project-b.txt")}, list);
		}
    }
    
    public void testRealWorldScenario() throws IOException {
        // Simulate a simple document management system
        Path docsRoot = getPath("/documents");
        Path userDocs = docsRoot.resolve("user123");
        Path projects = userDocs.resolve("projects");
        Path personal = userDocs.resolve("personal");
        
        // Setup structure
        Files.createDirectories(projects);
        Files.createDirectories(personal);
        
        // Create project files
        Files.write(projects.resolve("project-a.txt"), "Project A content".getBytes());
        Files.write(projects.resolve("project-b.txt"), "Project B content".getBytes());
        
        // Create personal files
        Files.write(personal.resolve("notes.txt"), "Personal notes".getBytes());
        Files.write(personal.resolve("todo.txt"), "Todo list".getBytes());
        
        // List all user documents
        List<Path> allDocs = Files.walk(userDocs)
            .filter(Files::isRegularFile)
            .collect(Collectors.toList());
        
        assertEquals(4, allDocs.size());
        
        // Search for specific files
        List<Path> projectFiles = Files.walk(projects)
            .filter(p -> p.toString().endsWith(".txt"))
            .collect(Collectors.toList());
        
        assertEquals(2, projectFiles.size());
        
        // Calculate total size
        long totalSize = Files.walk(userDocs)
            .filter(Files::isRegularFile)
            .mapToLong(p -> {
                try {
                    return Files.size(p);
                } catch (IOException e) {
                    return 0;
                }
            })
            .sum();
        
        assertTrue(totalSize > 0);
    }

    // ==================== Regression tests ====================
    
    public void testPathStartsWithWholeElements() {
        // Path.startsWith/endsWith compare name elements, not characters
        Path path = getPath("/foo/barbaz");
        assertFalse(path.startsWith(getPath("/foo/bar")));
        assertFalse(path.startsWith("/foo/bar"));
        assertTrue(path.startsWith(getPath("/foo")));
        assertTrue(path.startsWith(getPath("/foo/barbaz")));
        assertFalse(getPath("/a/xbar").endsWith("bar"));
        assertTrue(getPath("/a/xbar").endsWith("xbar"));
        assertTrue(getPath("/a/xbar").endsWith("a/xbar"));
        assertFalse(getPath("/a/xbar").endsWith("/xbar")); // absolute suffix must match the whole path
        assertFalse(getPath("a/b").startsWith(getPath("/a")));
    }
    
    public void testRepeatedSeparatorsCollapsed() {
        Path a = fs.getPath(path("/a") + separator + separator + "b");
        assertEquals(path("/a/b"), a.toString());
        assertEquals(getPath("/a/b"), a);
        assertEquals(2, a.getNameCount());
    }
    
    public void testGlobBracesAndClasses() {
        PathMatcher m = fs.getPathMatcher("glob:*.{java,class}");
        assertTrue(m.matches(fs.getPath("A.java")));
        assertTrue(m.matches(fs.getPath("A.class")));
        assertFalse(m.matches(fs.getPath("A.txt")));
        PathMatcher neg = fs.getPathMatcher("glob:[!a]*.txt");
        assertTrue(neg.matches(fs.getPath("b1.txt")));
        assertFalse(neg.matches(fs.getPath("a1.txt")));
        PathMatcher range = fs.getPathMatcher("glob:[a-c].txt");
        assertTrue(range.matches(fs.getPath("b.txt")));
        assertFalse(range.matches(fs.getPath("d.txt")));
        // An escaped character is literal, never a regex class
        PathMatcher esc = fs.getPathMatcher("glob:\\d.txt");
        assertTrue(esc.matches(fs.getPath("d.txt")));
        assertFalse(esc.matches(fs.getPath("1.txt")));
    }
    
    public void testToUriEncodesSpecialCharacters() throws IOException {
        Path p = getPath("/dir with space/file#1%.txt");
        java.net.URI uri = p.toUri();
        assertNotNull(uri);
        assertTrue(uri.toString(), uri.getPath().endsWith("dir with space/file#1%.txt") || uri.getPath().endsWith("dir with space\\file#1%.txt"));
    }
    
    public void testOnlyBasicAttributeViewSupported() throws IOException {
        Path file = getPath("/attr.txt");
        Files.write(file, new byte[] {1,2,3});
        assertEquals(3L, Files.readAttributes(file, "basic:size").get("size"));
        assertEquals(3L, Files.readAttributes(file, "size").get("size"));
        // A view the filesystem does not support is rejected (it used to fall back to basic attributes for "posix:*")
        if (!fs.supportedFileAttributeViews().contains("posix") && !(fs instanceof org.monflabs.filesystem.path.PathFileSystem)) {
            assertThrows(UnsupportedOperationException.class, () -> Files.readAttributes(file, "posix:*"));
        }
    }

    // ==================== java.nio contract tests ====================
    
    public void testSetLastModifiedTime() throws IOException {
        Path file = getPath("/times.txt");
        Files.write(file, "t".getBytes());
        FileTime t = FileTime.fromMillis(1_500_000_000_000L);
        // getFileAttributeView() used to return null, so this threw a NullPointerException
        Files.setLastModifiedTime(file, t);
        assertEquals(t, Files.getLastModifiedTime(file));
        java.nio.file.attribute.BasicFileAttributeView view =
            Files.getFileAttributeView(file, java.nio.file.attribute.BasicFileAttributeView.class);
        assertNotNull(view);
        assertEquals("basic", view.name());
        assertEquals(t, view.readAttributes().lastModifiedTime());
        FileTime t2 = FileTime.fromMillis(1_600_000_000_000L);
        Files.setAttribute(file, "basic:lastModifiedTime", t2);
        assertEquals(t2, Files.getLastModifiedTime(file));
        assertThrows(NoSuchFileException.class, () -> Files.setLastModifiedTime(getPath("/missing.txt"), t));
    }
    
    public void testUnknownAttributeNameRejected() throws IOException {
        Path file = getPath("/attrs.txt");
        Files.write(file, "a".getBytes());
        assertThrows(IllegalArgumentException.class, () -> Files.readAttributes(file, "basic:nope"));
        assertEquals(2, Files.readAttributes(file, "size,isDirectory").size());
    }
    
    public void testCreateDoesNotCreateMissingParents() throws IOException {
        // Opening with CREATE creates the file, never its parents (the delegating filesystem
        // used to create them silently)
        assertThrows(NoSuchFileException.class, () -> Files.write(getPath("/nope/deep/f.txt"), "x".getBytes()));
        assertFalse(Files.exists(getPath("/nope")));
    }
    
    public void testCopyDirectoryCopiesOnlyTheDirectory() throws IOException {
        Path src = getPath("/shallow-src");
        Files.createDirectories(src.resolve("sub"));
        Files.write(src.resolve("f.txt"), "f".getBytes());
        Files.copy(src, getPath("/shallow-tgt"));
        assertTrue(Files.isDirectory(getPath("/shallow-tgt")));
        try (Stream<Path> list = Files.list(getPath("/shallow-tgt"))) {
            assertEquals(0, list.count());
        }
    }
    
    public void testReplaceExistingNeverReplacesNonEmptyDirectory() throws IOException {
        Files.createDirectories(getPath("/full/inner"));
        Files.write(getPath("/full/inner/g.txt"), "g".getBytes());
        Files.write(getPath("/replacement.txt"), "r".getBytes());
        assertThrows(DirectoryNotEmptyException.class,
            () -> Files.copy(getPath("/replacement.txt"), getPath("/full"), StandardCopyOption.REPLACE_EXISTING));
        assertThrows(DirectoryNotEmptyException.class,
            () -> Files.move(getPath("/replacement.txt"), getPath("/full"), StandardCopyOption.REPLACE_EXISTING));
        assertEquals("g", new String(Files.readAllBytes(getPath("/full/inner/g.txt"))));
        assertTrue(Files.exists(getPath("/replacement.txt")));
    }
    
    public void testMoveOntoItselfSpelledDifferently() throws IOException {
        Files.createDirectories(getPath("/m"));
        Files.write(getPath("/same.txt"), "same".getBytes());
        Files.move(getPath("/same.txt"), getPath("/m/../same.txt"));
        assertEquals("same", new String(Files.readAllBytes(getPath("/same.txt"))));
    }
    
    public void testFilterExceptionPropagates() throws IOException {
        Files.write(getPath("/one.txt"), "1".getBytes());
        // An IOException from the filter used to silently drop the entry
        assertThrows(IOException.class, () -> Files.newDirectoryStream(getPath("/"), p -> { throw new IOException("filter"); }));
    }
    
    public void testDriveLikeNamesAreOrdinaryNames() throws IOException {
        if (fs.getSeparator().equals("\\")) {
            return; // Windows: "x:" really is a drive letter
        }
        Path p = fs.getPath("c:foo");
        // Only a Windows filesystem has drive letters: "c:foo" is a relative name here
        assertFalse(p.isAbsolute());
        assertNull(p.getRoot());
        assertEquals(1, p.getNameCount());
        Path q = fs.getPath("/x:/f.txt");
        assertEquals(2, q.getNameCount());
        assertEquals("x:", q.getName(0).toString());
        assertEquals(q, q.normalize());
        assertEquals(fs.getPath("/"), q.getRoot());
        assertEquals(fs.getPath("x:/f.txt"), q.subpath(0, 2));
    }
    
    public void testToRealPath() throws IOException {
        Files.createDirectories(getPath("/real/dir"));
        Files.write(getPath("/real/f.txt"), "r".getBytes());
        assertEquals(getPath("/real/f.txt"), getPath("/real/dir/../f.txt").toRealPath());
        assertEquals(getPath("/"), getPath("/").toRealPath());
        assertThrows(NoSuchFileException.class, () -> getPath("/real/missing.txt").toRealPath());
    }
    
    public void testRelativePathsAgreeWithToAbsolutePath() throws IOException {
        Files.write(getPath("/rel.txt"), "rel".getBytes());
        Path relative = fs.getPath("rel.txt");
        assertFalse(relative.isAbsolute());
        // Provider operations resolve a relative path the way toAbsolutePath() does
        assertEquals(Files.exists(relative.toAbsolutePath()), Files.exists(relative));
        assertTrue(Files.isSameFile(relative, relative.toAbsolutePath()));
    }
}
