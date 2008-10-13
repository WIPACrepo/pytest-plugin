package icecube.daq.maven.plugin;

import java.io.BufferedReader;
import java.io.File;
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

import org.codehaus.plexus.util.DirectoryScanner;

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
     * @parameter expression="${python}" default-value="python"
     */
    private String pythonExecutable;

    /**
     * Python source directory.
     *
     * @parameter expression="${sourceDir}" default-value="src/main/python"
     */
    private String sourceDirectory;

    /**
     * Python unit test directory.
     *
     * @parameter expression="${testDir}" default-value="src/test/python"
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

    /**
     * List of patterns for included Python tests.
     *
     * @parameter expression=""
     */
    private String[] includes;

    /**
     * List of patterns for included Python tests if none were supplied.
     */
    private static String[] defaultIncludes = {
        "**/test*.py",
        "**/*test.py",
    };

    /**
     * List of patterns for excluded Python tests.
     *
     * @parameter expression=""
     */
    private String[] excludes;

    /**
     * Set this to 'true' to skip running tests.
     * 
     * @parameter expression="${skipTests}"
     */
    private boolean skipTests;

    /**
     * Set this to 'true' to skip running tests.
     *
     * @parameter expression="${maven.test.skip}" default-value="false"
     */
    private boolean mavenTestSkip;

    /**
     * The base directory of the project being tested. This can be obtained in
     * your unit test by System.getProperty("basedir").
     * 
     * @parameter expression="${basedir}"
     * @required
     */
    private File baseDir;

    private static File buildPath(File baseDir, String dir, String defaultDir)
    {
        if (dir == null) {
            dir = defaultDir;
        }

        File path = new File(baseDir, dir);
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
        // don't bother doing anything if we're skipping tests
        if (skipTests || mavenTestSkip) {
            return;
        }

        // make sure testName is in lower case
        if (testName != null) {
            testName = testName.toLowerCase();
        }

        // if surefire.useFile is false, try pytest.useFile
        if (pyUseFile) {
            useFile = true;
        }

        File srcPath = buildPath(baseDir, sourceDirectory, "src/main/python");
        if (!srcPath.exists()) {
            throw new MojoExecutionException("Source directory \"" + srcPath +
                                             "\" does not exist");
        }

        File testPath = buildPath(baseDir, testDirectory, "src/test/python");
        if (!srcPath.exists()) {
            throw new MojoExecutionException("Test directory \"" + testPath +
                                             "\" does not exist");
        }

        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(testPath);
        scanner.setExcludes(excludes);
        if (includes == null) {
            scanner.setIncludes(defaultIncludes);
        } else {
            scanner.setIncludes(includes);
        }
        scanner.addDefaultExcludes();
        scanner.setCaseSensitive(false);
        scanner.scan();

        String[] tstNames = scanner.getIncludedFiles();
        if (tstNames == null) {
            tstNames = new String[0];
        }

        HashMap allDirs = getSourcePaths(srcPath);

        for (int i = 0; i < tstNames.length; i++) {
            File f = new File(testPath, tstNames[i]);

            // add test directory to python path
            if (!allDirs.containsKey(f.getParent())) {
                allDirs.put(f.getParent(), f.getParent());
            }

            // exclude test specified by -Dtest=
            if (testName != null) {
                final String fName = f.getName().toLowerCase();

                if (!fName.startsWith(testName)) {
                    tstNames[i] = null;
                } else if (!fName.equals(testName) &&
                           fName.charAt(testName.length()) != '.')
                {
                    tstNames[i] = null;
                }
            }
        }

        String path = TestRunner.buildPath(allDirs.keySet());

        int totTests = 0;
        int totFails = 0;
        int totErrs = 0;

        ArrayList failed = new ArrayList();
        for (int i = 0; i < tstNames.length; i++) {
            if (tstNames[i] == null) {
                continue;
            }

            File f = new File(testPath, tstNames[i]);

            System.out.println("Running " + f.getName());
            TestRunner runner = new TestRunner(pythonExecutable, f);
            try {
                // try running tests using xmlrunner
                runner.runTests(testPath, path, "-x");
                // if that failed, use whatever test runner is available
                if (!runner.isTextOutput() && !runner.isXMLOutput()) {
                    runner.reset();
                    runner.runTests(testPath, path, "-v");
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

    private HashMap getSourcePaths(File srcDir)
    {
        HashMap allDirs = new HashMap();

        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(srcDir);
        scanner.setIncludes(new String[] { "**/*.py" });
        scanner.addDefaultExcludes();
        scanner.setCaseSensitive(false);
        scanner.scan();

        String[] incFiles = scanner.getIncludedFiles();
        for (int i = 0; i < incFiles.length; i++) {
            File tmp = new File(srcDir, incFiles[i]);

            File f;
            try {
                f = tmp.getCanonicalFile();
            } catch (IOException ioe) {
                f = tmp;
            }

            File dir = f.getParentFile();
            if (!allDirs.containsKey(dir)) {
                allDirs.put(dir, dir);
            }
        }

        return allDirs;
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

    /** Does this test output contain an expected text output line? */
    private boolean isText;
    /** Does this test output contain an expected XML output line? */
    private boolean isXML;

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
     * Does this test output contain an expected text output line?
     *
     * @return <tt>true</tt> if the output appears to be text-oriented
     */
    boolean isTextOutput()
    {
        return isText;
    }

    /**
     * Does this test output contain an expected XML output line?
     *
     * @return <tt>true</tt> if the output appears to be XML-oriented
     */
    boolean isXMLOutput()
    {
        return isXML;
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
        isText = false;
        isXML = false;
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
    void runTests(File testDir, String pathEnv, String arg)
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

        // set working directory
        pBldr.directory(testDir);

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
                        if (line.startsWith("Ran ") &&
                            (line.contains(" test in ") ||
                             line.contains(" tests in ")))
                        {
                            isText = true;
                        } else if (line.startsWith("<testsuite")) {
                            isXML = true;
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

        if (isText && isXML) {
            final String errMsg = "Test output has both text and XML elements";

            throw new PyTestException(errMsg);
        }
    }
}
