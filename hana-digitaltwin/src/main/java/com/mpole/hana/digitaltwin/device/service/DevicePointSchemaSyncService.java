package com.mpole.hana.digitaltwin.device.service;

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

        if (!tagsToDelete.isEmpty()) {
            devicePointRepository.bulkDeleteByCategoryIdAndTagNameIn(categoryId, tagsToDelete);
        }

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
                    definition.type(),
                    definition.unit(),
                    definition.isDisplay()
            );
        }

        for (String tagName : tagsToUpdate) {
            PointSchemaDefinition definition = newByTag.get(tagName);
            if (definition == null) {
                continue;
            }
            devicePointRepository.bulkUpdateSchemaFieldsByCategoryIdAndTagName(
                    categoryId,
                    tagName,
                    resolvePointName(definition),
                    definition.type(),
                    definition.unit(),
                    definition.isDisplay()
            );
        }

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
                    definition.type(),
                    definition.unit(),
                    definition.isDisplay()
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
            result.add(new PointSchemaDefinition(
                    item.tagName().trim(),
                    item.type(),
                    item.unit(),
                    item.tagDesc(),
                    item.isDisplay()
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
