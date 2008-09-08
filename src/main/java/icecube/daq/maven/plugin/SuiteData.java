package icecube.daq.maven.plugin;

import java.io.File;
import java.io.PrintStream;

import java.text.DecimalFormat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Python unit test suite results.
 */
public class SuiteData
{
    /** Formatter for time. */
    private static final DecimalFormat timeFmt = new DecimalFormat("####0.000");

    /** Test suite name. */
    private String name;

    /** Number of tests run. */
    private int numTests;
    /** Number of erroneous test. */
    private int numErrors;
    /** Number of failed tests. */
    private int numFails;

    /** Time needed to run all the tests in the suite. */
    private double time;

    /** List of individual test case data. */
    private List cases;

    /** Standard output stream data from tests. */
    private StreamData sysOut;
    /** Standard error stream data from tests. */
    private StreamData sysErr;

    /**
     * Create test suite data holder.
     *
     * @param file test file
     */
    SuiteData(File file)
    {
        String fName = file.getName();

        int dot = fName.lastIndexOf(".");
        if (dot > 0) {
            fName = fName.substring(0, dot);
        }

        this.name = fName;
    }

    /**
     * Add a test case.
     *
     * @param caseData test case data
     */
    void addCase(CaseData caseData)
    {
        if (cases == null) {
            cases = new ArrayList();
        }

        cases.add(caseData);
    }

    /**
     * Dump test suite output to output stream.
     *
     * @param out output stream
     */
    void dump(PrintStream out)
    {
        if (sysErr != null) {
            sysErr.dump(out);
        }
        if (sysOut != null) {
            sysOut.dump(out);
        }
        if (cases != null) {
            for (Iterator iter = cases.iterator(); iter.hasNext(); ) {
                CaseData cd = (CaseData) iter.next();

                if (cd.hasOutput()) {
                    cd.dump(out);
                    out.println();
                }
            }
        }
    }

    /**
     * Does this case have output data to dump?
     *
     * @return <tt>true</tt> if dump() will produce output
     */
    boolean hasOutput()
    {
        boolean hasOut = sysErr != null || sysOut != null;
        if (!hasOut && cases != null) {
            for (Iterator iter = cases.iterator(); iter.hasNext(); ) {
                CaseData cd = (CaseData) iter.next();

                if (cd.hasOutput()) {
                    hasOut = true;
                    break;
                }
            }
        }
        return hasOut;
    }

    /**
     * Find the test case data for the specified test.
     *
     * @param className test runner class name
     * @param name test name
     *
     * @return <tt>null</tt> if test case was not found
     */
    CaseData findCase(String className, String name)
    {
        if (cases != null) {
            for (Iterator iter = cases.iterator(); iter.hasNext(); ) {
                CaseData cd = (CaseData) iter.next();

                if (cd.isMatch(className, name)) {
                    return cd;
                }
            }
        }

        return null;
    }

    /**
     * Get lines of text written to the standard error stream.
     *
     * @return error text lines
     */
    List getErrorLines()
    {
        if (sysErr == null) {
            return null;
        }

        return sysErr.getLines();
    }

    /**
     * Get the number of erroneous tests.
     *
     * @param number of errors
     */
    int getNumErrors()
    {
        return numErrors;
    }

    /**
     * Get the number of failed tests.
     *
     * @return number of failures
     */
    int getNumFailures()
    {
        return numFails;
    }

    /**
     * Get the total number of tests.
     *
     * @return total number of tests
     */
    int getNumTests()
    {
        return numTests;
    }

    /**
     * Get the total time taken to run tests.
     *
     * @return total time
     */
    double getTime()
    {
        return time;
    }

    /**
     * Get lines of text written to the standard output stream.
     *
     * @return output text lines
     */
    List getOutputLines()
    {
        if (sysOut == null) {
            return null;
        }

        return sysOut.getLines();
    }

    /**
     * Was anything written to the standard error stream?
     *
     * @return <tt>true</tt> if there are standard error lines
     */
    boolean hasErrorLines()
    {
        return sysErr != null && !sysErr.isEmpty();
    }

    /**
     * Was anything written to the standard output stream?
     *
     * @return <tt>true</tt> if there are standard output lines
     */
    boolean hasOutputLines()
    {
        return sysOut != null && !sysOut.isEmpty();
    }

    /**
     * Has this data been initialized?
     *
     * @return <tt>true</tt> if the necessary fields have been filled
     */
    boolean isInitialized()
    {
        return name != null &&
            (numTests != 0 || numErrors != 0 || numFails != 0);
    }

    /**
     * Did all the tests pass?
     *
     * @return <tt>true</tt> if no tests failed.
     */
    boolean isPassed()
    {
        return name != null && numTests != 0 && numErrors == 0 && numFails == 0;
    }

    /**
     * Set the test suite name.
     *
     * @param name test suite name
     */
    void setName(String name)
    {
        this.name = name;
    }

    /**
     * Set the number of erroneous tests.
     *
     * @param val number of errors
     */
    void setNumErrors(int val)
    {
        numErrors = val;
    }

    /**
     * Set the number of failed tests.
     *
     * @param val number of failures
     */
    void setNumFailures(int val)
    {
        numFails = val;
    }

    /**
     * Set the total number of tests.
     *
     * @param val total number of tests
     */
    void setNumTests(int val)
    {
        numTests = val;
    }

    /**
     * Set the standard error output data for the test suite.
     *
     * @param data error stream data
     */
    void setSystemErr(StreamData data)
    {
        sysErr = data;
    }

    /**
     * Set the standard output output data for the test suite.
     *
     * @param data output stream data
     */
    void setSystemOut(StreamData data)
    {
        sysOut = data;
    }

    /**
     * Set the total time used to run the test suite.
     *
     * @param val total time
     */
    void setTime(double val)
    {
        time = val;
    }

    /**
     * Summary string used for Maven output.
     *
     * @return summary string
     */
    public String summary()
    {
        return "Tests run: " + numTests +
            ", Failures: " + numFails +
            ", Errors: " + numErrors +
            ", Time elapsed: " + timeFmt.format(time) + " sec";
    }

    /**
     * Debugging representation of this data.
     *
     * @return debugging string
     */
    public String toString()
    {
        return "Suite:" + name + ",tsts=" + numTests + ",errs=" + numErrors +
            ",fail=" + numFails + ",time=" + time;
    }
}
