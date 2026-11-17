package com.edam.integrations.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hibernate.generator.EventType;

/**
 * A configured integration endpoint/credential bundle.
 *
 * <p>Lifecycle: create → (optionally) test → activate → execute. Deactivation prevents new
 * executions but preserves history; soft-delete (archived) hides the configuration from the
 * catalogue while retaining execution history (foreign key is ON DELETE RESTRICT).
 *
 * <p>The secret is stored encrypted at rest by {@link com.edam.integrations.crypto.IntegrationSecretCrypto}.
 * {@code secretReference} is reserved for future external secret-manager integration and is
 * always {@code null} in this phase.
 */
@Entity
@Table(name = "integration_configs", indexes = {
	@Index(name = "ix_integration_configs_active", columnList = "integration_type,is_active")
})
public class IntegrationConfig {

	@Id
	@Generated(event = EventType.INSERT)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "integration_code", nullable = false, length = 64, unique = true)
	private String code;

	@Column(name = "integration_name", nullable = false, length = 200)
	private String name;

	/** Registered adapter/provider identifier (e.g. "STUB_HTTP" is the only test adapter shipped). */
	@Column(name = "integration_type", nullable = false, length = 40)
	private String providerType;

	@Column(name = "description", length = 1000)
	private String description;

	@Column(name = "endpoint_url", length = 500)
	private String endpointUrl;

	@Enumerated(EnumType.STRING)
	@Column(name = "authentication_type", nullable = false, length = 40)
	private AuthType authenticationType = AuthType.NONE;

	/** Reserved for future external secret manager integration. Null in this phase. */
	@Column(name = "secret_reference", length = 300)
	private String secretReference;

	/** AES-256-GCM ciphertext (Base64). Never returned to API clients. */
	@Column(name = "encrypted_secret")
	private String encryptedSecret;

	/** Incremented when the secret is rotated. */
	@Column(name = "secret_version", nullable = false)
	private int secretVersion = 0;

	/** Adapter-specific non-sensitive settings; validated by the adapter on write. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "configuration", columnDefinition = "jsonb")
	private String configurationJson;

	@Column(name = "is_active", nullable = false)
	private boolean active = false;

	@Column(name = "archived", nullable = false)
	private boolean archived = false;

	@Enumerated(EnumType.STRING)
	@Column(name = "connection_status", nullable = false, length = 20)
	private ConnectionStatus connectionStatus = ConnectionStatus.UNKNOWN;

	@Column(name = "last_test_at")
	private Instant lastTestAt;

	@Column(name = "last_test_success")
	private Boolean lastTestSuccess;

	@Column(name = "last_test_message", length = 500)
	private String lastTestMessage;

	@Column(name = "last_test_duration_ms")
	private Integer lastTestDurationMs;

	@Column(name = "connection_checked_at")
	private Instant connectionCheckedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "created_by", length = 100)
	private String createdBy;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "updated_by", length = 100)
	private String updatedBy;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	@PrePersist void onCreate() {
		Instant now = Instant.now();
		if (this.createdAt == null) this.createdAt = now;
		this.updatedAt = now;
	}
	@PreUpdate void onUpdate() { this.updatedAt = Instant.now(); }

	public UUID getId() { return id; }
	public void setId(UUID id) { this.id = id; }
	public String getCode() { return code; }
	public void setCode(String code) { this.code = code; }
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public String getProviderType() { return providerType; }
	public void setProviderType(String providerType) { this.providerType = providerType; }
	public String getDescription() { return description; }
	public void setDescription(String description) { this.description = description; }
	public String getEndpointUrl() { return endpointUrl; }
	public void setEndpointUrl(String endpointUrl) { this.endpointUrl = endpointUrl; }
	public AuthType getAuthenticationType() { return authenticationType; }
	public void setAuthenticationType(AuthType authenticationType) { this.authenticationType = authenticationType; }
	public String getSecretReference() { return secretReference; }
	public void setSecretReference(String secretReference) { this.secretReference = secretReference; }
	public String getEncryptedSecret() { return encryptedSecret; }
	public void setEncryptedSecret(String encryptedSecret) { this.encryptedSecret = encryptedSecret; }
	public int getSecretVersion() { return secretVersion; }
	public void setSecretVersion(int secretVersion) { this.secretVersion = secretVersion; }
	public String getConfigurationJson() { return configurationJson; }
	public void setConfigurationJson(String configurationJson) { this.configurationJson = configurationJson; }
	public boolean isActive() { return active; }
	public void setActive(boolean active) { this.active = active; }
	public boolean isArchived() { return archived; }
	public void setArchived(boolean archived) { this.archived = archived; }
	public ConnectionStatus getConnectionStatus() { return connectionStatus; }
	public void setConnectionStatus(ConnectionStatus connectionStatus) { this.connectionStatus = connectionStatus; }
	public Instant getLastTestAt() { return lastTestAt; }
	public void setLastTestAt(Instant lastTestAt) { this.lastTestAt = lastTestAt; }
	public Boolean getLastTestSuccess() { return lastTestSuccess; }
	public void setLastTestSuccess(Boolean lastTestSuccess) { this.lastTestSuccess = lastTestSuccess; }
	public String getLastTestMessage() { return lastTestMessage; }
	public void setLastTestMessage(String lastTestMessage) { this.lastTestMessage = lastTestMessage; }
	public Integer getLastTestDurationMs() { return lastTestDurationMs; }
	public void setLastTestDurationMs(Integer lastTestDurationMs) { this.lastTestDurationMs = lastTestDurationMs; }
	public Instant getConnectionCheckedAt() { return connectionCheckedAt; }
	public void setConnectionCheckedAt(Instant connectionCheckedAt) { this.connectionCheckedAt = connectionCheckedAt; }
	public Instant getCreatedAt() { return createdAt; }
	public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
	public String getCreatedBy() { return createdBy; }
	public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
	public Instant getUpdatedAt() { return updatedAt; }
	public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
	public String getUpdatedBy() { return updatedBy; }
	public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
	public long getVersion() { return version; }
	public void setVersion(long version) { this.version = version; }

	public enum AuthType { NONE, BASIC, BEARER, OAUTH2, CLIENT_CERTIFICATE, LDAP_BIND, API_KEY }

	public enum ConnectionStatus { UNKNOWN, UNCONFIGURED, ACTIVE, INACTIVE, FAILED, TESTING }
}
