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
package org.monflabs.json.impexp.replication.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.impexp.replication.ReplicationResult;
import org.monflabs.json.impexp.replication.ReplicationTable;
import org.monflabs.util.path.FilesUtil;

/**
 * Replication table persisted as a JSON file.
 * <p>
 * The file holds the last replication date of every source/target pair:
 * <pre>
 * { "source1": { "target1": "2026-01-01T10:00:00Z", "target2": "..." }, ... }
 * </pre>
 * The file is read and rewritten on every access, so a table instance can be shared by
 * several targets. An update (read-modify-write) is done while holding a lock on the
 * file: an exclusive {@link FileLock} on a companion <code>.lock</code> file, which
 * serializes the updates of all the tables using that file, including from other
 * processes. The file is replaced atomically (written to a temporary file, then moved),
 * so a crash while saving cannot leave a truncated table. An empty file is an empty
 * table.
 */
public class FileReplicationTable implements ReplicationTable {
	
	private final Path file;
	private final Object monitor;
	
	public FileReplicationTable(Path file) {
		if(file==null) {
			throw new IllegalArgumentException("The replication file cannot be null");
		}
		this.file = file.toAbsolutePath().normalize();
		this.monitor = MONITORS.computeIfAbsent(this.file, (f) -> new Object());
	}
	
	public Path getFile() {
		return file;
	}

	@Override
	public Instant lastReplication(String source, String target) {
		OffsetDateTime dt = getLastReplication(source, target);
		return dt!=null ? dt.toInstant() : null;
	}

	@Override
	public void saveReplication(String source, String target, Instant newLastRep, ReplicationResult result) {
		setReplication(source, target, newLastRep!=null ? newLastRep.atOffset(ZoneOffset.UTC) : null);
	}

	public OffsetDateTime getLastReplication(String source, String target) {
		synchronized(monitor) {
			return readLastReplication(source, target);
		}
	}
	private OffsetDateTime readLastReplication(String source, String target) {
		JsonObject o = loadTable();
		JsonObject targets = o.get(key(source)) instanceof JsonObject t ? t : null;
		if(targets!=null && targets.get(key(target))!=null) {
			return targets.getOffsetDateTime(key(target));
		}
		return null;
	}
	
	public void setReplication(String source, String target, OffsetDateTime timestamp) {
		withLock(() -> {
			updateTable(source, target, timestamp);
			return null;
		});
	}
	private void updateTable(String source, String target, OffsetDateTime timestamp) {
		JsonObject o = loadTable();
		JsonObject targets = o.get(key(source)) instanceof JsonObject t ? t : null;
		if(targets==null) {
			targets = JsonObject.create();
			o.put(key(source), targets);
		}
		if(timestamp!=null) {
			targets.put(key(target), timestamp);
		} else {
			targets.remove(key(target));
		}
		saveTable(o);
	}

	// JSON keys cannot be null
	private static String key(String id) {
		return id!=null ? id : "";
	}
	
	private JsonObject loadTable() {
		if(Files.exists(file)) {
			String content = FilesUtil.readString(file,StandardCharsets.UTF_8);
			if(!content.isBlank()) {
				return JsonObject.parse(content);
			}
		}
		return JsonObject.create();
	}

	// The tables of a JVM using the same file share a monitor: a FileLock is held by the
	// process, it does not serialize the threads of a process
	private static final Map<Path,Object> MONITORS = new ConcurrentHashMap<>();

	private <T> T withLock(Supplier<T> action) {
		synchronized(monitor) {
			Path lockFile = file.resolveSibling(file.getFileName().toString()+".lock");
			try {
				Path parent = file.getParent();
				if(parent!=null && !Files.exists(parent)) {
					Files.createDirectories(parent);
				}
				try(FileChannel ch = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
					FileLock lock = ch.lock()) {
					return action.get();
				}
			} catch(IOException e) {
				throw new JsonException(e,"Error while locking replication file {0}",file);
			}
		}
	}
	
	private void saveTable(JsonObject table) {
		try {
			Path parent = file.getParent();
			if(parent!=null && !Files.exists(parent)) {
				Files.createDirectories(parent);
			}
			writeAtomically(file, table.stringify());
		} catch (Exception e) {
			throw new JsonException(e,"Error while saving replication file {0}",file);
		}
	}
	
	private static void writeAtomically(Path file, String content) throws IOException {
		Path tmp = Files.createTempFile(file.getParent(), file.getFileName().toString()+".", ".tmp");
		try {
			Files.writeString(tmp, content, StandardCharsets.UTF_8);
			try {
				Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch(AtomicMoveNotSupportedException e) {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(tmp);
		}
	}
}
