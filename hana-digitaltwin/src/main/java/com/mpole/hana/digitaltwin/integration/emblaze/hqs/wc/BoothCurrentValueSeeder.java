package com.mpole.hana.digitaltwin.integration.emblaze.hqs.wc;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 재실센서 장비의 {@code tbl_device_current_value} 행을 기동 시 한 번 깔아 둔다.
 *
 * <p><b>왜 필요한가</b> — 행이 없으면 조회가 {@code null} 을 받고, "값이 아직 안 온 부스"와
 * "센서가 죽은 부스"를 구분할 근거가 사라진다. 행을 미리 만들어 두면
 * {@code updated_at} 이 항상 존재해 신선도({@code occupancy-stale-seconds}) 판정을 걸 수 있고,
 * 관리자 화면에서도 장비가 값 없는 유령으로 보이지 않는다.</p>
 *
 * <p><b>덮어쓰기 위험 없음</b> — {@code ON CONFLICT DO NOTHING} 이라 이미 값이 있는 부스는
 * 건드리지 않는다. 기동할 때마다 돌아도 안전하고, 부스를 추가한 뒤 재기동하면 그 장비만 채워진다.</p>
 *
 * <p>{@link ApplicationReadyEvent} 를 쓰는 이유 — {@code @PostConstruct} 시점에는 아직
 * 트랜잭션 인프라·DataSource 준비가 보장되지 않는다.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BoothCurrentValueSeeder {

    private final DeviceWcBoothRepository deviceWcBoothRepository;

    // 화장실 부스 초기화 작업 진행....
    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        try {
            int created = deviceWcBoothRepository.seedBoothOccupancyRows();
            if (created > 0) {
                log.info("부스 재실 초기행 {}건 생성(occ=0)", created);
            } else {
                log.debug("부스 재실 초기행 생성 없음 — 이미 전부 존재");
            }
        } catch (Exception e) {
            // 초기행이 없어도 조회는 0(비어있음)으로 동작한다 — 기동을 막지 않는다.
            log.error("부스 재실 초기행 생성 실패: {}", e.getMessage(), e);
        }
    }
}
