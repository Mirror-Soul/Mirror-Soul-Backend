package com.mirrorsoul.mirrorsoul_api.common.csvCrawling;

import com.opencsv.CSVReader;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RegionDataInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM region", Integer.class);
        if (count != null && count > 0) {
            return;
        }

        ClassPathResource resource = new ClassPathResource("data/regions.csv");

        try (CSVReader csvReader = new CSVReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)
        )) {
            List<Object[]> batchArgs = new ArrayList<>();
            String[] row;
            boolean isFirstRow = true;

            while ((row = csvReader.readNext()) != null) {
                if (isFirstRow) {
                    isFirstRow = false;
                    continue;
                }

                String lawdCd = safeValue(row, 0);            // 법정동코드
                String sidoName = safeValue(row, 1);          // 시도명
                String sigunguName = safeValue(row, 2);       // 시군구명
                String eupmyeondongName = safeValue(row, 3);  // 읍면동명
                String riName = safeValue(row, 4);            // 리명
                String deletedAt = safeValue(row, 7);         // 삭제일자

                // 필수값 없으면 제외
                if (isBlank(lawdCd) || isBlank(sidoName) || isBlank(sigunguName) || isBlank(eupmyeondongName)) {
                    continue;
                }

                // 삭제된 법정동 제외
                if (!isBlank(deletedAt)) {
                    continue;
                }

                // 리 단위 데이터 제외
                if (!isBlank(riName)) {
                    continue;
                }

                // 시군구명 띄어쓰기 보정
                sigunguName = convertSigunguName(sigunguName);

                batchArgs.add(new Object[]{
                        lawdCd,
                        sidoName,
                        sigunguName,
                        eupmyeondongName
                });
            }

            jdbcTemplate.batchUpdate("""
                INSERT INTO region (lawd_cd, sido_name, sigungu_name, eupmyeondong_name)
                VALUES (?, ?, ?, ?)
            """, batchArgs);
        }
    }

    private String safeValue(String[] row, int index) {
        if (row.length <= index || row[index] == null) {
            return null;
        }
        String value = row[index].trim();
        return value.isEmpty() ? null : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String convertSigunguName(String sigunguName) {
        if (sigunguName == null) {
            return null;
        }

        return switch (sigunguName) {
            case "고양시덕양구" -> "고양시 덕양구";
            case "고양시일산동구" -> "고양시 일산동구";
            case "고양시일산서구" -> "고양시 일산서구";

            case "성남시분당구" -> "성남시 분당구";
            case "성남시수정구" -> "성남시 수정구";
            case "성남시중원구" -> "성남시 중원구";

            case "수원시권선구" -> "수원시 권선구";
            case "수원시영통구" -> "수원시 영통구";
            case "수원시장안구" -> "수원시 장안구";
            case "수원시팔달구" -> "수원시 팔달구";

            case "안산시단원구" -> "안산시 단원구";
            case "안산시상록구" -> "안산시 상록구";

            case "안양시동안구" -> "안양시 동안구";
            case "안양시만안구" -> "안양시 만안구";

            case "용인시기흥구" -> "용인시 기흥구";
            case "용인시수지구" -> "용인시 수지구";
            case "용인시처인구" -> "용인시 처인구";

            case "전주시덕진구" -> "전주시 덕진구";
            case "전주시완산구" -> "전주시 완산구";

            case "창원시마산합포구" -> "창원시 마산합포구";
            case "창원시마산회원구" -> "창원시 마산회원구";
            case "창원시성산구" -> "창원시 성산구";
            case "창원시의창구" -> "창원시 의창구";
            case "창원시진해구" -> "창원시 진해구";

            case "천안시동남구" -> "천안시 동남구";
            case "천안시서북구" -> "천안시 서북구";

            case "청주시상당구" -> "청주시 상당구";
            case "청주시서원구" -> "청주시 서원구";
            case "청주시청원구" -> "청주시 청원구";
            case "청주시흥덕구" -> "청주시 흥덕구";

            case "포항시남구" -> "포항시 남구";
            case "포항시북구" -> "포항시 북구";

            case "부천시원미구" -> "부천시 원미구";
            case "부천시소사구" -> "부천시 소사구";
            case "부천시오정구" -> "부천시 오정구";

            default -> sigunguName;
        };
    }
}
