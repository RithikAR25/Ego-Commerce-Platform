package com.ego.raw_ego.common.util;

import java.util.regex.Pattern;

/**
 * Lightweight HTML sanitizer for user-supplied text stored in the database
 * and returned in API responses.
 *
 * <h3>Why this exists</h3>
 * <p>Although React escapes output by default (preventing classic stored XSS),
 * data is also rendered in:</p>
 * <ul>
 *   <li>Transactional email HTML (SendGrid) — directly interpolated into HTML strings</li>
 *   <li>Admin panels — may render raw values</li>
 *   <li>Future integrations (webhooks, CSV exports) — unknown rendering contexts</li>
 * </ul>
 *
 * <h3>Strategy</h3>
 * <p>Two-pass approach:
 * <ol>
 *   <li><b>Strip HTML tags</b> — remove any {@code <tag>} or {@code </tag>} structure
 *       so injected markup cannot be reassembled downstream.</li>
 *   <li><b>Strip JavaScript event attributes</b> — catch patterns like
 *       {@code onerror=}, {@code onclick=} that survive tag stripping if they appear
 *       in attribute context.</li>
 * </ol>
 *
 * <h3>Scope</h3>
 * <p>Applied at the <b>service layer</b> on write, NOT at the controller layer.
 * This ensures sanitized content is stored in the DB — the source of truth is clean.
 *
 * <h3>What is NOT done here</h3>
 * <ul>
 *   <li>Rich-text (HTML) sanitization (allow some tags) — use the Jsoup {@code Safelist} API
 *       if EGO ever introduces a WYSIWYG review editor.</li>
 *   <li>SQL injection prevention — handled entirely by JPA parameterized queries.</li>
 *   <li>Path traversal, SSRF — not applicable to the fields this utility targets.</li>
 * </ul>
 */
public final class HtmlSanitizer {

    // Matches any HTML/XML tag: opening, closing, or self-closing
    private static final Pattern HTML_TAG_PATTERN =
            Pattern.compile("<[^>]*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    // Matches JS event handler attribute names that could survive tag stripping
    // e.g. "onerror=", "onclick=", "onmouseover=" etc.
    private static final Pattern JS_EVENT_PATTERN =
            Pattern.compile("\\bon\\w+\\s*=", Pattern.CASE_INSENSITIVE);

    // Matches javascript: URI scheme — used in href/src attributes
    private static final Pattern JS_URI_PATTERN =
            Pattern.compile("javascript\\s*:", Pattern.CASE_INSENSITIVE);

    private HtmlSanitizer() {
        // Utility class — no instances
    }

    /**
     * Sanitizes a user-supplied string by stripping HTML tags and JavaScript
     * injection patterns. Returns {@code null} if input is {@code null}.
     *
     * <p>Applied to: review title, review body, shipping address, any other
     * free-text field stored in the database and rendered in email/UI contexts.
     *
     * @param input the raw user input
     * @return sanitized string safe for storage and rendering
     */
    public static String sanitize(String input) {
        if (input == null) return null;

        String result = input.trim();
        result = HTML_TAG_PATTERN .matcher(result).replaceAll("");  // Strip HTML tags
        result = JS_EVENT_PATTERN .matcher(result).replaceAll("");  // Strip JS event handlers
        result = JS_URI_PATTERN   .matcher(result).replaceAll("");  // Strip javascript: URIs

        return result;
    }

    /**
     * Sanitizes and enforces a maximum character length.
     * Truncation occurs AFTER sanitization so the stored length is always
     * within the specified bound regardless of how many characters stripping removes.
     *
     * @param input     the raw user input
     * @param maxLength maximum allowed length after sanitization
     * @return sanitized and length-capped string
     */
    public static String sanitize(String input, int maxLength) {
        String result = sanitize(input);
        if (result == null) return null;
        return result.length() > maxLength ? result.substring(0, maxLength) : result;
    }
}
