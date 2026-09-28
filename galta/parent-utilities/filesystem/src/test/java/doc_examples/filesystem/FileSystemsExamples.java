package doc_examples.filesystem;

import static org.junit.Assert.assertThrows;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.ReadOnlyFileSystemException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.monflabs.filesystem.delegate.PathDelegatingFileSystem;
import org.monflabs.filesystem.file.FileFileSystem;
import org.monflabs.filesystem.memory.MemoryFileSystem;
import org.monflabs.filesystem.memory.MemoryFileSystemProvider;
import org.monflabs.filesystem.path.PathFileSystem;
import org.monflabs.filesystem.resources.ResourceFileSystem;
import org.monflabs.filesystem.zip.ZipFileSystem;

import org.monflabs.util.path.FileSystemRuntimeException;

import tests.ProjectTestCase;

/**
 * Samples for docs/Utilities/FileSystems.md.
 */
public class FileSystemsExamples extends ProjectTestCase {

	private File sandbox() {
		File dir = support.getTargetTempDirectory("doc-examples-sandbox", true);
		return dir;
	}

	public void testMemoryFileSystem() throws IOException {
		try (FileSystem fs = MemoryFileSystem.newBuilder().build()) {
			Path docs = fs.getPath("/docs");
			Files.createDirectories(docs);
			Files.writeString(docs.resolve("a.txt"), "Hello", StandardCharsets.UTF_8);
			Files.writeString(fs.getPath("/docs/b.txt"), "World", StandardCharsets.UTF_8);

			assertEquals("Hello", Files.readString(fs.getPath("/docs/a.txt")));
			try (Stream<Path> s = Files.list(docs)) {
				List<String> names = s.map(p -> p.getFileName().toString()).sorted().collect(Collectors.toList());
				assertEquals(List.of("a.txt", "b.txt"), names);
			}
			assertEquals("/", fs.getSeparator());
			assertFalse(fs.isReadOnly());
		}
	}

	public void testEachBuildIsIndependent() throws IOException {
		try (FileSystem fs1 = MemoryFileSystem.newBuilder().build();
			 FileSystem fs2 = MemoryFileSystem.newBuilder().build()) {
			Files.writeString(fs1.getPath("/x.txt"), "one");
			assertTrue(Files.exists(fs1.getPath("/x.txt")));
			assertFalse(Files.exists(fs2.getPath("/x.txt")));   // separate trees
		}
	}

	public void testClosedFileSystem() throws IOException {
		FileSystem fs = MemoryFileSystem.newBuilder().build();
		fs.close();
		assertFalse(fs.isOpen());
		assertThrows(ClosedFileSystemException.class, () -> fs.getPath("/a.txt"));
	}

	public void testFileFileSystemSandbox() throws IOException {
		File root = sandbox();
		try (FileSystem fs = FileFileSystem.newBuilder().root(root).build()) {
			Files.writeString(fs.getPath("/notes.txt"), "inside");
			assertTrue(new File(root, "notes.txt").exists());

			// ".." never climbs above the root: "/../notes.txt" is "/notes.txt"
			assertEquals("inside", Files.readString(fs.getPath("/../notes.txt")));
			Files.writeString(fs.getPath("/../../escape.txt"), "x");
			assertTrue(new File(root, "escape.txt").exists());
			assertFalse(new File(root.getParentFile(), "escape.txt").exists());

			// A sandboxed path has a "file-impl" URI relative to the root
			assertEquals(URI.create("file-impl:///notes.txt"), fs.getPath("/notes.txt").toUri());
			assertEquals(new File(root, "notes.txt"), fs.getPath("/notes.txt").toFile());
		}
	}

	public void testFileFileSystemRootMustExist() {
		File missing = new File(sandbox(), "does-not-exist");
		assertThrows(FileSystemRuntimeException.class, () -> FileFileSystem.newBuilder().root(missing).build());
	}

	public void testSymlinkEscapeIsRejected() throws IOException {
		File base = sandbox();
		File root = new File(base, "root");
		File outside = new File(base, "outside");
		root.mkdirs();
		outside.mkdirs();
		Files.writeString(new File(outside, "secret.txt").toPath(), "secret");
		try {
			Files.createSymbolicLink(new File(root, "link").toPath(), outside.toPath());
		} catch (UnsupportedOperationException | IOException e) {
			return; // no symlink support on this platform
		}
		try (FileSystem fs = PathFileSystem.newBuilder().root(root.toPath()).build()) {
			// The link lives inside the root but points outside: refused
			assertThrows(AccessDeniedException.class, () -> Files.readString(fs.getPath("/link/secret.txt")));
		}
	}

	public void testPathFileSystem() throws IOException {
		File root = sandbox();
		try (PathFileSystem fs = PathFileSystem.newBuilder().root(root.toPath()).build()) {
			Path p = fs.getPath("/data/values.txt");
			Files.createDirectories(p.getParent());
			Files.writeString(p, "42");

			assertEquals("/", fs.getSeparator());   // "/" on every platform
			assertEquals(root.toPath().resolve("data").resolve("values.txt"), fs.toOSPath("/data/values.txt"));
			assertEquals("42", Files.readString(root.toPath().resolve("data/values.txt")));
			// toUri() is the URI of the real file
			assertEquals(root.toPath().resolve("data/values.txt").toUri(), p.toUri());
		}
	}

	public void testPathDelegatingOverMemory() throws IOException {
		try (FileSystem memory = MemoryFileSystem.newBuilder().build()) {
			Path tenant = memory.getPath("/tenants/acme");
			Files.createDirectories(tenant);
			try (FileSystem fs = PathDelegatingFileSystem.newBuilder().root(tenant).build()) {
				Files.writeString(fs.getPath("/config.json"), "{}");
				assertTrue(Files.exists(memory.getPath("/tenants/acme/config.json")));
				// Same chroot rule: ".." stays at the delegate root
				assertTrue(Files.exists(fs.getPath("/../config.json")));
			}
		}
	}

	private Path createZip(File dir) throws IOException {
		Path zip = new File(dir, "sample.zip").toPath();
		try (OutputStream os = Files.newOutputStream(zip); ZipOutputStream zos = new ZipOutputStream(os)) {
			zos.putNextEntry(new ZipEntry("readme.txt"));
			zos.write("read me".getBytes(StandardCharsets.UTF_8));
			zos.closeEntry();
			zos.putNextEntry(new ZipEntry("src/Main.java"));   // no explicit "src/" entry
			zos.write("class Main {}".getBytes(StandardCharsets.UTF_8));
			zos.closeEntry();
		}
		return zip;
	}

	public void testZipFileSystem() throws IOException {
		Path zip = createZip(sandbox());
		try (FileSystem fs = ZipFileSystem.newBuilder().zipFile(zip).build()) {
			assertTrue(fs.isReadOnly());
			assertEquals("read me", Files.readString(fs.getPath("/readme.txt")));
			// "src" has no entry of its own but is seen as a directory
			assertTrue(Files.isDirectory(fs.getPath("/src")));
			try (Stream<Path> s = Files.list(fs.getPath("/"))) {
				assertEquals(List.of("readme.txt", "src"),
						s.map(p -> p.getFileName().toString()).sorted().collect(Collectors.toList()));
			}
			assertThrows(ReadOnlyFileSystemException.class, () -> Files.writeString(fs.getPath("/new.txt"), "x"));
			assertThrows(ReadOnlyFileSystemException.class, () -> Files.delete(fs.getPath("/readme.txt")));

			// Copying out to another filesystem is fine
			try (FileSystem memory = MemoryFileSystem.newBuilder().build()) {
				Files.copy(fs.getPath("/readme.txt"), memory.getPath("/readme.txt"));
				assertEquals("read me", Files.readString(memory.getPath("/readme.txt")));
			}
		}
	}

	public void testResourceFileSystem() throws IOException {
		// Reads test-resources/resources.manifest from the classpath
		try (FileSystem fs = ResourceFileSystem.newBuilder().root("test-resources").build()) {
			assertTrue(fs.isReadOnly());
			String props = Files.readString(fs.getPath("/config/app.properties"));
			assertTrue(props.contains("app.version=1.0.0"));
			assertTrue(Files.isDirectory(fs.getPath("/templates/email")));
			try (Stream<Path> s = Files.list(fs.getPath("/templates"))) {
				assertEquals(List.of("email", "web"),
						s.map(p -> p.getFileName().toString()).sorted().collect(Collectors.toList()));
			}
		}
	}

	public void testGlob() throws IOException {
		try (FileSystem fs = MemoryFileSystem.newBuilder().build()) {
			PathMatcher txtInDocs = fs.getPathMatcher("glob:/docs/*.txt");
			assertTrue(txtInDocs.matches(fs.getPath("/docs/a.txt")));
			assertFalse(txtInDocs.matches(fs.getPath("/docs/sub/a.txt")));    // * stops at "/"
			assertFalse(txtInDocs.matches(fs.getPath("docs/a.txt")));         // matched against the full path string

			PathMatcher anyTxt = fs.getPathMatcher("glob:**.txt");
			assertTrue(anyTxt.matches(fs.getPath("/docs/sub/a.txt")));        // ** crosses "/"

			PathMatcher alt = fs.getPathMatcher("glob:/src/*.{java,kt}");
			assertTrue(alt.matches(fs.getPath("/src/Main.kt")));
			assertFalse(alt.matches(fs.getPath("/src/Main.scala")));

			PathMatcher cls = fs.getPathMatcher("glob:/v[0-9]/file?.[!b]*");
			assertTrue(cls.matches(fs.getPath("/v1/file1.a")));
			assertFalse(cls.matches(fs.getPath("/v1/file1.b")));

			PathMatcher regex = fs.getPathMatcher("regex:/logs/\\d+\\.log");
			assertTrue(regex.matches(fs.getPath("/logs/2024.log")));
		}
	}

	public void testZipGlobIsRelative() throws IOException {
		Path zip = createZip(sandbox());
		try (FileSystem fs = ZipFileSystem.newBuilder().zipFile(zip).build()) {
			// Zip (and resource) matchers drop the leading "/" before matching
			assertTrue(fs.getPathMatcher("glob:src/*.java").matches(fs.getPath("/src/Main.java")));
			assertFalse(fs.getPathMatcher("glob:/src/*.java").matches(fs.getPath("/src/Main.java")));
		}
	}

	public void testUris() throws IOException {
		try (FileSystem fs = MemoryFileSystem.newBuilder().build()) {
			assertEquals(URI.create("memory:///docs/a%20b.txt"), fs.getPath("/docs/a b.txt").toUri());
		}

		// A provider created with registered=true remembers its filesystems by URI
		MemoryFileSystemProvider provider = new MemoryFileSystemProvider(true);
		URI uri = URI.create("memory:///store");
		try (FileSystem fs = MemoryFileSystem.newBuilder().provider(provider).uri(uri).build()) {
			assertSame(fs, provider.getFileSystem(uri));
			assertThrows(java.nio.file.FileSystemAlreadyExistsException.class,
					() -> MemoryFileSystem.newBuilder().provider(provider).uri(uri).build());
		}
		// Closing unregisters it
		assertThrows(java.nio.file.FileSystemNotFoundException.class, () -> provider.getFileSystem(uri));
	}

	public void testWalk() throws IOException {
		try (FileSystem fs = MemoryFileSystem.newBuilder().build()) {
			Files.createDirectories(fs.getPath("/a/b"));
			Files.writeString(fs.getPath("/a/b/c.txt"), "c");
			Files.copy(fs.getPath("/a/b/c.txt"), fs.getPath("/a/d.txt"));
			try (Stream<Path> s = Files.walk(fs.getPath("/"))) {
				List<String> all = s.map(Path::toString).sorted().collect(Collectors.toList());
				assertEquals(List.of("/", "/a", "/a/b", "/a/b/c.txt", "/a/d.txt"), all);
			}
		}
	}
}
