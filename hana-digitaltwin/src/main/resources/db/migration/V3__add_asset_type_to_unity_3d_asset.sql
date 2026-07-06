-- 장비 에셋(tbl_unity_3d_asset)에 에셋 타입 컬럼 추가
-- ddl-auto=update 환경에서는 Hibernate가 컬럼을 자동 추가할 수 있으나,
-- 기존 데이터 백필/제약 보장을 위해 아래를 수동 실행 권장.
--
-- AssetType enum(영문 저장): EQUIPMENT, EFFECT, ZONE, EVACUATION_ROUTE, TOILET_STALL, PARKING_SLOT

-- 1) 컬럼 추가 (기본값 EQUIPMENT)
ALTER TABLE tbl_unity_3d_asset
    ADD COLUMN IF NOT EXISTS asset_type VARCHAR(32) NOT NULL DEFAULT 'EQUIPMENT';

-- 2) 기존 NULL 데이터 백필 (방어적; 위 DEFAULT로 신규/기존 모두 채워지지만 안전망)
UPDATE tbl_unity_3d_asset
SET asset_type = 'EQUIPMENT'
WHERE asset_type IS NULL;

-- 3) 허용값 제약 (DB 차원 방어 — 선택)
-- ALTER TABLE tbl_unity_3d_asset
--     ADD CONSTRAINT ck_unity_asset_type
--     CHECK (asset_type IN ('EQUIPMENT','EFFECT','ZONE','EVACUATION_ROUTE','TOILET_STALL','PARKING_SLOT'));
