package pt.ulisboa.tecnico.cnv.javassist.tools;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import javassist.CannotCompileException;
import javassist.CtBehavior;

public class MappedICount extends AbstractJavassistTool {

    /*
     * Histogram of "Class.method" calls
     */
    private static final Map<String, Integer> methodCallHistogram = new HashMap<>();

    /*
     * Map BasicBlock to corresponding method call
     */
    private static final Map<String, String> basicBlockToMethod = new HashMap<>();

    /*
     * Histogram of BasicBlock calls
     */
    private static final Map<String, Integer> basicBlockHistogram = new HashMap<>();

    /*
     * Total number of executed basic blocks.
     */
    private static long nblocks = 0;

    /*
     * Total number of executed methods.
     */
    private static long nmethods = 0;

    /*
     * Total number of executed instructions.
     */
    private static long ninsts = 0;

    /*
     * Helper structure to keep last method call
     */
    private static Stack<String> lastMethodCall = new Stack<>();

    public MappedICount(List<String> packageNameList, String writeDestination) {
        super(packageNameList, writeDestination);
    }

    public static void printStatistics() {
        System.out.println(String.format("[%s] Method call histogram:", MappedICount.class.getSimpleName()));
        methodCallHistogram.forEach((k, v) -> System.out.println(String.format("[%s]     %s: %s", MappedICount.class.getSimpleName(), k, v)));
        System.out.println(String.format("[%s] BasicBlock histogram:", MappedICount.class.getSimpleName()));
        basicBlockHistogram.forEach((k, v) -> System.out.println(String.format("[%s]     %s: %s", MappedICount.class.getSimpleName(), k, v)));
        System.out.println(String.format("[%s] BasicBlock to Method mapping:", MappedICount.class.getSimpleName()));
        basicBlockToMethod.forEach((k, v) -> System.out.println(String.format("[%s]     %s: %s", MappedICount.class.getSimpleName(), k, v)));
        System.out.println(String.format("[%s] Total number of executed basic blocks: %s", MappedICount.class.getSimpleName(), nblocks));
        System.out.println(String.format("[%s] Total number of executed methods: %s", MappedICount.class.getSimpleName(), nmethods));
        System.out.println(String.format("[%s] Total number of executed instructions: %s", MappedICount.class.getSimpleName(), ninsts));
    }

    public static String getLastMethodCall() {
        if (lastMethodCall.isEmpty()) {
            return "null";
        }
        return lastMethodCall.peek();
    }

    public static void leavingFunction() {
        if (lastMethodCall.isEmpty()) {
            System.out.println(String.format("[%s] WARNING! leavingFunction is being called without a method call", MappedICount.class.getSimpleName()));
            return;
        }
        lastMethodCall.pop();
    }

    public static void calledFunction(String classDotMethod) {
        lastMethodCall.push(classDotMethod);
        methodCallHistogram.merge(classDotMethod, 1, Integer::sum);
        nmethods++;
    }

    public static void executingBasicBlock(String blockString, int length) {
        String newBlockString = String.format("%s:%s", MappedICount.getLastMethodCall(), blockString);
        basicBlockHistogram.merge(newBlockString, 1, Integer::sum);
        if (lastMethodCall.isEmpty()) {
            System.out.println(String.format("[%s] WARNING! BasicBlock %s is being executed without a method call", MappedICount.class.getSimpleName(), newBlockString));
            return;
        }
        if (basicBlockToMethod.containsKey(newBlockString) && basicBlockToMethod.get(newBlockString) != MappedICount.getLastMethodCall()) {
            System.out.println(String.format("[%s] WARNING! BasicBlock %s is being executed in different methods: %s and %s", MappedICount.class.getSimpleName(), newBlockString, basicBlockToMethod.get(newBlockString), MappedICount.getLastMethodCall()));
            return;
        }
        basicBlockToMethod.put(newBlockString, MappedICount.getLastMethodCall());
        nblocks++;
        ninsts += length;
    }

    @Override
    protected void transform(CtBehavior behavior) throws Exception {
        String classDotMethod = String.format("%s.%s", behavior.getClass().getName(), behavior.getLongName());
        behavior.insertBefore(String.format("%s.calledFunction(\"%s\");", MappedICount.class.getName(), classDotMethod));
        if (behavior.getName().equals("main")) {
            behavior.insertAfter(String.format("%s.printStatistics();", MappedICount.class.getName()));
        }
        behavior.insertAfter(String.format("%s.leavingFunction();", MappedICount.class.getName()));

        super.transform(behavior);
    }

    @Override
    protected void transform(BasicBlock block) throws CannotCompileException {
        super.transform(block);
        String blockString = String.format("%s:%s:%s", block.getPosition(), block.getLength(), block.getLine());
        block.behavior.insertAt(block.line, String.format("%s.executingBasicBlock(\"%s\", %s);", MappedICount.class.getName(), blockString, block.getLength()));
    }

}
