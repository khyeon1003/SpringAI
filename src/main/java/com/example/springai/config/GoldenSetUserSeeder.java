package com.example.springai.config;

import java.math.BigDecimal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 평가용 사용자 데이터를 넣는다.
 *
 * <p>`golden-set.json`의 `user_entity_and_rag` 4개 케이스는 학사정보를 요청 본문에 담지 않고
 * `userId`로 조회한다. 그 `userId`에 해당하는 행이 없으면 `UserTool`이 실패하므로,
 * 골든셋에 적힌 `userContext` 값을 그대로 `users`에 넣어 둔다.
 *
 * <p>평가 스크립트가 참조하는 매핑은 다음과 같다.
 * <pre>CHAT_EVAL_USER_IDS='{"answer-006":1,"answer-007":2,"answer-008":3,"answer-009":4}'</pre>
 *
 * <p>이 매핑을 지키려면 식별자가 1~4로 고정되어야 하므로 자동 증가에 맡기지 않고 명시적으로 넣는다.
 * 이미 사용자 데이터가 있으면 아무것도 하지 않는다.
 */
@Component
@ConditionalOnProperty(name = "app.seed.golden-set-users", havingValue = "true", matchIfMissing = true)
public class GoldenSetUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GoldenSetUserSeeder.class);

    /** golden-set.json 의 userContext 값. 순서가 곧 userId 1~4다. */
    private static final List<SeedUser> SEED_USERS = List.of(
            new SeedUser(1L, "answer-006", "4.12", "3.0", 42, 58),
            new SeedUser(2L, "answer-007", "1.85", "4.0", 45, 62),
            new SeedUser(3L, "answer-008", "3.45", "4.0", 50, 66),
            new SeedUser(4L, "answer-009", "3.80", "3.0", 40, 54));

    private final JdbcTemplate jdbcTemplate;

    public GoldenSetUserSeeder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from users", Integer.class);
        if (count != null && count > 0) {
            log.info("평가용 사용자 시딩 건너뜀 - users 테이블에 이미 {}행이 있음", count);
            return;
        }

        for (SeedUser user : SEED_USERS) {
            jdbcTemplate.update("""
                    insert into users (id, gpa, grade, general_education_credits, major_credits)
                    values (?, ?, ?, ?, ?)
                    """,
                    user.id(), new BigDecimal(user.gpa()), new BigDecimal(user.grade()),
                    user.generalEducationCredits(), user.majorCredits());
            log.info("평가용 사용자 추가 - userId={} ({}) gpa={} grade={} 교양={} 전공={}",
                    user.id(), user.goldenSetCaseId(), user.gpa(), user.grade(),
                    user.generalEducationCredits(), user.majorCredits());
        }

        // 명시적 식별자를 넣었으므로 이후 자동 증가가 충돌하지 않도록 시퀀스를 맞춘다.
        jdbcTemplate.execute("select setval('users_id_seq', (select max(id) from users))");
    }

    private record SeedUser(
            Long id,
            String goldenSetCaseId,
            String gpa,
            String grade,
            Integer generalEducationCredits,
            Integer majorCredits) {
    }
}
