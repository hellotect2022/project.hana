package com.mpole.hana.digitaltwin.sop.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tbl_sop_step", uniqueConstraints = {
        @UniqueConstraint(name = "uk_sop_step_no", columnNames = {"template_id", "step_no"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SopStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "step_id")
    private Long stepId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private SopTemplate template;

    @Column(name = "step_no", nullable = false)
    private Integer stepNo;

    @Column(nullable = false)
    private String title;

    private String subtitle;

    @Column(name = "require_all_items_complete")
    @Builder.Default
    private Boolean requireAllItemsComplete = false;

    @Column(name = "show_complete_button")
    @Builder.Default
    @Comment("스텝 우측 상단 완료 버튼")
    private Boolean showCompleteButton = true;

    @OneToMany(mappedBy = "step", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<SopStepItem> items = new ArrayList<>();
}
