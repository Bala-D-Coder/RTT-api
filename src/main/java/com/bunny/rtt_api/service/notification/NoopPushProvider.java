package com.bunny.rtt_api.service.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.push", havingValue = "noop", matchIfMissing = true)
public class NoopPushProvider implements PushProvider {

    private static final Logger log = LoggerFactory.getLogger(NoopPushProvider.class);

    @Override
    public void sendPush(String target, String title, String body) {
        log.info("NoopPushProvider: Simulated push notification target='{}', title='{}', body='{}'",
                target, title, body);
    }

    @Override
    public String name() {
        return "noop";
    }
}
