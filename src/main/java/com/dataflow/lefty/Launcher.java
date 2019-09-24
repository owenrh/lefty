package com.dataflow.lefty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

/**
 * Created by owen on 24/10/2016.
 */
@SpringBootApplication
public class Launcher {

    private static final Logger log = LoggerFactory.getLogger(Launcher.class);

    private static int NUM_RECORDS = 100_000_000;
    private static int NUM_PRE_ADDRESS_RUNS = 0;
    private static int NUM_WARM_RUNS = 0;

    public static void main(final String[] args) {

        final ApplicationContext appCtx = SpringApplication.run(Launcher.class, args);
        final Context context = appCtx.getBean(Context.class);

        if (args.length >= 1) {
            NUM_RECORDS = Integer.parseInt(args[0]);

            if (args.length >= 2) {
                NUM_PRE_ADDRESS_RUNS = Integer.parseInt(args[1]);
            }

            if (args.length >= 3) {
                NUM_WARM_RUNS = Integer.parseInt(args[2]);
            }
        }

        context.run(NUM_PRE_ADDRESS_RUNS, NUM_WARM_RUNS, NUM_RECORDS);
    }
}
