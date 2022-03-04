package com.leaprnd.observer4j;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import static com.leaprnd.observer4j.AbstractRefreshable.State.CLOSED;
import static com.leaprnd.observer4j.AbstractRefreshable.State.FROZEN;
import static com.leaprnd.observer4j.AbstractRefreshable.State.REFRESHING;
import static com.leaprnd.observer4j.AbstractRefreshable.State.WAITING;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

public abstract class AbstractRefreshable {

	private static final AtomicIntegerFieldUpdater<AbstractRefreshable> COUNTER_UPDATER;
	private static final AtomicReferenceFieldUpdater<AbstractRefreshable, State> STATE_UPDATER;

	static {
		COUNTER_UPDATER = newUpdater(AbstractRefreshable.class, "counter");
		STATE_UPDATER = newUpdater(AbstractRefreshable.class, State.class, "state");
	}

	enum State {
		WAITING,
		FROZEN,
		REFRESHING,
		CLOSED
	}

	protected final Executor executor;

	private volatile int counter = 0;
	private volatile State state = WAITING;

	protected AbstractRefreshable(Executor executor) {
		this.executor = executor;
	}

	public final void requestAsynchronousRefresh() {
		if (tryToRequestRefresh()) {
			executor.execute(this::refreshIfNecessary);
		}
	}

	public final void refreshSynchronousRefresh() {
		if (tryToRequestRefresh()) {
			refreshIfNecessary();
		}
	}

	private boolean tryToRequestRefresh() {
		final var state = STATE_UPDATER.get(this);
		if (state == CLOSED) {
			return false;
		}
		COUNTER_UPDATER.incrementAndGet(this);
		return state != FROZEN;
	}

	public final boolean freeze() {
		return STATE_UPDATER.compareAndSet(this, WAITING, FROZEN);
	}

	public final boolean thaw() {
		if (STATE_UPDATER.compareAndSet(this, FROZEN, WAITING)) {
			executor.execute(this::refreshIfNecessary);
			return true;
		} else {
			return false;
		}
	}

	private void refreshIfNecessary() {
		if (STATE_UPDATER.compareAndSet(this, WAITING, REFRESHING)) {
			try {
				while (true) {
					final int numberOfRequests = COUNTER_UPDATER.getAndSet(this, 0);
					if (numberOfRequests == 0) {
						break;
					}
					refresh(numberOfRequests);
				}
			} finally {
				while (true) {
					final var oldState = STATE_UPDATER.get(this);
					if (oldState == CLOSED) {
						cleanup();
						break;
					}
					if (oldState == REFRESHING) {
						if (STATE_UPDATER.compareAndSet(this, oldState, WAITING)) {
							break;
						} else {
							continue;
						}
					}
					throw new IllegalStateException("This should not be possible");
				}
			}
		}
	}

	protected abstract void refresh(int numberOfRequests);

	public final boolean isOpen() {
		return STATE_UPDATER.get(this) != CLOSED;
	}

	public final boolean isClosed() {
		return STATE_UPDATER.get(this) == CLOSED;
	}

	public boolean close() {
		final var oldState = STATE_UPDATER.getAndSet(this, CLOSED);
		return switch (oldState) {
			case WAITING, FROZEN -> {
				cleanup();
				yield true;
			}
			case REFRESHING -> true;
			case CLOSED -> false;
		};
	}

	protected void cleanup() {

	}

}
