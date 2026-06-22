-- Seed data for token_packages
-- Chạy sau khi Hibernate đã tạo bảng (ddl-auto=update khởi động lần đầu)
INSERT INTO token_packages (id, name, description, price_matic, token_amount, active, created_at, updated_at) VALUES
(1, 'Gói Khởi Động',  '500 Token — Lý tưởng để bắt đầu',            0.05,  500,  true, NOW(), NOW()),
(2, 'Gói Tiêu Chuẩn', '1200 Token — Thêm 200 token bonus',           0.10, 1200,  true, NOW(), NOW()),
(3, 'Gói Phổ Biến',   '2500 Token — Thêm 500 token bonus',           0.20, 2500,  true, NOW(), NOW()),
(4, 'Gói VIP',        '6000 Token — Thêm 1500 token bonus đặc biệt', 0.50, 6000,  true, NOW(), NOW())
ON DUPLICATE KEY UPDATE
name         = VALUES(name),
description  = VALUES(description),
price_matic  = VALUES(price_matic),
token_amount = VALUES(token_amount),
active       = VALUES(active);
