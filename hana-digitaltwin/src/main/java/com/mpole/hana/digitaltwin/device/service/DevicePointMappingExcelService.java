package com.mpole.hana.digitaltwin.device.service;

import com.mpole.hana.common.constant.PointDataSource;
import com.mpole.hana.digitaltwin.device.dto.DevicePointRefMappingRequest;
import com.mpole.hana.digitaltwin.device.dto.PointMappingImportResult;
import com.mpole.hana.digitaltwin.device.dto.PointMappingStatsResponse;
import com.mpole.hana.digitaltwin.device.entity.Device;
import com.mpole.hana.digitaltwin.device.entity.DeviceCategory;
import com.mpole.hana.digitaltwin.device.entity.DevicePoint;
import com.mpole.hana.digitaltwin.device.repository.DevicePointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 포인트 매핑 엑셀 양식 다운로드/업로드 및 통계 전용 서비스.
 * <p>기존 {@link DeviceService} 의 조회/수정 로직은 변경하지 않고 재활용(호출)만 한다.
 * 매핑 반영은 {@link DeviceService#updateDevicePointRefMappings(List)} 를 그대로 사용한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DevicePointMappingExcelService {

    private static final String COL_DEVICE_NAME = "장비명";
    private static final String COL_DEVICE_DESC = "장비상세설명";
    private static final String COL_CATEGORY = "카테고리";
    private static final String COL_TAG = "태그";
    private static final String COL_POINT_NAME = "설명";
    private static final String COL_POINT_DISPLAY_NAME = "표시명";
    private static final String COL_POINT_KEY = "point_key";
    private static final String COL_POINT_TYPE = "유형";
    private static final String COL_UNIT = "단위";
    private static final String COL_REF_DEVICE_CODE = "ref_device_code";
    private static final String COL_REF_POINT_CODE = "ref_point_code";
    /**
     * 값의 원천 수집 경로. 업로드에서는 <b>선택 컬럼</b>이다 —
     * 컬럼이 없거나 셀이 비어 있으면 기존 값을 유지한다(비우지 않는다).
     */
    private static final String COL_DATA_SOURCE = "data_source";

    // 업로드는 헤더 '이름'으로 컬럼을 찾으므로(추가 컬럼 무시) 표시용 컬럼을 늘려도 import 는 무영향.
    private static final String[] HEADERS = {
            COL_DEVICE_NAME, COL_DEVICE_DESC, COL_CATEGORY, COL_TAG, COL_POINT_NAME, COL_POINT_DISPLAY_NAME,
            COL_POINT_KEY, COL_POINT_TYPE, COL_UNIT, COL_REF_DEVICE_CODE, COL_REF_POINT_CODE,
            COL_DATA_SOURCE
    };

    private final DevicePointRepository devicePointRepository;
    private final DeviceCategoryCascadeService deviceCategoryCascadeService;
    private final DeviceService deviceService;

    /**
     * 현재 필터 범위(category/keyword)의 포인트 전체를 xlsx 로 생성한다.
     * <p>목록 조회는 기존 매핑 쿼리와 동일 조건의 {@code findAllForRefMapping} 를 재사용한다.</p>
     */
    @Transactional(readOnly = true)
    public byte[] buildTemplate(Long categoryId, String keyword) {
        String kw = normalizeKeyword(keyword);
        List<Long> categoryIds = deviceCategoryCascadeService.resolveSmallCategoryIdsForFilter(categoryId);

        List<DevicePoint> points;
        if (categoryIds != null && categoryIds.isEmpty()) {
            points = List.of();
        } else {
            points = devicePointRepository.findAllForRefMapping(null, categoryIds, kw);
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            XSSFSheet sheet = workbook.createSheet("포인트매핑");

            XSSFCellStyle headerStyle = workbook.createCellStyle();
            XSSFFont headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            // ref 컬럼만 편집 대상임을 색으로 구분(잠금 대신 최소 안내)
            XSSFCellStyle refHeaderStyle = workbook.createCellStyle();
            XSSFFont refHeaderFont = workbook.createFont();
            refHeaderFont.setBold(true);
            refHeaderStyle.setFont(refHeaderFont);

            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (DevicePoint p : points) {
                Device device = p.getDevice();
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(device != null ? nvl(device.getDeviceName()) : "");
                row.createCell(1).setCellValue(device != null ? nvl(device.getDescription()) : "");
                row.createCell(2).setCellValue(categoryPath(device));
                row.createCell(3).setCellValue(nvl(p.getTagName()));
                row.createCell(4).setCellValue(nvl(p.getDescription()));
                row.createCell(5).setCellValue(nvl(p.getPointDisplayName()));
                row.createCell(6).setCellValue(nvl(p.getPointKey()));
                row.createCell(7).setCellValue(nvl(p.getPointType()));
                row.createCell(8).setCellValue(nvl(p.getUnit()));
                row.createCell(9).setCellValue(nvl(p.getRefDeviceCode()));
                row.createCell(10).setCellValue(nvl(p.getRefPointCode()));
                row.createCell(11).setCellValue(p.getDataSource() != null ? p.getDataSource().name() : "");
            }

            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("엑셀 템플릿 생성에 실패했습니다.", e);
        }
    }

    /**
     * 엑셀 업로드 파싱 후 유효 행만 매핑 반영한다(부분 반영 + 실패 목록 반환).
     * <p>반영은 기존 {@link DeviceService#updateDevicePointRefMappings(List)} 를 재활용한다.</p>
     */
    @Transactional
    public PointMappingImportResult importMappings(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드된 파일이 비어 있습니다.");
        }

        DataFormatter formatter = new DataFormatter();
        List<ParsedRow> parsedRows = new ArrayList<>();

        try (InputStream in = file.getInputStream();
             XSSFWorkbook workbook = new XSSFWorkbook(in)) {

            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("시트가 존재하지 않습니다.");
            }
            XSSFSheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new IllegalArgumentException("헤더 행이 존재하지 않습니다.");
            }

            Map<String, Integer> colIndex = resolveColumnIndex(headerRow, formatter);
            requireColumn(colIndex, COL_POINT_KEY);
            requireColumn(colIndex, COL_REF_DEVICE_CODE);
            requireColumn(colIndex, COL_REF_POINT_CODE);

            int keyCol = colIndex.get(COL_POINT_KEY);
            int refDevCol = colIndex.get(COL_REF_DEVICE_CODE);
            int refPntCol = colIndex.get(COL_REF_POINT_CODE);
            // 선택 컬럼 — 없으면 -1. 구버전 양식으로 업로드해도 그대로 동작한다.
            Integer dataSourceColBoxed = colIndex.get(COL_DATA_SOURCE);
            int dataSourceCol = dataSourceColBoxed != null ? dataSourceColBoxed : -1;

            for (int r = headerRow.getRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                String pointKey = trimToEmpty(readCell(row, keyCol, formatter));
                String refDeviceCode = trimToEmpty(readCell(row, refDevCol, formatter));
                String refPointCode = trimToEmpty(readCell(row, refPntCol, formatter));
                String dataSource = dataSourceCol >= 0
                        ? trimToEmpty(readCell(row, dataSourceCol, formatter))
                        : "";

                if (pointKey.isEmpty() && refDeviceCode.isEmpty() && refPointCode.isEmpty() && dataSource.isEmpty()) {
                    continue; // 완전 빈 행은 무시
                }
                // 사용자 표시용 행 번호(1-based)
                parsedRows.add(new ParsedRow(r + 1, pointKey, refDeviceCode, refPointCode, dataSource));
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("엑셀 파일을 읽을 수 없습니다.", e);
        }

        return validateAndApply(parsedRows);
    }

    private PointMappingImportResult validateAndApply(List<ParsedRow> parsedRows) {
        int total = parsedRows.size();
        List<PointMappingImportResult.FailedRow> failed = new ArrayList<>();

        // point_key -> DevicePoint 사전 로딩(기존 메서드 무변경, 신규 조회 메서드 재활용)
        Set<String> keys = new HashSet<>();
        for (ParsedRow pr : parsedRows) {
            if (!pr.pointKey().isEmpty()) {
                keys.add(pr.pointKey());
            }
        }
        Map<String, DevicePoint> byKey = new HashMap<>();
        if (!keys.isEmpty()) {
            for (DevicePoint p : devicePointRepository.findAllByPointKeyIn(keys)) {
                byKey.put(p.getPointKey(), p);
            }
        }

        List<DevicePointRefMappingRequest> valid = new ArrayList<>();

        for (ParsedRow pr : parsedRows) {
            if (pr.pointKey().isEmpty()) {
                failed.add(fail(pr, "point_key 가 비어 있습니다."));
                continue;
            }
            DevicePoint point = byKey.get(pr.pointKey());
            if (point == null) {
                failed.add(fail(pr, "존재하지 않는 point_key 입니다."));
                continue;
            }

            boolean hasDevice = !pr.refDeviceCode().isEmpty();
            boolean hasPoint = !pr.refPointCode().isEmpty();
            if (hasDevice ^ hasPoint) {
                failed.add(fail(pr, "ref_device_code 와 ref_point_code 는 함께 입력하거나 모두 비워야 합니다."));
                continue;
            }

            // 1:N 팬아웃 허용 — 같은 ref 조합을 여러 point 가 가질 수 있어 파일내/DB 조합중복 검사 제거(both-or-neither 유지)

            // data_source 는 선택 입력 — 비어 있으면 null 로 넘겨 기존 값을 유지한다.
            String dataSource = null;
            if (!pr.dataSource().isEmpty()) {
                if (PointDataSource.fromCode(pr.dataSource()) == null) {
                    failed.add(fail(pr, "지원하지 않는 data_source 입니다: " + pr.dataSource()));
                    continue;
                }
                dataSource = pr.dataSource();
            }

            valid.add(new DevicePointRefMappingRequest(
                    point.getPointId(),
                    hasDevice ? pr.refDeviceCode() : null,
                    hasPoint ? pr.refPointCode() : null,
                    dataSource
            ));
        }

        int applied = 0;
        if (!valid.isEmpty()) {
            try {
                deviceService.updateDevicePointRefMappings(valid);
                applied = valid.size();
            } catch (RuntimeException e) {
                // 사전 검증(존재/both-or-neither/파일내·DB 중복)으로 대부분 걸러지므로 여기 도달은 경합 등 예외.
                // @Transactional 이므로 유효 배치 전체가 롤백된다. 부분 커밋을 남기지 않도록 예외를 전파한다.
                log.warn("포인트 매핑 배치 반영 실패(전체 롤백): {}", e.getMessage());
                throw new IllegalStateException("매핑 반영 중 오류가 발생했습니다: " + e.getMessage(), e);
            }
        }

        return new PointMappingImportResult(total, applied, failed);
    }

    /**
     * category/keyword 범위 기준 매핑 통계.
     * <p>count 쿼리를 재활용한다. mapped 는 기존 {@code countMappedForRefMapping},
     * total 은 신규 {@code countForRefMapping} 을 사용한다.</p>
     */
    @Transactional(readOnly = true)
    public PointMappingStatsResponse getStats(Long categoryId, String keyword) {
        String kw = normalizeKeyword(keyword);
        List<Long> categoryIds = deviceCategoryCascadeService.resolveSmallCategoryIdsForFilter(categoryId);
        if (categoryIds != null && categoryIds.isEmpty()) {
            return new PointMappingStatsResponse(0, 0);
        }
        long total = devicePointRepository.countForRefMapping(null, categoryIds, kw);
        long mapped = devicePointRepository.countMappedForRefMapping(null, categoryIds, kw);
        return new PointMappingStatsResponse(total, mapped);
    }

    private Map<String, Integer> resolveColumnIndex(Row headerRow, DataFormatter formatter) {
        Map<String, Integer> colIndex = new HashMap<>();
        for (int c = headerRow.getFirstCellNum(); c < headerRow.getLastCellNum(); c++) {
            String name = trimToEmpty(readCell(headerRow, c, formatter));
            if (!name.isEmpty() && !colIndex.containsKey(name)) {
                colIndex.put(name, c);
            }
        }
        return colIndex;
    }

    private void requireColumn(Map<String, Integer> colIndex, String name) {
        if (!colIndex.containsKey(name)) {
            throw new IllegalArgumentException("필수 헤더 컬럼이 없습니다: " + name);
        }
    }

    private String readCell(Row row, int col, DataFormatter formatter) {
        if (col < 0) {
            return "";
        }
        Cell cell = row.getCell(col);
        if (cell == null) {
            return "";
        }
        return formatter.formatCellValue(cell);
    }

    private PointMappingImportResult.FailedRow fail(ParsedRow pr, String reason) {
        return new PointMappingImportResult.FailedRow(pr.rowNumber(), pr.pointKey(), reason);
    }

    private static String normalizeKeyword(String keyword) {
        return keyword != null && !keyword.isBlank() ? keyword.trim() : null;
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }

    /** 장비 카테고리 경로("대 > 중 > 소"). 부모 체인을 거슬러 조립(readOnly 트랜잭션 내 lazy 로딩). */
    private static String categoryPath(Device device) {
        if (device == null || device.getCategory() == null) {
            return "";
        }
        java.util.Deque<String> parts = new java.util.ArrayDeque<>();
        DeviceCategory cur = device.getCategory();
        int guard = 0;
        while (cur != null && guard++ < 8) {
            if (cur.getCategoryName() != null) {
                parts.addFirst(cur.getCategoryName());
            }
            cur = cur.getParent();
        }
        return String.join(" > ", parts);
    }

    private record ParsedRow(
            int rowNumber,
            String pointKey,
            String refDeviceCode,
            String refPointCode,
            /** 빈 문자열이면 "변경 없음"(기존 값 유지) */
            String dataSource
    ) {
    }
}
