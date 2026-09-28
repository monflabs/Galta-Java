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
package org.monflabs.filesystem.resources;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.LinkOption;
import java.nio.file.Path;

import org.monflabs.filesystem.AbstractPath;

/**
 * Path implementation for Resource filesystem.
 * Represents a path to a classpath resource.
 */
public class ResourcePath extends AbstractPath {
    
    public ResourcePath(ResourceFileSystem fileSystem, String path) {
        super(fileSystem, path);
    }
    
    @Override
    public ResourceFileSystem getFileSystem() {
        return (ResourceFileSystem) super.getFileSystem();
    }
    
    @Override
    public URI toUri() {
        // resource://basepath!/path/to/resource
        ResourceFileSystem fs = getFileSystem();
        String basePath = fs.getBasePath();
        try {
            return new URI(fs.provider().getScheme(), "", basePath + "!" + toAbsolutePath().toString(), null, null);
        } catch (java.net.URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }
    
    @Override
    public Path toAbsolutePath() {
        if (isAbsolute()) {
            return this;
        }
        // Relative paths in Resource are made absolute by prepending "/"
        return new ResourcePath(getFileSystem(), separator + path);
    }
    
    @Override
    public Path toRealPath(LinkOption... options) throws IOException {
        // Resource filesystem has no symlinks, so real path is just the normalized absolute path
        Path absolute = toAbsolutePath().normalize();
        
        // Verify the path exists
        if (!getFileSystem().exists((ResourcePath) absolute)) {
            throw new java.nio.file.NoSuchFileException(absolute.toString());
        }
        
        return absolute;
    }
    
    @Override
    protected AbstractPath createPath(String path) {
        return new ResourcePath(getFileSystem(), path);
    }
    
    @Override
    public File toFile() {
        // Cannot convert Resource path to File
        throw new UnsupportedOperationException("Resource paths cannot be converted to File");
    }
}
