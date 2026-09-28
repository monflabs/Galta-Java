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
import java.net.URI;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Collections;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Abstract base FileSystem with common functionality.
 */
public abstract class AbstractFileSystem extends FileSystem {
    
    protected final AbstractFileSystemProvider provider;
    protected final URI uri;
    protected final String separator;
    protected volatile boolean open = true;
    
    protected AbstractFileSystem(AbstractFileSystemProvider provider, URI uri, String separator) {
        this.provider = provider;
        this.uri = uri;
        this.separator = separator;
    }
    
    @Override
    public FileSystemProvider provider() {
        return provider;
    }
    
    @Override
    public void close() throws IOException {
        if (open) {
            open = false;
            provider.removeFileSystem(this);
        }
    }

    @Override
    public boolean isOpen() {
        return open;
    }
    
    @Override
    public boolean isReadOnly() {
        return false;
    }
    
    @Override
    public String getSeparator() {
        return separator;
    }
    
    @Override
    public Path getPath(String first, String... more) {
    	checkOpen();
    	StringBuilder sb = new StringBuilder(first);
        for (String segment : more) {
            if (segment.length() > 0) {
                if (sb.length() > 0 && !sb.toString().endsWith(separator)) {
                    sb.append(separator);
                }
                sb.append(segment);
            }
        }
        return createPath(sb.toString());
    }
    
    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
    	checkOpen();
        int colonIndex = syntaxAndPattern.indexOf(':');
        if (colonIndex <= 0 || colonIndex == syntaxAndPattern.length() - 1) {
            throw new IllegalArgumentException("syntaxAndPattern must be in form 'syntax:pattern'");
        }
        
        String syntax = syntaxAndPattern.substring(0, colonIndex);
        String pattern = syntaxAndPattern.substring(colonIndex + 1);
        
        PathMatcher matcher;
        if (syntax.equalsIgnoreCase("glob")) {
            matcher = new GlobPathMatcher(pattern, separator);
        } else if (syntax.equalsIgnoreCase("regex")) {
            matcher = new RegexPathMatcher(pattern);
        } else {
            throw new UnsupportedOperationException("Syntax not supported: " + syntax);
        }
        if (matchRelativeToRoot()) {
            PathMatcher m = matcher;
            return path -> {
                String s = path.toString();
                return m.matches(s.startsWith(separator) ? createPath(s.substring(separator.length())) : path);
            };
        }
        return matcher;
    }
    
    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        throw new UnsupportedOperationException("UserPrincipalLookupService not supported");
    }
    
    @Override
    public WatchService newWatchService() throws IOException {
        throw new UnsupportedOperationException("WatchService not supported");
    }
    
    @Override
    public Set<String> supportedFileAttributeViews() {
        return Collections.singleton("basic");
    }
    
    public URI getUri() {
        return uri;
    }
    
    protected void checkOpen() {
        if (!open) {
            throw new ClosedFileSystemException();
        }
    }
    
    /**
     * Whether a leading "X:" name is a Windows drive letter. Only a filesystem using the
     * Windows separator has drive letters: on a "/" filesystem, "x:" is an ordinary name.
     */
    public boolean supportsDriveLetters() {
        return "\\".equals(separator);
    }
    
    /**
     * Whether path matchers see paths relative to the root ("a/b.txt" rather than "/a/b.txt").
     * The read-only ZIP and resource filesystems match that way.
     */
    protected boolean matchRelativeToRoot() {
        return false;
    }
    
    /**
     * Create a path instance for this filesystem.
     */
    protected abstract AbstractPath createPath(String path);
    
    // PathMatcher implementations
    protected static class GlobPathMatcher implements PathMatcher {
        private final Pattern pattern;
        //private final String separator;
        
        public GlobPathMatcher(String globPattern, String separator) {
            //this.separator = separator;
            this.pattern = Pattern.compile(globToRegex(globPattern, separator));
        }
        
        @Override
        public boolean matches(Path path) {
            return pattern.matcher(path.toString()).matches();
        }
        
        private static String globToRegex(String glob, String separator) {
            // Escape separator for use in regex (e.g., "\\" becomes "\\\\")
            String sepRegex = Pattern.quote(separator);
            // Pattern to match any character except separator
            String notSepRegex = "[^" + sepRegex + "]";
            
            StringBuilder regex = new StringBuilder("^");
            int groupDepth = 0;
            for (int i = 0; i < glob.length(); i++) {
                char c = glob.charAt(i);
                switch (c) {
                    case '*':
                        if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                            regex.append(".*");
                            i++; // skip next *
                        } else {
                            regex.append(notSepRegex).append("*");
                        }
                        break;
                    case '?':
                        regex.append(notSepRegex);
                        break;
                    case '{':
                        // {a,b} alternation
                        groupDepth++;
                        regex.append("(?:");
                        break;
                    case '}':
                        if (groupDepth > 0) {
                            groupDepth--;
                            regex.append(')');
                        } else {
                            regex.append("\\}");
                        }
                        break;
                    case ',':
                        regex.append(groupDepth > 0 ? "|" : ",");
                        break;
                    case '[': {
                        // Character class: [abc], [a-z], [!abc]
                        int end = glob.indexOf(']', i + 1);
                        if (end < 0) {
                            regex.append("\\[");
                            break;
                        }
                        String cls = glob.substring(i + 1, end);
                        regex.append('[');
                        if (cls.startsWith("!")) {
                            regex.append('^');
                            cls = cls.substring(1);
                        }
                        for (int j = 0; j < cls.length(); j++) {
                            char cc = cls.charAt(j);
                            if (cc == '\\' || cc == '[' || cc == ']' || cc == '^' || cc == '&') {
                                regex.append('\\');
                            }
                            regex.append(cc);
                        }
                        regex.append(']');
                        i = end;
                        break;
                    }
                    case '\\':
                        // Escaped glob character: match it literally (never as a regex class such as \d)
                        i++;
                        if (i < glob.length()) {
                            appendLiteral(regex, glob.charAt(i));
                        }
                        break;
                    case '/':
                        // In glob patterns, / always means separator
                        regex.append(sepRegex);
                        break;
                    default:
                        appendLiteral(regex, c);
                }
            }
            regex.append("$");
            return regex.toString();
        }
        
        private static void appendLiteral(StringBuilder regex, char c) {
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '-' && c != ' ') {
                regex.append('\\');
            }
            regex.append(c);
        }
    }
    
    protected static class RegexPathMatcher implements PathMatcher {
        private final Pattern pattern;
        
        public RegexPathMatcher(String regex) {
            this.pattern = Pattern.compile(regex);
        }
        
        @Override
        public boolean matches(Path path) {
            return pattern.matcher(path.toString()).matches();
        }
    }
}
