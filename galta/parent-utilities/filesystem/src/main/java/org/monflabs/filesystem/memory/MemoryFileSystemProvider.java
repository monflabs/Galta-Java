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
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.monflabs.filesystem.AbstractFileSystem;
import org.monflabs.filesystem.AbstractFileSystemProvider;
import org.monflabs.filesystem.AbstractPath;

/**
 * FileSystemProvider for in-memory filesystem.
 */
public class MemoryFileSystemProvider extends AbstractFileSystemProvider {
		
    public static final String SCHEME = "memory";
    
    public MemoryFileSystemProvider(boolean registered) {
    	super(registered);
    }
    
    @Override
    public String getScheme() {
        return SCHEME;
    }
    
    @Override
    protected AbstractFileSystem createFileSystem(URI uri, Map<String, ?> env) throws IOException {
        return new MemoryFileSystem(this, uri);
    }
    
    @Override
    protected AbstractPath createPath(FileSystem fs, String path) {
        return new MemoryPath((MemoryFileSystem) fs, path);
    }
    
    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options,
                                               FileAttribute<?>... attrs) throws IOException {
        checkPath(path);
        path = toAbsolutePath(path);
        MemoryFileSystem fs = (MemoryFileSystem) path.getFileSystem();
        MemoryPath memPath = (MemoryPath) path.normalize();
        
        boolean read = options.contains(StandardOpenOption.READ) || 
                       (!options.contains(StandardOpenOption.WRITE) && 
                        !options.contains(StandardOpenOption.APPEND));
        boolean write = options.contains(StandardOpenOption.WRITE) || 
                        options.contains(StandardOpenOption.APPEND);
        boolean append = options.contains(StandardOpenOption.APPEND);
        boolean truncate = options.contains(StandardOpenOption.TRUNCATE_EXISTING);
        boolean create = options.contains(StandardOpenOption.CREATE);
        boolean createNew = options.contains(StandardOpenOption.CREATE_NEW);
        
        MemoryFileNode node = fs.getNode(memPath);
        
        if (node == null) {
            if (!create && !createNew) {
                throw new NoSuchFileException(path.toString());
            }
            // Create new file
            MemoryPath parent = (MemoryPath) memPath.getParent();
            if (parent != null) {
                MemoryFileNode parentNode = fs.getNode(parent);
                if (parentNode == null) {
                    throw new NoSuchFileException(parent.toString());
                }
                if (!parentNode.isDirectory()) {
                    throw new NotDirectoryException(parent.toString());
                }
            }
            node = new MemoryFileNode(memPath.getFileName().toString(), false);
            fs.createNode(memPath, node);
        } else {
            if (createNew) {
                throw new FileAlreadyExistsException(path.toString());
            }
            if (node.isDirectory()) {
                throw new FileSystemException(path.toString(), null, "Is a directory");
            }
        }
        
        // The node's permissions apply to channels too, not only to checkAccess()
        if (read && !node.isReadable()) {
            throw new AccessDeniedException(path.toString());
        }
        if (write && !node.isWritable()) {
            throw new AccessDeniedException(path.toString());
        }
        
        if (truncate && write) {
            node.setContent(new byte[0]);
        }
        
        return new MemoryByteChannel(node, read, write, append);
    }
    
    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter)
            throws IOException {
        checkPath(dir);
        dir = toAbsolutePath(dir);
        MemoryFileSystem fs = (MemoryFileSystem) dir.getFileSystem();
        MemoryFileNode node = fs.getNode((MemoryPath) dir);
        
        if (node == null) {
            throw new NoSuchFileException(dir.toString());
        }
        if (!node.isDirectory()) {
            throw new NotDirectoryException(dir.toString());
        }
        
        List<Path> paths = new ArrayList<>();
        for (Map.Entry<String, MemoryFileNode> entry : node.getChildren().entrySet()) {
            Path childPath = dir.resolve(entry.getKey());
            // An IOException from the filter is the caller's error to see, not a reason to skip
            if (filter.accept(childPath)) {
                paths.add(childPath);
            }
        }
        
        return new DirectoryStream<Path>() {
            private boolean closed = false;
            
            @Override
            public Iterator<Path> iterator() {
                if (closed) {
                    throw new IllegalStateException("DirectoryStream is closed");
                }
                return paths.iterator();
            }
            
            @Override
            public void close() {
                closed = true;
            }
        };
    }
    
    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        checkPath(dir);
        dir = toAbsolutePath(dir);
        MemoryFileSystem fs = (MemoryFileSystem) dir.getFileSystem();
        MemoryPath memPath = (MemoryPath) dir.normalize();
        
        if (fs.getNode(memPath) != null) {
            throw new FileAlreadyExistsException(dir.toString());
        }
        
        MemoryPath parent = (MemoryPath) memPath.getParent();
        if (parent != null) {
            MemoryFileNode parentNode = fs.getNode(parent);
            if (parentNode == null) {
                throw new NoSuchFileException(parent.toString());
            }
            if (!parentNode.isDirectory()) {
                throw new NotDirectoryException(parent.toString());
            }
        }
        
        MemoryFileNode node = new MemoryFileNode(memPath.getFileName().toString(), true);
        fs.createNode(memPath, node);
    }
    
    @Override
    public void delete(Path path) throws IOException {
        checkPath(path);
        path = toAbsolutePath(path);
        MemoryFileSystem fs = (MemoryFileSystem) path.getFileSystem();
        MemoryPath memPath = (MemoryPath) path.normalize();
        
        MemoryFileNode node = fs.getNode(memPath);
        if (node == null) {
            throw new NoSuchFileException(path.toString());
        }
        
        if (node.isDirectory() && !node.getChildren().isEmpty()) {
            throw new DirectoryNotEmptyException(path.toString());
        }
        if (memPath.getParent() == null) {
            // Deleting "/" dropped the root from the index and left the filesystem unusable
            throw new FileSystemException(path.toString(), null, "Cannot delete the root directory");
        }
        
        fs.deleteNode(memPath);
    }
    
    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        transfer(source, target, false, options);
    }
    
    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        transfer(source, target, true, options);
    }
    
    /**
     * Copy or move, following the Files.copy()/Files.move() contract: a directory copy is
     * shallow (only the directory is created), a move takes the whole subtree, and
     * REPLACE_EXISTING never replaces a non-empty directory.
     */
    private void transfer(Path source, Path target, boolean move, CopyOption... options) throws IOException {
        checkPath(source);
        checkPath(target);
        // Normalized: "/d/../a.txt" is the same file as "/a.txt"
        MemoryPath srcPath = (MemoryPath) toAbsolutePath(source).normalize();
        MemoryPath tgtPath = (MemoryPath) toAbsolutePath(target).normalize();
        MemoryFileSystem fs = (MemoryFileSystem) srcPath.getFileSystem();
        // The target may live in another memory filesystem
        MemoryFileSystem tgtFs = (MemoryFileSystem) tgtPath.getFileSystem();
        
        MemoryFileNode srcNode = fs.getNode(srcPath);
        if (srcNode == null) {
            throw new NoSuchFileException(source.toString());
        }
        
        boolean replaceExisting = false;
        boolean copyAttributes = move;
        for (CopyOption option : options) {
            if (option == StandardCopyOption.REPLACE_EXISTING) {
                replaceExisting = true;
            } else if (option == StandardCopyOption.COPY_ATTRIBUTES) {
                copyAttributes = true;
            }
        }
        
        MemoryFileNode tgtNode = tgtFs.getNode(tgtPath);
        if (tgtNode == srcNode && fs == tgtFs) {
            return; // Copying or moving a file onto itself is a no-op
        }
        if (move && fs == tgtFs && srcNode.isDirectory() && tgtPath.startsWith(srcPath)) {
            // Moving a directory into its own subtree would delete everything
            throw new FileSystemException(source.toString(), target.toString(), "Cannot move a directory into itself");
        }
        if (tgtNode != null) {
            if (!replaceExisting) {
                throw new FileAlreadyExistsException(target.toString());
            }
            if (tgtNode.isDirectory() && !tgtNode.getChildren().isEmpty()) {
                throw new DirectoryNotEmptyException(target.toString());
            }
        }
        
        MemoryPath tgtParent = (MemoryPath) tgtPath.getParent();
        if (tgtParent == null) {
            throw new FileSystemException(target.toString(), null, "Cannot replace the root directory");
        }
        MemoryFileNode parentNode = tgtFs.getNode(tgtParent);
        if (parentNode == null) {
            throw new NoSuchFileException(tgtParent.toString());
        }
        if (!parentNode.isDirectory()) {
            throw new NotDirectoryException(tgtParent.toString());
        }
        
        if (tgtNode != null) {
            tgtFs.deleteNode(tgtPath);
        }
        
        // The file name is the target's
        MemoryFileNode copy = copyNode(srcNode, tgtPath.getFileName().toString(), move, copyAttributes);
        tgtFs.createNode(tgtPath, copy);
        if (move) {
            fs.deleteNode(srcPath);
        }
    }
    
    @Override
    public boolean isHidden(Path path) throws IOException {
        checkPath(path);
        path = toAbsolutePath(path);
        MemoryFileSystem fs = (MemoryFileSystem) path.getFileSystem();
        MemoryFileNode node = fs.getNode((MemoryPath) path);
        return node != null && node.isHidden();
    }
    
    @Override
    public FileStore getFileStore(Path path) throws IOException {
        checkPath(path);
        return new MemoryFileStore((MemoryFileSystem) path.getFileSystem());
    }
    
    @Override
    public void checkAccess(Path path, AccessMode... modes) throws IOException {
        checkPath(path);
        path = toAbsolutePath(path);
        MemoryFileSystem fs = (MemoryFileSystem) path.getFileSystem();
        MemoryFileNode node = fs.getNode((MemoryPath) path);
        
        if (node == null) {
            throw new NoSuchFileException(path.toString());
        }
        
        for (AccessMode mode : modes) {
            switch (mode) {
                case READ:
                    if (!node.isReadable()) {
                        throw new AccessDeniedException(path.toString());
                    }
                    break;
                case WRITE:
                    if (!node.isWritable()) {
                        throw new AccessDeniedException(path.toString());
                    }
                    break;
                case EXECUTE:
                    if (!node.isExecutable()) {
                        throw new AccessDeniedException(path.toString());
                    }
                    break;
            }
        }
    }
    
    @Override
    public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type,
                                                             LinkOption... options) throws IOException {
        checkPath(path);
        path = toAbsolutePath(path);
        if (type == BasicFileAttributes.class) {
            MemoryFileSystem fs = (MemoryFileSystem) path.getFileSystem();
            MemoryFileNode node = fs.getNode((MemoryPath) path);
            if (node == null) {
                throw new NoSuchFileException(path.toString());
            }
            return type.cast(new MemoryFileAttributes(node));
        }
        throw new UnsupportedOperationException("Attribute type not supported: " + type);
    }
    
    private MemoryFileNode copyNode(MemoryFileNode source, String name, boolean deep, boolean copyAttributes) {
        MemoryFileNode copy = new MemoryFileNode(name, source.isDirectory());
        if (source.isFile()) {
            copy.setContent(source.getContent());
        } else if (deep) {
            for (Map.Entry<String, MemoryFileNode> entry : source.getChildren().entrySet()) {
                copy.addChild(entry.getKey(), copyNode(entry.getValue(), entry.getKey(), true, true));
            }
        }
        copy.setHidden(source.isHidden());
        copy.setReadable(source.isReadable());
        copy.setWritable(source.isWritable());
        copy.setExecutable(source.isExecutable());
        if (copyAttributes) {
            copy.setTimes(source.getLastModifiedTime(), source.getLastAccessTime(), source.getCreationTime());
        }
        return copy;
    }
    
    @Override
    protected void setTimes(Path path, FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) throws IOException {
        checkPath(path);
        path = toAbsolutePath(path);
        MemoryFileNode node = ((MemoryFileSystem) path.getFileSystem()).getNode((MemoryPath) path);
        if (node == null) {
            throw new NoSuchFileException(path.toString());
        }
        node.setTimes(lastModifiedTime, lastAccessTime, createTime);
    }
    
    // Inner class for SeekableByteChannel
    private static class MemoryByteChannel implements SeekableByteChannel {
        private final MemoryFileNode node;
        private final boolean read;
        private final boolean write;
        private final boolean append;
        private long position;
        private volatile boolean open = true;
        
        public MemoryByteChannel(MemoryFileNode node, boolean read, boolean write, boolean append) {
            this.node = node;
            this.read = read;
            this.write = write;
            this.append = append;
            this.position = append ? node.getSize() : 0;
        }
        
        @Override
        public synchronized int read(ByteBuffer dst) throws IOException {
            checkOpen();
            if (!read) {
                throw new NonReadableChannelException();
            }
            int length = node.read(position, dst);
            if (length > 0) {
                position += length;
            }
            return length;
        }
        
        @Override
        public synchronized int write(ByteBuffer src) throws IOException {
            checkOpen();
            if (!write) {
                throw new NonWritableChannelException();
            }
            int length = src.remaining();
            // In append mode, the node writes at its current end
            position = node.write(position, src, append);
            return length;
        }
        
        @Override
        public synchronized long position() throws IOException {
            checkOpen();
            return position;
        }
        
        @Override
        public synchronized SeekableByteChannel position(long newPosition) throws IOException {
            checkOpen();
            if (newPosition < 0) {
                throw new IllegalArgumentException("Negative position");
            }
            // A long: casting to int wrapped positions beyond 2GB
            this.position = newPosition;
            return this;
        }
        
        @Override
        public long size() throws IOException {
            checkOpen();
            return node.getSize();
        }
        
        @Override
        public synchronized SeekableByteChannel truncate(long size) throws IOException {
            checkOpen();
            if (size < 0) {
                throw new IllegalArgumentException("Negative size");
            }
            if (!write) {
                throw new NonWritableChannelException();
            }
            node.truncate(size);
            if (position > size) {
                position = size;
            }
            return this;
        }
        
        @Override
        public boolean isOpen() {
            return open;
        }
        
        @Override
        public void close() {
            open = false;
        }
        
        private void checkOpen() throws ClosedChannelException {
            if (!open) {
                throw new ClosedChannelException();
            }
        }
    }
    
    // Inner class for BasicFileAttributes
    private static class MemoryFileAttributes implements BasicFileAttributes {
        private final MemoryFileNode node;
        
        public MemoryFileAttributes(MemoryFileNode node) {
            this.node = node;
        }
        
        @Override
        public FileTime lastModifiedTime() {
            return node.getLastModifiedTime();
        }
        
        @Override
        public FileTime lastAccessTime() {
            return node.getLastAccessTime();
        }
        
        @Override
        public FileTime creationTime() {
            return node.getCreationTime();
        }
        
        @Override
        public boolean isRegularFile() {
            return node.isFile();
        }
        
        @Override
        public boolean isDirectory() {
            return node.isDirectory();
        }
        
        @Override
        public boolean isSymbolicLink() {
            return false;
        }
        
        @Override
        public boolean isOther() {
            return false;
        }
        
        @Override
        public long size() {
            return node.getSize();
        }
        
        @Override
        public Object fileKey() {
            return node;
        }
    }
}
