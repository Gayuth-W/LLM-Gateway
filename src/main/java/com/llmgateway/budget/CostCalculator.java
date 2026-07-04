package com.llmgateway.budget;

import com.llmgateway.config.GatewayProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Pure cost function: converts token usage into USD using the per-model price table
 * from configuration. Ollama is free, so prices are synthetic and exist purely so
 * budget enforcement is observable in a demo.
 *
 *   cost = inputTokens/1000 * inputPrice  +  outputTokens/1000 * outputPrice
 */
@Component
public class CostCalculator {

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
    private static final MathContext MC = MathContext.DECIMAL64;

    private final GatewayProperties props;

    public CostCalculator(GatewayProperties props) {
        this.props = props;
    }

    public BigDecimal cost(String model, long inputTokens, long outputTokens) {
        GatewayProperties.Price price = props.priceFor(model);

        BigDecimal in = BigDecimal.valueOf(inputTokens)
                .divide(THOUSAND, MC)
                .multiply(BigDecimal.valueOf(price.inputPer1k()), MC);

        BigDecimal out = BigDecimal.valueOf(outputTokens)
                .divide(THOUSAND, MC)
                .multiply(BigDecimal.valueOf(price.outputPer1k()), MC);

        return in.add(out, MC).setScale(6, RoundingMode.HALF_UP);
    }
}
