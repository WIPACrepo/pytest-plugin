package icecube.daq.maven.plugin;

import java.io.File;
import java.io.PrintStream;

import java.util.ArrayList;
import java.util.Iterator;

class TraceFrame
{
    private File file;
    private int line;
    private String test;
    private String srcLine;

    TraceFrame(String name, int line, String test)
    {
        this.file = new File(name);
        this.line = line;
        this.test = test;
    }

    void dump(PrintStream out)
    {
        out.println("  File \"" + file.getName() + "\", line " + line +
                    ", in " + test);
        if (srcLine != null) {
            out.println("    " + srcLine);
        }
    }

    boolean isPartial()
    {
        return test == null;
    }

    boolean isSkippable()
    {
        return file.getName().startsWith("unittest.py");
    }

    void setSource(String line)
    {
        if (srcLine != null) {
            throw new Error("Multiple source lines for " + file +
                            " test " + test + ": \"" + srcLine + "\" and \"" +
                            line + "\"");
        }

        srcLine = line;
    }

    void setTest(String test)
    {
        if (this.test != null) {
            throw new Error("Multiple test names for " + file + ": \"" +
                            this.test + "\" and \"" + test + "\"");
        }

        this.test = test;
    }
}

/**
 * Erroneous/failure data from a Python unit test.
 */
class ErrorData
{
    /** <tt>true</tt> if this represents an error (as opposed to a failure) */
    private boolean isError;
    /** exception name */
    private String excName;
    /** exception test */
    private String excText;
    /** Traceback data */
    private ArrayList trace;
    /** Current trace stack entry. */
    private TraceFrame curFrame;

    ErrorData(String type)
    {
        if (type.equalsIgnoreCase("error")) {
            isError = true;
        } else if (type.equalsIgnoreCase("failure")) {
            isError = false;
        } else {
            throw new Error("Unknown error type \"" + type + "\"");
        }
    }

    ErrorData(boolean isError)
    {
        this.isError = isError;
    }

    void addTraceFile(String name, int line, String test)
    {
        curFrame = new TraceFrame(name, line, test);

        if (trace == null) {
            trace = new ArrayList();
        }

        trace.add(curFrame);
    }

    void addTraceSource(String line)
    {
        if (curFrame == null) {
            throw new Error("Found source line without active frame");
        }

        curFrame.setSource(line);
    }

    void dump(String className, String name, PrintStream out)
    {
        String errType;
        if (isError) {
            errType = "ERROR";
        } else {
            errType = "FAIL";
        }

        out.println("===================================" +
                    "===================================");
        out.println(errType + ": " + name + " (" + className + ")");
        out.println("-----------------------------------" +
                    "-----------------------------------");
        out.println("Traceback (most recent call last):");

        for (Iterator it = trace.iterator(); it.hasNext(); ) {
            TraceFrame frame = (TraceFrame) it.next();
            if (!frame.isSkippable()) {
                frame.dump(out);
            }
        }

        int dot = excName.lastIndexOf('.');
        String excBase;
        if (dot < 0) {
            excBase = excName;
        } else {
            excBase = excName.substring(dot + 1);
        }

        if (excText == null) {
            out.println(excBase);
        } else {
            out.println(excBase + ": " + excText);
        }
    }

    /**
     * Has the exception text has been set?
     *
     * @return <tt>true</tt> if there is text for this exception
     */
    boolean hasExceptionText()
    {
        return excText != null;
    }

    /**
     * Is the current frame only partially initialized?
     *
     * @return <tt>true</tt> if the current frame has missing data
     */
    boolean isFramePartial()
    {
        return curFrame != null && curFrame.isPartial();
    }

    /**
     * Set the exception name.
     *
     * @param name exception name
     */
    void setExceptionName(String name)
    {
        if (excName != null) {
            throw new Error("Multiple exception names found");
        }

        excName = name;
    }

    /**
     * Set the exception text.
     *
     * @param text exception text
     */
    void setExceptionText(String text)
    {
        if (excText != null) {
            throw new Error("Multiple exception texts found (\"" + excText +
                            "\" and \"" + text + "\")");
        }

        excText = text;
    }

    /**
     * Set the test name for the current frame.
     *
     * @param test test name
     */
    void setFrameTest(String test)
    {
        curFrame.setTest(test);
    }
}
