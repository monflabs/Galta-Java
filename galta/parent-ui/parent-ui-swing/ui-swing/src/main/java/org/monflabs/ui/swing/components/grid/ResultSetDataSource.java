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

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * A source over a JDBC {@link ResultSet}, read forward as the user scrolls
 * (a forward-only result set is enough). Closing the source closes the result
 * set and, if given, its statement; the connection stays the caller's.
 */
public class ResultSetDataSource extends SequentialDataSource {

	public static final int DEFAULT_BATCH_SIZE = 500;

	private final ResultSet resultSet;
	private final Statement statement;
	private final int batchSize;
	private List<VirtualColumn> columns;

	public ResultSetDataSource(ResultSet resultSet) {
		this(resultSet, null, DEFAULT_BATCH_SIZE);
	}

	public ResultSetDataSource(ResultSet resultSet, Statement statement, int batchSize) {
		this.resultSet = resultSet;
		this.statement = statement;
		this.batchSize = Math.max(1, batchSize);
	}

	@Override
	public synchronized List<VirtualColumn> getColumns() throws SQLException {
		if(columns==null) {
			ResultSetMetaData md = resultSet.getMetaData();
			List<VirtualColumn> list = new ArrayList<>();
			for(int i=1; i<=md.getColumnCount(); i++) {
				list.add(new VirtualColumn(md.getColumnLabel(i), columnType(md.getColumnClassName(i))));
			}
			columns = list;
		}
		return columns;
	}

	private static Class<?> columnType(String className) {
		try {
			Class<?> c = Class.forName(className);
			// Only the types with a dedicated renderer: anything else is shown as text
			return Number.class.isAssignableFrom(c) || c==Boolean.class ? c : Object.class;
		} catch(ClassNotFoundException | LinkageError e) {
			return Object.class;
		}
	}

	@Override
	protected List<Object[]> nextBatch() throws SQLException {
		int count = getColumns().size();
		List<Object[]> rows = new ArrayList<>();
		while(rows.size()<batchSize && resultSet.next()) {
			Object[] row = new Object[count];
			for(int i=0; i<count; i++) {
				row[i] = resultSet.getObject(i+1);
			}
			rows.add(row);
		}
		return rows;
	}

	@Override
	public synchronized void close() {
		super.close();
		try {
			resultSet.close();
			if(statement!=null) {
				statement.close();
			}
		} catch(SQLException e) {
			// Closing: nothing more to do
		}
	}
}
