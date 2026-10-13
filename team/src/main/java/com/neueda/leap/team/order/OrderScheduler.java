package com.neueda.leap.team.order;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix="app.orders",name="worker-enabled",havingValue="true",matchIfMissing=true)
public class OrderScheduler {
    private final OrderWorker worker;
    public OrderScheduler(OrderWorker worker){this.worker=worker;}
    @Scheduled(fixedDelayString="${app.orders.poll-delay-ms:5000}",initialDelayString="${app.orders.poll-delay-ms:5000}")
    public void tick(){worker.runOnce();}
}
