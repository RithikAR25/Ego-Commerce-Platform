package com.ego.raw_ego.common.util;

/**
 * Utility for masking Personally Identifiable Information (PII) in log output.
 *
 * <p>Email addresses are PII under GDPR, India's DPDP Act, and CCPA. Writing full email
 * addresses to application logs creates a secondary PII data store that may be shipped to
 * third-party log aggregation services and retained beyond the database retention policy.
 *
 * <p>This class provides masking helpers that preserve enough of the value for debugging
 * (e.g. confirming which user triggered an event) without exposing the full address.
 *
 * <p>Usage in log statements:
 * <pre>
 *   log.info("Registration attempt: email={}", LogMasker.maskEmail(normalizedEmail));
 *   // Output: "Registration attempt: email=ri***@***.com"
 * </pre>
 */
public final class LogMasker {

    private LogMasker() {
        // Utility class — no instantiation
    }

    /**
     * Masks an email address for safe log output.
     *
     * <p>Keeps the first two characters of the local part and the TLD of the domain,
     * replacing everything else with asterisks.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code "rithik@gmail.com"} → {@code "ri***@***.com"}</li>
     *   <li>{@code "a@b.io"}           → {@code "a***@***.io"}</li>
     *   <li>{@code null}               → {@code "(null)"}</li>
     *   <li>{@code "notanemail"}       → {@code "no***"} (no @ found)</li>
     * </ul>
     *
     * @param email the raw email address (may be null)
     * @return a masked string safe for log output
     */
    public static String maskEmail(String email) {
        if (email == null) return "(null)";
        if (email.isBlank()) return "(blank)";

        int atIndex = email.indexOf('@');
        if (atIndex < 0) {
            // Not a valid email — mask all but first 2 chars
            return maskPrefix(email);
        }

        String local  = email.substring(0, atIndex);
        String domain = email.substring(atIndex + 1);

        String maskedLocal  = maskPrefix(local);
        String maskedDomain = maskDomain(domain);

        return maskedLocal + "@" + maskedDomain;
    }

    // ── Internal helpers ───────────────────────────────────────────────────────

    private static String maskPrefix(String value) {
        if (value.length() <= 2) return value + "***";
        return value.substring(0, 2) + "***";
    }

    private static String maskDomain(String domain) {
        int lastDot = domain.lastIndexOf('.');
        if (lastDot < 0) return "***";
        String tld = domain.substring(lastDot); // ".com", ".io", etc.
        return "***" + tld;
    }
}
