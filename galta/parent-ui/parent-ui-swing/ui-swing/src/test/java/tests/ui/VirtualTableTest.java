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
package tests.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import javax.swing.SortOrder;
import javax.swing.SwingUtilities;

import org.monflabs.ui.swing.components.grid.ListDataSource;
import org.monflabs.ui.swing.components.grid.SequentialDataSource;
import org.monflabs.ui.swing.components.grid.TableExport;
import org.monflabs.ui.swing.components.grid.VirtualColumn;
import org.monflabs.ui.swing.components.grid.VirtualDataSource;
import org.monflabs.ui.swing.components.grid.VirtualTable;
import org.monflabs.ui.swing.components.grid.VirtualTableModel;

import tests.ProjectTestCase;

/**
 * Headless tests of the virtual table: lazy paging, unknown row counts,
 * sequential sources, errors and the check column.
 */
public class VirtualTableTest extends ProjectTestCase {

	private static final List<VirtualColumn> COLUMNS = List.of(new VirtualColumn("n", Integer.class), new VirtualColumn("s"));

	private static List<Object[]> rows(int count) {
		List<Object[]> rows = new ArrayList<>();
		for(int i=0; i<count; i++) {
			rows.add(new Object[] { i, "row "+i });
		}
		return rows;
	}

	private static void edt(Runnable r) throws Exception {
		SwingUtilities.invokeAndWait(r);
	}

	/** Lets the event queue run until the condition, evaluated on the EDT, holds. */
	private static void await(BooleanSupplier condition) throws Exception {
		long end = System.currentTimeMillis()+5000;
		boolean[] ok = new boolean[1];
		while(!ok[0]) {
			edt(() -> ok[0] = condition.getAsBoolean());
			if(!ok[0]) {
				assertTrue("Timeout", System.currentTimeMillis()<end);
				Thread.sleep(10);
			}
		}
	}

	/** Counts the fetches over a list source. */
	private static class CountingSource extends ListDataSource {
		final AtomicInteger fetches = new AtomicInteger();
		final long count;
		CountingSource(int rowCount, long announcedCount) {
			super(COLUMNS, rows(rowCount));
			this.count = announcedCount;
		}
		@Override
		public long getRowCount() {
			return count;
		}
		@Override
		public List<Object[]> fetch(int offset, int limit) {
			fetches.incrementAndGet();
			return super.fetch(offset, limit);
		}
	}

	public void testOnlyVisiblePagesAreFetched() throws Exception {
		CountingSource src = new CountingSource(100_000, 100_000);
		VirtualTableModel[] m = new VirtualTableModel[1];
		edt(() -> {
			m[0] = new VirtualTableModel();
			m[0].setPageSize(100);
			m[0].setDataSource(src);
		});
		await(() -> m[0].getRowCount()==100_000);
		assertEquals(2, m[0].getColumnCount());
		// The first page is loaded when the source opens; reading it prefetches page 1, nothing else
		await(() -> m[0].isPageLoaded(0));
		edt(() -> assertEquals("row 0", m[0].getValueAt(0, 1)));
		await(() -> m[0].isPageLoaded(1));
		edt(() -> assertEquals("row 5", m[0].getValueAt(5, 1)));
		assertEquals(2, src.fetches.get());

		// Jump to the end
		edt(() -> m[0].getValueAt(99_999, 0));
		await(() -> m[0].isPageLoaded(999));
		edt(() -> assertEquals(99_999, m[0].getValueAt(99_999, 0)));
		edt(m[0]::dispose);
	}

	public void testCacheIsBounded() throws Exception {
		VirtualTableModel[] m = new VirtualTableModel[1];
		edt(() -> {
			m[0] = new VirtualTableModel();
			m[0].setPageSize(10);
			m[0].setMaxPages(3);
			m[0].setDataSource(new CountingSource(1000, 1000));
		});
		await(() -> m[0].getRowCount()==1000);
		for(int page=0; page<20; page++) {
			int row = page*10;
			edt(() -> m[0].getValueAt(row, 0));
			await(() -> m[0].isPageLoaded(row/10));
		}
		edt(() -> {
			assertFalse(m[0].isPageLoaded(0));
			assertTrue(m[0].isPageLoaded(19));
			m[0].dispose();
		});
	}

	public void testUnknownRowCountGrowsUntilTheEnd() throws Exception {
		VirtualTableModel[] m = new VirtualTableModel[1];
		edt(() -> {
			m[0] = new VirtualTableModel();
			m[0].setPageSize(10);
			m[0].setDataSource(new CountingSource(25, VirtualDataSource.UNKNOWN));
		});
		// Page 0 and its prefetched next page are loaded only once rows are read
		await(() -> m[0].getRowCount()==10);
		edt(() -> m[0].getValueAt(9, 0));
		await(() -> m[0].getStatus().complete() || m[0].getRowCount()==20);
		edt(() -> m[0].getValueAt(19, 0));
		await(() -> m[0].getStatus().complete());
		edt(() -> {
			assertEquals(25, m[0].getRowCount());
			assertEquals(25, m[0].getStatus().totalRows());
			assertEquals(VirtualTableModel.State.READY, m[0].getStatus().state());
			m[0].dispose();
		});
	}

	public void testSequentialSourceReadsForwardOnce() throws Exception {
		AtomicInteger batches = new AtomicInteger();
		SequentialDataSource src = new SequentialDataSource() {
			int next;
			@Override
			public List<VirtualColumn> getColumns() {
				return COLUMNS;
			}
			@Override
			protected List<Object[]> nextBatch() {
				batches.incrementAndGet();
				if(next>=95) {
					return null;
				}
				List<Object[]> r = new ArrayList<>();
				for(int i=0; i<30 && next<95; i++, next++) {
					r.add(new Object[] { next, "row "+next });
				}
				return r;
			}
		};
		// Random access over a cursor: the end first, then the beginning
		List<Object[]> end = src.fetch(90, 10).stream().toList();
		assertEquals(5, end.size());
		assertEquals(90, end.get(0)[0]);
		assertEquals(10, src.fetch(0, 10).size());
		assertEquals(0, src.fetch(95, 10).size());
		assertEquals(5, batches.get()); // 4 batches of data, then the end, no re-read
	}

	public void testFailingSourceReportsTheError() throws Exception {
		VirtualTable[] t = new VirtualTable[1];
		List<VirtualTableModel.Status> statuses = new ArrayList<>();
		edt(() -> {
			t[0] = new VirtualTable();
			t[0].addStatusListener(statuses::add);
			t[0].setDataSource(new VirtualDataSource() {
				@Override
				public List<VirtualColumn> getColumns() {
					return COLUMNS;
				}
				@Override
				public List<Object[]> fetch(int offset, int limit) {
					throw new IllegalStateException("boom");
				}
			});
		});
		await(() -> t[0].getVirtualModel().getStatus().state()==VirtualTableModel.State.ERROR);
		edt(() -> {
			VirtualTableModel.Status s = t[0].getVirtualModel().getStatus();
			assertEquals(VirtualTableModel.State.ERROR, s.state());
			assertEquals("boom", s.error().getMessage());
			t[0].dispose();
		});
	}

	public void testCheckColumn() throws Exception {
		VirtualTable[] t = new VirtualTable[1];
		edt(() -> {
			t[0] = new VirtualTable(new ListDataSource(COLUMNS, rows(10)));
			t[0].setCheckable(true);
		});
		await(() -> t[0].getRowCount()==10);
		edt(() -> {
			assertEquals(3, t[0].getColumnCount());
			assertEquals("Select", t[0].getColumnName(0));
			assertEquals(Boolean.class, t[0].getColumnClass(0));
			t[0].setValueAt(true, 3, 0);
			t[0].setValueAt(true, 7, 0);
			t[0].setValueAt(false, 3, 0);
			assertEquals(List.of(7L), t[0].getCheckedRows());
			t[0].dispose();
		});
	}

	public void testNewSourceReplacesTheOldOne() throws Exception {
		VirtualTable[] t = new VirtualTable[1];
		boolean[] closed = new boolean[1];
		edt(() -> {
			t[0] = new VirtualTable(new ListDataSource(COLUMNS, rows(10)) {
				@Override
				public void close() {
					closed[0] = true;
				}
			});
		});
		await(() -> t[0].getRowCount()==10);
		edt(() -> t[0].setDataSource(new ListDataSource(COLUMNS, rows(3))));
		await(() -> t[0].getRowCount()==3 && closed[0]);
		edt(() -> {
			t[0].setDataSource(null);
			assertEquals(0, t[0].getRowCount());
			assertEquals(VirtualTableModel.State.EMPTY, t[0].getVirtualModel().getStatus().state());
			t[0].dispose();
		});
	}

	// ---- Sorting

	private static ListDataSource unordered() {
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[] { 2, "b" });
		rows.add(new Object[] { 10, null });
		rows.add(new Object[] { 1, "C" });
		return new ListDataSource(COLUMNS, rows);
	}

	private static List<Object> column(VirtualDataSource src, int column) throws Exception {
		List<Object> values = new ArrayList<>();
		for(Object[] r: src.fetch(0, 100)) {
			values.add(r[column]);
		}
		return values;
	}

	public void testListSourceSortsNumbersByValueAndKeepsEmptyValuesLast() throws Exception {
		ListDataSource src = unordered();
		assertEquals(java.util.Arrays.asList(1, 2, 10), column(src.sorted(0, SortOrder.ASCENDING), 0));
		assertEquals(java.util.Arrays.asList(10, 2, 1), column(src.sorted(0, SortOrder.DESCENDING), 0));
		assertEquals(java.util.Arrays.asList("b", "C", null), column(src.sorted(1, SortOrder.ASCENDING), 1));
		assertEquals(java.util.Arrays.asList("C", "b", null), column(src.sorted(1, SortOrder.DESCENDING), 1));
		// Back to the original order, from any order
		assertEquals(java.util.Arrays.asList(2, 10, 1), column(src.sorted(0, SortOrder.ASCENDING).sorted(0, SortOrder.UNSORTED), 0));
	}

	public void testClickOnAHeaderCyclesTheOrderAndMarksTheColumn() throws Exception {
		VirtualTable[] t = new VirtualTable[1];
		edt(() -> {
			t[0] = new VirtualTable();
			t[0].setSortable(true);
			t[0].setDataSource(unordered());
		});
		await(() -> t[0].getRowCount()==3 && t[0].getVirtualModel().isPageLoaded(0));
		edt(() -> t[0].toggleSort(0));
		await(() -> t[0].getSortOrder()==SortOrder.ASCENDING && t[0].getVirtualModel().isPageLoaded(0) && t[0].getRowCount()==3);
		edt(() -> {
			assertEquals(1, t[0].getValueAt(0, 0));
			assertEquals("n ▲", t[0].getColumnModel().getColumn(0).getHeaderValue());
			assertEquals("s", t[0].getColumnModel().getColumn(1).getHeaderValue());
			// The model keeps the plain name
			assertEquals("n", t[0].getModel().getColumnName(0));
			t[0].toggleSort(0);
		});
		await(() -> t[0].getSortOrder()==SortOrder.DESCENDING && t[0].getVirtualModel().isPageLoaded(0) && t[0].getRowCount()==3);
		edt(() -> {
			assertEquals(10, t[0].getValueAt(0, 0));
			assertEquals("n ▼", t[0].getColumnModel().getColumn(0).getHeaderValue());
			t[0].toggleSort(0);
		});
		await(() -> t[0].getSortColumn()==-1 && t[0].getVirtualModel().isPageLoaded(0) && t[0].getRowCount()==3);
		edt(() -> {
			assertEquals(2, t[0].getValueAt(0, 0));
			assertEquals("n", t[0].getColumnModel().getColumn(0).getHeaderValue());
			t[0].dispose();
		});
	}

	public void testSortingNeedsTheOptionAndASortableSource() throws Exception {
		VirtualTable[] t = new VirtualTable[1];
		edt(() -> {
			t[0] = new VirtualTable(unordered());
			t[0].toggleSort(0);
			assertEquals(SortOrder.UNSORTED, t[0].getSortOrder());
			t[0].setSortable(true);
			t[0].setDataSource(new SequentialDataSource() {
				@Override
				public List<VirtualColumn> getColumns() {
					return COLUMNS;
				}
				@Override
				protected List<Object[]> nextBatch() {
					return null;
				}
			});
			t[0].toggleSort(0);
			assertEquals(SortOrder.UNSORTED, t[0].getSortOrder());
			t[0].dispose();
		});
	}

	private static VirtualTableModel.Rows read(VirtualTable t, int max) throws Exception {
		Object[] answer = new Object[2];
		edt(() -> t.getVirtualModel().readRows(max, (r, e) -> { answer[0] = r; answer[1] = e; }));
		await(() -> answer[0]!=null || answer[1]!=null);
		assertNull(answer[1]);
		return (VirtualTableModel.Rows)answer[0];
	}

	public void testReadRowsIsCappedAndSaysSo() throws Exception {
		VirtualTable[] t = new VirtualTable[1];
		edt(() -> {
			t[0] = new VirtualTable(new ListDataSource(COLUMNS, rows(1000)));
			t[0].getVirtualModel().setPageSize(100);
		});
		await(() -> t[0].getVirtualModel().isPageLoaded(0));
		VirtualTableModel.Rows capped = read(t[0], 250);
		assertEquals(250, capped.rows().size());
		assertTrue(capped.truncated());
		assertEquals(249, capped.rows().get(249)[0]);
		VirtualTableModel.Rows exact = read(t[0], 1000);
		assertEquals(1000, exact.rows().size());
		assertFalse(exact.truncated());
		assertEquals(1000, read(t[0], -1).rows().size());
		assertEquals(VirtualTable.DEFAULT_COPY_LIMIT, t[0].getCopyLimit());
		edt(() -> t[0].dispose());
	}

	public void testReadRowsOfASequentialSourceOfUnknownSize() throws Exception {
		VirtualTable[] t = new VirtualTable[1];
		edt(() -> {
			t[0] = new VirtualTable(new SequentialDataSource() {
				int sent;
				@Override
				public List<VirtualColumn> getColumns() {
					return COLUMNS;
				}
				@Override
				protected List<Object[]> nextBatch() {
					if(sent>=450) {
						return null;
					}
					List<Object[]> batch = rows(50);
					sent += 50;
					return batch;
				}
			});
		});
		await(() -> t[0].getVirtualModel().isPageLoaded(0));
		VirtualTableModel.Rows all = read(t[0], 10_000);
		assertEquals(450, all.rows().size());
		assertFalse(all.truncated());
		edt(() -> t[0].dispose());
	}

	public void testCsvHasAHeaderAndQuotesWhatNeedsIt() {
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[] { 1, "a,b" });
		rows.add(new Object[] { 2, "say \"hi\"\nnow" });
		rows.add(new Object[] { null, null });
		assertEquals("n,s\r\n1,\"a,b\"\r\n2,\"say \"\"hi\"\"\nnow\"\r\n,", TableExport.toCsv(COLUMNS, rows));
		assertEquals("n,s", TableExport.toCsv(COLUMNS, List.of()));
	}

	public void testJsonHoldsTheRowsInAnArray() {
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[] { 1, "a\"b\\" });
		rows.add(new Object[] { null, Boolean.TRUE });
		assertEquals("{\n  \"rows\": [\n    {\"n\": 1, \"s\": \"a\\\"b\\\\\"},\n    {\"n\": null, \"s\": true}\n  ]\n}",
				TableExport.toJson(COLUMNS, rows));
		assertEquals("{\n  \"rows\": []\n}", TableExport.toJson(COLUMNS, List.of()));
	}

	public void testASortStateCanBeRestoredWithTheSource() throws Exception {
		VirtualTable[] t = new VirtualTable[1];
		edt(() -> {
			t[0] = new VirtualTable();
			t[0].setSortable(true);
			t[0].setDataSource(unordered(), 0, SortOrder.DESCENDING);
		});
		await(() -> t[0].getRowCount()==3 && t[0].getVirtualModel().isPageLoaded(0));
		edt(() -> {
			assertEquals(SortOrder.DESCENDING, t[0].getSortOrder());
			assertEquals(10, t[0].getValueAt(0, 0));
			t[0].dispose();
		});
	}
}
