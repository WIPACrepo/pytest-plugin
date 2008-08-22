package icecube.daq.maven.plugin;

import java.io.PrintStream;

/**
 * Python unit test case results.
 */
class CaseData
{
    /** Test runner class name. */
    private String className;
    /** Test name. */
    private String name;

    /** Time taken to run test. */
    private double time;

    /** Test error data. */
    private ErrorData error;
    /** Test failure data. */
    private ErrorData failure;

    /**
     * Dump test case output to output stream.
     *
     * @param out output stream
     */
    void dump(PrintStream out)
    {
        if (error != null) {
            error.dump(className, name, out);
        }
        if (failure != null) {
            failure.dump(className, name, out);
        }
    }

    /**
     * Does this case have output data to dump?
     *
     * @return <tt>true</tt> if dump() will produce output
     */
    boolean hasOutput()
    {
        return error != null || failure != null;
    }

    /**
     * Is this the data for the specified class/name pair?
     *
     * @param className test runner class name
     * @param name test name
     *
     * @return <tt>true</tt> if this is the desired data
     */
    boolean isMatch(String className, String name)
    {
        return className.equals(this.className) && name.equals(this.name);
    }

    /**
     * Set the test runner class name.
     *
     * @param className test runner class name
     */
    void setClassName(String className)
    {
        this.className = className;
    }

    /**
     * Set the error data for this test case.
     *
     * @param error error data
     */
    void setError(ErrorData error)
    {
        this.error = error;
    }

    /**
     * Set the failure data for this test case.
     *
     * @param failure failure data
     */
    void setFailure(ErrorData failure)
    {
        this.failure = failure;
    }

    /**
     * Set the test name.
     *
     * @param name test name
     */
    void setName(String name)
    {
        this.name = name;
    }

    /**
     * Set the time needed to run the test.
     *
     * @param val time
     */
    void setTime(double val)
    {
        time = val;
    }

    /**
     * Debugging representation of this data.
     *
     * @return debugging string
     */
    public String toString()
    {
        return "Case:" + className + "*" + name + ",time=" + time;
    }
}
