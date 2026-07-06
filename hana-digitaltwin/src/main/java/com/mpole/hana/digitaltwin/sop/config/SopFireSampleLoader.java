package com.mpole.hana.digitaltwin.sop.config;

import com.mpole.hana.digitaltwin.sop.dto.SopStructureDtos.*;
import com.mpole.hana.digitaltwin.sop.dto.SopTemplateCreateRequest;
import com.mpole.hana.digitaltwin.sop.dto.SopTemplateSummaryResponse;
import com.mpole.hana.digitaltwin.sop.entity.SopButtonActionType;
import com.mpole.hana.digitaltwin.sop.entity.SopItemType;
import com.mpole.hana.digitaltwin.sop.repository.SopTemplateRepository;
import com.mpole.hana.digitaltwin.sop.service.SopTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 최초 기동 시 화재감지 SOP 샘플 템플릿 등록 (없을 때만)
 */
@Component
@RequiredArgsConstructor
public class SopFireSampleLoader implements ApplicationRunner {

    private final SopTemplateRepository templateRepository;
    private final SopTemplateService sopTemplateService;

    @Override
    public void run(ApplicationArguments args) {
        if (templateRepository.existsByTemplateCode("FIRE_DETECT")) {
            return;
        }
        SopTemplateSummaryResponse created = sopTemplateService.createTemplate(
                new SopTemplateCreateRequest(
                        "FIRE_DETECT",
                        "화재감지 SOP",
                        "화재 센서 감지 시 디지털 SOP",
                        10
                )
        );
        sopTemplateService.saveStructure(created.templateId(), buildFireStructure());
    }

    private SopTemplateStructureRequest buildFireStructure() {
        return new SopTemplateStructureRequest(
                "디지털 SOP | 화재감지 이벤트",
                10,
                List.of(
                        new SopStepDto(null, 1, "화재감지 인식 (오작동 확인)", null, false, true, List.of(
                                new SopStepItemDto(null, 1, SopItemType.CHECKLIST, "화재 신호 위치 확인", null, null, true, List.of()),
                                new SopStepItemDto(null, 2, SopItemType.CHECKLIST, "CCTV 화면 및 현장 상태 확인", null, null, false, List.of()),
                                new SopStepItemDto(null, 3, SopItemType.BUTTON_GROUP, null, null, null, false, List.of(
                                        new SopButtonDto(null, "fire", "화재 발생", "danger", SopButtonActionType.TRANSITION,
                                                Map.of("toStepNo", 2), 1),
                                        new SopButtonDto(null, "false_alarm", "오작동 확인", "default", SopButtonActionType.CLOSE_SOP,
                                                Map.of("resultStatus", "FALSE_ALARM"), 2)
                                ))
                        )),
                        new SopStepDto(null, 2, "화재 상황 전파 (실제 화재 시)", null, false, true, List.of(
                                new SopStepItemDto(null, 1, SopItemType.TEXT, "사내 근무자 문자 전송", null, null, false, List.of()),
                                new SopStepItemDto(null, 2, SopItemType.CONTACT, "하나금융그룹 HQ", null,
                                        Map.of("phone", "000-000-0000"), false, List.of())
                        )),
                        new SopStepDto(null, 3, "화재 현장 출동 및 신고", null, false, true, List.of(
                                new SopStepItemDto(null, 1, SopItemType.TEXT, "유관 기관 신고", null, null, false, List.of())
                        )),
                        new SopStepDto(null, 4, "상황처리", null, false, true, List.of(
                                new SopStepItemDto(null, 1, SopItemType.ACTION, "상황 종료 방송 송출", null,
                                        Map.of("executor", "BROADCAST_END"), false, List.of())
                        ))
                ),
                List.of(
                        new SopTransitionDto(1, "fire", 2, null),
                        new SopTransitionDto(1, "false_alarm", null, "FALSE_ALARM")
                )
        );
    }
}
