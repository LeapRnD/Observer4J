package com.leaprnd.observer4j;

enum ReferenceStrength {

	STRONG_REFERENCE,
	WEAK_REFERENCE;

	<T> ImmutableMapEntry<T> toEntry(SynchronousListener<? super T> listener, T value) {
		return switch (this) {
			case STRONG_REFERENCE -> new StrongImmutableMapEntry<>(listener, value);
			case WEAK_REFERENCE -> new WeakImmutableMapEntry<>(listener, value);
		};
	}

}