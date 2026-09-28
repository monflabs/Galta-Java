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
package org.monflabs.json.impexp.csv;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.impexp.JsonContent;
import org.monflabs.json.impexp.JsonKey;
import org.monflabs.json.impexp.impl.JsonSourceImpl;
import org.monflabs.json.impexp.replication.RangeFilter;
import org.monflabs.json.impexp.util.StaticContent;
import org.monflabs.util.ObjectBuilder;
import org.monflabs.util.iterators.Iterators;

import de.siegmar.fastcsv.reader.CloseableIterator;
import de.siegmar.fastcsv.reader.CsvReader;
import de.siegmar.fastcsv.reader.CsvRecord;

//
// Uses: https://github.com/osiegmar/FastCSV
//
public class CsvSource extends JsonSourceImpl implements CsvBase {
	
	public static class Column {
		private String name;
		private Function<String,Object> cellReader;
		public Column(String name, Function<String,Object> cellReader) {
			this.name = name;
			this.cellReader = cellReader;
		}
		public String getName() {
			return name;
		}
		public Function<String,Object> getCellReader() {
			return cellReader;
		}
	}
	
	public interface Row {
		int size();
		Object get(String name);
	}
	private class RowImpl implements Row {
		private CsvRecord csvRow;
		private RowImpl(CsvRecord csvRow) {
			this.csvRow = csvRow;
		}
		@Override
		public int size() {
			// The columns can come from the header row, not only from the builder
			return colMappers.size();
		}
		@Override
		public Object get(String name) {
			ColumnMapper mapper = colMappers.get(name);
			if(mapper!=null) {
				// Short rows are allowed, so missing cells are null
				if(mapper.index>=csvRow.getFieldCount()) {
					return null;
				}
				String value = csvRow.getField(mapper.index);
				if(emptyAsNull && value!=null && value.isEmpty()) {
					return null;
				}
				if(value!=null && mapper.cellReader!=null) {
					return mapper.cellReader.apply(value);
				}
				return value;
			}
			return null;
		}
	}
	
	public static class Builder extends ObjectBuilder<CsvSource> {
		private Map<String,Column> columns;
		private boolean firstRowAsHeader=true;
		private char fieldSeparator = ',';
		private boolean estimateCount;
		private boolean emptyAsNull;
		private Supplier<Reader> readerFactory;
		private Function<Row,String> collectionFunction;
		private Function<Row,String> keyFunction;
		private Function<Row,Instant> timestampFunction;

		private Builder() {}
		/**
		 * CSV has no null value: {@link CsvTarget} writes a null as an empty cell. When
		 * this is true, an empty cell is read back as null (the cell reader, if any, is
		 * not called), otherwise it is read as an empty string. Default is false.
		 */
		public Builder emptyAsNull(boolean emptyAsNull) {
			this.emptyAsNull = emptyAsNull;
			return this;
		}
		public Builder reader(Supplier<Reader> readerFactory) {
			this.readerFactory = readerFactory;
			return this;
		}
		public Builder column(String name) {
			return column(name,null);
		}
		public Builder column(String name, Function<String,Object> reader) {
			if(columns==null) {
				columns = new LinkedHashMap<>();
			}
			columns.put(name,new Column(name,reader));
			return this;
		}
		public Builder estimateCount(boolean estimateCount) {
			this.estimateCount = estimateCount;
			return this;
		}
		public Builder firstRowAsHeader(boolean firstRowAsHeader) {
			this.firstRowAsHeader = firstRowAsHeader;
			return this;
		}
		public Builder fieldSeparator(char fieldSeparator) {
			this.fieldSeparator = fieldSeparator;
			return this;
		}
		public Builder collectionFunction(Function<Row,String> collectionFunction) {
			this.collectionFunction = collectionFunction;
			return this;
		}
		public Builder keyFunction(Function<Row,String> keyFunction) {
			this.keyFunction = keyFunction;
			return this;
		}
		public Builder timestampFunction(Function<Row,Instant>timestampFunction) {
			this.timestampFunction = timestampFunction;
			return this;
		}
		@Override
		protected CsvSource _build() {
			return new CsvSource(this);
		}
	}
	public static Builder newBuilder() {
		return new Builder();
	}
	
	private static final class ColumnMapper {
		int index;
		Function<String,Object> cellReader;
		private ColumnMapper(int index, Function<String,Object> cellReader) {
			this.index = index;
			this.cellReader = cellReader;
		}
	}

	private Map<String,Column> columns;
	private boolean firstRowAsHeader;
	private char fieldSeparator;
	private boolean estimateCount;
	private boolean emptyAsNull;
	private Supplier<Reader> readerFactory;
	private Function<Row,String> collectionFunction;
	private Function<Row,String> keyFunction;
	private Function<Row,Instant> timestampFunction;
	
	private CsvReader<CsvRecord> csvReader;
    private CloseableIterator<CsvRecord> csvIterator;
	private Map<String,ColumnMapper> colMappers;
	private Long estimatedCount;
	
	
	protected CsvSource(Builder builder) {
		this.columns = builder.columns;
		this.firstRowAsHeader = builder.firstRowAsHeader;
		this.fieldSeparator = builder.fieldSeparator;
		this.estimateCount = builder.estimateCount;
		this.emptyAsNull = builder.emptyAsNull;
		this.readerFactory = builder.readerFactory;
		this.collectionFunction = builder.collectionFunction;
		this.keyFunction = builder.keyFunction;
		this.timestampFunction = builder.timestampFunction;
	}
	
	public Map<String,Column> getColumns() {
		return columns;
	}

	public boolean isEmptyAsNull() {
		return emptyAsNull;
	}

	public boolean isFirstRowAsHeader() {
		return firstRowAsHeader;
	}

	public char getFieldSeparator() {
		return fieldSeparator;
	}

	public Supplier<Reader> getReaderFactory() {
		return readerFactory;
	}

	public Function<Row, String> getCollectionFunction() {
		return collectionFunction;
	}

	public Function<Row, String> getKeyFunction() {
		return keyFunction;
	}
	
	@Override
	public void init(RangeFilter rangeFilter) {
		super.init(rangeFilter);
		close();
		this.csvReader = createCsvReader(readerFactory.get());
		this.csvIterator = csvReader.iterator();
		this.colMappers = new LinkedHashMap<>();
		Map<String,Column> columns = getColumns();
		if(firstRowAsHeader) {
			// An empty input has no header, and then no documents
			CsvRecord firstRow = csvIterator.hasNext() ? csvIterator.next() : null;
			if(firstRow!=null) {
				int count = firstRow.getFieldCount();
				for(int i=0; i<count; i++) {
					String colName = firstRow.getField(i);
					if(colMappers.containsKey(colName)) {
						// The JSON object cannot hold 2 values for the same property: report it
						// instead of silently losing a column
						throw new JsonException(null,"Duplicate column name \"{0}\" in the CSV header",colName);
					}
					Column col = columns!=null ? columns.get(colName) : null;
					if(col==null) {
						// Don't generate an error, just map the type
						colMappers.put(colName, new ColumnMapper(i, null));
					} else {
						colMappers.put(colName, new ColumnMapper(i, col.getCellReader()));
					}
				}
			}
		} else {
			if(columns!=null) {
				int i = 0;
				for(Map.Entry<String,Column> e: columns.entrySet()) {
					colMappers.put(e.getKey(), new ColumnMapper(i++, e.getValue().getCellReader()));
				}
			} else {
				throw new JsonException(null,"Columns are not defined in the sourec and/or the CSV file");
			}
		}
	}
	protected CsvReader<CsvRecord> createCsvReader(Reader reader) {
		return CsvReader.builder()
	    	.fieldSeparator(fieldSeparator)
	    	.skipEmptyLines(true)
	    	// Short and long rows are accepted: missing cells read as null
	    	.allowExtraFields(true)
	    	.allowMissingFields(true)
			.ofCsvRecord(skipByteOrderMark(reader));
	}
	/**
	 * Skip a leading UTF-8 byte order mark, so it does not end up in the first column name.
	 */
	protected static Reader skipByteOrderMark(Reader reader) {
		PushbackReader pr = new PushbackReader(reader,1);
		try {
			int c = pr.read();
			if(c>=0 && c!='\uFEFF') {
				pr.unread(c);
			}
		} catch(IOException ex) {
			throw new JsonException(ex,"Error while reading the CSV content");
		}
		return pr;
	}

	@Override
	public void close() {
		CsvReader<CsvRecord> r = csvReader;
		csvReader = null;
		csvIterator = null;
		colMappers = null;
		if(r!=null) {
			try {
				r.close();
			} catch(IOException ex) {
				throw new JsonException(ex,"Error while closing the CSV reader");
			}
		}
		super.close();
	}
	
	/**
	 * Number of data rows (the header row and the empty rows are not counted), or -1
	 * if the count was not requested with {@link Builder#estimateCount(boolean)}.
	 * The content is fully parsed once to count the rows, so quoted cells spanning
	 * several lines are properly handled.
	 */
	@Override
	public long estimatedCount() {
		if(estimateCount) {
			if(estimatedCount==null) {
				long count = -1;
				try(CsvReader<CsvRecord> r = createCsvReader(readerFactory.get())) {
					count = 0;
					for(CloseableIterator<CsvRecord> it=r.iterator(); it.hasNext(); it.next()) {
						count++;
					}
					if(firstRowAsHeader && count>0) {
						count--;
					}
				} catch(Exception ex) {
					count = -1;
				}
				this.estimatedCount = count;
			}
			return estimatedCount;
		}
		return -1;
	}
	
	@Override
	protected Iterator<JsonContent> createJsonContentIterator() {
		AtomicLong indexCounter = new AtomicLong();
		return Iterators.map(csvIterator, (csvRow) -> {
			long index = indexCounter.getAndIncrement();
			RowImpl row = new RowImpl(csvRow);
			String collection = getCollection(index, row);
			String key = getKey(index, row);
			JsonObject json = composeJson(row);
			Instant timestamp = getTimestamp(index, row);
			return new StaticContent(JsonKey.of(collection, key), json, timestamp);
		});
	}
	
	protected String getCollection(long index, Row row) {
		if(collectionFunction!=null) {
			return collectionFunction.apply(row);
		}
		return null;
	}
	protected String getKey(long index, Row row) {
		if(keyFunction!=null) {
			return keyFunction.apply(row);
		}
		return Long.toString(index);
	}
	protected Instant getTimestamp(long index, Row row) {
		if(timestampFunction!=null) {
			return timestampFunction.apply(row);
		}
		return null;
	}
	protected JsonObject composeJson(Row row) {
		JsonObject o = JsonObject.create();
		for(String col: colMappers.keySet()) {
			o.put(col, row.get(col));
		}
		return o;
	}
}
