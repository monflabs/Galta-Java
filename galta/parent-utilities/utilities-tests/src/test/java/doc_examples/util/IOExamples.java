package doc_examples.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.monflabs.util.FileUtil;
import org.monflabs.util.ForwardRuntimeException;
import org.monflabs.util.IOStreamUtil;
import org.monflabs.util.PathUtil;
import org.monflabs.util.io.FastStringReader;
import org.monflabs.util.io.LRUCachedOutputStream;
import org.monflabs.util.io.LRUCharBuffer;
import org.monflabs.util.io.NullOutputStream;
import org.monflabs.util.io.ReaderInputStream;
import org.monflabs.util.io.WriterOutputStream;

import tests.ProjectTestCase;

/**
 * Samples for docs/Utilities/IO.md
 */
public class IOExamples extends ProjectTestCase {

	public void testIOStreamUtil() throws Exception {
		InputStream in = new ByteArrayInputStream("h\u00e9llo".getBytes(StandardCharsets.UTF_8));
		assertEquals("h\u00e9llo", IOStreamUtil.readContent(in, StandardCharsets.UTF_8));

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		IOStreamUtil.setContent(out, "h\u00e9llo", StandardCharsets.UTF_8);   // flushed, not closed
		assertEquals(6, out.size());
		assertArrayEquals(out.toByteArray(), new ByteArrayInputStream(out.toByteArray()).readAllBytes());

		IOStreamUtil.close(null);   // null is ignored
		ForwardRuntimeException e = assertThrows(ForwardRuntimeException.class,
				() -> IOStreamUtil.close(() -> { throw new java.io.IOException("disk gone"); }));
		assertEquals("Error while closing stream", e.getMessage());
	}

	@SuppressWarnings("deprecation")
	public void testFileUtil() throws Exception {
		File dir = new File(Files.createTempDirectory("doc").toFile(), "out");
		FileUtil.prepareEmptyDirectory(dir);             // creates it (and empties it if it existed)
		File f = new File(dir, "notes.txt");
		FileUtil.setContent(f, "caf\u00e9", StandardCharsets.UTF_8);
		assertEquals("caf\u00e9", FileUtil.readContent(f, StandardCharsets.UTF_8));

		File copy = new File(dir, "copy.txt");
		FileUtil.copy(f, copy);
		assertEquals(5, copy.length());

		assertTrue(FileUtil.emptyDirectory(dir));       // deletes the content, keeps the directory
		assertEquals(0, dir.list().length);
		assertTrue(FileUtil.deleteFile(dir.getParentFile()));   // recursive

		ForwardRuntimeException e = assertThrows(ForwardRuntimeException.class, () -> FileUtil.readContent(f));
		assertEquals("Error while reading file " + f.getPath(), e.getMessage());
	}

	public void testPathUtilNames() throws Exception {
		PathUtil p = PathUtil.POSIX;
		assertEquals("/a/b", p.getParentPath("/a/b/c.tar.gz"));
		assertEquals("", p.getParentPath("c.txt"));
		assertEquals("c.tar.gz", p.getFileName("/a/b/c.tar.gz"));
		assertEquals("gz", p.getFileExtension("/a/b/c.tar.gz"));
		assertEquals("", p.getFileExtension("/a.d/Makefile"));      // the dot must be in the file name
		assertEquals("/a/b/c.tar", p.removeExtension("/a/b/c.tar.gz"));
		assertEquals("/a/b/c.tar.bz2", p.setExtension("/a/b/c.tar.gz", "bz2"));
	}

	public void testPathUtilCompose() throws Exception {
		PathUtil p = PathUtil.POSIX;
		assertEquals("a/b", p.concat("a/", "/b"));
		assertEquals("/a/b/c", p.concat("/a", "b/", "c"));        // varargs: empty/null parts skipped
		assertArrayEquals(new String[] {"a", "b", "c"}, p.getParts("/a/b/c/"));
		assertEquals("b/c", p.compose(new String[] {"a", "b", "c"}, 1, 2));

		assertEquals("c/d", p.getRelativePath("/a/b", "/a/b/c/d"));
		assertEquals("", p.getRelativePath("/a/b", "/a/b"));
		assertNull(p.getRelativePath("/a/b", "/a/bc"));            // not below the base
		assertEquals("a/b", p.getRelativePath("/", "/a/b"));
	}

	public void testPathUtilSeparators() throws Exception {
		assertEquals("C:\\dir", PathUtil.WIN.getParentPath("C:\\dir\\file.txt"));

		// A second separator is accepted on input and normalized to the first one
		PathUtil both = PathUtil.of('/', '\\');
		assertEquals("file.txt", both.getFileName("dir\\sub/file.txt"));
		assertEquals("dir/sub/file.txt", both.normalize("dir\\sub/file.txt"));

		assertEquals("java.util", PathUtil.DOT.getParentPath("java.util.List"));
	}

	public void testReaderAndWriterBridges() throws Exception {
		InputStream in = new ReaderInputStream(new StringReader("\u00e9t\u00e9"), StandardCharsets.UTF_8);
		assertEquals(5, in.readAllBytes().length);                      // chars encoded to bytes

		StringWriter w = new StringWriter();
		WriterOutputStream os = new WriterOutputStream(w, StandardCharsets.UTF_8);
		os.write("\u00e9t\u00e9".getBytes(StandardCharsets.UTF_8));    // bytes decoded to chars
		os.close();
		assertEquals("\u00e9t\u00e9", w.toString());
	}

	public void testFastStringReader() throws Exception {
		FastStringReader r = new FastStringReader("abcdef", 2);   // starts at index 2
		assertEquals('c', r.read());
		r.mark(0);
		assertEquals(2, r.skip(2));
		r.reset();
		assertEquals('d', r.read());
	}

	public void testLRUCharBuffer() throws Exception {
		LRUCharBuffer buffer = new LRUCharBuffer.MemoryCharBuffer(10, 2);   // capacity 10, slack 2
		buffer.write("12345678");
		buffer.write("abc");                  // overflow: oldest characters dropped
		assertEquals("45678abc", buffer.toString());
		buffer.write("ABCDEFGHIJKLMNOPQRST");
		assertEquals("MNOPQRST", buffer.toString());   // at most capacity-slack characters kept after a trim

		// Tee an output stream into a buffer, e.g. to keep the tail of a process output
		LRUCharBuffer tail = new LRUCharBuffer.MemoryCharBuffer(5, 0);
		ByteArrayOutputStream all = new ByteArrayOutputStream();
		try (LRUCachedOutputStream os = LRUCachedOutputStream.of(all, tail)) {
			os.write("hello world".getBytes(StandardCharsets.UTF_8));
		}
		assertEquals("hello world", all.toString(StandardCharsets.UTF_8));
		assertEquals("world", tail.toString());

		NullOutputStream.instance.write(new byte[100]);   // discards everything
	}
}
