/*
 * Copyright (c) 2023-2026 Philippe Riand
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
package org.monflabs.ui.swing.components.grid;

import java.util.ArrayList;
import java.util.List;

/**
 * Base of the sources that can only be read forward: a JDBC cursor, a query
 * whose results come in batches (<code>nextRecordsUrl</code>), a stream.
 * <p>
 * The table scrolls randomly, so the rows read so far are kept in memory and
 * the cursor is advanced only when a page beyond them is needed. Subclasses
 * only implement {@link #nextBatch()}; override {@link #getColumns()} and
 * optionally {@link #getRowCount()}.
 */
public abstract class SequentialDataSource implements VirtualDataSource {

	private final List<Object[]> buffer = new ArrayList<>();
	private boolean exhausted;

	/**
	 * The next rows of the cursor, or null or empty at its end. Called again
	 * only while the table needs more rows.
	 */
	protected abstract List<Object[]> nextBatch() throws Exception;

	@Override
	public synchronized List<Object[]> fetch(int offset, int limit) throws Exception {
		long needed = (long)offset+limit;
		while(buffer.size()<needed && !exhausted) {
			List<Object[]> batch = nextBatch();
			if(batch==null || batch.isEmpty()) {
				exhausted = true;
			} else {
				buffer.addAll(batch);
			}
		}
		if(offset>=buffer.size()) {
			return List.of();
		}
		return new ArrayList<>(buffer.subList(offset, Math.min(buffer.size(), offset+limit)));
	}

	@Override
	public synchronized void close() {
		buffer.clear();
		exhausted = true;
	}
}
