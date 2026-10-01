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
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystemLoopException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.NotLinkException;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttributeView;

/**
 * Containment checks shared by the sandboxed providers (file, path and path-delegating).
 * <p>
 * The checks are made on the path before the operation runs, with symbolic links
 * followed. They keep a well-behaved caller inside the root, but the sandbox is
 * <b>not a security boundary</b>:
 * <ul>
 * <li>Another process (or thread) writing in the root at the same time can swap a
 * checked directory for a symbolic link between the check and the operation (a
 * time-of-check/time-of-use race) and make the operation act outside the root. Plain
 * java.nio offers no openat()/O_NOFOLLOW-style API to close that window.</li>
 * <li>A hard link inside the root to a file outside it cannot be told apart from a
 * regular file: it is read and written like one.</li>
 * </ul>
 * Use operating system isolation (a container, a dedicated user, a chroot) when the
 * content of the root, or concurrent local writers, are not trusted.
 * <p>
 * An operation that acts on a path without following its last element (reading the
 * attributes with {@link LinkOption#NOFOLLOW_LINKS}, deleting, moving, copying with
 * NOFOLLOW_LINKS) only checks the parent: a link pointing outside the root can be
 * inspected, deleted or moved, never followed.
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
        checkInside(path, root, root.toRealPath(), displayPath);
    }

    /**
     * Same as {@link #checkInside(Path, Path, Object)}, with the real path of the root
     * already resolved: a filesystem resolves it once instead of on every operation.
     */
    public static void checkInside(Path path, Path root, Path realRoot, Object displayPath) throws IOException {
        Path current= path.toAbsolutePath().normalize();
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
    
    /**
     * Check a path whose last element is acted upon without being followed (attributes read
     * with NOFOLLOW_LINKS, delete, move...): the path must be lexically inside the root, and
     * its parent must stay inside once symbolic links are followed. The last element itself
     * may be a symbolic link pointing anywhere.
     */
    public static void checkParentInside(Path path, Path root, Path realRoot, Object displayPath) throws IOException {
        Path current = path.toAbsolutePath().normalize();
        Path normalizedRoot = root.toAbsolutePath().normalize();
        if (!current.startsWith(normalizedRoot) && !current.startsWith(realRoot)) {
            throw escapes(displayPath);
        }
        Path parent = current.getParent();
        if (parent == null || current.equals(normalizedRoot) || current.equals(realRoot)) {
            // The root itself
            checkInside(current, root, realRoot, displayPath);
            return;
        }
        checkInside(parent, root, realRoot, displayPath);
    }

    /**
     * Whether the options ask not to follow symbolic links.
     */
    public static boolean noFollow(Object... options) {
        if (options != null) {
            for (Object o : options) {
                if (o == LinkOption.NOFOLLOW_LINKS) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The same exception, reporting the paths of the filesystem instead of the paths of the
     * underlying one: a sandboxed filesystem does not disclose where its root lives.
     */
    public static FileSystemException withPaths(FileSystemException e, Object file, Object other) {
        String f = file != null ? file.toString() : null;
        String o = other != null ? other.toString() : null;
        String reason = e.getReason();
        FileSystemException t;
        if (e instanceof NoSuchFileException) {
            t = new NoSuchFileException(f, o, reason);
        } else if (e instanceof AccessDeniedException) {
            t = new AccessDeniedException(f, o, reason);
        } else if (e instanceof FileAlreadyExistsException) {
            t = new FileAlreadyExistsException(f, o, reason);
        } else if (e instanceof DirectoryNotEmptyException) {
            t = new DirectoryNotEmptyException(f);
        } else if (e instanceof NotDirectoryException) {
            t = new NotDirectoryException(f);
        } else if (e instanceof NotLinkException) {
            t = new NotLinkException(f, o, reason);
        } else if (e instanceof AtomicMoveNotSupportedException) {
            t = new AtomicMoveNotSupportedException(f, o, reason);
        } else if (e instanceof FileSystemLoopException) {
            t = new FileSystemLoopException(f);
        } else if (e.getClass() == FileSystemException.class) {
            t = new FileSystemException(f, o, reason);
        } else {
            // A subclass we don't know how to rebuild: keep it
            return e;
        }
        t.setStackTrace(e.getStackTrace());
        return t;
    }

    /**
     * A file attribute view of the underlying filesystem, for a type other than the basic
     * view. The underlying path is resolved, and checked against the sandbox, by every call
     * of the view (except name()), so a view obtained once never escapes the root.
     *
     * @param type the view interface
     * @param probe an underlying path, only used to know whether the view is supported
     * @param resolver resolves (and checks) the underlying path on each call
     * @return the view, or null when the underlying filesystem does not support it
     */
    public static <V extends FileAttributeView> V delegatedView(Class<V> type, Path probe, Resolver resolver, LinkOption... options) {
        if (!type.isInterface()) {
            return null;
        }
        V probeView = Files.getFileAttributeView(probe, type, options);
        if (probeView == null) {
            return null;
        }
        String name = probeView.name();
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                switch (method.getName()) {
                    case "equals":
                        return proxy == args[0];
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    default:
                        return type.getSimpleName() + "[" + name + "]";
                }
            }
            if (method.getName().equals("name") && method.getParameterCount() == 0) {
                return name;
            }
            V real = Files.getFileAttributeView(resolver.resolve(), type, options);
            if (real == null) {
                throw new UnsupportedOperationException("View not supported: " + name);
            }
            try {
                return method.invoke(real, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        };
        ClassLoader loader = type.getClassLoader() != null ? type.getClassLoader() : Sandbox.class.getClassLoader();
        return type.cast(Proxy.newProxyInstance(loader, new Class<?>[] { type }, handler));
    }

    /**
     * Resolves the underlying path of a delegated view, checked against the sandbox.
     */
    @FunctionalInterface
    public interface Resolver {
        Path resolve() throws IOException;
    }

    /**
     * The real path of a sandbox root, resolved on first use and then kept.
     */
    public static final class RealRoot {
        private final Path root;
        private volatile Path real;

        public RealRoot(Path root) {
            this.root = root;
        }

        public Path get() throws IOException {
            Path r = real;
            if (r == null) {
                r = root.toRealPath();
                real = r;
            }
            return r;
        }
    }

    private static AccessDeniedException escapes(Object displayPath) {
        return new AccessDeniedException("Path escapes filesystem root: " + displayPath);
    }
}
