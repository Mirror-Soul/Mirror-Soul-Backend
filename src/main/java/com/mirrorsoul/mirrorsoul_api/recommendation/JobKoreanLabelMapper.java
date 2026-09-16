package com.mirrorsoul.mirrorsoul_api.recommendation;

import com.mirrorsoul.mirrorsoul_api.domain.enums.Job;
import org.springframework.stereotype.Component;

@Component
public class JobKoreanLabelMapper {

    public String toKoreanLabel(Job job) {
        if (job == null) {
            return "";
        }
        return switch (job) {
            case IT_TECH -> "기술 및 IT";
            case DESIGN -> "디자인";
            case PLANNING_STRATEGY -> "기획 및 전략";
            case MARKETING_PR -> "마케팅 및 PR";
            case SALES_BUSINESS -> "영업 및 비즈니스";
            case HR_RECRUITING -> "인사 및 채용";
            case FINANCE_ACCOUNTING -> "재무 및 회계";
            case OPERATIONS_CS -> "운영 및 고객지원";
            case EDUCATION -> "교육";
            case MEDICAL_HEALTHCARE -> "의료 및 헬스케어";
            case MEDIA_CONTENT -> "미디어 및 콘텐츠";
            case LEGAL_PUBLIC -> "법률 및 공공";
            case MANUFACTURING_ENGINEERING -> "제조 및 엔지니어링";
            case STUDENT -> "학생";
            case FREELANCER -> "프리랜서";
            case ETC -> "기타";
        };
    }
}
