package com.edam.communication.model;

public enum OutboxStatus {
	PENDING,
	PROCESSING,
	SENT,
	FAILED,
	RETRY_PENDING
}
