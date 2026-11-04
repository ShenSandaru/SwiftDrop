import test from "node:test";
import assert from "node:assert/strict";
import { sanitizeInternalRedirect, getDefaultDashboardForRoles } from "./redirect.ts";

test("sanitizeInternalRedirect returns fallback for empty or root target", () => {
  assert.equal(sanitizeInternalRedirect(null, "/fallback"), "/fallback");
  assert.equal(sanitizeInternalRedirect(undefined, "/fallback"), "/fallback");
  assert.equal(sanitizeInternalRedirect("", "/fallback"), "/fallback");
  assert.equal(sanitizeInternalRedirect("   ", "/fallback"), "/fallback");
  assert.equal(sanitizeInternalRedirect("/", "/customer"), "/customer");
  assert.equal(sanitizeInternalRedirect("/", "/"), "/");
});

test("sanitizeInternalRedirect accepts safe internal paths", () => {
  assert.equal(sanitizeInternalRedirect("/customer"), "/customer");
  assert.equal(sanitizeInternalRedirect("/courier/parcels/123"), "/courier/parcels/123");
  assert.equal(sanitizeInternalRedirect("/dispatch?view=active&id=42"), "/dispatch?view=active&id=42");
  assert.equal(sanitizeInternalRedirect("/merchant#inventory"), "/merchant#inventory");
});

test("sanitizeInternalRedirect prevents open redirect vectors", () => {
  // Protocol-relative URLs
  assert.equal(sanitizeInternalRedirect("//attacker.com", "/fallback"), "/fallback");
  assert.equal(sanitizeInternalRedirect("//attacker.com/path", "/fallback"), "/fallback");

  // Backslash tricks
  assert.equal(sanitizeInternalRedirect("/\\attacker.com", "/fallback"), "/fallback");

  // Absolute external URLs
  assert.equal(sanitizeInternalRedirect("https://attacker.com", "/fallback"), "/fallback");
  assert.equal(sanitizeInternalRedirect("http://localhost:3000.attacker.com", "/fallback"), "/fallback");
  assert.equal(sanitizeInternalRedirect("javascript:alert(1)", "/fallback"), "/fallback");

  // Control characters & CRLF injection
  assert.equal(sanitizeInternalRedirect("/customer\r\nSet-Cookie:malicious", "/fallback"), "/fallback");
  assert.equal(sanitizeInternalRedirect("/customer\x00extra", "/fallback"), "/fallback");

  // Login loop prevention
  assert.equal(sanitizeInternalRedirect("/login", "/fallback"), "/fallback");
});

test("getDefaultDashboardForRoles maps roles accurately with safe fallback", () => {
  assert.equal(getDefaultDashboardForRoles(null), "/customer");
  assert.equal(getDefaultDashboardForRoles(undefined), "/customer");
  assert.equal(getDefaultDashboardForRoles([]), "/customer");

  assert.equal(getDefaultDashboardForRoles(["CUSTOMER"]), "/customer");
  assert.equal(getDefaultDashboardForRoles(["COURIER"]), "/courier");
  assert.equal(getDefaultDashboardForRoles(["MERCHANT"]), "/merchant");
  assert.equal(getDefaultDashboardForRoles(["DISPATCHER_ADMIN"]), "/dispatch");

  // Priority with multiple roles
  assert.equal(getDefaultDashboardForRoles(["CUSTOMER", "DISPATCHER_ADMIN"]), "/dispatch");
  assert.equal(getDefaultDashboardForRoles(["CUSTOMER", "COURIER"]), "/courier");
  assert.equal(getDefaultDashboardForRoles(["CUSTOMER", "MERCHANT"]), "/merchant");

  // Unknown role fallback
  assert.equal(getDefaultDashboardForRoles(["UNKNOWN_ROLE"]), "/customer");
});

