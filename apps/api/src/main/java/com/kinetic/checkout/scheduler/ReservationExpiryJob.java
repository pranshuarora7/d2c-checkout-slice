package com.kinetic.checkout.scheduler;

import com.kinetic.checkout.service.OrderLifecycleService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.expiry.enabled", havingValue = "true", matchIfMissing = true)
public class ReservationExpiryJob {
    private final OrderLifecycleService lifecycle;

    public ReservationExpiryJob(OrderLifecycleService lifecycle) {
        this.lifecycle = lifecycle;
    }

    @Scheduled(fixedDelayString = "${app.expiry.interval-ms:10000}")
    public void expire() {
        lifecycle.expireDue();
    }
}
