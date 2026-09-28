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
package org.monflabs.filesystem;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Abstract base Path implementation with common path manipulation logic.
 */
public abstract class AbstractPath implements Path {
    
    protected final AbstractFileSystem fileSystem;
    protected final String path;
    protected final String separator;
    
    protected AbstractPath(AbstractFileSystem fileSystem, String path) {
        this.fileSystem = fileSystem;
        this.separator = fileSystem.getSeparator();
        this.path = normalizePath(path);
    }
    
    protected String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        // Normalize separators
        path = path.replace('/', separator.charAt(0)).replace('\\', separator.charAt(0));
        
        // Collapse repeated separators ("a//b" is "a/b")
        String doubleSep = separator + separator;
        while (path.contains(doubleSep)) {
            path = path.replace(doubleSep, separator);
        }
        
        // Remove trailing separators (except for root)
        while (path.length() > 1 && path.endsWith(separator)) {
            path = path.substring(0, path.length() - 1);
        }
        
        return path;
    }
    
    @Override
    public AbstractFileSystem getFileSystem() {
        return fileSystem;
    }
    
    @Override
    public boolean isAbsolute() {
        if (path.isEmpty()) {
            return false;
        }
        // Unix-style: starts with /
        if (path.startsWith(separator)) {
            return true;
        }
        // Windows-style: drive letter followed by a separator ("C:\\x"). "C:x" is relative
        // to the drive's current directory, so it is not absolute
        return hasDriveLetter() && path.length() >= 3 && path.startsWith(separator, 2);
    }
    
    /**
     * Whether this path starts with a Windows drive letter ("C:"). Only filesystems that
     * support drive letters have them; elsewhere "x:" is an ordinary file name.
     */
    protected boolean hasDriveLetter() {
        return fileSystem.supportsDriveLetters() && path.length() >= 2
            && Character.isLetter(path.charAt(0)) && path.charAt(1) == ':';
    }
    
    @Override
    public Path getRoot() {
        if (!isAbsolute()) {
            return null;
        }
        if (path.startsWith(separator)) {
            return createPath(separator);
        }
        // Windows-style root
        if (hasDriveLetter()) {
            int sepIndex = path.indexOf(separator);
            if (sepIndex > 0) {
                return createPath(path.substring(0, sepIndex + 1));
            }
            return createPath(path.substring(0, 2) + separator);
        }
        return null;
    }
    
    @Override
    public Path getFileName() {
        if (path.isEmpty() || path.equals(separator)) {
            return null;
        }
        int lastSep = path.lastIndexOf(separator);
        if (lastSep == -1) {
            return createPath(path);
        }
        if (lastSep == path.length() - 1) {
            // Path ends with separator
            int prevSep = path.lastIndexOf(separator, lastSep - 1);
            if (prevSep == -1) {
                return createPath(path.substring(0, lastSep));
            }
            return createPath(path.substring(prevSep + 1, lastSep));
        }
        return createPath(path.substring(lastSep + 1));
    }
    
    @Override
    public Path getParent() {
        if (path.isEmpty() || path.equals(separator)) {
            return null;
        }
        int lastSep = path.lastIndexOf(separator);
        if (lastSep == -1) {
            return null;
        }
        if (lastSep == 0) {
            return createPath(separator);
        }
        return createPath(path.substring(0, lastSep));
    }
    
    @Override
    public int getNameCount() {
        return getNameComponents().size();
    }
    
    @Override
    public Path getName(int index) {
        List<String> names = getNameComponents();
        if (index < 0 || index >= names.size()) {
            throw new IllegalArgumentException("Invalid index: " + index);
        }
        return createPath(names.get(index));
    }
    
    @Override
    public Path subpath(int beginIndex, int endIndex) {
        List<String> names = getNameComponents();
        if (beginIndex < 0 || beginIndex >= names.size() || 
            endIndex <= beginIndex || endIndex > names.size()) {
            throw new IllegalArgumentException("Invalid indices");
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = beginIndex; i < endIndex; i++) {
            if (i > beginIndex) {
                sb.append(separator);
            }
            sb.append(names.get(i));
        }
        return createPath(sb.toString());
    }
    
    /**
     * The root prefix of this path ("/" or "C:\\"), or "" for a relative path.
     */
    protected String rootPrefix() {
        Path root = getRoot();
        return root != null ? root.toString() : "";
    }
    
    // Path.startsWith/endsWith compare whole name elements, not characters:
    // "/foo/barbaz" does not start with "/foo/bar"
    @Override
    public boolean startsWith(Path other) {
        if (other.getFileSystem() != this.fileSystem || !(other instanceof AbstractPath)) {
            return false;
        }
        AbstractPath o = (AbstractPath) other;
        if (o.path.isEmpty()) {
            return path.isEmpty();
        }
        if (isAbsolute() != o.isAbsolute() || !rootPrefix().equals(o.rootPrefix())) {
            return false;
        }
        List<String> names = getNameComponents();
        List<String> otherNames = o.getNameComponents();
        if (otherNames.size() > names.size()) {
            return false;
        }
        return names.subList(0, otherNames.size()).equals(otherNames);
    }
    
    @Override
    public boolean startsWith(String other) {
        return startsWith(createPath(other));
    }
    
    @Override
    public boolean endsWith(Path other) {
        if (other.getFileSystem() != this.fileSystem || !(other instanceof AbstractPath)) {
            return false;
        }
        AbstractPath o = (AbstractPath) other;
        if (o.path.isEmpty()) {
            return path.isEmpty();
        }
        if (o.isAbsolute()) {
            return isAbsolute() && rootPrefix().equals(o.rootPrefix())
                && getNameComponents().equals(o.getNameComponents());
        }
        List<String> names = getNameComponents();
        List<String> otherNames = o.getNameComponents();
        if (otherNames.size() > names.size()) {
            return false;
        }
        return names.subList(names.size() - otherNames.size(), names.size()).equals(otherNames);
    }
    
    @Override
    public boolean endsWith(String other) {
        return endsWith(createPath(other));
    }
    
    @Override
    public Path normalize() {
        if (path.isEmpty()) {
            return this;
        }
        
        List<String> parts = new ArrayList<>();
        // getNameComponents() skips the drive letter of a Windows-style path
        for (String segment : getNameComponents()) {
            if (segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                if (!parts.isEmpty() && !parts.get(parts.size() - 1).equals("..")) {
                    parts.remove(parts.size() - 1);
                } else if (!isAbsolute()) {
                    parts.add(segment);
                }
            } else {
                parts.add(segment);
            }
        }
        
        StringBuilder normalized = new StringBuilder();
        if (isAbsolute()) {
            // "/" or "C:\\" - a drive path used to come out as "\\C:\\..."
            normalized.append(rootPrefix());
        }
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                normalized.append(separator);
            }
            normalized.append(parts.get(i));
        }
        
        return createPath(normalized.length() == 0 ? "." : normalized.toString());
    }
    
    @Override
    public Path resolve(Path other) {
        if (other.isAbsolute()) {
            return other;
        }
        if (other.toString().isEmpty()) {
            return this;
        }
        String otherPath = other.toString();
        if (path.isEmpty()) {
            return createPath(otherPath);
        }
        if (path.endsWith(separator)) {
            return createPath(path + otherPath);
        }
        return createPath(path + separator + otherPath);
    }
    
    @Override
    public Path resolve(String other) {
        if (other.isEmpty()) {
            return this;
        }
        return resolve(createPath(other));
    }
    
    @Override
    public Path resolveSibling(Path other) {
        Path parent = getParent();
        if (parent == null) {
            return other;
        }
        return parent.resolve(other);
    }
    
    @Override
    public Path resolveSibling(String other) {
        return resolveSibling(createPath(other));
    }
    
    @Override
    public Path relativize(Path other) {
        if (!(other instanceof AbstractPath)) {
            throw new IllegalArgumentException("Other path must be AbstractPath");
        }
        if (this.isAbsolute() != other.isAbsolute()) {
            throw new IllegalArgumentException("Paths must both be absolute or both be relative");
        }
        
        if (this.equals(other)) {
            return createPath("");
        }
        
        List<String> thisNames = getNameComponents();
        List<String> otherNames = ((AbstractPath) other).getNameComponents();
        
        // Find common prefix
        int commonPrefix = 0;
        while (commonPrefix < Math.min(thisNames.size(), otherNames.size()) &&
               thisNames.get(commonPrefix).equals(otherNames.get(commonPrefix))) {
            commonPrefix++;
        }
        
        // Build relative path
        StringBuilder relative = new StringBuilder();
        for (int i = commonPrefix; i < thisNames.size(); i++) {
            if (relative.length() > 0) {
                relative.append(separator);
            }
            relative.append("..");
        }
        for (int i = commonPrefix; i < otherNames.size(); i++) {
            if (relative.length() > 0) {
                relative.append(separator);
            }
            relative.append(otherNames.get(i));
        }
        
        return createPath(relative.length() == 0 ? "." : relative.toString());
    }
    
    @Override
    public Path toAbsolutePath() {
        if (isAbsolute()) {
            return this;
        }
        // Default implementation - subclasses may override
        return createPath(separator + path);
    }
    
    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events,
                            WatchEvent.Modifier... modifiers) throws IOException {
        throw new UnsupportedOperationException("WatchService not supported");
    }
    
    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events) 
            throws IOException {
        throw new UnsupportedOperationException("WatchService not supported");
    }
    
    @Override
    public Iterator<Path> iterator() {
        List<String> names = getNameComponents();
        List<Path> paths = new ArrayList<>();
        for (String name : names) {
            paths.add(createPath(name));
        }
        return paths.iterator();
    }
    
    @Override
    public int compareTo(Path other) {
        return this.path.compareTo(other.toString());
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof AbstractPath)) return false;
        AbstractPath other = (AbstractPath) obj;
        return this.fileSystem.equals(other.fileSystem) && this.path.equals(other.path);
    }
    
    @Override
    public int hashCode() {
        return path.hashCode();
    }
    
    @Override
    public String toString() {
        return path;
    }
    
    protected List<String> getNameComponents() {
        if (path.isEmpty() || path.equals(separator)) {
            return new ArrayList<>();
        }
        String[] parts = path.split(java.util.regex.Pattern.quote(separator));
        List<String> names = new ArrayList<>();
        boolean skipDrive = hasDriveLetter();
        for (String part : parts) {
            if (!part.isEmpty()) {
                // Skip the drive letter of a Windows path ("C:")
                if (skipDrive) {
                    skipDrive = false;
                    if (part.length() == 2 && part.charAt(1) == ':') {
                        continue;
                    }
                }
                names.add(part);
            }
        }
        return names;
    }
    
    /**
     * Create a new path instance.
     */
    protected abstract AbstractPath createPath(String path);
}
