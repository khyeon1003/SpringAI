package com.example.springai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 기동 시 OpenAI 키 설정이 의도한 값인지 확인한다.
 *
 * <p>Spring Boot는 OS 환경변수를 {@code .env}로 읽어들인 값보다 먼저 적용한다. 셸에
 * {@code OPENAI_API_KEY}가 남아 있으면 {@code .env}를 아무리 고쳐도 옛 키로 호출되고,
 * 그 결과는 401 뒤에 붙는 500 응답으로만 드러나 원인을 찾기 어렵다. 그래서 두 값이 다를 때
 * 실행 시점에 경고를 남긴다.
 */
@Component
public class ApiKeyStartupCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyStartupCheck.class);
    private static final String ENV_NAME = "OPENAI_API_KEY";
    private static final String PROPERTY_NAME = "spring.ai.openai.api-key";

    private final ConfigurableEnvironment environment;

    public ApiKeyStartupCheck(ConfigurableEnvironment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        String effectiveKey = environment.getProperty(PROPERTY_NAME);

        if (!StringUtils.hasText(effectiveKey)) {
            log.error("{}가 비어 있습니다. .env 파일에 실제 키를 넣어야 합니다.", ENV_NAME);
            return;
        }
        if (effectiveKey.startsWith("sk-your")) {
            log.error("{}가 .env.example 의 예시 값 그대로입니다. 실제 키로 교체해야 합니다.", ENV_NAME);
            return;
        }

        String fromDotEnv = valueFromDotEnv();
        if (fromDotEnv != null && !fromDotEnv.equals(effectiveKey)) {
            log.warn("""
                    OS 환경변수 {}가 .env 의 값을 덮어쓰고 있습니다.
                    지금 사용 중인 키: {} / .env 에 적힌 키: {}
                    셸에 남은 옛 키라면 `unset {}` 후 다시 실행하십시오.""",
                    ENV_NAME, masked(effectiveKey), masked(fromDotEnv), ENV_NAME);
            return;
        }

        log.info("{} 확인 - {}", ENV_NAME, masked(effectiveKey));
    }

    /** {@code spring.config.import} 로 읽어들인 .env 프로퍼티 소스에서 직접 값을 꺼낸다. */
    private String valueFromDotEnv() {
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (!source.getName().contains(".env") || !(source instanceof EnumerablePropertySource<?>)) {
                continue;
            }
            Object value = source.getProperty(ENV_NAME);
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }

    private String masked(String key) {
        return key.length() <= 12 ? "****" : key.substring(0, 8) + "…" + key.substring(key.length() - 4);
    }
}
