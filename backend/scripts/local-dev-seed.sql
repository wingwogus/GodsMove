-- 로컬 개발 DB 시드 스크립트
-- 목적: 회원 2명(M1 보이스테스트, M2 본인이쓸이메일) 기준으로 일지(farming_record) 30건 + 게시글(community_post) 30건씩 채운다.
-- 사용법:
--   docker exec -i ccc-postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f - < backend/scripts/local-dev-seed.sql
-- 전제:
--   1) 로컬 DB가 완전히 재생성(ddl-auto=create 재기동)된 경우, 앱을 한 번 부팅해 MedicinalCropSeedRunner가
--      crop 테이블을 먼저 채우게 한 뒤 이 스크립트를 실행한다 (crop_id는 이름으로 서브쿼리 조회하므로 순서 중요).
--   2) 모든 INSERT는 ON CONFLICT (id) DO NOTHING 으로 재실행해도 중복이 생기지 않는다.
--   3) member.password_hash는 실제 해시를 커밋 파일에 남기지 않기 위해 NULL로 둔다(로컬 전용 시드 데이터).
BEGIN;

-- 1) 회원 (이미 존재해야 정상이며, DB가 밀렸을 때 복구용으로만 포함)
INSERT INTO member (id, email, phone, status, management_type, name, nickname, experience_level, birth_date, password_hash, role, profile_media_id, withdrawn_at, created_at, updated_at)
VALUES
  ('7f6ec560-b894-4aff-8b47-2033d18f8658', 'voice-qa-1784189582@example.com', '01000000000', 'ACTIVE', 'AGRICULTURAL_INDIVIDUAL', '보이스테스트', '보이스테스트', 5, '1990-01-01', NULL, 'ROLE_USER', NULL, NULL, '2026-07-16 17:13:03.583984', '2026-07-16 17:13:03.583984'),
  ('ad55875b-84f9-4f1d-be1b-67eec3884575', '본인이쓸이메일@example.com', NULL, 'ACTIVE', NULL, NULL, NULL, NULL, NULL, NULL, 'ROLE_USER', NULL, NULL, '2026-07-16 17:18:17.654214', '2026-07-16 17:18:17.654214')
ON CONFLICT (id) DO NOTHING;

-- 2) 농장
INSERT INTO farm (id, owner_member_id, name, road_address, jibun_address, latitude, longitude, pnu, land_category, area_sqm, area_is_manual_entry, data_source_address, data_source_coordinate, data_source_land_characteristic, data_source_parcel, created_at, updated_at)
VALUES
  ('abda29c3-e273-4ea1-a410-3f74637d785c', '7f6ec560-b894-4aff-8b47-2033d18f8658', '테스트농장', '경기도 이천시 테스트로 1', NULL, 37.27, 128.44, NULL, NULL, NULL, false, NULL, NULL, NULL, NULL, '2026-07-16 17:13:04.075974', '2026-07-16 17:13:04.075974'),
  ('b61d6d34-5b24-4573-8fee-90c7ddb09d29', 'ad55875b-84f9-4f1d-be1b-67eec3884575', '청명약초농장', '충청북도 제천시 봉양읍 약초로 128', NULL, 37.1326, 128.1930, NULL, NULL, 3300.00, true, NULL, NULL, NULL, NULL, '2026-01-05 09:00:00', '2026-01-05 09:00:00')
ON CONFLICT (id) DO NOTHING;

-- 3) 병해충 방제 참조 데이터
INSERT INTO pest (id, name, created_at, updated_at)
VALUES
  ('fbd890cf-8697-40ee-9dc9-7c5a7f29fd12', '진딧물', '2026-01-05 09:00:00', '2026-01-05 09:00:00'),
  ('55ee86d3-d77c-4427-a3c0-94034b1c0687', '총채벌레', '2026-01-05 09:00:00', '2026-01-05 09:00:00')
ON CONFLICT (id) DO NOTHING;

INSERT INTO pesticide (id, item_name, brand_name, active_ingredient, formulation, usage_category, human_toxicity, fish_toxicity, manufacturer, created_at, updated_at)
VALUES
  ('2be90716-4951-450c-a4e0-e1b416a27a4b', '다이센엠-45', '농협케미컬', '만코제브', '수화제', '살균제', '저독성', '저독성', '농협케미컬', '2026-01-05 09:00:00', '2026-01-05 09:00:00'),
  ('a21e212c-da40-4cbc-9d0f-74afd1d5b8ba', '코니도', '바이엘', '이미다클로프리드', '액상수화제', '살충제', '저독성', '중독성', '바이엘크롭사이언스', '2026-01-05 09:00:00', '2026-01-05 09:00:00')
ON CONFLICT (id) DO NOTHING;

-- 3-1) 회원 등록 작물 (음성 세션이 농지별 작물 목록·농약 필터에 사용)
INSERT INTO member_crop (id, member_id, farm_id, crop_id, created_at, updated_at)
VALUES
  ('c1a94f10-0000-4000-8000-000000000001', '7f6ec560-b894-4aff-8b47-2033d18f8658', 'abda29c3-e273-4ea1-a410-3f74637d785c', (SELECT id FROM crop WHERE name = '감자'), '2026-01-05 09:00:00', '2026-01-05 09:00:00'),
  ('c1a94f10-0000-4000-8000-000000000002', '7f6ec560-b894-4aff-8b47-2033d18f8658', 'abda29c3-e273-4ea1-a410-3f74637d785c', (SELECT id FROM crop WHERE name = '가지'), '2026-01-05 09:00:00', '2026-01-05 09:00:00'),
  ('c1a94f10-0000-4000-8000-000000000003', '7f6ec560-b894-4aff-8b47-2033d18f8658', 'abda29c3-e273-4ea1-a410-3f74637d785c', (SELECT id FROM crop WHERE name = '가락지나물'), '2026-01-05 09:00:00', '2026-01-05 09:00:00'),
  ('c1a94f10-0000-4000-8000-000000000004', 'ad55875b-84f9-4f1d-be1b-67eec3884575', 'b61d6d34-5b24-4573-8fee-90c7ddb09d29', (SELECT id FROM crop WHERE name = '황기'), '2026-01-05 09:00:00', '2026-01-05 09:00:00')
ON CONFLICT (id) DO NOTHING;

-- 3-2) 농약-작물 적용 사실 (음성 지침의 작물 기반 농약 목록 주입 경로 검증용)
INSERT INTO pesticide_application (id, pesticide_id, pest_id, crop_name, dilution_rate, usage_amount, usage_timing, max_usage_count, created_at, updated_at)
VALUES
  ('d2b85e20-0000-4000-8000-000000000001', 'a21e212c-da40-4cbc-9d0f-74afd1d5b8ba', 'fbd890cf-8697-40ee-9dc9-7c5a7f29fd12', '감자', '2000배', NULL, NULL, NULL, '2026-01-05 09:00:00', '2026-01-05 09:00:00'),
  ('d2b85e20-0000-4000-8000-000000000002', 'a21e212c-da40-4cbc-9d0f-74afd1d5b8ba', '55ee86d3-d77c-4427-a3c0-94034b1c0687', '가지', '2000배', NULL, NULL, NULL, '2026-01-05 09:00:00', '2026-01-05 09:00:00'),
  ('d2b85e20-0000-4000-8000-000000000003', '2be90716-4951-450c-a4e0-e1b416a27a4b', 'fbd890cf-8697-40ee-9dc9-7c5a7f29fd12', '감자', '500배', NULL, NULL, NULL, '2026-01-05 09:00:00', '2026-01-05 09:00:00')
ON CONFLICT (id) DO NOTHING;

-- 4) 일지 (farming_record) 60건 (회원당 30건)
INSERT INTO farming_record (id, member_id, farm_id, crop_id, work_type, worked_at, weather_condition, weather_temperature, memo, entry_mode, is_deleted, created_at, updated_at)
VALUES
  ('38e1a599-68f5-428f-a724-274dfa8034be', '7f6ec560-b894-4aff-8b47-2033d18f8658', 'abda29c3-e273-4ea1-a410-3f74637d785c', (SELECT id FROM crop WHERE name = '가락지나물'), 'PLANTING', '2026-02-01 16:00:00', '맑음', 5, '가락지나물 모종을 정식했다. 뿌리 활착이 잘 되도록 물을 충분히 줬다.', 'MANUAL', false, '2026-02-01 18:00:00', '2026-02-01 18:00:00'),
  ('2ca2b77d-1da4-40fe-a65d-431b0eebbd62', '7f6ec560-b894-4aff-8b47-2033d18f8658', 'abda29c3-e273-4ea1-a410-3f74637d785c', (SELECT id FROM crop WHERE name = '감자'), 'WATERING', '2026-02-06 07:20:00', '맑음', 2, '가뭄이 이어져 감자에 스프링클러로 관수했다.', 'MANUAL', false, '2026-02-06 08:20:00', '2026-02-06 08:20:00'),
  ('87039e65-5454-4de7-bd65-2a632f765fcc', '7f6ec560-b894-4aff-8b47-2033d18f8658', 'abda29c3-e273-4ea1-a410-3f74637d785c', (SELECT id FROM crop WHERE name = '가지'), 'FERTILIZING', '2026-02-12 08:10:00', '흐림', 1, '가지 밭에 유기질 비료를 시비했다.', 'MANUAL', false, '2026-02-12 11:10:00', '2026-02-12 11:10:00'),
  ('54941f39-f057-4bbb-8468-d4cc3a840655', '7f6ec560-b894-4aff-8b47-2033d18f8658', 'abda29c3-e273-4ea1-a410-3f74637d785c', (SELECT id FROM crop WHERE name = '가락지나물'), 'WATERING', '2026-02-17 08:00:00', '맑음', 5, '가락지나물 잎이 처져 있어 아침 일찍 물을 줬다.', 'MANUAL', false, '2026-02-17 11:00:00', '2026-02-17 11:00:00'),
  ('f7163818-2068-4e54-8483-67b1ddf2f4a1', '7f6ec560-b894-4aff-8b47-2033d18f8658', 'abda29c3-e273-4ea1-a410-3f74637d785c', (SELECT id FROM crop WHERE name = '감자'), 'PLANTING', '2026-02-23 16:40:00', '흐림', 3, '감자 씨앗을 파종했다. 발아율을 높이기 위해 두둑을 미리 정리해두었다.', 'MANUAL', false, '2026-02-23 17:40:00', '2026-02-23 17:40:00'),
  ('45883659-7a56-4f6c-9b20-3840f4084c9c', '7f6ec560-b894-4aff-8b47-2033d18f8658', 'abda29c3-e273-4ea1-a410-3f74637d785c', (SELECT id FROM crop WHERE name = '가지'), 'WEEDING', '2026-02-28 07:40:00', '맑음', 2, '가지 두둑 주변 김매기를 했다.', 'MANUAL', false, '2026-02-28 08:40:00', '2026-02-28 08:40:00'),
  ('04d9316a-36f0-445d-828e-394c87d2c7c7', '7f6ec560-b894-4aff-8b47-2033d18f8658', 'abda29c3-e273-4ea1-a410-3f74637d785c', (SELECT id FROM crop WHERE name = '가락지나물'), 'WATERING', '2026-03-06 14:00:00', '맑음', 9, '가락지나물 잎이 처져 있어 아침 일찍 물을 줬다.', 'MANUAL', false, '2026-03-06 16:00:00', '2026-03-06 16:00:00'),
  ('6d0e6f12-6483-4f88-9670-cb7f9555c0a4', '7f6ec560-b894-4aff-8b47-2033d18f8658', 'abda29c3-e273-4ea1-a410-3f74637d785c', (SELECT id FROM crop WHERE name = '감자'), 'PEST_CONTROL', '2026-03-11 07:00:00', '비', 9, '예방 차원에서 감자에 방제 작업을 실시했다.', 'MANUAL', false, '2026-03-11 08:00:00', '2026-03-11 08:00:00'),
  ('ef7ab756-32d0-4435-a5a5-9f08e1dbb5c5', '7f6ec560-b894-4aff-8b47-2033d18f8658', 'abda29c3-e273-4ea1-a410-3f74637d785c', (SELECT id FROM crop WHERE name = '가지'), 'FERTILIZING', '2026-03-17 08:10:00', '흐림', 11, '가지 밭에 유기질 비료를 시비했다.', 'MANUAL', false, '2026-03-17 11:10:00', '2026-03-17 11:10:00'),
  ('79c2d0b0-fb8d-4717-ae8c-455d80a837ea', '7f6ec560-b894-4aff-8b47-2033d18f8658', 'abda29c3-e273-4ea1-a410-3f74637d785c', (SELECT id FROM crop WHERE name = '가락지나물'), 'WATERING', '2026-03-22 15:40:00', '맑음', 8, '가락지나물 밭에 물을 줬다. 토양이 많이 말라 있어 평소보다 오래 관수했다.', 'MANUAL', false, '2026-03-22 17:40:00', '2026-03-22 17:40:00'),
  ('38dafd71-0178-45c9-a323-abba78e69ee2', '7f6ec560-b894-4aff-8b47-2033d18f8658', 'abda29c3-e273-4ea1-a410-3f74637d785c', (SELECT id FROM crop WHERE name = '감자'), 'WEEDING', '2026-03-28 07:40:00', '비', 9, '감자 밭 사이 잡초를 제거했다. 장마철 전이라 그런지 잡초가 빨리 자랐다.', 'MANUAL', false, '2026-03-28 08:40:00', '2026-03-28 08:40:00'),
  ('69d889ae-8c07-472a-a359-e301040fc471', '7f6ec560-b894-4aff-8b47-2033d18f8658', 'abda29c3-e273-4ea1-a410-3f74637d785c', (SELECT id FROM crop WHERE name = '가지'), 'PEST_CONTROL', '2026-04-02 08:40:00', '황사', 17, '예방 차원에서 가지에 방제 작업을 실시했다.', 'MANUAL', false, '2026-04-02 10:40:00', '2026-04-02 10:40:00'),
  ('a6032fcf-c93f-4c0a-b1a8-494d3d5e6796', '7f6ec560-b894-4aff-8b47-2033d18f8658', 'abda29c3-e273-4ea1-a410-3f74637d785c', (SELECT id FROM crop WHERE name = '가락지나물'), 'WATERING', '2026-04-08 14:10:00', '황사', 16, '가락지나물 밭에 물을 줬다. 토양이 많이 말라 있어 평소보다 오래 관수했다.', 'MANUAL', false, '2026-04-08 16:10:00', '2026-04-08 16:10:00'),
  ('7d61cd7d-c7bd-4c4d-909e-03d566c89db1', '7f6ec560-b894-4aff-8b47-2033d18f8658', 'abda29c3-e273-4ea1-a410-3f74637d785c', (SELECT id FROM crop WHERE name = '감자'), 'FERTILIZING', '2026-04-13 14:40:00', '흐림', 12, '감자 밭에 유기질 비료를 시비했다.', 'MANUAL', false, '2026-04-13 15:40:00', '2026-04-13 15:40:00'),
  ('ee50b2fc-1b68-486b-98bf-678ebfb7d900', '7f6ec560-b894-4aff-8b47-2033d18f8658', 'abda29c3-e273-4ea1-a410-3f74637d785c', (SELECT id FROM crop WHERE name = '가지'), 'PRUNING', '2026-04-19 09:00:00', '황사', 18, '가지 곁순과 웃자란 가지를 정리했다.', 'MANUAL', false, '2026-04-19 12:00:00', '2026-04-19 12:00:00'),
  ('b1c7f3fd-9377-437f-97f1-391f449fdfc3', '7f6ec560-b894-4aff-8b47-2033d18f8658', 'abda29c3-e273-4ea1-a410-3f74637d785c', (SELECT id FROM crop WHERE name = '가락지나물'), 'WATERING', '2026-04-24 17:10:00', '흐림', 13, '가락지나물 잎이 처져 있어 아침 일찍 물을 줬다.', 'MANUAL', false, '2026-04-24 18:10:00', '2026-04-24 18:10:00'),
  ('6dad5fcf-32eb-4f03-a785-879d1c285a76', '7f6ec560-b894-4aff-8b47-2033d18f8658', 'abda29c3-e273-4ea1-a410-3f74637d785c', (SELECT id FROM crop WHERE name = '감자'), 'PEST_CONTROL', '2026-04-30 16:30:00', '흐림', 15, '감자에서 병해충이 발견되어 방제 작업을 진행했다.', 'MANUAL', false, '2026-04-30 19:30:00', '2026-04-30 19:30:00'),
  ('9385f2bb-216b-401a-a8e8-5ac7fab28e66', '7f6ec560-b894-4aff-8b47-2033d18f8658', 'abda29c3-e273-4ea1-a410-3f74637d785c', (SELECT id FROM crop WHERE name = '가지'), 'WEEDING', '2026-05-05 09:20:00', '맑음', 18, '가지 두둑 주변 김매기를 했다.', 'MANUAL', false, '2026-05-05 12:20:00', '2026-05-05 12:20:00'),
  ('63bf18d0-527b-45dc-888c-3f0b382bb55d', '7f6ec560-b894-4aff-8b47-2033d18f8658', 'abda29c3-e273-4ea1-a410-3f74637d785c', (SELECT id FROM crop WHERE name = '가락지나물'), 'WATERING', '2026-05-11 08:10:00', '맑음', 19, '가락지나물 잎이 처져 있어 아침 일찍 물을 줬다.', 'MANUAL', false, '2026-05-11 11:10:00', '2026-05-11 11:10:00'),
  ('5da0b42e-eae8-4917-9a32-10f87f246741', '7f6ec560-b894-4aff-8b47-2033d18f8658', 'abda29c3-e273-4ea1-a410-3f74637d785c', (SELECT id FROM crop WHERE name = '감자'), 'FERTILIZING', '2026-05-16 17:20:00', '흐림', 21, '감자 밭에 유기질 비료를 시비했다.', 'MANUAL', false, '2026-05-16 18:20:00', '2026-05-16 18:20:00'),
  ('18d52665-fd32-4d61-91da-4abad095c743', '7f6ec560-b894-4aff-8b47-2033d18f8658', 'abda29c3-e273-4ea1-a410-3f74637d785c', (SELECT id FROM crop WHERE name = '가지'), 'PEST_CONTROL', '2026-05-22 07:00:00', '비', 18, '예방 차원에서 가지에 방제 작업을 실시했다.', 'MANUAL', false, '2026-05-22 09:00:00', '2026-05-22 09:00:00'),
  ('7779f826-d4a2-439f-95f9-6e0165086cc2', '7f6ec560-b894-4aff-8b47-2033d18f8658', 'abda29c3-e273-4ea1-a410-3f74637d785c', (SELECT id FROM crop WHERE name = '가락지나물'), 'PRUNING', '2026-05-27 14:00:00', '비', 17, '가락지나물 곁순과 웃자란 가지를 정리했다.', 'MANUAL', false, '2026-05-27 17:00:00', '2026-05-27 17:00:00'),
  ('14597e31-771f-4e13-8db9-0d8133850ccd', '7f6ec560-b894-4aff-8b47-2033d18f8658', 'abda29c3-e273-4ea1-a410-3f74637d785c', (SELECT id FROM crop WHERE name = '감자'), 'WATERING', '2026-06-02 09:20:00', '맑음', 22, '감자 밭에 물을 줬다. 토양이 많이 말라 있어 평소보다 오래 관수했다.', 'MANUAL', false, '2026-06-02 10:20:00', '2026-06-02 10:20:00'),
  ('2a89c7a9-d16c-4975-9aac-b49bf6d708fd', '7f6ec560-b894-4aff-8b47-2033d18f8658', 'abda29c3-e273-4ea1-a410-3f74637d785c', (SELECT id FROM crop WHERE name = '가지'), 'WEEDING', '2026-06-07 15:20:00', '맑음', 26, '가지 밭 사이 잡초를 제거했다. 장마철 전이라 그런지 잡초가 빨리 자랐다.', 'MANUAL', false, '2026-06-07 17:20:00', '2026-06-07 17:20:00'),
  ('d8a6afcb-7629-434f-95cb-07a707b3bbc9', '7f6ec560-b894-4aff-8b47-2033d18f8658', 'abda29c3-e273-4ea1-a410-3f74637d785c', (SELECT id FROM crop WHERE name = '가락지나물'), 'ETC', '2026-06-13 17:00:00', '장마', 22, '가락지나물 밭 주변 배수로를 정비하고 시설물을 점검했다.', 'MANUAL', false, '2026-06-13 20:00:00', '2026-06-13 20:00:00'),
  ('cbb44ca3-88df-40da-8a60-a8dc66e2e4d2', '7f6ec560-b894-4aff-8b47-2033d18f8658', 'abda29c3-e273-4ea1-a410-3f74637d785c', (SELECT id FROM crop WHERE name = '감자'), 'FERTILIZING', '2026-06-18 16:30:00', '장마', 22, '감자 밭에 유기질 비료를 시비했다.', 'MANUAL', false, '2026-06-18 19:30:00', '2026-06-18 19:30:00'),
  ('5e3a410f-e50f-40c1-9f95-69bee64d337e', '7f6ec560-b894-4aff-8b47-2033d18f8658', 'abda29c3-e273-4ea1-a410-3f74637d785c', (SELECT id FROM crop WHERE name = '가지'), 'PEST_CONTROL', '2026-06-24 15:00:00', '장마', 24, '예방 차원에서 가지에 방제 작업을 실시했다.', 'MANUAL', false, '2026-06-24 17:00:00', '2026-06-24 17:00:00'),
  ('eee6ee6e-b000-44fb-bc58-e0984c46aef8', '7f6ec560-b894-4aff-8b47-2033d18f8658', 'abda29c3-e273-4ea1-a410-3f74637d785c', (SELECT id FROM crop WHERE name = '가락지나물'), 'HARVEST', '2026-06-29 14:00:00', '흐림', 22, '가락지나물 수확을 진행했다. 올해는 작황이 나쁘지 않은 편이다.', 'MANUAL', false, '2026-06-29 15:00:00', '2026-06-29 15:00:00'),
  ('50853615-ac82-4c19-aba0-474f58d86a55', '7f6ec560-b894-4aff-8b47-2033d18f8658', 'abda29c3-e273-4ea1-a410-3f74637d785c', (SELECT id FROM crop WHERE name = '감자'), 'HARVEST', '2026-07-05 15:20:00', '흐림', 27, '감자 일부를 수확해 상태를 확인했다.', 'MANUAL', false, '2026-07-05 17:20:00', '2026-07-05 17:20:00'),
  ('ef96e764-063c-45dc-b14f-bad3a344326b', '7f6ec560-b894-4aff-8b47-2033d18f8658', 'abda29c3-e273-4ea1-a410-3f74637d785c', (SELECT id FROM crop WHERE name = '가지'), 'HARVEST', '2026-07-10 17:40:00', '맑음', 26, '가지 수확을 진행했다. 올해는 작황이 나쁘지 않은 편이다.', 'MANUAL', false, '2026-07-10 19:40:00', '2026-07-10 19:40:00'),
  ('69d90e30-baee-4f12-9acc-a85c7296611c', 'ad55875b-84f9-4f1d-be1b-67eec3884575', 'b61d6d34-5b24-4573-8fee-90c7ddb09d29', (SELECT id FROM crop WHERE name = '강활'), 'PLANTING', '2026-02-04 08:30:00', '흐림', 1, '강활 씨앗을 파종했다. 발아율을 높이기 위해 두둑을 미리 정리해두었다.', 'MANUAL', false, '2026-02-04 10:30:00', '2026-02-04 10:30:00'),
  ('c4af1d6c-cc48-4b25-9d85-47138ee2dbea', 'ad55875b-84f9-4f1d-be1b-67eec3884575', 'b61d6d34-5b24-4573-8fee-90c7ddb09d29', (SELECT id FROM crop WHERE name = '개똥쑥'), 'WATERING', '2026-02-09 14:30:00', '맑음', 6, '가뭄이 이어져 개똥쑥에 스프링클러로 관수했다.', 'MANUAL', false, '2026-02-09 17:30:00', '2026-02-09 17:30:00'),
  ('cc9eccc0-00dd-4738-ba6f-85a009f8dc98', 'ad55875b-84f9-4f1d-be1b-67eec3884575', 'b61d6d34-5b24-4573-8fee-90c7ddb09d29', (SELECT id FROM crop WHERE name = '감초'), 'FERTILIZING', '2026-02-15 08:30:00', '흐림', 4, '감초에 웃거름을 줬다. 생육 상태를 보니 질소가 부족해 보였다.', 'MANUAL', false, '2026-02-15 09:30:00', '2026-02-15 09:30:00'),
  ('6f89f741-54b5-4569-91e8-f61a8343de47', 'ad55875b-84f9-4f1d-be1b-67eec3884575', 'b61d6d34-5b24-4573-8fee-90c7ddb09d29', (SELECT id FROM crop WHERE name = '강활'), 'WATERING', '2026-02-20 07:10:00', '맑음', 3, '가뭄이 이어져 강활에 스프링클러로 관수했다.', 'MANUAL', false, '2026-02-20 09:10:00', '2026-02-20 09:10:00'),
  ('376c329f-c67d-44ef-a391-ba21a1361106', 'ad55875b-84f9-4f1d-be1b-67eec3884575', 'b61d6d34-5b24-4573-8fee-90c7ddb09d29', (SELECT id FROM crop WHERE name = '개똥쑥'), 'PLANTING', '2026-02-26 14:00:00', '맑음', 6, '개똥쑥 모종을 정식했다. 뿌리 활착이 잘 되도록 물을 충분히 줬다.', 'MANUAL', false, '2026-02-26 16:00:00', '2026-02-26 16:00:00'),
  ('830ac87b-fd2d-4b5b-85b6-58022101c30c', 'ad55875b-84f9-4f1d-be1b-67eec3884575', 'b61d6d34-5b24-4573-8fee-90c7ddb09d29', (SELECT id FROM crop WHERE name = '감초'), 'WEEDING', '2026-03-03 14:20:00', '흐림', 7, '감초 밭 사이 잡초를 제거했다. 장마철 전이라 그런지 잡초가 빨리 자랐다.', 'MANUAL', false, '2026-03-03 15:20:00', '2026-03-03 15:20:00'),
  ('d29d7c91-7743-4d0e-946c-c13aa478c339', 'ad55875b-84f9-4f1d-be1b-67eec3884575', 'b61d6d34-5b24-4573-8fee-90c7ddb09d29', (SELECT id FROM crop WHERE name = '강활'), 'WATERING', '2026-03-08 17:30:00', '흐림', 11, '강활 밭에 물을 줬다. 토양이 많이 말라 있어 평소보다 오래 관수했다.', 'MANUAL', false, '2026-03-08 19:30:00', '2026-03-08 19:30:00'),
  ('772e4b59-3c13-4db8-8519-d0c8340f0123', 'ad55875b-84f9-4f1d-be1b-67eec3884575', 'b61d6d34-5b24-4573-8fee-90c7ddb09d29', (SELECT id FROM crop WHERE name = '개똥쑥'), 'PEST_CONTROL', '2026-03-14 09:30:00', '비', 8, '예방 차원에서 개똥쑥에 방제 작업을 실시했다.', 'MANUAL', false, '2026-03-14 10:30:00', '2026-03-14 10:30:00'),
  ('d49e52a9-72cb-4a36-8545-22a56a97263c', 'ad55875b-84f9-4f1d-be1b-67eec3884575', 'b61d6d34-5b24-4573-8fee-90c7ddb09d29', (SELECT id FROM crop WHERE name = '감초'), 'FERTILIZING', '2026-03-19 16:40:00', '맑음', 10, '감초에 웃거름을 줬다. 생육 상태를 보니 질소가 부족해 보였다.', 'MANUAL', false, '2026-03-19 17:40:00', '2026-03-19 17:40:00'),
  ('8a60a18e-5bdc-466f-b73b-29b994257417', 'ad55875b-84f9-4f1d-be1b-67eec3884575', 'b61d6d34-5b24-4573-8fee-90c7ddb09d29', (SELECT id FROM crop WHERE name = '강활'), 'WATERING', '2026-03-24 16:30:00', '흐림', 11, '강활 밭에 물을 줬다. 토양이 많이 말라 있어 평소보다 오래 관수했다.', 'MANUAL', false, '2026-03-24 19:30:00', '2026-03-24 19:30:00'),
  ('bd1caea3-7c25-49c9-9c79-c6ee3f513824', 'ad55875b-84f9-4f1d-be1b-67eec3884575', 'b61d6d34-5b24-4573-8fee-90c7ddb09d29', (SELECT id FROM crop WHERE name = '개똥쑥'), 'WEEDING', '2026-03-30 08:10:00', '맑음', 12, '개똥쑥 밭 사이 잡초를 제거했다. 장마철 전이라 그런지 잡초가 빨리 자랐다.', 'MANUAL', false, '2026-03-30 11:10:00', '2026-03-30 11:10:00'),
  ('a882c5a8-15ef-4f7e-8b08-3989b244ff60', 'ad55875b-84f9-4f1d-be1b-67eec3884575', 'b61d6d34-5b24-4573-8fee-90c7ddb09d29', (SELECT id FROM crop WHERE name = '감초'), 'PEST_CONTROL', '2026-04-04 09:10:00', '황사', 15, '예방 차원에서 감초에 방제 작업을 실시했다.', 'MANUAL', false, '2026-04-04 12:10:00', '2026-04-04 12:10:00'),
  ('76709720-b0b5-4db6-bb6a-106881214694', 'ad55875b-84f9-4f1d-be1b-67eec3884575', 'b61d6d34-5b24-4573-8fee-90c7ddb09d29', (SELECT id FROM crop WHERE name = '강활'), 'WATERING', '2026-04-10 07:40:00', '흐림', 13, '강활 잎이 처져 있어 아침 일찍 물을 줬다.', 'MANUAL', false, '2026-04-10 08:40:00', '2026-04-10 08:40:00'),
  ('9b09e6c9-a375-470a-a8bf-a0022a8c5c56', 'ad55875b-84f9-4f1d-be1b-67eec3884575', 'b61d6d34-5b24-4573-8fee-90c7ddb09d29', (SELECT id FROM crop WHERE name = '개똥쑥'), 'FERTILIZING', '2026-04-15 16:40:00', '흐림', 16, '개똥쑥 밭에 유기질 비료를 시비했다.', 'MANUAL', false, '2026-04-15 17:40:00', '2026-04-15 17:40:00'),
  ('0e0e9f31-62fb-4014-9057-c24d60da62ab', 'ad55875b-84f9-4f1d-be1b-67eec3884575', 'b61d6d34-5b24-4573-8fee-90c7ddb09d29', (SELECT id FROM crop WHERE name = '감초'), 'PRUNING', '2026-04-20 07:20:00', '흐림', 12, '감초 곁순과 웃자란 가지를 정리했다.', 'MANUAL', false, '2026-04-20 08:20:00', '2026-04-20 08:20:00'),
  ('367b748a-930f-439f-a72b-215ff15f7684', 'ad55875b-84f9-4f1d-be1b-67eec3884575', 'b61d6d34-5b24-4573-8fee-90c7ddb09d29', (SELECT id FROM crop WHERE name = '강활'), 'WATERING', '2026-04-26 07:00:00', '흐림', 15, '강활 잎이 처져 있어 아침 일찍 물을 줬다.', 'MANUAL', false, '2026-04-26 10:00:00', '2026-04-26 10:00:00'),
  ('1eede72c-c07e-4520-9813-00884020364a', 'ad55875b-84f9-4f1d-be1b-67eec3884575', 'b61d6d34-5b24-4573-8fee-90c7ddb09d29', (SELECT id FROM crop WHERE name = '개똥쑥'), 'PEST_CONTROL', '2026-05-01 15:30:00', '맑음', 18, '예방 차원에서 개똥쑥에 방제 작업을 실시했다.', 'MANUAL', false, '2026-05-01 17:30:00', '2026-05-01 17:30:00'),
  ('ccf13d1a-02d3-40d7-9b85-afd47e714aff', 'ad55875b-84f9-4f1d-be1b-67eec3884575', 'b61d6d34-5b24-4573-8fee-90c7ddb09d29', (SELECT id FROM crop WHERE name = '감초'), 'WEEDING', '2026-05-06 15:40:00', '흐림', 18, '감초 밭 사이 잡초를 제거했다. 장마철 전이라 그런지 잡초가 빨리 자랐다.', 'MANUAL', false, '2026-05-06 17:40:00', '2026-05-06 17:40:00'),
  ('9713a777-8ce0-41ec-b28e-a0d04ac21469', 'ad55875b-84f9-4f1d-be1b-67eec3884575', 'b61d6d34-5b24-4573-8fee-90c7ddb09d29', (SELECT id FROM crop WHERE name = '강활'), 'WATERING', '2026-05-12 08:00:00', '맑음', 21, '가뭄이 이어져 강활에 스프링클러로 관수했다.', 'MANUAL', false, '2026-05-12 11:00:00', '2026-05-12 11:00:00'),
  ('147d8149-362d-4e27-a30e-c76a0ca0b7b9', 'ad55875b-84f9-4f1d-be1b-67eec3884575', 'b61d6d34-5b24-4573-8fee-90c7ddb09d29', (SELECT id FROM crop WHERE name = '개똥쑥'), 'FERTILIZING', '2026-05-17 15:00:00', '흐림', 21, '개똥쑥 밭에 유기질 비료를 시비했다.', 'MANUAL', false, '2026-05-17 17:00:00', '2026-05-17 17:00:00'),
  ('4523bb90-61b1-47ae-81fb-794fe38faf29', 'ad55875b-84f9-4f1d-be1b-67eec3884575', 'b61d6d34-5b24-4573-8fee-90c7ddb09d29', (SELECT id FROM crop WHERE name = '감초'), 'PEST_CONTROL', '2026-05-23 17:10:00', '비', 18, '감초에서 병해충이 발견되어 방제 작업을 진행했다.', 'MANUAL', false, '2026-05-23 18:10:00', '2026-05-23 18:10:00'),
  ('0a1bda72-3ba5-44db-a724-90df9d2447bc', 'ad55875b-84f9-4f1d-be1b-67eec3884575', 'b61d6d34-5b24-4573-8fee-90c7ddb09d29', (SELECT id FROM crop WHERE name = '강활'), 'PRUNING', '2026-05-28 07:40:00', '비', 20, '강활 곁순과 웃자란 가지를 정리했다.', 'MANUAL', false, '2026-05-28 09:40:00', '2026-05-28 09:40:00'),
  ('e0fdc4d9-66f4-4752-ad66-9956833a0766', 'ad55875b-84f9-4f1d-be1b-67eec3884575', 'b61d6d34-5b24-4573-8fee-90c7ddb09d29', (SELECT id FROM crop WHERE name = '개똥쑥'), 'WATERING', '2026-06-02 07:10:00', '흐림', 23, '개똥쑥 밭에 물을 줬다. 토양이 많이 말라 있어 평소보다 오래 관수했다.', 'MANUAL', false, '2026-06-02 09:10:00', '2026-06-02 09:10:00'),
  ('cef4b7e5-4ab8-4356-837f-5fd2a06f2f8d', 'ad55875b-84f9-4f1d-be1b-67eec3884575', 'b61d6d34-5b24-4573-8fee-90c7ddb09d29', (SELECT id FROM crop WHERE name = '감초'), 'WEEDING', '2026-06-08 14:00:00', '흐림', 23, '감초 두둑 주변 김매기를 했다.', 'MANUAL', false, '2026-06-08 15:00:00', '2026-06-08 15:00:00'),
  ('110cf41d-c3a2-4dc4-8f8e-49c7fa55519e', 'ad55875b-84f9-4f1d-be1b-67eec3884575', 'b61d6d34-5b24-4573-8fee-90c7ddb09d29', (SELECT id FROM crop WHERE name = '강활'), 'ETC', '2026-06-13 15:10:00', '비', 24, '강활 밭 주변 배수로를 정비하고 시설물을 점검했다.', 'MANUAL', false, '2026-06-13 18:10:00', '2026-06-13 18:10:00'),
  ('f6c79ef3-919a-4b60-87b7-1d2db3cfd377', 'ad55875b-84f9-4f1d-be1b-67eec3884575', 'b61d6d34-5b24-4573-8fee-90c7ddb09d29', (SELECT id FROM crop WHERE name = '개똥쑥'), 'FERTILIZING', '2026-06-18 15:40:00', '맑음', 24, '개똥쑥 밭에 유기질 비료를 시비했다.', 'MANUAL', false, '2026-06-18 18:40:00', '2026-06-18 18:40:00'),
  ('b73cec1d-720d-4f1d-b32a-d16dfb98f6fd', 'ad55875b-84f9-4f1d-be1b-67eec3884575', 'b61d6d34-5b24-4573-8fee-90c7ddb09d29', (SELECT id FROM crop WHERE name = '감초'), 'PEST_CONTROL', '2026-06-24 07:40:00', '흐림', 24, '예방 차원에서 감초에 방제 작업을 실시했다.', 'MANUAL', false, '2026-06-24 08:40:00', '2026-06-24 08:40:00'),
  ('6d0260a7-7d9c-41a7-9716-9df89519bfd2', 'ad55875b-84f9-4f1d-be1b-67eec3884575', 'b61d6d34-5b24-4573-8fee-90c7ddb09d29', (SELECT id FROM crop WHERE name = '강활'), 'HARVEST', '2026-06-29 07:30:00', '비', 21, '강활 일부를 수확해 상태를 확인했다.', 'MANUAL', false, '2026-06-29 09:30:00', '2026-06-29 09:30:00'),
  ('12d9a6a5-c0bd-4c36-88ff-5c653e8ecd84', 'ad55875b-84f9-4f1d-be1b-67eec3884575', 'b61d6d34-5b24-4573-8fee-90c7ddb09d29', (SELECT id FROM crop WHERE name = '개똥쑥'), 'HARVEST', '2026-07-05 16:40:00', '비', 27, '개똥쑥 일부를 수확해 상태를 확인했다.', 'MANUAL', false, '2026-07-05 19:40:00', '2026-07-05 19:40:00'),
  ('c3321ee8-20c4-4ddf-9354-7097dfee7fdd', 'ad55875b-84f9-4f1d-be1b-67eec3884575', 'b61d6d34-5b24-4573-8fee-90c7ddb09d29', (SELECT id FROM crop WHERE name = '감초'), 'HARVEST', '2026-07-10 15:40:00', '장마', 24, '감초 수확을 진행했다. 올해는 작황이 나쁘지 않은 편이다.', 'MANUAL', false, '2026-07-10 18:40:00', '2026-07-10 18:40:00')
ON CONFLICT (id) DO NOTHING;

-- 4-1) planting_record
INSERT INTO planting_record (id, record_id, planting_method, seed_amount, seed_amount_unit, seedling_count, seedling_unit, propagation_method, created_at, updated_at)
VALUES
  ('3ddcaf06-f069-498b-a340-00db161615bc', '38e1a599-68f5-428f-a724-274dfa8034be', 'SEED', 81.85, 'G', NULL, NULL, 'CUTTING', '2026-02-01 18:00:00', '2026-02-01 18:00:00'),
  ('280f9918-60ad-4439-96ae-103e62bb5aad', 'f7163818-2068-4e54-8483-67b1ddf2f4a1', 'SEED', 87.43, 'G', NULL, NULL, 'DIVISION', '2026-02-23 17:40:00', '2026-02-23 17:40:00'),
  ('e7817ccc-965b-430d-947f-c01e1c73fe01', '69d90e30-baee-4f12-9acc-a85c7296611c', 'SEED', 51.66, 'G', NULL, NULL, 'DIVISION', '2026-02-04 10:30:00', '2026-02-04 10:30:00'),
  ('d1bd4e16-466d-4b8f-8a44-af6078e541e0', '376c329f-c67d-44ef-a391-ba21a1361106', 'SEEDLING', NULL, NULL, 80, 'JU', NULL, '2026-02-26 16:00:00', '2026-02-26 16:00:00')
ON CONFLICT (id) DO NOTHING;

-- 4-2) watering_record
INSERT INTO watering_record (id, record_id, irrigation_amount, irrigation_method, created_at, updated_at)
VALUES
  ('576c4be9-e560-419f-b2f3-be7fa2979c3e', '2ca2b77d-1da4-40fe-a65d-431b0eebbd62', 'NORMAL', 'SPRAYING', '2026-02-06 08:20:00', '2026-02-06 08:20:00'),
  ('69dfcb5d-8092-45d9-a188-b54a24103412', '54941f39-f057-4bbb-8468-d4cc3a840655', 'SUFFICIENT', 'DRIP', '2026-02-17 11:00:00', '2026-02-17 11:00:00'),
  ('16f33fb9-4af6-4f39-a158-379587c216ee', '04d9316a-36f0-445d-828e-394c87d2c7c7', 'SUFFICIENT', 'SPRAYING', '2026-03-06 16:00:00', '2026-03-06 16:00:00'),
  ('8b93c82a-5dd3-43ee-b290-4b4259297385', '79c2d0b0-fb8d-4717-ae8c-455d80a837ea', 'LOW', 'ETC', '2026-03-22 17:40:00', '2026-03-22 17:40:00'),
  ('fd78e214-76a8-4d2f-a84e-8941c54c93ee', 'a6032fcf-c93f-4c0a-b1a8-494d3d5e6796', 'NORMAL', 'SPRAYING', '2026-04-08 16:10:00', '2026-04-08 16:10:00'),
  ('85311d37-948a-4220-951b-6aee08f744ca', 'b1c7f3fd-9377-437f-97f1-391f449fdfc3', 'SUFFICIENT', 'DRIP', '2026-04-24 18:10:00', '2026-04-24 18:10:00'),
  ('765cd045-12a8-4c3d-8102-91455c2188c7', '63bf18d0-527b-45dc-888c-3f0b382bb55d', 'NORMAL', 'ETC', '2026-05-11 11:10:00', '2026-05-11 11:10:00'),
  ('b537ff44-a6b3-4cd0-ae8e-30203b3c6732', '14597e31-771f-4e13-8db9-0d8133850ccd', 'LOW', 'DRIP', '2026-06-02 10:20:00', '2026-06-02 10:20:00'),
  ('40e262ad-dc13-421b-bee0-2f669c60becd', 'c4af1d6c-cc48-4b25-9d85-47138ee2dbea', 'NORMAL', 'DRIP', '2026-02-09 17:30:00', '2026-02-09 17:30:00'),
  ('f8e018a5-8af2-4eee-b428-19e7c5be3464', '6f89f741-54b5-4569-91e8-f61a8343de47', 'NORMAL', 'DRIP', '2026-02-20 09:10:00', '2026-02-20 09:10:00'),
  ('8c550fa5-f179-4947-9eb4-9b7909e1fab8', 'd29d7c91-7743-4d0e-946c-c13aa478c339', 'SUFFICIENT', 'SPRAYING', '2026-03-08 19:30:00', '2026-03-08 19:30:00'),
  ('ac5b291a-48e9-4ade-9bf4-4f5a476e2112', '8a60a18e-5bdc-466f-b73b-29b994257417', 'LOW', 'DRIP', '2026-03-24 19:30:00', '2026-03-24 19:30:00'),
  ('a6803e3e-1e5a-419e-8edd-bc30aab31ebd', '76709720-b0b5-4db6-bb6a-106881214694', 'LOW', 'ETC', '2026-04-10 08:40:00', '2026-04-10 08:40:00'),
  ('db7954f9-33a6-41e7-af13-41bb463dd9eb', '367b748a-930f-439f-a72b-215ff15f7684', 'SUFFICIENT', 'DRIP', '2026-04-26 10:00:00', '2026-04-26 10:00:00'),
  ('1214c6e2-5101-4bc4-9f3c-03c62c9d5de6', '9713a777-8ce0-41ec-b28e-a0d04ac21469', 'NORMAL', 'DRIP', '2026-05-12 11:00:00', '2026-05-12 11:00:00'),
  ('83d6d664-3e0b-44e1-93d9-5b70143e7ba7', 'e0fdc4d9-66f4-4752-ad66-9956833a0766', 'SUFFICIENT', 'SPRAYING', '2026-06-02 09:10:00', '2026-06-02 09:10:00')
ON CONFLICT (id) DO NOTHING;

-- 4-3) fertilizing_record
INSERT INTO fertilizing_record (id, record_id, material_name, amount, amount_unit, application_method, created_at, updated_at)
VALUES
  ('458da058-54b0-4d31-b58d-c25ecad7699b', '87039e65-5454-4de7-bd65-2a632f765fcc', '유기질 복합비료', 1023.77, 'ML', 'FOLIAR', '2026-02-12 11:10:00', '2026-02-12 11:10:00'),
  ('0269d322-13ac-4669-ace6-8eac3ccec301', 'ef7ab756-32d0-4435-a5a5-9f08e1dbb5c5', '액체 비료', 1404.99, 'ML', 'SOIL', '2026-03-17 11:10:00', '2026-03-17 11:10:00'),
  ('661ceec5-9b9a-4b5c-bcf6-2e4580ae4423', '7d61cd7d-c7bd-4c4d-909e-03d566c89db1', '황산칼리', 1769.2, 'G', 'SOIL', '2026-04-13 15:40:00', '2026-04-13 15:40:00'),
  ('44e95c8f-bd62-4c50-90f1-95b216fa9993', '5da0b42e-eae8-4917-9a32-10f87f246741', '황산칼리', 2822.28, 'G', 'SOIL', '2026-05-16 18:20:00', '2026-05-16 18:20:00'),
  ('4266170e-7e19-4df0-b4e3-9c428e360793', 'cbb44ca3-88df-40da-8a60-a8dc66e2e4d2', '부숙 퇴비', 976.02, 'ML', 'SOIL', '2026-06-18 19:30:00', '2026-06-18 19:30:00'),
  ('03cc5fd6-9719-4ab0-9896-09587602f5e1', 'cc9eccc0-00dd-4738-ba6f-85a009f8dc98', '요소비료', 1844.09, 'G', 'FOLIAR', '2026-02-15 09:30:00', '2026-02-15 09:30:00'),
  ('bf04ef79-71ef-42af-96ce-3c8ac854a237', 'd49e52a9-72cb-4a36-8545-22a56a97263c', '액체 비료', 903.95, 'ML', 'FOLIAR', '2026-03-19 17:40:00', '2026-03-19 17:40:00'),
  ('193b4cef-b5d6-44f7-8ee4-42e3dacecada', '9b09e6c9-a375-470a-a8bf-a0022a8c5c56', '요소비료', 2814.41, 'G', 'SOIL', '2026-04-15 17:40:00', '2026-04-15 17:40:00'),
  ('bc2dd03a-b8aa-42cb-96be-6166876d9eb3', '147d8149-362d-4e27-a30e-c76a0ca0b7b9', '황산칼리', 2848.09, 'ML', 'SOIL', '2026-05-17 17:00:00', '2026-05-17 17:00:00'),
  ('fbc3fd42-f94b-4f30-85da-fcb126054ec4', 'f6c79ef3-919a-4b60-87b7-1d2db3cfd377', '액체 비료', 1315.46, 'ML', 'FOLIAR', '2026-06-18 18:40:00', '2026-06-18 18:40:00')
ON CONFLICT (id) DO NOTHING;

-- 4-4) pest_control_record
INSERT INTO pest_control_record (id, record_id, pesticide_id, pest_id, pesticide_amount, pesticide_amount_unit, total_spray_amount, total_spray_amount_unit, created_at, updated_at)
VALUES
  ('269d9946-e39e-45ec-aca9-8d9609e9fce1', '6d0e6f12-6483-4f88-9670-cb7f9555c0a4', 'a21e212c-da40-4cbc-9d0f-74afd1d5b8ba', 'fbd890cf-8697-40ee-9dc9-7c5a7f29fd12', 84.91, 'G', 3979.96, 'ML', '2026-03-11 08:00:00', '2026-03-11 08:00:00'),
  ('25ccdcb9-c36f-4f02-8ad3-c4c2a09f5cd8', '69d889ae-8c07-472a-a359-e301040fc471', '2be90716-4951-450c-a4e0-e1b416a27a4b', NULL, 220.43, 'ML', 3147.88, 'ML', '2026-04-02 10:40:00', '2026-04-02 10:40:00'),
  ('4683cd59-98f9-420f-a8d3-9c88baa321b0', '6dad5fcf-32eb-4f03-a785-879d1c285a76', '2be90716-4951-450c-a4e0-e1b416a27a4b', '55ee86d3-d77c-4427-a3c0-94034b1c0687', 109.86, 'G', 1963.49, 'ML', '2026-04-30 19:30:00', '2026-04-30 19:30:00'),
  ('ef083f5e-0e2e-4f21-b6b6-dd3c8eee665f', '18d52665-fd32-4d61-91da-4abad095c743', 'a21e212c-da40-4cbc-9d0f-74afd1d5b8ba', 'fbd890cf-8697-40ee-9dc9-7c5a7f29fd12', 106.17, 'ML', 2352.34, 'ML', '2026-05-22 09:00:00', '2026-05-22 09:00:00'),
  ('e8752092-70d3-4139-bbe3-0de6eb4048d3', '5e3a410f-e50f-40c1-9f95-69bee64d337e', '2be90716-4951-450c-a4e0-e1b416a27a4b', NULL, 65.15, 'ML', 3912.85, 'ML', '2026-06-24 17:00:00', '2026-06-24 17:00:00'),
  ('996d2d12-4587-4cf2-b96a-35368e541ecc', '772e4b59-3c13-4db8-8519-d0c8340f0123', 'a21e212c-da40-4cbc-9d0f-74afd1d5b8ba', 'fbd890cf-8697-40ee-9dc9-7c5a7f29fd12', 208.57, 'ML', 4317.39, 'ML', '2026-03-14 10:30:00', '2026-03-14 10:30:00'),
  ('72e9c3fa-6198-45dd-983c-0b5ebbac902d', 'a882c5a8-15ef-4f7e-8b08-3989b244ff60', '2be90716-4951-450c-a4e0-e1b416a27a4b', 'fbd890cf-8697-40ee-9dc9-7c5a7f29fd12', 141.16, 'G', 4732.35, 'ML', '2026-04-04 12:10:00', '2026-04-04 12:10:00'),
  ('e493b9fa-b3c7-42b3-a2ab-f48109a251e6', '1eede72c-c07e-4520-9813-00884020364a', '2be90716-4951-450c-a4e0-e1b416a27a4b', '55ee86d3-d77c-4427-a3c0-94034b1c0687', 99.89, 'ML', 4673.81, 'ML', '2026-05-01 17:30:00', '2026-05-01 17:30:00'),
  ('d3968c5c-4480-4273-a473-0f89873cf175', '4523bb90-61b1-47ae-81fb-794fe38faf29', '2be90716-4951-450c-a4e0-e1b416a27a4b', NULL, 136.42, 'G', 4145.71, 'ML', '2026-05-23 18:10:00', '2026-05-23 18:10:00'),
  ('a19e4f07-68a2-4c88-91aa-63317902a909', 'b73cec1d-720d-4f1d-b32a-d16dfb98f6fd', '2be90716-4951-450c-a4e0-e1b416a27a4b', NULL, 151.52, 'G', 4334.9, 'ML', '2026-06-24 08:40:00', '2026-06-24 08:40:00')
ON CONFLICT (id) DO NOTHING;

-- 4-5) weeding_record
INSERT INTO weeding_record (id, record_id, weeding_method, created_at, updated_at)
VALUES
  ('e6edc90e-2959-48f4-a45d-be33e0566131', '45883659-7a56-4f6c-9b20-3840f4084c9c', 'HERBICIDE', '2026-02-28 08:40:00', '2026-02-28 08:40:00'),
  ('16711077-33a6-4732-bd82-ebca9d1e0c26', '38dafd71-0178-45c9-a323-abba78e69ee2', 'HERBICIDE', '2026-03-28 08:40:00', '2026-03-28 08:40:00'),
  ('101b81e1-61c9-405c-9db7-4bc46b3237b8', '9385f2bb-216b-401a-a8e8-5ac7fab28e66', 'HAND', '2026-05-05 12:20:00', '2026-05-05 12:20:00'),
  ('a8f0d415-7d1f-4de5-8c8e-2ed11230eab8', '2a89c7a9-d16c-4975-9aac-b49bf6d708fd', 'MULCHING', '2026-06-07 17:20:00', '2026-06-07 17:20:00'),
  ('4df2c09c-d108-4f78-bd0e-0cb3470b5837', '830ac87b-fd2d-4b5b-85b6-58022101c30c', 'MACHINE', '2026-03-03 15:20:00', '2026-03-03 15:20:00'),
  ('09846bdd-91e1-4c4d-a074-66ae13b85537', 'bd1caea3-7c25-49c9-9c79-c6ee3f513824', 'MULCHING', '2026-03-30 11:10:00', '2026-03-30 11:10:00'),
  ('af007e7f-3490-4f0a-bf32-a43ec4cd4fff', 'ccf13d1a-02d3-40d7-9b85-afd47e714aff', 'HAND', '2026-05-06 17:40:00', '2026-05-06 17:40:00'),
  ('d33fff8e-7145-4ec5-b61e-27fa9c4e35ed', 'cef4b7e5-4ab8-4356-837f-5fd2a06f2f8d', 'HAND', '2026-06-08 15:00:00', '2026-06-08 15:00:00')
ON CONFLICT (id) DO NOTHING;

-- 4-6) harvest_record
INSERT INTO harvest_record (id, record_id, harvest_amount, medicinal_part, harvest_source, growth_period, is_last_harvest, created_at, updated_at)
VALUES
  ('10b9be1e-e399-4f24-9a7f-cabd1b695dc5', 'eee6ee6e-b000-44fb-bc58-e0984c46aef8', 47.54, 'WHOLE_HERB', 'CULTIVATED', 77, false, '2026-06-29 15:00:00', '2026-06-29 15:00:00'),
  ('3091c6ef-f32a-4ebb-8cef-f1df14e103a8', '50853615-ac82-4c19-aba0-474f58d86a55', 27.49, 'RHIZOME', 'CULTIVATED', 69, true, '2026-07-05 17:20:00', '2026-07-05 17:20:00'),
  ('d69846e7-01a3-4036-95ef-8695fdc50749', 'ef96e764-063c-45dc-b14f-bad3a344326b', 77.83, 'FRUIT', 'CULTIVATED', 90, true, '2026-07-10 19:40:00', '2026-07-10 19:40:00'),
  ('7cbe0f21-6ad4-45ea-877b-b18ec5d91753', '6d0260a7-7d9c-41a7-9716-9df89519bfd2', 65.0, 'ROOT_BARK', 'CULTIVATED', 137, false, '2026-06-29 09:30:00', '2026-06-29 09:30:00'),
  ('a719037e-c267-4209-90df-fb7333e8dcc4', '12d9a6a5-c0bd-4c36-88ff-5c653e8ecd84', 40.95, 'WHOLE_HERB', 'CULTIVATED', 87, true, '2026-07-05 19:40:00', '2026-07-05 19:40:00'),
  ('561a4eab-b996-4576-96b0-d0acb2b31ec1', 'c3321ee8-20c4-4ddf-9354-7097dfee7fdd', 29.87, 'ROOT_BARK', 'CULTIVATED', 144, true, '2026-07-10 18:40:00', '2026-07-10 18:40:00')
ON CONFLICT (id) DO NOTHING;

-- 5) 게시글 (community_post) 60건 (회원당 30건)
INSERT INTO community_post (id, author_member_id, crop_id, farming_record_id, post_type, title, body, is_deleted, created_at, updated_at)
VALUES
  ('979a210d-265c-4707-a0b8-a88c2163c79e', '7f6ec560-b894-4aff-8b47-2033d18f8658', (SELECT id FROM crop WHERE name = '가락지나물'), '38e1a599-68f5-428f-a724-274dfa8034be', 'GENERAL', '가락지나물 관리 팁 정리해봤어요', '이번 시즌 가락지나물 농사를 지어봤는데 날씨 영향을 많이 받는 것 같아요. 비슷한 경험 있으신 분들 이야기 들어보고 싶습니다.', false, '2026-02-01 19:00:00', '2026-02-01 19:00:00'),
  ('f0fc385a-587d-49fa-a712-08af802b3b17', '7f6ec560-b894-4aff-8b47-2033d18f8658', (SELECT id FROM crop WHERE name = '감자'), '2ca2b77d-1da4-40fe-a65d-431b0eebbd62', 'GENERAL', '올해 감자 작황 어떠신가요', '감자 밭 상태를 공유합니다. 생육이 예상보다 빠른 편인데 이 정도면 정상인지 궁금하네요.', false, '2026-02-06 11:20:00', '2026-02-06 11:20:00'),
  ('939f67dc-57e6-4813-8357-d01f1c7823e7', '7f6ec560-b894-4aff-8b47-2033d18f8658', (SELECT id FROM crop WHERE name = '가지'), '87039e65-5454-4de7-bd65-2a632f765fcc', 'GENERAL', '올해 가지 작황 어떠신가요', '가지를 키운 지 얼마 안 됐는데 생각보다 손이 많이 가네요. 물주기 주기랑 병해충 관리가 특히 신경 쓰입니다. 다들 어떻게 관리하시는지 궁금합니다.', false, '2026-02-12 11:10:00', '2026-02-12 11:10:00'),
  ('ae780c16-9d0f-4102-9b66-b8aa2cd4f979', '7f6ec560-b894-4aff-8b47-2033d18f8658', (SELECT id FROM crop WHERE name = '가락지나물'), '54941f39-f057-4bbb-8468-d4cc3a840655', 'GENERAL', '가락지나물 키우면서 겪은 시행착오 공유합니다', '가락지나물를 키운 지 얼마 안 됐는데 생각보다 손이 많이 가네요. 물주기 주기랑 병해충 관리가 특히 신경 쓰입니다. 다들 어떻게 관리하시는지 궁금합니다.', false, '2026-02-17 11:00:00', '2026-02-17 11:00:00'),
  ('8d95e7a6-cc32-4e73-a170-3e4b4b54c31d', '7f6ec560-b894-4aff-8b47-2033d18f8658', (SELECT id FROM crop WHERE name = '감자'), 'f7163818-2068-4e54-8483-67b1ddf2f4a1', 'QUESTION', '감자 웃거름 시기 질문드립니다', '장마철 앞두고 감자 병해충 방제를 미리 해두는 게 나을지 고민입니다. 조언 부탁드립니다.', false, '2026-02-23 19:40:00', '2026-02-23 19:40:00'),
  ('e98bb32c-dd7c-4324-bf8d-2ad9f3bf456e', '7f6ec560-b894-4aff-8b47-2033d18f8658', (SELECT id FROM crop WHERE name = '가지'), '45883659-7a56-4f6c-9b20-3840f4084c9c', 'GENERAL', '가지 수확 후기 남깁니다', '이번 시즌 가지 농사를 지어봤는데 날씨 영향을 많이 받는 것 같아요. 비슷한 경험 있으신 분들 이야기 들어보고 싶습니다.', false, '2026-02-28 12:40:00', '2026-02-28 12:40:00'),
  ('7476611b-5257-472b-99fc-4474e4f93d8d', '7f6ec560-b894-4aff-8b47-2033d18f8658', (SELECT id FROM crop WHERE name = '가락지나물'), '04d9316a-36f0-445d-828e-394c87d2c7c7', 'GENERAL', '가락지나물 관리 팁 정리해봤어요', '가락지나물를 키운 지 얼마 안 됐는데 생각보다 손이 많이 가네요. 물주기 주기랑 병해충 관리가 특히 신경 쓰입니다. 다들 어떻게 관리하시는지 궁금합니다.', false, '2026-03-06 19:00:00', '2026-03-06 19:00:00'),
  ('3eeb3ef7-9df7-4fc6-ad6e-e1704de89ed3', '7f6ec560-b894-4aff-8b47-2033d18f8658', (SELECT id FROM crop WHERE name = '감자'), '6d0e6f12-6483-4f88-9670-cb7f9555c0a4', 'GENERAL', '감자 수확 후기 남깁니다', '이번 시즌 감자 농사를 지어봤는데 날씨 영향을 많이 받는 것 같아요. 비슷한 경험 있으신 분들 이야기 들어보고 싶습니다.', false, '2026-03-11 11:00:00', '2026-03-11 11:00:00'),
  ('fc3b06b0-3e73-44cf-8dd0-b48405ff576a', '7f6ec560-b894-4aff-8b47-2033d18f8658', (SELECT id FROM crop WHERE name = '가지'), 'ef7ab756-32d0-4435-a5a5-9f08e1dbb5c5', 'GENERAL', '올해 가지 작황 어떠신가요', '가지를 키운 지 얼마 안 됐는데 생각보다 손이 많이 가네요. 물주기 주기랑 병해충 관리가 특히 신경 쓰입니다. 다들 어떻게 관리하시는지 궁금합니다.', false, '2026-03-17 13:10:00', '2026-03-17 13:10:00'),
  ('10c7568e-b29a-4ae8-b4f8-29ce01456aa6', '7f6ec560-b894-4aff-8b47-2033d18f8658', (SELECT id FROM crop WHERE name = '가락지나물'), '79c2d0b0-fb8d-4717-ae8c-455d80a837ea', 'QUESTION', '가락지나물 방제 시기 문의', '가락지나물 잎 뒷면에 작은 반점이 생겼는데 병해인지 영양 결핍인지 구분이 안 됩니다. 비슷한 증상 겪어보신 분 계신가요?', false, '2026-03-22 18:40:00', '2026-03-22 18:40:00'),
  ('36a1adc8-621a-4db2-8535-6d5620952856', '7f6ec560-b894-4aff-8b47-2033d18f8658', (SELECT id FROM crop WHERE name = '감자'), NULL, 'GENERAL', '감자 수확 후기 남깁니다', '감자를 키운 지 얼마 안 됐는데 생각보다 손이 많이 가네요. 물주기 주기랑 병해충 관리가 특히 신경 쓰입니다. 다들 어떻게 관리하시는지 궁금합니다.', false, '2026-03-19 01:00:00', '2026-03-19 01:00:00'),
  ('db201327-cf24-4171-bb0e-43f07b1d2483', '7f6ec560-b894-4aff-8b47-2033d18f8658', (SELECT id FROM crop WHERE name = '가지'), NULL, 'GENERAL', '가지 관리 팁 정리해봤어요', '이번 시즌 가지 농사를 지어봤는데 날씨 영향을 많이 받는 것 같아요. 비슷한 경험 있으신 분들 이야기 들어보고 싶습니다.', false, '2026-03-24 01:00:00', '2026-03-24 01:00:00'),
  ('bf14d266-c19d-4ed9-9882-7fedd23cf37b', '7f6ec560-b894-4aff-8b47-2033d18f8658', (SELECT id FROM crop WHERE name = '가락지나물'), NULL, 'GENERAL', '가락지나물 관리 팁 정리해봤어요', '가락지나물를 키운 지 얼마 안 됐는데 생각보다 손이 많이 가네요. 물주기 주기랑 병해충 관리가 특히 신경 쓰입니다. 다들 어떻게 관리하시는지 궁금합니다.', false, '2026-03-29 01:00:00', '2026-03-29 01:00:00'),
  ('8cc30894-7fe2-4973-b19c-39ce6ddeec8e', '7f6ec560-b894-4aff-8b47-2033d18f8658', (SELECT id FROM crop WHERE name = '감자'), NULL, 'GENERAL', '감자 수확 후기 남깁니다', '감자를 키운 지 얼마 안 됐는데 생각보다 손이 많이 가네요. 물주기 주기랑 병해충 관리가 특히 신경 쓰입니다. 다들 어떻게 관리하시는지 궁금합니다.', false, '2026-04-03 05:00:00', '2026-04-03 05:00:00'),
  ('c67f0251-6dd9-4a79-aba3-149fa2a72954', '7f6ec560-b894-4aff-8b47-2033d18f8658', (SELECT id FROM crop WHERE name = '가지'), NULL, 'QUESTION', '가지 방제 시기 문의', '장마철 앞두고 가지 병해충 방제를 미리 해두는 게 나을지 고민입니다. 조언 부탁드립니다.', false, '2026-04-08 12:00:00', '2026-04-08 12:00:00'),
  ('d6adbfa3-b7bc-43d4-93b0-c79edd1b31b0', '7f6ec560-b894-4aff-8b47-2033d18f8658', (SELECT id FROM crop WHERE name = '가락지나물'), NULL, 'GENERAL', '가락지나물 관리 팁 정리해봤어요', '이번 시즌 가락지나물 농사를 지어봤는데 날씨 영향을 많이 받는 것 같아요. 비슷한 경험 있으신 분들 이야기 들어보고 싶습니다.', false, '2026-04-13 05:00:00', '2026-04-13 05:00:00'),
  ('bfcf2a78-0bf9-47ac-ad6d-a1ac63688dbb', '7f6ec560-b894-4aff-8b47-2033d18f8658', (SELECT id FROM crop WHERE name = '감자'), NULL, 'GENERAL', '감자 관리 팁 정리해봤어요', '감자 밭 상태를 공유합니다. 생육이 예상보다 빠른 편인데 이 정도면 정상인지 궁금하네요.', false, '2026-04-18 12:00:00', '2026-04-18 12:00:00'),
  ('e9c8dff3-f89a-43d3-afca-8526baaa1f68', '7f6ec560-b894-4aff-8b47-2033d18f8658', (SELECT id FROM crop WHERE name = '가지'), NULL, 'GENERAL', '가지 수확 후기 남깁니다', '가지 밭 상태를 공유합니다. 생육이 예상보다 빠른 편인데 이 정도면 정상인지 궁금하네요.', false, '2026-04-23 05:00:00', '2026-04-23 05:00:00'),
  ('c40fe042-64e6-45ea-988b-7f6da783005e', '7f6ec560-b894-4aff-8b47-2033d18f8658', (SELECT id FROM crop WHERE name = '가락지나물'), NULL, 'GENERAL', '올해 가락지나물 작황 어떠신가요', '가락지나물 밭 상태를 공유합니다. 생육이 예상보다 빠른 편인데 이 정도면 정상인지 궁금하네요.', false, '2026-04-28 05:00:00', '2026-04-28 05:00:00'),
  ('1204645e-d34c-40e5-80e7-8ca3be2618e6', '7f6ec560-b894-4aff-8b47-2033d18f8658', (SELECT id FROM crop WHERE name = '감자'), NULL, 'QUESTION', '감자 웃거름 시기 질문드립니다', '감자 웃거름을 언제쯤 주는 게 적당할지 궁금합니다. 지금 시기에 줘도 괜찮을까요?', false, '2026-05-03 01:00:00', '2026-05-03 01:00:00'),
  ('5697ea9d-07e2-46be-b6d0-adcb48caaea1', '7f6ec560-b894-4aff-8b47-2033d18f8658', (SELECT id FROM crop WHERE name = '가지'), NULL, 'GENERAL', '가지 재배 3개월차 후기', '가지 밭 상태를 공유합니다. 생육이 예상보다 빠른 편인데 이 정도면 정상인지 궁금하네요.', false, '2026-05-08 05:00:00', '2026-05-08 05:00:00'),
  ('82382179-2e97-4282-a7cf-df3ef7a765cb', '7f6ec560-b894-4aff-8b47-2033d18f8658', (SELECT id FROM crop WHERE name = '가락지나물'), NULL, 'GENERAL', '올해 가락지나물 작황 어떠신가요', '이번 시즌 가락지나물 농사를 지어봤는데 날씨 영향을 많이 받는 것 같아요. 비슷한 경험 있으신 분들 이야기 들어보고 싶습니다.', false, '2026-05-13 05:00:00', '2026-05-13 05:00:00'),
  ('2b21574e-e6db-4d6a-8caf-e13300c46661', '7f6ec560-b894-4aff-8b47-2033d18f8658', (SELECT id FROM crop WHERE name = '감자'), NULL, 'GENERAL', '감자 키우면서 겪은 시행착오 공유합니다', '감자 밭 상태를 공유합니다. 생육이 예상보다 빠른 편인데 이 정도면 정상인지 궁금하네요.', false, '2026-05-18 05:00:00', '2026-05-18 05:00:00'),
  ('4f925c56-633a-446e-8a0d-f183141ec36e', '7f6ec560-b894-4aff-8b47-2033d18f8658', (SELECT id FROM crop WHERE name = '가지'), NULL, 'GENERAL', '올해 가지 작황 어떠신가요', '이번 시즌 가지 농사를 지어봤는데 날씨 영향을 많이 받는 것 같아요. 비슷한 경험 있으신 분들 이야기 들어보고 싶습니다.', false, '2026-05-23 05:00:00', '2026-05-23 05:00:00'),
  ('d5e82545-1d21-4402-82c8-e52067699792', '7f6ec560-b894-4aff-8b47-2033d18f8658', (SELECT id FROM crop WHERE name = '가락지나물'), NULL, 'QUESTION', '가락지나물 웃거름 시기 질문드립니다', '장마철 앞두고 가락지나물 병해충 방제를 미리 해두는 게 나을지 고민입니다. 조언 부탁드립니다.', false, '2026-05-28 01:00:00', '2026-05-28 01:00:00'),
  ('7d2e6682-670e-42dc-9424-5a11565d9d02', '7f6ec560-b894-4aff-8b47-2033d18f8658', (SELECT id FROM crop WHERE name = '감자'), NULL, 'GENERAL', '올해 감자 작황 어떠신가요', '감자를 키운 지 얼마 안 됐는데 생각보다 손이 많이 가네요. 물주기 주기랑 병해충 관리가 특히 신경 쓰입니다. 다들 어떻게 관리하시는지 궁금합니다.', false, '2026-06-02 01:00:00', '2026-06-02 01:00:00'),
  ('4ebb6464-4d06-4562-be02-f8773684f814', '7f6ec560-b894-4aff-8b47-2033d18f8658', (SELECT id FROM crop WHERE name = '가지'), NULL, 'GENERAL', '가지 수확 후기 남깁니다', '가지 밭 상태를 공유합니다. 생육이 예상보다 빠른 편인데 이 정도면 정상인지 궁금하네요.', false, '2026-06-07 01:00:00', '2026-06-07 01:00:00'),
  ('370ec0ab-08bc-400b-9f34-d91856419881', '7f6ec560-b894-4aff-8b47-2033d18f8658', (SELECT id FROM crop WHERE name = '가락지나물'), NULL, 'GENERAL', '올해 가락지나물 작황 어떠신가요', '가락지나물를 키운 지 얼마 안 됐는데 생각보다 손이 많이 가네요. 물주기 주기랑 병해충 관리가 특히 신경 쓰입니다. 다들 어떻게 관리하시는지 궁금합니다.', false, '2026-06-12 05:00:00', '2026-06-12 05:00:00'),
  ('64715e19-7148-423c-8923-9a6dc55a22c1', '7f6ec560-b894-4aff-8b47-2033d18f8658', (SELECT id FROM crop WHERE name = '감자'), NULL, 'GENERAL', '감자 수확 후기 남깁니다', '이번 시즌 감자 농사를 지어봤는데 날씨 영향을 많이 받는 것 같아요. 비슷한 경험 있으신 분들 이야기 들어보고 싶습니다.', false, '2026-06-17 12:00:00', '2026-06-17 12:00:00'),
  ('34fc1043-871d-4634-8c01-e52d6ae3e919', '7f6ec560-b894-4aff-8b47-2033d18f8658', (SELECT id FROM crop WHERE name = '가지'), NULL, 'QUESTION', '가지 웃거름 시기 질문드립니다', '가지 웃거름을 언제쯤 주는 게 적당할지 궁금합니다. 지금 시기에 줘도 괜찮을까요?', false, '2026-06-22 01:00:00', '2026-06-22 01:00:00'),
  ('5b979949-dbb2-4796-8f8e-409b2ede0011', 'ad55875b-84f9-4f1d-be1b-67eec3884575', (SELECT id FROM crop WHERE name = '강활'), '69d90e30-baee-4f12-9acc-a85c7296611c', 'GENERAL', '올해 강활 작황 어떠신가요', '이번 시즌 강활 농사를 지어봤는데 날씨 영향을 많이 받는 것 같아요. 비슷한 경험 있으신 분들 이야기 들어보고 싶습니다.', false, '2026-02-04 12:30:00', '2026-02-04 12:30:00'),
  ('7fdee0a7-f8d8-4d91-a9d1-9162be42a8f4', 'ad55875b-84f9-4f1d-be1b-67eec3884575', (SELECT id FROM crop WHERE name = '개똥쑥'), 'c4af1d6c-cc48-4b25-9d85-47138ee2dbea', 'GENERAL', '개똥쑥 관리 팁 정리해봤어요', '개똥쑥 밭 상태를 공유합니다. 생육이 예상보다 빠른 편인데 이 정도면 정상인지 궁금하네요.', false, '2026-02-09 17:30:00', '2026-02-09 17:30:00'),
  ('65fff8b2-8a72-4e40-9621-5c81802d4277', 'ad55875b-84f9-4f1d-be1b-67eec3884575', (SELECT id FROM crop WHERE name = '감초'), 'cc9eccc0-00dd-4738-ba6f-85a009f8dc98', 'GENERAL', '감초 관리 팁 정리해봤어요', '이번 시즌 감초 농사를 지어봤는데 날씨 영향을 많이 받는 것 같아요. 비슷한 경험 있으신 분들 이야기 들어보고 싶습니다.', false, '2026-02-15 12:30:00', '2026-02-15 12:30:00'),
  ('2793d592-c8b8-4910-af2b-324ce4d79995', 'ad55875b-84f9-4f1d-be1b-67eec3884575', (SELECT id FROM crop WHERE name = '강활'), '6f89f741-54b5-4569-91e8-f61a8343de47', 'GENERAL', '강활 키우면서 겪은 시행착오 공유합니다', '이번 시즌 강활 농사를 지어봤는데 날씨 영향을 많이 받는 것 같아요. 비슷한 경험 있으신 분들 이야기 들어보고 싶습니다.', false, '2026-02-20 11:10:00', '2026-02-20 11:10:00'),
  ('8e106449-f4fb-431f-a51d-19bea412a8f0', 'ad55875b-84f9-4f1d-be1b-67eec3884575', (SELECT id FROM crop WHERE name = '개똥쑥'), '376c329f-c67d-44ef-a391-ba21a1361106', 'QUESTION', '개똥쑥 웃거름 시기 질문드립니다', '개똥쑥 웃거름을 언제쯤 주는 게 적당할지 궁금합니다. 지금 시기에 줘도 괜찮을까요?', false, '2026-02-26 19:00:00', '2026-02-26 19:00:00'),
  ('84378486-72a6-42b0-82b9-09737a1e6cd8', 'ad55875b-84f9-4f1d-be1b-67eec3884575', (SELECT id FROM crop WHERE name = '감초'), '830ac87b-fd2d-4b5b-85b6-58022101c30c', 'GENERAL', '감초 관리 팁 정리해봤어요', '감초 밭 상태를 공유합니다. 생육이 예상보다 빠른 편인데 이 정도면 정상인지 궁금하네요.', false, '2026-03-03 17:20:00', '2026-03-03 17:20:00'),
  ('4039a93c-b8fa-4c12-b004-d3ed6e3d5aa0', 'ad55875b-84f9-4f1d-be1b-67eec3884575', (SELECT id FROM crop WHERE name = '강활'), 'd29d7c91-7743-4d0e-946c-c13aa478c339', 'GENERAL', '강활 수확 후기 남깁니다', '강활를 키운 지 얼마 안 됐는데 생각보다 손이 많이 가네요. 물주기 주기랑 병해충 관리가 특히 신경 쓰입니다. 다들 어떻게 관리하시는지 궁금합니다.', false, '2026-03-08 21:30:00', '2026-03-08 21:30:00'),
  ('be5c60a4-7113-438d-83e7-be2848e92082', 'ad55875b-84f9-4f1d-be1b-67eec3884575', (SELECT id FROM crop WHERE name = '개똥쑥'), '772e4b59-3c13-4db8-8519-d0c8340f0123', 'GENERAL', '개똥쑥 수확 후기 남깁니다', '이번 시즌 개똥쑥 농사를 지어봤는데 날씨 영향을 많이 받는 것 같아요. 비슷한 경험 있으신 분들 이야기 들어보고 싶습니다.', false, '2026-03-14 12:30:00', '2026-03-14 12:30:00'),
  ('9e18fa5d-1920-4728-9cf3-7f87442bac9f', 'ad55875b-84f9-4f1d-be1b-67eec3884575', (SELECT id FROM crop WHERE name = '감초'), 'd49e52a9-72cb-4a36-8545-22a56a97263c', 'GENERAL', '감초 수확 후기 남깁니다', '이번 시즌 감초 농사를 지어봤는데 날씨 영향을 많이 받는 것 같아요. 비슷한 경험 있으신 분들 이야기 들어보고 싶습니다.', false, '2026-03-19 21:40:00', '2026-03-19 21:40:00'),
  ('597c4ded-1ff3-4d4d-8f97-d0784ebcc0e5', 'ad55875b-84f9-4f1d-be1b-67eec3884575', (SELECT id FROM crop WHERE name = '강활'), '8a60a18e-5bdc-466f-b73b-29b994257417', 'QUESTION', '강활 방제 시기 문의', '강활 웃거름을 언제쯤 주는 게 적당할지 궁금합니다. 지금 시기에 줘도 괜찮을까요?', false, '2026-03-24 21:30:00', '2026-03-24 21:30:00'),
  ('4e0ede4b-12f1-47aa-9467-87ebdb1a3538', 'ad55875b-84f9-4f1d-be1b-67eec3884575', (SELECT id FROM crop WHERE name = '개똥쑥'), NULL, 'GENERAL', '올해 개똥쑥 작황 어떠신가요', '이번 시즌 개똥쑥 농사를 지어봤는데 날씨 영향을 많이 받는 것 같아요. 비슷한 경험 있으신 분들 이야기 들어보고 싶습니다.', false, '2026-03-21 17:30:00', '2026-03-21 17:30:00'),
  ('39df7df4-c919-4794-a0e2-4a9286d135b0', 'ad55875b-84f9-4f1d-be1b-67eec3884575', (SELECT id FROM crop WHERE name = '감초'), NULL, 'GENERAL', '감초 관리 팁 정리해봤어요', '감초 밭 상태를 공유합니다. 생육이 예상보다 빠른 편인데 이 정도면 정상인지 궁금하네요.', false, '2026-03-26 17:30:00', '2026-03-26 17:30:00'),
  ('73e12a23-c56a-46c0-8b34-ca4a0727f43d', 'ad55875b-84f9-4f1d-be1b-67eec3884575', (SELECT id FROM crop WHERE name = '강활'), NULL, 'GENERAL', '강활 수확 후기 남깁니다', '강활 밭 상태를 공유합니다. 생육이 예상보다 빠른 편인데 이 정도면 정상인지 궁금하네요.', false, '2026-04-01 04:30:00', '2026-04-01 04:30:00'),
  ('ca65cc0d-8482-461f-a2fc-67d35dd59fde', 'ad55875b-84f9-4f1d-be1b-67eec3884575', (SELECT id FROM crop WHERE name = '개똥쑥'), NULL, 'GENERAL', '개똥쑥 키우면서 겪은 시행착오 공유합니다', '개똥쑥를 키운 지 얼마 안 됐는데 생각보다 손이 많이 가네요. 물주기 주기랑 병해충 관리가 특히 신경 쓰입니다. 다들 어떻게 관리하시는지 궁금합니다.', false, '2026-04-06 04:30:00', '2026-04-06 04:30:00'),
  ('3ade7fbd-3436-433e-80fa-bc2b176acde1', 'ad55875b-84f9-4f1d-be1b-67eec3884575', (SELECT id FROM crop WHERE name = '감초'), NULL, 'QUESTION', '감초 웃거름 시기 질문드립니다', '감초 잎 뒷면에 작은 반점이 생겼는데 병해인지 영양 결핍인지 구분이 안 됩니다. 비슷한 증상 겪어보신 분 계신가요?', false, '2026-04-10 21:30:00', '2026-04-10 21:30:00'),
  ('bcfa1f1b-322e-404e-9385-7b45bafe3b80', 'ad55875b-84f9-4f1d-be1b-67eec3884575', (SELECT id FROM crop WHERE name = '강활'), NULL, 'GENERAL', '올해 강활 작황 어떠신가요', '강활를 키운 지 얼마 안 됐는데 생각보다 손이 많이 가네요. 물주기 주기랑 병해충 관리가 특히 신경 쓰입니다. 다들 어떻게 관리하시는지 궁금합니다.', false, '2026-04-15 21:30:00', '2026-04-15 21:30:00'),
  ('3dcdaeff-29e4-4125-8333-76454ebeb662', 'ad55875b-84f9-4f1d-be1b-67eec3884575', (SELECT id FROM crop WHERE name = '개똥쑥'), NULL, 'GENERAL', '개똥쑥 수확 후기 남깁니다', '이번 시즌 개똥쑥 농사를 지어봤는데 날씨 영향을 많이 받는 것 같아요. 비슷한 경험 있으신 분들 이야기 들어보고 싶습니다.', false, '2026-04-20 17:30:00', '2026-04-20 17:30:00'),
  ('f3290af4-7875-4db1-9e75-46d5210a8810', 'ad55875b-84f9-4f1d-be1b-67eec3884575', (SELECT id FROM crop WHERE name = '감초'), NULL, 'GENERAL', '감초 수확 후기 남깁니다', '이번 시즌 감초 농사를 지어봤는데 날씨 영향을 많이 받는 것 같아요. 비슷한 경험 있으신 분들 이야기 들어보고 싶습니다.', false, '2026-04-25 21:30:00', '2026-04-25 21:30:00'),
  ('21b5cf95-7595-41bd-bf20-10c63bc8a266', 'ad55875b-84f9-4f1d-be1b-67eec3884575', (SELECT id FROM crop WHERE name = '강활'), NULL, 'GENERAL', '강활 수확 후기 남깁니다', '이번 시즌 강활 농사를 지어봤는데 날씨 영향을 많이 받는 것 같아요. 비슷한 경험 있으신 분들 이야기 들어보고 싶습니다.', false, '2026-04-30 21:30:00', '2026-04-30 21:30:00'),
  ('4f29f557-4cb8-43b4-ae9e-79637e692056', 'ad55875b-84f9-4f1d-be1b-67eec3884575', (SELECT id FROM crop WHERE name = '개똥쑥'), NULL, 'QUESTION', '개똥쑥 웃거름 시기 질문드립니다', '개똥쑥 잎 뒷면에 작은 반점이 생겼는데 병해인지 영양 결핍인지 구분이 안 됩니다. 비슷한 증상 겪어보신 분 계신가요?', false, '2026-05-05 21:30:00', '2026-05-05 21:30:00'),
  ('dbb2c8bb-f744-413b-948a-737626e088aa', 'ad55875b-84f9-4f1d-be1b-67eec3884575', (SELECT id FROM crop WHERE name = '감초'), NULL, 'GENERAL', '감초 키우면서 겪은 시행착오 공유합니다', '감초 밭 상태를 공유합니다. 생육이 예상보다 빠른 편인데 이 정도면 정상인지 궁금하네요.', false, '2026-05-11 04:30:00', '2026-05-11 04:30:00'),
  ('d3c201bf-5798-45a2-98be-023deadc8c0b', 'ad55875b-84f9-4f1d-be1b-67eec3884575', (SELECT id FROM crop WHERE name = '강활'), NULL, 'GENERAL', '강활 키우면서 겪은 시행착오 공유합니다', '이번 시즌 강활 농사를 지어봤는데 날씨 영향을 많이 받는 것 같아요. 비슷한 경험 있으신 분들 이야기 들어보고 싶습니다.', false, '2026-05-15 17:30:00', '2026-05-15 17:30:00'),
  ('b51432d6-7770-476f-b774-cd0c792949c7', 'ad55875b-84f9-4f1d-be1b-67eec3884575', (SELECT id FROM crop WHERE name = '개똥쑥'), NULL, 'GENERAL', '개똥쑥 키우면서 겪은 시행착오 공유합니다', '개똥쑥 밭 상태를 공유합니다. 생육이 예상보다 빠른 편인데 이 정도면 정상인지 궁금하네요.', false, '2026-05-20 17:30:00', '2026-05-20 17:30:00'),
  ('55415be4-a986-4c9b-9649-28df329bfd20', 'ad55875b-84f9-4f1d-be1b-67eec3884575', (SELECT id FROM crop WHERE name = '감초'), NULL, 'GENERAL', '감초 키우면서 겪은 시행착오 공유합니다', '감초를 키운 지 얼마 안 됐는데 생각보다 손이 많이 가네요. 물주기 주기랑 병해충 관리가 특히 신경 쓰입니다. 다들 어떻게 관리하시는지 궁금합니다.', false, '2026-05-25 17:30:00', '2026-05-25 17:30:00'),
  ('aca14985-2d48-4646-aec9-d365da102368', 'ad55875b-84f9-4f1d-be1b-67eec3884575', (SELECT id FROM crop WHERE name = '강활'), NULL, 'QUESTION', '강활 잎에 반점이 생겼는데 원인이 뭘까요', '장마철 앞두고 강활 병해충 방제를 미리 해두는 게 나을지 고민입니다. 조언 부탁드립니다.', false, '2026-05-30 17:30:00', '2026-05-30 17:30:00'),
  ('543a62a9-4c34-46e3-9bfb-5a96d95d76f2', 'ad55875b-84f9-4f1d-be1b-67eec3884575', (SELECT id FROM crop WHERE name = '개똥쑥'), NULL, 'GENERAL', '올해 개똥쑥 작황 어떠신가요', '개똥쑥를 키운 지 얼마 안 됐는데 생각보다 손이 많이 가네요. 물주기 주기랑 병해충 관리가 특히 신경 쓰입니다. 다들 어떻게 관리하시는지 궁금합니다.', false, '2026-06-04 21:30:00', '2026-06-04 21:30:00'),
  ('0f8e608d-dd65-45ee-9991-f4fdde0c9b03', 'ad55875b-84f9-4f1d-be1b-67eec3884575', (SELECT id FROM crop WHERE name = '감초'), NULL, 'GENERAL', '감초 키우면서 겪은 시행착오 공유합니다', '감초 밭 상태를 공유합니다. 생육이 예상보다 빠른 편인데 이 정도면 정상인지 궁금하네요.', false, '2026-06-09 17:30:00', '2026-06-09 17:30:00'),
  ('c779a280-e92c-49d4-ad2d-4e9cc1d4965a', 'ad55875b-84f9-4f1d-be1b-67eec3884575', (SELECT id FROM crop WHERE name = '강활'), NULL, 'GENERAL', '강활 수확 후기 남깁니다', '강활 밭 상태를 공유합니다. 생육이 예상보다 빠른 편인데 이 정도면 정상인지 궁금하네요.', false, '2026-06-14 21:30:00', '2026-06-14 21:30:00'),
  ('4999e1ef-03d0-4f16-b70b-26545e77c0c9', 'ad55875b-84f9-4f1d-be1b-67eec3884575', (SELECT id FROM crop WHERE name = '개똥쑥'), NULL, 'GENERAL', '개똥쑥 재배 3개월차 후기', '개똥쑥를 키운 지 얼마 안 됐는데 생각보다 손이 많이 가네요. 물주기 주기랑 병해충 관리가 특히 신경 쓰입니다. 다들 어떻게 관리하시는지 궁금합니다.', false, '2026-06-20 04:30:00', '2026-06-20 04:30:00'),
  ('a607ac75-049f-457c-a092-d744cbd5ced5', 'ad55875b-84f9-4f1d-be1b-67eec3884575', (SELECT id FROM crop WHERE name = '감초'), NULL, 'QUESTION', '감초 방제 시기 문의', '장마철 앞두고 감초 병해충 방제를 미리 해두는 게 나을지 고민입니다. 조언 부탁드립니다.', false, '2026-06-25 04:30:00', '2026-06-25 04:30:00')
ON CONFLICT (id) DO NOTHING;

COMMIT;
