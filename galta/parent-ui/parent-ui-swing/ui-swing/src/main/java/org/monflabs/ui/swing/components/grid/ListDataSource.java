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
import java.util.Collection;
import java.util.List;

import javax.swing.SortOrder;

/**
 * A source over rows already in memory (a CSV file, a parsed JSON array, ...).
 * The table still only renders the visible rows.
 */
public class ListDataSource implements SortableDataSource {

	private final List<VirtualColumn> columns;
	private final List<Object[]> rows;
	// The rows in their original order, when this source is a sorted view of them
	private final List<Object[]> original;

	public ListDataSource(List<VirtualColumn> columns, List<Object[]> rows) {
		this(columns, rows, rows);
	}

	private ListDataSource(List<VirtualColumn> columns, List<Object[]> rows, List<Object[]> original) {
		this.columns = List.copyOf(columns);
		this.rows = rows;
		this.original = original;
	}

	@Override
	public boolean isSortable(int column) {
		return column>=0 && column<columns.size();
	}

	/**
	 * The rows ordered by a column: numbers and other comparable values by
	 * their value, the rest by their text; empty values last.
	 */
	@Override
	public ListDataSource sorted(int column, SortOrder order) {
		if(order==SortOrder.UNSORTED || !isSortable(column)) {
			return new ListDataSource(columns, original, original);
		}
		List<Object[]> copy = new ArrayList<>(original);
		boolean ascending = order==SortOrder.ASCENDING;
		copy.sort((a, b) -> {
			Object x = column<a.length ? a[column] : null;
			Object y = column<b.length ? b[column] : null;
			if(x==null || y==null) {
				// Empty values stay last in both directions
				return x==y ? 0 : x==null ? 1 : -1;
			}
			int c = compare(x, y);
			return ascending ? c : -c;
		});
		return new ListDataSource(columns, copy, original);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static int compare(Object x, Object y) {
		if(x instanceof String sx && y instanceof String sy) {
			int c = sx.compareToIgnoreCase(sy);
			return c!=0 ? c : sx.compareTo(sy);
		}
		if(x instanceof Comparable cx && x.getClass()==y.getClass()) {
			return cx.compareTo(y);
		}
		if(x instanceof Number nx && y instanceof Number ny) {
			return Double.compare(nx.doubleValue(), ny.doubleValue());
		}
		return x.toString().compareToIgnoreCase(y.toString());
	}

	/**
	 * Rows given as collections (each value in the column order), with
	 * untyped columns.
	 */
	public static ListDataSource ofRows(List<String> columnNames, Collection<? extends Collection<?>> rows) {
		List<VirtualColumn> columns = new ArrayList<>();
		for(String n: columnNames) {
			columns.add(new VirtualColumn(n));
		}
		List<Object[]> list = new ArrayList<>(rows.size());
		for(Collection<?> r: rows) {
			list.add(r.toArray());
		}
		return new ListDataSource(columns, list);
	}

	@Override
	public List<VirtualColumn> getColumns() {
		return columns;
	}

	@Override
	public long getRowCount() {
		return rows.size();
	}

	@Override
	public List<Object[]> fetch(int offset, int limit) {
		if(offset>=rows.size()) {
			return List.of();
		}
		return new ArrayList<>(rows.subList(offset, Math.min(rows.size(), offset+limit)));
	}
}
