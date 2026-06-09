package com.ego.raw_ego.catalog.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link StockUrgencyService}.
 *
 * <p>These are pure unit tests — no Spring context, no database, no mocking.
 * The service is stateless and has no dependencies, so we instantiate it directly.
 *
 * <p><b>Boundary values under test:</b>
 * <pre>
 *   qty == 0   → OUT_OF_STOCK message
 *   qty == 1   → "Only 1 left in your size"
 *   qty == 3   → "Only 3 left in your size"  (upper ONLY_X boundary)
 *   qty == 10  → "Selling fast"              (upper SELLING_FAST boundary)
 *   qty == 11  → null message, lowStock=false (no urgency zone)
 * </pre>
 */
@DisplayName("StockUrgencyService — urgency rule boundary tests")
class StockUrgencyServiceTest {

    private StockUrgencyService service;

    @BeforeEach
    void setUp() {
        service = new StockUrgencyService();
    }

    // ── qty = 0 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("qty=0 → lowStock=true, message='Out of stock', quantityAvailable=0")
    void outOfStock_whenQuantityIsZero() {
        StockUrgencyService.StockUrgencyResult result = service.computeUrgency(0);

        assertThat(result.quantityAvailable()).isEqualTo(0);
        assertThat(result.lowStock()).isTrue();
        assertThat(result.stockUrgencyMessage()).isEqualTo("Out of stock");
    }

    // ── qty = 1 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("qty=1 → lowStock=true, message='Only 1 left in your size'")
    void onlyXLeft_whenQuantityIsOne() {
        StockUrgencyService.StockUrgencyResult result = service.computeUrgency(1);

        assertThat(result.quantityAvailable()).isEqualTo(1);
        assertThat(result.lowStock()).isTrue();
        assertThat(result.stockUrgencyMessage()).isEqualTo("Only 1 left in your size");
    }

    // ── qty = 3 (upper boundary of ONLY_X_LEFT zone) ─────────────────────────

    @Test
    @DisplayName("qty=3 → lowStock=true, message='Only 3 left in your size'")
    void onlyXLeft_whenQuantityIsThree() {
        StockUrgencyService.StockUrgencyResult result = service.computeUrgency(3);

        assertThat(result.quantityAvailable()).isEqualTo(3);
        assertThat(result.lowStock()).isTrue();
        assertThat(result.stockUrgencyMessage()).isEqualTo("Only 3 left in your size");
    }

    // ── qty = 10 (upper boundary of SELLING_FAST zone) ───────────────────────

    @Test
    @DisplayName("qty=10 → lowStock=true, message='Selling fast'")
    void sellingFast_whenQuantityIsTen() {
        StockUrgencyService.StockUrgencyResult result = service.computeUrgency(10);

        assertThat(result.quantityAvailable()).isEqualTo(10);
        assertThat(result.lowStock()).isTrue();
        assertThat(result.stockUrgencyMessage()).isEqualTo("Selling fast");
    }

    // ── qty = 11 (first value in NO-urgency zone) ─────────────────────────────

    @Test
    @DisplayName("qty=11 → lowStock=false, message=null (no urgency)")
    void noUrgency_whenQuantityIsEleven() {
        StockUrgencyService.StockUrgencyResult result = service.computeUrgency(11);

        assertThat(result.quantityAvailable()).isEqualTo(11);
        assertThat(result.lowStock()).isFalse();
        assertThat(result.stockUrgencyMessage()).isNull();
    }
}
