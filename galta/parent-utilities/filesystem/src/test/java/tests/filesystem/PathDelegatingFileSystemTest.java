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

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.monflabs.filesystem.delegate.PathDelegatingFileSystem;
import org.monflabs.filesystem.memory.MemoryFileSystem;

/**
 * Comprehensive test suite for MemoryFileSystem implementation.
 */
public class PathDelegatingFileSystemTest extends AbstractFileSystemTest {
    
    // Track underlying filesystems for proper cleanup
    private final Map<FileSystem, FileSystem> underlyingFSMap = new ConcurrentHashMap<>();
    
    @Override
    protected FileSystem createFileSystem(int index) throws IOException {
        // Create underlying MemoryFileSystem
        MemoryFileSystem underlyingFS = MemoryFileSystem.newBuilder()
        		.build();
        
        // Create a directory in the underlying filesystem to use as root
        Path delegateRoot = underlyingFS.getPath("/test-root");
        Files.createDirectories(delegateRoot);
        
        // Create PathDelegatingFileSystem that delegates to this path
        PathDelegatingFileSystem delegatingFS = PathDelegatingFileSystem.newBuilder()
        		.root(delegateRoot)
        		.build();
        
        // Track the underlying filesystem for this delegating filesystem
        underlyingFSMap.put(delegatingFS, underlyingFS);
        
        return delegatingFS;
    }
    
    @Override
    protected void cleanupFileSystem(FileSystem fs) throws IOException {
        // Close the delegating filesystem
        if (fs != null && fs.isOpen()) {
            fs.close();
        }
        
        // Close the corresponding underlying filesystem
        FileSystem underlyingFS = underlyingFSMap.remove(fs);
        if (underlyingFS != null && underlyingFS.isOpen()) {
            underlyingFS.close();
        }
    }
    
    @Override
    protected String getFileSystemName() {
        return "PathDelegatingFileSystem (delegating to MemoryFileSystem)";
    }
}
