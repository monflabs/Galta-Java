package doc_examples.util;

import static org.junit.Assert.assertThrows;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.monflabs.util.model.ClassMetadata;
import org.monflabs.util.model.ClassMetadata.AccessManager;
import org.monflabs.util.model.ClassMetadata.ClassInfoCache;
import org.monflabs.util.model.ModelAccessor;
import org.monflabs.util.model.ModelException;
import org.monflabs.util.model.PojoAccessor;

import tests.ProjectTestCase;

/**
 * Samples for docs/Utilities/Reflection.md
 */
public class ReflectionExamples extends ProjectTestCase {

	public static class Account {
		public String owner = "ann";
		public double balance;           // shadowed by the property below
		private double cents = 1050;

		public double getBalance() {     // property "balance"
			return cents / 100;
		}
		public void setBalance(double value) {
			cents = value * 100;
		}

		public String getId() {          // read-only property "id"
			return "A-1";
		}

		public String describe() {
			return "account";
		}
		public String describe(int level) {
			return "int " + level;
		}
		public String describe(double level) {
			return "double " + level;
		}
		public String describe(String prefix) {
			return prefix + owner;
		}
	}

	public static class Point {
		public final int x;
		public final int y;
		public Point(int x, int y) {
			this.x = x;
			this.y = y;
		}
	}

	public void testMembers() throws Exception {
		PojoAccessor accessor = new PojoAccessor();
		Account account = new Account();

		assertEquals("ann", accessor.getMember(account, "owner"));        // public field
		assertEquals(10.5, accessor.getMember(account, "balance"));        // the getter wins over the field
		assertTrue(accessor.putMember(account, "balance", 20));            // Integer converted to double
		assertEquals(20.0, account.getBalance());
		assertEquals(0.0, account.balance);                                // the field was not touched

		assertSame(ModelAccessor.UNHANDLED, accessor.getMember(account, "missing"));
		assertFalse(accessor.putMember(account, "missing", 1));
	}

	public void testReadOnlyProperty() throws Exception {
		PojoAccessor accessor = new PojoAccessor();
		assertEquals("A-1", accessor.getMember(new Account(), "id"));
		// A member that exists but cannot be written throws, even without setUseExceptions(true)
		ModelException e = assertThrows(ModelException.class, () -> accessor.putMember(new Account(), "id", "B-2"));
		assertTrue(e.getMessage().startsWith("Error while setting read-only property id"));
	}

	public void testExceptions() throws Exception {
		PojoAccessor accessor = new PojoAccessor();
		accessor.setUseExceptions(true);       // throw instead of returning UNHANDLED/false
		ModelException e = assertThrows(ModelException.class, () -> accessor.getMember(new Account(), "missing"));
		assertEquals("Invalid member missing for class " + Account.class.getName(), e.getMessage());
	}

	public void testOverloads() throws Exception {
		PojoAccessor accessor = new PojoAccessor();
		Account account = new Account();
		assertEquals("account", accessor.call(account, "describe", new Object[] {}));
		assertEquals("int 3", accessor.call(account, "describe", new Object[] {3}));          // exact match
		assertEquals("double 2.5", accessor.call(account, "describe", new Object[] {2.5}));
		assertEquals("Dear ann", accessor.call(account, "describe", new Object[] {"Dear "}));
	}

	public static class Scale {
		public String of(long value) {
			return "long " + value;
		}
		public String of(BigDecimal value) {
			return "decimal " + value;
		}
	}

	public void testOverloadConversions() throws Exception {
		PojoAccessor accessor = new PojoAccessor();
		// No exact match for an Integer: every Number overload is a candidate, and two unrelated
		// candidates are reported as an ambiguity
		ModelException e = assertThrows(ModelException.class, () -> accessor.call(new Scale(), "of", new Object[] {7}));
		assertTrue(e.getMessage().startsWith("Ambiguity between of("));

		// With a single candidate the argument is converted
		assertEquals("long 7", accessor.call(new Scale(), "of", new Object[] {7L}));
		assertEquals("decimal 7", accessor.call(new Scale(), "of", new Object[] {new BigDecimal("7")}));
	}

	public void testIndexedAccess() throws Exception {
		PojoAccessor accessor = new PojoAccessor();
		int[] numbers = {1, 2, 3};
		assertEquals(2, accessor.getMember(numbers, 1));
		assertTrue(accessor.putMember(numbers, 1, 20.7));         // converted to the component type
		assertEquals(20, numbers[1]);
		assertSame(ModelAccessor.UNHANDLED, accessor.getMember(numbers, 3));   // out of bounds

		List<String> list = new ArrayList<>(List.of("a", "b"));
		assertTrue(accessor.putMember(list, 0, "z"));
		assertFalse(accessor.putMember(list, 2, "c"));           // set() only, lists do not grow

		Map<String, Object> map = new HashMap<>();
		accessor.putMember(map, "k", 1);                         // maps are accessed by key
		assertEquals(1, accessor.getMember(map, "k"));
		assertSame(ModelAccessor.UNHANDLED, accessor.getMember(map, "other"));
	}

	public void testConstruct() throws Exception {
		PojoAccessor accessor = new PojoAccessor();
		Point p = (Point) accessor.constructObject(Point.class.getName(), new Object[] {1, 2});
		assertEquals(2, p.y);
		Point converted = (Point) accessor.constructObject(Point.class.getName(), new Object[] {1.9, 2L});
		assertEquals(1, converted.x);   // arguments converted like for call()

		// constructArray() loads the component class by name, so primitive names are not found
		assertSame(ModelAccessor.UNHANDLED, accessor.constructArray("int", 3));
		String[] names = (String[]) accessor.constructArray("java.lang.String", 3);
		assertEquals(3, names.length);
	}

	public void testClassMetadata() throws Exception {
		ClassMetadata metadata = new ClassMetadata(null);
		ClassInfoCache info = metadata.getClassInfoCache(Account.class);
		assertEquals(Set.of("owner", "balance"), info.getValueAccessors());   // public fields
		assertTrue(info.getMethods().contains("describe"));
		assertNotNull(info.getProperty("balance").getSetter());
		assertNotNull(info.getField("balance"));
	}

	public void testAccessManager() throws Exception {
		AccessManager noOwner = new AccessManager() {
			@Override
			public boolean acceptField(String name) {
				return !name.equals("owner");
			}
		};
		PojoAccessor accessor = new PojoAccessor(new ClassMetadata(noOwner));
		assertSame(ModelAccessor.UNHANDLED, accessor.getMember(new Account(), "owner"));
		assertEquals(10.5, accessor.getMember(new Account(), "balance"));
	}

	public void testConvertObject() throws Exception {
		assertEquals(3, ClassMetadata.convertObject(3.9, int.class));
		assertEquals("abc", ClassMetadata.convertObject("abc", int.class));        // not convertible: unchanged
		assertEquals(42, ClassMetadata.convertObjectPermissive("42", int.class));  // permissive parses strings
		assertEquals(true, ClassMetadata.convertObjectPermissive(1, boolean.class));
		assertEquals('A', ClassMetadata.convertObject(65, char.class));
	}
}
