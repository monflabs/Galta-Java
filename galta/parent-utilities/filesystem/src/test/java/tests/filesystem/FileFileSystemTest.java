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

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;

import org.monflabs.filesystem.file.FileFileSystem;
import org.monflabs.util.FileUtil;

/**
 * Comprehensive test suite for File FileSystem implementation.
 */
public class FileFileSystemTest extends AbstractFileSystemTest {
    
    private File tempFileFolder;
    
    @Override
    protected FileSystem createFileSystem(int index) throws IOException {
        // Create a unique temporary directory for each test
        tempFileFolder = new File(support.getTargetTempDirectory(), "FileFileSystemTest-"+ index );
        FileUtil.deleteFile(tempFileFolder);
        tempFileFolder.mkdirs();

        Path tempDirectory = tempFileFolder.toPath();
        FileSystem fs = FileFileSystem.newBuilder().root(tempDirectory.toFile()).build();
        
        return fs;
    }
    
    @Override
    protected void cleanupFileSystem(FileSystem fs) throws IOException {
        if (fs != null && fs.isOpen()) {
            fs.close();
        }
        
        // Delete the temporary directory and all its contents
        // Delete temporary folder
        if (tempFileFolder != null && tempFileFolder.exists()) {
            FileUtil.deleteFile(tempFileFolder);
        }
    }
    
    @Override
    protected String getFileSystemName() {
    	return "FileFileSystem (sandboxed)";
    }


    public void testIsSameFileFollowsLinks() throws IOException {
        java.nio.file.Path target = tempFileFolder.toPath().resolve("target.txt");
        java.nio.file.Files.writeString(target, "x");
        try {
            java.nio.file.Files.createSymbolicLink(tempFileFolder.toPath().resolve("link.txt"), target);
        } catch (UnsupportedOperationException | IOException e) {
            return; // No symbolic links on this platform
        }
        // A lexical comparison said false
        assertTrue(java.nio.file.Files.isSameFile(fs.getPath("/link.txt"), fs.getPath("/target.txt")));
        assertFalse(java.nio.file.Files.isSameFile(fs.getPath("/link.txt"), fs.getPath("/")));
    }

    public void testFileStoreIsTheStoreOfTheFile() throws IOException {
        java.nio.file.Files.writeString(fs.getPath("/store.txt"), "x");
        java.nio.file.FileStore store = java.nio.file.Files.getFileStore(fs.getPath("/store.txt"));
        java.nio.file.FileStore expected = java.nio.file.Files.getFileStore(tempFileFolder.toPath());
        // It used to be the store of "/" (read-only and with the wrong sizes on another volume)
        assertEquals(expected.name(), store.name());
        assertEquals(expected.isReadOnly(), store.isReadOnly());
        assertEquals(expected.getTotalSpace(), store.getTotalSpace());
        org.junit.Assert.assertThrows(java.nio.file.NoSuchFileException.class, () -> java.nio.file.Files.getFileStore(fs.getPath("/missing.txt")));
    }
}
