package com.ewallet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Secure E-Wallet & Transaction Ledger
 *
 * <p>Entry point. Notable cross-cutting concerns enabled here:
 * <ul>
 *   <li>{@code @EnableJpaAuditing}  — auto-populate createdAt / updatedAt via {@link org.springframework.data.annotation.CreatedDate}</li>
 *   <li>{@code @EnableCaching}      — in-memory Caffeine cache backs idempotency key deduplication</li>
 *   <li>{@code @EnableAsync}        — async audit log writes keep transaction hot-path latency low</li>
 * </ul>
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableCaching
@EnableAsync
public class EWalletApplication {

    public static void main(String[] args) {
        SpringApplication.run(EWalletApplication.class, args);
    }
}
