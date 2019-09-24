package com.dataflow.lefty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

/**
 * Created by Owen Rees-Hayward on 24/10/2016.
 */
@SpringBootApplication
public class Generator {

    private static final Logger log = LoggerFactory.getLogger(Generator.class);

    private static int NUM_RECORDS = 100_000_000;

    public static void main(final String[] args) {

        final ApplicationContext appCtx = SpringApplication.run(Generator.class, args);
         final Context context = appCtx.getBean(Context.class);

        if (args.length >= 2) {
            NUM_RECORDS = Integer.parseInt(args[0]);
        }

        context.generateDataFiles(NUM_RECORDS);
    }
}
