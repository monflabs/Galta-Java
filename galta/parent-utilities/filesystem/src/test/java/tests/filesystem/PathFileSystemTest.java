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

import org.monflabs.filesystem.path.PathFileSystem;
import org.monflabs.util.FileUtil;

/**
 * Comprehensive test suite for Path FileSystem implementation.
 */
public class PathFileSystemTest extends AbstractFileSystemTest {
    
    private File tempFileFolder;
    
    @Override
    protected FileSystem createFileSystem(int index) throws IOException {
        // Create a unique temporary directory for each test
        tempFileFolder = new File(support.getTargetTempDirectory(), "PathFileSystemTest-"+ index );
        FileUtil.deleteFile(tempFileFolder);
        tempFileFolder.mkdirs();

        Path tempDirectory = tempFileFolder.toPath();
        FileSystem fs = PathFileSystem.newBuilder().root(tempDirectory).build();
        
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
    	return "PathFileSystem (sandboxed)";
    }


    public void testToUriDoesNotDiscloseTheHostPath() throws IOException {
        java.net.URI uri = fs.getPath("/dir/a b.txt").toUri();
        assertEquals(java.net.URI.create("pathfs:///dir/a%20b.txt"), uri);
        assertFalse(uri.toString(), uri.toString().contains(tempFileFolder.getName()));
        assertEquals("/x", fs.getPath("/../x").toUri().getPath());
    }
}
