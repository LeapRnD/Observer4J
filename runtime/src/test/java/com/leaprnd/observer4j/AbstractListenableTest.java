package com.leaprnd.observer4j;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import static com.leaprnd.observer4j.Exceptions.unchecked;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class AbstractListenableTest {

	private static final ImmutablePersonProperties ANAKIN_SKYWALKER = new ImmutablePersonProperties(
		1234,
		1,
		"Anakin",
		"Skywalker",
		1.88,
		120
	);

	private static final ImmutablePersonProperties DARTH_VADER = new ImmutablePersonProperties(
		1234,
		2,
		"Darth",
		"Vader",
		1.88,
		120
	);

	private ExecutorService executor;

	@BeforeEach
	public void createExecutor() {
		executor = newFixedThreadPool(8);
	}

	@AfterEach
	public void destroyExecutor() {
		executor.shutdownNow();
	}

	private static class AtomicException extends AtomicReference<Throwable> {

		public void fail(String message) {
			store(new AssertionFailedError(message));
		}

		public void store(Throwable newException) {
			final var oldException = compareAndExchange(null, newException);
			if (oldException != null) {
				oldException.addSuppressed(newException);
			}
		}

		public void rethrow() {
			final var exception = get();
			if (exception == null) {
				return;
			}
			throw unchecked(exception);
		}

	}

	private abstract class TestTask implements Runnable, AutoCloseable {

		private final CountDownLatch start = new CountDownLatch(1);
		private final CountDownLatch finish;
		private final CountDownLatch ready;
		private final AtomicException exceptions = new AtomicException();

		public TestTask(int numberOfTimesToExecute) {
			finish = new CountDownLatch(numberOfTimesToExecute);
			ready = new CountDownLatch(numberOfTimesToExecute);
			for (int index = 0; index < numberOfTimesToExecute; index ++) {
				executor.execute(this);
			}
			try {
				assertTrue(ready.await(5, SECONDS), "The task(s) never became ready!");
			} catch (InterruptedException exception) {
				fail(exception);
			}
			start.countDown();
		}

		@Override
		public void run() {
			try {
				ready.countDown();
				assertTrue(start.await(5, SECONDS), "The task(s) never started!");
				go();
			} catch (Throwable exception) {
				exceptions.store(exception);
			} finally {
				finish.countDown();
			}
		}

		public abstract void go();

		@Override
		public void close() {
			try {
				assertTrue(finish.await(5, SECONDS), "The task(s) never finished!");
			} catch (InterruptedException exception) {
				fail(exception);
			}
			exceptions.rethrow();
		}

	}

	@Test
	public void testUpdateBlocksUntilInitialized() {
		final var person = new Person(executor);
		try (final var task = new TestTask(1) {
			@Override
			public void go() {
				person.update(oldProperties -> new PersonPropertiesForwarder(DARTH_VADER));
			}
		}) {
			person.initialize(new PersonPropertiesForwarder(ANAKIN_SKYWALKER));
		}
		assertEquals(DARTH_VADER, person.takeSnapshot().immutableCopy());
	}

	@Test
	public void testListenWithWeakSynchronousListenerBlocksUntilInitialized() {
		final var person = new Person(executor);
		final var listener = new WeakSynchronousListener<>() {
			@Override
			public void onUpdate(Object before, Object after) {}
		};
		try (final var task = new TestTask(1) {
			@Override
			public void go() {
				assertEquals(ANAKIN_SKYWALKER, person.listenWith(listener).immutableCopy());
			}
		}) {
			person.initialize(new PersonPropertiesForwarder(ANAKIN_SKYWALKER));
		}
	}

	@Test
	public void testListenWithStrongSynchronousListenerBlocksUntilInitialized() {
		final var person = new Person(executor);
		final var listener = new StrongSynchronousListener<>() {
			@Override
			public void onUpdate(Object before, Object after) {}
		};
		try (final var task = new TestTask(1) {
			@Override
			public void go() {
				final var value = person.listenWith(listener);
				try {
					assertEquals(ANAKIN_SKYWALKER, value.immutableCopy());
				} finally {
					person.unlistenWith(listener);
				}
			}
		}) {
			person.initialize(new PersonPropertiesForwarder(ANAKIN_SKYWALKER));
		}
	}

	@Test
	public void testUpdateWhenSkipable() {
		final var person = new Person(executor);
		person.initialize(new PersonPropertiesForwarder(ANAKIN_SKYWALKER));
		final var exception = new AtomicException();
		final var strongListener = new StrongSynchronousListener<PersonProperties>() {
			@Override
			public void onUpdate(PersonProperties before, PersonProperties after) {
				exception.fail("The strong synchronous listener was not supposed to be notified!");
			}
		};
		person.listenWith(strongListener);
		try {
			final var weakListener = new WeakSynchronousListener<PersonProperties>() {
				@Override
				public void onUpdate(PersonProperties before, PersonProperties after) {
					exception.fail("The weak synchronous listener was not supposed to be notified!");
				}
			};
			person.listenWith(weakListener);
			try {
				person
					.update(
						new PersonPropertiesForwarder(
							new ImmutablePersonProperties(
								ANAKIN_SKYWALKER.id(),
								ANAKIN_SKYWALKER.version() - 1,
								ANAKIN_SKYWALKER.givenName(),
								ANAKIN_SKYWALKER.familyName(),
								ANAKIN_SKYWALKER.heightInMeters(),
								ANAKIN_SKYWALKER.massInKilograms()
							)
						)
					);
			} finally {
				assertTrue(person.unlistenWith(weakListener));
			}
		} finally {
			assertTrue(person.unlistenWith(strongListener));
		}
		exception.rethrow();
	}

	@Test
	public void testUpdateWhenReplaceable() {
		final var person = new Person(executor);
		person.initialize(new PersonPropertiesForwarder(ANAKIN_SKYWALKER));
		final var exception = new AtomicException();
		final var strongListener = new StrongSynchronousListener<PersonProperties>() {
			@Override
			public void onUpdate(PersonProperties before, PersonProperties after) {
				exception.fail("The strong synchronous listener was not supposed to be notified!");
			}
		};
		assertEquals(ANAKIN_SKYWALKER.id(), person.listenWith(strongListener).id());
		try {
			final var weakListener = new WeakSynchronousListener<PersonProperties>() {
				@Override
				public void onUpdate(PersonProperties before, PersonProperties after) {
					exception.fail("The weak synchronous listener was not supposed to be notified!");
				}
			};
			assertEquals(ANAKIN_SKYWALKER.id(), person.listenWith(weakListener).id());
			try {
				person
					.update(
						new PersonPropertiesForwarder(
							new ImmutablePersonProperties(
								ANAKIN_SKYWALKER.id(),
								ANAKIN_SKYWALKER.version() + 1,
								ANAKIN_SKYWALKER.givenName(),
								ANAKIN_SKYWALKER.familyName(),
								ANAKIN_SKYWALKER.heightInMeters(),
								ANAKIN_SKYWALKER.massInKilograms()
							)
						)
					);
			} finally {
				assertTrue(person.unlistenWith(weakListener));
			}
		} finally {
			assertTrue(person.unlistenWith(strongListener));
		}
		exception.rethrow();
	}

	@Test
	public void testUpdateWhenRedelegatable() throws Throwable {
		final var person = new Person(executor);
		person.initialize(new PersonPropertiesForwarder(ANAKIN_SKYWALKER));
		final var exception = new AtomicException();
		final var latch = new CountDownLatch(1);
		final var strongListener = new StrongSynchronousListener<PersonProperties>() {
			@Override
			public void onUpdate(PersonProperties before, PersonProperties after) {
				assertEquals(ANAKIN_SKYWALKER.version(), before.version());
				assertEquals(ANAKIN_SKYWALKER.version() + 1, after.version());
				latch.countDown();
			}
		};
		assertEquals(ANAKIN_SKYWALKER.id(), person.listenWith(strongListener).id());
		assertEquals(ANAKIN_SKYWALKER.version(), person.listenWith(strongListener).version());
		try {
			final var weakListener = new WeakSynchronousListener<PersonProperties>() {
				@Override
				public void onUpdate(PersonProperties before, PersonProperties after) {
					exception.fail("The weak synchronous listener was not supposed to be notified!");
				}
			};
			assertEquals(ANAKIN_SKYWALKER.id(), person.listenWith(weakListener).id());
			try {
				person
					.update(
						new PersonPropertiesForwarder(
							new ImmutablePersonProperties(
								ANAKIN_SKYWALKER.id(),
								ANAKIN_SKYWALKER.version() + 1,
								ANAKIN_SKYWALKER.givenName(),
								ANAKIN_SKYWALKER.familyName(),
								ANAKIN_SKYWALKER.heightInMeters(),
								ANAKIN_SKYWALKER.massInKilograms()
							)
						)
					);
			} finally {
				assertTrue(person.unlistenWith(weakListener));
			}
		} finally {
			assertTrue(person.unlistenWith(strongListener));
		}
		exception.rethrow();
		assertTrue(latch.await(5, SECONDS), "The strong synchronous listener was supposed to be notified!");
	}

	@Test
	public void testConcurrentUpdates() {
		final var person = new Person(executor);
		person.initialize(new PersonPropertiesForwarder(ANAKIN_SKYWALKER));
		final var task = new TestTask(4) {
			@Override
			public void go() {
				for (int index = 0; index < 100; index ++) {
					person
						.update(
							oldValue -> new PersonPropertiesForwarder(
								new ImmutablePersonProperties(
									oldValue.id(),
									oldValue.version() + 1,
									oldValue.givenName(),
									oldValue.familyName(),
									oldValue.heightInMeters(),
									oldValue.massInKilograms()
								)
							)
						);
				}
			}
		};
		task.close();
		assertEquals(ANAKIN_SKYWALKER.version() + 400, person.takeSnapshot().version());
	}

}
