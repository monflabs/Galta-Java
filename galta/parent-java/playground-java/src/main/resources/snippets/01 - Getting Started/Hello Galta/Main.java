import org.monflabs.json.JsonObject;

public class Main {

	public static void main(String[] args) {
		JsonObject order = JsonObject.parse("{\"id\":\"A1\",\"lines\":[{\"product\":\"pen\",\"quantity\":3}]}");
		String product = order.getArray("lines").getObject(0).getString("product");
		System.out.println("First product: "+product);       // pen

		order.put("paid", true);
		System.out.println(order.stringify());              // {"id":"A1","lines":[...],"paid":true}
	}
}
