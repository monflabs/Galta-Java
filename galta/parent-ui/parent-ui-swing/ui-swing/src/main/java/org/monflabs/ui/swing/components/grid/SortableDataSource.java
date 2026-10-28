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

import javax.swing.SortOrder;

/**
 * A {@link VirtualDataSource} that can deliver its rows ordered by a column.
 * <p>
 * The rows of a {@link VirtualTable} come page by page, so the table can't
 * sort what it has: the order has to be given by the source (an
 * <code>ORDER BY</code>, a sort of the list, ...). When the user asks for an
 * order (see {@link VirtualTable#setSortable(boolean)}), the table asks the
 * source for another source with that order and shows it, closing the first.
 */
public interface SortableDataSource extends VirtualDataSource {

	/**
	 * Whether the rows can be ordered by a column (an index in
	 * {@link #getColumns()}). Called on the event dispatch thread: it must not
	 * block.
	 */
	boolean isSortable(int column);

	/**
	 * A source of the same rows in another order: by <code>column</code>, or
	 * the original order of this source for {@link SortOrder#UNSORTED}. It is
	 * called on the event dispatch thread and must return quickly: the rows are
	 * to be read later, by the new source, from its own thread. It orders from
	 * the original rows, whatever order this source has, and leaves this source
	 * usable (the table closes it afterwards).
	 */
	VirtualDataSource sorted(int column, SortOrder order);
}
