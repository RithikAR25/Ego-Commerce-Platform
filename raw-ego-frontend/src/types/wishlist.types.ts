/**
 * wishlist.types.ts
 *
 * TypeScript interfaces for the wishlist domain.
 */

import type { InventoryStatus } from './catalog.types';

export interface WishlistItem {
  variantId:         number;
  sku:               string;
  productName:       string;
  variantLabel:      string;
  price:             number;
  compareAtPrice:    number | null;
  discountPercent:   number | null;
  primaryImageUrl:   string | null;
  stockStatus:       InventoryStatus;
  quantityAvailable: number;
}

export interface WishlistResponse {
  items: WishlistItem[];
}

export interface AddToWishlistPayload {
  variantId: number;
}
