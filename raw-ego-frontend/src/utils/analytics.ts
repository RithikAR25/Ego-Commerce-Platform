/**
 * analytics.ts
 *
 * Analytics abstraction layer.
 * Prepares the platform for future analytics integration (e.g., Segment, Mixpanel, GA4)
 * without coupling the UI components to a specific vendor.
 */

export const trackQuickViewOpened = (productSlug: string) => {
  console.debug(`[Analytics] Quick View Opened: ${productSlug}`);
  // TODO: Add vendor integration
};

export const trackQuickViewAddToCart = (productSlug: string, variantId: number, quantity: number) => {
  console.debug(`[Analytics] Quick View Add To Cart: ${productSlug} | Variant: ${variantId} | Qty: ${quantity}`);
  // TODO: Add vendor integration
};

export const trackQuickViewViewDetails = (productSlug: string) => {
  console.debug(`[Analytics] Quick View View Details Clicked: ${productSlug}`);
  // TODO: Add vendor integration
};
