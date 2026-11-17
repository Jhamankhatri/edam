#!/usr/bin/env python3
"""Generate Phase 4J documentation from introspected data."""
import json, os, sys
from pathlib import Path

ROOT = Path("/home/user/edam")
OUT = ROOT / "docs/phase-4j"
GEN = OUT / "_generated"
controllers = json.loads((GEN/"controllers.json").read_text())
permissions = json.loads((GEN/"permissions.json").read_text())
entities = json.loads((GEN/"entities.json").read_text())
migrations = json.loads((GEN/"migrations.json").read_text())

perm_by_const = {p["const"]: p["code"] for p in permissions}
perm_by_code = {p["code"]: p["const"] for p in permissions}

MODULE_MAP = {
    "AuthController": ("identity", "Authentication & Session"),
    "SystemController": ("web", "System/Info"),
    "UserController": ("usermanagement", "User Management"),
    "RoleController": ("rolemanagement", "Roles & Permissions"),
    "OrganizationController": ("organization", "Organization Structure"),
    "MasterDataController": ("masterdata", "Master Data (Currencies, Vendors, Tax Authorities)"),
    "ExpenseConfigurationController": ("expenseconfiguration", "Expense Categories/Configuration"),
    "ExpenseRequestController": ("expenserequest", "OPEX/CAPEX Request Lifecycle"),
    "ApprovalLimitController": ("approvallimits", "Department Approval Limits"),
    "ExceedingLimitRuleController": ("approvallimits", "Exceeding-Limit Rules"),
    "RecommendingAuthorityController": ("approvallimits", "Recommending Authorities"),
    "DelegationMatrixController": ("delegation", "Delegation Matrix"),
    "CommitteeController": ("committee", "Expense Committees"),
    "WorkflowConfigurationController": ("workflow", "Workflow Configuration & Publishing"),
    "ApprovalController": ("workflow", "Approval Inbox & Actions"),
    "BusinessRuleController": ("businessrules", "Business Rules"),
    "DocumentController": ("documents", "Document Management"),
    "SmtpConfigurationController": ("communication", "SMTP Configuration"),
    "EmailTemplateController": ("communication", "Email Templates"),
    "NotificationEventConfigController": ("communication", "Notification Event Triggers"),
    "NotificationInboxController": ("communication", "Notification Inbox"),
    "ConfigurationController": ("configuration", "Branding, Themes & Application Settings"),
    "ReportingController": ("reporting", "Dashboard & Reporting"),
    "AuditController": ("audit", "Audit History"),
    "RequestTimelineController": ("audit", "Request Timeline"),
    "IntegrationController": ("integrations", "Integration Configuration & Execution"),
}

def per_module_endpoints():
    mod = {}
    for ctrl, data in controllers.items():
        m = MODULE_MAP.get(ctrl, ("other", ctrl))[0]
        mod.setdefault(m, []).append((ctrl, data))
    return mod

# Map module name -> folder mapping for postman
FOLDER_MAP = {
    "AuthController": "Authentication and Session",
    "SystemController": "Authentication and Session",
    "UserController": "Users and User Management",
    "RoleController": "Roles and Permissions",
    "OrganizationController": "Organization Management",
    "MasterDataController": "Master Data",
    "ExpenseConfigurationController": "Expense Configuration",
    "ExpenseRequestController": "OPEX/CAPEX",  # split later by path
    "ApprovalLimitController": "Department Approval Limits",
    "ExceedingLimitRuleController": "Delegation and Recommending Authorities",
    "RecommendingAuthorityController": "Delegation and Recommending Authorities",
    "DelegationMatrixController": "Delegation and Recommending Authorities",
    "CommitteeController": "Expense Committees",
    "WorkflowConfigurationController": "Workflow Configuration",
    "ApprovalController": "Approval Inbox",
    "BusinessRuleController": "Business Rules",
    "DocumentController": "Document Management",
    "SmtpConfigurationController": "SMTP Configuration",
    "EmailTemplateController": "Email Templates and Triggers",
    "NotificationEventConfigController": "Email Templates and Triggers",
    "NotificationInboxController": "Notifications",
    "ConfigurationController": "Branding and Themes",
    "ReportingController": "Dashboard and Reporting",
    "AuditController": "Audit",
    "RequestTimelineController": "Audit",
    "IntegrationController": "Integrations",
}

print("Loaded:", len(controllers), "controllers,", len(permissions), "permissions,", len(entities), "entities,", len(migrations), "migrations")
