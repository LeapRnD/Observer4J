package com.leaprnd.observer4j;

import static java.lang.System.identityHashCode;

record StrongImmutableMapEntry<T> (SynchronousListener<? super T> listener, T value) implements ImmutableMapEntry<T> {

	@Override
	public int identityHashCodeOfListener() {
		return identityHashCode(listener);
	}

	@Override
	public boolean wasListenerGarbageCollected() {
		return false;
	}

	@Override
	public ImmutableMapEntry<T> with(T newValue) {
		return new StrongImmutableMapEntry<>(listener, newValue);
	}

}
