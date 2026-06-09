package com.ego.raw_ego.catalog.service;

import org.springframework.stereotype.Component;

/**
 * Centralizes stock urgency messaging rules for the storefront.
 *
 * <p>This component translates a raw {@code quantityAvailable} integer into a
 * human-readable urgency signal that the frontend renders inline on the Product
 * Detail Page — e.g. "Only 3 left in your size".
 *
 * <p><b>Rules (all thresholds defined as constants — change here only):</b>
 * <pre>
 *   qty == 0        → lowStock=true,  message="Out of stock"
 *   qty  1 – 3      → lowStock=true,  message="Only X left in your size"
 *   qty  4 – 10     → lowStock=true,  message="Selling fast"
 *   qty >= 11       → lowStock=false, message=null
 * </pre>
 *
 * <p><b>Architecture note:</b> This is a stateless {@code @Component} with no
 * repository dependencies. It is injected into the service layer (not DTOs)
 * and its result is populated onto {@link com.ego.raw_ego.catalog.dto.response.VariantResponse}
 * during the DTO mapping step.
 */
@Component
public class StockUrgencyService {

    /** Upper bound (inclusive) for "Only X left" message. */
    private static final int ONLY_X_LEFT_THRESHOLD  = 3;

    /** Upper bound (inclusive) for "Selling fast" message. */
    private static final int SELLING_FAST_THRESHOLD = 10;

    /**
     * Computes urgency metadata for a given available quantity.
     *
     * @param quantityAvailable units available for purchase (must be >= 0)
     * @return a {@link StockUrgencyResult} record — never null
     */
    public StockUrgencyResult computeUrgency(int quantityAvailable) {
        if (quantityAvailable <= 0) {
            return new StockUrgencyResult(0, true, "Out of stock");
        }
        if (quantityAvailable <= ONLY_X_LEFT_THRESHOLD) {
            String message = "Only " + quantityAvailable + " left in your size";
            return new StockUrgencyResult(quantityAvailable, true, message);
        }
        if (quantityAvailable <= SELLING_FAST_THRESHOLD) {
            return new StockUrgencyResult(quantityAvailable, true, "Selling fast");
        }
        // No urgency — plenty of stock
        return new StockUrgencyResult(quantityAvailable, false, null);
    }

    /**
     * Immutable result record returned by {@link #computeUrgency(int)}.
     *
     * @param quantityAvailable raw unit count (forwarded directly to the DTO)
     * @param lowStock          true when quantity warrants any urgency signal
     * @param stockUrgencyMessage human-readable message; null when no urgency
     */
    public record StockUrgencyResult(
            int     quantityAvailable,
            boolean lowStock,
            String  stockUrgencyMessage
    ) {}
}
