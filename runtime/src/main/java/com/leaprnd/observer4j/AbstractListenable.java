package com.leaprnd.observer4j;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.ref.Cleaner;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import static com.leaprnd.observer4j.EmptyImmutableMap.emptyImmutableMap;
import static com.leaprnd.observer4j.Exceptions.unchecked;
import static com.leaprnd.observer4j.ReferenceStrength.STRONG_REFERENCE;
import static com.leaprnd.observer4j.ReferenceStrength.WEAK_REFERENCE;
import static com.leaprnd.observer4j.ReturnValue.RETURN_NEW_VALUE;
import static java.lang.ref.Cleaner.create;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.ConcurrentHashMap.newKeySet;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;
import static org.slf4j.LoggerFactory.getLogger;

public abstract class AbstractListenable<T> implements Listenable<T>, Runnable {

	private static final VarHandle STATE_UPDATER;

	static {
		try {
			STATE_UPDATER = MethodHandles.lookup().findVarHandle(AbstractListenable.class, "state", State.class);
		} catch (ReflectiveOperationException exception) {
			throw new ExceptionInInitializerError(exception);
		}
	}

	private static sealed abstract class Emission {

		private final Emission previous;
		private Emission next;

		private Emission(Emission previous) {
			this.previous = previous;
		}

		public Emission getPrevious() {
			return previous;
		}

		public Emission getNext() {
			return next;
		}

		public void setNext(Emission next) {
			this.next = next;
		}

		public abstract void emit();

	}

	private final class EmitUpdateGlobally extends Emission {

		private final T oldValue;
		private final T newValue;

		public EmitUpdateGlobally(Emission previous, T oldValue, T newValue) {
			super(previous);
			this.oldValue = detach(oldValue);
			this.newValue = detach(newValue);
		}

		@Override
		public void emit() {
			emitUpdateGlobally(oldValue, newValue);
		}

	}

	private static final class EmitUpdateToListener<T> extends Emission {

		private final ImmutableMapEntry<T> entry;
		private final T newValue;

		public EmitUpdateToListener(Emission previous, ImmutableMapEntry<T> entry, T newValue) {
			super(previous);
			this.entry = entry;
			this.newValue = newValue;
		}

		@Override
		public void emit() {
			final var listener = entry.listener();
			if (listener == null) {
				return;
			}
			final var oldValue = entry.value();
			listener.onUpdate(oldValue, newValue);
		}

	}

	private record State<T> (CountDownLatch latch, T value, ImmutableMap<T> forwarders, Emission emission) {

		private State(T value) {
			this(null, value, value == null ? null : emptyImmutableMap(), null);
		}

		private State(CountDownLatch latch) {
			this(requireNonNull(latch), null, null, null);
		}

		private State(T value, ImmutableMap<T> forwarders, Emission emission) {
			this(null, requireNonNull(value), requireNonNull(forwarders), emission);
		}

		private State {
			if (value != null) {
				if (forwarders == null) {
					throw new IllegalArgumentException();
				}
				if (latch != null) {
					throw new IllegalArgumentException();
				}
			}
		}

		public State<T> with(ImmutableMap<T> newForwarders) {
			if (newForwarders == forwarders) {
				return this;
			}
			return new State<>(null, value, newForwarders.clean(), emission);
		}

	}

	protected static final Executor DIRECT_EXECUTOR = Runnable::run;

	protected final Executor executor;

	@NotNull
	private volatile State<T> state;

	protected AbstractListenable(@Nullable T initialValue) {
		this(DIRECT_EXECUTOR, initialValue);
	}

	protected AbstractListenable() {
		this(DIRECT_EXECUTOR, null);
	}

	protected AbstractListenable(Executor executor) {
		this(executor, null);
	}

	protected AbstractListenable(Executor executor, @Nullable T initialValue) {
		this.executor = executor;
		this.state = new State<>(initialValue);
	}

	public final void initialize(T initialValue) {
		final var newState = new State<>(requireNonNull(initialValue));
		while (true) {
			final var oldState = this.state;
			if (oldState.value != null) {
				throw new IllegalStateException("This listenable was already initialized!");
			}
			if (STATE_UPDATER.compareAndSet(this, oldState, newState)) {
				final var latch = oldState.latch;
				if (latch != null) {
					latch.countDown();
				}
				return;
			}
		}
	}

	protected void emitUpdateGlobally(T oldValue, T newValue) {}

	@Override
	public final T takeSnapshot() {
		return detach(waitUntilInitializedToGetState().value);
	}

	protected T detach(T value) {
		return value;
	}

	private static final Cleaner CLEANER = create(runnable -> {
		final var thread = new Thread(runnable);
		thread.setDaemon(true);
		thread.setName("Listenable Cleaner");
		return thread;
	});

	@Override
	public final T listenWith(Listener<? super T> listener) {
		return listener.listenTo(this);
	}

	@Override
	public final T listenWith(WeakSynchronousListener<? super T> listener) {
		return listenWith(listener, WEAK_REFERENCE);
	}

	@Override
	public final T listenWith(StrongSynchronousListener<? super T> listener) {
		return listenWith(listener, STRONG_REFERENCE);
	}

	private T listenWith(SynchronousListener<? super T> listener, ReferenceStrength strategy) {
		while (true) {
			final var oldState = waitUntilInitializedToGetState();
			final var oldForwarders = oldState.forwarders;
			final var oldValue = oldState.value;
			final var newForwarder = forward(oldValue);
			final var newForwarders = oldForwarders.with(listener, strategy, newForwarder);
			if (newForwarders == oldForwarders) {
				return oldForwarders.get(listener);
			}
			final var newState = oldState.with(newForwarders);
			if (tryToChangeState(oldState, newState)) {
				CLEANER.register(listener, this);
				return newForwarder;
			}
		}
	}

	protected T forward(T value) {
		return value;
	}

	@Override
	public final Optional<? extends T> relistenWith(Listener<? super T> listener) {
		return listener.relistenTo(this);
	}

	@Override
	public final Optional<T> relistenWith(SynchronousListener<? super T> listener) {
		final var forwarder = waitUntilInitializedToGetState().forwarders.get(listener);
		return ofNullable(forwarder);
	}

	@Override
	public final boolean unlistenWith(Listener<? super T> listener) {
		return listener.unlistenTo(this);
	}

	@Override
	public final boolean unlistenWith(SynchronousListener<? super T> listener) {
		while (true) {
			final var oldState = waitUntilInitializedToGetState();
			final var oldForwarders = oldState.forwarders;
			final var newForwarders = oldForwarders.without(listener);
			if (oldForwarders == newForwarders) {
				return false;
			}
			final var newState = oldState.with(newForwarders);
			if (tryToChangeState(oldState, newState)) {
				return true;
			}
		}
	}

	protected final T update(UnaryOperator<T> action) {
		return update(action, RETURN_NEW_VALUE);
	}

	protected final T update(UnaryOperator<T> action, ReturnValue returnValue) {
		while (true) {
			final var oldState = waitUntilInitializedToGetState();
			final var oldValue = oldState.value;
			final var newValue = action.apply(oldValue);
			final var newState = update(oldState, newValue);
			if (tryToChangeState(oldState, newState)) {
				return detach(switch (returnValue) {
					case RETURN_OLD_VALUE -> oldValue;
					case RETURN_NEW_VALUE -> newValue;
				});
			}
		}
	}

	protected final T update(T newValue) {
		return update(newValue, RETURN_NEW_VALUE);
	}

	protected final T update(T newValue, ReturnValue returnValue) {
		while (true) {
			final var oldState = waitUntilInitializedToGetState();
			final var oldValue = oldState.value;
			final var newState = update(oldState, newValue);
			if (tryToChangeState(oldState, newState)) {
				return detach(switch (returnValue) {
					case RETURN_OLD_VALUE -> oldValue;
					case RETURN_NEW_VALUE -> newValue;
				});
			}
		}
	}

	private State<T> update(State<T> oldState, T newValue) {
		final var oldValue = oldState.value;
		if (tryToSkipUpdate(oldValue, newValue)) {
			return oldState;
		}
		final var oldForwarders = oldState.forwarders;
		final var emitUpdateGlobally = new EmitUpdateGlobally(oldState.emission, oldValue, newValue);
		if (tryToReplace(oldValue, newValue)) {
			return new State<>(newValue, oldForwarders, emitUpdateGlobally);
		}
		final var newEmissions = new AtomicReference<Emission>(emitUpdateGlobally);
		final var newForwarders = oldForwarders.map(entry -> {
			final var oldForwarder = entry.value();
			if (tryToRedelegate(oldForwarder, newValue)) {
				return oldForwarder;
			} else {
				final var newForwarder = forward(newValue);
				newEmissions.updateAndGet(emission -> new EmitUpdateToListener<>(emission, entry, newForwarder));
				return newForwarder;
			}
		});
		return new State<>(newValue, newForwarders, newEmissions.get());
	}

	protected boolean tryToReplace(T oldValue, T newValue) {
		return false;
	}

	protected boolean tryToRedelegate(T oldValue, T newValue) {
		return false;
	}

	protected boolean tryToSkipUpdate(T oldValue, T newValue) {
		return newValue.equals(oldValue);
	}

	private boolean tryToChangeState(State<T> oldState, State<T> newState) {
		if (STATE_UPDATER.compareAndSet(this, oldState, newState)) {
			executor.execute(this);
			return true;
		}
		return false;
	}

	@SuppressWarnings("rawtypes")
	private static final AtomicIntegerFieldUpdater<
		AbstractListenable
	> EMITTING = newUpdater(AbstractListenable.class, "emitting");

	private volatile int emitting = 0;

	private static final Set<AbstractListenable<?>> STRONG_REFERENCES_TO_OBSERVED = newKeySet();
	private static final Logger LOGGER = getLogger(AbstractListenable.class);

	@Override
	public final void run() {
		if (EMITTING.compareAndSet(this, 0, 1)) {
			try {
				while (true) {
					final var oldState = waitUntilInitializedToGetState();
					final var oldForwarders = oldState.forwarders;
					final var newForwarders = oldForwarders.clean();
					if (newForwarders == oldForwarders && oldState.emission == null) {
						if (oldForwarders.isEmpty()) {
							STRONG_REFERENCES_TO_OBSERVED.remove(this);
						} else {
							STRONG_REFERENCES_TO_OBSERVED.add(this);
						}
						return;
					}
					final var newState = new State<>(oldState.value, newForwarders, null);
					if (STATE_UPDATER.compareAndSet(this, oldState, newState)) {
						var emission = oldState.emission;
						if (emission == null) {
							continue;
						}
						while (true) {
							var previousEmission = emission.getPrevious();
							if (previousEmission == null) {
								break;
							}
							previousEmission.setNext(emission);
							emission = previousEmission;
						}
						while (emission != null) {
							try {
								emission.emit();
							} catch (Throwable throwable) {
								LOGGER.error("Could emit update of {}!", AbstractListenable.this, throwable);
							}
							emission = emission.getNext();
						}
					}
				}
			} finally {
				EMITTING.set(this, 0);
			}
		}
	}

	private State<T> waitUntilInitializedToGetState() {
		try {
			while (true) {
				final var oldState = this.state;
				final var oldLatch = oldState.latch;
				if (oldLatch != null) {
					oldLatch.await();
					continue;
				}
				final var oldValue = oldState.value;
				if (oldValue == null) {
					final var newLatch = new CountDownLatch(1);
					final var newState = new State<>(newLatch);
					if (STATE_UPDATER.compareAndSet(this, oldState, newState)) {
						newLatch.await();
						continue;
					} else {
						continue;
					}
				}
				return oldState;
			}
		} catch (InterruptedException exception) {
			throw unchecked(exception);
		}
	}

}