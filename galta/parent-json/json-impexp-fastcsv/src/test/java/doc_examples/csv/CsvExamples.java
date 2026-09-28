package doc_examples.csv;

import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonException;
import org.monflabs.json.JsonObject;
import org.monflabs.json.impexp.container.JsonContainerSource;
import org.monflabs.json.impexp.container.JsonContainerTarget;
import org.monflabs.json.impexp.container.JsonInMemoryFormat;
import org.monflabs.json.impexp.csv.CsvSource;
import org.monflabs.json.impexp.csv.CsvTarget;

import tests.ProjectTestCase;

/**
 * Samples of the GaltaJSON documentation: docs/GaltaJSON/Modules/ImportExport.md (CSV)
 */
public class CsvExamples extends ProjectTestCase {

	public void testCsvWithHeader() throws Exception {
		String csv = """
				id,name,price,since
				p1,Pen,1.20,2024-01-15
				p2,Paper,3.5,2023-06-01
				""";
		CsvSource source = CsvSource.newBuilder()
				.reader(() -> new StringReader(csv))              // called for each import
				.column("price", s -> new BigDecimal(s))          // cell readers for some columns
				.keyFunction(row -> (String)row.get("id"))
				.collectionFunction(row -> "products")
				.timestampFunction(row -> LocalDate.parse((String)row.get("since"))
						.atStartOfDay().toInstant(ZoneOffset.UTC))
				.build();

		JsonContainerTarget target = JsonContainerTarget.newBuilder()
				.format(JsonInMemoryFormat.RECORDSBYCOLKEY)
				.build();
		source.exportTo(target);

		JsonObject pen = ((JsonObject)target.getContainer()).getObject("products").getObject("p1");
		// {"id":"p1","name":"Pen","price":1.20,"since":"2024-01-15"}: every header column is kept
		assertEquals("Pen", pen.getString("name"));
		assertEquals(new BigDecimal("1.20"), pen.get("price"));
		assertEquals("2024-01-15", pen.get("since"));        // no reader: a string
	}

	public void testCsvWithoutHeader() throws Exception {
		String csv = """
				p1;Pen;12
				p2;Paper
				p3;Ink;7;extra
				""";
		CsvSource source = CsvSource.newBuilder()
				.reader(() -> new StringReader(csv))
				.firstRowAsHeader(false)
				.fieldSeparator(';')
				.column("id")                                     // declared columns, by position
				.column("name")
				.column("stock", Integer::valueOf)
				.keyFunction(row -> (String)row.get("id"))
				.build();

		JsonContainerTarget target = JsonContainerTarget.newBuilder()
				.format(JsonInMemoryFormat.RECORDSBYKEY)
				.build();
		source.exportTo(target);
		JsonObject byKey = (JsonObject)target.getContainer();
		assertEquals(12, byKey.getObject("p1").get("stock"));
		assertTrue(byKey.getObject("p2").containsKey("stock"));  // short row: the missing cell is null
		assertNull(byKey.getObject("p2").get("stock"));
		assertFalse(byKey.getObject("p3").containsKey("extra")); // extra cells are ignored
	}

	public void testNoColumnsFails() throws Exception {
		CsvSource source = CsvSource.newBuilder()
				.reader(() -> new StringReader("a,b\n"))
				.firstRowAsHeader(false)
				.build();
		try {
			source.exportTo(JsonContainerTarget.newBuilder().build());
			fail();
		} catch(JsonException e) {
			// Columns are not defined in the source and/or the CSV file
		}
	}

	public void testByteOrderMarkAndEmptyCells() throws Exception {
		String csv = "﻿id,qty\nA,\nB,5\n";                 // UTF-8 BOM, then an empty cell
		CsvSource source = CsvSource.newBuilder()
				.reader(() -> new StringReader(csv))
				.column("qty", s -> s.isEmpty() ? null : Integer.valueOf(s))   // readers see "" for an empty cell
				.keyFunction(row -> (String)row.get("id"))
				.build();
		JsonContainerTarget target = JsonContainerTarget.newBuilder()
				.format(JsonInMemoryFormat.RECORDSBYKEY)
				.build();
		source.exportTo(target);
		JsonObject a = ((JsonObject)target.getContainer()).getObject("A");
		assertEquals("A", a.get("id"));                          // not "﻿id"
		assertNull(a.get("qty"));
		assertEquals(5, ((JsonObject)target.getContainer()).getObject("B").get("qty"));
	}

	public void testCsvTargetInferredColumns() throws Exception {
		JsonContainerSource source = JsonContainerSource.newBuilder()
				.container(JsonArray.parse("""
						[ { "id": "p1", "name": "Pen", "price": 1.2, "tags": ["a","b"] },
						  { "id": "p2", "name": "Paper, A4" } ]
						"""))
				.build();

		StringWriter out = new StringWriter();
		CsvTarget target = CsvTarget.newBuilder()
				.writer(() -> out)                                // closed at the end of the export
				.build();
		source.exportTo(target);

		assertEquals("""
				id,name,price,tags
				p1,Pen,1.2,"[""a"",""b""]"
				p2,"Paper, A4",,
				""", out.toString().replace("\r\n", "\n"));
	}

	public void testCsvTargetDeclaredColumns() throws Exception {
		JsonContainerSource source = JsonContainerSource.newBuilder()
				.container(JsonArray.parse("""
						[ { "id": "p1", "price": 1.2, "qty": 3 } ]
						"""))
				.keyFunction(o -> ((JsonObject)o).getString("id"))
				.build();

		StringWriter out = new StringWriter();
		CsvTarget target = CsvTarget.newBuilder()
				.writer(() -> out)
				.fieldSeparator(';')
				.column("key", c -> c.getKey().getId())          // cell writers receive the JsonContent
				.column("price")
				.column("total", c -> {
					JsonObject o = (JsonObject)c.getJson();
					return String.valueOf(o.getDouble("price") * o.getInt("qty"));
				})
				.build();
		source.exportTo(target);
		assertEquals("key;price;total\np1;1.2;3.5999999999999996\n", out.toString().replace("\r\n", "\n"));
	}

	public void testCsvTargetFromSchema() throws Exception {
		JsonObject schema = JsonObject.parse("""
				{ "type": "object", "properties": { "name": { "type": "string" }, "age": { "type": "integer" } } }
				""");
		StringWriter out = new StringWriter();
		CsvTarget target = CsvTarget.newBuilder()
				.writer(() -> out)
				.firstRowAsHeader(false)
				.columnsFromSchema(schema)
				.build();
		JsonContainerSource.newBuilder()
				.container(JsonArray.parse("[ { \"age\": 36, \"name\": \"Ada\" } ]"))
				.build()
				.exportTo(target);
		assertEquals("Ada,36\n", out.toString().replace("\r\n", "\n"));
	}
}
