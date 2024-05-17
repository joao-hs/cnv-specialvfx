package pt.ulisboa.tecnico.cnv.javassist.tools;

import com.sun.net.httpserver.HttpExchange;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

import javassist.CannotCompileException;
import javassist.CtBehavior;

public class SpecialVFXTool extends AbstractJavassistTool {

    /**
     * RequestID -> Number of executed basic blocks.
     */
    private static final Map<Long, Long> nblocks = new ConcurrentHashMap<>();

    /**
     * RequestID -> Number of executed methods.
     */
    private static final Map<Long, Long> nmethods = new ConcurrentHashMap<>();

    /**
     * RequestID -> Number of executed instructions.
     */
    private static final Map<Long, Long> ninsts = new ConcurrentHashMap<>();

    /*
     * Log entry format: requestID,nmethods,nblocks,ninsts
     */
    private static final ConcurrentLinkedQueue<String> log = new ConcurrentLinkedQueue<>();
    private static final Object logLock = new Object();

    private static final Map<Long, Long> threadToRequest = new ConcurrentHashMap<>();

    private static final Map<Long, String> requestIdToFeatures = new ConcurrentHashMap<>();

    public SpecialVFXTool(List<String> packageNameList, String writeDestination) {
        super(packageNameList, writeDestination);
    }

    public static void registerRequest(HttpExchange t) {
        Long requestId = Long.parseLong(t.getRequestHeaders().get("X-Request-Id").get(0));
        threadToRequest.put(Thread.currentThread().getId(), requestId);
        requestIdToFeatures.put(requestId, t.getRequestHeaders().get("X-Features").get(0));
    }

    public static Long getRequestId() {
        // Since each request is handled by a new thread, we can use the thread ID for the request ID.
        return threadToRequest.get(Thread.currentThread().getId());
    }

    public static void incBasicBlock(int position, int length) {
        Long requestId = getRequestId();
        if (requestId == null) {
            return;
        }
        nblocks.merge(requestId, 1L, Long::sum);
        ninsts.merge(requestId, (long) length, Long::sum);
    }

    public static void incBehavior(String name) {
        Long requestId = getRequestId();
        if (requestId == null) {
            return;
        }
        nmethods.merge(requestId, 1L, Long::sum);
    }

    public static void logRequest() {
        long requestId = getRequestId();
        String entry = String.format("%d,%s,%d,%d,%d", requestId, requestIdToFeatures.get(requestId), nmethods.get(requestId), nblocks.get(requestId), ninsts.get(requestId));
        log.add(entry);
        // reset counters
        nblocks.remove(requestId);
        nmethods.remove(requestId);
        ninsts.remove(requestId);
        requestIdToFeatures.remove(requestId);
    }

    /*
     * Remove every entry from the log and return them.
     */
    public static String[] flushLog() {
        synchronized (logLock) { // prevent concurrent flushing
            int size = log.size();
            String[] logEntries = new String[size];
            for (int i = 0; i < size; i++) {
                logEntries[i] = log.poll();
            }
            
            return logEntries;
        }
    }

    @Override
    protected void transform(CtBehavior behavior) throws Exception {
        super.transform(behavior);
        behavior.insertAfter(String.format("%s.incBehavior(\"%s\");", SpecialVFXTool.class.getName(), behavior.getLongName()));

        if (behavior.getName().equals("handle")) {
            behavior.insertBefore(String.format("%s.registerRequest(he);", SpecialVFXTool.class.getName()));
            behavior.insertAfter(String.format("%s.logRequest();", SpecialVFXTool.class.getName()));
        }
    }

    @Override
    protected void transform(BasicBlock block) throws CannotCompileException {
        super.transform(block);
        block.behavior.insertAt(block.line, String.format("%s.incBasicBlock(%s, %s);", SpecialVFXTool.class.getName(), block.getPosition(), block.getLength()));
    }

}
