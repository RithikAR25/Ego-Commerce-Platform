Variant matrix (SKU system) — the core data model
This is the most important design decision for a clothing brand. Getting the variant model wrong causes pain everywhere — inventory, cart, order history, search. Here is the canonical approach.
The mental model: A Product is a concept (e.g. "Oversized Acid-Wash Tee"). A ProductVariant is a specific, purchasable combination (e.g. "Oversized Acid-Wash Tee / Red / M"). Every variant gets its own unique SKU and its own inventory row. The cart, order items, and inventory all reference variants — never products directly.

Product: "Oversized Acid-Wash Tee"
├── Variant: RED-S → SKU: OAT-RED-S → inventory: 12 units
├── Variant: RED-M → SKU: OAT-RED-M → inventory: 8 units
├── Variant: RED-L → SKU: OAT-RED-L → inventory: 0 units ← out of stock
├── Variant: RED-XL → SKU: OAT-RED-XL → inventory: 3 units
├── Variant: BLUE-S → SKU: OAT-BLU-S → inventory: 15 units
└── ...

Complete schema for the variant matrix:

-- Attribute types defined at product level
CREATE TABLE product_attribute_types (
id BIGINT PRIMARY KEY AUTO_INCREMENT,
product_id BIGINT NOT NULL REFERENCES products(id),
name VARCHAR(50) NOT NULL, -- 'size', 'color'
display_order INT NOT NULL DEFAULT 0
);

-- Each possible value for an attribute type
CREATE TABLE product_attribute_values (
id BIGINT PRIMARY KEY AUTO_INCREMENT,
attribute_type_id BIGINT NOT NULL REFERENCES product_attribute_types(id),
value VARCHAR(100) NOT NULL, -- 'S', 'M', 'L', 'Red', 'Blue'
display_order INT NOT NULL DEFAULT 0,
hex_color VARCHAR(7), -- '#FF0000' for color swatches
swatch_image_url VARCHAR(500) -- for pattern swatches
);

-- The variant itself — one row per unique combination
CREATE TABLE product_variants (
id BIGINT PRIMARY KEY AUTO_INCREMENT,
product_id BIGINT NOT NULL REFERENCES products(id),
sku VARCHAR(100) NOT NULL UNIQUE,
price_override DECIMAL(10,2), -- NULL = use product base_price
is_active BOOLEAN NOT NULL DEFAULT TRUE,
INDEX idx_variant_product (product_id),
INDEX idx_variant_sku (sku)
);

-- Maps each variant to its attribute values (the join table)
CREATE TABLE variant_attribute_values (
variant_id BIGINT NOT NULL REFERENCES product_variants(id),
attribute_value_id BIGINT NOT NULL REFERENCES product_attribute_values(id),
PRIMARY KEY (variant_id, attribute_value_id)
);

-- One inventory row per variant
CREATE TABLE inventory_records (
id BIGINT PRIMARY KEY AUTO_INCREMENT,
variant_id BIGINT NOT NULL UNIQUE REFERENCES product_variants(id),
quantity_available INT NOT NULL DEFAULT 0 CHECK (quantity_available >= 0),
quantity_reserved INT NOT NULL DEFAULT 0 CHECK (quantity_reserved >= 0),
low_stock_threshold INT NOT NULL DEFAULT 5,
version INT NOT NULL DEFAULT 0, -- optimistic lock
updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

Why this schema instead of hardcoded size / color columns? If you hardcode columns, adding a new attribute (e.g. "material", "fit") requires an ALTER TABLE. With attribute types and values, you add rows. The frontend reads the attribute structure dynamically and builds the size/color selector grid without any code change.
Frontend variant selector logic: When a user lands on a product page, you fetch all variants with their attribute values. You build a 2D availability matrix client-side. When the user selects "Red" first, you grey out all sizes that have zero stock in Red. When they then select "M", you resolve to exactly one variant ID to add to cart.
