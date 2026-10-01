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
import java.net.URI;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.ReadOnlyFileSystemException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.monflabs.filesystem.zip.ZipFileSystem;
import org.monflabs.filesystem.zip.ZipFileSystemProvider;
import org.monflabs.util.FileUtil;

import tests.ProjectTestCase;

/**
 * Test suite for ZipFileSystem implementation.
 * 
 * Note: ZipFileSystem is read-only, so many tests from AbstractFileSystemTest
 * will be overridden to expect ReadOnlyFileSystemException.
 */
public class ZipFileSystemTest extends ProjectTestCase {
    
    private static final AtomicInteger fsCounter = new AtomicInteger(0);
    private ZipFileSystemProvider provider;
    private FileSystem fs;
    private File tempZipFolder;
    private Path tempZipFile;
    
	private static final ZipFileSystemProvider zipFileSystemProvider = new ZipFileSystemProvider(false);

    
    @Override
	public void setUp() throws IOException {
        provider = zipFileSystemProvider;
        
        // Create a temporary ZIP file with test content
        tempZipFolder = new File(support.getTargetTempDirectory(), "ZipFileSystemTest-"+ fsCounter.incrementAndGet() );
        FileUtil.deleteFile(tempZipFolder);
        tempZipFolder.mkdirs();

        tempZipFile = new File(tempZipFolder, "zip-fs-test-.zip").toPath();
        createTestZipContent(tempZipFile);
        
        // Open the ZIP as a filesystem
        Map<String, Object> env = new HashMap<>();
        env.put(ZipFileSystemProvider.ZIP_FILE_PARAM, tempZipFile);
        
        fs = provider.newFileSystem(ZipFileSystem.DEFAULT_URI, env);
    }
    
    @Override
    public void tearDown() throws IOException {
        if (fs != null && fs.isOpen()) {
            fs.close();
        }
        
        // Delete temporary folder
        if (tempZipFolder != null && tempZipFolder.exists()) {
            FileUtil.deleteFile(tempZipFolder);
        }
    }
    
    /**
     * Create a ZIP file with test content.
     */
    private void createTestZipContent(Path zipPath) throws IOException {
        Map<String, String> env = new HashMap<>();
        env.put("create", "true");
        
        URI uri = URI.create("jar:" + zipPath.toUri());
        
        try (FileSystem zipFs = FileSystems.newFileSystem(uri, env)) {
            // Create files and directories for testing
            Files.write(zipFs.getPath("/file1.txt"), "content1".getBytes());
            Files.write(zipFs.getPath("/file2.txt"), "content2".getBytes());
            
            Files.createDirectories(zipFs.getPath("/dir1"));
            Files.write(zipFs.getPath("/dir1/file3.txt"), "content3".getBytes());
            
            Files.createDirectories(zipFs.getPath("/dir1/dir2"));
            Files.write(zipFs.getPath("/dir1/dir2/file4.txt"), "content4".getBytes());
            
            Files.createDirectories(zipFs.getPath("/emptydir"));
        }
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
        assertEquals("zip", store.type());
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
        
        byte[] content = Files.readAllBytes(file);
        assertEquals("content1", new String(content));
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
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path entry : stream) {
                count++;
                String name = entry.getFileName().toString();
                assertTrue(name.equals("file1.txt") || 
                          name.equals("file2.txt") ||
                          name.equals("dir1") ||
                          name.equals("emptydir"));
            }
        }
        
        assertTrue(count >= 3); // At least file1, file2, dir1
    }
    
    public void testSubdirectoryListing() throws IOException {
        Path dir = fs.getPath("/dir1");
        assertTrue(Files.isDirectory(dir));
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            boolean foundFile = false;
            boolean foundDir = false;
            
            for (Path entry : stream) {
                String name = entry.getFileName().toString();
                if (name.equals("file3.txt")) foundFile = true;
                if (name.equals("dir2")) foundDir = true;
            }
            
            assertTrue(foundFile);
            assertTrue(foundDir);
        }
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
        // The root is a synthetic entry: its size used to be -1
        assertEquals(0, Files.readAttributes(fs.getPath("/"), java.nio.file.attribute.BasicFileAttributes.class).size());
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
        
        assertEquals(4, matchCount); // file1..file4, and nothing else
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

    public void testListMissingDirectoryThrows() {
        assertThrows(NoSuchFileException.class, () -> Files.newDirectoryStream(fs.getPath("/no-such-dir")));
    }
    
    public void testSyntheticEntriesHaveTimes() throws IOException {
        // The root and implicit directories are synthetic entries; they must still report times
        assertNotNull(Files.getLastModifiedTime(fs.getPath("/")));
        assertTrue(Files.isDirectory(fs.getPath("/")));
    }
    
    public void testAccessAfterClose() throws IOException {
        Path p = fs.getPath("/");
        fs.close();
        assertThrows(ClosedFileSystemException.class, () -> fs.provider().newDirectoryStream(p, x -> true));
        assertThrows(ClosedFileSystemException.class, () -> fs.provider().readAttributes(p, java.nio.file.attribute.BasicFileAttributes.class));
    }

    // === Regression tests ===
    
    public void testEntryLookupNormalizesPath() throws IOException {
        // "/dir1/../dir1/file3.txt" is "/dir1/file3.txt": it used to be looked up verbatim and not found
        assertTrue(Files.exists(fs.getPath("/dir1/../dir1/file3.txt")));
        assertTrue(Files.exists(fs.getPath("/./file1.txt")));
        assertEquals("content3", new String(Files.readAllBytes(fs.getPath("/dir1/./dir2/../file3.txt"))));
        assertTrue(Files.isDirectory(fs.getPath("/dir1/dir2/..")));
        assertEquals(fs.getPath("/dir1/file3.txt"), fs.getPath("dir1/../dir1/file3.txt").toRealPath());
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(fs.getPath("/emptydir/../dir1"))) {
            int n = 0;
            for (Path p : ds) {
                n++;
            }
            assertEquals(2, n); // file3.txt, dir2
        }
    }
    
    public void testGlobUsesTheSharedMatcher() {
        // [!x] negates (it used to match a literal '!'), \d is a literal 'd' (it used to be a regex
        // digit class), and a stray '}' is literal instead of an unbalanced regex group
        // [!f] is "any character but f": it used to be the regex class [!f], "'!' or 'f'"
        PathMatcher neg = fs.getPathMatcher("glob:dir1/[!f].txt");
        assertTrue(neg.matches(fs.getPath("/dir1/a.txt")));
        assertFalse(neg.matches(fs.getPath("/dir1/f.txt")));
        PathMatcher esc = fs.getPathMatcher("glob:a/\\d1.txt");
        assertTrue(esc.matches(fs.getPath("/a/d1.txt")));
        assertFalse(esc.matches(fs.getPath("/a/51.txt")));
        PathMatcher brace = fs.getPathMatcher("glob:a}.txt");
        assertTrue(brace.matches(fs.getPath("a}.txt")));
        PathMatcher alt = fs.getPathMatcher("glob:*.{txt,dat}");
        assertTrue(alt.matches(fs.getPath("/x.dat")));
        assertFalse(alt.matches(fs.getPath("/x.bin")));
        // Matching stays relative to the root
        assertTrue(fs.getPathMatcher("glob:dir1/*.txt").matches(fs.getPath("/dir1/file3.txt")));
        assertTrue(fs.getPathMatcher("regex:dir1/.*").matches(fs.getPath("/dir1/file3.txt")));
        assertThrows(UnsupportedOperationException.class, () -> fs.getPathMatcher("foo:x"));
        assertThrows(IllegalArgumentException.class, () -> fs.getPathMatcher("glob"));
    }
    
    public void testImplicitDirectoryAttributes() throws IOException {
        // A ZIP without directory entries: "impl" only exists through "impl/x.txt"
        Path zip = new File(tempZipFolder, "implicit.zip").toPath();
        try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new java.util.zip.ZipEntry("impl/x.txt"));
            out.write("xyz".getBytes());
            out.closeEntry();
        }
        try (FileSystem zfs = ZipFileSystem.newBuilder().zipFile(zip).build()) {
            var attrs = Files.readAttributes(zfs.getPath("/impl"), java.nio.file.attribute.BasicFileAttributes.class);
            assertTrue(attrs.isDirectory());
            assertEquals(0, attrs.size()); // used to be -1
            assertEquals(3, Files.size(zfs.getPath("/impl/x.txt")));
        }
    }
    
    public void testAttributesByName() throws IOException {
        Path file = fs.getPath("/file1.txt");
        // The requested list is honoured (every attribute used to be returned)
        Map<String, Object> size = Files.readAttributes(file, "size");
        assertEquals(1, size.size());
        assertEquals(8L, size.get("size"));
        Map<String, Object> two = Files.readAttributes(file, "basic:size,isDirectory");
        assertEquals(2, two.size());
        assertEquals(Boolean.FALSE, two.get("isDirectory"));
        assertTrue(Files.readAttributes(file, "*").containsKey("fileKey"));
        // The view name is honoured (it used to be ignored)
        assertThrows(UnsupportedOperationException.class, () -> Files.readAttributes(file, "posix:permissions"));
        assertThrows(IllegalArgumentException.class, () -> Files.readAttributes(file, "basic:nope"));
    }
    
    public void testAttributeViewIsReadOnly() throws IOException {
        Path file = fs.getPath("/file1.txt");
        var view = Files.getFileAttributeView(file, java.nio.file.attribute.BasicFileAttributeView.class);
        // The view used to be null, so Files.setLastModifiedTime() threw a NullPointerException
        assertNotNull(view);
        assertEquals(8, view.readAttributes().size());
        assertThrows(ReadOnlyFileSystemException.class,
            () -> Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(0)));
    }
    
    public void testFilterExceptionPropagates() {
        // An IOException from the filter used to silently drop the entry
        assertThrows(IOException.class, () -> Files.newDirectoryStream(fs.getPath("/"), p -> { throw new IOException("filter"); }));
    }

    public void testUnsafeEntryNamesAreSkipped() throws Exception {
        java.nio.file.Path zip = new File(tempZipFolder, "slip.zip").toPath();
        try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(Files.newOutputStream(zip))) {
            for (String name : new String[] {"../evil.txt", "/abs.txt", "ok/./x.txt", "ok/../y.txt", "a//b.txt", "good/sub/a.txt", "good/b.txt"}) {
                out.putNextEntry(new java.util.zip.ZipEntry(name));
                out.write(name.getBytes());
                out.closeEntry();
            }
        }
        Map<String, Object> env = new HashMap<>();
        env.put(ZipFileSystemProvider.ZIP_FILE_PARAM, zip);
        try (FileSystem zfs = provider.newFileSystem(ZipFileSystem.DEFAULT_URI, env);
             java.util.stream.Stream<Path> walk = Files.walk(zfs.getPath("/"))) {
            // A ".." child made Files.walk() loop forever, and paths climb out of the archive
            java.util.List<String> all = walk.map(Path::toString).sorted().toList();
            assertEquals(java.util.List.of("/", "/good", "/good/b.txt", "/good/sub", "/good/sub/a.txt"), all);
            assertTrue(Files.isDirectory(zfs.getPath("/good/sub")));   // implicit directory
            assertEquals("good/sub/a.txt", Files.readString(zfs.getPath("/good/sub/a.txt")));
            assertFalse(Files.exists(zfs.getPath("/evil.txt")));
            assertFalse(Files.exists(zfs.getPath("/ok")));
        }
    }

    public void testRegisteredProviderFindsTheArchiveOfAUri() throws IOException {
        ZipFileSystemProvider registered = new ZipFileSystemProvider(true);
        Map<String, Object> env = new HashMap<>();
        env.put(ZipFileSystemProvider.ZIP_FILE_PARAM, tempZipFile);
        try (FileSystem zfs = registered.newFileSystem(URI.create("zip:///archive.zip"), env)) {
            Path p = zfs.getPath("/dir1/file3.txt");
            Path back = registered.getPath(p.toUri());
            assertSame(zfs, back.getFileSystem());
            assertEquals(p, back);
            assertThrows(java.nio.file.FileSystemNotFoundException.class, () -> registered.getFileSystem(URI.create("zip:///other.zip")));
        }
    }

    public void testChannelPositionBeyond2GBAndClosedChannel() throws IOException {
        java.nio.channels.SeekableByteChannel ch = Files.newByteChannel(fs.getPath("/file1.txt"));
        long far = 3L << 31;
        ch.position(far);
        // The position used to be cast to an int and wrap around
        assertEquals(far, ch.position());
        assertEquals(-1, ch.read(java.nio.ByteBuffer.allocate(4)));
        ch.close();
        // A closed channel throws ClosedChannelException, not a plain IOException
        assertThrows(java.nio.channels.ClosedChannelException.class, () -> ch.read(java.nio.ByteBuffer.allocate(4)));
        assertThrows(java.nio.channels.ClosedChannelException.class, () -> ch.position());
    }
}
