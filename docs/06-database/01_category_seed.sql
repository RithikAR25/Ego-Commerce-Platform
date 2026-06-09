-- ============================================================
-- 01_category_seed.sql
-- Enterprise 3-level category taxonomy (ROOT → GROUP → LEAF)
-- Execute AFTER schema is created (Hibernate ddl-auto=update)
-- All timestamps use @now variable for consistency.
-- ============================================================

SET @now = NOW(6);

-- ── ROOT CATEGORIES ──────────────────────────────────────────
INSERT INTO categories (id, parent_id, name, code, slug, description, display_order, is_active, created_at, updated_at) VALUES
  (1,  NULL, 'Men',   'MEN',  'men',   'Fashion for men',    1, true, @now, @now),
  (2,  NULL, 'Women', 'WMN',  'women', 'Fashion for women',  2, true, @now, @now),
  (3,  NULL, 'Kids',  'KIDS', 'kids',  'Fashion for kids',   3, true, @now, @now),
  (4,  NULL, 'Home',  'HOME', 'home',  'Home & living',      4, true, @now, @now),
  (5,  NULL, 'Gen Z', 'GENZ', 'gen-z', 'Gen Z street style', 5, true, @now, @now);

-- ── GROUP CATEGORIES — MEN ────────────────────────────────────
INSERT INTO categories (id, parent_id, name, code, slug, description, display_order, is_active, created_at, updated_at) VALUES
  (10, 1, 'Topwear',         'MNTW', 'men-topwear',        NULL, 1, true, @now, @now),
  (11, 1, 'Bottomwear',      'MNBW', 'men-bottomwear',     NULL, 2, true, @now, @now),
  (12, 1, 'Footwear',        'MNFW', 'men-footwear',       NULL, 3, true, @now, @now),
  (13, 1, 'Sports & Active', 'MNSA', 'men-sports-active',  NULL, 4, true, @now, @now),
  (14, 1, 'Accessories',     'MNAC', 'men-accessories',    NULL, 5, true, @now, @now),
  (15, 1, 'Ethnic Wear',     'MNET', 'men-ethnic-wear',    NULL, 6, true, @now, @now),
  (16, 1, 'Winter Wear',     'MNWW', 'men-winter-wear',    NULL, 7, true, @now, @now);

-- ── GROUP CATEGORIES — WOMEN ──────────────────────────────────
INSERT INTO categories (id, parent_id, name, code, slug, description, display_order, is_active, created_at, updated_at) VALUES
  (20, 2, 'Topwear',              'WMTW', 'women-topwear',              NULL, 1, true, @now, @now),
  (21, 2, 'Bottomwear',           'WMBW', 'women-bottomwear',           NULL, 2, true, @now, @now),
  (22, 2, 'Footwear',             'WMFW', 'women-footwear',             NULL, 3, true, @now, @now),
  (23, 2, 'Sports & Active',      'WMSA', 'women-sports-active',        NULL, 4, true, @now, @now),
  (24, 2, 'Accessories',          'WMAC', 'women-accessories',          NULL, 5, true, @now, @now),
  (25, 2, 'Ethnic Wear',          'WMET', 'women-ethnic-wear',          NULL, 6, true, @now, @now),
  (26, 2, 'Lingerie & Nightwear', 'WMLN', 'women-lingerie-nightwear',   NULL, 7, true, @now, @now);

-- ── GROUP CATEGORIES — KIDS ───────────────────────────────────
INSERT INTO categories (id, parent_id, name, code, slug, description, display_order, is_active, created_at, updated_at) VALUES
  (30, 3, 'Boys Clothing',  'KDBG', 'kids-boys-clothing',  NULL, 1, true, @now, @now),
  (31, 3, 'Girls Clothing', 'KDGG', 'kids-girls-clothing', NULL, 2, true, @now, @now),
  (32, 3, 'Boys Footwear',  'KDBF', 'kids-boys-footwear',  NULL, 3, true, @now, @now),
  (33, 3, 'Girls Footwear', 'KDGF', 'kids-girls-footwear', NULL, 4, true, @now, @now),
  (34, 3, 'Accessories',    'KDAC', 'kids-accessories',    NULL, 5, true, @now, @now);

-- ── GROUP CATEGORIES — HOME ───────────────────────────────────
INSERT INTO categories (id, parent_id, name, code, slug, description, display_order, is_active, created_at, updated_at) VALUES
  (40, 4, 'Bed & Bath',       'HMBB', 'home-bed-bath',       NULL, 1, true, @now, @now),
  (41, 4, 'Kitchen & Dining', 'HMKD', 'home-kitchen-dining', NULL, 2, true, @now, @now),
  (42, 4, 'Decor',            'HMDC', 'home-decor',          NULL, 3, true, @now, @now),
  (43, 4, 'Storage',          'HMST', 'home-storage',        NULL, 4, true, @now, @now),
  (44, 4, 'Lighting',         'HMLG', 'home-lighting',       NULL, 5, true, @now, @now);

-- ── GROUP CATEGORIES — GEN Z ──────────────────────────────────
INSERT INTO categories (id, parent_id, name, code, slug, description, display_order, is_active, created_at, updated_at) VALUES
  (50, 5, 'Streetwear',  'GZSW', 'genz-streetwear', NULL, 1, true, @now, @now),
  (51, 5, 'Sneakers',    'GZSK', 'genz-sneakers',   NULL, 2, true, @now, @now),
  (52, 5, 'Oversized',   'GZOS', 'genz-oversized',  NULL, 3, true, @now, @now),
  (53, 5, 'Y2K',         'GZY2', 'genz-y2k',        NULL, 4, true, @now, @now),
  (54, 5, 'Accessories', 'GZAC', 'genz-accessories', NULL, 5, true, @now, @now);

-- ── LEAF CATEGORIES — MEN / Topwear ──────────────────────────
INSERT INTO categories (id, parent_id, name, code, slug, description, display_order, is_active, created_at, updated_at) VALUES
  (100, 10, 'T-Shirts',      'TSH', 't-shirts',      NULL,  1, true, @now, @now),
  (101, 10, 'Polo T-Shirts', 'PLO', 'polo-t-shirts', NULL,  2, true, @now, @now),
  (102, 10, 'Casual Shirts', 'CSH', 'casual-shirts', NULL,  3, true, @now, @now),
  (103, 10, 'Formal Shirts', 'FSH', 'formal-shirts', NULL,  4, true, @now, @now),
  (104, 10, 'Sweatshirts',   'SWT', 'sweatshirts',   NULL,  5, true, @now, @now),
  (105, 10, 'Hoodies',       'HDY', 'hoodies',       NULL,  6, true, @now, @now),
  (106, 10, 'Jackets',       'JCK', 'jackets',       NULL,  7, true, @now, @now),
  (107, 10, 'Blazers',       'BLZ', 'blazers',       NULL,  8, true, @now, @now),
  (108, 10, 'Co-ord Sets',   'CDS', 'co-ord-sets',   NULL,  9, true, @now, @now),
  (109, 10, 'Tank Tops',     'TNK', 'tank-tops',     NULL, 10, true, @now, @now);

-- ── LEAF CATEGORIES — MEN / Bottomwear ───────────────────────
INSERT INTO categories (id, parent_id, name, code, slug, description, display_order, is_active, created_at, updated_at) VALUES
  (110, 11, 'Jeans',       'JNS', 'jeans',       NULL, 1, true, @now, @now),
  (111, 11, 'Trousers',    'TRS', 'trousers',    NULL, 2, true, @now, @now),
  (112, 11, 'Shorts',      'SHT', 'shorts',      NULL, 3, true, @now, @now),
  (113, 11, 'Joggers',     'JGR', 'joggers',     NULL, 4, true, @now, @now),
  (114, 11, 'Cargos',      'CGO', 'cargos',      NULL, 5, true, @now, @now),
  (115, 11, 'Track Pants', 'TKP', 'track-pants', NULL, 6, true, @now, @now);

-- ── LEAF CATEGORIES — MEN / Footwear ─────────────────────────
INSERT INTO categories (id, parent_id, name, code, slug, description, display_order, is_active, created_at, updated_at) VALUES
  (120, 12, 'Sneakers',     'SNK', 'men-sneakers',  NULL, 1, true, @now, @now),
  (121, 12, 'Casual Shoes', 'CAS', 'casual-shoes',  NULL, 2, true, @now, @now),
  (122, 12, 'Formal Shoes', 'FMS', 'formal-shoes',  NULL, 3, true, @now, @now),
  (123, 12, 'Sandals',      'SDL', 'men-sandals',   NULL, 4, true, @now, @now),
  (124, 12, 'Sports Shoes', 'SPS', 'sports-shoes',  NULL, 5, true, @now, @now),
  (125, 12, 'Flip Flops',   'FLF', 'flip-flops',   NULL, 6, true, @now, @now);

-- ── LEAF CATEGORIES — MEN / Sports & Active ──────────────────
INSERT INTO categories (id, parent_id, name, code, slug, description, display_order, is_active, created_at, updated_at) VALUES
  (130, 13, 'Sports T-Shirts', 'STS', 'sports-t-shirts', NULL, 1, true, @now, @now),
  (131, 13, 'Sports Shorts',   'SSH', 'sports-shorts',   NULL, 2, true, @now, @now),
  (132, 13, 'Tracksuits',      'TSU', 'tracksuits',      NULL, 3, true, @now, @now),
  (133, 13, 'Gym Wear',        'GYM', 'gym-wear',        NULL, 4, true, @now, @now),
  (134, 13, 'Swimwear',        'SWM', 'swimwear',        NULL, 5, true, @now, @now);

-- ── LEAF CATEGORIES — MEN / Accessories ──────────────────────
INSERT INTO categories (id, parent_id, name, code, slug, description, display_order, is_active, created_at, updated_at) VALUES
  (140, 14, 'Belts',           'BLT', 'belts',           NULL, 1, true, @now, @now),
  (141, 14, 'Wallets',         'WLT', 'wallets',         NULL, 2, true, @now, @now),
  (142, 14, 'Caps & Hats',     'CAP', 'caps-hats',       NULL, 3, true, @now, @now),
  (143, 14, 'Sunglasses',      'SGL', 'sunglasses',      NULL, 4, true, @now, @now),
  (144, 14, 'Watches',         'WTC', 'watches',         NULL, 5, true, @now, @now),
  (145, 14, 'Bags & Backpacks','BAG', 'bags-backpacks',  NULL, 6, true, @now, @now),
  (146, 14, 'Socks',           'SCK', 'socks',           NULL, 7, true, @now, @now);

-- ── LEAF CATEGORIES — MEN / Ethnic Wear ──────────────────────
INSERT INTO categories (id, parent_id, name, code, slug, description, display_order, is_active, created_at, updated_at) VALUES
  (150, 15, 'Kurtas',        'KRT', 'kurtas',        NULL, 1, true, @now, @now),
  (151, 15, 'Sherwanis',     'SRW', 'sherwanis',     NULL, 2, true, @now, @now),
  (152, 15, 'Pathani Suits', 'PTS', 'pathani-suits', NULL, 3, true, @now, @now),
  (153, 15, 'Nehru Jackets', 'NJK', 'nehru-jackets', NULL, 4, true, @now, @now);

-- ── LEAF CATEGORIES — MEN / Winter Wear ──────────────────────
INSERT INTO categories (id, parent_id, name, code, slug, description, display_order, is_active, created_at, updated_at) VALUES
  (160, 16, 'Sweaters',       'SWE', 'sweaters',       NULL, 1, true, @now, @now),
  (161, 16, 'Coats',          'COT', 'coats',          NULL, 2, true, @now, @now),
  (162, 16, 'Thermal Wear',   'THR', 'thermal-wear',   NULL, 3, true, @now, @now),
  (163, 16, 'Puffer Jackets', 'PFJ', 'puffer-jackets', NULL, 4, true, @now, @now);

-- ── LEAF CATEGORIES — WOMEN / Topwear ────────────────────────
INSERT INTO categories (id, parent_id, name, code, slug, description, display_order, is_active, created_at, updated_at) VALUES
  (200, 20, 'Tops & Tees',      'TPS', 'tops-tees',           NULL, 1, true, @now, @now),
  (201, 20, 'Crop Tops',        'CRP', 'crop-tops',           NULL, 2, true, @now, @now),
  (202, 20, 'Shirts & Blouses', 'SBL', 'shirts-blouses',      NULL, 3, true, @now, @now),
  (203, 20, 'Sweatshirts',      'WST', 'women-sweatshirts',   NULL, 4, true, @now, @now),
  (204, 20, 'Hoodies',          'WHD', 'women-hoodies',       NULL, 5, true, @now, @now),
  (205, 20, 'Jackets',          'WJK', 'women-jackets',       NULL, 6, true, @now, @now),
  (206, 20, 'Dresses',          'DRS', 'dresses',             NULL, 7, true, @now, @now),
  (207, 20, 'Co-ord Sets',      'WCS', 'women-co-ord-sets',   NULL, 8, true, @now, @now);

-- ── LEAF CATEGORIES — WOMEN / Bottomwear ─────────────────────
INSERT INTO categories (id, parent_id, name, code, slug, description, display_order, is_active, created_at, updated_at) VALUES
  (210, 21, 'Jeans',       'WJN', 'women-jeans',        NULL, 1, true, @now, @now),
  (211, 21, 'Trousers',    'WTR', 'women-trousers',     NULL, 2, true, @now, @now),
  (212, 21, 'Shorts',      'WSH', 'women-shorts',       NULL, 3, true, @now, @now),
  (213, 21, 'Skirts',      'SKR', 'skirts',             NULL, 4, true, @now, @now),
  (214, 21, 'Leggings',    'LGG', 'leggings',           NULL, 5, true, @now, @now),
  (215, 21, 'Track Pants', 'WTP', 'women-track-pants',  NULL, 6, true, @now, @now);

-- ── LEAF CATEGORIES — WOMEN / Footwear ───────────────────────
INSERT INTO categories (id, parent_id, name, code, slug, description, display_order, is_active, created_at, updated_at) VALUES
  (220, 22, 'Heels',     'HEL', 'heels',           NULL, 1, true, @now, @now),
  (221, 22, 'Flats',     'FLT', 'flats',           NULL, 2, true, @now, @now),
  (222, 22, 'Sneakers',  'WSN', 'women-sneakers',  NULL, 3, true, @now, @now),
  (223, 22, 'Sandals',   'WSL', 'women-sandals',   NULL, 4, true, @now, @now),
  (224, 22, 'Boots',     'BOT', 'boots',           NULL, 5, true, @now, @now),
  (225, 22, 'Slippers',  'SLP', 'slippers',        NULL, 6, true, @now, @now);

-- ── LEAF CATEGORIES — WOMEN / Accessories ────────────────────
INSERT INTO categories (id, parent_id, name, code, slug, description, display_order, is_active, created_at, updated_at) VALUES
  (230, 24, 'Handbags',        'HBG', 'handbags',         NULL, 1, true, @now, @now),
  (231, 24, 'Clutches',        'CLT', 'clutches',         NULL, 2, true, @now, @now),
  (232, 24, 'Jewellery',       'JWL', 'jewellery',        NULL, 3, true, @now, @now),
  (233, 24, 'Hair Accessories','HAR', 'hair-accessories', NULL, 4, true, @now, @now),
  (234, 24, 'Sunglasses',      'WGL', 'women-sunglasses', NULL, 5, true, @now, @now),
  (235, 24, 'Watches',         'WWC', 'women-watches',    NULL, 6, true, @now, @now);

-- ── LEAF CATEGORIES — KIDS / Boys Clothing ───────────────────
INSERT INTO categories (id, parent_id, name, code, slug, description, display_order, is_active, created_at, updated_at) VALUES
  (300, 30, 'Boys T-Shirts', 'BTS', 'boys-t-shirts', NULL, 1, true, @now, @now),
  (301, 30, 'Boys Shirts',   'BSH', 'boys-shirts',   NULL, 2, true, @now, @now),
  (302, 30, 'Boys Jeans',    'BJN', 'boys-jeans',    NULL, 3, true, @now, @now),
  (303, 30, 'Boys Shorts',   'BST', 'boys-shorts',   NULL, 4, true, @now, @now),
  (304, 30, 'Boys Ethnic',   'BET', 'boys-ethnic',   NULL, 5, true, @now, @now);

-- ── LEAF CATEGORIES — KIDS / Girls Clothing ──────────────────
INSERT INTO categories (id, parent_id, name, code, slug, description, display_order, is_active, created_at, updated_at) VALUES
  (310, 31, 'Girls Dresses', 'GDR', 'girls-dresses', NULL, 1, true, @now, @now),
  (311, 31, 'Girls Tops',    'GTP', 'girls-tops',    NULL, 2, true, @now, @now),
  (312, 31, 'Girls Jeans',   'GJN', 'girls-jeans',   NULL, 3, true, @now, @now),
  (313, 31, 'Girls Ethnic',  'GET', 'girls-ethnic',  NULL, 4, true, @now, @now),
  (314, 31, 'Girls Skirts',  'GSK', 'girls-skirts',  NULL, 5, true, @now, @now);

-- ── LEAF CATEGORIES — GEN Z / Streetwear ─────────────────────
INSERT INTO categories (id, parent_id, name, code, slug, description, display_order, is_active, created_at, updated_at) VALUES
  (500, 50, 'Graphic Tees',   'GRT', 'graphic-tees',   NULL, 1, true, @now, @now),
  (501, 50, 'Hoodies',        'GZH', 'genz-hoodies',   NULL, 2, true, @now, @now),
  (502, 50, 'Cargo Pants',    'GCP', 'genz-cargos',    NULL, 3, true, @now, @now),
  (503, 50, 'Bomber Jackets', 'BMB', 'bomber-jackets', NULL, 4, true, @now, @now);

-- ── LEAF CATEGORIES — GEN Z / Oversized ──────────────────────
INSERT INTO categories (id, parent_id, name, code, slug, description, display_order, is_active, created_at, updated_at) VALUES
  (510, 52, 'Oversized Tees',    'OVT', 'oversized-tees',    NULL, 1, true, @now, @now),
  (511, 52, 'Oversized Hoodies', 'OVH', 'oversized-hoodies', NULL, 2, true, @now, @now),
  (512, 52, 'Oversized Shirts',  'OVS', 'oversized-shirts',  NULL, 3, true, @now, @now);

-- ── LEAF CATEGORIES — GEN Z / Y2K ────────────────────────────
INSERT INTO categories (id, parent_id, name, code, slug, description, display_order, is_active, created_at, updated_at) VALUES
  (520, 53, 'Y2K Tops',        'YTP', 'y2k-tops',        NULL, 1, true, @now, @now),
  (521, 53, 'Y2K Bottoms',     'YBT', 'y2k-bottoms',     NULL, 2, true, @now, @now),
  (522, 53, 'Y2K Accessories', 'YAC', 'y2k-accessories', NULL, 3, true, @now, @now);

-- ── LEAF CATEGORIES — GEN Z / Sneakers ───────────────────────
INSERT INTO categories (id, parent_id, name, code, slug, description, display_order, is_active, created_at, updated_at) VALUES
  (530, 51, 'Chunky Sneakers',   'CKS', 'chunky-sneakers',   NULL, 1, true, @now, @now),
  (531, 51, 'Low-top Sneakers',  'LTS', 'low-top-sneakers',  NULL, 2, true, @now, @now),
  (532, 51, 'High-top Sneakers', 'HTS', 'high-top-sneakers', NULL, 3, true, @now, @now);

-- ── LEAF CATEGORIES — HOME ────────────────────────────────────
INSERT INTO categories (id, parent_id, name, code, slug, description, display_order, is_active, created_at, updated_at) VALUES
  (400, 40, 'Bedsheets',      'BDS', 'bedsheets',    NULL, 1, true, @now, @now),
  (401, 40, 'Pillows',        'PLW', 'pillows',      NULL, 2, true, @now, @now),
  (402, 40, 'Towels',         'TWL', 'towels',       NULL, 3, true, @now, @now),
  (410, 41, 'Mugs & Cups',    'MGC', 'mugs-cups',    NULL, 1, true, @now, @now),
  (411, 41, 'Plates & Bowls', 'PLB', 'plates-bowls', NULL, 2, true, @now, @now),
  (412, 42, 'Wall Art',       'WAR', 'wall-art',     NULL, 1, true, @now, @now),
  (413, 42, 'Candles',        'CDL', 'candles',      NULL, 2, true, @now, @now),
  (414, 42, 'Photo Frames',   'PHF', 'photo-frames', NULL, 3, true, @now, @now);

-- ============================================================
-- CATEGORY HIERARCHY LINKS
-- Dual-system: categories.parent_id (canonical) +
--              category_hierarchy_links (navigation ordering + cross-listing)
-- ROOT → GROUP links (parent_category_id = ROOT id)
-- GROUP → LEAF links (parent_category_id = GROUP id)
-- ============================================================

-- ── ROOT → GROUP links: MEN ───────────────────────────────────
INSERT INTO category_hierarchy_links (parent_category_id, child_category_id, is_primary, display_order, is_visible, created_at, updated_at) VALUES
  (1, 10, true, 1, true, @now, @now),
  (1, 11, true, 2, true, @now, @now),
  (1, 12, true, 3, true, @now, @now),
  (1, 13, true, 4, true, @now, @now),
  (1, 14, true, 5, true, @now, @now),
  (1, 15, true, 6, true, @now, @now),
  (1, 16, true, 7, true, @now, @now);

-- ── ROOT → GROUP links: WOMEN ─────────────────────────────────
INSERT INTO category_hierarchy_links (parent_category_id, child_category_id, is_primary, display_order, is_visible, created_at, updated_at) VALUES
  (2, 20, true, 1, true, @now, @now),
  (2, 21, true, 2, true, @now, @now),
  (2, 22, true, 3, true, @now, @now),
  (2, 23, true, 4, true, @now, @now),
  (2, 24, true, 5, true, @now, @now),
  (2, 25, true, 6, true, @now, @now),
  (2, 26, true, 7, true, @now, @now);

-- ── ROOT → GROUP links: KIDS ──────────────────────────────────
INSERT INTO category_hierarchy_links (parent_category_id, child_category_id, is_primary, display_order, is_visible, created_at, updated_at) VALUES
  (3, 30, true, 1, true, @now, @now),
  (3, 31, true, 2, true, @now, @now),
  (3, 32, true, 3, true, @now, @now),
  (3, 33, true, 4, true, @now, @now),
  (3, 34, true, 5, true, @now, @now);

-- ── ROOT → GROUP links: HOME ──────────────────────────────────
INSERT INTO category_hierarchy_links (parent_category_id, child_category_id, is_primary, display_order, is_visible, created_at, updated_at) VALUES
  (4, 40, true, 1, true, @now, @now),
  (4, 41, true, 2, true, @now, @now),
  (4, 42, true, 3, true, @now, @now),
  (4, 43, true, 4, true, @now, @now),
  (4, 44, true, 5, true, @now, @now);

-- ── ROOT → GROUP links: GEN Z ─────────────────────────────────
INSERT INTO category_hierarchy_links (parent_category_id, child_category_id, is_primary, display_order, is_visible, created_at, updated_at) VALUES
  (5, 50, true, 1, true, @now, @now),
  (5, 51, true, 2, true, @now, @now),
  (5, 52, true, 3, true, @now, @now),
  (5, 53, true, 4, true, @now, @now),
  (5, 54, true, 5, true, @now, @now);

-- ── GROUP → LEAF links: MEN / Topwear ────────────────────────
INSERT INTO category_hierarchy_links (parent_category_id, child_category_id, is_primary, display_order, is_visible, created_at, updated_at) VALUES
  (10, 100, true,  1, true, @now, @now),
  (10, 101, true,  2, true, @now, @now),
  (10, 102, true,  3, true, @now, @now),
  (10, 103, true,  4, true, @now, @now),
  (10, 104, true,  5, true, @now, @now),
  (10, 105, true,  6, true, @now, @now),
  (10, 106, true,  7, true, @now, @now),
  (10, 107, true,  8, true, @now, @now),
  (10, 108, true,  9, true, @now, @now),
  (10, 109, true, 10, true, @now, @now);

-- ── GROUP → LEAF links: MEN / Bottomwear ─────────────────────
INSERT INTO category_hierarchy_links (parent_category_id, child_category_id, is_primary, display_order, is_visible, created_at, updated_at) VALUES
  (11, 110, true, 1, true, @now, @now),
  (11, 111, true, 2, true, @now, @now),
  (11, 112, true, 3, true, @now, @now),
  (11, 113, true, 4, true, @now, @now),
  (11, 114, true, 5, true, @now, @now),
  (11, 115, true, 6, true, @now, @now);

-- ── GROUP → LEAF links: MEN / Footwear ───────────────────────
INSERT INTO category_hierarchy_links (parent_category_id, child_category_id, is_primary, display_order, is_visible, created_at, updated_at) VALUES
  (12, 120, true, 1, true, @now, @now),
  (12, 121, true, 2, true, @now, @now),
  (12, 122, true, 3, true, @now, @now),
  (12, 123, true, 4, true, @now, @now),
  (12, 124, true, 5, true, @now, @now),
  (12, 125, true, 6, true, @now, @now);

-- ── GROUP → LEAF links: MEN / Sports & Active ────────────────
INSERT INTO category_hierarchy_links (parent_category_id, child_category_id, is_primary, display_order, is_visible, created_at, updated_at) VALUES
  (13, 130, true, 1, true, @now, @now),
  (13, 131, true, 2, true, @now, @now),
  (13, 132, true, 3, true, @now, @now),
  (13, 133, true, 4, true, @now, @now),
  (13, 134, true, 5, true, @now, @now);

-- ── GROUP → LEAF links: MEN / Accessories ────────────────────
INSERT INTO category_hierarchy_links (parent_category_id, child_category_id, is_primary, display_order, is_visible, created_at, updated_at) VALUES
  (14, 140, true, 1, true, @now, @now),
  (14, 141, true, 2, true, @now, @now),
  (14, 142, true, 3, true, @now, @now),
  (14, 143, true, 4, true, @now, @now),
  (14, 144, true, 5, true, @now, @now),
  (14, 145, true, 6, true, @now, @now),
  (14, 146, true, 7, true, @now, @now);

-- ── GROUP → LEAF links: MEN / Ethnic Wear ────────────────────
INSERT INTO category_hierarchy_links (parent_category_id, child_category_id, is_primary, display_order, is_visible, created_at, updated_at) VALUES
  (15, 150, true, 1, true, @now, @now),
  (15, 151, true, 2, true, @now, @now),
  (15, 152, true, 3, true, @now, @now),
  (15, 153, true, 4, true, @now, @now);

-- ── GROUP → LEAF links: MEN / Winter Wear ────────────────────
INSERT INTO category_hierarchy_links (parent_category_id, child_category_id, is_primary, display_order, is_visible, created_at, updated_at) VALUES
  (16, 160, true, 1, true, @now, @now),
  (16, 161, true, 2, true, @now, @now),
  (16, 162, true, 3, true, @now, @now),
  (16, 163, true, 4, true, @now, @now);

-- ── GROUP → LEAF links: WOMEN / Topwear ──────────────────────
INSERT INTO category_hierarchy_links (parent_category_id, child_category_id, is_primary, display_order, is_visible, created_at, updated_at) VALUES
  (20, 200, true, 1, true, @now, @now),
  (20, 201, true, 2, true, @now, @now),
  (20, 202, true, 3, true, @now, @now),
  (20, 203, true, 4, true, @now, @now),
  (20, 204, true, 5, true, @now, @now),
  (20, 205, true, 6, true, @now, @now),
  (20, 206, true, 7, true, @now, @now),
  (20, 207, true, 8, true, @now, @now);

-- ── GROUP → LEAF links: WOMEN / Bottomwear ───────────────────
INSERT INTO category_hierarchy_links (parent_category_id, child_category_id, is_primary, display_order, is_visible, created_at, updated_at) VALUES
  (21, 210, true, 1, true, @now, @now),
  (21, 211, true, 2, true, @now, @now),
  (21, 212, true, 3, true, @now, @now),
  (21, 213, true, 4, true, @now, @now),
  (21, 214, true, 5, true, @now, @now),
  (21, 215, true, 6, true, @now, @now);

-- ── GROUP → LEAF links: WOMEN / Footwear ─────────────────────
INSERT INTO category_hierarchy_links (parent_category_id, child_category_id, is_primary, display_order, is_visible, created_at, updated_at) VALUES
  (22, 220, true, 1, true, @now, @now),
  (22, 221, true, 2, true, @now, @now),
  (22, 222, true, 3, true, @now, @now),
  (22, 223, true, 4, true, @now, @now),
  (22, 224, true, 5, true, @now, @now),
  (22, 225, true, 6, true, @now, @now);

-- ── GROUP → LEAF links: WOMEN / Accessories ──────────────────
INSERT INTO category_hierarchy_links (parent_category_id, child_category_id, is_primary, display_order, is_visible, created_at, updated_at) VALUES
  (24, 230, true, 1, true, @now, @now),
  (24, 231, true, 2, true, @now, @now),
  (24, 232, true, 3, true, @now, @now),
  (24, 233, true, 4, true, @now, @now),
  (24, 234, true, 5, true, @now, @now),
  (24, 235, true, 6, true, @now, @now);

-- ── GROUP → LEAF links: KIDS ──────────────────────────────────
INSERT INTO category_hierarchy_links (parent_category_id, child_category_id, is_primary, display_order, is_visible, created_at, updated_at) VALUES
  (30, 300, true, 1, true, @now, @now),
  (30, 301, true, 2, true, @now, @now),
  (30, 302, true, 3, true, @now, @now),
  (30, 303, true, 4, true, @now, @now),
  (30, 304, true, 5, true, @now, @now),
  (31, 310, true, 1, true, @now, @now),
  (31, 311, true, 2, true, @now, @now),
  (31, 312, true, 3, true, @now, @now),
  (31, 313, true, 4, true, @now, @now),
  (31, 314, true, 5, true, @now, @now);

-- ── GROUP → LEAF links: HOME ──────────────────────────────────
INSERT INTO category_hierarchy_links (parent_category_id, child_category_id, is_primary, display_order, is_visible, created_at, updated_at) VALUES
  (40, 400, true, 1, true, @now, @now),
  (40, 401, true, 2, true, @now, @now),
  (40, 402, true, 3, true, @now, @now),
  (41, 410, true, 1, true, @now, @now),
  (41, 411, true, 2, true, @now, @now),
  (42, 412, true, 1, true, @now, @now),
  (42, 413, true, 2, true, @now, @now),
  (42, 414, true, 3, true, @now, @now);

-- ── GROUP → LEAF links: GEN Z ─────────────────────────────────
INSERT INTO category_hierarchy_links (parent_category_id, child_category_id, is_primary, display_order, is_visible, created_at, updated_at) VALUES
  (50, 500, true, 1, true, @now, @now),
  (50, 501, true, 2, true, @now, @now),
  (50, 502, true, 3, true, @now, @now),
  (50, 503, true, 4, true, @now, @now),
  (52, 510, true, 1, true, @now, @now),
  (52, 511, true, 2, true, @now, @now),
  (52, 512, true, 3, true, @now, @now),
  (53, 520, true, 1, true, @now, @now),
  (53, 521, true, 2, true, @now, @now),
  (53, 522, true, 3, true, @now, @now),
  (51, 530, true, 1, true, @now, @now),
  (51, 531, true, 2, true, @now, @now),
  (51, 532, true, 3, true, @now, @now);

-- ── CROSS-LISTING: Gen Z Hoodies also appear under Oversized ─
INSERT INTO category_hierarchy_links (parent_category_id, child_category_id, is_primary, display_order, is_visible, created_at, updated_at) VALUES
  (52, 501, false, 4, true, @now, @now);

-- ── CROSS-LISTING: Men Sweatshirts also appear under Winter Wear ─
INSERT INTO category_hierarchy_links (parent_category_id, child_category_id, is_primary, display_order, is_visible, created_at, updated_at) VALUES
  (16, 104, false, 5, true, @now, @now);
