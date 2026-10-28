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

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.SortOrder;
import javax.swing.UIManager;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;

/**
 * A {@link JTable} with virtual scrolling: it displays any number of rows
 * from a {@link VirtualDataSource}, fetching from the source only the pages
 * that are visible (and the next one) and keeping a bounded number in memory.
 * <p>
 * Typical use, from any data (JDBC, CSV, a REST API, ...):
 * <pre>
 * VirtualTable table = new VirtualTable();
 * table.setDataSource(new ResultSetDataSource(statement.executeQuery(sql)));
 * panel.add(new JScrollPane(table));
 * table.addStatusListener(s -&gt; label.setText(s.loadedRows() + " rows"));
 * </pre>
 * The table can be reused with another source at any time. It adds, to the
 * plain {@link JTable}: striped rows, columns sized to their first rows,
 * horizontal scrolling for wide data, an optional column of check boxes
 * ({@link #setCheckable(boolean)}), sorting by a click on the column headers
 * ({@link #setSortable(boolean)}), and cell renderers by column type
 * ({@link VirtualColumn#type()}) that show a placeholder for the rows not yet
 * loaded.
 * <p>
 * Like every Swing component, use it from the event dispatch thread.
 */
@SuppressWarnings("serial")
public class VirtualTable extends JTable {

	/** The default maximum number of rows {@link #copyAsCsv(CopyListener)} and {@link #copyAsJson(CopyListener)} read. */
	public static final int DEFAULT_COPY_LIMIT = 10_000;

	private static final int MAX_AUTO_WIDTH = 400;
	private static final int SAMPLE_ROWS = 50;

	private final TableCellRenderer placeholderRenderer = new PlaceholderRenderer();
	private boolean pendingFit;
	private boolean striped = true;
	private boolean sortable;
	// The column (in the data source) the rows are ordered by
	private int sortColumn = -1;
	private SortOrder sortOrder = SortOrder.UNSORTED;
	private int copyLimit = DEFAULT_COPY_LIMIT;

	public VirtualTable() {
		this(null);
	}

	public VirtualTable(VirtualDataSource source) {
		super(new VirtualTableModel());
		setAutoResizeMode(AUTO_RESIZE_OFF);
		setFillsViewportHeight(true);
		setAutoCreateRowSorter(false);
		setShowGrid(false);
		setShowHorizontalLines(false);
		setShowVerticalLines(true);
		setIntercellSpacing(new Dimension(1, 0));
		setRowHeight(Math.max(getRowHeight(), getFontMetrics(getFont()).getHeight()+6));
		getTableHeader().addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if(sortable && e.getButton()==MouseEvent.BUTTON1 && e.getClickCount()==1 && getTableHeader().getResizingColumn()==null) {
					int column = getTableHeader().columnAtPoint(e.getPoint());
					if(column>=0) {
						toggleSort(column);
					}
				}
			}
		});
		if(source!=null) {
			setDataSource(source);
		}
	}

	/**
	 * Only {@link VirtualTableModel}s are accepted: the table is the view of a
	 * virtual source.
	 */
	@Override
	public void setModel(TableModel model) {
		if(!(model instanceof VirtualTableModel)) {
			throw new IllegalArgumentException("A VirtualTable needs a VirtualTableModel");
		}
		super.setModel(model);
	}

	public VirtualTableModel getVirtualModel() {
		return (VirtualTableModel)getModel();
	}

	/** Shows the rows of a source (null to clear the table). The table owns the source and closes it. */
	public void setDataSource(VirtualDataSource source) {
		sortColumn = -1;
		sortOrder = SortOrder.UNSORTED;
		show(source);
	}

	private void show(VirtualDataSource source) {
		pendingFit = true;
		clearSelection();
		getVirtualModel().setDataSource(source);
	}

	/**
	 * Shows the rows of a source ordered by a column, to restore a sort state:
	 * the same as {@link #setDataSource(VirtualDataSource)} followed by
	 * {@link #sortBy(int, SortOrder)}, without loading the unsorted rows.
	 */
	public void setDataSource(VirtualDataSource source, int column, SortOrder order) {
		setDataSource(source);
		if(source!=null && order!=null && order!=SortOrder.UNSORTED) {
			sortBy(column, order);
		}
	}

	// ---- Copy

	/** Told how a copy ended, on the event dispatch thread. */
	public interface CopyListener {
		/** The rows are on the clipboard; <code>truncated</code> when there were more than the limit. */
		void copied(int rows, boolean truncated);

		void failed(Throwable error);
	}

	/** The maximum number of rows a copy reads, 10,000 by default; negative for all of them. */
	public int getCopyLimit() {
		return copyLimit;
	}

	public void setCopyLimit(int copyLimit) {
		this.copyLimit = copyLimit;
	}

	/** Copies the rows, with a header row, as CSV to the clipboard (up to the {@link #getCopyLimit() limit}). */
	public void copyAsCsv(CopyListener listener) {
		copy(TableExport::toCsv, listener);
	}

	/** Copies the rows as a JSON object holding an array of rows (<code>rows</code>) to the clipboard. */
	public void copyAsJson(CopyListener listener) {
		copy(TableExport::toJson, listener);
	}

	private void copy(java.util.function.BiFunction<List<VirtualColumn>, List<Object[]>, String> format, CopyListener listener) {
		getVirtualModel().readRows(copyLimit, (rows, error) -> {
			if(error!=null) {
				listener.failed(error);
				return;
			}
			try {
				String text = format.apply(rows.columns(), rows.rows());
				Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
				listener.copied(rows.rows().size(), rows.truncated());
			} catch(Throwable t) {
				listener.failed(t);
			}
		});
	}

	// ---- Sorting

	/**
	 * Lets the user order the rows by clicking on a column header: ascending,
	 * then descending, then back to the order of the source. An arrow in the
	 * header shows the order. It works with a {@link SortableDataSource}: the
	 * order is given by the source, as the table only has some of the rows.
	 * Off by default.
	 */
	public void setSortable(boolean sortable) {
		this.sortable = sortable;
	}

	public boolean isSortable() {
		return sortable;
	}

	/** The column (in the data source) the rows are ordered by, -1 for none. */
	public int getSortColumn() {
		return sortColumn;
	}

	public SortOrder getSortOrder() {
		return sortOrder;
	}

	/**
	 * What a click on a column header does: orders by the column, ascending if
	 * it was not the one the rows are ordered by, then descending, then none.
	 * @param viewColumn the column in the table
	 */
	public void toggleSort(int viewColumn) {
		if(viewColumn<0 || viewColumn>=getColumnCount()) {
			return;
		}
		int column = convertColumnIndexToModel(viewColumn)-(isCheckable() ? 1 : 0);
		if(column<0) {
			return;
		}
		SortOrder next = column!=sortColumn ? SortOrder.ASCENDING
				: sortOrder==SortOrder.ASCENDING ? SortOrder.DESCENDING : SortOrder.UNSORTED;
		sortBy(column, next);
	}

	/**
	 * Shows the rows ordered by a column of the data source, if the table is
	 * {@link #setSortable(boolean) sortable} and the source can do it.
	 */
	public void sortBy(int column, SortOrder order) {
		if(!sortable || !(getDataSource() instanceof SortableDataSource source) || column<0 || !source.isSortable(column)) {
			return;
		}
		VirtualDataSource sorted = source.sorted(column, order);
		if(sorted==null || sorted==source) {
			return;
		}
		sortColumn = order==SortOrder.UNSORTED ? -1 : column;
		sortOrder = order;
		show(sorted);
	}

	// The columns are created again with the data: the header says which one gives the order
	@Override
	public void createDefaultColumnsFromModel() {
		super.createDefaultColumnsFromModel();
		if(sortOrder!=null && sortOrder!=SortOrder.UNSORTED) {
			int offset = isCheckable() ? 1 : 0;
			var columns = getColumnModel().getColumns();
			while(columns.hasMoreElements()) {
				TableColumn tc = columns.nextElement();
				if(tc.getModelIndex()-offset==sortColumn) {
					tc.setHeaderValue(getModel().getColumnName(tc.getModelIndex())+(sortOrder==SortOrder.ASCENDING ? " ▲" : " ▼"));
				}
			}
		}
	}

	public VirtualDataSource getDataSource() {
		return getVirtualModel().getDataSource();
	}

	/** Adds a first column of check boxes. */
	public void setCheckable(boolean checkable) {
		pendingFit = true;
		getVirtualModel().setCheckable(checkable);
	}

	public boolean isCheckable() {
		return getVirtualModel().isCheckable();
	}

	/** The indexes (in the data source) of the checked rows. */
	public List<Long> getCheckedRows() {
		return getVirtualModel().getCheckedRows();
	}

	public void setPageSize(int pageSize) {
		getVirtualModel().setPageSize(pageSize);
	}

	/** Called on the event dispatch thread when the loading state or the row count changes. */
	public void addStatusListener(Consumer<VirtualTableModel.Status> l) {
		getVirtualModel().addStatusListener(l);
	}

	public void removeStatusListener(Consumer<VirtualTableModel.Status> l) {
		getVirtualModel().removeStatusListener(l);
	}

	public boolean isStriped() {
		return striped;
	}

	public void setStriped(boolean striped) {
		this.striped = striped;
		repaint();
	}

	/** Releases the source and the loading thread, when the table is discarded for good. */
	public void dispose() {
		getVirtualModel().dispose();
	}

	// ---- Rendering

	@Override
	public TableCellRenderer getCellRenderer(int row, int column) {
		Object value = getValueAt(row, column);
		if(value==VirtualTableModel.LOADING || value==VirtualTableModel.FAILED) {
			return placeholderRenderer;
		}
		Class<?> type = getColumnClass(column);
		if(value!=null && !type.isInstance(value)) {
			// Not the declared type: show it as text rather than failing in a typed renderer
			return getDefaultRenderer(Object.class);
		}
		return super.getCellRenderer(row, column);
	}

	@Override
	public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
		Component c = super.prepareRenderer(renderer, row, column);
		if(striped && !isRowSelected(row)) {
			c.setBackground(row%2==0 ? getBackground() : stripeColor());
		}
		return c;
	}

	private Color stripeColor() {
		Color c = UIManager.getColor("Table.alternateRowColor");
		if(c!=null) {
			return c;
		}
		Color bg = getBackground();
		Color fg = getForeground();
		// A touch of the text color over the background
		return new Color(
				(bg.getRed()*95+fg.getRed()*5)/100,
				(bg.getGreen()*95+fg.getGreen()*5)/100,
				(bg.getBlue()*95+fg.getBlue()*5)/100);
	}

	@Override
	public String getToolTipText(MouseEvent e) {
		int row = rowAtPoint(e.getPoint());
		int col = columnAtPoint(e.getPoint());
		if(row>=0 && col>=0) {
			Object v = getValueAt(row, col);
			if(v!=null && !(v instanceof Boolean) && v!=VirtualTableModel.LOADING) {
				String s = v.toString();
				if(s.length()>40) {
					// Long values are cut in the cell
					return "<html><body style='width:400px'>"+escape(s.length()>1000 ? s.substring(0, 1000)+"…" : s);
				}
			}
		}
		return super.getToolTipText(e);
	}

	private static String escape(String s) {
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static class PlaceholderRenderer extends DefaultTableCellRenderer {
		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
			super.getTableCellRendererComponent(table, value, isSelected, false, row, column);
			setHorizontalAlignment(LEFT);
			if(!isSelected) {
				setForeground(value==VirtualTableModel.FAILED ? Color.RED : Color.GRAY);
			}
			return this;
		}
	}

	// ---- Layout

	@Override
	public void tableChanged(TableModelEvent e) {
		super.tableChanged(e);
		if(pendingFit && getRowCount()>0 && getVirtualModel().isPageLoaded(0)) {
			pendingFit = false;
			fitColumns();
		}
	}

	/** Sizes the columns to their header and first loaded rows. */
	public void fitColumns() {
		int rows = Math.min(getRowCount(), SAMPLE_ROWS);
		for(int col=0; col<getColumnCount(); col++) {
			TableColumn tc = getColumnModel().getColumn(col);
			Component h = getTableHeader().getDefaultRenderer().getTableCellRendererComponent(this, tc.getHeaderValue(), false, false, -1, col);
			int w = h.getPreferredSize().width;
			for(int row=0; row<rows; row++) {
				if(getVirtualModel().getRow(row)!=null || getVirtualModel().isCheckable() && col==0) {
					Component c = prepareRenderer(getCellRenderer(row, col), row, col);
					w = Math.max(w, c.getPreferredSize().width);
				}
			}
			tc.setPreferredWidth(Math.min(w+getIntercellSpacing().width+8, MAX_AUTO_WIDTH));
		}
	}

	// Scroll horizontally when the columns are wider than the view, otherwise fill it
	@Override
	public boolean getScrollableTracksViewportWidth() {
		return getParent() instanceof JViewport vp && vp.getWidth()>getPreferredSize().width;
	}
}
