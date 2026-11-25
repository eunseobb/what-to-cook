-- 이미지가 없는 레시피 카드의 플레이스홀더 색. 색값 대신 프론트 디자인 토큰 이름(--color-<key>)을 둔다.
ALTER TABLE tag ADD COLUMN color_key VARCHAR(30) NOT NULL DEFAULT 'accent-sage';

-- 대표 태그는 주로 음식 종류이므로 음식 종류끼리 색을 나눈다. plum(AI 배지)과 primary(주요 버튼)는 쓰지 않는다.
UPDATE tag SET color_key = 'surface-card' WHERE type = 'CUISINE' AND name IN ('중식', '디저트');
UPDATE tag SET color_key = 'accent-gray' WHERE type = 'CUISINE' AND name = '일식';
UPDATE tag SET color_key = 'accent-navy' WHERE type = 'CUISINE' AND name = '양식';
