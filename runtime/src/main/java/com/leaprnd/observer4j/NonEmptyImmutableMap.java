package com.leaprnd.observer4j;

import java.lang.reflect.Array;
import java.util.function.Function;

import static com.leaprnd.observer4j.EmptyImmutableMap.emptyImmutableMap;
import static java.lang.System.arraycopy;
import static java.lang.System.identityHashCode;
import static java.util.Arrays.copyOf;

final class NonEmptyImmutableMap<T> implements ImmutableMap<T> {

	private final ImmutableMapEntry<T>[] entries;

	@SafeVarargs
	NonEmptyImmutableMap(ImmutableMapEntry<T> ... entries) {
		this.entries = entries;
	}

	@Override
	public T get(SynchronousListener<? super T> listener) {
		var index = binarySearch(listener);
		if (index < 0) {
			return null;
		}
		return entries[index].value();
	}

	@Override
	public NonEmptyImmutableMap<T> with(SynchronousListener<? super T> listener, ReferenceStrength strategy, T value) {
		int index = -binarySearch(listener) - 1;
		if (index < 0) {
			return this;
		}
		final var entry = strategy.toEntry(listener, value);
		if (entry.listener() != listener) {
			throw new IllegalArgumentException(
				"The entry returned by ImmutableReferenceMapEntryFactory.build() cannot have a different listener!"
			);
		}
		final var length = entries.length + 1;
		final var newEntries = newArray(entries, length);
		arraycopy(entries, 0, newEntries, 0, index);
		newEntries[index] = entry;
		arraycopy(entries, index, newEntries, index + 1, entries.length - index);
		return new NonEmptyImmutableMap<>(newEntries);
	}

	@Override
	public ImmutableMap<T> without(SynchronousListener<? super T> listener) {
		final var index = binarySearch(listener);
		if (index < 0) {
			return this;
		}
		final var newEntries = copyWithoutIndex(entries, index);
		if (newEntries == null) {
			return emptyImmutableMap();
		}
		return new NonEmptyImmutableMap<>(newEntries);
	}

	@Override
	public boolean isEmpty() {
		return false;
	}

	@Override
	public ImmutableMap<T> clean() {
		final var oldEntries = entries;
		final var oldLength = oldEntries.length;
		var newLength = oldLength;
		for (final var oldEntry : oldEntries) {
			if (oldEntry.wasListenerGarbageCollected()) {
				newLength --;
			}
		}
		if (newLength == oldLength) {
			return this;
		}
		if (newLength == 0) {
			return emptyImmutableMap();
		}
		final var newEntries = newArray(oldEntries, newLength);
		var index = 0;
		var entriesToRemove = oldLength - newLength;
		for (final var oldEntry : oldEntries) {
			if (entriesToRemove > 0 && oldEntry.wasListenerGarbageCollected()) {
				entriesToRemove --;
				continue;
			}
			newEntries[index] = oldEntry;
			index ++;
		}
		return new NonEmptyImmutableMap<>(newEntries);
	}

	@Override
	public ImmutableMap<T> map(Function<ImmutableMapEntry<T>, T> mapper) {
		ImmutableMapEntry<T>[] newEntries = null;
		final var length = entries.length;
		var index = length;
		while (--index >= 0) {
			final var entry = entries[index];
			final var listener = entry.listener();
			if (listener == null) {
				continue;
			}
			final var oldValue = entry.value();
			final var newValue = mapper.apply(entry);
			if (oldValue == newValue) {
				continue;
			}
			if (newEntries == null) {
				newEntries = copyOf(entries, length);
			}
			newEntries[index] = entry.with(newValue);
		}
		if (newEntries == null) {
			return this;
		}
		return new NonEmptyImmutableMap<>(newEntries);
	}

	private int binarySearch(SynchronousListener<? super T> listener) {
		final var hashCodeOfListener = identityHashCode(listener);
		var low = 0;
		var high = entries.length - 1;
		while (low <= high) {
			var middle = (low + high) >>> 1;
			var entry = entries[middle];
			var comparison = entry.identityHashCodeOfListener() - hashCodeOfListener;
			if (comparison < 0) {
				low = middle + 1;
			} else if (comparison > 0) {
				high = middle - 1;
			} else {
				return middle;
			}
		}
		return -(low + 1);
	}

	private static <T> T[] copyWithoutIndex(T[] previous, int index) {
		final var nextLength = previous.length - 1;
		if (nextLength == 0) {
			return null;
		}
		final var next = newArray(previous, nextLength);
		if (index > 0) {
			arraycopy(previous, 0, next, 0, index);
		}
		if (index != nextLength) {
			arraycopy(previous, index + 1, next, index, nextLength - index);
		}
		return next;
	}

	private static <T> T[] newArray(T[] reference, int length) {
		Class<?> type = reference.getClass().getComponentType();
		@SuppressWarnings("unchecked")
		T[] result = (T[]) Array.newInstance(type, length);
		return result;
	}

}
