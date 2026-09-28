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
package tests.tests.leaks;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.monflabs.tests.leaks.ResourceTracker;

/**
 * Demonstrates that Files API (Java 7+) leak detection works.
 * Before fix: Files.newInputStream/newOutputStream were NOT tracked.
 * After fix: They ARE tracked via FileChannel instrumentation.
 */
public class FileSystemAPILeakTest extends BaseLeakTest {
    
    private File tempFile;
    
    @Override
	@Before
    public void setUp() throws Exception {
        // Create temp file
        tempFile = File.createTempFile("files-api-test", ".txt");
        tempFile.deleteOnExit();
        
        // Write some data
        Files.write(tempFile.toPath(), "Hello, World!".getBytes());

        // We start tracking last
        //ResourceTracker.getInstance().startTracking();
        super.setUp();
    }
    
    @Override
	@After
    public void tearDown() throws Exception {
        // We stop tracking first
        super.tearDown();
        ResourceTracker.getInstance().stopTracking();
        if (tempFile != null && tempFile.exists()) {
            tempFile.delete();
        }
    }
    
    @Test
    public void testFilesNewInputStreamLeak() throws Exception {
        // Create leak using Files API
        InputStream is = Files.newInputStream(tempFile.toPath());
        
        // Read some data
        is.read();
        
        // FORGOT TO CLOSE! This should be detected as a leak
        
        // Check for leaks
        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
        assertEquals("Files.newInputStream leak should be detected", 1, leaks.size());
        
        // Clean up to avoid interference with other tests
        is.close();
    }
    
    @Test
    public void testFilesNewOutputStreamLeak() throws Exception {
        // Create leak using Files API
        OutputStream os = Files.newOutputStream(tempFile.toPath());
        
        // Write some data
        os.write("Test".getBytes());
        
        // FORGOT TO CLOSE! This should be detected as a leak
        
        // Check for leaks
        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
        assertEquals("Files.newOutputStream leak should be detected", 1, leaks.size());
        
        // Clean up
        os.close();
    }
    
    @Test
    public void testFilesNewInputStreamNoLeak() throws Exception {
        // Properly close the stream
        try (InputStream is = Files.newInputStream(tempFile.toPath())) {
            // Read some data
            is.read();
        }
        
        // Check for leaks
        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
        assertEquals("No leak should be detected when properly closed", 0, leaks.size());
    }
    
    @Test
    public void testFilesBufferedReaderLeak() throws Exception {
        // Create leak using Files.newBufferedReader
        BufferedReader reader = Files.newBufferedReader(tempFile.toPath());
        
        // Read some data
        String line = reader.readLine();
        logIgnore(line);
        
        // FORGOT TO CLOSE! This should be detected as a leak
        // Check for leaks
        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
        assertEquals("Files.newBufferedReader leak should be detected", 1, leaks.size());
        
        // Clean up
        reader.close();
    }
    
    @Test
    public void testFilesBufferedWriterLeak() throws Exception {
        // Create leak using Files.newBufferedWriter
        BufferedWriter writer = Files.newBufferedWriter(tempFile.toPath());
        
        // Write some data
        writer.write("Test line\n");
        
        // FORGOT TO CLOSE! This should be detected as a leak
        
        // Check for leaks
        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
        assertEquals("Files.newBufferedWriter leak should be detected", 1, leaks.size());
        
        // Clean up
        writer.close();
    }
    
    @Test
    public void testComparisonOldVsNewAPI() throws Exception {
        // Old API
        FileInputStream fis = new FileInputStream(tempFile);
        
        // New API
        InputStream nis = Files.newInputStream(tempFile.toPath());
        
        // Both should be tracked
        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
        assertEquals("Both old and new API should be tracked", 2, leaks.size());
        
        // Clean up
        fis.close();
        nis.close();
    }
}
