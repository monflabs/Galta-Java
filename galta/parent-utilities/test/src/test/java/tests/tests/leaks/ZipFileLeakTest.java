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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.monflabs.tests.leaks.ResourceTracker;

/**
 * Comprehensive tests for ZipFile resource leak detection.
 */
public class ZipFileLeakTest extends BaseLeakTest {
    
    private File tempZipFile;
    
    @Override
    public void setUp() throws Exception {
        // Create a temporary zip file with some content
        tempZipFile = File.createTempFile("test-archive", ".zip");
        tempZipFile.deleteOnExit();
        
        createTestZipFile(tempZipFile);
        
        super.setUp();
    }
    
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        
        if (tempZipFile != null && tempZipFile.exists()) {
            tempZipFile.delete();
        }
    }
    
    /**
     * Helper: Create a test ZIP file with some entries
     */
    private void createTestZipFile(File zipFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            // Add first entry
            ZipEntry entry1 = new ZipEntry("file1.txt");
            zos.putNextEntry(entry1);
            zos.write("Content of file 1\n".getBytes());
            zos.closeEntry();
            
            // Add second entry
            ZipEntry entry2 = new ZipEntry("folder/file2.txt");
            zos.putNextEntry(entry2);
            zos.write("Content of file 2 in folder\n".getBytes());
            zos.closeEntry();
            
            // Add third entry
            ZipEntry entry3 = new ZipEntry("data.bin");
            zos.putNextEntry(entry3);
            zos.write(new byte[]{0x00, 0x01, 0x02, 0x03, 0x04});
            zos.closeEntry();
        }
    }
    
    // ==================== ZipFile Tests ====================
    
    public void testZipFileLeak() throws Exception {
        ZipFile zipFile = new ZipFile(tempZipFile);
        
        // Read an entry
        ZipEntry entry = zipFile.getEntry("file1.txt");
        InputStream is = zipFile.getInputStream(entry);
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        logIgnore(reader.readLine());
        reader.close();
        
        // FORGOT TO CLOSE ZIPFILE! This should be detected as a leak
        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
        assertEquals("ZipFile leak should be detected", 2, leaks.size()); // ZipFile + RandomFile
        
        // Clean up
        zipFile.close();
    }
    
    public void testZipFileNoLeak() throws Exception {
        try (ZipFile zipFile = new ZipFile(tempZipFile)) {
            ZipEntry entry = zipFile.getEntry("file1.txt");
            try (InputStream is = zipFile.getInputStream(entry);
                BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                logIgnore(reader.readLine());
            }
        }
        
        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
        assertEquals("No leak when properly closed", 0, leaks.size());
    }
    
    public void testZipFileReadAllEntries() throws Exception {
        ZipFile zipFile = new ZipFile(tempZipFile);
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (!entry.isDirectory()) {
                try (InputStream is = zipFile.getInputStream(entry)) {
                    byte[] buffer = new byte[(int)entry.getSize()];
                    is.read(buffer);
                }
            }
        }
        
        // FORGOT TO CLOSE! This should be detected as a leak
        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
        assertEquals("ZipFile leak should be detected", 2, leaks.size()); // ZipFile + RandomFile
        
        // Clean up
        zipFile.close();
    }
    
    public void testZipFileConstructorVariants() throws Exception {
        // Constructor with File
        ZipFile zipFile1 = new ZipFile(tempZipFile);
        
        // Constructor with String path
        ZipFile zipFile2 = new ZipFile(tempZipFile.getAbsolutePath());
        
        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
        assertEquals("Should detect both ZipFile leaks", 3, leaks.size()); // 2 ZipFile+1 RandomFile (The file is shared)
        
        // Clean up
        zipFile1.close();
        zipFile2.close();
    }
    
    // ==================== ZipInputStream Tests ====================
    
    public void testZipInputStreamLeak() throws Exception {
        FileInputStream fis = new FileInputStream(tempZipFile);
        ZipInputStream zis = new ZipInputStream(fis);

        // Read entries
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
        	logIgnore(entry);
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = zis.read(buffer)) != -1) {
                // Process data
            	logIgnore(bytesRead);
            }
            zis.closeEntry();
        }
        
        // FORGOT TO CLOSE! This should be detected as a leak
        // Note: ZipInputStream wraps FileInputStream, so we might see leaks for both
        
        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
        assertTrue("Should detect leak (FileInputStream at minimum)", leaks.size() >= 1);
        
        // Clean up
        zis.close();
        fis.close();
    }
    
    public void testZipInputStreamNoLeak() throws Exception {
        try (FileInputStream fis = new FileInputStream(tempZipFile);
            ZipInputStream zis = new ZipInputStream(fis)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
            	logIgnore(entry);
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = zis.read(buffer)) != -1) {
                    // Process data
                	logIgnore(bytesRead);
                }
                zis.closeEntry();
            }
        }
        
        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
        assertEquals("No leak when properly closed", 0, leaks.size());
    }
    
    // ==================== ZipOutputStream Tests ====================
    
    public void testZipOutputStreamLeak() throws Exception {
        File outputZip = File.createTempFile("output-test", ".zip");
        outputZip.deleteOnExit();
        
        FileOutputStream fos = new FileOutputStream(outputZip);
        ZipOutputStream zos = new ZipOutputStream(fos);
        
        // Write an entry
        ZipEntry entry = new ZipEntry("test.txt");
        zos.putNextEntry(entry);
        zos.write("Test content".getBytes());
        zos.closeEntry();
        
        // FORGOT TO CLOSE! This should be detected as a leak
        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
        assertTrue("Should detect leak (FileOutputStream at minimum)", leaks.size() >= 1);
        
        // Clean up
        zos.close();
        fos.close();
        outputZip.delete();
    }
    
    public void testZipOutputStreamNoLeak() throws Exception {
        File outputZip = File.createTempFile("output-test", ".zip");
        outputZip.deleteOnExit();
        
        try (FileOutputStream fos = new FileOutputStream(outputZip);
            ZipOutputStream zos = new ZipOutputStream(fos)) {
            
            ZipEntry entry = new ZipEntry("test.txt");
            zos.putNextEntry(entry);
            zos.write("Test content".getBytes());
            zos.closeEntry();
        }
        
        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
        assertEquals("No leak when properly closed", 0, leaks.size());
        
        outputZip.delete();
    }
    
    // ==================== GZIPInputStream Tests ====================
    
    public void testGZIPInputStreamLeak() throws Exception {
        // Create a GZIP file
        File gzipFile = File.createTempFile("test", ".gz");
        gzipFile.deleteOnExit();
        try (GZIPOutputStream gzos = new GZIPOutputStream(new FileOutputStream(gzipFile))) {
            gzos.write("Test GZIP content".getBytes());
        }
        
        // Now read it without closing
        FileInputStream fis = new FileInputStream(gzipFile);
        GZIPInputStream gzis = new GZIPInputStream(fis);
        
        byte[] buffer = new byte[1024];
        int bytesRead = gzis.read(buffer);
        logIgnore(bytesRead);
        
        // FORGOT TO CLOSE!
        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
        assertTrue("Should detect leak (FileInputStream at minimum)", leaks.size() >= 1);
        
        // Clean up
        gzis.close();
        fis.close();
        gzipFile.delete();
    }
    
    public void testGZIPInputStreamNoLeak() throws Exception {
        // Create a GZIP file
        File gzipFile = File.createTempFile("test", ".gz");
        gzipFile.deleteOnExit();
        
        try (GZIPOutputStream gzos = new GZIPOutputStream(new FileOutputStream(gzipFile))) {
            gzos.write("Test GZIP content".getBytes());
        }
        
        // Read with proper closure
        try (FileInputStream fis = new FileInputStream(gzipFile);
            GZIPInputStream gzis = new GZIPInputStream(fis)) {
            
            byte[] buffer = new byte[1024];
            int bytesRead = gzis.read(buffer);
            logIgnore(bytesRead);
        }
        
        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
        assertEquals("No leak when properly closed", 0, leaks.size());
        
        gzipFile.delete();
    }
}
