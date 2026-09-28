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
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.ReadOnlyFileSystemException;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicInteger;

import org.monflabs.filesystem.resources.ResourceFileSystem;
import org.monflabs.util.FileUtil;

import tests.ProjectTestCase;

/**
 * Test suite for ResourceFileSystem implementation.
 * 
 * Note: ResourceFileSystem is read-only, so tests expect ReadOnlyFileSystemException
 * for write operations.
 */
public class ResourceFileSystemTest extends ProjectTestCase {
    
    private static final AtomicInteger fsCounter = new AtomicInteger(0);
    private FileSystem fs;
    private File tempResourceFolder;
    private Path tempResourceDir;
    private ClassLoader testClassLoader;
    
    @Override
	public void setUp() throws IOException {
        // Create temporary directory for test resources
        tempResourceFolder = new File(support.getTargetTempDirectory(), "ResourceFileSystemTest-"+ fsCounter.incrementAndGet() );
        FileUtil.deleteFile(tempResourceFolder);
        tempResourceFolder.mkdirs();
        
        tempResourceDir = tempResourceFolder.toPath();
        
        // Create test resources
        createTestResources(tempResourceDir);
        
        // Create ClassLoader that loads from temp directory
        try {
            URL[] urls = new URL[] { tempResourceDir.toUri().toURL() };
            testClassLoader = new URLClassLoader(urls, null);
        } catch (Exception e) {
            throw new IOException("Failed to create test ClassLoader", e);
        }
        
        // Open the resource filesystem
        fs = ResourceFileSystem.newBuilder()
        		.classLoader(testClassLoader)
        		.build();
    }
    
    @Override
	public void tearDown() throws IOException {
        if (fs != null && fs.isOpen()) {
            fs.close();
        }
        
        // Close ClassLoader if possible
        if (testClassLoader instanceof URLClassLoader) {
            ((URLClassLoader) testClassLoader).close();
        }
        
        // Delete temporary folder
        if (tempResourceFolder != null && tempResourceFolder.exists()) {
            FileUtil.deleteFile(tempResourceFolder);
        }
    }
    
    /**
     * Create test resources in a temporary directory.
     */
    private void createTestResources(Path dir) throws IOException {
        // Create manifest file
        String manifest = 
            "file1.txt\n" +
            "file2.txt\n" +
            "dir1/file3.txt\n" +
            "dir1/dir2/file4.txt\n";
        Files.writeString(dir.resolve("resources.manifest"), manifest);
        
        // Create test files
        Files.writeString(dir.resolve("file1.txt"), "content1");
        Files.writeString(dir.resolve("file2.txt"), "content2");
        
        Files.createDirectories(dir.resolve("dir1"));
        Files.writeString(dir.resolve("dir1/file3.txt"), "content3");
        
        Files.createDirectories(dir.resolve("dir1/dir2"));
        Files.writeString(dir.resolve("dir1/dir2/file4.txt"), "content4");
    }

    
    // === Basic FileSystem Tests ===
    
    public void testFileSystemReadOnly() {
        assertTrue(fs.isOpen());
        assertTrue(fs.isReadOnly());
        assertEquals("/", fs.getSeparator());
    }
    
	public void testFileSystemClose() throws IOException {
        fs.close();
        assertFalse(fs.isOpen());
        
        // Operations after close should fail
        assertThrows(ClosedFileSystemException.class, () -> fs.getPath("/test.txt"));
    }
    
    public void testRootDirectory() {
        Iterable<Path> roots = fs.getRootDirectories();
        assertNotNull(roots);
        
        Path root = roots.iterator().next();
        assertEquals("/", root.toString());
    }
    
    public void testFileStore() {
        Iterable<FileStore> stores = fs.getFileStores();
        assertNotNull(stores);
        
        FileStore store = stores.iterator().next();
        assertTrue(store.isReadOnly());
        assertEquals("resource", store.type());
    }
    
    // === Path Operations Tests ===
    
	public void testPathCreation() {
        Path root = fs.getPath("/");
        assertNotNull(root);
        assertEquals("/", root.toString());
        
        Path file = fs.getPath("/test.txt");
        assertEquals("/test.txt", file.toString());
        
        Path nested = fs.getPath("/dir1/dir2/file.txt");
        assertEquals("/dir1/dir2/file.txt", nested.toString());
    }
    
	public void testPathResolve() {
        Path base = fs.getPath("/dir1");
        Path resolved = base.resolve("file.txt");
        assertEquals("/dir1/file.txt", resolved.toString());
    }
    
    // === File Reading Tests ===
    
    public void testFileReading() throws IOException {
        Path file = fs.getPath("/file1.txt");
        assertTrue(Files.exists(file));
        
        String content = new String(Files.readAllBytes(file));
        assertEquals("content1", content);
    }
    
    public void testNestedFileReading() throws IOException {
        Path file = fs.getPath("/dir1/file3.txt");
        assertTrue(Files.exists(file));
        
        String content = new String(Files.readAllBytes(file));
        assertEquals("content3", content);
    }
    
    public void testNonExistentFile() {
        Path file = fs.getPath("/nonexistent.txt");
        assertFalse(Files.exists(file));
        
        assertThrows(NoSuchFileException.class, () -> Files.readAllBytes(file));
    }
    
    // === Directory Operations Tests ===
    
	public void testDirectoryListing() throws IOException {
        Path root = fs.getPath("/");
        
        int count = 0;
        boolean foundFile1 = false;
        boolean foundFile2 = false;
        boolean foundDir1 = false;
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path entry : stream) {
                count++;
                String name = entry.getFileName().toString();
                if (name.equals("file1.txt")) foundFile1 = true;
                if (name.equals("file2.txt")) foundFile2 = true;
                if (name.equals("dir1")) foundDir1 = true;
            }
        }
        
        assertTrue(count >= 2); // At least file1, file2, dir1
        assertTrue(foundFile1);
        assertTrue(foundFile2);
        assertTrue(foundDir1);
    }
    
    public void testSubdirectoryListing() throws IOException {
        Path dir = fs.getPath("/dir1");
        assertTrue(Files.isDirectory(dir));
        
        boolean foundFile = false;
        boolean foundDir = false;
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                String name = entry.getFileName().toString();
                if (name.equals("file3.txt")) foundFile = true;
                if (name.equals("dir2")) foundDir = true;
            }
        }
        
        assertTrue(foundFile);
        assertTrue(foundDir);
    }
    
    public void testImplicitDirectories() throws IOException {
        Path dir1 = fs.getPath("/dir1");
        assertTrue(Files.exists(dir1));
        assertTrue(Files.isDirectory(dir1));
        
        Path dir2 = fs.getPath("/dir1/dir2");
        assertTrue(Files.exists(dir2));
        assertTrue(Files.isDirectory(dir2));
    }
    
	public void testWalkFileTree() throws IOException {
        Path root = fs.getPath("/");
        
        long fileCount = Files.walk(root)
            .filter(Files::isRegularFile)
            .count();
        
        assertTrue(fileCount >= 4); // file1, file2, file3, file4
    }
    
    // === Attributes Tests ===
    
    public void testFileAttributes() throws IOException {
        Path file = fs.getPath("/file1.txt");
        
        var attrs = Files.readAttributes(file, java.nio.file.attribute.BasicFileAttributes.class);
        
        assertTrue(attrs.isRegularFile());
        assertFalse(attrs.isDirectory());
        assertEquals(8, attrs.size()); // "content1".length()
    }
    
	public void testDirectoryAttributes() throws IOException {
        Path dir = fs.getPath("/dir1");
        
        var attrs = Files.readAttributes(dir, java.nio.file.attribute.BasicFileAttributes.class);
        
        assertFalse(attrs.isRegularFile());
        assertTrue(attrs.isDirectory());
        assertEquals(0, attrs.size());
    }
    
    // === PathMatcher Tests ===
    
	public void testGlobMatching() {
        PathMatcher txtMatcher = fs.getPathMatcher("glob:*.txt");
        
        assertTrue(txtMatcher.matches(fs.getPath("file1.txt")));
        assertTrue(txtMatcher.matches(fs.getPath("file2.txt")));
        assertFalse(txtMatcher.matches(fs.getPath("file.dat")));
    }
    
    public void testRecursiveMatching() throws IOException {
        PathMatcher matcher = fs.getPathMatcher("glob:**.txt");
        
        long matchCount = Files.walk(fs.getPath("/"))
            .filter(matcher::matches)
            .count();
        
        assertEquals("Should match all .txt files", 4, matchCount);
    }
    
    public void testPathPatternMatching() throws IOException {
        PathMatcher matcher = fs.getPathMatcher("glob:dir1/*.txt");
        
        assertTrue(matcher.matches(fs.getPath("dir1/file3.txt")));
        assertFalse(matcher.matches(fs.getPath("file1.txt")));
        assertFalse(matcher.matches(fs.getPath("dir1/dir2/file4.txt")));
    }
    
    // === Read-Only Enforcement Tests ===
    
    public void testWriteBlocked() {
        Path file = fs.getPath("/new.txt");
        
        assertThrows(ReadOnlyFileSystemException.class, 
            () -> Files.write(file, "data".getBytes()));
    }
    
    public void testDeleteBlocked() {
        Path file = fs.getPath("/file1.txt");
        
        assertThrows(ReadOnlyFileSystemException.class, 
            () -> Files.delete(file));
    }
    
    public void testCreateDirectoryBlocked() {
        Path dir = fs.getPath("/newdir");
        
        assertThrows(ReadOnlyFileSystemException.class, 
            () -> Files.createDirectory(dir));
    }
    
    public void testMoveBlocked() {
        Path source = fs.getPath("/file1.txt");
        Path target = fs.getPath("/file1-moved.txt");
        
        assertThrows(ReadOnlyFileSystemException.class, 
            () -> Files.move(source, target));
    }
    
    public void testWriteAccessBlocked() {
        Path file = fs.getPath("/file1.txt");
        
        assertThrows(AccessDeniedException.class, 
            () -> fs.provider().checkAccess(file, AccessMode.WRITE));
    }
    
    // === Integration Tests ===
    
    public void testCompleteReadWorkflow() throws IOException {
        // Navigate to nested file
        Path root = fs.getPath("/");
        Path dir1 = root.resolve("dir1");
        Path dir2 = dir1.resolve("dir2");
        Path file = dir2.resolve("file4.txt");
        
        // Verify path
        assertEquals("/dir1/dir2/file4.txt", file.toString());
        
        // Check existence
        assertTrue(Files.exists(file));
        assertTrue(Files.isRegularFile(file));
        
        // Read content
        String content = new String(Files.readAllBytes(file));
        assertEquals("content4", content);
        
        // Check parent
        assertEquals("/dir1/dir2", file.getParent().toString());
    }
    
    public void testCopyToOtherFilesystem() throws IOException {
        Path source = fs.getPath("/file1.txt");
        Path target = Files.createTempFile("resource-copy-", ".txt");
        
        try {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            
            String sourceContent = new String(Files.readAllBytes(source));
            String targetContent = new String(Files.readAllBytes(target));
            
            assertEquals(sourceContent, targetContent);
        } finally {
            Files.deleteIfExists(target);
        }
    }
    
    // === Base Path Tests ===
    
    public void testBasePath() throws IOException {
        // Create a subdirectory with its own manifest
        Path subDir = tempResourceDir.resolve("subdir");
        Files.createDirectories(subDir);
        
        String subManifest = "sub1.txt\nsub2.txt\n";
        Files.writeString(subDir.resolve("resources.manifest"), subManifest);
        Files.writeString(subDir.resolve("sub1.txt"), "sub content 1");
        Files.writeString(subDir.resolve("sub2.txt"), "sub content 2");
        
        try (FileSystem subFS = ResourceFileSystem.newBuilder().classLoader(testClassLoader).root("subdir/").build()) {
            Path file = subFS.getPath("/sub1.txt");
            assertTrue(Files.exists(file));
            
            String content = new String(Files.readAllBytes(file));
            assertEquals("sub content 1", content);
        }
    }
    
    public void testCustomManifestFile() throws IOException {
        // Create custom manifest file
        Path customDir = tempResourceDir.resolve("custom");
        Files.createDirectories(customDir);
        
        String manifest = "custom.txt\n";
        Files.writeString(customDir.resolve("custom.manifest"), manifest);
        Files.writeString(customDir.resolve("custom.txt"), "custom content");
        
        try (FileSystem customFS = ResourceFileSystem.newBuilder().classLoader(testClassLoader).root("custom/").manifest("custom.manifest").build()) {
            Path file = customFS.getPath("/custom.txt");
            assertTrue(Files.exists(file));
            
            String content = new String(Files.readAllBytes(file));
            assertEquals("custom content", content);
        }
    }
    
    public void testMissingManifest() {
        assertThrows(org.monflabs.util.path.FileSystemRuntimeException.class, 
            () -> ResourceFileSystem.newBuilder().classLoader(testClassLoader).root("nonexistent/").build() );
    }
    
    // === Edge Cases ===
    
    public void testEmptyDirectory() throws IOException {
        // Create empty directory in manifest
        String manifest = "emptydir/placeholder.txt\n";
        Path emptyTest = tempResourceDir.resolve("emptytest");
        Files.createDirectories(emptyTest);
        Files.writeString(emptyTest.resolve("resources.manifest"), manifest);
        
        Files.createDirectories(emptyTest.resolve("emptydir"));
        Files.writeString(emptyTest.resolve("emptydir/placeholder.txt"), "placeholder");
        
        try (FileSystem emptyFS = ResourceFileSystem.newBuilder().classLoader(testClassLoader).root("emptytest/").build()) {
            Path emptyDir = emptyFS.getPath("/emptydir");
            assertTrue(Files.exists(emptyDir));
            assertTrue(Files.isDirectory(emptyDir));
        }
    }
    
    public void testSpecialCharacterPaths() throws IOException {
        String manifest = "special-file_123.txt\n";
        Path specialDir = tempResourceDir.resolve("special");
        Files.createDirectories(specialDir);
        Files.writeString(specialDir.resolve("resources.manifest"), manifest);
        Files.writeString(specialDir.resolve("special-file_123.txt"), "special content");
        
        try (FileSystem specialFS = ResourceFileSystem.newBuilder().classLoader(testClassLoader).root("special/").build()) {
            Path file = specialFS.getPath("/special-file_123.txt");
            assertTrue(Files.exists(file));
            
            String content = new String(Files.readAllBytes(file));
            assertEquals("special content", content);
        }
    }

    // === Regression tests ===
    
    public void testListMissingDirectoryThrows() {
        // A missing directory used to list as empty
        assertThrows(NoSuchFileException.class, () -> Files.newDirectoryStream(fs.getPath("/no-such-dir")));
        assertThrows(java.nio.file.NotDirectoryException.class, () -> Files.newDirectoryStream(fs.getPath("/file1.txt")));
    }
    
    public void testPathsAreNormalized() throws IOException {
        assertTrue(Files.exists(fs.getPath("/dir1/../dir1/file3.txt")));
        assertEquals("content3", new String(Files.readAllBytes(fs.getPath("/dir1/dir2/../file3.txt"))));
        assertTrue(Files.isDirectory(fs.getPath("/dir1/./dir2")));
        assertEquals(fs.getPath("/file1.txt"), fs.getPath("dir1/../file1.txt").toRealPath());
    }
    
    public void testHashInFileNames() throws IOException {
        // Only a whole line starting with '#' is a comment: "file#1.txt" used to be read as "file"
        Path dir = tempResourceDir.resolve("hash");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("resources.manifest"), "# a comment\n  # an indented comment\nfile#1.txt\t-1\n\nplain.txt\n");
        Files.writeString(dir.resolve("file#1.txt"), "hash");
        Files.writeString(dir.resolve("plain.txt"), "plain");
        try (FileSystem hfs = ResourceFileSystem.newBuilder().classLoader(testClassLoader).root("hash/").build()) {
            assertEquals(java.util.Set.of("file#1.txt", "plain.txt"), ((ResourceFileSystem) hfs).getResourcePaths());
            assertEquals("hash", new String(Files.readAllBytes(hfs.getPath("/file#1.txt"))));
            assertFalse(Files.exists(hfs.getPath("/file")));
        }
    }
    
    public void testResourceIsReadFromItsManifestsClasspathEntry() throws IOException {
        // Two classpath entries, each with a manifest; both hold "shared.txt" but only the
        // second lists it. The bytes must come from the second entry, not from the first match
        Path a = tempResourceDir.resolve("cpA");
        Path b = tempResourceDir.resolve("cpB");
        Files.createDirectories(a);
        Files.createDirectories(b);
        Files.writeString(a.resolve("resources.manifest"), "onlyA.txt\n");
        Files.writeString(a.resolve("onlyA.txt"), "A");
        Files.writeString(a.resolve("shared.txt"), "from A (not listed)");
        Files.writeString(b.resolve("resources.manifest"), "shared.txt\n");
        Files.writeString(b.resolve("shared.txt"), "from B");
        try (URLClassLoader cl = new URLClassLoader(new URL[] { a.toUri().toURL(), b.toUri().toURL() }, null);
             FileSystem two = ResourceFileSystem.newBuilder().classLoader(cl).build()) {
            assertEquals("A", new String(Files.readAllBytes(two.getPath("/onlyA.txt"))));
            assertEquals("from B", new String(Files.readAllBytes(two.getPath("/shared.txt"))));
            assertEquals(6, Files.size(two.getPath("/shared.txt")));
        }
    }
    
    public void testAttributesByNameAndView() throws IOException {
        Path file = fs.getPath("/file1.txt");
        java.util.Map<String, Object> size = Files.readAttributes(file, "size");
        assertEquals(1, size.size()); // the requested list is honoured
        assertEquals(8L, size.get("size"));
        assertThrows(UnsupportedOperationException.class, () -> Files.readAttributes(file, "posix:permissions"));
        var view = Files.getFileAttributeView(file, java.nio.file.attribute.BasicFileAttributeView.class);
        assertNotNull(view); // used to be null: Files.setLastModifiedTime() threw a NullPointerException
        assertEquals(8, view.readAttributes().size());
        assertThrows(ReadOnlyFileSystemException.class,
            () -> Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(0)));
    }
    
    public void testGlobUsesTheSharedMatcher() {
        // [!f] is "any character but f": it used to be the regex class [!f], "'!' or 'f'"
        PathMatcher neg = fs.getPathMatcher("glob:dir1/[!f].txt");
        assertTrue(neg.matches(fs.getPath("/dir1/a.txt")));
        assertFalse(neg.matches(fs.getPath("/dir1/f.txt")));
        PathMatcher esc = fs.getPathMatcher("glob:\\d1.txt");
        assertTrue(esc.matches(fs.getPath("/d1.txt")));
        assertFalse(esc.matches(fs.getPath("/51.txt")));
    }
    
    public void testFileStoreTotalSpace() throws IOException {
        // Sum of the sizes, without reading every resource
        assertEquals(32, fs.getFileStores().iterator().next().getTotalSpace());
    }
    
    public void testFilterExceptionPropagates() {
        assertThrows(IOException.class, () -> Files.newDirectoryStream(fs.getPath("/"), p -> { throw new IOException("filter"); }));
    }
}
