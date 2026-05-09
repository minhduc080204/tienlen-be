INSERT INTO nft_items (id, name, description, price_matic, image_url, active) VALUES
(1, 'Classic Blue Back', 'Traditional blue card back design for a classic feel.', 0.1, 'https://example.com/nft/classic-blue.png', true),
(2, 'Golden Dragon Back', 'Exquisite golden dragon pattern, symbol of power and luck.', 0.5, 'https://example.com/nft/golden-dragon.png', true),
(3, 'Cyberpunk Frame', 'Futuristic neon frame to make your profile stand out.', 0.2, 'https://example.com/nft/cyberpunk-frame.png', true)
ON DUPLICATE KEY UPDATE
name = VALUES(name),
description = VALUES(description),
price_matic = VALUES(price_matic),
image_url = VALUES(image_url),
active = VALUES(active);
