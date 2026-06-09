package com.ego.raw_ego.common.util;

/**
 * Utility for generating SEO-friendly URL slugs from human-readable strings.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Lowercase</li>
 *   <li>Replace {@code &} with {@code and}</li>
 *   <li>Replace {@code '} (apostrophe) and parentheses with empty string</li>
 *   <li>Replace any non-alphanumeric character (except hyphens) with a hyphen</li>
 *   <li>Collapse consecutive hyphens into one</li>
 *   <li>Strip leading/trailing hyphens</li>
 * </ol>
 *
 * <p>Examples:
 * <pre>
 *   "Oversized Acid-Wash Tee"  → "oversized-acid-wash-tee"
 *   "Men's Classic Hoodie"     → "mens-classic-hoodie"
 *   "Cargo Pants (Olive)"      → "cargo-pants-olive"
 *   "T&T Co-ords"              → "t-and-t-co-ords"
 * </pre>
 */
public final class SlugUtils {

    private SlugUtils() {}

    /**
     * Converts an arbitrary string to a URL-safe slug.
     *
     * @param input the raw string (e.g. product name, category name)
     * @return a lowercase, hyphen-separated slug
     */
    public static String toSlug(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Slug input must not be blank");
        }

        return input
                .toLowerCase()
                .replace("&", "and")
                .replaceAll("[''()]", "")          // apostrophes, parentheses
                .replaceAll("[^a-z0-9\\-]", "-")   // non-alphanumeric → hyphen
                .replaceAll("-{2,}", "-")           // collapse consecutive hyphens
                .replaceAll("^-|-$", "");           // strip leading/trailing hyphens
    }

    /**
     * Generates a unique slug by appending a numeric suffix if the base slug is taken.
     *
     * <p>Example: if "oversized-tee" exists, tries "oversized-tee-2", "oversized-tee-3", etc.
     *
     * @param input    the raw name to slugify
     * @param existsFn a function that returns true if the slug is already in use
     * @return a unique slug
     */
    public static String toUniqueSlug(String input, java.util.function.Predicate<String> existsFn) {
        String base = toSlug(input);
        String candidate = base;
        int suffix = 2;

        while (existsFn.test(candidate)) {
            candidate = base + "-" + suffix++;
        }

        return candidate;
    }
}
