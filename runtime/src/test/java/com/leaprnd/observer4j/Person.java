package com.leaprnd.observer4j;

import java.util.concurrent.Executor;

class Person extends AbstractListenable<PersonPropertiesForwarder> {

	public Person(PersonPropertiesForwarder initialValue) {
		super(initialValue);
	}

	public Person() {}

	public Person(Executor executor) {
		super(executor);
	}

	public Person(Executor executor, PersonPropertiesForwarder initialValue) {
		super(executor, initialValue);
	}

	@Override
	protected PersonPropertiesForwarder detach(PersonPropertiesForwarder value) {
		return new PersonPropertiesForwarder(value.getDelegateWithoutRecordingAccess());
	}

	@Override
	protected PersonPropertiesForwarder forward(PersonPropertiesForwarder value) {
		return new PersonPropertiesForwarder(value);
	}

	@Override
	protected boolean tryToReplace(PersonPropertiesForwarder oldValue, PersonPropertiesForwarder newValue) {
		return oldValue.tryToReplaceDelegate(newValue.getDelegateWithoutRecordingAccess());
	}

	@Override
	protected boolean tryToRedelegate(PersonPropertiesForwarder oldValue, PersonPropertiesForwarder newValue) {
		return oldValue.tryToReplaceDelegate(newValue);
	}

	@Override
	protected boolean tryToSkipUpdate(PersonPropertiesForwarder oldValue, PersonPropertiesForwarder newValue) {
		return oldValue.version() > newValue.version();
	}

}
