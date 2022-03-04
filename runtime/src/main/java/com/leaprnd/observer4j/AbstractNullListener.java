package com.leaprnd.observer4j;

import java.util.Optional;

import static java.util.Optional.empty;

public interface AbstractNullListener<T> extends Listener<T> {

	@Override
	default <X extends T> X listenTo(Listenable<X> listenable) {
		return listenable.takeSnapshot();
	}

	@Override
	default <X extends T> Optional<? extends X> relistenTo(Listenable<X> listenable) {
		return empty();
	}

	@Override
	default <X extends T> boolean unlistenTo(Listenable<X> listenable) {
		return false;
	}

}