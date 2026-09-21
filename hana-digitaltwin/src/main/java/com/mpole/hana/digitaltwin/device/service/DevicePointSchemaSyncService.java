package com.mpole.hana.digitaltwin.device.service;

import com.mpole.hana.common.constant.PointDataSource;
import com.mpole.hana.digitaltwin.device.repository.DevicePointRepository;
import com.mpole.hana.digitaltwin.device.schema.PointSchemaDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 카테고리 schema_definition 변경 시, 해당 카테고리 장비의 tbl_device_point 동기화.
 */
@Service
@RequiredArgsConstructor
public class DevicePointSchemaSyncService {

    private final DevicePointRepository devicePointRepository;

    @Transactional
    public void syncForCategory(Long categoryId, List<PointSchemaDefinition> oldSchema, List<PointSchemaDefinition> newSchema) {
        List<PointSchemaDefinition> oldList = normalizeSchema(oldSchema);
        List<PointSchemaDefinition> newList = normalizeSchema(newSchema);

        Map<String, PointSchemaDefinition> oldByTag = indexByTagName(oldList);
        Map<String, PointSchemaDefinition> newByTag = indexByTagName(newList);
        Map<String, String> renames = detectRenamesByIndex(oldList, newList, oldByTag, newByTag);

        Set<String> oldTags = new HashSet<>(oldByTag.keySet());
        Set<String> newTags = new HashSet<>(newByTag.keySet());

        Set<String> renamedOldTags = renames.keySet();
        Set<String> renamedNewTags = new HashSet<>(renames.values());

        Set<String> tagsToDelete = new HashSet<>(oldTags);
        tagsToDelete.removeAll(newTags);
        tagsToDelete.removeAll(renamedOldTags);

        Set<String> tagsToInsert = new HashSet<>(newTags);
        tagsToInsert.removeAll(oldTags);
        tagsToInsert.removeAll(renamedNewTags);

        Set<String> tagsToUpdate = new HashSet<>(oldTags);
        tagsToUpdate.retainAll(newTags);
        tagsToUpdate.removeAll(renamedOldTags);

        // 삭제: 신 스키마에서 빠졌고 rename 도 아닌 태그 → 카테고리 내 해당 포인트 제거(검증 없음).
        if (!tagsToDelete.isEmpty()) {
            devicePointRepository.bulkDeleteByCategoryIdAndTagNameIn(categoryId, tagsToDelete);
        }

        // 이름변경: old→new 매핑 태그. [검증] new point_key 충돌 없는지 확인 후 기존 행의 tag/키/속성 UPDATE.
        //           (UPDATE 라 old 포인트가 없던 장비엔 생성 안 됨 → 아래 백필에서 보완.)
        for (Map.Entry<String, String> rename : renames.entrySet()) {
            String oldTag = rename.getKey();
            String newTag = rename.getValue();
            PointSchemaDefinition definition = newByTag.get(newTag);
            if (definition == null) {
                continue;
            }
            assertNoRenameKeyConflict(categoryId, oldTag, newTag);
            devicePointRepository.bulkRenameByCategoryId(
                    categoryId,
                    oldTag,
                    newTag,
                    resolvePointName(definition),
                    definition.tagDisplayName(),
                    definition.type(),
                    definition.unit(),
                    definition.isDisplay(),
                    resolveDataSource(definition)
            );
        }

        // 갱신: old·new 양쪽에 존재(동일명)하는 태그 → 기존 행의 속성(설명/표시명/타입/단위/활성) UPDATE.
        //       (UPDATE 라 포인트가 없던 장비엔 생성 안 됨 → 아래 백필에서 보완.)
        for (String tagName : tagsToUpdate) {
            PointSchemaDefinition definition = newByTag.get(tagName);
            if (definition == null) {
                continue;
            }
            devicePointRepository.bulkUpdateSchemaFieldsByCategoryIdAndTagName(
                    categoryId,
                    tagName,
                    resolvePointName(definition),
                    definition.tagDisplayName(),
                    definition.type(),
                    definition.unit(),
                    definition.isDisplay(),
                    resolveDataSource(definition)
            );
        }

        // 신규: 신 스키마에만 있는 태그. [검증] new point_key 충돌 없는지 확인 후, 태그 없는 카테고리 내 전 장비에 INSERT(NOT EXISTS).
        for (String tagName : tagsToInsert) {
            PointSchemaDefinition definition = newByTag.get(tagName);
            if (definition == null) {
                continue;
            }
            assertNoInsertKeyConflict(categoryId, tagName);
            devicePointRepository.bulkInsertByCategoryIdAndTag(
                    categoryId,
                    tagName,
                    resolvePointName(definition),
                    definition.tagDisplayName(),
                    definition.type(),
                    definition.unit(),
                    definition.isDisplay(),
                    resolveDataSource(definition)
            );
        }

        // 백필(정합 보장): rename/update 는 UPDATE 라 포인트가 누락된 장비엔 안 생긴다.
        //   갱신·이름변경 대상 태그를 대상으로 NOT EXISTS INSERT 를 한 번 더 돌려, "카테고리 내 모든 장비가
        //   현재 스키마의 모든 태그 포인트를 갖는다"를 멱등 보장한다. (tagsToInsert 는 위에서 이미 전수 생성됨)
        Set<String> tagsToBackfill = new HashSet<>(tagsToUpdate);
        tagsToBackfill.addAll(renamedNewTags);
        for (String tagName : tagsToBackfill) {
            PointSchemaDefinition definition = newByTag.get(tagName);
            if (definition == null) {
                continue;
            }
            // [검증] 누락 장비에 넣을 point_key 가 다른 태그로 이미 쓰이는지(진짜 충돌만 잡음, 보유 장비는 제외됨).
            assertNoInsertKeyConflict(categoryId, tagName);
            devicePointRepository.bulkInsertByCategoryIdAndTag(
                    categoryId,
                    tagName,
                    resolvePointName(definition),
                    definition.tagDisplayName(),
                    definition.type(),
                    definition.unit(),
                    definition.isDisplay(),
                    resolveDataSource(definition)
            );
        }
    }

    private void assertNoRenameKeyConflict(Long categoryId, String oldTag, String newTag) {
        if (devicePointRepository.existsPointKeyConflictOnRename(categoryId, oldTag, newTag)) {
            throw new IllegalStateException(
                    "point_key가 이미 사용 중입니다: *_" + newTag + " (tagName=" + newTag + ", oldTag=" + oldTag + ")"
            );
        }
    }

    private void assertNoInsertKeyConflict(Long categoryId, String tagName) {
        if (devicePointRepository.existsPointKeyConflictOnInsert(categoryId, tagName)) {
            throw new IllegalStateException("이미 사용 중인 point_key 입니다: *_" + tagName);
        }
    }

    private static String resolvePointName(PointSchemaDefinition definition) {
        if (definition.tagDesc() != null && !definition.tagDesc().isBlank()) {
            return definition.tagDesc().trim();
        }
        return definition.tagName().trim();
    }

    /**
     * 스키마의 원천 소스 코드를 검증해 반환한다.
     *
     * <p>비어 있으면 null 을 돌려주고, 쿼리 쪽 {@code COALESCE(:dataSource, p.data_source)} 가
     * <b>기존 포인트 값을 유지</b>한다 — 소스를 지정하지 않은 스키마를 저장했다고 해서
     * 매핑 화면에서 개별 지정한 AMQP/DERIVED 가 지워지면, 그 포인트가 VIEW01 로 간주되어
     * gateway 기동 시 값이 null 로 덮인다. 되돌리려면 {@code VIEW01} 을 명시한다.</p>
     */
    private static String resolveDataSource(PointSchemaDefinition definition) {
        String raw = definition.dataSource();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        PointDataSource parsed = PointDataSource.fromCode(raw);
        if (parsed == null) {
            throw new IllegalArgumentException(
                    "지원하지 않는 data_source 입니다: " + raw + " (tagName=" + definition.tagName() + ")"
            );
        }
        return parsed.name();
    }

    /**
     * UI에서 행 순서를 유지한 채 tagName만 바꾼 경우(리네임)를 인덱스 기준으로 감지.
     */
    private static Map<String, String> detectRenamesByIndex(
            List<PointSchemaDefinition> oldList,
            List<PointSchemaDefinition> newList,
            Map<String, PointSchemaDefinition> oldByTag,
            Map<String, PointSchemaDefinition> newByTag
    ) {
        Map<String, String> renames = new LinkedHashMap<>();
        int limit = Math.min(oldList.size(), newList.size());
        for (int i = 0; i < limit; i++) {
            String oldTag = oldList.get(i).tagName();
            String newTag = newList.get(i).tagName();
            if (oldTag.equals(newTag)) {
                continue;
            }
            if (!oldByTag.containsKey(oldTag) || !newByTag.containsKey(newTag)) {
                continue;
            }
            if (renames.containsKey(oldTag) || renames.containsValue(newTag)) {
                continue;
            }
            boolean oldTagStillInNewList = newList.stream().anyMatch(s -> s.tagName().equals(oldTag));
            boolean newTagWasInOldList = oldList.stream().anyMatch(s -> s.tagName().equals(newTag));
            if (!oldTagStillInNewList && !newTagWasInOldList) {
                renames.put(oldTag, newTag);
            }
        }
        return renames;
    }

    private static List<PointSchemaDefinition> normalizeSchema(List<PointSchemaDefinition> schema) {
        if (schema == null || schema.isEmpty()) {
            return List.of();
        }
        List<PointSchemaDefinition> result = new ArrayList<>();
        for (PointSchemaDefinition item : schema) {
            if (item == null || item.tagName() == null || item.tagName().isBlank()) {
                continue;
            }
            // sortOrder 가 비어 있으면 배열에 담긴 순서를 그대로 쓴다
            // (이 필드 도입 전 데이터 — 지금 순서가 곧 의도한 순서다).
            result.add(new PointSchemaDefinition(
                    item.tagName().trim(),
                    item.type(),
                    item.unit(),
                    item.tagDesc(),
                    item.tagDisplayName(),
                    item.isDisplay(),
                    item.dataSource(),
                    item.sortOrderOr(result.size()),
                    item.hud()
            ));
        }
        return result;
    }

    private static Map<String, PointSchemaDefinition> indexByTagName(List<PointSchemaDefinition> list) {
        return list.stream().collect(Collectors.toMap(
                PointSchemaDefinition::tagName,
                s -> s,
                (a, b) -> b,
                LinkedHashMap::new
        ));
    }
}
