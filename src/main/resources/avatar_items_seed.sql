-- Seed data for DiceBear Avatars
INSERT INTO avatar_items (id, name, src_url, price_matic, price_tokens, style, active, created_at) VALUES
(1, 'Adventurer Leo', 'https://api.dicebear.com/9.x/adventurer/svg?seed=Leo', 0.0, 0, 'adventurer', true, NOW()),
(2, 'Adventurer Mia', 'https://api.dicebear.com/9.x/adventurer/svg?seed=Mia', 0.0, 0, 'adventurer', true, NOW()),
(3, 'Neutral Spark', 'https://api.dicebear.com/9.x/adventurer-neutral/svg?seed=Spark', 0.0, 0, 'adventurer-neutral', true, NOW()),
(4, 'Neutral Shadow', 'https://api.dicebear.com/9.x/adventurer-neutral/svg?seed=Shadow', 0.0, 0, 'adventurer-neutral', true, NOW()),
(5, 'Big Ears Bunny', 'https://api.dicebear.com/9.x/big-ears/svg?seed=Bunny', 0.0, 0, 'big-ears', true, NOW()),
(6, 'Big Ears Fox', 'https://api.dicebear.com/9.x/big-ears/svg?seed=Fox', 0.0, 0, 'big-ears', true, NOW()),
(7, 'Cyber Boy Bot', 'https://api.dicebear.com/9.x/bottts/svg?seed=CyberBoy', 0.005, 500, 'bottts', true, NOW()),
(8, 'Retro Girl Bot', 'https://api.dicebear.com/9.x/bottts/svg?seed=RetroGirl', 0.005, 500, 'bottts', true, NOW()),
(9, 'Cool Executive', 'https://api.dicebear.com/9.x/avataaars/svg?seed=CoolExec', 0.01, 1000, 'avataaars', true, NOW()),
(10, 'Techno Geek', 'https://api.dicebear.com/9.x/avataaars/svg?seed=Techno', 0.01, 1000, 'avataaars', true, NOW()),
(11, 'Thumbs Up', 'https://api.dicebear.com/9.x/thumbs/svg?seed=Up', 0.002, 200, 'thumbs', true, NOW()),
(12, 'Thumbs Down', 'https://api.dicebear.com/9.x/thumbs/svg?seed=Down', 0.002, 200, 'thumbs', true, NOW())
ON DUPLICATE KEY UPDATE
name = VALUES(name),
src_url = VALUES(src_url),
price_matic = VALUES(price_matic),
price_tokens = VALUES(price_tokens),
style = VALUES(style),
active = VALUES(active);
