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

    // TODO: This is a temporary solution for the checkpoint submission. Remove after.
    private static final AtomicLong localRequestId = new AtomicLong(-1L);

    public SpecialVFXTool(List<String> packageNameList, String writeDestination) {
        super(packageNameList, writeDestination);
    }

    public static void registerRequest(HttpExchange t) {
        List<String> headersValue = t.getRequestHeaders().get("X-Request-Id");
        Long requestId;
        if (headersValue == null) {
            requestId = localRequestId.incrementAndGet();
            threadToRequest.put(Thread.currentThread().getId(), requestId);
            requestIdToFeatures.put(requestId, "");
            return;
        }
        requestId = Long.parseLong(headersValue.get(0));
        threadToRequest.put(Thread.currentThread().getId(), requestId);
        headersValue = t.getRequestHeaders().get("X-Features");
        if (headersValue == null) {
            requestIdToFeatures.put(requestId, "");
            return;
        }
        requestIdToFeatures.put(requestId, String.join(",", headersValue));
    }

    public static Long getRequestId() {
        // Since each request is handled by a new thread, we can use the thread ID for the request ID.
        return threadToRequest.get(Thread.currentThread().getId());
    }

    public static void incBasicBlock(int length) {
        Long requestId = getRequestId();
        if (requestId == null) {
            return;
        }
        ninsts.merge(requestId, (long) length, Long::sum);
    }

    public static void logRequest() {
        Long requestId = getRequestId();
        if (requestId == null) {
            return;
        }
        String entry;
        entry = String.format("%d|%s|%d", requestId, requestIdToFeatures.get(requestId), Math.round(ninsts.get(requestId) / 1e6));
        ninsts.remove(requestId);
        log.add(entry);

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
        if (behavior.getName().equals("handle")) {
            behavior.insertAfter(String.format("%s.logRequest();", SpecialVFXTool.class.getName()));
        }
    }

    @Override
    protected void transform(BasicBlock block) throws CannotCompileException {
        super.transform(block);
        block.behavior.insertAt(block.line, String.format("%s.incBasicBlock(%s);", SpecialVFXTool.class.getName(), block.getLength()));
    }

}
