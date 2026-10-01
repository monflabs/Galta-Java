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
package org.monflabs.json.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.monflabs.util.Console;
import org.monflabs.util.PathUtil;
import org.monflabs.util.StringUtil;
import org.monflabs.util.UserPath;
import org.monflabs.util.config.ConfigException;


/**
 * Configuration stored as JSON files in a folder.
 * <p>
 * The <code>$ref</code> resources must stay inside the folder: a reference with an absolute
 * path, climbing out with "..", or going through a symbolic link that leads outside of the
 * folder is rejected.
 * <p>
 * A file is saved to a temporary file then moved, so a failure cannot leave a truncated file.
 * A file that is a symbolic link is written to the target of the link (the link is kept), and
 * an existing file keeps its POSIX permissions (a new file is only readable and writable by its
 * owner).
 * <p>
 * Several configurations (including in other processes) can share a folder: the loads, saves
 * and updates hold a lock on the folder (a <code>.&lt;fileName&gt;.lock</code> file), and an
 * update reloads the files first when auto save is on, so it applies to their current content.
 */
public class JsonFileConfig extends AbstractJsonConfig {

	public static class Builder extends ConfigBuilder<JsonFileConfig,Builder> {
		private Path folder;
		private String fileName;
		private boolean readOnly;
		private Builder() {}
		public Builder readOnly(boolean readOnly) {
			this.readOnly = readOnly;
			return this;
		}
		public Builder folder(Path folder) {
			this.folder = folder;
			return this;
		}
		public Builder fileName(String fileName) {
			this.fileName = fileName;
			return this;
		}
		@Override
		protected JsonFileConfig _build() {
			return new JsonFileConfig(this);
		}
	}
	public static Builder newBuilder() {
		return new Builder();
	}

	// The configurations of the JVM using the same files share a monitor: a FileLock is
	// held by the process, it does not serialize its threads (and cannot be acquired twice)
	private static final Map<Path,Object> MONITORS = new ConcurrentHashMap<>();

	private Path folder;
	private Path devFolder;
	private String fileName;
	private boolean readOnly;
	// The lock of the storage is held by this configuration (it is reentrant)
	private int lockCount;

	private JsonFileConfig(Builder b) {
		super(b);
		this.folder = b.folder;
		this.fileName = b.fileName;
		this.readOnly = b.readOnly;
		if(folder!=null) {
			if(!Files.exists(folder)) {
				try {
					Files.createDirectories(folder);
				} catch(IOException ex) {
					Console.log(ex);
				}
			}
			// This is for development environments
			Path userHomeDev = UserPath.getUserHomeDev();
			if(userHomeDev!=null) {
				devFolder = userHomeDev.resolve(folder.getFileName().toString());
			}
		}
		load();
	}

	@Override
	public boolean isReadOnly() {
		return readOnly;
	}

	public Path getFolder() {
		return folder;
	}

	@Override
	protected boolean reloadBeforeUpdate() {
		return true;
	}

	@Override
	protected synchronized <T> T withStorageLock(Supplier<T> operation) {
		if(readOnly || folder==null || lockCount>0) {
			return operation.get();
		}
		Path lockFile = folder.toAbsolutePath().normalize().resolve("."+fileName+".lock");
		Object monitor = MONITORS.computeIfAbsent(lockFile, (f) -> new Object());
		synchronized(monitor) {
			lockCount++;
			try {
				FileChannel ch;
				try {
					ch = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
				} catch(IOException ex) {
					// The folder cannot hold a lock file: only the threads of this process
					// are synchronized
					return operation.get();
				}
				try(ch; FileLock lock = ch.lock()) {
					return operation.get();
				} catch(IOException ex) {
					throw new ConfigException(ex, "Error while locking the configuration file {0}", lockFile);
				}
			} finally {
				lockCount--;
			}
		}
	}

	@Override
	public InputStream getResource(String path) {
		Path resource = getFile(path,false);
		if(resource!=null) {
			if(Files.isRegularFile(resource)) {
				try {
					return Files.newInputStream(resource);
				} catch(IOException ex) {
					throw new ConfigException(ex, "Error while loading file '{0}'",resource);
				}
			}
			throw new ConfigException(null, "Error while loading file '{0}'",resource);
		}
		return null;
	}
	@Override
	public void setResource(String path, Consumer<OutputStream> save) {
		if(isReadOnly() || folder==null) {
			throw new ConfigException(null,"Config is readonly");
		}
		Path resource = getFile(path,true);
		try  {
			// A symbolic link is kept: its target is written
			Path target = Files.isSymbolicLink(resource) ? realPath(resource) : resource;
			Files.createDirectories(target.getParent());
			// Written to a temporary file then moved, so a failure while writing cannot
			// leave a truncated configuration file. The temporary file is only readable by its
			// owner, and gets the permissions of the file it replaces.
			Path tmp = Files.createTempFile(target.getParent(), target.getFileName().toString()+".", ".tmp");
			try {
				copyPermissions(target, tmp);
				try(OutputStream os=Files.newOutputStream(tmp)) {
					save.accept(os);
				}
				try {
					Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
				} catch(AtomicMoveNotSupportedException ex) {
					Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
				}
			} finally {
				Files.deleteIfExists(tmp);
			}
		} catch(ConfigException e) {
			throw e;
		} catch(Exception e) {
			throw new ConfigException(e,"Error while writing file '{0}'", resource);
		}
	}
	private static void copyPermissions(Path from, Path to) throws IOException {
		if(Files.exists(from)) {
			PosixFileAttributeView src = Files.getFileAttributeView(from, PosixFileAttributeView.class);
			PosixFileAttributeView dst = Files.getFileAttributeView(to, PosixFileAttributeView.class);
			if(src!=null && dst!=null) {
				dst.setPermissions(src.readAttributes().permissions());
			}
		}
	}

	private Path getFile(String path, boolean forWrite) {
		if(folder!=null) {
			// The main file is chosen by the application, the other resources come from the
			// content ($ref): only the latter are restricted to the real folder
			boolean ref = StringUtil.isNotEmpty(path);
			String fn = ref ? path : fileName;
			if(File.separatorChar!=PathUtil.POSIX_SEP) {
				fn = fn.replace(PathUtil.POSIX_SEP, File.separatorChar);
			}
			Path file = contained(folder, fn, ref);
			if(Files.exists(file)) {
				return file;
			}
			if(devFolder!=null) {
				Path devFile = contained(devFolder, fn, ref);
				if(Files.exists(devFile)) {
					return devFile;
				}
			}
			if(forWrite) {
				return file;
			}
		}
		return null;
	}
	/**
	 * Resolves a resource name (a $ref of the configuration) in a folder. The resource
	 * must stay inside the folder: an absolute path or a path climbing out of it with
	 * ".." is rejected, as the referenced resources are also written back when the
	 * configuration is saved. With realPath, the resource must also stay inside the real
	 * folder once the symbolic links are resolved.
	 */
	private static Path contained(Path dir, String fn, boolean realPath) {
		Path base = dir.toAbsolutePath().normalize();
		Path file = base.resolve(fn).normalize();
		if(!file.startsWith(base) || file.equals(base)) {
			throw new ConfigException(null, "Resource '{0}' is outside of the configuration folder", fn);
		}
		if(realPath) {
			try {
				Path realBase = realPath(base);
				Path realFile = realPath(file);
				if(!realFile.startsWith(realBase) || realFile.equals(realBase)) {
					throw new ConfigException(null, "Resource '{0}' is outside of the configuration folder (through a symbolic link)", fn);
				}
			} catch(IOException ex) {
				throw new ConfigException(ex, "Cannot resolve the resource '{0}'", fn);
			}
		}
		return file;
	}
	/**
	 * The real path of a file that may not exist: the symbolic links are followed (even when
	 * dangling), and the missing part of the path is resolved against the real path of its
	 * deepest existing ancestor.
	 */
	private static Path realPath(Path p) throws IOException {
		Path cur = p.toAbsolutePath().normalize();
		Path tail = null;
		for(int hops=0; cur!=null; ) {
			if(Files.isSymbolicLink(cur)) {
				if(++hops>40) {
					throw new IOException("Too many levels of symbolic links: "+p);
				}
				Path target = Files.readSymbolicLink(cur);
				Path parent = cur.getParent();
				cur = (parent!=null ? parent.resolve(target) : target).toAbsolutePath().normalize();
				continue;
			}
			if(Files.exists(cur, LinkOption.NOFOLLOW_LINKS)) {
				Path real = cur.toRealPath();
				return tail!=null ? real.resolve(tail).normalize() : real;
			}
			Path name = cur.getFileName();
			if(name!=null) {
				tail = tail!=null ? name.resolve(tail) : name;
			}
			cur = cur.getParent();
		}
		return p.toAbsolutePath().normalize();
	}
}
