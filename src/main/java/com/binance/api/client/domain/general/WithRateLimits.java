package com.binance.api.client.domain.general;

import java.util.Map;

/*
 * WithRateLimits
 *
 * @author Raigedas Radišauskas
 */
public interface WithRateLimits {
    
    Map<String, Integer> getRateLimits();
    
    default Integer getRateLimit(RateLimitType type, RateLimitInterval interval, int count) {
        if (type == RateLimitType.RAW_REQUESTS) {
            throw new UnsupportedOperationException(String.format(
                    "type %s not supported", type.getClass().getSimpleName()
            ));
        }
        return getRateLimits().get(
                (type == RateLimitType.ORDERS ? "order-count" : "used-weight")
                + "-"
                + count + interval.name().toLowerCase().charAt(0)
        );
    }

}
