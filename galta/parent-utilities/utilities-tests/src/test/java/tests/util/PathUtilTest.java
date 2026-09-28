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
package tests.util;

import static org.junit.Assert.assertArrayEquals;

import org.monflabs.util.PathUtil;

import tests.ProjectTestCase;

public class PathUtilTest extends ProjectTestCase {

    public void testConcat() throws Exception {
        // Existing cases
        assertEquals("", PathUtil.POSIX.concat(null, null));
        assertEquals("", PathUtil.POSIX.concat(null, ""));
        assertEquals("", PathUtil.POSIX.concat("", null));
        assertEquals("", PathUtil.POSIX.concat("", ""));
        assertEquals("2", PathUtil.POSIX.concat("", "2"));
        assertEquals("1", PathUtil.POSIX.concat("1", null));
        assertEquals("1", PathUtil.POSIX.concat("1", ""));
        assertEquals("1/2", PathUtil.POSIX.concat("1", "2"));
        assertEquals("1/2", PathUtil.POSIX.concat("1", "/2"));
        assertEquals("1/2", PathUtil.POSIX.concat("1/", "2"));
        assertEquals("1/2", PathUtil.POSIX.concat("1/", "/2"));
        assertEquals("/1/2", PathUtil.POSIX.concat("/1", "2"));
        assertEquals("1/2/", PathUtil.POSIX.concat("1", "2/"));
        assertEquals("/1/2/", PathUtil.POSIX.concat("/1", "2/"));

        // Additional cases
        assertEquals("/2", PathUtil.POSIX.concat("/", "2"));
        assertEquals("1/2/3", PathUtil.POSIX.concat("1/2", "3"));
        assertEquals("1/2/3", PathUtil.POSIX.concat("1", "2/3"));
        assertEquals("1/2/3", PathUtil.POSIX.concat("1/", "/2/3"));
        assertEquals("1/2/3/", PathUtil.POSIX.concat("1", "2/3/"));
        assertEquals("1/2/3/", PathUtil.POSIX.concat("1/", "/2/3/"));
        assertEquals("a/b/c", PathUtil.POSIX.concat("a", "b/c"));
        assertEquals("a/b/c", PathUtil.POSIX.concat("a/b", "c"));
        assertEquals("a/b/c", PathUtil.POSIX.concat("a/", "/b/c"));
        assertEquals("a/b/c/", PathUtil.POSIX.concat("a", "b/c/"));
        assertEquals("a/b/c/", PathUtil.POSIX.concat("a/", "/b/c/"));
        
        // More segments
        assertEquals("", PathUtil.POSIX.concat());
        assertEquals("1", PathUtil.POSIX.concat("1"));
        assertEquals("1", PathUtil.POSIX.concat(new String[]{"1"}));
        assertEquals("1/2", PathUtil.POSIX.concat(new String[]{"1","2"}));
        assertEquals("1/2/3", PathUtil.POSIX.concat("1","2","3"));
        assertEquals("1/2/3", PathUtil.POSIX.concat("1/","/2/","3"));
        assertEquals("/1/2/3/", PathUtil.POSIX.concat("/1","2","3/"));
    }

    public void testParts() throws Exception {
        assertArrayEquals(new String[] {}, PathUtil.POSIX.getParts(null));
        assertArrayEquals(new String[] {}, PathUtil.POSIX.getParts(""));
        assertArrayEquals(new String[] {"a"}, PathUtil.POSIX.getParts("a"));
        assertArrayEquals(new String[] {"a", "b"}, PathUtil.POSIX.getParts("a/b"));
        // Additional cases
        assertArrayEquals(new String[] {"a", "b", "c"}, PathUtil.POSIX.getParts("a/b/c"));
        assertArrayEquals(new String[] {"a", "b", "c"}, PathUtil.POSIX.getParts("/a/b/c"));
        assertArrayEquals(new String[] {"a", "b", "c"}, PathUtil.POSIX.getParts("a/b/c/"));
        assertArrayEquals(new String[] {"a", "b", "c"}, PathUtil.POSIX.getParts("/a/b/c/"));
        assertArrayEquals(new String[] {""}, PathUtil.POSIX.getParts("/"));
        assertArrayEquals(new String[] {"",""}, PathUtil.POSIX.getParts("///"));
        assertArrayEquals(new String[] {"a"}, PathUtil.POSIX.getParts("/a/"));
    }

    public void testCompose() throws Exception {
        assertEquals("", PathUtil.POSIX.compose(null));
        assertEquals("", PathUtil.POSIX.compose(new String[] {}));
        assertEquals("a", PathUtil.POSIX.compose(new String[] {"a"}));
        assertEquals("a/b", PathUtil.POSIX.compose(new String[] {"a", "b"}));
        assertEquals("a/b/c", PathUtil.POSIX.compose(new String[] {"a", "b", "c"}));

        assertEquals("", PathUtil.POSIX.compose(null, 1, 1));
        assertEquals("", PathUtil.POSIX.compose(new String[] {}, 1, 1));
        assertEquals("", PathUtil.POSIX.compose(new String[] {"a"}, 1, 1));
        assertEquals("b", PathUtil.POSIX.compose(new String[] {"a", "b"}, 1, 1));
        assertEquals("b", PathUtil.POSIX.compose(new String[] {"a", "b", "c"}, 1, 1));
        assertEquals("a/b", PathUtil.POSIX.compose(new String[] {"a", "b", "c"}, 0, 2));
        assertEquals("b/c", PathUtil.POSIX.compose(new String[] {"a", "b", "c"}, 1, 2));

        // Additional cases
        assertEquals("b/c", PathUtil.POSIX.compose(new String[] {"a", "b", "c"}, 1, 5));
        assertEquals("", PathUtil.POSIX.compose(new String[] {"a", "b", "c"}, 3, 1));
        assertEquals("", PathUtil.POSIX.compose(new String[] {"a", "b", "c"}, 3, 0));
        assertEquals("", PathUtil.POSIX.compose(new String[] {"a", "b", "c"}, 0, 0));
    }

    public void testFileName() {
        assertEquals("myfile", PathUtil.POSIX.getFileName("myfile"));
        assertEquals("myfile", PathUtil.POSIX.getFileName("/myfile"));
        assertEquals("myfile", PathUtil.POSIX.getFileName("a/myfile"));
        assertEquals("myfile", PathUtil.POSIX.getFileName("a/b/myfile"));
        assertEquals("", PathUtil.POSIX.getFileName(""));
        assertEquals("", PathUtil.POSIX.getFileName("/"));
        assertEquals("", PathUtil.POSIX.getFileName("//"));
        assertEquals("", PathUtil.POSIX.getFileName("abc/"));
        assertEquals("", PathUtil.POSIX.getFileName("/abc/"));
        assertEquals("myfile.ex", PathUtil.POSIX.getFileName("myfile.ex"));

        // Additional cases
        assertEquals("file", PathUtil.POSIX.getFileName("dir/subdir/file"));
        assertEquals("", PathUtil.POSIX.getFileName("dir/subdir/file/"));
        assertEquals("file.txt", PathUtil.POSIX.getFileName("dir/file.txt"));
        assertEquals("", PathUtil.POSIX.getFileName("/dir/"));
        assertEquals(null, PathUtil.POSIX.getFileName(null));
    }

    public void testFileExtension() {
        assertEquals("", PathUtil.POSIX.getFileExtension("myfile"));
        assertEquals("", PathUtil.POSIX.getFileExtension("/myfile"));
        assertEquals("", PathUtil.POSIX.getFileExtension("a.b/myfile"));
        assertEquals("", PathUtil.POSIX.getFileExtension("/a.b/myfile"));
        assertEquals("", PathUtil.POSIX.getFileExtension("/a.b/"));
        assertEquals("b", PathUtil.POSIX.getFileExtension("/a.b"));
        assertEquals("ex", PathUtil.POSIX.getFileExtension(".ex"));
        assertEquals("ex", PathUtil.POSIX.getFileExtension("/.ex"));
        assertEquals("ex", PathUtil.POSIX.getFileExtension("myfile.ex"));
        assertEquals("ex", PathUtil.POSIX.getFileExtension("a.b/myfile.ex"));
        assertEquals("ex", PathUtil.POSIX.getFileExtension("a.b/.ex"));

        // Additional cases
        assertEquals("gz", PathUtil.POSIX.getFileExtension("archive.tar.gz"));
        assertEquals("", PathUtil.POSIX.getFileExtension("dir/"));
        assertEquals("", PathUtil.POSIX.getFileExtension(""));
        assertEquals(null, PathUtil.POSIX.getFileExtension(null));
        assertEquals("hidden", PathUtil.POSIX.getFileExtension(".hidden"));
    }

    public void testRemoveExtension() {
        assertEquals("", PathUtil.POSIX.removeExtension(""));
        assertEquals("/", PathUtil.POSIX.removeExtension("/"));
        assertEquals("myfile", PathUtil.POSIX.removeExtension("myfile"));
        assertEquals("myfile", PathUtil.POSIX.removeExtension("myfile.a"));
        assertEquals("myfile.a", PathUtil.POSIX.removeExtension("myfile.a.b"));
        assertEquals("/myfile", PathUtil.POSIX.removeExtension("/myfile"));
        assertEquals("a.b/myfile", PathUtil.POSIX.removeExtension("a.b/myfile"));
        assertEquals("a.b/myfile", PathUtil.POSIX.removeExtension("a.b/myfile.c"));
        assertEquals("a.b/myfile.c", PathUtil.POSIX.removeExtension("a.b/myfile.c.d"));

        // Additional cases
        assertEquals("archive.tar", PathUtil.POSIX.removeExtension("archive.tar.gz"));
        assertEquals("file", PathUtil.POSIX.removeExtension("file.txt"));
        assertEquals("", PathUtil.POSIX.removeExtension(".ext"));
        assertEquals(null, PathUtil.POSIX.removeExtension(null));
        assertEquals("dir/file", PathUtil.POSIX.removeExtension("dir/file.txt"));
    }

    public void testRelativePath() throws Exception {
        String base = "/a/b";
        assertEquals("c/d", PathUtil.POSIX.getRelativePath(base, "/a/b/c/d"));
        assertEquals("c\\d", PathUtil.WIN.getRelativePath("\\a\\b", "\\a\\b\\c\\d"));

        assertEquals("", PathUtil.POSIX.getRelativePath(base, "/a/b"));
        assertEquals(null, PathUtil.POSIX.getRelativePath(base, "/a/bb"));
        assertEquals(null, PathUtil.POSIX.getRelativePath(base, "/a"));
        assertEquals(null, PathUtil.POSIX.getRelativePath(base, "/a/bb/c"));

        base = "a/b";
        assertEquals("c/d", PathUtil.POSIX.getRelativePath(base, "a/b/c/d"));
        assertEquals("c\\d", PathUtil.WIN.getRelativePath("\\a\\b", "\\a\\b\\c\\d"));

        assertEquals("", PathUtil.POSIX.getRelativePath(base, "a/b"));
        assertEquals(null, PathUtil.POSIX.getRelativePath(base, "a/bb"));
        assertEquals(null, PathUtil.POSIX.getRelativePath(base, "a"));
        assertEquals(null, PathUtil.POSIX.getRelativePath(base, "a/bb/c"));

        // Additional cases
        assertEquals(null, PathUtil.POSIX.getRelativePath(null, "a/b"));
        assertEquals(null, PathUtil.POSIX.getRelativePath("a/b", null));
        assertEquals(null, PathUtil.POSIX.getRelativePath(null, null));
        assertEquals("", PathUtil.POSIX.getRelativePath("", ""));
        assertEquals("b", PathUtil.POSIX.getRelativePath("a", "a/b"));
        assertEquals(null, PathUtil.POSIX.getRelativePath("a", "b/a"));
        assertEquals(null, PathUtil.POSIX.getRelativePath("a/b", "a"));
        assertEquals("c/d/e", PathUtil.POSIX.getRelativePath("a/b", "a/b/c/d/e"));
    }

    public void testMultiSep() throws Exception {
        PathUtil pp = PathUtil.of('/','\\');
        assertEquals("a/b", pp.normalize("a/b"));
        assertEquals("a/b", pp.normalize("a\\b"));
        
        assertEquals("a\\b\\", pp.removeLeadingSep("\\a\\b\\"));
        assertEquals("\\a\\b", pp.removeTrailingSep("\\a\\b\\"));
        assertEquals("a\\b", pp.removeSep("\\a\\b\\"));
        assertEquals("a\\b/", pp.removeLeadingSep("/a\\b/"));
        assertEquals("/a\\b", pp.removeTrailingSep("/a\\b/"));
        assertEquals("a\\b", pp.removeSep("/a\\b/"));

        assertArrayEquals(new String[]{"a","b","c"}, pp.getParts("\\a\\b\\c"));
        assertArrayEquals(new String[]{"a","b","c"}, pp.getParts("/a\\b\\c"));
        assertArrayEquals(new String[]{"a","b","c"}, pp.getParts("\\a/b\\c"));
        assertArrayEquals(new String[]{"a","b","c"}, pp.getParts("\\a\\b/c"));

        assertEquals("file.txt", pp.getFileName("/a\\b/file.txt"));
        assertEquals("file.txt", pp.getFileName("/a\\b\\file.txt"));

        assertEquals(true, pp.hasFileExtension("/a\\b.log/file.txt"));
        assertEquals(false, pp.hasFileExtension("/a\\b.log/file"));
        assertEquals("txt", pp.getFileExtension("/a\\b.log\\file.txt"));
        assertEquals("txt", pp.getFileExtension("/a\\b.log/file.txt"));
        assertEquals("", pp.getFileExtension("/a\\b.log\\file"));
        assertEquals("", pp.getFileExtension("/a\\b.log/file"));
        assertEquals("/a\\b.log/file", pp.removeExtension("/a\\b.log/file.txt"));
        assertEquals("/a\\b.log/file", pp.removeExtension("/a\\b.log/file"));
        assertEquals("/a\\b.log/file.doc", pp.setExtension("/a\\b.log/file","doc"));
        assertEquals("/a\\b.log/file.doc", pp.setExtension("/a\\b.log/file.txt","doc"));
       
        assertEquals("c\\d/e", pp.getRelativePath("a/b", "a\\b/c\\d/e"));

    }

    public void testRelativePathEdgeCases() throws Exception {
        // Everything is relative to an empty base (it used to return null)
        assertEquals("a/b", PathUtil.POSIX.getRelativePath("", "a/b"));
        assertEquals("/a/b", PathUtil.POSIX.getRelativePath("", "/a/b"));
        assertEquals("a/b", PathUtil.POSIX.getRelativePath("/", "/a/b"));
        assertEquals(null, PathUtil.POSIX.getRelativePath("/x/", "/a/b"));
        // A base ending with a separator is separator agnostic too
        PathUtil pp = PathUtil.of('/','\\');
        assertEquals("c", pp.getRelativePath("a/b/", "a\\b\\c"));
        assertEquals("c", pp.getRelativePath("a\\b", "a/b/c"));
    }

    public void testDeepParts() throws Exception {
        // getParts() used to recurse once per segment
        StringBuilder b = new StringBuilder();
        for(int i=0; i<100_000; i++) {
            b.append("/p");
        }
        String[] parts = PathUtil.POSIX.getParts(b.toString());
        assertEquals(100_000, parts.length);
        assertEquals("p", parts[99_999]);
        assertArrayEquals(new String[] {"a","","b"}, PathUtil.POSIX.getParts("/a//b/"));
    }
}
