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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

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
 * The file is read and rewritten on every access, and the methods are synchronized
 * on the table instance, so a table instance can be shared by several targets. The
 * file is replaced atomically (written to a temporary file, then moved), so a crash
 * while saving cannot leave a truncated table.
 */
public class FileReplicationTable implements ReplicationTable {
	
	private final Path file;
	
	public FileReplicationTable(Path file) {
		if(file==null) {
			throw new IllegalArgumentException("The replication file cannot be null");
		}
		this.file = file.toAbsolutePath();
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

	public synchronized OffsetDateTime getLastReplication(String source, String target) {
		JsonObject o = loadTable();
		JsonObject targets = o.get(key(source)) instanceof JsonObject t ? t : null;
		if(targets!=null && targets.get(key(target))!=null) {
			return targets.getOffsetDateTime(key(target));
		}
		return null;
	}
	
	public synchronized void setReplication(String source, String target, OffsetDateTime timestamp) {
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
			return JsonObject.parse(content);
		}
		return JsonObject.create();
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
