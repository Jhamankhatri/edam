package com.edam.institution;

import com.edam.common.exception.ResourceNotFoundException;

/** Raised when an institution code has no corresponding active record. */
public class InstitutionNotFoundException extends ResourceNotFoundException {

	public InstitutionNotFoundException(String code) {
		super("Institution '" + code + "'");
	}

}
