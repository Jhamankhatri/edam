package com.edam.communication.model;

/**
 * Closed set of events Phase 4D publishes. These are trusted business events, not
 * administrator-supplied strings. {@link NotificationEvent} mirrors this enum in DB-visible
 * form through {@code notification_event_configs}.
 */
public enum CommunicationEvent {

	EXPENSE_SUBMITTED("An expense was submitted for approval", false),
	APPROVAL_REQUIRED("An approval task is awaiting your action", true),
	EXPENSE_APPROVED("Your expense was approved", false),
	EXPENSE_REJECTED("Your expense was rejected", false),
	EXPENSE_RETURNED("Your expense was returned for revision", false),
	EXPENSE_FORWARDED("An approval was forwarded", false),
	EXPENSE_CANCELLED("A request you were part of was cancelled", false);

	private final String description;
	/** True when the event is mandatory (cannot be disabled by user preferences). */
	private final boolean mandatory;

	CommunicationEvent(String description, boolean mandatory) {
		this.description = description;
		this.mandatory = mandatory;
	}

	public String description() { return this.description; }
	public boolean isMandatory() { return this.mandatory; }
}
