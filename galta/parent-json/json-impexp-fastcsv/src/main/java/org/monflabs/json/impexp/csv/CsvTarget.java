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
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.JsonUtil;
import org.monflabs.json.impexp.JsonContent;
import org.monflabs.json.impexp.JsonContent.TYPE;
import org.monflabs.json.impexp.impl.JsonTargetImpl;
import org.monflabs.json.jsonschema.SchemaNode;
import org.monflabs.util.IOStreamUtil;
import org.monflabs.util.iterators.Iterators;

import de.siegmar.fastcsv.writer.CsvWriter;

/**
 * CSV target.
 * <p>
 * The columns are either defined explicitly, in which case the other properties of the
 * records are ignored, or inferred from the first record. CSV has no null value: a null
 * (or missing) property is written as an empty cell, see {@link CsvSource.Builder#emptyAsNull(boolean)}
 * to read it back as null.
 */
public class CsvTarget extends JsonTargetImpl implements CsvBase {
	
	public static class Column {
		private String name;
		private Function<JsonContent,String> cellWriter;
		public Column(String name, Function<JsonContent,String> cellWriter) {
			this.name = name;
			this.cellWriter = cellWriter;
		}
		public String getName() {
			return name;
		}
		public Function<JsonContent,String> getCellWriter() {
			return cellWriter;
		}
	}

	public static class Builder extends TargetBuilder<CsvTarget,Builder> {
		private LinkedHashMap<String,Column> columns;
		private boolean firstRowAsHeader=true;
		private char fieldSeparator = ',';
		private Supplier<Writer> writerFactory;
		private boolean closeWriter = true;
		
		private Builder() {}
		/**
		 * Whether the target closes the writer returned by the writer factory when the
		 * import ends. Default is true, as the factory is called for every import. Set it
		 * to false when the factory returns a writer the caller owns (it is then only
		 * flushed).
		 */
		public Builder closeWriter(boolean closeWriter) {
			this.closeWriter = closeWriter;
			return this;
		}
		public Builder writer(Supplier<Writer> writerFactory) {
			this.writerFactory = writerFactory;
			return this;
		}
		public Builder column(String name) {
			return column(name,null);
		}
		public Builder column(String name, Function<JsonContent,String> cellWriter) {
			if(columns==null) {
				columns = new LinkedHashMap<>();
			}
			columns.put(name,new Column(name,cellWriter));
			return this;
		}
		public Builder columnsFromSchema(JsonObject schema) {
			SchemaNode node = new SchemaNode(schema);
			node.getProperties().entrySet().forEach( (e) -> {
				// The JSON value is converted automatically to a string
				column(e.getKey());
			});
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
		@Override
		protected CsvTarget _build() {
			return new CsvTarget(this);
		}
	}	
	public static Builder newBuilder() {
		return new Builder();
	}


	private LinkedHashMap<String,Column> columns;
	private boolean firstRowAsHeader;
	private char fieldSeparator;
	private Supplier<Writer> writerFactory;
	private boolean closeWriter;
	
	private boolean columnsInferred;
	
	private Writer writer;
	private CsvWriter csvWriter;

	protected CsvTarget(Builder builder) {
		super(builder);
		this.columns = builder.columns;
		this.firstRowAsHeader = builder.firstRowAsHeader;
		this.fieldSeparator = builder.fieldSeparator;
		this.writerFactory = builder.writerFactory;
		this.closeWriter = builder.closeWriter;
	}
	
	public Map<String,Column> getColumns() {
		return columns;
	}

	public boolean isFirstRowAsHeader() {
		return firstRowAsHeader;
	}

	public char getFieldSeparator() {
		return fieldSeparator;
	}

	public Supplier<Writer> getWriterFactory() {
		return writerFactory;
	}

	public boolean isCloseWriter() {
		return closeWriter;
	}

	@Override
	public void init() {
		if(columnsInferred) {
			// The columns were inferred from a previous import, start over
			columns = null;
			columnsInferred = false;
		}
		this.writer = writerFactory.get();
		this.csvWriter = createCsvWriter(writer);
		if(columns!=null) {
			if(firstRowAsHeader) {
				csvWriter.writeRecord(columns.keySet());
			}
		}
	}

	@Override
	public void close() {
		Writer w = writer;
		CsvWriter cw = csvWriter;
		writer = null;
		csvWriter = null;
		if(cw!=null) {
			// The CSV writer buffers internally: push its content to the writer
			// before the writer is closed or handed back to the caller
			try {
				cw.flush();
			} catch(IOException ex) {
				throw new JsonException(ex,"Error while flushing the CSV writer");
			}
		}
		if(w!=null) {
			if(closeWriter) {
				IOStreamUtil.close(w);
			} else {
				try {
					w.flush();
				} catch(IOException ex) {
					throw new JsonException(ex,"Error while flushing the CSV writer");
				}
			}
		}
	}

	@Override
	public synchronized void saveJsonContent(JsonContent content) {
		// Does not support delete
		if(content.getType()==TYPE.RECORD) {
			JsonObject o = (JsonObject)content.getJson();
			
			if(columns==null) {
				columns = new LinkedHashMap<>();
				columnsInferred = true;
				for(String k: o.keySet()) {
					columns.put(k,new Column(k,null));
				}
				if(firstRowAsHeader) {
					csvWriter.writeRecord(columns.keySet());
				}
			} else if(columnsInferred) {
				// The header is already written: a property that is not part of the columns
				// inferred from the first record cannot be exported. Report it rather than
				// silently dropping data (explicit columns are a projection, and ignore
				// the other properties).
				for(String k: o.keySet()) {
					if(!columns.containsKey(k)) {
						throw new JsonException(null,"Record {0} has a property \"{1}\" that is not part of the CSV columns inferred from the first record, the columns should be defined explicitly",content.getKey(),k);
					}
				}
			}

			csvWriter.writeRecord( () ->
				Iterators.map(columns.entrySet().iterator(), (e) -> {
					Column column = e.getValue();
					// A cell writer is always invoked, so it can compute columns missing from the JSON
					if(column.cellWriter!=null) {
						return column.cellWriter.apply(content); 
					} else {
						return JsonUtil.toStringValue(o.get(e.getKey())); 
					}
				})
			);
		}
	}
	
	protected CsvWriter createCsvWriter(Writer writer) {
		return CsvWriter.builder()
			.fieldSeparator(fieldSeparator)
			.build(writer);
	}

}
