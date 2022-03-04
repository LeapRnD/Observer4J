package com.leaprnd.observer4j;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

import static java.lang.Integer.MAX_VALUE;
import static java.lang.Integer.numberOfLeadingZeros;

public abstract class AbstractSubstitutableForwarder<T> {

	private static final VarHandle VALUE;

	static {
		try {
			VALUE = MethodHandles.lookup().findVarHandle(AbstractSubstitutableForwarder.class, "state", State.class);
		} catch (ReflectiveOperationException exception) {
			throw new ExceptionInInitializerError(exception);
		}
	}

	private record State<T> (T delegate, int flagsOfFields) {
		private State<T> with(T newDelegate) {
			return new State<>(newDelegate, flagsOfFields);
		}
		private State<T> with(int newFlagsOfFields) {
			return new State<>(delegate, newFlagsOfFields);
		}
	}

	private volatile State<T> state;

	public AbstractSubstitutableForwarder(T delegate) {
		state = new State<>(delegate, 0);
	}

	protected abstract boolean areFieldsEqual(T oldDelegate, T newDelegate, int indexOfField);
	protected abstract int getNumberOfFields();

	protected final T getDelegateWithoutRecordingAccess() {
		return state.delegate;
	}

	protected final T recordAccessToField(int indexOfField) {
		while (true) {
			final var oldState = state;
			final var flagsOfFields = oldState.flagsOfFields;
			final var flagOfField = 1 << indexOfField;
			if ((flagsOfFields & flagOfField) == flagOfField) {
				return oldState.delegate;
			}
			final var newState = oldState.with(flagsOfFields | flagOfField);
			if (VALUE.weakCompareAndSet(this, oldState, newState)) {
				return newState.delegate;
			}
		}
	}

	public final boolean tryToReplaceDelegate(T newDelegate) {
		while (true) {
			final var oldState = state;
			final var oldDelegate = oldState.delegate;
			var flagsOfFields = oldState.flagsOfFields;
			while (flagsOfFields > 0) {
				final var indexOfField = 31 - numberOfLeadingZeros(flagsOfFields);
				if (areFieldsEqual(oldDelegate, newDelegate, indexOfField)) {
					final var flagOfField = 1 << indexOfField;
					flagsOfFields &= ~flagOfField;
				} else {
					return false;
				}
			}
			final var newState = oldState.with(newDelegate);
			if (VALUE.weakCompareAndSet(this, oldState, newState)) {
				return true;
			}
		}
	}

	@Override
	public int hashCode() {
		return recordAccessToEveryField().hashCode();
	}

	@Override
	public boolean equals(Object other) {
		if (other == null) {
			return false;
		}
		if (other == this) {
			return true;
		}
		if (other instanceof AbstractSubstitutableForwarder otherProperties) {
			return delegateIsEqualTo(otherProperties.recordAccessToEveryField());
		} else {
			return delegateIsEqualTo(other);
		}
	}

	private boolean delegateIsEqualTo(Object object) {
		return recordAccessToEveryField().equals(object);
	}

	@Override
	public String toString() {
		return recordAccessToEveryField().toString();
	}

	protected final T recordAccessToEveryField() {
		final var flagsOfEveryField = MAX_VALUE / (1 << (31 - getNumberOfFields()));
		while (true) {
			final var oldState = state;
			final var newState = oldState.with(flagsOfEveryField);
			if (VALUE.weakCompareAndSet(this, oldState, newState)) {
				return newState.delegate;
			}
		}
	}

}
