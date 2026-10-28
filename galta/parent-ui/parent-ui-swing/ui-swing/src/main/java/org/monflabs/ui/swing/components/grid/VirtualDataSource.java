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

import java.util.List;

/**
 * The rows behind a {@link VirtualTable}, fetched a page at a time.
 * <p>
 * All the methods are called from a background thread, one call at a time,
 * never from the event dispatch thread: they may block (a query, a network
 * call). {@link #getColumns()} is called first, then {@link #getRowCount()},
 * then {@link #fetch(int, int)} as the user scrolls. A source is used by one
 * table only.
 * <p>
 * Ready-made sources: {@link ListDataSource} (rows in memory: CSV, JSON, ...),
 * {@link SequentialDataSource} (a cursor read forward: queries, paged APIs) and
 * {@link ResultSetDataSource} (JDBC).
 */
public interface VirtualDataSource extends AutoCloseable {

	/** The value of {@link #getRowCount()} when the number of rows is not known up front. */
	long UNKNOWN = -1;

	List<VirtualColumn> getColumns() throws Exception;

	/**
	 * The total number of rows, or {@link #UNKNOWN}. When unknown, the table
	 * grows as the user scrolls, until a fetch returns fewer rows than asked.
	 */
	default long getRowCount() throws Exception {
		return UNKNOWN;
	}

	/**
	 * The rows from <code>offset</code> (0 based), at most <code>limit</code>.
	 * Fewer rows (or none) means the end of the data. Each row has one value
	 * per column.
	 */
	List<Object[]> fetch(int offset, int limit) throws Exception;

	/** Releases the resources (a result set, a connection). */
	@Override
	default void close() {
	}
}
