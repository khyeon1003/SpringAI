package com.example.springai.metrics;

import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TokenUsageMetrics {

    private final DistributionSummary tokensPerQuery;
    private final AtomicLong queryCount = new AtomicLong();
    private final AtomicLong totalTokens = new AtomicLong();
    private final long averageTokenLimit;

    public TokenUsageMetrics(
            MeterRegistry meterRegistry,
            @Value("${app.metrics.token-usage.average-limit}") long averageTokenLimit) {
        if (averageTokenLimit <= 0) {
            throw new IllegalArgumentException("app.metrics.token-usage.average-limit must be greater than 0");
        }
        this.averageTokenLimit = averageTokenLimit;
        this.tokensPerQuery = DistributionSummary.builder("chat.query.tokens")
                .description("Total model tokens consumed by one chatbot query")
                .baseUnit("tokens")
                .register(meterRegistry);

        Gauge.builder("chat.query.tokens.average", this, TokenUsageMetrics::averageTokens)
                .description("Average total tokens consumed per chatbot query")
                .baseUnit("tokens")
                .register(meterRegistry);
        Gauge.builder("chat.query.tokens.average.limit", this, metrics -> metrics.averageTokenLimit)
                .description("Configured upper limit for average tokens per chatbot query")
                .baseUnit("tokens")
                .register(meterRegistry);
        Gauge.builder("chat.query.tokens.average.compliant", this, TokenUsageMetrics::isAverageCompliant)
                .description("1 when average query token usage is within the configured limit, otherwise 0")
                .register(meterRegistry);
    }

    public void record(Usage usage) {
        if (usage == null || usage.getTotalTokens() == null) {
            return;
        }

        long tokens = usage.getTotalTokens();
        if (tokens < 0) {
            return;
        }
        tokensPerQuery.record(tokens);
        totalTokens.addAndGet(tokens);
        queryCount.incrementAndGet();
    }

    private double averageTokens() {
        long count = queryCount.get();
        return count == 0 ? 0.0 : (double) totalTokens.get() / count;
    }

    private double isAverageCompliant() {
        return averageTokens() <= averageTokenLimit ? 1.0 : 0.0;
    }
}
