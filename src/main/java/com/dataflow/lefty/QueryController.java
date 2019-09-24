package com.dataflow.lefty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Created by Owen Rees-Hayward on 16/11/2016.
 */
@RestController
public class QueryController {

    private static final Logger log = LoggerFactory.getLogger(QueryController.class);

    @Autowired
    private Context context;

    private final Integer computeLock = 1;

    @RequestMapping("/compute")
    public Mono<Response> compute(@RequestBody Map<String,String> ctx) {
        synchronized (computeLock) {
            final long start = System.currentTimeMillis();
            final List results = context.compute(ctx);
            final long end = System.currentTimeMillis();

            final Response response = new Response(results, (end - start));
            return Mono.just(response);
        }
    }

    private static class Response {
        private final long millisTaken;
        private final int numResults;
        private final List results;

        public Response(final List results, final long timeTaken) {
            this.numResults = results.size();
            this.results = results;
            this.millisTaken = timeTaken;
        }

        public int getNumResults() {
            return numResults;
        }

        public List getResults() {
            return results;
        }

        public long getMillisTaken() {
            return millisTaken;
        }
    }
}
