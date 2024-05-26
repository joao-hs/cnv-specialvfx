package pt.ulisboa.tecnico.cnv.javassist.tools;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import javassist.CannotCompileException;
import javassist.CtBehavior;

/*
 * Used as a proof-of-concept that the instrumentation overhead does not justify the information gain on the image processing workload.
 */
public class ICountImageProc extends AbstractJavassistTool {

    private static Integer ninsts = 0;
    private static final AtomicLong localRequestId = new AtomicLong(-1L);

    public ICountImageProc(List<String> packageNameList, String writeDestination) {
        super(packageNameList, writeDestination);
    }

    public static void printNInsts() {
        System.out.println(String.format("%d,%d", localRequestId.incrementAndGet(), ninsts));
        ninsts = 0;
    }

    public static void incBasicBlock(int length) {
        ninsts += length;
    }

    @Override
    protected void transform(CtBehavior behavior) throws Exception {
        super.transform(behavior);
        if (behavior.getName().equals("handle")) {
            behavior.insertAfter(String.format("%s.printNInsts();", ICountImageProc.class.getName()));
        }
    }

    @Override
    protected void transform(BasicBlock block) throws CannotCompileException {
        super.transform(block);
        block.behavior.insertAt(block.line, String.format("%s.incBasicBlock(%s);", ICountImageProc.class.getName(), block.getLength()));
    }

}
