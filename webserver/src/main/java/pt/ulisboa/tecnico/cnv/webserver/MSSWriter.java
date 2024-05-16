package pt.ulisboa.tecnico.cnv.webserver;

import pt.ulisboa.tecnico.cnv.javassist.tools.SpecialVFXTool;

public class MSSWriter {
    public static class MSSEntry {
        // key
        public long requestId;
        // features: will be used to predict the cost


        // instrumentation: will be used to calculate the cost
        public long nmethods;
        public long nblocks;
        public long ninsts;

        public MSSEntry(long requestId, long nmethods, long nblocks, long ninsts) {
            this.requestId = requestId;
            this.nmethods = nmethods;
            this.nblocks = nblocks;
            this.ninsts = ninsts;
        }

        public String toString() {
            return String.format("%d,%d,%d,%d", this.requestId, this.nmethods, this.nblocks, this.ninsts);
        }

        public String toJson() {
            return String.format("{\"requestId\": %d, \"nmethods\": %d, \"nblocks\": %d, \"ninsts\": %d}", this.requestId, this.nmethods, this.nblocks, this.ninsts);
        }
    }

    public static class RequestFeatures {
        
    }
    private static MSSWriter instance = new MSSWriter();

    private int retrievingInterval = 15000;

    private MSSWriter() {
    }

    public static MSSWriter getInstance() {
        return instance;
    }

    public void setInstrumentationRetrievingInterval(int interval) {
        this.retrievingInterval = interval;
    }

    public void start() {
        new Thread(() -> {
            System.out.println("Starting instrumentation retriever");
            System.out.println("requestId,nmethods,nblocks,ninsts");
            while (true) {
                try {
                    Thread.sleep(this.retrievingInterval);
                    this.handleLogEntries(SpecialVFXTool.flushLog());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void handleLogEntries(String[] logEntries) {
        // TODO: Implement this method
        // for now, just print

        for (String entry : logEntries) {
            System.out.println(entry);
        }
    }
}
