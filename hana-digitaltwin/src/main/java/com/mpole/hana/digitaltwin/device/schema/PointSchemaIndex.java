package com.mpole.hana.digitaltwin.device.schema;

import com.mpole.hana.digitaltwin.device.entity.DeviceCategory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 카테고리 스키마를 {@code tagName} 으로 찾을 수 있게 색인한 것.
 *
 * <h3>왜 필요한가</h3>
 * <p>{@code sortOrder}·{@code hud} 는 <b>카테고리의 {@code schema_definition}(JSONB)</b> 에만 있다.
 * 반면 응답은 {@code tbl_device_point} 행이나 {@code tbl_device_current_value.points} 에서 만들어진다.
 * 둘을 잇는 키가 {@code tagName} 이라, 매번 리스트를 훑지 않도록 한 번 색인해 둔다.</p>
 *
 * <p>포인트 컬럼으로 내려쓰지 않는 이유 — 정본이 두 곳으로 갈라지면 스키마를 고쳐도
 * 기존 포인트 행이 따라오지 않아 화면과 설정이 어긋난다({@code isDisplay → active} 가 그 예다).</p>
 *
 * <p><b>추가 쿼리가 없다.</b> 카테고리는 {@code categoryPath} 를 만들려고 이미 로딩돼 있고,
 * {@code schemaDefinitions} 는 그 행의 JSONB 컬럼이다.</p>
 */
public final class PointSchemaIndex {

    private static final PointSchemaIndex EMPTY = new PointSchemaIndex(Map.of());

    private final Map<String, PointSchemaDefinition> byTagName;

    private PointSchemaIndex(Map<String, PointSchemaDefinition> byTagName) {
        this.byTagName = byTagName;
    }

    /** 카테고리가 없거나 스키마가 비어 있으면 빈 색인을 준다(호출부에서 null 검사 불필요). */
    public static PointSchemaIndex of(DeviceCategory category) {
        if (category == null) {
            return EMPTY;
        }
        return of(category.getSchemaDefinitions());
    }

    public static PointSchemaIndex of(List<PointSchemaDefinition> definitions) {
        if (definitions == null || definitions.isEmpty()) {
            return EMPTY;
        }
        Map<String, PointSchemaDefinition> map = new HashMap<>();
        for (int i = 0; i < definitions.size(); i++) {
            PointSchemaDefinition d = definitions.get(i);
            if (d == null || d.tagName() == null || d.tagName().isBlank()) {
                continue;
            }
            // sortOrder 가 없던 시절 데이터는 배열 순서가 곧 의도한 순서다.
            PointSchemaDefinition resolved = d.sortOrder() != null ? d : new PointSchemaDefinition(
                    d.tagName(), d.type(), d.unit(), d.tagDesc(), d.tagDisplayName(),
                    d.isDisplay(), d.dataSource(), i, d.hud(),
                    d.accumulate(), d.scale(), d.historyMode());
            // 같은 tagName 이 둘이면 앞선 것을 남긴다(스키마 입력 오류 — 순서를 뒤집지 않는다).
            map.putIfAbsent(resolved.tagName().trim(), resolved);
        }
        return map.isEmpty() ? EMPTY : new PointSchemaIndex(map);
    }

    public static PointSchemaIndex empty() {
        return EMPTY;
    }

    /** 없으면 null — 스키마에서 지워졌는데 포인트 행은 남아 있는 태그. */
    public PointSchemaDefinition find(String tagName) {
        if (tagName == null) {
            return null;
        }
        return byTagName.get(tagName.trim());
    }

    /** 표시 순서. 스키마에 없는 태그는 null(클라이언트가 뒤로 밀면 된다). */
    public Integer sortOrderOf(String tagName) {
        PointSchemaDefinition d = find(tagName);
        return d != null ? d.sortOrder() : null;
    }

    /** HUD 표출 여부. 스키마에 없거나 미지정이면 false. */
    public boolean hudOf(String tagName) {
        PointSchemaDefinition d = find(tagName);
        return d != null && d.hudEnabled();
    }

    public boolean isEmpty() {
        return byTagName.isEmpty();
    }
}
