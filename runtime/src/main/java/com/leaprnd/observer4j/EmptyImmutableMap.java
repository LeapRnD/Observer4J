package com.leaprnd.observer4j;

import java.util.function.Function;

@SuppressWarnings({ "rawtypes", "unchecked" })
enum EmptyImmutableMap implements ImmutableMap {

	EMPTY_IMMUTABLE_MAP;

	public static <T> ImmutableMap<T> emptyImmutableMap() {
		return EMPTY_IMMUTABLE_MAP;
	}

	@Override
	public Object get(SynchronousListener listener) {
		return null;
	}

	@Override
	public ImmutableMap with(SynchronousListener listener, ReferenceStrength strategy, Object value) {
		return new NonEmptyImmutableMap(strategy.toEntry(listener, value));
	}

	@Override
	public ImmutableMap without(SynchronousListener listener) {
		return this;
	}

	@Override
	public boolean isEmpty() {
		return true;
	}

	@Override
	public ImmutableMap clean() {
		return this;
	}

	@Override
	public ImmutableMap map(Function mapper) {
		return this;
	}

}
