package com.edam.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Fine-grained platform capability permission code assigned to institution-configured roles.
 */
@Entity
@Table(name = "permissions")
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 100)
    private String code;

    @Column(name = "module_code", nullable = false, length = 50)
    private String moduleCode;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    protected Permission() {
    }

    public Permission(String code, String moduleCode, String description) {
        this.code = code;
        this.moduleCode = moduleCode;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getModuleCode() {
        return moduleCode;
    }

    public String getDescription() {
        return description;
    }
}
