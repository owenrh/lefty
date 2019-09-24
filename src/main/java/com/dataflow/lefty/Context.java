package com.dataflow.lefty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.xeustechnologies.jcl.JarClassLoader;
import org.xeustechnologies.jcl.JclObjectFactory;
import org.yaml.snakeyaml.Yaml;

import javax.annotation.PreDestroy;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.text.NumberFormat;
import java.util.*;

/**
 * Created by Owen Rees-Hayward on 24/10/2016.
 */
@Component
@Profile({ "live", "generator" })
public class Context {

    private static final Logger log = LoggerFactory.getLogger(Context.class);

    private static final String CONFIGURATION_YAML = "configuration.yaml";
    private static final String DATUM_CLASSNAME = "datum-classname";
    private static final String PROCESSOR_CLASSNAME = "processor-classname";

    private static final Random random = new Random();

    private static final NumberFormat formatter = NumberFormat.getInstance();

    private String jobDirectory;
    private String dataDirectory;

    private Processor[] processors;
    private Engine flyEngine;

    @Value("${LEFTY_JOB_DIR}")
    public void setJobDirectory(final String jobDirectory) {
        this.jobDirectory = jobDirectory;
    }

    @Value("${LEFTY_DATA_DIR}")
    public void setDataDirectory(final String dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    public void run(final int numPreAddressRuns,
                    final int numWarnRuns,
                    final int numRecords) {

        log.info("Job directory set as: " + jobDirectory);

        final JarClassLoader jcl = new JarClassLoader();
        jcl.add(jobDirectory);

        final InputStream configIn = jcl.getResourceAsStream(CONFIGURATION_YAML);
        final Yaml yaml = new Yaml();
        final Map<String, String> config = (Map) yaml.load(configIn);

        final String datumClassname = config.get(DATUM_CLASSNAME);
        final String processorClassname = config.get(PROCESSOR_CLASSNAME);

        final Datum flyweight = getFly(jcl, datumClassname);

        this.flyEngine = new Engine(flyweight, dataDirectory);

        final int numberOfStoredRecords = flyEngine.numberOfStoredRecords();
        log.info("Number of records in data files is " + formatter.format(numberOfStoredRecords));

        final long address = flyEngine.init(numberOfStoredRecords);
        processors = getProcessors(jcl, processorClassname, Config.NUM_SPLITS);

        final ByteBuffer byteBuffer = ByteBuffer.allocate((int) flyweight.getObjectSize());
        byteBuffer.putLong(random.nextLong());

        // TODO: could optimize the pre-address and pre-warm by going to the byte/long level?

        // pre-address memory
        preAddressMemory(numPreAddressRuns, numRecords, flyweight, address, byteBuffer);

//        flyEngine.loadDataFilesBuffered();
        flyEngine.loadDataFilesMapped();

        // warm memory
        warmMemory(numWarnRuns, numRecords, flyweight, address, byteBuffer);

        System.gc();
    }

    public void generateDataFiles(final int numRecords) {

        final JarClassLoader jcl = new JarClassLoader();
        jcl.add(jobDirectory);

        final InputStream configIn = jcl.getResourceAsStream(CONFIGURATION_YAML);
        final Yaml yaml = new Yaml();
        final Map<String, String> config = (Map) yaml.load(configIn);

        final String datumClassname = config.get(DATUM_CLASSNAME);
        final String processorClassname = config.get(PROCESSOR_CLASSNAME);

        final Datum flyweight = getFly(jcl, datumClassname);

        this.flyEngine = new Engine(flyweight, dataDirectory);
        final long address = flyEngine.init(numRecords);
        processors = getProcessors(jcl, processorClassname, Config.NUM_SPLITS);

        final ByteBuffer byteBuffer = ByteBuffer.allocate((int) flyweight.getObjectSize());
        generateRandomData(numRecords, flyweight, address, byteBuffer);

        log.info("Storing data");
        flyEngine.storeData();
        log.info("Stored data");
    }

    private void warmMemory(final int numWarnRuns,
                            final int numRecords,
                            final Datum flyweight,
                            final long address,
                            final ByteBuffer byteBuffer) {

        log.info("Pre-warming memory - start");
        for (int h = 0; h < numWarnRuns; h++) {
            log.info("Run> " + (h + 1) + " of " + numWarnRuns);
            for (int i = 0; i < numRecords; i++) {
                byteBuffer.rewind();
                flyweight.get(address, i).store(byteBuffer);
            }
        }
        log.info("Pre-warming memory - end");
    }

    private void preAddressMemory(final int numPreAddressRuns,
                                  final int numRecords,
                                  final Datum flyweight,
                                  final long address,
                                  final ByteBuffer byteBuffer) {
        log.info("Pre-addressing memory - start");
        for (int h = 0; h < numPreAddressRuns; h++) {
            log.info("Run> " + (h + 1) + " of " + numPreAddressRuns);

            for (int i = 0; i < numRecords; i++) {
                final Datum fly = flyweight.get(address, i);
                byteBuffer.rewind();
                fly.load(byteBuffer);
            }
        }
        log.info("Pre-addressing memory - end");
    }

    private void generateRandomData(final int numRecords,
                                    final Datum flyweight,
                                    final long address,
                                    final ByteBuffer byteBuffer) {
        log.info("Generate random data - start");

        for (int i = 0; i < numRecords; i++) {
            final Datum fly = flyweight.get(address, i);
            byteBuffer.rewind();
            byteBuffer.putLong(random.nextLong());
            byteBuffer.rewind();
            fly.load(byteBuffer);
        }

        log.info("Generate random data - end");
    }

    public List compute(final Map<String,String> ctx) {
        // TODO: sort the typing of the results list?
        final List results = new ArrayList<>();

        for (int i = 0; i < processors.length; i++) {
            processors[i].setContext(ctx);
        }

        flyEngine.executeProcessor(processors, results);

        return results;
    }

    protected static Datum getFly(final JarClassLoader jcl, final String datumClassname) {
        final JclObjectFactory factory = JclObjectFactory.getInstance();
        final Object obj = factory.create(jcl, datumClassname);

        return (Datum) obj;
    }

    protected static Processor getProcessor(final JarClassLoader jcl, final String processorClassname) {
        final JclObjectFactory factory = JclObjectFactory.getInstance();
        final Object obj = factory.create(jcl, processorClassname);

        return (Processor) obj;
    }

    protected static Processor[] getProcessors(final JarClassLoader jcl,
                                               final String processorClassname,
                                               final int numProcessors) {
        final JclObjectFactory factory = JclObjectFactory.getInstance();

        final Processor[] processors = new Processor[numProcessors];
        for (int i = 0; i < numProcessors; i++) {
            processors[i] = (Processor) factory.create(jcl, processorClassname);
        }

        return processors;
    }

    @PreDestroy
    public void destroy() {
        log.info("Destroying context on shutdown");
        flyEngine.destroy();
    }
}
