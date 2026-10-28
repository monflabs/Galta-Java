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
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

/**
 * A table model that loads its rows lazily from a {@link VirtualDataSource}.
 * <p>
 * Rows are fetched by pages on a background thread, when a cell of the page
 * is first asked for (that is, when it becomes visible), and the next page is
 * prefetched. Only a bounded number of pages is kept: the least recently used
 * ones are dropped and fetched again if needed. A cell whose page is not yet
 * loaded reads as {@link #LOADING}.
 * <p>
 * The model is used from the event dispatch thread, like any Swing model; the
 * source is only called from the model's own thread.
 */
@SuppressWarnings("serial")
public class VirtualTableModel extends AbstractTableModel {

	/** The value of a cell whose page is being loaded. */
	public static final Object LOADING = new Placeholder("…");

	/** The value of a cell whose page failed to load (see {@link #retry()}). */
	public static final Object FAILED = new Placeholder("⚠");

	private static final class Placeholder {
		private final String text;
		Placeholder(String text) {
			this.text = text;
		}
		@Override
		public String toString() {
			return text;
		}
	}

	public static final int DEFAULT_PAGE_SIZE = 200;
	public static final int DEFAULT_MAX_PAGES = 100;

	public enum State {
		/** No source. */
		EMPTY,
		/** The source is opening or a page is loading. */
		LOADING,
		/** Nothing is being loaded. */
		READY,
		/** The source or a page failed: see {@link Status#error()}. */
		ERROR
	}

	/**
	 * @param loadedRows the rows known so far
	 * @param totalRows  the total number of rows, or {@link VirtualDataSource#UNKNOWN}
	 * @param complete   true when the end of the data is reached (loadedRows is the total)
	 */
	public record Status(State state, long loadedRows, long totalRows, boolean complete, Throwable error) {
	}

	private VirtualDataSource source;
	private List<VirtualColumn> columns = List.of();
	private long totalRows = VirtualDataSource.UNKNOWN;
	private long knownRows;
	private boolean complete;
	private boolean opening;
	private Throwable error;

	private int pageSize = DEFAULT_PAGE_SIZE;
	private int maxPages = DEFAULT_MAX_PAGES;
	private final Map<Integer,Object[][]> pages = new LinkedHashMap<>(16, 0.75f, true);
	private final Set<Integer> loading = new HashSet<>();
	private final Set<Integer> failed = new HashSet<>();

	private boolean checkable;
	private final Set<Long> checked = new TreeSet<>();

	// Bumped when the source changes: the results of the previous one are ignored
	private int generation;
	private ExecutorService executor;
	private final List<Consumer<Status>> listeners = new ArrayList<>();

	public VirtualTableModel() {
	}

	// ---- Source

	/**
	 * Shows the rows of a new source (null to empty the table). The previous
	 * source is closed once its pending calls are done. The model owns the
	 * source from now on.
	 */
	public void setDataSource(VirtualDataSource newSource) {
		checkEdt();
		VirtualDataSource old = source;
		generation++;
		source = newSource;
		columns = List.of();
		totalRows = VirtualDataSource.UNKNOWN;
		knownRows = 0;
		complete = newSource==null;
		error = null;
		opening = newSource!=null;
		pages.clear();
		loading.clear();
		failed.clear();
		checked.clear();
		fireTableStructureChanged();
		if(old!=null) {
			// After the calls of the old source still queued
			executor().execute(old::close);
		}
		fireStatus();
		if(newSource!=null) {
			open(newSource, generation);
		}
	}

	public VirtualDataSource getDataSource() {
		return source;
	}

	private void open(VirtualDataSource src, int gen) {
		executor().execute(() -> {
			if(gen!=generation) {
				return;
			}
			List<VirtualColumn> cols;
			long total;
			try {
				cols = List.copyOf(src.getColumns());
				total = src.getRowCount();
			} catch(Throwable t) {
				SwingUtilities.invokeLater(() -> {
					if(gen==generation) {
						opening = false;
						error = t;
						complete = true;
						fireStatus();
					}
				});
				return;
			}
			SwingUtilities.invokeLater(() -> {
				if(gen!=generation) {
					return;
				}
				columns = cols;
				totalRows = total;
				complete = total==0;
				opening = false;
				fireTableStructureChanged();
				fireStatus();
				if(!complete) {
					requestPage(0);
				}
			});
		});
	}

	/** Closes the source and stops the loading thread. The model is not usable afterwards. */
	public void dispose() {
		checkEdt();
		VirtualDataSource old = source;
		generation++;
		source = null;
		if(executor!=null) {
			if(old!=null) {
				executor.execute(old::close);
			}
			executor.shutdown();
		} else if(old!=null) {
			old.close();
		}
	}

	private ExecutorService executor() {
		if(executor==null) {
			executor = Executors.newSingleThreadExecutor(r -> {
				Thread t = new Thread(r, "VirtualTableModel");
				t.setDaemon(true);
				return t;
			});
		}
		return executor;
	}

	/** What {@link #readRows(int, BiConsumer)} read: the rows, and whether there were more. */
	public record Rows(List<VirtualColumn> columns, List<Object[]> rows, boolean truncated) {
	}

	/**
	 * Reads the rows of the source from the first one, at most <code>max</code>
	 * (all of them for a negative value), whatever the ones displayed. It runs
	 * with the other calls to the source, one page at a time. <code>done</code>
	 * is called on the event dispatch thread, with the rows or the error, unless
	 * the source was replaced meanwhile.
	 */
	public void readRows(int max, BiConsumer<Rows, Throwable> done) {
		checkEdt();
		VirtualDataSource src = source;
		if(src==null || opening) {
			done.accept(new Rows(columns, List.of(), false), null);
			return;
		}
		int gen = generation;
		int size = pageSize;
		List<VirtualColumn> cols = columns;
		executor().execute(() -> {
			List<Object[]> rows = new ArrayList<>();
			boolean truncated = false;
			try {
				while(gen==generation) {
					// One more than the maximum, to know whether there is more
					int want = max<0 ? size : (int)Math.min(size, (long)max+1-rows.size());
					if(want<=0) {
						break;
					}
					List<Object[]> page = src.fetch(rows.size(), want);
					if(page==null || page.isEmpty()) {
						break;
					}
					rows.addAll(page);
					if(page.size()<want) {
						break;
					}
				}
			} catch(Throwable t) {
				SwingUtilities.invokeLater(() -> {
					if(gen==generation) {
						done.accept(null, t);
					}
				});
				return;
			}
			if(max>=0 && rows.size()>max) {
				truncated = true;
				rows = new ArrayList<>(rows.subList(0, max));
			}
			Rows result = new Rows(cols, rows, truncated);
			SwingUtilities.invokeLater(() -> {
				if(gen==generation) {
					done.accept(result, null);
				}
			});
		});
	}

	// ---- Options

	public int getPageSize() {
		return pageSize;
	}

	/** The number of rows fetched at once (it applies to the next source). */
	public void setPageSize(int pageSize) {
		this.pageSize = Math.max(1, pageSize);
	}

	public int getMaxPages() {
		return maxPages;
	}

	/** The number of pages kept in memory; at least 3 (current, previous, prefetched). */
	public void setMaxPages(int maxPages) {
		this.maxPages = Math.max(3, maxPages);
	}

	public boolean isCheckable() {
		return checkable;
	}

	/** Adds a first column of check boxes to select rows (see {@link #getCheckedRows()}). */
	public void setCheckable(boolean checkable) {
		if(this.checkable!=checkable) {
			this.checkable = checkable;
			fireTableStructureChanged();
		}
	}

	/** The indexes (in the data source) of the checked rows, ascending. */
	public List<Long> getCheckedRows() {
		return List.copyOf(checked);
	}

	public void clearChecked() {
		if(!checked.isEmpty()) {
			checked.clear();
			if(getRowCount()>0) {
				fireTableRowsUpdated(0, getRowCount()-1);
			}
		}
	}

	/**
	 * The values of a row, or null if its page is not loaded (it never loads
	 * it: use {@link #getValueAt(int, int)} for that).
	 */
	public Object[] getRow(int row) {
		Object[][] p = pages.get(row/pageSize);
		int i = row%pageSize;
		return p!=null && i<p.length ? p[i] : null;
	}

	// ---- Status

	public Status getStatus() {
		State s;
		if(source==null) {
			s = State.EMPTY;
		} else if(error!=null || !failed.isEmpty()) {
			s = State.ERROR;
		} else if(opening || !loading.isEmpty()) {
			s = State.LOADING;
		} else {
			s = State.READY;
		}
		Throwable e = error;
		return new Status(s, knownRows, totalRows, complete, e);
	}

	/** Called on the event dispatch thread each time the status changes. */
	public void addStatusListener(Consumer<Status> l) {
		listeners.add(l);
	}

	public void removeStatusListener(Consumer<Status> l) {
		listeners.remove(l);
	}

	private void fireStatus() {
		Status s = getStatus();
		for(Consumer<Status> l: new ArrayList<>(listeners)) {
			l.accept(s);
		}
	}

	/** Fetches again the pages that failed. */
	public void retry() {
		checkEdt();
		if(!failed.isEmpty()) {
			failed.clear();
			error = null;
			if(getRowCount()>0) {
				fireTableRowsUpdated(0, getRowCount()-1);
			}
			fireStatus();
		}
	}

	// ---- Paging

	/** True if the page is in memory. */
	public boolean isPageLoaded(int page) {
		return pages.containsKey(page);
	}

	private boolean pageExists(int page) {
		if(complete) {
			return (long)page*pageSize<knownRows;
		}
		return totalRows<0 || (long)page*pageSize<totalRows;
	}

	private void requestPage(int page) {
		if(source==null || opening || page<0 || !pageExists(page) || pages.containsKey(page) || loading.contains(page) || failed.contains(page)) {
			return;
		}
		loading.add(page);
		fireStatus();
		VirtualDataSource src = source;
		int gen = generation;
		int size = pageSize;
		executor().execute(() -> {
			if(gen!=generation) {
				return;
			}
			List<Object[]> rows;
			try {
				rows = src.fetch(page*size, size);
			} catch(Throwable t) {
				SwingUtilities.invokeLater(() -> pageFailed(gen, page, t));
				return;
			}
			List<Object[]> result = rows!=null ? rows : List.of();
			SwingUtilities.invokeLater(() -> pageLoaded(gen, page, result));
		});
	}

	private void pageFailed(int gen, int page, Throwable t) {
		if(gen!=generation) {
			return;
		}
		loading.remove(page);
		failed.add(page);
		error = t;
		int first = page*pageSize;
		fireTableRowsUpdated(first, Math.max(first, Math.min(getRowCount(), first+pageSize)-1));
		fireStatus();
	}

	private void pageLoaded(int gen, int page, List<Object[]> rows) {
		if(gen!=generation) {
			return;
		}
		loading.remove(page);
		int oldCount = getRowCount();
		long offset = (long)page*pageSize;
		pages.put(page, rows.toArray(new Object[0][]));
		// A page can be dropped at once if the cache is full of newer ones: never the page just loaded
		while(pages.size()>maxPages) {
			Integer eldest = pages.keySet().iterator().next();
			if(eldest==page) {
				break;
			}
			pages.remove(eldest);
		}
		long end = offset+rows.size();
		if(end>knownRows) {
			knownRows = end;
		}
		if(rows.size()<pageSize) {
			// The end of the data, whatever the source announced
			complete = true;
			totalRows = end;
			knownRows = end;
		}
		int newCount = getRowCount();
		if(newCount>oldCount) {
			fireTableRowsInserted(oldCount, newCount-1);
		} else if(newCount<oldCount) {
			fireTableRowsDeleted(newCount, oldCount-1);
		}
		if(!rows.isEmpty()) {
			int first = (int)offset;
			int last = Math.min(newCount, first+rows.size())-1;
			if(first<=Math.min(last, oldCount-1)) {
				// Rows that were already displayed as LOADING
				fireTableRowsUpdated(first, Math.min(last, oldCount-1));
			}
		}
		fireStatus();
	}

	// ---- TableModel

	private int columnOffset() {
		return checkable ? 1 : 0;
	}

	@Override
	public int getRowCount() {
		long n = complete || totalRows<0 ? knownRows : Math.max(totalRows, knownRows);
		return (int)Math.min(n, Integer.MAX_VALUE);
	}

	@Override
	public int getColumnCount() {
		return columns.size()+columnOffset();
	}

	@Override
	public String getColumnName(int column) {
		if(checkable && column==0) {
			return "Select";
		}
		return columns.get(column-columnOffset()).name();
	}

	@Override
	public Class<?> getColumnClass(int column) {
		if(checkable && column==0) {
			return Boolean.class;
		}
		return columns.get(column-columnOffset()).type();
	}

	@Override
	public boolean isCellEditable(int row, int column) {
		return checkable && column==0;
	}

	@Override
	public void setValueAt(Object value, int row, int column) {
		if(checkable && column==0) {
			if(Boolean.TRUE.equals(value)) {
				checked.add((long)row);
			} else {
				checked.remove((long)row);
			}
			fireTableCellUpdated(row, column);
		}
	}

	@Override
	public Object getValueAt(int row, int column) {
		if(checkable && column==0) {
			return checked.contains((long)row);
		}
		int page = row/pageSize;
		Object[][] p = pages.get(page);
		// Prefetch the next page: the user is likely to scroll down
		requestPage(page+1);
		if(p==null) {
			requestPage(page);
			return failed.contains(page) ? FAILED : LOADING;
		}
		int i = row%pageSize;
		int c = column-columnOffset();
		if(i>=p.length) {
			return LOADING;
		}
		Object[] values = p[i];
		return c<values.length ? values[c] : null;
	}

	private static void checkEdt() {
		if(!SwingUtilities.isEventDispatchThread()) {
			throw new IllegalStateException("To be called on the event dispatch thread");
		}
	}

	/** The columns of the current source (empty until it is opened). */
	public List<VirtualColumn> getColumns() {
		return Collections.unmodifiableList(columns);
	}
}
