-- =============================================================================
-- APIScope Sample App — Seed Data
-- Loaded by Spring Boot after Hibernate creates the schema (create-drop).
-- H2 console: http://localhost:8080/h2-console  (JDBC URL: jdbc:h2:mem:sampledb)
-- =============================================================================

-- ── Products (SKUs P001–P010) ─────────────────────────────────────────────────
-- P001 stock = 42  so the Flow Tracer error demo (quantity=999) still triggers
-- P003, P006, P009 are intentionally low-stock for the low-stock alerts endpoint
INSERT INTO product (sku, name, category, price, stock_quantity, warehouse_id, reorder_level, status) VALUES
('P001', 'Wireless Headphones Pro',    'Electronics', 149.99,  42,  'WH-EAST-01',  10, 'ACTIVE'),
('P002', 'Mechanical Keyboard RGB',    'Electronics',  89.99, 120,  'WH-EAST-01',  20, 'ACTIVE'),
('P003', 'USB-C Hub 7-Port',           'Electronics',  49.99,   8,  'WH-WEST-02',  15, 'ACTIVE'),
('P004', 'Ergonomic Mouse Pad XL',     'Accessories',  24.99, 350,  'WH-EAST-01',  50, 'ACTIVE'),
('P005', 'Standing Desk Converter',    'Furniture',   299.99,  15,  'WH-SOUTH-03',  5, 'ACTIVE'),
('P006', 'Dual Monitor Arm',           'Accessories',  79.99,   6,  'WH-WEST-02',  10, 'ACTIVE'),
('P007', 'Noise-Cancelling Earbuds',   'Electronics', 199.99,  75,  'WH-EAST-01',  15, 'ACTIVE'),
('P008', 'Laptop Backpack Pro 17"',    'Accessories',  59.99, 200,  'WH-EAST-01',  30, 'ACTIVE'),
('P009', 'Smart LED Desk Lamp',        'Electronics',  39.99,   4,  'WH-SOUTH-03', 10, 'ACTIVE'),
('P010', 'Portable SSD 1 TB',         'Electronics', 119.99,  90,  'WH-WEST-02',  20, 'ACTIVE');

-- ── Customers ─────────────────────────────────────────────────────────────────
INSERT INTO customer (email, name, phone, city, country, created_date) VALUES
('alice@example.com',  'Alice Johnson',  '+1-555-0101', 'New York',    'US', '2026-01-15'),
('bob@example.com',    'Bob Smith',      '+1-555-0102', 'Los Angeles', 'US', '2026-02-03'),
('carol@example.com',  'Carol Davis',    '+44-20-0001', 'London',      'GB', '2026-02-20'),
('david@example.com',  'David Kim',      '+82-2-0001',  'Seoul',       'KR', '2026-03-10'),
('eva@example.com',    'Eva Martinez',   '+34-91-0001', 'Madrid',      'ES', '2026-04-01');

-- ── Sales Orders ──────────────────────────────────────────────────────────────
-- customer_id / product_id reference the auto-generated IDENTITY columns (1-based)
INSERT INTO sales_order (order_ref, customer_id, product_id, quantity, unit_price, total_amount, status, created_date) VALUES
('ORD-00000001', 1, 1,  2, 149.99, 299.98, 'DELIVERED',  '2026-01-20'),
('ORD-00000002', 2, 7,  1, 199.99, 199.99, 'DELIVERED',  '2026-02-05'),
('ORD-00000003', 3, 2,  3,  89.99, 269.97, 'SHIPPED',    '2026-02-22'),
('ORD-00000004', 1, 10, 2, 119.99, 239.98, 'CONFIRMED',  '2026-03-01'),
('ORD-00000005', 4, 5,  1, 299.99, 299.99, 'DELIVERED',  '2026-03-12'),
('ORD-00000006', 5, 8,  2,  59.99, 119.98, 'DELIVERED',  '2026-03-15'),
('ORD-00000007', 2, 1,  1, 149.99, 149.99, 'SHIPPED',    '2026-03-20'),
('ORD-00000008', 3, 4,  5,  24.99, 124.95, 'CONFIRMED',  '2026-04-02'),
('ORD-00000009', 1, 7,  2, 199.99, 399.98, 'DELIVERED',  '2026-04-10'),
('ORD-00000010', 4, 2,  1,  89.99,  89.99, 'CANCELLED',  '2026-04-11'),
('ORD-00000011', 5, 3,  1,  49.99,  49.99, 'CONFIRMED',  '2026-04-15'),
('ORD-00000012', 2, 6,  2,  79.99, 159.98, 'SHIPPED',    '2026-04-18'),
('ORD-00000013', 3, 9,  1,  39.99,  39.99, 'CONFIRMED',  '2026-04-22'),
('ORD-00000014', 1, 1,  3, 149.99, 449.97, 'CONFIRMED',  '2026-05-01'),
('ORD-00000015', 4, 8,  1,  59.99,  59.99, 'CONFIRMED',  '2026-05-05');

-- ── Payments ──────────────────────────────────────────────────────────────────
INSERT INTO payment (payment_ref, order_ref, amount, currency, status, payment_method_id, auth_code, created_at) VALUES
('PAY-00000001', 'ORD-00000001', 299.98, 'USD', 'CAPTURED',    'pm_visa_4242',  'AUTH-112233', '2026-01-20 10:05:00'),
('PAY-00000002', 'ORD-00000002', 199.99, 'USD', 'CAPTURED',    'pm_mc_5555',    'AUTH-223344', '2026-02-05 14:30:00'),
('PAY-00000003', 'ORD-00000003', 269.97, 'GBP', 'CAPTURED',    'pm_visa_4100',  'AUTH-334455', '2026-02-22 09:15:00'),
('PAY-00000004', 'ORD-00000004', 239.98, 'USD', 'AUTHORISED',  'pm_amex_3782',  'AUTH-445566', '2026-03-01 11:20:00'),
('PAY-00000005', 'ORD-00000005', 299.99, 'USD', 'CAPTURED',    'pm_visa_4242',  'AUTH-556677', '2026-03-12 16:45:00'),
('PAY-00000006', 'ORD-00000006', 119.98, 'EUR', 'CAPTURED',    'pm_mc_5105',    'AUTH-667788', '2026-03-15 08:30:00'),
('PAY-00000007', 'ORD-00000007', 149.99, 'USD', 'AUTHORISED',  'pm_mc_5555',    'AUTH-778899', '2026-03-20 13:00:00'),
('PAY-00000008', 'ORD-00000010',  89.99, 'USD', 'REFUNDED',    'pm_visa_4242',  'AUTH-889900', '2026-04-11 10:00:00');
