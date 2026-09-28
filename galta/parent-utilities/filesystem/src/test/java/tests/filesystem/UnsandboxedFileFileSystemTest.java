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
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import org.monflabs.filesystem.file.FileFileSystem;
import org.monflabs.util.FileUtil;

import tests.ProjectTestCase;

/**
 * A FileFileSystem without a root: relative paths are relative to the working directory,
 * for every operation.
 */
public class UnsandboxedFileFileSystemTest extends ProjectTestCase {
    
    private FileSystem fs;
    private File dir;
    
    @Override
    public void setUp() throws IOException {
        fs = FileFileSystem.newBuilder().build();
        dir = new File(support.getTargetTempDirectory(), "UnsandboxedFileFileSystemTest");
        FileUtil.deleteFile(dir);
        dir.mkdirs();
    }
    
    @Override
    public void tearDown() throws IOException {
        fs.close();
        FileUtil.deleteFile(dir);
    }
    
    public void testRelativePathsUseTheWorkingDirectory() throws IOException {
        // The module's pom.xml is in the working directory of the test run
        assertTrue(new File("pom.xml").exists());
        Path rel = fs.getPath("pom.xml");
        assertEquals(new File("pom.xml").getAbsolutePath(), rel.toAbsolutePath().toString());
        // Provider operations used to resolve a relative path against "/", not the working directory
        assertTrue(Files.exists(rel));
        assertTrue(Files.isRegularFile(rel));
        assertEquals(new File("pom.xml").length(), Files.size(rel));
        assertTrue(Files.isSameFile(rel, rel.toAbsolutePath()));
        assertTrue(Files.isSameFile(rel, fs.getPath(new File("pom.xml").getAbsolutePath())));
    }
    
    public void testAbsolutePathsAreOsPaths() throws IOException {
        Path file = fs.getPath(dir.getAbsolutePath(), "a.txt");
        Files.write(file, "abc".getBytes());
        assertTrue(new File(dir, "a.txt").exists());
        assertEquals(new File(dir, "a.txt").toPath().toRealPath().toString(), file.toRealPath().toString());
        assertThrows(NoSuchFileException.class, () -> fs.getPath(dir.getAbsolutePath(), "missing").toRealPath());
    }
    
    public void testMoveAndTimes() throws IOException {
        Path a = fs.getPath(dir.getAbsolutePath(), "m.txt");
        Path b = fs.getPath(dir.getAbsolutePath(), "sub", "m2.txt");
        Files.write(a, "m".getBytes());
        Files.createDirectory(b.getParent());
        Files.move(a, b);
        assertFalse(Files.exists(a));
        assertEquals("m", new String(Files.readAllBytes(b)));
        FileTime t = FileTime.fromMillis(1_400_000_000_000L);
        Files.setLastModifiedTime(b, t);
        assertEquals(t, Files.getLastModifiedTime(b));
        // ATOMIC_MOVE is honoured (Files.move), not silently ignored
        Files.move(b, a, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        assertTrue(Files.exists(a));
    }
}
