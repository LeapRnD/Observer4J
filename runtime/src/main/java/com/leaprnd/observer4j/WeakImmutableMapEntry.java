package com.leaprnd.observer4j;

import java.lang.ref.WeakReference;

import static java.lang.System.identityHashCode;

final class WeakImmutableMapEntry<
	T
> extends WeakReference<SynchronousListener<? super T>> implements ImmutableMapEntry<T> {

	private final int identityHashCodeOfListener;
	private final T value;

	public WeakImmutableMapEntry(SynchronousListener<? super T> listener, T value) {
		this(listener, value, identityHashCode(listener));
	}

	private WeakImmutableMapEntry(SynchronousListener<? super T> listener, T value, int identityHashCodeOfListener) {
		super(listener);
		this.value = value;
		this.identityHashCodeOfListener = identityHashCodeOfListener;
	}

	@Override
	public SynchronousListener<? super T> listener() {
		return get();
	}

	@Override
	public T value() {
		return value;
	}

	@Override
	public int identityHashCodeOfListener() {
		return identityHashCodeOfListener;
	}

	@Override
	public boolean wasListenerGarbageCollected() {
		return get() == null;
	}

	@Override
	public ImmutableMapEntry<T> with(T newValue) {
		return new WeakImmutableMapEntry<>(get(), newValue, identityHashCodeOfListener);
	}

}
