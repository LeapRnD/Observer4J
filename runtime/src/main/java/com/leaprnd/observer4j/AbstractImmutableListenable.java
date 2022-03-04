package com.leaprnd.observer4j;

import java.util.Optional;

import static java.util.Optional.of;

public interface AbstractImmutableListenable<T> extends Listenable<T> {

	@Override
	default T listenWith(Listener<? super T> listener) {
		return takeSnapshot();
	}

	@Override
	default Optional<? extends T> relistenWith(Listener<? super T> listener) {
		return of(takeSnapshot());
	}

	@Override
	default boolean unlistenWith(Listener<? super T> listener) {
		return false;
	}

	@Override
	default T listenWith(WeakSynchronousListener<? super T> listener) {
		return takeSnapshot();
	}

	@Override
	default T listenWith(StrongSynchronousListener<? super T> listener) {
		return takeSnapshot();
	}

	@Override
	default Optional<? extends T> relistenWith(SynchronousListener<? super T> listener) {
		return of(takeSnapshot());
	}

	@Override
	default boolean unlistenWith(SynchronousListener<? super T> listener) {
		return false;
	}

}
