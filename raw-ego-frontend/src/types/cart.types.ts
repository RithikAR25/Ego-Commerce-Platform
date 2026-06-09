/**
 * cart.types.ts
 *
 * TypeScript interfaces for the cart domain.
 * These MUST mirror the exact backend DTO field names — never invent field names.
 *
 * Key backend DTOs this maps to:
 *   - CartResponse.java
 *   - CartItemResponse.java
 *   - AddToCartRequest.java
 *   - UpdateCartItemRequest.java
 *   - MergeCartRequest.java
 */

import type { InventoryStatus } from './catalog.types';

// ── Response types ─────────────────────────────────────────────────────────────

/**
 * Matches CartItemResponse.java — a single line item in the cart.
 */
export interface CartItem {
  variantId:        number;
  sku:              string;
  productName:      string;
  /** Human-readable label, e.g. "Black / M" */
  variantLabel:     string;
  price:            number;
  compareAtPrice:   number | null;
  discountPercent:  number | null;
  primaryImageUrl:  string | null;
  quantity:         number;
  stockStatus:      InventoryStatus;
  /** Raw available stock — used for quantity selector max */
  quantityAvailable: number;
}

/**
 * Matches CartResponse.java — the top-level cart envelope.
 */
export interface CartResponse {
  items:     CartItem[];
  /** Total units across all line items — displayed as cart icon badge. */
  itemCount: number;
  /** Server-computed sum of price × quantity. */
  subtotal:  number;
}

// ── Request payload types ──────────────────────────────────────────────────────

/** Matches AddToCartRequest.java */
export interface AddToCartPayload {
  variantId: number;
  quantity:  number;
}

/** Matches UpdateCartItemRequest.java */
export interface UpdateCartItemPayload {
  quantity: number;
}

/** Matches MergeCartRequest.java */
export interface MergeCartPayload {
  sessionId: string;
}
