-- SOP 템플릿·버전 테이블 통합 (기존 DB가 version 구조일 때 1회 실행)
-- ddl-auto=update만 쓰는 환경에서는 Hibernate가 컬럼 추가 후, 아래를 수동 실행할 수 있습니다.

-- 1) template에 version 컬럼 이전
ALTER TABLE tbl_sop_template ADD COLUMN IF NOT EXISTS title VARCHAR(255);
ALTER TABLE tbl_sop_template ADD COLUMN IF NOT EXISTS timeout_minutes INT DEFAULT 10;
ALTER TABLE tbl_sop_template ADD COLUMN IF NOT EXISTS unity_layout JSONB;
ALTER TABLE tbl_sop_template ADD COLUMN IF NOT EXISTS revision INT DEFAULT 1;

UPDATE tbl_sop_template t
SET title = COALESCE(v.title, t.template_name),
    timeout_minutes = COALESCE(v.timeout_minutes, 10),
    unity_layout = v.unity_layout,
    revision = 1
FROM tbl_sop_template_version v
WHERE v.version_id = t.current_version_id
  AND t.title IS NULL;

UPDATE tbl_sop_template SET title = template_name WHERE title IS NULL;
UPDATE tbl_sop_template SET timeout_minutes = 10 WHERE timeout_minutes IS NULL;
UPDATE tbl_sop_template SET revision = 1 WHERE revision IS NULL;

-- 2) step / transition: template_id (version_id가 있을 때)
ALTER TABLE tbl_sop_step ADD COLUMN IF NOT EXISTS template_id BIGINT;
UPDATE tbl_sop_step s
SET template_id = v.template_id
FROM tbl_sop_template_version v
WHERE s.version_id = v.version_id AND s.template_id IS NULL;

ALTER TABLE tbl_sop_transition ADD COLUMN IF NOT EXISTS template_id BIGINT;
UPDATE tbl_sop_transition tr
SET template_id = v.template_id
FROM tbl_sop_template_version v
WHERE tr.version_id = v.version_id AND tr.template_id IS NULL;

-- 3) instance: template_id + snapshot (기존 instance가 있으면 수동 보완 필요)
ALTER TABLE tbl_sop_instance ADD COLUMN IF NOT EXISTS template_id BIGINT;
ALTER TABLE tbl_sop_instance ADD COLUMN IF NOT EXISTS locked_revision INT;
ALTER TABLE tbl_sop_instance ADD COLUMN IF NOT EXISTS structure_snapshot JSONB;

UPDATE tbl_sop_instance i
SET template_id = v.template_id,
    locked_revision = COALESCE(v.version_no, 1)
FROM tbl_sop_template_version v
WHERE i.version_id = v.version_id AND i.template_id IS NULL;

-- DROP TABLE tbl_sop_template_version;  -- 데이터 이전 확인 후 수동 삭제
