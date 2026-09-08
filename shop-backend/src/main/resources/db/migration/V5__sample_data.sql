-- V5: sample catalog (no real PII; no plaintext passwords)
INSERT INTO category (category_id, parent_id, name, slug, sort_order, active) VALUES
 (1, NULL, '여성', 'women', 1, TRUE),
 (2, NULL, '키즈', 'kids', 2, TRUE),
 (3, 1, '상의', 'women-tops', 1, TRUE),
 (4, 1, '원피스', 'women-dresses', 2, TRUE),
 (5, 1, '하의', 'women-bottoms', 3, TRUE),
 (6, 2, '여아', 'kids-girls', 1, TRUE);

SELECT setval('category_category_id_seq', (SELECT MAX(category_id) FROM category));

INSERT INTO product (product_id, category_id, product_name, brand_name, summary, description,
                     normal_price, sale_price, status, fit_type, material, thickness, stretch, see_through, season)
VALUES
 (1, 3, '린넨 블렌드 셔츠', 'Petitcamel', '부드러운 크림톤 데일리 셔츠',
  '자연광 아래에서도 편안한 린넨 블렌드 소재의 셔츠입니다.',
  59000, 49000, 'ON_SALE', 'REGULAR', '린넨 혼방', '보통', '약간', '없음', 'SS'),
 (2, 4, '코튼 플레어 원피스', 'Petitcamel', '데이트·모임용 플레어 원피스',
  '아이보리 코튼 원단의 여유로운 플레어 실루엣입니다.',
  89000, 79000, 'ON_SALE', 'RELAXED', '코튼', '보통', '없음', '없음', 'SS'),
 (3, 5, '와이드 코튼 팬츠', 'Petitcamel', '편안한 데일리 와이드 팬츠',
  '밝은 베이지 톤의 와이드 핏 팬츠입니다.',
  69000, 59000, 'ON_SALE', 'RELAXED', '코튼', '보통', '없음', '없음', 'FW'),
 (4, 6, '키즈 코튼 티셔츠', 'Petitcamel', '부드러운 아동용 티셔츠',
  '피부 자극을 줄인 순면 티셔츠입니다.',
  29000, 25000, 'ON_SALE', 'REGULAR', '코튼', '얇음', '약간', '없음', 'SS');

SELECT setval('product_product_id_seq', (SELECT MAX(product_id) FROM product));

INSERT INTO product_sku (sku_id, product_id, sku_code, color, size, additional_price, status) VALUES
 (1, 1, 'PC-SHIRT-IV-S', 'Ivory', 'S', 0, 'ON_SALE'),
 (2, 1, 'PC-SHIRT-IV-M', 'Ivory', 'M', 0, 'ON_SALE'),
 (3, 1, 'PC-SHIRT-CM-M', 'Camel', 'M', 0, 'ON_SALE'),
 (4, 2, 'PC-DRESS-IV-S', 'Ivory', 'S', 0, 'ON_SALE'),
 (5, 2, 'PC-DRESS-IV-M', 'Ivory', 'M', 0, 'ON_SALE'),
 (6, 3, 'PC-PANTS-BG-M', 'Beige', 'M', 0, 'ON_SALE'),
 (7, 3, 'PC-PANTS-BG-L', 'Beige', 'L', 0, 'ON_SALE'),
 (8, 4, 'PC-KIDS-TEE-CR-110', 'Cream', '110', 0, 'ON_SALE'),
 (9, 4, 'PC-KIDS-TEE-CR-120', 'Cream', '120', 0, 'ON_SALE');

SELECT setval('product_sku_sku_id_seq', (SELECT MAX(sku_id) FROM product_sku));

INSERT INTO inventory (sku_id, stock_quantity, reserved_quantity, reorder_point) VALUES
 (1, 20, 0, 5),
 (2, 15, 0, 5),
 (3, 10, 0, 3),
 (4, 12, 0, 5),
 (5, 8, 0, 5),
 (6, 18, 0, 5),
 (7, 0, 0, 5),
 (8, 25, 0, 5),
 (9, 22, 0, 5);

INSERT INTO product_image (product_id, image_url, image_type, alt_text, sort_order) VALUES
 (1, NULL, 'MAIN', '린넨 블렌드 셔츠 메인', 0),
 (2, NULL, 'MAIN', '코튼 플레어 원피스 메인', 0),
 (3, NULL, 'MAIN', '와이드 코튼 팬츠 메인', 0),
 (4, NULL, 'MAIN', '키즈 코튼 티셔츠 메인', 0);

INSERT INTO product_measurement (product_id, size, shoulder, chest, waist, sleeve, total_length) VALUES
 (1, 'S', 36.0, 46.0, 44.0, 58.0, 68.0),
 (1, 'M', 37.5, 48.0, 46.0, 59.0, 70.0),
 (2, 'S', 34.0, 44.0, 40.0, NULL, 110.0),
 (2, 'M', 35.5, 46.0, 42.0, NULL, 112.0),
 (3, 'M', NULL, NULL, 36.0, NULL, 98.0),
 (3, 'L', NULL, NULL, 38.0, NULL, 100.0);
