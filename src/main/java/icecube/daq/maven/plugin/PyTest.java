package icecube.daq.maven.plugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

/**
 * Run Python unit tests.
 *
 * @goal pytest
 * @phase test
 */
public class PyTest
    extends AbstractMojo
{
    /**
     * Python executable path.
     *
     * @parameter expression="python"
     */
    private String pythonExecutable;

    /**
     * Python source directory.
     *
     * @parameter expression="src/main/python"
     */
    private String sourceDirectory;

    /**
     * Python unit test directory.
     *
     * @parameter expression="src/test/python"
     */
    private String testDirectory;

    /**
     * Should we save test output to a file?
     *
     * Note that the value is forced to 'false' until reporting is implemented
     *
     * @parameter expression="${pytest.useFile}" default-value="false"
     */
    private boolean pyUseFile;

    /**
     * Honor the Surefire version of pytest.useFile.
     *
     * @parameter expression="${surefire.useFile}"
     */
    private boolean useFile;

    /**
     * Name of test to run.
     *
     * @parameter expression="${test}"
     */
    private String testName;

    private static File buildPath(String dir, String defaultDir)
    {
        if (dir == null) {
            dir = defaultDir;
        }

        File path = new File(dir);
        if (!path.isAbsolute()) {
            path = new File(System.getProperty("user.dir"), dir);
        }

        return path;
    }

    private void dumpOutput(List lines, PrintStream out)
    {
        for (Iterator it = lines.iterator(); it.hasNext(); ) {
            out.println(it.next());
        }
    }

    /**
     * Run Python unit tests.
     *
     * @throws MojoExecutionException if tests could not be executed
     * @throws MojoFailureException if one or more tests failed
     */
    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        ArrayList pyFiles = new ArrayList();

        // if surefire.useFile is false, try pytest.useFile
        if (pyUseFile) {
            useFile = true;
        }

        File srcPath = buildPath(sourceDirectory, "src/main/python");
        if (!srcPath.exists()) {
            throw new MojoExecutionException("Source directory \"" + srcPath +
                                             "\" does not exist");
        }

        File testPath = buildPath(testDirectory, "src/test/python");
        if (!srcPath.exists()) {
            throw new MojoExecutionException("Test directory \"" + testPath +
                                             "\" does not exist");
        }

        PyFilter pyFilter = new PyFilter();

        findTests(pyFilter, testPath, pyFiles);

        HashMap allDirs = getSourcePaths(pyFilter, srcPath);
        for (Iterator it = pyFiles.iterator(); it.hasNext(); ) {
            File f = (File) it.next();

            if (!allDirs.containsKey(f.getParent())) {
                allDirs.put(f.getParent(), f.getParent());
            }

            final String fName = f.getName();

            String name = fName.toLowerCase();
            if (!name.startsWith("test") &&
                !(name.endsWith("test") || name.endsWith("test.py")))
            {
                it.remove();
            } else if (testName != null) {
                if (!fName.startsWith(testName)) {
                    it.remove();
                } else if (!fName.equals(testName) &&
                           fName.charAt(testName.length()) != '.')
                {
                    it.remove();
                }
            }
        }

        String path = TestRunner.buildPath(allDirs.keySet());

        int totTests = 0;
        int totFails = 0;
        int totErrs = 0;

        ArrayList failed = new ArrayList();
        for (Iterator it = pyFiles.iterator(); it.hasNext(); ) {
            File f = (File) it.next();

            System.out.println("Running " + f.getName());
            TestRunner runner = new TestRunner(pythonExecutable, f);
            try {
                // try running tests using xmlrunner
                runner.runTests(path, "-x");
                // if that failed, use whatever test runner is available
                if (runner.isBadOption()) {
                    runner.reset();
                    runner.runTests(path, "-v");
                }

                if (runner.hasErrorLines()) {
                    getLog().error("!! Unexpected output" +
                                   " on standard error stream !!");
                }

                List outLines = runner.getOutputLines();

                SuiteData data = new SuiteData(f);

                try {
                    new PyTestParser(outLines, data);
                } catch (PyTestException pte) {
                    data = null;
                    getLog().error("Couldn't parse output from " + f.getName(),
                                   pte);
                }

                if (useFile) {
                    getLog().error("XML output is not implemented");
                } else {
                    if (data != null) {
                        data.dump(System.out);
                    } else {
                        if (runner.hasOutputLines()) {
                            dumpOutput(runner.getOutputLines(), System.out);
                        }
                        if (runner.hasErrorLines()) {
                            dumpOutput(runner.getErrorLines(), System.err);
                        }
                    }
                }

                if (data != null) {
                    System.out.println(data.summary());
                    totTests += data.getNumTests();
                    totFails += data.getNumFailures();
                    totErrs += data.getNumErrors();
                }

                if (data == null || !data.isPassed()) {
                    failed.add(f);
                }
            } catch (PyTestException pte) {
                getLog().error("Couldn't run test " + f, pte);
            }
        }

        System.out.println();
        System.out.println("Results :");

        if (failed.size() > 0) {
            System.out.println();
            System.out.println("Failed tests:");
            for (Iterator it = failed.iterator(); it.hasNext(); ) {
                System.out.println("  "  + it.next());
            }
        }

        System.out.println();
        System.out.println("Tests run: " + totTests + ", Failures: " +
                           totFails + ", Errors: " + totErrs);

        if (failed.size() > 0) {
            throw new MojoFailureException("Test failed");
        }
    }

    /**
     * Find all Python files matching 'filter' under 'dir'
     * or its subdirectories, and return the result in 'fileList'.
     *
     * @param filter file filter
     * @param dir top directory
     * @param fileList list of files found
     */
    private void findTests(FileFilter filter, File dir, List fileList)
    {
        File[] list = dir.listFiles(filter);
        if (list == null) {
            getLog().error("Didn't find any files in " + dir);
            return;
        }

        for (int i = 0; i < list.length; i++) {
            if (list[i].isDirectory()) {
                findTests(filter, list[i], fileList);
            } else {
                File f;
                try {
                    f = new File(list[i].getCanonicalPath());
                } catch (IOException ioe) {
                    f = list[i];
                }
                fileList.add(f);
            }
        }
    }

    private HashMap getSourcePaths(FileFilter filter, File dir)
    {
        return getSourcePaths(filter, dir, new HashMap());
    }

    private HashMap getSourcePaths(FileFilter filter, File dir, HashMap allDirs)
    {
        File[] list = dir.listFiles(filter);

        boolean found = false;
        for (int i = 0; i < list.length; i++) {
            if (list[i].isDirectory()) {
                getSourcePaths(filter, list[i], allDirs);
            } else {
                found = true;
            }
        }

        if (found) {
            File f;
            try {
                f = new File(dir.getCanonicalPath());
            } catch (IOException ioe) {
                f = dir;
            }
            allDirs.put(f, f);
        }

        return allDirs;
    }

    /**
     * Find all Python files (as indicated by the '.py' extension),
     * skipping over '.svn' and 'cvs' subdirectories.
     */
    class PyFilter
        implements FileFilter
    {
        /**
         * Accept non-revision control directories and
         * files ending in '.py', '.PY', etc.
         *
         * @param file file being filtered
         */
        public boolean accept(File file)
        {
            if (file.isDirectory()) {
                if (file.getName().equalsIgnoreCase(".svn") ||
                    file.getName().equalsIgnoreCase("cvs"))
                {
                    return false;
                }

                return true;
            }

            String name = file.getName().toLowerCase();
            if (!name.endsWith(".py")) {
                return false;
            }

            return true;
        }
    }
}

/**
 * Run a Python test.
 */
class TestRunner
{
    /**
     * Python path environment variable.
     */
    private static final String PATH_ENV_NAME = "PYTHONPATH";

    /** Python executable. */
    private String pythonExecutable;
    /** Python test script. */
    private File testFile;

    /** Did Python complain about the specified argument? */
    private boolean badOption;

    /** Lines written to the standard output stream. */
    private ArrayList outLines = new ArrayList();
    /** Lines written to the standard error stream. */
    private ArrayList errLines = new ArrayList();

    /** Test process exit value. */
    private int exitVal = -1;

    /**
     * Create a test runner for the specified Python unit test script.
     *
     * @param f test file
     */
    TestRunner(String pythonExecutable, File f)
    {
        this.pythonExecutable = pythonExecutable;
        testFile = f;
    }

    /**
     * Build a Unix-style path from 'elements'.
     *
     * @param elements collection of path elements
     *
     * @return Unix-style path
     */
    public static String buildPath(Collection elements)
    {
        String path = System.getenv(PATH_ENV_NAME);
        for (Iterator it = elements.iterator(); it.hasNext(); ) {
            if (path == null || path.length() == 0) {
                path = it.next().toString();
            } else {
                path += ":" + it.next().toString();
            }
        }
        return path;
    }

    /**
     * Get lines of text written to the standard error stream.
     *
     * @return error text lines
     */
    List getErrorLines()
    {
        return errLines;
    }

    /**
     * Get the test process exit value.
     *
     * @return exit value
     */
    int getExitValue()
    {
        return exitVal;
    }

    /**
     * Get lines of text written to the standard output stream.
     *
     * @return output text lines
     */
    List getOutputLines()
    {
        return outLines;
    }

    /**
     * Was anything written to the standard error stream?
     *
     * @return <tt>true</tt> if there are standard error lines
     */
    boolean hasErrorLines()
    {
        return errLines.size() > 0;
    }

    /**
     * Was anything written to the standard output stream?
     *
     * @return <tt>true</tt> if there are standard output lines
     */
    boolean hasOutputLines()
    {
        return outLines.size() > 0;
    }

    /**
     * Did Python complain about the specified argument?
     *
     * @return <tt>true</tt> if the argument was invalid
     */
    boolean isBadOption()
    {
        return badOption;
    }

    /**
     * Open the stream as a Reader.
     *
     * @param in input stream
     *
     * @return encapsulated input stream
     */
    private static BufferedReader openReader(InputStream in)
    {
        return new BufferedReader(new InputStreamReader(in));
    }

    /**
     * Reset to the initial state.
     */
    void reset()
    {
        badOption = false;
        outLines.clear();
        errLines.clear();
        exitVal = -1;
    }

    /**
     * Run the unit tests.
     *
     * @param pathEnv Python path
     * @param arg python argument (if non-null)
     *
     * @throws PyTestException if there is a problem
     */
    void runTests(String pathEnv, String arg)
        throws PyTestException
    {
        ArrayList args = new ArrayList();
        args.add(pythonExecutable);
        args.add(testFile.toString());
        if (arg != null && arg.length() > 0) {
            args.add(arg);
        }

        ProcessBuilder pBldr = new ProcessBuilder(args);
        pBldr.redirectErrorStream(true);

        // set PYTHONPATH envvar
        Map env = pBldr.environment();
        env.put(PATH_ENV_NAME, pathEnv);

        Process proc;
        try {
            proc = pBldr.start();
        } catch (IOException ioe) {
            throw new PyTestException("Couldn't run " + testFile, ioe);
        }

        try {
            proc.getOutputStream().close();
        } catch (IOException ioe) {
            // ignore errors on close
        }

        BufferedReader stdout = openReader(proc.getInputStream());
        // ignoring error stream
        BufferedReader stderr = null;

        while (stdout != null || stderr != null) {
            String line;

            for (int i = 0; i < 2; i++) {
                String name;
                BufferedReader rdr;
                List list;

                if (i == 0) {
                    name = "stdout";
                    rdr = stdout;
                    list = outLines;
                } else {
                    name = "stdout";
                    rdr = stderr;
                    list = errLines;
                }

                if (rdr != null) {
                    try {
                        line = rdr.readLine();
                    } catch (IOException ioe) {
                        throw new PyTestException("Couldn't read " + name, ioe);
                    }

                    if (line == null) {
                        rdr = null;
                    } else {
                        if (list.size() == 0 &&
                            line.contains("option " + arg + " not recognized"))
                        {
                            badOption = true;
                            stdout = null;
                            stderr = null;
                            proc.destroy();
                        }

                        list.add(line);
                    }

                    if (rdr == null) {
                        if (i == 0) {
                            stdout = null;
                        } else {
                            stderr = null;
                        }
                    }
                }
            }
        }

        try {
            proc.waitFor();
        } catch (InterruptedException ie) {
            throw new PyTestException("Couldn't wait for " + testFile, ie);
        }

        exitVal = proc.exitValue();
    }
}
