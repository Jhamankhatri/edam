package com.edam.communication.service;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.edam.communication.model.CommunicationEvent;
import com.edam.communication.model.NotificationPreferenceEvent;
import com.edam.communication.repository.NotificationPreferenceEventRepository;
import com.edam.communication.repository.NotificationPreferenceRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lookup helper for per-user notification opt-outs.
 *
 * <p>Phase 4D delivers all email as immediate (no digest); the {@code email_digest} column on
 * {@code comm_notification_preferences} is reserved for a future {@code DAILY} roll-up and is
 * not consulted here. Opt-outs are per (user, event, channel):
 * <ul>
 *   <li>If no preference row exists, defaults are both channels ON.</li>
 *   <li>Mandatory events (see {@link CommunicationEvent#isMandatory()}) are always delivered —
 *       an opt-out row is ignored for them.</li>
 *   <li>Otherwise the per-event flags in {@code comm_notification_preference_events} apply; if
 *       no per-event row exists, the user-level default applies.</li>
 * </ul>
 *
 * <p>The service caches nothing across requests; preferences are read on every dispatch. A
 * small memoization inside {@link #channelEnabled} keeps repeated reads in the same dispatch
 * path cheap.
 */
@Service
public class NotificationPreferenceService {

	private final NotificationPreferenceRepository preferences;
	private final NotificationPreferenceEventRepository eventPrefs;

	public NotificationPreferenceService(NotificationPreferenceRepository preferences,
			NotificationPreferenceEventRepository eventPrefs) {
		this.preferences = preferences;
		this.eventPrefs = eventPrefs;
	}

	public boolean emailEnabled(UUID userId, CommunicationEvent event) {
		if (event.isMandatory()) return true;
		return channelEnabled(userId, event, true);
	}

	public boolean inAppEnabled(UUID userId, CommunicationEvent event) {
		if (event.isMandatory()) return true;
		return channelEnabled(userId, event, false);
	}

	@Transactional(readOnly = true)
	protected boolean channelEnabled(UUID userId, CommunicationEvent event, boolean email) {
		var prefs = preferences.findByUserId(userId).orElse(null);
		boolean userDefault = true;
		if (prefs != null && !email && !prefs.isInAppEnabled()) {
			userDefault = false;
		}
		if (prefs == null) return userDefault;
		// Per-event override (if any have been stored).
		for (NotificationPreferenceEvent ev : eventPrefs.findByPreferenceId(prefs.getId())) {
			if (event.name().equals(ev.getEventCode())) {
				return email ? ev.isEmailEnabled() : ev.isInAppEnabled();
			}
		}
		return userDefault;
	}
}
