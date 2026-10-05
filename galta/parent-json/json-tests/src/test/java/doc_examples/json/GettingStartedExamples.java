package doc_examples.json;

import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;

import tests.ProjectTestCase;

public class GettingStartedExamples extends ProjectTestCase {

	public void testFirstSteps() throws Exception {
		JsonFactory factory = JsonFactory.get();

		JsonObject order = (JsonObject)factory.parse("{\"id\":\"A1\",\"lines\":[{\"product\":\"pen\",\"quantity\":3}]}");
		String product = order.getArray("lines").getObject(0).getString("product");   // "pen"
		order.put("paid", true);
		String text = factory.stringify(order);
		// {"id":"A1","lines":[{"product":"pen","quantity":3}],"paid":true}

		assertEquals("pen", product);
		assertEquals("{\"id\":\"A1\",\"lines\":[{\"product\":\"pen\",\"quantity\":3}],\"paid\":true}", text);
	}
}
