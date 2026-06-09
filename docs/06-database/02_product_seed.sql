-- 02_product_seed.sql
SET @now = NOW(6);

-- Clear existing products
DELETE FROM inventory_records;
DELETE FROM variant_attribute_values;
DELETE FROM product_variants;
DELETE FROM product_attribute_values;
DELETE FROM product_attribute_types;
DELETE FROM products;

-- 1. Create a Product
INSERT INTO products (id, name, slug, product_code, description, status, is_deleted, created_at, updated_at, version, category_id)
VALUES (1, 'Classic Black Hoodie', 'classic-black-hoodie', 'HOODIEBLK1', 'A premium cotton heavy-weight hoodie.', 'ACTIVE', 0, @now, @now, 0, 501);

-- 2. Create Attribute Types (Size and Color) for Product 1
INSERT INTO product_attribute_types (id, product_id, name, display_order)
VALUES 
(1, 1, 'Size', 1),
(2, 1, 'Color', 2);

-- 3. Create Attribute Values
INSERT INTO product_attribute_values (id, attribute_type_id, value, code, display_order, hex_color)
VALUES 
(1, 1, 'Medium', 'M', 1, NULL),
(2, 1, 'Large', 'L', 2, NULL),
(3, 2, 'Black', 'BLK', 1, '#000000');

-- 4. Create Variants
-- Variant 1: Medium, Black
INSERT INTO product_variants (id, product_id, sku, price, compare_at_price, is_active, created_at, updated_at, version)
VALUES (1, 1, 'HOODIE-BLK-M', 2999.00, 3999.00, 1, @now, @now, 0);

-- Variant 2: Large, Black
INSERT INTO product_variants (id, product_id, sku, price, compare_at_price, is_active, created_at, updated_at, version)
VALUES (2, 1, 'HOODIE-BLK-L', 2999.00, 3999.00, 1, @now, @now, 0);

-- 5. Link Variants to Attribute Values
-- Variant 1 Links
INSERT INTO variant_attribute_values (variant_id, attribute_value_id) VALUES (1, 1); -- Medium
INSERT INTO variant_attribute_values (variant_id, attribute_value_id) VALUES (1, 3); -- Black

-- Variant 2 Links
INSERT INTO variant_attribute_values (variant_id, attribute_value_id) VALUES (2, 2); -- Large
INSERT INTO variant_attribute_values (variant_id, attribute_value_id) VALUES (2, 3); -- Black

-- 6. Inventory Records
INSERT INTO inventory_records (id, variant_id, quantity_available, quantity_reserved, low_stock_threshold, updated_at, version)
VALUES 
(1, 1, 50, 0, 10, @now, 0),
(2, 2, 50, 0, 10, @now, 0);
