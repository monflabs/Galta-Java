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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.RandomAccessFile;
import java.util.List;

import org.monflabs.tests.leaks.ResourceTracker;

/**
 * Comprehensive tests for traditional File I/O resource leak detection.
 * Tests FileInputStream, FileOutputStream, RandomAccessFile, FileReader, FileWriter.
 */
public class FileIOLeakTest extends BaseLeakTest {
    
    private File tempFile;
    
    @Override
    public void setUp() throws Exception {
        // Create temp file
        tempFile = File.createTempFile("fileio-test", ".txt");
        tempFile.deleteOnExit();
        
        // Write some initial data
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write("Initial data for testing\n".getBytes());
        }
        
        // We start tracking last
        //ResourceTracker.getInstance().startTracking();
        super.setUp();
    }
    
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        
        if (tempFile != null && tempFile.exists()) {
            tempFile.delete();
        }
    }
    
   // ==================== FileInputStream Tests ====================
    
    public void testFileInputStreamLeak() throws Exception {
        FileInputStream fis = new FileInputStream(tempFile);
        
        // Read some data
        fis.read();
        
        // FORGOT TO CLOSE! This should be detected as a leak
        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
        assertEquals("FileInputStream leak should be detected", 1, leaks.size());
        assertEquals("FileInputStream", leaks.get(0).getAllocationInfo().getResourceType());
        
        // Clean up
        fis.close();
    }
    
    public void testFileInputStreamNoLeak() throws Exception {
        try (FileInputStream fis = new FileInputStream(tempFile)) {
            fis.read();
        }
        
        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
        assertEquals("No leak when properly closed", 0, leaks.size());
    }
    
    // ==================== FileOutputStream Tests ====================
    
    public void testFileOutputStreamLeak() throws Exception {
        FileOutputStream fos = new FileOutputStream(tempFile);
        
        // Write some data
        fos.write("Test data".getBytes());
        
        // FORGOT TO CLOSE! This should be detected as a leak
        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
        assertEquals("FileOutputStream leak should be detected", 1, leaks.size());
        assertTrue("Should show file path", leaks.get(0).formatReport().contains(tempFile.getAbsolutePath()));
        
        // Clean up
        fos.close();
    }
    
    public void testFileOutputStreamNoLeak() throws Exception {
        try (FileOutputStream fos = new FileOutputStream(tempFile, true)) {
            fos.write("Appended data\n".getBytes());
        }
        
        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
        assertEquals("No leak when properly closed", 0, leaks.size());
    }
    
    // ==================== RandomAccessFile Tests ====================
    
    public void testRandomAccessFileLeak() throws Exception {
        RandomAccessFile raf = new RandomAccessFile(tempFile, "r");
        
        // Read some data
        raf.read();
        
        // FORGOT TO CLOSE! This should be detected as a leak
        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
        assertEquals("RandomAccessFile leak should be detected", 1, leaks.size());
        assertEquals("RandomAccessFile", leaks.get(0).getAllocationInfo().getResourceType());
        
        // Clean up
        raf.close();
    }
    
    public void testRandomAccessFileNoLeak() throws Exception {
        try (RandomAccessFile raf = new RandomAccessFile(tempFile, "rw")) {
            raf.seek(0);
            raf.write("Modified".getBytes());
        }
        
        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
        assertEquals("No leak when properly closed", 0, leaks.size());
    }
    
    // ==================== FileReader Tests ====================
    
    public void testFileReaderLeak() throws Exception {
        FileReader fr = new FileReader(tempFile);
        
        // Read some data
        fr.read();
        
        // FORGOT TO CLOSE! This should be detected as a leak
        // We track the internal FileInputStream, not FileReader itself
        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
        assertEquals("FileReader leak should be detected (via internal FileInputStream)", 1, leaks.size());
        assertEquals("FileInputStream", leaks.get(0).getAllocationInfo().getResourceType());
        
        // Clean up
        fr.close();
    }
    
    public void testFileReaderNoLeak() throws Exception {
        try (FileReader fr = new FileReader(tempFile)) {
            char[] buffer = new char[100];
            fr.read(buffer);
        }
        
        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
        assertEquals("No leak when properly closed", 0, leaks.size());
    }
    
    // ==================== FileWriter Tests ====================
    
    public void testFileWriterLeak() throws Exception {
        FileWriter fw = new FileWriter(tempFile, true);
        // Write some data
        fw.write("Test data from FileWriter\n");
        
        // FORGOT TO CLOSE! This should be detected as a leak
        // We track the internal FileOutputStream, not FileWriter itself
        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
        
        assertEquals("FileWriter leak should be detected (via internal FileOutputStream)", 1, leaks.size());
        assertEquals("FileOutputStream", leaks.get(0).getAllocationInfo().getResourceType());
        
        // Clean up
        fw.close();
    }
    
    public void testFileWriterNoLeak() throws Exception {
        try (FileWriter fw = new FileWriter(tempFile, true)) {
            fw.write("Hello");
        }
        
        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
        assertEquals("No leak when properly closed", 0, leaks.size());
    }
    
    // ==================== Multiple Resources Test ====================
    
    public void testMultipleFileResourceLeaks() throws Exception {
        // Create multiple leaks
        FileInputStream fis = new FileInputStream(tempFile);
        FileOutputStream fos = new FileOutputStream(tempFile);
        RandomAccessFile raf = new RandomAccessFile(tempFile, "r");
        
        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
        assertEquals("Should detect all 3 leaks", 3, leaks.size());
        
        // Clean up
        fis.close();
        fos.close();
        raf.close();
    }
    
    public void testMixedProperAndLeakedResources() throws Exception {
        
        // Properly closed
        try (FileInputStream fis = new FileInputStream(tempFile)) {
            fis.read();
        }
        
        // Leaked
        FileOutputStream fos = new FileOutputStream(tempFile);
        fos.write("Leaked data".getBytes());
        
        // Properly closed
        try (RandomAccessFile raf = new RandomAccessFile(tempFile, "r")) {
            raf.read();
        }
        
        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
        assertEquals("Should detect only the FileOutputStream leak", 1, leaks.size());
        assertEquals("FileOutputStream", leaks.get(0).getAllocationInfo().getResourceType());
        
        // Clean up
        fos.close();
    }
    
    // ==================== Constructor Variants Test ====================
    
    public void testFileInputStreamConstructorVariants() throws Exception {
        // Constructor with String path
        FileInputStream fis1 = new FileInputStream(tempFile.getAbsolutePath());
        
        // Constructor with File object
        FileInputStream fis2 = new FileInputStream(tempFile);
        
        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
        assertEquals("Should detect both constructor variants", 2, leaks.size());
        
        for (ResourceTracker.LeakInfo leak : leaks) {
            // Both should show the file path
            assertTrue("Should contain file path", leak.formatReport().contains(tempFile.getAbsolutePath()));
        }
        
        // Clean up
        fis1.close();
        fis2.close();
    }
}
