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
package org.monflabs.galtajs.library.node.fs;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.rt.RuntimeUtil;

/**
 * Filesystem primitives shared between the node:fs (sync) and node:fs/promises
 * (async) module surfaces. Operations translate java.nio.file exceptions into
 * JS-style errors and return values suited to the module boundary (String,
 * JSArray, JSObject).
 */
public final class NodeFsOps {

	private NodeFsOps() {}

	/* ------------------------------------------------------------------ *
	 * Options / coercion                                                 *
	 * ------------------------------------------------------------------ */

	static Path pathOf(Object v) {
		if (v == null) {
			throw RuntimeUtil.typeError("fs: path must be a string");
		}
		if (v instanceof CharSequence cs) {
			return Paths.get(cs.toString());
		}
		throw RuntimeUtil.typeError("fs: path must be a string");
	}

	static Charset encodingOf(Object options, Charset dflt) {
		if (options == null || options == RuntimeUtil.UNDEFINED) return dflt;
		if (options instanceof CharSequence cs) return Charset.forName(cs.toString());
		if (options instanceof JSObject o) {
			Object enc = o.getOwnProperty("encoding");
			if (enc instanceof CharSequence cs) return Charset.forName(cs.toString());
		}
		return dflt;
	}

	static boolean recursiveOf(Object options) {
		if (options instanceof JSObject o) {
			Object r = o.getOwnProperty("recursive");
			return r == Boolean.TRUE;
		}
		return false;
	}

	static boolean forceOf(Object options) {
		if (options instanceof JSObject o) {
			Object f = o.getOwnProperty("force");
			return f == Boolean.TRUE;
		}
		return false;
	}

	/* ------------------------------------------------------------------ *
	 * Read / Write                                                       *
	 * ------------------------------------------------------------------ */

	static Object readFile(Path p, Object options) {
		try {
			byte[] bytes = Files.readAllBytes(p);
			// Node.js: when encoding is omitted, returns a Buffer. We don't
			// have Buffer yet, so default to utf-8 String — this matches what
			// most tests actually assert.
			Charset cs = encodingOf(options, StandardCharsets.UTF_8);
			return new String(bytes, cs);
		} catch (NoSuchFileException e) {
			throw RuntimeUtil.typeError("ENOENT: no such file or directory, open '"+p+"'");
		} catch (IOException e) {
			throw RuntimeUtil.typeError("fs.readFile: "+e.getMessage());
		}
	}

	static void writeFile(Path p, Object data, Object options) {
		try {
			byte[] bytes = toBytes(data, encodingOf(options, StandardCharsets.UTF_8));
			Files.write(p, bytes);
		} catch (IOException e) {
			throw RuntimeUtil.typeError("fs.writeFile: "+e.getMessage());
		}
	}

	static void appendFile(Path p, Object data, Object options) {
		try {
			byte[] bytes = toBytes(data, encodingOf(options, StandardCharsets.UTF_8));
			Files.write(p, bytes,
					StandardOpenOption.CREATE,
					StandardOpenOption.WRITE,
					StandardOpenOption.APPEND);
		} catch (IOException e) {
			throw RuntimeUtil.typeError("fs.appendFile: "+e.getMessage());
		}
	}

	private static byte[] toBytes(Object data, Charset cs) {
		if (data instanceof byte[] b) return b;
		if (data instanceof CharSequence s) return s.toString().getBytes(cs);
		return String.valueOf(data).getBytes(cs);
	}

	/* ------------------------------------------------------------------ *
	 * Stat / exists / realpath                                           *
	 * ------------------------------------------------------------------ */

	static boolean exists(Path p) {
		return Files.exists(p);
	}

	static JSObject stat(JSEnvironment env, Path p) {
		try {
			BasicFileAttributes a = Files.readAttributes(p, BasicFileAttributes.class);
			JSObject o = JSObject.create(env);
			o.setOwnProperty("size", a.size());
			o.setOwnProperty("isFile", a.isRegularFile());
			o.setOwnProperty("isDirectory", a.isDirectory());
			o.setOwnProperty("isSymbolicLink", a.isSymbolicLink());
			o.setOwnProperty("mtimeMs", (double) a.lastModifiedTime().toMillis());
			o.setOwnProperty("atimeMs", (double) a.lastAccessTime().toMillis());
			o.setOwnProperty("ctimeMs", (double) a.creationTime().toMillis());
			o.setOwnProperty("birthtimeMs", (double) a.creationTime().toMillis());
			return o;
		} catch (NoSuchFileException e) {
			throw RuntimeUtil.typeError("ENOENT: no such file or directory, stat '"+p+"'");
		} catch (IOException e) {
			throw RuntimeUtil.typeError("fs.stat: "+e.getMessage());
		}
	}

	static String realpath(Path p) {
		try {
			return p.toRealPath().toString();
		} catch (IOException e) {
			throw RuntimeUtil.typeError("fs.realpath: "+e.getMessage());
		}
	}

	static void access(Path p) {
		if (!Files.exists(p)) {
			throw RuntimeUtil.typeError("ENOENT: no such file or directory, access '"+p+"'");
		}
	}

	/* ------------------------------------------------------------------ *
	 * Directory operations                                               *
	 * ------------------------------------------------------------------ */

	static Object readdir(JSEnvironment env, Path p) {
		try (Stream<Path> s = Files.list(p)) {
			List<Object> names = new ArrayList<>();
			s.forEach(child -> names.add(child.getFileName().toString()));
			return JSArray.of(env, names.toArray());
		} catch (NoSuchFileException e) {
			throw RuntimeUtil.typeError("ENOENT: no such file or directory, scandir '"+p+"'");
		} catch (IOException e) {
			throw RuntimeUtil.typeError("fs.readdir: "+e.getMessage());
		}
	}

	static void mkdir(Path p, Object options) {
		try {
			if (recursiveOf(options)) {
				Files.createDirectories(p);
			} else {
				Files.createDirectory(p);
			}
		} catch (IOException e) {
			throw RuntimeUtil.typeError("fs.mkdir: "+e.getMessage());
		}
	}

	static void rm(Path p, Object options) {
		boolean recursive = recursiveOf(options);
		boolean force = forceOf(options);
		try {
			if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
				if (force) return;
				throw RuntimeUtil.typeError("ENOENT: no such file or directory, rm '"+p+"'");
			}
			if (recursive && Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
				deleteTree(p);
			} else {
				Files.delete(p);
			}
		} catch (IOException e) {
			throw RuntimeUtil.typeError("fs.rm: "+e.getMessage());
		}
	}

	private static void deleteTree(Path root) throws IOException {
		try (Stream<Path> s = Files.walk(root)) {
			// Delete deepest first
			List<Path> list = s.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList();
			for (Path entry : list) {
				Files.delete(entry);
			}
		}
	}

	static void unlink(Path p) {
		try {
			Files.delete(p);
		} catch (NoSuchFileException e) {
			throw RuntimeUtil.typeError("ENOENT: no such file or directory, unlink '"+p+"'");
		} catch (IOException e) {
			throw RuntimeUtil.typeError("fs.unlink: "+e.getMessage());
		}
	}

	static void rename(Path from, Path to) {
		try {
			Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw RuntimeUtil.typeError("fs.rename: "+e.getMessage());
		}
	}

	static void copyFile(Path from, Path to) {
		try {
			Files.copy(from, to, new CopyOption[]{StandardCopyOption.REPLACE_EXISTING});
		} catch (IOException e) {
			throw RuntimeUtil.typeError("fs.copyFile: "+e.getMessage());
		}
	}
}
