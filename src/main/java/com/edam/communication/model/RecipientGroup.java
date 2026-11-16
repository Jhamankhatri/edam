package com.edam.communication.model;

/** Controlled set of recipient groups that an administrator may enable per event. */
public enum RecipientGroup {
	REQUESTER,
	ASSIGNED_APPROVER,
	DEPARTMENT_HEAD,
	WORKFLOW_PARTICIPANTS,
	DESIGNATED_ADMINISTRATORS,
	FORWARDED_RECIPIENT
}
