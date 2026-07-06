-- tbl_menu → tbl_device_system 마이그레이션 (기존 DB가 있을 때 수동 실행)
-- Hibernate ddl-auto=update 사용 시 신규 테이블은 자동 생성됩니다.

-- 1) BMS 시스템 (기존 tbl_menu 데이터 이전)
CREATE TABLE IF NOT EXISTS tbl_device_system (
    system_id   BIGSERIAL PRIMARY KEY,
    system_name VARCHAR(255) NOT NULL,
    system_code VARCHAR(255) NOT NULL UNIQUE,
    sort_order  INTEGER NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO tbl_device_system (system_id, system_name, system_code, sort_order, active)
SELECT menu_id, menu_name, menu_code, sort_order, active
FROM tbl_menu
ON CONFLICT (system_id) DO NOTHING;

-- 2) 시스템 ↔ 소분류 카테고리 매핑
CREATE TABLE IF NOT EXISTS tbl_device_system_category (
    mapping_id  BIGSERIAL PRIMARY KEY,
    system_id   BIGINT NOT NULL REFERENCES tbl_device_system(system_id) ON DELETE CASCADE,
    category_id BIGINT NOT NULL REFERENCES tbl_device_category(category_id) ON DELETE CASCADE,
    CONSTRAINT uk_device_system_category UNIQUE (system_id, category_id)
);

-- 3) 역할 ↔ BMS 시스템
CREATE TABLE IF NOT EXISTS tbl_role_device_system (
    role_device_system_id BIGSERIAL PRIMARY KEY,
    role_id               BIGINT NOT NULL REFERENCES tbl_role(role_id) ON DELETE CASCADE,
    system_id             BIGINT NOT NULL REFERENCES tbl_device_system(system_id) ON DELETE CASCADE,
    CONSTRAINT uk_role_device_system UNIQUE (role_id, system_id)
);

INSERT INTO tbl_role_device_system (role_id, system_id)
SELECT role_id, menu_id
FROM tbl_role_menu
ON CONFLICT DO NOTHING;

-- 4) 구 테이블 제거 (데이터 이전 확인 후 실행)
-- DROP TABLE IF EXISTS tbl_role_menu;
-- DROP TABLE IF EXISTS tbl_menu;
