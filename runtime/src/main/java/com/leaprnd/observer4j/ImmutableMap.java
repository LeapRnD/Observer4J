package com.leaprnd.observer4j;

import java.util.function.Function;

sealed interface ImmutableMap<T> permits EmptyImmutableMap,NonEmptyImmutableMap {
	T get(SynchronousListener<? super T> listener);
	ImmutableMap<T> with(SynchronousListener<? super T> listener, ReferenceStrength strategy, T value);
	ImmutableMap<T> without(SynchronousListener<? super T> listener);
	boolean isEmpty();
	ImmutableMap<T> clean();
	ImmutableMap<T> map(Function<ImmutableMapEntry<T>, T> mapper);
}