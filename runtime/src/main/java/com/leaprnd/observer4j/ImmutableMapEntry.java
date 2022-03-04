package com.leaprnd.observer4j;

sealed interface ImmutableMapEntry<T> permits StrongImmutableMapEntry,WeakImmutableMapEntry {
	SynchronousListener<? super T> listener();
	T value();
	int identityHashCodeOfListener();
	boolean wasListenerGarbageCollected();
	ImmutableMapEntry<T> with(T newValue);
}
