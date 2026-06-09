# Product Flow — EGO Frontend

> **Phase:** 12 (Frontend — Storefront)
> **Status:** 📋 Planned
> **Depends on:** Backend Phase 3 (Catalog API)

All decisions conform to [product-architecture-decisions.md](../database/product-architecture-decisions.md).
API contract defined in [product-module.md](../backend/product-module.md).

---

## Routes

| Route | Component | Description |
|-------|-----------|-------------|
| `/` | `HomePage` | Featured products, categories |
| `/products` | `ProductListingPage` | All products (paginated) |
| `/[categorySlug]/[subcategorySlug]` | `CategoryPage` | Products filtered by subcategory |
| `/products/[slug]` | `ProductDetailPage` | Product detail with variant selector |

---

## Component Tree

```
ProductDetailPage
├── ProductImageGallery           ← switches images on color select
│   ├── MainImage                 ← large primary image
│   └── ThumbnailStrip            ← scrollable thumbnail row
│
├── ProductInfo
│   ├── ProductName               ← <h1>
│   ├── CategoryBreadcrumb        ← Men > Oversized Tees
│   ├── PriceDisplay              ← price + compare_at_price + discount badge
│   └── ProductTags               ← e.g. "Streetwear", "Oversized"
│
├── VariantSelector
│   ├── ColorSwatchGroup          ← circles with hex_color / swatch_image_url
│   │   └── ColorSwatch (×N)      ← click → updates images + resolves available sizes
│   └── SizeButtonGroup
│       └── SizeButton (×N)       ← greyed if unavailable for selected color
│
├── AddToCartSection
│   ├── StockStatusBadge          ← "In Stock" | "Low Stock (3 left)" | "Out of Stock"
│   └── AddToCartButton           ← disabled if OUT_OF_STOCK or no size selected
│
├── ProductTabs
│   ├── DescriptionTab
│   ├── MaterialTab               ← material + care instructions
│   └── ReviewsTab                ← Phase 9
│
└── RelatedProducts               ← same subcategory, Phase 4+ (Elasticsearch)
```

---

## Variant Selector Logic

### Data Model in Component State

```typescript
interface ProductDetailData {
  id: number;
  name: string;
  slug: string;
  attributeTypes: AttributeType[];   // [{name: "Color", values: [...]}, {name: "Size", values: [...]}]
  variants: VariantData[];
  galleryImages: ImageData[];
}

interface VariantData {
  id: number;
  sku: string;
  price: number;
  compareAtPrice: number | null;
  discountPercent: number | null;
  isActive: boolean;
  stockStatus: 'IN_STOCK' | 'LOW_STOCK' | 'OUT_OF_STOCK';
  attributeValues: { typeId: number; valueId: number; value: string; code: string }[];
  images: ImageData[];
}

// Component state
const [selectedColor, setSelectedColor] = useState<number | null>(null);   // attributeValueId
const [selectedSize, setSelectedSize]   = useState<number | null>(null);   // attributeValueId
const [resolvedVariant, setResolvedVariant] = useState<VariantData | null>(null);
```

### Availability Matrix (computed on mount)

```typescript
// Build a map: colorValueId → Set<sizeValueId> (only IN_STOCK or LOW_STOCK)
const availabilityMatrix = useMemo(() => {
  const matrix = new Map<number, Set<number>>();
  for (const variant of variants) {
    if (!variant.isActive || variant.stockStatus === 'OUT_OF_STOCK') continue;
    const colorVal = variant.attributeValues.find(v => v.typeName === 'Color');
    const sizeVal  = variant.attributeValues.find(v => v.typeName === 'Size');
    if (!colorVal || !sizeVal) continue;
    if (!matrix.has(colorVal.valueId)) matrix.set(colorVal.valueId, new Set());
    matrix.get(colorVal.valueId)!.add(sizeVal.valueId);
  }
  return matrix;
}, [variants]);
```

### Color Selection → Image Swap

```typescript
const handleColorSelect = (colorValueId: number) => {
  setSelectedColor(colorValueId);
  setSelectedSize(null);          // reset size on color change
  setResolvedVariant(null);

  // Find ANY active variant with this color for image display
  const representativeVariant = variants.find(v =>
    v.isActive &&
    v.attributeValues.some(av => av.typeId === COLOR_TYPE_ID && av.valueId === colorValueId)
  );
  if (representativeVariant?.images.length) {
    setDisplayedImages(representativeVariant.images);  // swap gallery
  } else {
    setDisplayedImages(galleryImages);                 // fallback
  }
};
```

### Size Selection → Resolve Variant

```typescript
const handleSizeSelect = (sizeValueId: number) => {
  setSelectedSize(sizeValueId);

  const variant = variants.find(v =>
    v.isActive &&
    v.attributeValues.some(av => av.typeId === COLOR_TYPE_ID && av.valueId === selectedColor) &&
    v.attributeValues.some(av => av.typeId === SIZE_TYPE_ID  && av.valueId === sizeValueId)
  );
  setResolvedVariant(variant ?? null);
};
```

### Size Button Availability

```typescript
// A size button is available if the selected color has stock for it
const isSizeAvailable = (sizeValueId: number): boolean => {
  if (!selectedColor) return true;  // show all when no color selected
  return availabilityMatrix.get(selectedColor)?.has(sizeValueId) ?? false;
};
```

---

## Price Display Logic

```typescript
interface PriceDisplayProps {
  price: number;
  compareAtPrice: number | null;
  discountPercent: number | null;
}

// Display: ₹1,299   ~~₹1,799~~   28% OFF
// If no compareAtPrice: display ₹1,299 only

const formatPrice = (amount: number) =>
  new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 })
    .format(amount);
```

---

## Add to Cart Flow

```
User clicks "Add to Cart"
    ↓
[Guard] Is a color selected?         → NO  → Show "Please select a color" toast
    ↓ YES
[Guard] Is a size selected?          → NO  → Shake the size selector
    ↓ YES
[Guard] resolvedVariant !== null?    → NO  → Show "Variant unavailable" (should not happen)
    ↓ YES
POST /api/v1/cart/items
  body: { variantId: resolvedVariant.id, quantity: 1 }
    ↓
Cart store updated (Zustand)
    ↓
Show cart drawer / success toast
```

---

## Product Listing Page

### URL Structure

```
/men/oversized-tees?colors=BLK,WHT&sizes=M,L&minPrice=999&maxPrice=2999&sortBy=newest&page=0
```

### Product Card (Summary)

```
┌──────────────────────────┐
│   [Image]                │
│                          │
│  Oversized Acid-Wash Tee │ ← product.name
│  ₹1,299  ~~₹1,799~~      │ ← price display
│  ● ● ○                   │ ← color swatches (first 3 colors)
│  28% OFF                 │ ← discount badge
└──────────────────────────┘
```

- Clicking card → navigate to `/products/{slug}`
- Hovering card → animate to second gallery image (if available)
- Color swatch hover → swap thumbnail to that color's primary image

---

## SEO Considerations

| Element | Value | Source |
|---------|-------|--------|
| `<title>` | `{product.name} — EGO` | `product.name` |
| `<meta name="description">` | First 160 chars of description | `product.description` |
| `<link rel="canonical">` | `/products/{slug}` | `product.slug` |
| `og:image` | Primary gallery image URL | `product.galleryImages[0].url` |
| Structured data | `Product` JSON-LD schema | price, availability, image |

### JSON-LD Structured Data

```json
{
  "@context": "https://schema.org",
  "@type": "Product",
  "name": "Oversized Acid-Wash Tee",
  "image": ["https://res.cloudinary.com/.../hero.jpg"],
  "description": "...",
  "brand": { "@type": "Brand", "name": "EGO" },
  "offers": {
    "@type": "Offer",
    "priceCurrency": "INR",
    "price": "1299",
    "availability": "https://schema.org/InStock",
    "url": "https://ego.in/products/oversized-acid-wash-tee"
  }
}
```

---

## API Integration

### Fetching Product Detail

```typescript
// src/api/catalog.api.ts

export const getProductBySlug = async (slug: string): Promise<ProductDetailResponse> => {
  const response = await apiClient.get(`/products/${slug}`);
  return response.data.data;
};

export const getProductsByCategory = async (
  categorySlug: string,
  params: ProductFilterParams
): Promise<PagedResponse<ProductSummaryResponse>> => {
  const response = await apiClient.get(`/products/category/${categorySlug}`, { params });
  return response.data.data;
};
```

### React Query Keys

```typescript
// src/lib/queryKeys.ts
export const productKeys = {
  all:        ['products']                          as const,
  detail:     (slug: string) => ['products', slug] as const,
  byCategory: (cat: string, filters: object) =>
                ['products', 'category', cat, filters] as const,
  list:       (filters: object) => ['products', 'list', filters] as const,
};
```

---

## State Management (Zustand)

```typescript
// src/store/productStore.ts
interface ProductStore {
  selectedColor:    number | null;
  selectedSize:     number | null;
  resolvedVariant:  VariantData | null;
  displayedImages:  ImageData[];

  setSelectedColor:   (id: number | null) => void;
  setSelectedSize:    (id: number | null) => void;
  setResolvedVariant: (v: VariantData | null) => void;
  setDisplayedImages: (imgs: ImageData[]) => void;
  resetSelections:    () => void;
}
```

> Note: Product page state is local (not persisted). Only cart state is persisted to localStorage.

---

## Phases & Dependencies

| Feature | Phase |
|---------|-------|
| Product listing + detail pages | Phase 12 |
| Variant selector + image swap | Phase 12 |
| Search UI (Elasticsearch-backed) | Phase 12 (after Phase 4) |
| Checkout flow + Razorpay | Phase 12 (after Phase 7) |
| Reviews on PDP | Phase 12 (after Phase 9) |
| Wishlist button on PDP | Phase 12 (after Phase 9) |
| Admin product CRUD | Phase 13 |
