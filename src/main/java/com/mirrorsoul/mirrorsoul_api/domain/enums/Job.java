package com.mirrorsoul.mirrorsoul_api.domain.enums;

import io.swagger.v3.oas.annotations.media.Schema;


@Schema(description = "직업 분야")
public enum Job {

    @Schema(description = "기술 & IT")
    IT_TECH,

    @Schema(description = "디자인")
    DESIGN,

    @Schema(description = "기획 · 전략")
    PLANNING_STRATEGY,

    @Schema(description = "마케팅 · PR")
    MARKETING_PR,

    @Schema(description = "영업 · 비즈니스")
    SALES_BUSINESS,

    @Schema(description = "인사 · 채용")
    HR_RECRUITING,

    @Schema(description = "재무 · 회계")
    FINANCE_ACCOUNTING,

    @Schema(description = "운영 · 고객지원")
    OPERATIONS_CS,

    @Schema(description = "교육")
    EDUCATION,

    @Schema(description = "의료 · 헬스케어")
    MEDICAL_HEALTHCARE,

    @Schema(description = "미디어 · 콘텐츠")
    MEDIA_CONTENT,

    @Schema(description = "법률 · 공공")
    LEGAL_PUBLIC,

    @Schema(description = "제조 · 엔지니어링")
    MANUFACTURING_ENGINEERING,

    @Schema(description = "학생")
    STUDENT,

    @Schema(description = "프리랜서")
    FREELANCER,

    @Schema(description = "기타")
    ETC;

}