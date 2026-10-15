CREATE TABLE dark_stores (
    id        VARCHAR(16) PRIMARY KEY,
    name      VARCHAR(80) NOT NULL,
    city      VARCHAR(40) NOT NULL,
    lat       DOUBLE PRECISION NOT NULL,
    lon       DOUBLE PRECISION NOT NULL,
    radius_km DOUBLE PRECISION NOT NULL DEFAULT 3.0,
    pickers   INT NOT NULL DEFAULT 3
);

CREATE TABLE products (
    sku      VARCHAR(24) PRIMARY KEY,
    name     VARCHAR(80) NOT NULL,
    category VARCHAR(40) NOT NULL,
    unit     VARCHAR(30) NOT NULL,
    price    NUMERIC(10, 2) NOT NULL,
    mrp      NUMERIC(10, 2) NOT NULL,
    emoji    VARCHAR(8) NOT NULL,
    max_per_order INT NOT NULL DEFAULT 10
);

-- Postgres is the stock ledger (source of truth); Redis holds the hot, sellable counters.
CREATE TABLE store_stock (
    store_id VARCHAR(16) NOT NULL REFERENCES dark_stores (id),
    sku      VARCHAR(24) NOT NULL REFERENCES products (sku),
    on_hand  INT NOT NULL CHECK (on_hand >= 0),
    PRIMARY KEY (store_id, sku)
);

-- Makes committing an order's stock idempotent: a retried commit cannot decrement twice.
CREATE TABLE committed_orders (
    order_id     VARCHAR(40) PRIMARY KEY,
    store_id     VARCHAR(16) NOT NULL,
    committed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO dark_stores (id, name, city, lat, lon) VALUES
    ('BLR-KOR', 'Koramangala 5th Block', 'Bengaluru', 12.9352, 77.6245),
    ('BLR-IND', 'Indiranagar 100ft Road', 'Bengaluru', 12.9784, 77.6408),
    ('BLR-HSR', 'HSR Layout Sector 2', 'Bengaluru', 12.9116, 77.6474),
    ('BLR-WHF', 'Whitefield ITPL', 'Bengaluru', 12.9698, 77.7500),
    ('BLR-JAY', 'Jayanagar 4th Block', 'Bengaluru', 12.9250, 77.5938),
    ('BOM-BAN', 'Bandra West', 'Mumbai', 19.0596, 72.8295),
    ('BOM-AND', 'Andheri West', 'Mumbai', 19.1364, 72.8296),
    ('BOM-POW', 'Powai Hiranandani', 'Mumbai', 19.1176, 72.9060),
    ('BOM-LOP', 'Lower Parel', 'Mumbai', 18.9980, 72.8301),
    ('DEL-HKV', 'Hauz Khas', 'Delhi NCR', 28.5494, 77.2001),
    ('GGN-CYB', 'Gurugram Cyber City', 'Delhi NCR', 28.4950, 77.0895);

INSERT INTO products (sku, name, category, unit, price, mrp, emoji, max_per_order) VALUES
    ('FV-BANANA', 'Robusta Banana', 'Fruits & Vegetables', '6 pcs', 48, 60, '🍌', 10),
    ('FV-ONION', 'Onion', 'Fruits & Vegetables', '1 kg', 42, 55, '🧅', 10),
    ('FV-TOMATO', 'Tomato Hybrid', 'Fruits & Vegetables', '500 g', 28, 35, '🍅', 10),
    ('FV-POTATO', 'Potato', 'Fruits & Vegetables', '1 kg', 38, 45, '🥔', 10),
    ('FV-CORIANDER', 'Coriander Leaves', 'Fruits & Vegetables', '100 g', 12, 20, '🌿', 10),
    ('FV-APPLE', 'Shimla Apple', 'Fruits & Vegetables', '4 pcs', 149, 180, '🍎', 10),
    ('FV-LEMON', 'Lemon', 'Fruits & Vegetables', '4 pcs', 25, 30, '🍋', 10),
    ('DA-MILK', 'Amul Taaza Toned Milk', 'Dairy, Bread & Eggs', '500 ml', 27, 27, '🥛', 10),
    ('DA-BUTTER', 'Amul Butter', 'Dairy, Bread & Eggs', '100 g', 58, 58, '🧈', 10),
    ('DA-EGGS', 'Farm Eggs', 'Dairy, Bread & Eggs', '6 pcs', 54, 60, '🥚', 10),
    ('DA-PANEER', 'Fresh Paneer', 'Dairy, Bread & Eggs', '200 g', 90, 100, '🧀', 10),
    ('DA-CURD', 'Nandini Curd', 'Dairy, Bread & Eggs', '400 g', 40, 42, '🥣', 10),
    ('DA-BREAD', 'Brown Bread', 'Dairy, Bread & Eggs', '400 g', 50, 55, '🍞', 10),
    ('SN-CHIPS', 'Lay''s Classic Salted', 'Snacks & Munchies', '52 g', 20, 20, '🍟', 10),
    ('SN-KURKURE', 'Kurkure Masala Munch', 'Snacks & Munchies', '90 g', 20, 20, '🌶️', 10),
    ('SN-BHUJIA', 'Haldiram''s Aloo Bhujia', 'Snacks & Munchies', '200 g', 55, 60, '🥨', 10),
    ('SN-COOKIES', 'Dark Fantasy Choco Fills', 'Snacks & Munchies', '75 g', 40, 40, '🍪', 10),
    ('SN-MAGGI', 'Maggi 2-Minute Noodles', 'Snacks & Munchies', '4 x 70 g', 56, 60, '🍜', 10),
    ('SN-SILK', 'Cadbury Dairy Milk Silk', 'Snacks & Munchies', '60 g', 90, 90, '🍫', 10),
    ('BV-COKE', 'Coca-Cola', 'Cold Drinks & Juices', '750 ml', 45, 45, '🥤', 10),
    ('BV-REDBULL', 'Red Bull Energy Drink', 'Cold Drinks & Juices', '250 ml', 125, 125, '⚡', 10),
    ('BV-TEA', 'Tata Tea Gold', 'Tea, Coffee & More', '250 g', 160, 175, '🍵', 10),
    ('BV-COFFEE', 'Nescafé Classic', 'Tea, Coffee & More', '50 g', 180, 190, '☕', 10),
    ('BV-WATER', 'Bisleri Water', 'Cold Drinks & Juices', '1 L', 20, 20, '💧', 10),
    ('BV-AAMRAS', 'Paper Boat Aamras', 'Cold Drinks & Juices', '200 ml', 30, 35, '🥭', 10),
    ('PC-PASTE', 'Colgate Strong Teeth', 'Personal Care', '200 g', 110, 125, '🪥', 10),
    ('PC-SOAP', 'Dove Cream Beauty Bar', 'Personal Care', '3 x 100 g', 165, 195, '🧼', 10),
    ('PC-SHAMPOO', 'Head & Shoulders Shampoo', 'Personal Care', '180 ml', 199, 230, '🧴', 10),
    ('PC-RAZOR', 'Gillette Guard Razor', 'Personal Care', '1 pc', 145, 160, '🪒', 10),
    ('HM-DETERGENT', 'Surf Excel Easy Wash', 'Home & Cleaning', '1 kg', 140, 155, '🧺', 10),
    ('HM-VIM', 'Vim Dishwash Bar', 'Home & Cleaning', '300 g', 30, 32, '🍽️', 10),
    ('HM-HARPIC', 'Harpic Toilet Cleaner', 'Home & Cleaning', '500 ml', 95, 105, '🚽', 10),
    ('HM-BATTERY', 'Duracell AA Batteries', 'Home & Cleaning', '4 pcs', 170, 190, '🔋', 10),
    ('FLASH-PS5', 'PlayStation 5 Slim (Flash Deal)', 'Flash Deals', '1 console', 29990, 54990, '🎮', 1);

-- Deterministic pseudo-random stock levels per store and product.
INSERT INTO store_stock (store_id, sku, on_hand)
SELECT s.id, p.sku,
       CASE WHEN p.sku = 'FLASH-PS5' THEN 50
            ELSE 30 + (abs(hashtext(s.id || p.sku)) % 90) END
FROM dark_stores s CROSS JOIN products p;
