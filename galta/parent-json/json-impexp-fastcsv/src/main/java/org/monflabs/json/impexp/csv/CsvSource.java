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
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.impexp.JsonContent;
import org.monflabs.json.impexp.JsonKey;
import org.monflabs.json.impexp.impl.JsonSourceImpl;
import org.monflabs.json.impexp.replication.RangeFilter;
import org.monflabs.json.impexp.util.StaticContent;
import org.monflabs.util.ObjectBuilder;

import de.siegmar.fastcsv.reader.CloseableIterator;
import de.siegmar.fastcsv.reader.CsvParseException;
import de.siegmar.fastcsv.reader.CsvReader;
import de.siegmar.fastcsv.reader.CsvRecord;

//
// Uses: https://github.com/osiegmar/FastCSV
//
/**
 * CSV source.
 * <p>
 * Every stream gets its own reader from the reader factory, closed with the stream, so
 * the source can be streamed several times, including concurrently.
 * <p>
 * Invalid content (like a quoted cell that is never closed) and cell conversion errors
 * throw a {@link JsonException} with the line number (and the column) of the error.
 */
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
		private final Map<String,ColumnMapper> colMappers;
		private final CsvRecord csvRow;
		private RowImpl(Map<String,ColumnMapper> colMappers, CsvRecord csvRow) {
			this.colMappers = colMappers;
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
					try {
						return mapper.cellReader.apply(value);
					} catch(RuntimeException ex) {
						throw new JsonException(ex,"Error while reading the CSV cell at line {0}, column \"{1}\": {2}",
								csvRow.getStartingLineNumber(), name, ex.getMessage()!=null ? ex.getMessage() : ex.toString());
					}
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
		private Boolean skipEmptyLines;
		private boolean trimHeader = true;
		private boolean allowMissingColumns;
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
		/**
		 * Whether the empty lines are skipped. An empty line is a row with a single empty
		 * cell: with a single column, it is an empty value, not a line to skip. By default
		 * (when not set), the empty lines are skipped when there are several columns, and
		 * read as rows when there is only one.
		 */
		public Builder skipEmptyLines(boolean skipEmptyLines) {
			this.skipEmptyLines = skipEmptyLines;
			return this;
		}
		/**
		 * Whether the column names of the header row are trimmed. Default is true.
		 */
		public Builder trimHeader(boolean trimHeader) {
			this.trimHeader = trimHeader;
			return this;
		}
		/**
		 * Whether a column defined with {@link #column(String, Function)} can be missing
		 * from the header row (it is then absent from the documents). Default is false: a
		 * missing column throws an exception.
		 */
		public Builder allowMissingColumns(boolean allowMissingColumns) {
			this.allowMissingColumns = allowMissingColumns;
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
	private Boolean skipEmptyLines;
	private boolean trimHeader;
	private boolean allowMissingColumns;
	private Supplier<Reader> readerFactory;
	private Function<Row,String> collectionFunction;
	private Function<Row,String> keyFunction;
	private Function<Row,Instant> timestampFunction;

	private volatile Long estimatedCount;


	protected CsvSource(Builder builder) {
		this.columns = builder.columns;
		this.firstRowAsHeader = builder.firstRowAsHeader;
		this.fieldSeparator = builder.fieldSeparator;
		this.estimateCount = builder.estimateCount;
		this.emptyAsNull = builder.emptyAsNull;
		this.skipEmptyLines = builder.skipEmptyLines;
		this.trimHeader = builder.trimHeader;
		this.allowMissingColumns = builder.allowMissingColumns;
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

	/**
	 * Reads the header (or takes the defined columns) of a new stream.
	 */
	private Map<String,ColumnMapper> readColumns(CloseableIterator<CsvRecord> csvIterator) {
		Map<String,ColumnMapper> colMappers = new LinkedHashMap<>();
		Map<String,Column> columns = getColumns();
		if(firstRowAsHeader) {
			// An empty input has no header, and then no documents
			CsvRecord firstRow = csvIterator.hasNext() ? csvIterator.next() : null;
			if(firstRow!=null) {
				int count = firstRow.getFieldCount();
				// Ignore the empty columns at the end of the header (a spreadsheet can add
				// trailing separators)
				while(count>0 && headerName(firstRow, count-1).isEmpty()) {
					count--;
				}
				for(int i=0; i<count; i++) {
					String colName = headerName(firstRow, i);
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
				if(columns!=null && !allowMissingColumns) {
					for(String name: columns.keySet()) {
						if(!colMappers.containsKey(name)) {
							throw new JsonException(null,"Column \"{0}\" is missing from the CSV header",name);
						}
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
				throw new JsonException(null,"Columns are not defined in the source and/or the CSV file");
			}
		}
		return colMappers;
	}
	private String headerName(CsvRecord header, int index) {
		String name = header.getField(index);
		return trimHeader ? name.trim() : name;
	}

	protected CsvReader<CsvRecord> createCsvReader(Reader reader) {
		return CsvReader.builder()
	    	.fieldSeparator(fieldSeparator)
	    	// The empty lines are handled by the source, see Builder.skipEmptyLines()
	    	.skipEmptyLines(false)
	    	// Short and long rows are accepted: missing cells read as null
	    	.allowExtraFields(true)
	    	.allowMissingFields(true)
	    	// A quote that is never closed would silently swallow the rest of the content
	    	.allowUnclosedQuote(false)
			.ofCsvRecord(skipByteOrderMark(reader));
	}
	/**
	 * Skip a leading UTF-8 byte order mark, so it does not end up in the first column name.
	 */
	protected static Reader skipByteOrderMark(Reader reader) {
		PushbackReader pr = new PushbackReader(reader,1);
		try {
			int c = pr.read();
			if(c>=0 && c!='﻿') {
				pr.unread(c);
			}
		} catch(IOException ex) {
			throw new JsonException(ex,"Error while reading the CSV content");
		}
		return pr;
	}

	private static void closeReader(CsvReader<CsvRecord> r) {
		try {
			r.close();
		} catch(IOException ex) {
			throw new JsonException(ex,"Error while closing the CSV reader");
		}
	}

	// An empty line is read as a row with a single, empty, cell
	private static boolean isEmptyLine(CsvRecord r) {
		return r.getFieldCount()==1 && r.getField(0).isEmpty();
	}

	private boolean skipEmptyLines(Map<String,ColumnMapper> mappers) {
		return skipEmptyLines!=null ? skipEmptyLines : mappers.size()!=1;
	}

	/**
	 * Number of data rows (the header row and the skipped empty rows are not counted), or
	 * -1 if the count was not requested with {@link Builder#estimateCount(boolean)}. The
	 * content is fully parsed once to count the rows, so quoted cells spanning several
	 * lines are properly handled.
	 */
	@Override
	public long estimatedCount() {
		if(estimateCount) {
			if(estimatedCount==null) {
				long count = -1;
				try(CsvReader<CsvRecord> r = createCsvReader(readerFactory.get())) {
					CloseableIterator<CsvRecord> it = r.iterator();
					boolean skip = skipEmptyLines(readColumns(it));
					count = 0;
					while(it.hasNext()) {
						CsvRecord rec = it.next();
						if(!skip || !isEmptyLine(rec)) {
							count++;
						}
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
	protected Stream<JsonContent> createJsonContentStream(RangeFilter filter) {
		CsvReader<CsvRecord> csvReader = createCsvReader(readerFactory.get());
		try {
			CloseableIterator<CsvRecord> csvIterator = csvReader.iterator();
			Map<String,ColumnMapper> colMappers = wrapParseErrors(() -> readColumns(csvIterator));
			boolean skip = skipEmptyLines(colMappers);
			Iterator<JsonContent> it = new Iterator<JsonContent>() {
				long index;
				CsvRecord next;
				@Override
				public boolean hasNext() {
					while(next==null) {
						if(!wrapParseErrors(csvIterator::hasNext)) {
							return false;
						}
						CsvRecord r = wrapParseErrors(csvIterator::next);
						if(!skip || !isEmptyLine(r)) {
							next = r;
						}
					}
					return true;
				}
				@Override
				public JsonContent next() {
					if(!hasNext()) {
						throw new NoSuchElementException();
					}
					CsvRecord csvRow = next;
					next = null;
					return toContent(index++, new RowImpl(colMappers, csvRow));
				}
			};
			Spliterator<JsonContent> spit = Spliterators.spliteratorUnknownSize(it, Spliterator.NONNULL);
			return StreamSupport.stream(spit, false).onClose(() -> closeReader(csvReader));
		} catch(RuntimeException | Error e) {
			try {
				closeReader(csvReader);
			} catch(Exception ce) {
				e.addSuppressed(ce);
			}
			throw e;
		}
	}
	private static <T> T wrapParseErrors(Supplier<T> s) {
		try {
			return s.get();
		} catch(CsvParseException ex) {
			throw new JsonException(ex,"Invalid CSV content: {0}",ex.getMessage());
		}
	}
	private JsonContent toContent(long index, RowImpl row) {
		String collection = getCollection(index, row);
		String key = getKey(index, row);
		JsonObject json = composeJson(row);
		Instant timestamp = getTimestamp(index, row);
		return new StaticContent(JsonKey.of(collection, key), json, timestamp);
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
		for(String col: ((RowImpl)row).colMappers.keySet()) {
			o.put(col, row.get(col));
		}
		return o;
	}
}
