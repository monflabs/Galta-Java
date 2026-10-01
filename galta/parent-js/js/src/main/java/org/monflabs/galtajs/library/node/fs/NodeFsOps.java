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
import java.nio.file.AccessDeniedException;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.InvalidPathException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NotDirectoryException;
import java.nio.file.NoSuchFileException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.rt.JSRuntimeException;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;

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

	static Path pathOf(FileSystem fs, Object v) {
		if (v instanceof CharSequence cs) {
			try {
				return fs.getPath(cs.toString());
			} catch (InvalidPathException e) {
				throw RuntimeUtil.typeError("fs: invalid path '"+cs+"'");
			}
		}
		throw RuntimeUtil.typeError("fs: path must be a string");
	}

	// The encoding named by the options: a string, or { encoding }; null when
	// there is none
	private static String encodingName(Object options) {
		Object enc = options;
		if (options instanceof JSObject o) {
			enc = o.getOwnProperty("encoding");
		}
		if (enc instanceof CharSequence cs) {
			return cs.toString();
		}
		return null;
	}

	// Node's encodings: text ones map to a charset, the binary-to-text ones
	// (hex, base64, base64url) are handled by decode()/encode()
	static Charset charsetOf(String enc) {
		return switch (enc.toLowerCase(java.util.Locale.ROOT)) {
			case "utf8", "utf-8" -> StandardCharsets.UTF_8;
			case "latin1", "binary" -> StandardCharsets.ISO_8859_1;
			case "ascii" -> StandardCharsets.US_ASCII;
			case "ucs2", "ucs-2", "utf16le", "utf-16le" -> StandardCharsets.UTF_16LE;
			default -> null;
		};
	}

	// bytes -> string, with the encoding of the options (UTF-8 by default)
	static String decode(byte[] bytes, Object options) {
		String enc = encodingName(options);
		if (enc == null) {
			return new String(bytes, StandardCharsets.UTF_8);
		}
		switch (enc.toLowerCase(java.util.Locale.ROOT)) {
			case "hex": return HexFormat.of().formatHex(bytes);
			case "base64": return Base64.getEncoder().encodeToString(bytes);
			case "base64url": return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
			default: return new String(bytes, requireCharset(enc));
		}
	}

	// string -> bytes, with the encoding of the options (UTF-8 by default)
	private static byte[] encode(String s, Object options) {
		String enc = encodingName(options);
		if (enc == null) {
			return s.getBytes(StandardCharsets.UTF_8);
		}
		try {
			switch (enc.toLowerCase(java.util.Locale.ROOT)) {
				case "hex": return HexFormat.of().parseHex(s.length() % 2 == 0 ? s : s.substring(0, s.length() - 1));
				case "base64": return Base64.getMimeDecoder().decode(s.replace('-', '+').replace('_', '/'));
				case "base64url": return Base64.getMimeDecoder().decode(s.replace('-', '+').replace('_', '/'));
				default: return s.getBytes(requireCharset(enc));
			}
		} catch (IllegalArgumentException e) {
			throw RuntimeUtil.typeError("fs: invalid "+enc+" data");
		}
	}

	private static Charset requireCharset(String enc) {
		Charset cs = charsetOf(enc);
		if (cs == null) {
			throw RuntimeUtil.typeError("fs: unknown encoding: "+enc);
		}
		return cs;
	}

	/**
	 * A Node-style error for a failed file system call: an Error whose
	 * {@code code} (ENOENT, EEXIST...), {@code syscall} and {@code path}
	 * properties are set, with Node's message format.
	 */
	static JSRuntimeException fsError(IOException e, String syscall, Path p) {
		String code;
		String text;
		if (e instanceof NoSuchFileException) {
			code = "ENOENT"; text = "no such file or directory";
		} else if (e instanceof FileAlreadyExistsException) {
			code = "EEXIST"; text = "file already exists";
		} else if (e instanceof DirectoryNotEmptyException) {
			code = "ENOTEMPTY"; text = "directory not empty";
		} else if (e instanceof NotDirectoryException) {
			code = "ENOTDIR"; text = "not a directory";
		} else if (e instanceof AccessDeniedException) {
			code = "EACCES"; text = "permission denied";
		} else if (e instanceof FileSystemException fse && fse.getReason() != null && fse.getReason().toLowerCase(java.util.Locale.ROOT).contains("is a directory")) {
			code = "EISDIR"; text = "illegal operation on a directory";
		} else if (e instanceof FileSystemException fse && fse.getReason() != null && fse.getReason().toLowerCase(java.util.Locale.ROOT).contains("not a directory")) {
			code = "ENOTDIR"; text = "not a directory";
		} else {
			code = "EIO"; text = e.getMessage() != null ? e.getMessage() : "i/o error";
		}
		return fsError(code, text, syscall, p, e);
	}
	static JSRuntimeException fsError(String code, String text, String syscall, Path p, Throwable cause) {
		JSRuntimeException ex = cause != null
				? RuntimeUtil.error(cause, "{0}: {1}, {2} '{3}'", code, text, syscall, p)
				: RuntimeUtil.error("{0}: {1}, {2} '{3}'", code, text, syscall, p);
		if (ex.getJavascriptException() instanceof JSObject o) {
			o.setOwnProperty("code", code);
			o.setOwnProperty("syscall", syscall);
			o.setOwnProperty("path", p.toString());
		}
		return ex;
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
		if (Files.isDirectory(p)) {
			throw fsError("EISDIR", "illegal operation on a directory", "read", p, null);
		}
		try {
			byte[] bytes = Files.readAllBytes(p);
			// Node.js: when encoding is omitted, returns a Buffer. We don't
			// have Buffer yet, so default to utf-8 String — this matches what
			// most tests actually assert.
			return decode(bytes, options);
		} catch (IOException e) {
			throw fsError(e, "open", p);
		}
	}

	static void writeFile(Path p, Object data, Object options) {
		byte[] bytes = toBytes(data, options);
		if (Files.isDirectory(p)) {
			throw fsError("EISDIR", "illegal operation on a directory", "open", p, null);
		}
		try {
			Files.write(p, bytes);
		} catch (IOException e) {
			throw fsError(e, "open", p);
		}
	}

	static void appendFile(Path p, Object data, Object options) {
		byte[] bytes = toBytes(data, options);
		if (Files.isDirectory(p)) {
			throw fsError("EISDIR", "illegal operation on a directory", "open", p, null);
		}
		try {
			Files.write(p, bytes,
					StandardOpenOption.CREATE,
					StandardOpenOption.WRITE,
					StandardOpenOption.APPEND);
		} catch (IOException e) {
			throw fsError(e, "open", p);
		}
	}

	private static byte[] toBytes(Object data, Object options) {
		if (data instanceof byte[] b) return b;
		if (data instanceof CharSequence s) return encode(s.toString(), options);
		return encode(String.valueOf(data), options);
	}

	/* ------------------------------------------------------------------ *
	 * Stat / exists / realpath                                           *
	 * ------------------------------------------------------------------ */

	static boolean exists(Path p) {
		return Files.exists(p);
	}

	// stats.isFile() & co are methods, as in Node
	private static final class StatFlag extends BaseMethod {
		private final boolean value;
		StatFlag(JSEnvironment env, String name, boolean value) {
			super(env, name, 0);
			this.value = value;
		}
		@Override
		protected Object invoke(Object _this, Object[] parameters) {
			return value;
		}
	}

	static JSObject stat(JSEnvironment env, Path p) {
		try {
			BasicFileAttributes a = Files.readAttributes(p, BasicFileAttributes.class);
			// stat() follows symbolic links: whether p itself is one
			boolean link = Files.isSymbolicLink(p);
			JSObject o = JSObject.create(env);
			o.setOwnProperty("size", a.size());
			o.setOwnMethod(new StatFlag(env, "isFile", a.isRegularFile()));
			o.setOwnMethod(new StatFlag(env, "isDirectory", a.isDirectory()));
			o.setOwnMethod(new StatFlag(env, "isSymbolicLink", link));
			o.setOwnProperty("mtimeMs", (double) a.lastModifiedTime().toMillis());
			o.setOwnProperty("atimeMs", (double) a.lastAccessTime().toMillis());
			o.setOwnProperty("ctimeMs", (double) a.creationTime().toMillis());
			o.setOwnProperty("birthtimeMs", (double) a.creationTime().toMillis());
			return o;
		} catch (IOException e) {
			throw fsError(e, "stat", p);
		}
	}

	static String realpath(Path p) {
		try {
			return p.toRealPath().toString();
		} catch (IOException e) {
			throw fsError(e, "realpath", p);
		}
	}

	static void access(Path p) {
		if (!Files.exists(p)) {
			throw fsError("ENOENT", "no such file or directory", "access", p, null);
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
		} catch (IOException e) {
			throw fsError(e, "scandir", p);
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
			throw fsError(e, "mkdir", p);
		}
	}

	static void rm(Path p, Object options) {
		boolean recursive = recursiveOf(options);
		boolean force = forceOf(options);
		try {
			if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
				if (force) return;
				throw fsError("ENOENT", "no such file or directory", "rm", p, null);
			}
			if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
				if (!recursive) {
					throw fsError("EISDIR", "illegal operation on a directory", "rm", p, null);
				}
				deleteTree(p);
			} else {
				Files.delete(p);
			}
		} catch (IOException e) {
			throw fsError(e, "rm", p);
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
		} catch (IOException e) {
			throw fsError(e, "unlink", p);
		}
	}

	static void rename(Path from, Path to) {
		try {
			Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw fsError(e, "rename", from);
		}
	}

	static void copyFile(Path from, Path to) {
		try {
			Files.copy(from, to, new CopyOption[]{StandardCopyOption.REPLACE_EXISTING});
		} catch (IOException e) {
			throw fsError(e, "copyfile", from);
		}
	}
}
