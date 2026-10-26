package com.bunny.rtt_api.bootstrap;

import com.bunny.rtt_api.config.AppProps;
import com.bunny.rtt_api.service.common.DemoDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class DemoDataRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataRunner.class);

    private final AppProps appProps;
    private final DemoDataService demoDataService;

    public DemoDataRunner(AppProps appProps, DemoDataService demoDataService) {
        this.appProps = appProps;
        this.demoDataService = demoDataService;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean shouldSeed = appProps.seedOnStartup()
                || args.containsOption("app.seed-on-startup")
                || args.containsOption("seed-on-startup");

        if (!shouldSeed) {
            log.info("DemoDataRunner skipped (seed-on-startup is false)");
            return;
        }

        log.info("Starting DemoDataRunner...");
        DemoDataService.ReseedResult result = demoDataService.seedDemoData();
        log.info("DemoDataRunner finished: {} notes, {} blocks, {} reminders, {} tags created.",
                result.notes(), result.blocks(), result.reminders(), result.tags());
    }
}
