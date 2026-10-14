package com.mpole.hana.digitaltwin.device.service;

import com.mpole.hana.digitaltwin.device.entity.Device;
import com.mpole.hana.digitaltwin.device.entity.DeviceCategory;
import com.mpole.hana.digitaltwin.device.repository.DeviceRepository;
import com.mpole.hana.digitaltwin.unityAsset.entity.UnityAsset;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * 장비 목록 엑셀(xlsx) 다운로드 전용 서비스.
 * <p>{@link DevicePointMappingExcelService} 와 동일한 POI 패턴. 목록 조회는 기존
 * {@link DeviceRepository#searchDevices}(unpaged) 를 재사용해 화면 필터(category/keyword)와 동일 범위로 내려받는다.</p>
 */
@Service
@RequiredArgsConstructor
public class DeviceExcelService {

    private static final String[] HEADERS = {"장비이름", "표시명", "설명", "카테고리", "3D에셋명"};

    private final DeviceRepository deviceRepository;
    private final DeviceCategoryCascadeService deviceCategoryCascadeService;

    @Transactional(readOnly = true)
    public byte[] buildDeviceListExcel(Long categoryId, String keyword) {
        String kw = keyword != null && !keyword.isBlank() ? keyword.trim() : null;
        List<Long> categoryIds = deviceCategoryCascadeService.resolveSmallCategoryIdsForFilter(categoryId);

        List<Device> devices;
        if (categoryIds != null && categoryIds.isEmpty()) {
            devices = List.of();
        } else {
            // 엑셀은 제어 유무와 무관하게 전체를 내보낸다(hasCommandPoint=null).
            devices = deviceRepository.searchDevices(kw, categoryIds, null, Pageable.unpaged()).getContent();
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            XSSFSheet sheet = workbook.createSheet("장비목록");

            XSSFCellStyle headerStyle = workbook.createCellStyle();
            XSSFFont headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (Device d : devices) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(nvl(d.getDeviceName()));
                row.createCell(1).setCellValue(nvl(d.getDeviceDisplayName()));
                row.createCell(2).setCellValue(nvl(d.getDescription()));
                row.createCell(3).setCellValue(categoryPath(d));
                row.createCell(4).setCellValue(assetName(d));
            }

            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("장비 목록 엑셀 생성에 실패했습니다.", e);
        }
    }

    /** 3D 에셋명 — 카테고리 기본 에셋(defaultAsset) 기준(DeviceResponse 와 동일). */
    private static String assetName(Device device) {
        DeviceCategory cat = device.getCategory();
        UnityAsset asset = cat != null ? cat.getDefaultAsset() : null;
        return asset != null ? nvl(asset.getAssetName()) : "";
    }

    /** 카테고리 경로("대 > 중 > 소") — 부모 체인 조립(readOnly 트랜잭션 내 lazy 로딩). */
    private static String categoryPath(Device device) {
        if (device == null || device.getCategory() == null) {
            return "";
        }
        Deque<String> parts = new ArrayDeque<>();
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

    private static String nvl(String value) {
        return value == null ? "" : value;
    }
}
