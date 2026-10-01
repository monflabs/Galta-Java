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
package org.monflabs.playground;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import org.monflabs.util.StringUtil;
import org.monflabs.util.path.FilesUtil;

/**
 * Writes the in-memory files of a snippet back to its folder.
 * <ul>
 * <li>Only the text files are saved: a binary file (see
 * {@link ExecutionContext#isText(byte[])}) cannot be edited, so it is left
 * alone.</li>
 * <li>A file is only written when its content changed, and atomically (a
 * temporary file next to it, then moved over it): an interrupted save never
 * leaves a truncated file.</li>
 * <li>The line breaks are normalized, and a file that used CRLF line breaks
 * keeps them.</li>
 * <li>A file modified on disk since it was loaded is a conflict: nothing is
 * written unless the save is forced.</li>
 * </ul>
 */
public class SnippetStorage {

	/**
	 * What a save did, or would have done.
	 */
	public static final class SaveResult {
		private final List<Path> saved;
		private final List<Path> conflicts;
		private final List<Path> skippedBinary;

		SaveResult(List<Path> saved, List<Path> conflicts, List<Path> skippedBinary) {
			this.saved = Collections.unmodifiableList(saved);
			this.conflicts = Collections.unmodifiableList(conflicts);
			this.skippedBinary = Collections.unmodifiableList(skippedBinary);
		}
		/**
		 * The physical files written.
		 */
		public List<Path> getSaved() {
			return saved;
		}
		/**
		 * The physical files modified on disk since they were loaded: when not
		 * empty, nothing was written.
		 */
		public List<Path> getConflicts() {
			return conflicts;
		}
		public boolean hasConflicts() {
			return !conflicts.isEmpty();
		}
		/**
		 * The in-memory files not saved because they are not text.
		 */
		public List<Path> getSkippedBinary() {
			return skippedBinary;
		}
	}

	private final MemoryFileSystemSnippet fs;
	private final Path folder;

	/**
	 * @param fs the in-memory files
	 * @param folder the snippet folder, where the files without a physical file are created
	 */
	public SnippetStorage(MemoryFileSystemSnippet fs, Path folder) {
		this.fs = fs;
		this.folder = folder;
	}

	public SnippetStorage(ExecutionContext context) {
		this(context.getSnippetFs(), context.getSnippet().getFolder());
	}

	/**
	 * Saves the modified text files.
	 *
	 * @param force write even the files modified on disk since they were loaded
	 * @throws IOException when the folder is read-only or a file cannot be
	 * written - the files written before stay written
	 */
	public SaveResult save(boolean force) throws IOException {
		if(folder.getFileSystem().isReadOnly()) {
			throw new IOException("The snippet folder is read-only: "+folder);
		}
		List<Path> memFiles = new ArrayList<>();
		try (Stream<Path> stream = Files.list(FilesUtil.getRoot(fs))) {
			stream.filter(Files::isRegularFile).filter(Snippet::isVisible).forEach(memFiles::add);
		}
		memFiles.sort(java.util.Comparator.comparing(Path::toString));

		List<Path> binary = new ArrayList<>();
		List<Path> conflicts = new ArrayList<>();
		List<Pending> pending = new ArrayList<>();
		for(Path memFile: memFiles) {
			byte[] content = fs.readBytes(memFile);
			if(content==null) {
				continue;	// deleted meanwhile
			}
			if(!ExecutionContext.isText(content)) {
				binary.add(memFile);
				continue;
			}
			Path target = fs.getPhysicalFile(memFile);
			if(target==null) {
				// A file that only exists in memory, like the README.md generated
				// for a folder: saved next to the snippet files
				target = folder.resolve(memFile.getFileName().toString());
			}
			byte[] onDisk = Files.isRegularFile(target) ? Files.readAllBytes(target) : null;
			byte[] loaded = fs.getLoadedContent(memFile);
			if(!force && onDisk!=null && loaded!=null && !Arrays.equals(onDisk, loaded)) {
				conflicts.add(target);
				continue;
			}
			byte[] newContent = toFileContent(new String(content, StandardCharsets.UTF_8), loaded!=null ? loaded : onDisk);
			if(!Arrays.equals(newContent, onDisk)) {
				pending.add(new Pending(memFile, target, newContent));
			} else {
				// Same as on disk: the reference becomes the disk content
				fs.setPhysicalFile(memFile, target, onDisk);
			}
		}
		if(!conflicts.isEmpty()) {
			return new SaveResult(List.of(), conflicts, binary);
		}

		List<Path> saved = new ArrayList<>();
		for(Pending p: pending) {
			writeAtomically(p.target, p.content);
			fs.setPhysicalFile(p.memFile, p.target, p.content);
			saved.add(p.target);
		}
		return new SaveResult(saved, List.of(), binary);
	}

	private record Pending(Path memFile, Path target, byte[] content) {}

	/**
	 * The bytes to write for a text: line breaks normalized, CRLF when the
	 * reference content (the file as it was) used CRLF line breaks.
	 */
	static byte[] toFileContent(String text, byte[] reference) {
		String s = StringUtil.normalizeLineBreaks(text);
		if(reference!=null && usesCrlf(reference)) {
			s = s.replace("\n", "\r\n");
		}
		return s.getBytes(StandardCharsets.UTF_8);
	}

	/**
	 * Whether the content's line breaks are CRLF (the majority of them).
	 */
	static boolean usesCrlf(byte[] content) {
		int crlf = 0, lf = 0;
		for(int i=0; i<content.length; i++) {
			if(content[i]=='\n') {
				if(i>0 && content[i-1]=='\r') {
					crlf++;
				} else {
					lf++;
				}
			}
		}
		return crlf>lf;
	}

	/**
	 * Writes a file through a temporary file in the same folder, moved over
	 * the target: readers see the old content or the new one, and a failure
	 * leaves the old file intact.
	 */
	public static void writeAtomically(Path target, byte[] content) throws IOException {
		Path dir = target.toAbsolutePath().getParent();
		// Not Files.createTempFile(): its owner-only permissions would end up
		// on the snippet file
		Path tmp = dir.resolve("."+target.getFileName().toString()+"."+Long.toHexString(ThreadLocalRandom.current().nextLong())+".tmp");
		try {
			Files.write(tmp, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
			try {
				Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch(AtomicMoveNotSupportedException e) {
				Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(tmp);
		}
	}
}
