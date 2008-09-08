package icecube.daq.maven.plugin;

import org.apache.maven.plugin.testing.AbstractMojoTestCase;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class PyTestParserTest
    extends AbstractMojoTestCase
{
    class ExpectedData
    {
        private String baseName;
        private int numTests;
        private int numErrors;
        private int numFailed;
        private double time;
        private boolean hasXMLTest;

        ExpectedData(File file, List lines)
        {
            baseName = getBaseName(file);

            for (Iterator it = lines.iterator(); it.hasNext(); ) {
                String line = (String) it.next();

                if (line.startsWith("tsts=")) {
                    numTests = getIntValue(file, "tsts", line.substring(5));
                } else if (line.startsWith("errs=")) {
                    numErrors = getIntValue(file, "errs", line.substring(5));
                } else if (line.startsWith("fail=")) {
                    numFailed = getIntValue(file, "fail", line.substring(5));
                } else if (line.startsWith("time=")) {
                    String dblStr = line.substring(5);
                    try {
                        time = Double.parseDouble(dblStr);
                    } catch (NumberFormatException nfe) {
                        throw new Error("Bad \"time\" value \"" + dblStr +
                                "\" in \"" + file + "\"");
                    }
                } else if (line.startsWith("xml=")) {
                    hasXMLTest = line.endsWith("=true");
                } else {
                    throw new Error("Unexpected line \"" + line + "\" in " +
                                    file);
                }
            }
        }

        void check(File f, SuiteData data, boolean isXML)
        {
            if (isXML && !hasXMLTest) {
                assertEquals("Bad number of tests in " + f,
                             0, data.getNumTests());
                assertEquals("Bad number of errors in " + f,
                             0, data.getNumErrors());
                assertEquals("Bad number of failures in " + f,
                             0, data.getNumFailures());
                assertEquals("Bad time in " + f, 0.0, data.getTime(), 0.00005);
            } else {
                assertEquals("Bad number of tests in " + f,
                             numTests, data.getNumTests());
                assertEquals("Bad number of errors in " + f,
                             numErrors, data.getNumErrors());
                assertEquals("Bad number of failures in " + f,
                             numFailed, data.getNumFailures());
                assertEquals("Bad time in " + f, time, data.getTime(), 0.00005);
            }
        }

        private int getIntValue(File file, String fldName, String intStr)
        {
            try {
                return Integer.parseInt(intStr);
            } catch (NumberFormatException nfe) {
                throw new Error("Bad \"" + fldName + "\" value \"" + intStr +
                                "\" in \"" + file + "\"");
            }
        }

        boolean isFile(File f)
        {
            return getBaseName(f).equals(baseName);
        }

        public String toString()
        {
            return "ExpData:" + baseName + ",tsts=" + numTests +
                ",errs=" + numErrors + ",fail=" + numFailed + ",time=" + time;
        }
    }

    private static String getBaseName(File f)
    {
        String fileName = f.getName();

        int idx = fileName.lastIndexOf('.');
        if (idx <= 0) {
            return fileName;
        }

        return fileName.substring(0, idx);
    }

    private List readFile(File file)
        throws IOException
    {
        BufferedReader rdr = new BufferedReader(new FileReader(file));

        ArrayList lines = new ArrayList();
        while (true) {
            String line = rdr.readLine();
            if (line == null) {
                break;
            }

            lines.add(line);
        }

        return lines;
    }

    protected void setUp()
        throws Exception
    {
        // required for mojo lookups to work
        super.setUp();
    }

    public void testBasic()
        throws IOException, PyTestException
    {
        File tstDir =
            new File(getBasedir(), "/target/test-classes/test-output");

        File[] tstOut = tstDir.listFiles();
        Arrays.sort(tstOut);

        ExpectedData expData = null;
        for (int i = 0; i < tstOut.length; i++) {
            List lines = readFile(tstOut[i]);

            if (tstOut[i].getName().endsWith(".exp")) {
                expData = new ExpectedData(tstOut[i], lines);
                continue;
            } else if (!expData.isFile(tstOut[i])) {
                final String fileName = tstOut[i].getName();
                final String baseName = getBaseName(tstOut[i]);
                fail("Did not find expected data for " + fileName +
                     " in \"" + baseName + ".exp\"");
            }

            SuiteData suite = new SuiteData(tstOut[i]);

            boolean isXML = tstOut[i].getName().endsWith(".xout");

            PyTestParser parser = new PyTestParser(lines, suite, isXML);
            expData.check(tstOut[i], suite, isXML);
        }
    }
}
