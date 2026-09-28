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
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
 * Containment checks shared by the sandboxed providers (file, path and path-delegating).
 */
public final class Sandbox {
    
    // Same limit as most operating systems before they report a symbolic link loop
    private static final int MAX_LINK_HOPS = 40;
    
    private Sandbox() {
    }
    
    /**
     * Check that a path, once symbolic links are followed, stays inside the sandbox root.
     * The path itself may not exist yet (a file about to be created), so the nearest existing
     * ancestor is what gets resolved. A dangling symbolic link is followed to its target as
     * well: opening it with CREATE would create that target, wherever it is.
     *
     * @param path the path in the underlying filesystem
     * @param root the sandbox root in the same filesystem
     * @param displayPath the path shown in the exception message
     * @throws AccessDeniedException if the path escapes the root
     */
    public static void checkInside(Path path, Path root, Object displayPath) throws IOException {
        Path realRoot = root.toRealPath();
        Path current = path.toAbsolutePath().normalize();
        if (!current.startsWith(root.toAbsolutePath().normalize()) && !current.startsWith(realRoot)) {
            throw escapes(displayPath);
        }
        for (int hops = 0; hops <= MAX_LINK_HOPS; hops++) {
            Path existing = current;
            while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
                existing = existing.getParent();
            }
            if (existing == null) {
                return;
            }
            if (Files.isSymbolicLink(existing) && !Files.exists(existing)) {
                // Dangling link: continue with what it points to
                Path target = Files.readSymbolicLink(existing);
                for (Path name : target) {
                    if (name.toString().equals("..")) {
                        // ".." in a link target cannot be resolved lexically when the
                        // parents are links themselves: refuse rather than guess
                        throw escapes(displayPath);
                    }
                }
                Path remainder = existing.relativize(current);
                Path resolved = existing.getParent() != null ? existing.getParent().resolve(target) : target;
                current = resolved.resolve(remainder).toAbsolutePath().normalize();
                continue;
            }
            if (!existing.toRealPath().startsWith(realRoot)) {
                throw escapes(displayPath);
            }
            return;
        }
        // Too many links: a loop, or a chain long enough to hide an escape
        throw escapes(displayPath);
    }
    
    private static AccessDeniedException escapes(Object displayPath) {
        return new AccessDeniedException("Path escapes filesystem root: " + displayPath);
    }
}
