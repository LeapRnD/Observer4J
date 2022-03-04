package com.leaprnd.observer4j;

import static java.util.Objects.requireNonNull;

public record ImmutableListenable<T> (T value) implements AbstractImmutableListenable<T> {

	public ImmutableListenable {
		requireNonNull(value);
	}

	@Override
	public T takeSnapshot() {
		return value;
	}

}
