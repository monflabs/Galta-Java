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
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Abstract base Path implementation with common path manipulation logic.
 * <p>
 * The syntax follows the JDK's own paths: the empty path has one empty name element and
 * {@link #normalize()} turns "." or "a/.." into the empty path. On a filesystem whose
 * separator is "/", a backslash is an ordinary character of a file name, as it is for
 * the JDK on Unix; on a filesystem whose separator is a backslash (Windows), "/" is
 * accepted as a separator too.
 */
public abstract class AbstractPath implements Path {
    
    protected final AbstractFileSystem fileSystem;
    protected final String path;
    protected final String separator;
    
    protected AbstractPath(AbstractFileSystem fileSystem, String path) {
        this.fileSystem = fileSystem;
        this.separator = fileSystem.getSeparator();
        if (path != null && path.indexOf('\0') >= 0) {
            throw new InvalidPathException(path, "Nul character not allowed");
        }
        this.path = normalizePath(path);
    }
    
    protected String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        // On a backslash filesystem (Windows) "/" is a separator too. On a "/" filesystem a
        // backslash is an ordinary character: converting it made "a\b" two names, unlike the
        // JDK on Unix
        if (separator.equals("\\")) {
            path = path.replace('/', '\\');
        }
        
        // Collapse repeated separators ("a//b" is "a/b")
        String doubleSep = separator + separator;
        while (path.contains(doubleSep)) {
            path = path.replace(doubleSep, separator);
        }
        
        // Remove trailing separators, but never the one of a root: "/", and the Windows
        // "C:\" whose separator is part of the root (stripping it left "C:", a path relative
        // to the drive, and rootPrefix() then built "C:dir" instead of "C:\dir")
        int rootLength = startsWithDriveLetter(path) ? 3 : 1;
        while (path.length() > rootLength && path.endsWith(separator)) {
            path = path.substring(0, path.length() - 1);
        }
        
        return path;
    }

    /**
     * A path as the path part of a URI: always "/" separated, whatever the filesystem
     * separator is. A Windows "\" is not a valid URI path separator, and an absolute URI
     * whose path does not start with "/" is rejected by {@link java.net.URI}.
     */
    protected String toUriPath(Path p) {
        String s = p.toString();
        return separator.equals("/") ? s : s.replace(separator, "/");
    }

    /**
     * A URI of the provider's scheme for a path of this filesystem, carrying the authority
     * of the filesystem's URI ("memory://tenant1/a.txt"), so that the provider's
     * getPath(URI) finds this filesystem again. The path is encoded as needed.
     *
     * @param uriPath the URI path, starting with "/"
     */
    protected java.net.URI buildUri(String uriPath) {
        java.net.URI fsUri = fileSystem.getUri();
        String authority = fsUri != null ? fsUri.getAuthority() : null;
        if (!uriPath.startsWith("/")) {
            uriPath = "/" + uriPath;
        }
        try {
            // The multi-argument constructor encodes spaces, '#', '%'...
            return new java.net.URI(fileSystem.provider().getScheme(), authority != null ? authority : "", uriPath, null, null);
        } catch (java.net.URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
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
        return startsWithDriveLetter(path);
    }

    /**
     * Whether a path string starts with a Windows drive letter ("C:"), on a filesystem that
     * has them. Takes the string so that it can also be used while normalizing, before
     * {@link #path} is assigned.
     */
    private boolean startsWithDriveLetter(String p) {
        return fileSystem.supportsDriveLetters() && p.length() >= 2
            && Character.isLetter(p.charAt(0)) && p.charAt(1) == ':';
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
        if (path.isEmpty()) {
            // The empty path has one, empty, name (as with the JDK)
            return this;
        }
        if (path.equals(separator)) {
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
        return path.isEmpty() ? 1 : getNameComponents().size();
    }
    
    @Override
    public Path getName(int index) {
        if (path.isEmpty() && index == 0) {
            return this;
        }
        List<String> names = getNameComponents();
        if (index < 0 || index >= names.size()) {
            throw new IllegalArgumentException("Invalid index: " + index);
        }
        return createPath(names.get(index));
    }
    
    @Override
    public Path subpath(int beginIndex, int endIndex) {
        if (path.isEmpty() && beginIndex == 0 && endIndex == 1) {
            return this;
        }
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
        
        // "." and "a/.." normalize to the empty path, as with the JDK
        return createPath(normalized.toString());
    }
    
    /**
     * The other path as one of this filesystem's kind. A path of another provider cannot
     * be combined with this one: ProviderMismatchException, as the JDK's paths do.
     */
    protected AbstractPath checkSameKind(Path other) {
        if (other == null) {
            throw new NullPointerException();
        }
        if (!(other instanceof AbstractPath) || other.getClass() != getClass()) {
            throw new ProviderMismatchException();
        }
        return (AbstractPath) other;
    }
    
    @Override
    public Path resolve(Path other) {
        checkSameKind(other);
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
    
    /**
     * The relative path from this path to the other, following the JDK's algorithm: both
     * paths are normalized first, and a base whose remaining names contain ".." cannot be
     * relativized (IllegalArgumentException). For any two paths p and q of the same type,
     * {@code p.resolve(p.relativize(q)).normalize()} equals {@code q.normalize()}.
     */
    @Override
    public Path relativize(Path other) {
        AbstractPath child = checkSameKind(other);
        if (path.equals(child.path)) {
            return createPath("");
        }
        if (this.isAbsolute() != child.isAbsolute()) {
            throw new IllegalArgumentException("'other' is different type of Path");
        }
        if (isAbsolute() && !rootPrefix().equals(child.rootPrefix())) {
            throw new IllegalArgumentException("'other' has different root");
        }
        if (path.isEmpty()) {
            return child;
        }
        
        List<String> baseNames = ((AbstractPath) normalize()).getNameComponents();
        List<String> childNames = ((AbstractPath) child.normalize()).getNameComponents();
        
        // Skip the common names
        int common = 0;
        int n = Math.min(baseNames.size(), childNames.size());
        while (common < n && baseNames.get(common).equals(childNames.get(common))) {
            common++;
        }
        
        // ".." cannot be undone: "../a" has no relative path to "a"
        for (int i = common; i < baseNames.size(); i++) {
            if (baseNames.get(i).equals("..")) {
                throw new IllegalArgumentException("Unable to compute relative path from " + this + " to " + other);
            }
        }
        
        StringBuilder relative = new StringBuilder();
        for (int i = common; i < baseNames.size(); i++) {
            if (relative.length() > 0) {
                relative.append(separator);
            }
            relative.append("..");
        }
        for (int i = common; i < childNames.size(); i++) {
            if (relative.length() > 0) {
                relative.append(separator);
            }
            relative.append(childNames.get(i));
        }
        return createPath(relative.toString());
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
        if (path.isEmpty()) {
            return java.util.Collections.<Path>singletonList(this).iterator();
        }
        List<String> names = getNameComponents();
        List<Path> paths = new ArrayList<>();
        for (String name : names) {
            paths.add(createPath(name));
        }
        return paths.iterator();
    }
    
    @Override
    public int compareTo(Path other) {
        // Paths of different providers cannot be compared (ClassCastException, as with the JDK)
        if (other.getClass() != getClass()) {
            throw new ClassCastException(other.getClass().getName() + " cannot be compared with " + getClass().getName());
        }
        return this.path.compareTo(((AbstractPath) other).path);
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
    
    // Computed once: a path is immutable
    private List<String> nameComponents;

    /**
     * The name elements of this path (an unmodifiable list).
     */
    protected List<String> getNameComponents() {
        List<String> names = nameComponents;
        if (names == null) {
            names = java.util.Collections.unmodifiableList(splitNameComponents());
            nameComponents = names;
        }
        return names;
    }

    private List<String> splitNameComponents() {
        if (path.isEmpty() || path.equals(separator)) {
            return new ArrayList<>(0);
        }
        // Split by hand: String.split() compiled a regex on every call
        List<String> parts = new ArrayList<>();
        int sepLen = separator.length();
        int start = 0;
        while (true) {
            int i = sepLen == 0 ? -1 : path.indexOf(separator, start);
            parts.add(i < 0 ? path.substring(start) : path.substring(start, i));
            if (i < 0) {
                break;
            }
            start = i + sepLen;
        }
        List<String> names = new ArrayList<>(parts.size());
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
