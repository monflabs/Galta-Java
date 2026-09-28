package doc_examples.util;

import static org.junit.Assert.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.monflabs.util.Console;
import org.monflabs.util.ObjectBuilder;
import org.monflabs.util.ObjectBuilderException;
import org.monflabs.util.builder.Required;
import org.monflabs.util.generators.Generator;
import org.monflabs.util.generators.GeneratorImpl;
import org.monflabs.util.performance.PerformanceWatch;
import org.monflabs.util.profiler.Aggregator;
import org.monflabs.util.profiler.Profiler;
import org.monflabs.util.profiler.ProfilerSnapshot;

import tests.ProjectTestCase;

/**
 * Samples for docs/Utilities/Runtime.md
 */
public class RuntimeExamples extends ProjectTestCase {

	public void testGenerator() throws Exception {
		try (Generator<Integer, String> g = GeneratorImpl.create(y -> {
			for (int i = 1; i <= 3; i++) {
				y.yield(i);
			}
			return "done";
		})) {
			List<Integer> values = new ArrayList<>();
			g.forEachRemaining(values::add);          // a Generator is an Iterator
			assertEquals(List.of(1, 2, 3), values);
			assertEquals("done", g.getReturnValue());  // the body's return value
		}
	}

	public void testGeneratorIsLazy() throws Exception {
		List<String> log = new ArrayList<>();
		try (Generator<String, Void> g = GeneratorImpl.create(y -> {
			log.add("started");
			y.yield("a");
			return null;
		})) {
			assertTrue(log.isEmpty());      // nothing runs until the first hasNext()/next()
			assertEquals("a", g.next());
			assertEquals(List.of("started"), log);
		}
	}

	public void testNextWithValue() throws Exception {
		try (Generator<String, String> g = GeneratorImpl.create(y -> {
			String name = y.yield("Who are you?");     // yield() returns what next(value) sends
			String city = y.yield("Where do you live, " + name + "?");
			return name + " from " + city;
		})) {
			assertEquals("Who are you?", g.next(null));      // the first value sent is discarded
			assertEquals("Where do you live, Ann?", g.next("Ann"));
			assertThrows(NoSuchElementException.class, () -> g.next("Paris"));  // body completed
			assertEquals("Ann from Paris", g.getReturnValue());
		}
	}

	public void testThrowInto() throws Exception {
		try (Generator<String, Void> g = GeneratorImpl.create(y -> {
			try {
				y.yield("waiting");
			} catch (IllegalStateException e) {
				y.yield("recovered from " + e.getMessage());
			}
			return null;
		})) {
			assertEquals("waiting", g.next());
			// Raised at the paused yield(): the body can catch it
			assertEquals("recovered from boom", g.throwInto(new IllegalStateException("boom")));
		}
	}

	public void testReturnWith() throws Exception {
		List<String> log = new ArrayList<>();
		Generator<Integer, String> g = GeneratorImpl.create(y -> {
			try {
				for (int i = 0; ; i++) {
					y.yield(i);
				}
			} finally {
				log.add("cleanup");
			}
		});
		assertEquals(Integer.valueOf(0), g.next());
		// Unwinds the body from its yield(): finally blocks run, catch blocks do not
		assertThrows(NoSuchElementException.class, () -> g.returnWith("stopped"));
		assertEquals("stopped", g.getReturnValue());
		assertEquals(List.of("cleanup"), log);
		assertFalse(g.hasNext());
	}

	public void testBodyException() throws Exception {
		try (Generator<Integer, Void> g = GeneratorImpl.create(y -> {
			y.yield(1);
			throw new IllegalArgumentException("bad input");
		})) {
			assertEquals(Integer.valueOf(1), g.next());
			IllegalArgumentException e = assertThrows(IllegalArgumentException.class, g::hasNext);
			assertEquals("bad input", e.getMessage());   // the body's exception, unwrapped
		}
	}

	public void testCloseAndExecutor() throws Exception {
		List<String> log = new ArrayList<>();
		ExecutorService executor = Executors.newCachedThreadPool();
		try {
			Generator<Integer, Void> g = GeneratorImpl.create(executor, y -> {
				try {
					y.yield(1);
					y.yield(2);
				} finally {
					log.add("released");
				}
				return null;
			});
			assertEquals(Integer.valueOf(1), g.next());
			g.close();                 // resumes the parked body so its thread can finish
			assertEquals(List.of("released"), log);
			assertFalse(g.hasNext());
		} finally {
			executor.shutdown();
		}
	}

	public void testProfiler() throws Exception {
		Profiler.reset();
		Profiler.start();
		try {
			for (String table : new String[] {"users", "orders", "users"}) {
				Profiler.profile("query", table, () -> {
					Profiler.profile("parse", () -> Thread.sleep(1));   // nested block
				});
			}
			ProfilerSnapshot snapshot = Profiler.createSnapshot("after load");
			Aggregator query = snapshot.getMainAggregator().getChildren().get(0);
			assertEquals("query", query.getType());
			assertEquals(3, query.getCount());                  // grouped over both parameters
			assertEquals(2, query.getChildren().size());         // query[users], query[orders]
			assertTrue(query.getTotalWallTime() >= 3_000_000);   // nanoseconds
		} finally {
			Profiler.stop();
			Profiler.reset();
		}
		// When stopped, profile() simply runs the block
		assertEquals("x", Profiler.profile("idle", () -> "x"));
	}

	public void testPerformanceWatch() throws Exception {
		PerformanceWatch w = new PerformanceWatch("sum {0}", 1000);
		int[] runs = {0};
		w.run(() -> runs[0]++, 5, 2);          // 5 measured iterations after 2 warm-up runs
		assertEquals(7, runs[0]);
		assertEquals(5, w.getIterations().size());
		assertTrue(w.getTotalWallTime() >= w.getAverageWallTime());

		w.startIteration();                    // or measure by hand
		w.endIteration();
		assertEquals(6, w.getIterations().size());
	}

	public void testConsole() throws Exception {
		PrintStream saved = System.out;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
		try {
			Console.log("{0} files in {1}", 3, "/tmp");     // StringFormat placeholders
		} finally {
			System.setOut(saved);
		}
		assertEquals("3 files in /tmp" + System.lineSeparator(), out.toString(StandardCharsets.UTF_8));

		Exception e = new RuntimeException("outer", new IllegalStateException("inner"));
		assertEquals("outer\ninner", Console.getMessage(e));   // the whole cause chain
	}

	public static class Connection {
		final String host;
		final int port;
		Connection(String host, int port) {
			this.host = host;
			this.port = port;
		}
	}

	public static class ConnectionBuilder extends ObjectBuilder<Connection> {
		@Required
		private String host;
		private int port = 80;

		public ConnectionBuilder host(String host) {
			this.host = host;
			return this;
		}
		public ConnectionBuilder port(int port) {
			this.port = port;
			return this;
		}
		@Override
		protected void validate() {
			assertNotNull(host, "host");
			if (port <= 0) {
				throw exception("Invalid port {0}", port);
			}
		}
		@Override
		protected Connection _build() {
			return new Connection(host, port);
		}
	}

	public void testObjectBuilder() throws Exception {
		Connection c = new ConnectionBuilder().host("example.com").port(8080).build();
		assertEquals(8080, c.port);

		ObjectBuilderException e = assertThrows(ObjectBuilderException.class, () -> new ConnectionBuilder().build());
		assertEquals("Object host cannot be null", e.getMessage());
		e = assertThrows(ObjectBuilderException.class, () -> new ConnectionBuilder().host("h").port(0).build());
		assertEquals("Invalid port 0", e.getMessage());
	}
}
