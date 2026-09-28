package tests.rhino;

import java.util.Map;

import tests.BaseProjectTestCase;

public abstract class RhinoTestCase extends BaseProjectTestCase {

	public static Map<String,Object> SYMBOLS = Map.of(
		"PUBLIC", false,
		"PRIVATE", true
	);
	protected RhinoTestCase() {
	}
	
}
