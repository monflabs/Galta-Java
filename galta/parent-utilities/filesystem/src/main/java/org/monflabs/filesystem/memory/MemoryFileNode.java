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
package org.monflabs.filesystem.memory;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a file or directory node in the in-memory filesystem.
 */
public class MemoryFileNode {
    
    private final String name;
    private final boolean directory;
    // Largest array the JVM reliably allocates
    private static final int MAX_SIZE = Integer.MAX_VALUE - 8;
    
    private byte[] content;
    private int size;
    private final Map<String, MemoryFileNode> children;
    private volatile FileTime creationTime;
    private volatile FileTime lastModifiedTime;
    private volatile FileTime lastAccessTime;
    private boolean hidden;
    private boolean readable = true;
    private boolean writable = true;
    private boolean executable = false;
    private Map<String, Object> properties;
    
    public MemoryFileNode(String name, boolean directory) {
        this.name = name;
        this.directory = directory;
        this.content = directory ? null : new byte[0];
        this.children = directory ? new ConcurrentHashMap<>() : null;
        long now = System.currentTimeMillis();
        this.creationTime = FileTime.fromMillis(now);
        this.lastModifiedTime = FileTime.fromMillis(now);
        this.lastAccessTime = FileTime.fromMillis(now);
    }
    
    public String getName() {
        return name;
    }
    
    public boolean isDirectory() {
        return directory;
    }
    
    public boolean isFile() {
        return !directory;
    }
    
    /**
     * A copy of the file content.
     */
    public synchronized byte[] getContent() {
        if (directory) {
            throw new IllegalStateException("Cannot get content of directory");
        }
        updateAccessTime();
        return Arrays.copyOf(content, size);
    }
    
    public synchronized void setContent(byte[] content) {
        if (directory) {
            throw new IllegalStateException("Cannot set content of directory");
        }
        this.content = content;
        this.size = content.length;
        updateModifiedTime();
    }
    
    /**
     * Read from the given position into the buffer.
     * @return the number of bytes read, or -1 at the end of the file
     */
    synchronized int read(long position, ByteBuffer dst) {
        updateAccessTime();
        if (position >= size) {
            return -1;
        }
        int length = (int) Math.min(dst.remaining(), size - position);
        dst.put(content, (int) position, length);
        return length;
    }
    
    /**
     * Write the buffer at the given position (or at the end when appending), growing the file
     * as needed. The capacity grows geometrically, so a file written chunk by chunk is not
     * copied whole on every write, and writers of the same file do not lose each other's bytes.
     * @return the position after the written bytes
     */
    synchronized long write(long position, ByteBuffer src, boolean append) throws IOException {
        if (append) {
            position = size;
        }
        int length = src.remaining();
        long end = position + length;
        if (end > MAX_SIZE) {
            throw new IOException("File too large for an in-memory file: " + end + " bytes");
        }
        if (end > content.length) {
            int capacity = (int) Math.min(MAX_SIZE, Math.max(end, content.length * 2L));
            content = Arrays.copyOf(content, capacity);
        }
        if (position > size) {
            // Writing past the end leaves a zero-filled gap
            Arrays.fill(content, size, (int) position, (byte) 0);
        }
        src.get(content, (int) position, length);
        size = Math.max(size, (int) end);
        updateModifiedTime();
        return end;
    }
    
    synchronized void truncate(long newSize) {
        if (newSize < size) {
            Arrays.fill(content, (int) newSize, size, (byte) 0);
            size = (int) newSize;
            updateModifiedTime();
        }
    }
    
    public synchronized Object getProperty(String key) {
    	return getPropertyOrDefault(key,null);
    }
    public synchronized Object getPropertyOrDefault(String key, Object defValue) {
    	if(properties!=null) {
    		if(properties.containsKey(key)) {
    			return properties.get(key);
    		}
    	}
    	return defValue;
    }
    public synchronized void putProperty(String key, Object value) {
    	if(properties==null) {
    		properties = new HashMap<>();
    	}
    	properties.put(key,value);
    }
    
    public synchronized long getSize() {
        return directory ? 0 : size;
    }
    
    public Map<String, MemoryFileNode> getChildren() {
        if (!directory) {
            throw new IllegalStateException("Cannot get children of file");
        }
        return children;
    }
    
    public MemoryFileNode getChild(String name) {
        if (!directory) {
            return null;
        }
        return children.get(name);
    }
    
    public void addChild(String name, MemoryFileNode child) {
        if (!directory) {
            throw new IllegalStateException("Cannot add child to file");
        }
        children.put(name, child);
        updateModifiedTime();
    }
    
    public MemoryFileNode removeChild(String name) {
        if (!directory) {
            throw new IllegalStateException("Cannot remove child from file");
        }
        MemoryFileNode removed = children.remove(name);
        if (removed != null) {
            updateModifiedTime();
        }
        return removed;
    }
    
    public boolean hasChild(String name) {
        return directory && children.containsKey(name);
    }
    
    public FileTime getCreationTime() {
        return creationTime;
    }
    
    public FileTime getLastModifiedTime() {
        return lastModifiedTime;
    }
    
    public FileTime getLastAccessTime() {
        return lastAccessTime;
    }
    
    public void updateAccessTime() {
        this.lastAccessTime = FileTime.fromMillis(System.currentTimeMillis());
    }
    
    public void updateModifiedTime() {
        long now = System.currentTimeMillis();
        this.lastModifiedTime = FileTime.fromMillis(now);
        this.lastAccessTime = FileTime.fromMillis(now);
    }
    
    /**
     * Set the times of this node; a null time is left unchanged.
     */
    public synchronized void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime creationTime) {
        if (lastModifiedTime != null) {
            this.lastModifiedTime = lastModifiedTime;
        }
        if (lastAccessTime != null) {
            this.lastAccessTime = lastAccessTime;
        }
        if (creationTime != null) {
            this.creationTime = creationTime;
        }
    }
    
    public boolean isHidden() {
        return hidden;
    }
    
    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }
    
    public boolean isReadable() {
        return readable;
    }
    
    public void setReadable(boolean readable) {
        this.readable = readable;
    }
    
    public boolean isWritable() {
        return writable;
    }
    
    public void setWritable(boolean writable) {
        this.writable = writable;
    }
    
    public boolean isExecutable() {
        return executable;
    }
    
    public void setExecutable(boolean executable) {
        this.executable = executable;
    }
}
