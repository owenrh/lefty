package com.dataflow.lefty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.Unsafe;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;

/**
 * Created by Owen Rees-Hayward on 01/11/2016.
 */
public class Engine {

    private static final Logger log = LoggerFactory.getLogger(Engine.class);

    private static final long MAX_DATAFILE_SIZE = 2_000_000_000L;

    private static final NumberFormat formatter = NumberFormat.getInstance();

    private static final Unsafe unsafe;
    private static long address;
    private static Datum flyweight;

    private final ForkJoinPool fjp = new ForkJoinPool();

    private final String dataDirectory;

    private int numRecords;

    static {
        try {
            final Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = (Unsafe) field.get(null);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Engine(final Datum fly, final String dataDirectory) {
        this.dataDirectory = dataDirectory;
        flyweight = fly;
        flyweight.setUnsafe(unsafe);
    }

    public final long init(final int numRecords) {
        final long requiredHeap = numRecords * flyweight.getObjectSize();
        address = unsafe.allocateMemory(requiredHeap);

        this.numRecords = numRecords;

        return address;
    }

    public void storeData() {

        final long objectSize = flyweight.getObjectSize();
        final long numBytes = objectSize * numRecords;
        long bytesRemaining = numBytes;

        int fileNum = 0;
        long startRecord = 0;
        while (bytesRemaining > 0) {
            long fileBytes = bytesRemaining;
            if (bytesRemaining > MAX_DATAFILE_SIZE) {
                fileBytes = MAX_DATAFILE_SIZE - (MAX_DATAFILE_SIZE % objectSize);
            }

            final long numRecords = fileBytes / objectSize;

            writeDataFile(fileNum, fileBytes, startRecord, numRecords);

            startRecord += numRecords;
            bytesRemaining -= fileBytes;
            fileNum++;
        }
    }

    private String getDataFilename(final int fileNum) {
        return dataDirectory + "/bytes." + fileNum + ".dat";
    }

    private void writeDataFile(final int fileNum,
                               final long fileBytes,
                               final long startRecord,
                               final long numRecords) {
        try {
            final File file = new File(getDataFilename(fileNum));
            file.createNewFile();
            final FileChannel fileChannel = new RandomAccessFile(file, "rw").getChannel();
            final MappedByteBuffer buffer = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0, fileBytes);

            final long endRecord = startRecord + numRecords;
            for (long i = startRecord; i < endRecord; i++) {
                flyweight.get(address, (int) i).store(buffer);
            }

            fileChannel.close();
        }
        catch (Exception e) {
            // TODO: hmm, don't want to be doing this
            e.printStackTrace();
        }
    }

    public int numberOfStoredRecords() {

        int fileNum = 0;
        int numberOfRecords = 0;
        String dataFileName = getDataFilename(fileNum);
        File file = new File(dataFileName);

        while (file.exists()) {
            numberOfRecords += (file.length() / flyweight.getObjectSize());

            fileNum++;
            dataFileName = getDataFilename(fileNum);
            file = new File(dataFileName);
        }

        return numberOfRecords;
    }

    public void loadDataFilesMapped() {

        int fileNum = 0;
        long startRecord = 0;
        String dataFileName = getDataFilename(fileNum);
        File file = new File(dataFileName);

        while (file.exists()) {

            log.info("Loading datafile> " + dataFileName);
            long start = new Date().getTime();
            try {
                System.gc();
                final long fileBytes = file.length();

                final RandomAccessFile dataFile = new RandomAccessFile(file, "r");
                final FileChannel fileChannel = dataFile.getChannel();
                final MappedByteBuffer buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileBytes);

                final long numRecords = fileBytes / flyweight.getObjectSize();
                final long endRecord = startRecord + numRecords - 1; // FIXME: remove dodgey minus 1 hack!!!!!!!
                for (long i = startRecord; i < endRecord; i++) {
                    flyweight.get(address, (int) i).load(buffer);
                }

                fileChannel.close();
                dataFile.close();
                startRecord += numRecords;
            }
            catch (Exception e) {
                e.printStackTrace();
            }

            long loadMillis = new Date().getTime() - start;
            log.info("Load time> " + formatter.format(loadMillis) + " ms");

            fileNum++;
            dataFileName = getDataFilename(fileNum);
            file = new File(dataFileName);
        }
    }

    public void loadDataFilesBuffered() {

        int fileNum = 0;
        long startRecord = 0;
        String dataFileName = getDataFilename(fileNum);
        File file = new File(dataFileName);
        while (file.exists()) {

            log.info("Loading datafile> " + dataFileName);
            long start = new Date().getTime();
            try {
                System.gc();
                final long fileBytes = file.length();

                final DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));

                final long numRecords = fileBytes / flyweight.getObjectSize();
                final long endRecord = startRecord + numRecords;
                for (long i = startRecord; i < endRecord; i++) {
                    flyweight.get(address, (int) i).load(dis);
                }

                dis.close();
                startRecord += numRecords;
            }
            catch (Exception e) {
                e.printStackTrace();
            }

            long loadMillis = new Date().getTime() - start;
            log.info("Load time> " + formatter.format(loadMillis) + " ms");

            fileNum++;
            dataFileName = getDataFilename(fileNum);
            file = new File(dataFileName);
        }
    }

    public final void executeProcessor(final Processor[] processors, final List results) {

        try {
            final List<Callable<List>> tasks = createTasks(processors);

            long start = System.currentTimeMillis();
            final List<Future<List>> futures = fjp.invokeAll(tasks);

            for (Future<List> future : futures) {
                results.addAll(future.get());
            }

            long duration = System.currentTimeMillis() - start;
            log.info("Run - duration " + duration + "ms: Found " + results.size() + " results");
        }
        catch (Throwable t) { // TODO: sort out exception handling
            t.printStackTrace();
        }
    }

    private final List<Callable<List>> createTasks(final Processor[] processors) {
        final List<Callable<List>> tasks = new ArrayList<>();

        final int splitWidth = numRecords / Config.NUM_SPLITS;

        for (int splitIdx = 0; splitIdx < Config.NUM_SPLITS; splitIdx++) {
            final int startIdx = splitIdx * splitWidth;
            final int endIdx = startIdx + splitWidth;
            final Processor processor = processors[splitIdx];

            tasks.add(new Callable<List>() {
                @Override
                public List call() throws Exception {
                    final List results = new ArrayList();

                    for (int j = startIdx; j < endIdx; j++) {
                        final Datum fly = flyweight.get(address, j);
                        processor.processDatum(results, fly);
                    }

                    return results;
                }
            });
        }

        return tasks;
    }

    public final void destroy() {
        unsafe.freeMemory(address);
    }
}
