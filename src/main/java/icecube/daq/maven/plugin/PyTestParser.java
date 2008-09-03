package icecube.daq.maven.plugin;

import java.io.IOException;
import java.io.StringReader;

import java.util.Iterator;
import java.util.List;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

import org.xml.sax.helpers.XMLReaderFactory;

/**
 * Error handler which doesn't print errors to console.
 */
class SuppressErrors
    implements ErrorHandler
{
    public void error(SAXParseException exception)
        throws SAXException
    {
    }
    public void fatalError(SAXParseException exception)
        throws SAXException
    {
    }
    public void warning(SAXParseException exception)
        throws SAXException
    {
    }
}

class TracebackParser
{
    /** Match a traceback file line. */
    private static final Pattern filePat =
        Pattern.compile("^\\s*File \"([^\"]+)\", line (\\d+), in (\\S+)\\s*$");
    /** Match a partial traceback file line. */
    private static final Pattern partialPat =
        Pattern.compile("^\\s*File \"([^\"]+)\", line (\\d+),\\s*$");
    /** Match the end of a partial traceback file line. */
    private static final Pattern testPartPat =
        Pattern.compile("^\\s*in (\\S+)\\s*$");
    /** Match a traceback exception line. */
    private static final Pattern excPat =
        Pattern.compile("^(\\S+)(:\\s+(.*))?\\s*$");
    /** Match a traceback source line. */
    private static final Pattern sourcePat =
        Pattern.compile("^\\s*(\\S.*\\S*)\\s*$");

    boolean parse(ErrorData err, String line)
        throws PyTestException
    {
        Matcher match;

        if (err.isFramePartial()) {
            match = testPartPat.matcher(line);
            if (match.find()) {
                err.setFrameTest(match.group(1));
                return true;
            }
        }

        match = filePat.matcher(line);
        if (match.find()) {
            int num;
            try {
                num = Integer.parseInt(match.group(2));
            } catch (NumberFormatException nfe) {
                throw new PyTestException("Bad line number \"" +
                                          match.group(2) +
                                          "\" in traceback for \"" +
                                          match.group(1));
            }

            err.addTraceFile(match.group(1), num, match.group(3));
            return true;
        }

        match = partialPat.matcher(line);
        if (match.find()) {
            int num;
            try {
                num = Integer.parseInt(match.group(2));
            } catch (NumberFormatException nfe) {
                throw new PyTestException("Bad line number \"" +
                                          match.group(2) +
                                          "\" in traceback for \"" +
                                          match.group(1));
            }

            err.addTraceFile(match.group(1), num, null);
            return true;
        }

        match = excPat.matcher(line);
        if (match.find()) {
            return false;
        }

        match = sourcePat.matcher(line);
        if (match.find()) {
            err.addTraceSource(match.group(1));
            return true;
        }

        return false;
    }
}

/**
 * Python unit test XML result parser.
 */
class TestXMLParser
    implements ContentHandler
{
    /** parser states. */
    private static final int INITIAL = 1;
    private static final int IN_SUITE = 2;
    private static final int IN_CASE = 3;
    private static final int IN_STDOUT = 4;
    private static final int IN_STDERR = 5;
    private static final int IN_ERROR = 6;
    private static final int IN_FAILURE = 7;

    /** Current parser state. */
    private int state = INITIAL;
    /** Parsed test suite data. */
    private SuiteData data;
    /** Current test case being parsed. */
    private CaseData curCase;
    /** Current error/failure output being parsed. */
    private ErrorData curError;
    /** Current standard output/error stream data being parsed. */
    private StreamData curOut;
    /** Traceback parser */
    private TracebackParser tracebackParser;

    /**
     * Create a parser for the Python unit test XML output.
     *
     * @param data test suite data
     */
    TestXMLParser(SuiteData data)
    {
        this.data = data;
    }

    /**
     * Process character data.
     *
     * @param array of characters from XML data
     * @param start starting position
     * @param length number of characters
     *
     * @throws SAXException if there is a problem
     */
    public void characters(char[] ch, int start, int length)
        throws SAXException
    {
        String line = String.valueOf(ch, start, length);

        if (state == IN_ERROR || state == IN_FAILURE) {
            if (curError == null) {
                throw new SAXException("No active error object");
            }

            if (!curError.hasExceptionText()) {
                curError.setExceptionText(line);
            } else if (line.trim().length() > 0) {
                if (tracebackParser == null) {
                    tracebackParser = new TracebackParser();
                }

                try {
                    if (!tracebackParser.parse(curError, line)) {
                        throw new SAXException("Unparseable traceback line \"" +
                                               line + "\"");
                    }
                } catch (PyTestException pte) {
                    throw new SAXException(pte.getMessage());
                }
            }
        } else if (state == IN_STDOUT ||
                   state == IN_STDERR)
        {
            if (curOut == null) {
                throw new SAXException("No active output object");
            }

            curOut.addLine(line);
        } else if (line.trim().length() > 0) {
            System.out.println(":: " + line);
        }
    }

    /**
     * Do nothing.
     */
    public void endDocument()
    {
        // do nothing
    }

    /**
     * Process end of an element.
     *
     * @param namespaceURI namespace URI
     * @param localName local name (without prefix) or empty string
     *                  if namespace processing is not being performed
     * @param qName qualified XML 1.0 name (without prefix) or empty string
     *              if qualified names are not available
     *
     * @throws SAXException if there is a problem
     */
    public void endElement(String namespaceURI, String localName, String qName)
        throws SAXException
    {
        if (namespaceURI.length() > 0) {
            throw new SAXException("Unknown namespaceURI \"" + namespaceURI +
                                   "\"");
        }

        if (state == INITIAL) {
            throw new SAXException("Unexpected end tag </" + localName + ">");
        } else if (state == IN_SUITE) {
            if (localName.equals("testsuite")) {
                state = INITIAL;
            } else {
                throw new SAXException("Unexpected testsuite end tag </" +
                                       localName + ">");
            }
        } else if (state == IN_CASE) {
            if (localName.equals("testcase")) {
                if (curCase == null) {
                    throw new SAXException("No case data at testcase end tag");
                }

                data.addCase(curCase);
                curCase = null;
                state = IN_SUITE;
            } else {
                throw new SAXException("Unexpected testcase end tag </" +
                                       localName + ">");
            }
        } else if (state == IN_STDOUT) {
            if (localName.equals("system-out")) {
                if (curOut == null) {
                    throw new SAXException("No output data" +
                                           " at system-out end tag");
                }

                data.setSystemOut(curOut);
                curOut = null;
                state = IN_SUITE;
            } else {
                throw new SAXException("Unexpected system-out end tag </" +
                                       localName + ">");
            }
        } else if (state == IN_STDERR) {
            if (localName.equals("system-err")) {
                if (curOut == null) {
                    throw new SAXException("No output data" +
                                           " at system-err end tag");
                }

                data.setSystemErr(curOut);
                curOut = null;
                state = IN_SUITE;
            } else {
                throw new SAXException("Unexpected system-err end tag </" +
                                       localName + ">");
            }
        } else if (state == IN_ERROR) {
            if (localName.equals("error")) {
                if (curError == null) {
                    throw new SAXException("No error data at error end tag");
                } else if (curCase == null) {
                    throw new SAXException("No testcase data at error end tag");
                }

                curCase.setError(curError);
                curError = null;
                state = IN_CASE;
            } else {
                throw new SAXException("Unexpected error end tag </" +
                                       localName + ">");
            }
        } else if (state == IN_FAILURE) {
            if (localName.equals("failure")) {
                if (curError == null) {
                    throw new SAXException("No error data at failure end tag");
                } else if (curCase == null) {
                    throw new SAXException("No testcase data" +
                                           " at failure end tag");
                }

                curCase.setFailure(curError);
                curError = null;
                state = IN_CASE;
            } else {
                throw new SAXException("Unexpected error end tag </" +
                                       localName + ">");
            }
        } else {
            System.err.println("Unknown state " + state);
        }
    }

    /**
     * Unimplemented.
     *
     * @param prefix namespace prefix being mapped
     */
    public void endPrefixMapping(String prefix)
    {
        throw new Error("Unimplemented");
    }

    /**
     * Do nothing.
     */
    public void ignorableWhitespace(char[] ch, int start, int length)
    {
        // do nothing
    }

    public void parse(List lines)
        throws PyTestException, SAXException
    {
        try {
            XMLReader parser = XMLReaderFactory.createXMLReader();
            parser.setContentHandler(this);
            parser.setErrorHandler(new SuppressErrors());
            parser.parse(new InputSource(new ListReader(lines)));
        } catch (IOException ioe) {
            throw new PyTestException(ioe);
        }
    }

    /**
     * Unimplemented.
     *
     * @param target processing instruction target
     * @param target processing instruction data (<tt>null</tt> if none was
     *               supplied)
     */
    public void processingInstruction(String target, String data)
    {
        throw new Error("Unimplemented");
    }

    /**
     * Do nothing.
     */
    public void setDocumentLocator(Locator locator)
    {
        // do nothing
    }

    /**
     * Unimplemented.
     */
    public void skippedEntity(String name)
    {
        throw new Error("Unimplemented");
    }

    /**
     * Do nothing.
     */
    public void startDocument()
    {
        // do nothing
    }

    /**
     * Process start of an element.
     *
     * @param namespaceURI namespace URI
     * @param localName local name (without prefix) or empty string
     *                  if namespace processing is not being performed
     * @param qName qualified XML 1.0 name (without prefix) or empty string
     *              if qualified names are not available
     * @param attrs attributes attached to the element.  If there are no
     *              attributes, it shall be an empty Attributes object.
     *
     * @throws SAXException if there is a problem
     */
    public void startElement(String namespaceURI, String localName,
                             String qName, Attributes attrs)
        throws SAXException
    {
        if (namespaceURI.length() > 0) {
            throw new SAXException("Unknown namespaceURI \"" + namespaceURI +
                                   "\"");
        }

        if (state == INITIAL) {
            if (localName.equals("testsuite")) {
                if (data.isInitialized()) {
                    throw new SAXException("Found multiple testsuites");
                }

                for (int i = 0; i < attrs.getLength(); i++) {
                    if (attrs.getLocalName(i).equals("name")) {
                        data.setName(attrs.getValue(i));
                    } else if (attrs.getLocalName(i).equals("errors")) {
                        try {
                            int val = Integer.parseInt(attrs.getValue(i));
                            data.setNumErrors(val);
                        } catch (NumberFormatException nfe) {
                            throw new SAXException("Bad number of errors \"" +
                                                   attrs.getValue(i) + "\"");
                        }
                    } else if (attrs.getLocalName(i).equals("failures")) {
                        try {
                            int val = Integer.parseInt(attrs.getValue(i));
                            data.setNumFailures(val);
                        } catch (NumberFormatException nfe) {
                            throw new SAXException("Bad number of failures \"" +
                                                   attrs.getValue(i) + "\"");
                        }
                    } else if (attrs.getLocalName(i).equals("tests")) {
                        try {
                            int val = Integer.parseInt(attrs.getValue(i));
                            data.setNumTests(val);
                        } catch (NumberFormatException nfe) {
                            throw new SAXException("Bad number of tests \"" +
                                                   attrs.getValue(i) + "\"");
                        }
                    } else if (attrs.getLocalName(i).equals("time")) {
                        try {
                            double val = Double.parseDouble(attrs.getValue(i));
                            data.setTime(val);
                        } catch (NumberFormatException nfe) {
                            throw new SAXException("Bad time \"" +
                                                   attrs.getValue(i) + "\"");
                        }
                    } else {
                        throw new SAXException("Unknown testsuite attribute" +
                                               " \"" + attrs.getLocalName(i) +
                                               "\"");
                    }
                }

                state = IN_SUITE;
            } else {
                throw new SAXException("Unexpected initial tag <" +
                                       localName + ">");
            }
        } else if (state == IN_SUITE) {
            if (localName.equals("testcase")) {
                if (curCase != null) {
                    throw new SAXException("Previous case was not terminated");
                }

                CaseData tmpCase = new CaseData();

                for (int i = 0; i < attrs.getLength(); i++) {
                    if (attrs.getLocalName(i).equals("classname")) {
                        tmpCase.setClassName(attrs.getValue(i));
                    } else if (attrs.getLocalName(i).equals("name")) {
                        tmpCase.setName(attrs.getValue(i));
                    } else if (attrs.getLocalName(i).equals("time")) {
                        try {
                            double val = Double.parseDouble(attrs.getValue(i));
                            tmpCase.setTime(val);
                        } catch (NumberFormatException nfe) {
                            throw new SAXException("Bad time \"" +
                                                   attrs.getValue(i) + "\"");
                        }
                    } else {
                        throw new SAXException("Unknown testcase attribute \"" +
                                               attrs.getLocalName(i) + "\"");
                    }
                }

                curCase = tmpCase;
                state = IN_CASE;
            } else if (localName.equals("system-out")) {
                if (curOut != null) {
                    throw new SAXException("Previous output data" +
                                           " was not terminated");
                }

                StreamData tmpOut = new StreamData();

                if (attrs.getLength() > 0) {
                    throw new SAXException("Unexpected attributes for" +
                                           " system-out tag");
                }

                curOut = tmpOut;
                state = IN_STDOUT;
            } else if (localName.equals("system-err")) {
                if (curOut != null) {
                    throw new SAXException("Previous output data" +
                                           " was not terminated");
                }

                StreamData tmpOut = new StreamData();

                if (attrs.getLength() > 0) {
                    throw new SAXException("Unexpected attributes for" +
                                           " system-err tag");
                }

                curOut = tmpOut;
                state = IN_STDERR;
            } else {
                throw new SAXException("Unexpected testsuite tag <" +
                                       localName + ">");
            }
        } else if (state == IN_CASE) {
            if (localName.equals("error") || localName.equals("failure")) {
                if (curError != null) {
                    throw new SAXException("Previous error data" +
                                           " was not terminated");
                }

                ErrorData tmpError = new ErrorData(localName);

                for (int i = 0; i < attrs.getLength(); i++) {
                    if (attrs.getLocalName(i).equals("type")) {
                        try {
                            tmpError.setExceptionName(attrs.getValue(i));
                        } catch (Error err) {
                            throw new SAXException(err.getMessage());
                        }
                    } else {
                        throw new SAXException("Unknown " + localName +
                                               " attribute \"" +
                                               attrs.getLocalName(i) + "\"");
                    }
                }

                curError = tmpError;
                if (localName.equals("error")) {
                    state = IN_ERROR;
                } else {
                    state = IN_FAILURE;
                }
            } else {
                System.out.println("Ignoring <" + localName + ">");
            }
        } else {
            System.err.println("Unknown state " + state);
        }
    }

    /**
     * Unimplemented.
     *
     * @param prefix namespace prefix being mapped
     * @param uri namespace URI being mapped to
     */
    public void startPrefixMapping(String prefix, String uri)
    {
        throw new Error("Unimplemented");
    }
}

/**
 * Python unit test test result parser.
 */
class TestTextParser
{
    /** Match a start-of-test line. */
    private static final Pattern testPat =
        Pattern.compile("^\\s*(\\S+)\\s+\\((\\S+)\\)\\s\\.{3}\\s(.*)\\s*$");
    /** Match a separator line of equals signs (=). */
    private static final Pattern sepEqualPat =
        Pattern.compile("=+\\s*$");
    /** Match a test case detail line. */
    private static final Pattern detailPat =
        Pattern.compile("^(\\S+):\\s+(\\S+)\\s+\\((\\S+)\\)\\s*$");
    /** Match a separator line of minus signs (-). */
    private static final Pattern sepMinusPat =
        Pattern.compile("-+\\s*$");
    /** Match a traceback header line. */
    private static final Pattern tracePat =
        Pattern.compile("^Traceback \\(most recent call last\\):\\s*$");
    /** Match a test case detail type line. */
    private static final Pattern detailTypePat =
        Pattern.compile("^(\\S+)(:\\s+(.*))?\\s*$");
    /** Match a successful test suite summary line. */
    private static final Pattern runPat =
        Pattern.compile("Ran (\\d+) tests? in (\\d+\\.\\d+)s\\s*$");
    /** Match a failed test suite summary line. */
    private static final Pattern failPat =
        Pattern.compile("FAILED\\s+\\((\\S+)=(\\d+)(,\\s+(\\S+)" +
                        "=(\\d+))?\\)\\s*$");

    /** All possible parser states. */
    private static final int INITIAL = 1;
    private static final int IN_CASE = 2;
    private static final int IN_DETAIL = 3;
    private static final int IN_DETAIL_SEP = 4;
    private static final int IN_DETAIL_BODY = 5;
    private static final int IN_DETAIL_END = 6;
    private static final int IN_FINAL = 7;
    private static final int IN_STDOUT = 8;

    /** Valid status strings. */
    private static final String[] validStatus =
        new String[] { "ERROR", "FAIL", "ok" };

    /** Parsed test suite data. */
    private SuiteData data;
    /** Current test case being parsed. */
    private CaseData curCase;

    /** Current error/failure details being parsed. */
    private ErrorData detail;
    /** Is the current detail for an ERROR test? */
    private boolean isDetailError;
    /** Traceback parser */
    private TracebackParser tracebackParser;

    /**
     * Create a parser for the Python unit test text output.
     *
     * @param data test suite data
     */
    TestTextParser(SuiteData data)
    {
        this.data = data;
    }

    /**
     * Parse the Python unit test output.
     *
     * @param lines list of text lines
     *
     * @throws PyTestException if there is a problem
     */
    void parse(List lines)
        throws PyTestException
    {
        StreamData outData = new StreamData();
        StreamData errData = new StreamData();

        int state = INITIAL;

        for (Iterator iter = lines.iterator(); iter.hasNext(); ) {
            String line = (String) iter.next();

            if (state == INITIAL) {
                Matcher match;

                match = testPat.matcher(line);
                if (match.find()) {
                    CaseData caseData = new CaseData();
                    caseData.setName(match.group(1));
                    caseData.setClassName(match.group(2));

                    data.addCase(caseData);

                    final String status = match.group(3);

                    boolean isValid = false;
                    for (int i = 0; i < validStatus.length; i++) {
                        if (status.endsWith(validStatus[i])) {
                            final int newLen =
                                status.length() - validStatus[i].length();
                            if (newLen > 0) {
                                String subLine = status.substring(0, newLen);
                                errData.addLine(subLine);
                            }

                            isValid = true;
                        }
                    }

                    if (isValid) {
                        state = INITIAL;
                    } else {
                        errData.addLine(status);
                        state = IN_CASE;
                    }

                    continue;
                }

                match = sepEqualPat.matcher(line);
                if (match.find()) {
                    state = IN_DETAIL;
                    continue;
                }

                match = sepMinusPat.matcher(line);
                if (match.find()) {
                    state = IN_FINAL;
                    continue;
                }
            }

            if (state == IN_CASE) {
                boolean isValid = false;
                for (int i = 0; i < validStatus.length; i++) {
                    if (line.endsWith(validStatus[i])) {
                        final int newLen =
                            line.length() - validStatus[i].length();
                        if (newLen > 0) {
                            errData.addLine(line.substring(0, newLen));
                        }

                        isValid = true;
                    }
                }

                if (isValid) {
                    state = INITIAL;
                } else {
                    errData.addLine(line);
                    state = IN_CASE;
                }

                continue;
            }

            if (state == IN_DETAIL) {
                if (curCase != null) {
                    throw new PyTestException("Found existing testcase" +
                                              " for detail");
                } else if (detail != null) {
                    throw new PyTestException("Found existing detail" +
                                              " for testcase");
                }

                Matcher match = detailPat.matcher(line);
                if (!match.find()) {
                    throw new PyTestException("Expected details after \"===\"");
                }

                final String name = match.group(2);
                final String className = match.group(3);

                curCase = data.findCase(className, name);
                if (curCase == null) {
                    throw new PyTestException("Found details for unknown " +
                                              className + " testcase " +
                                              name);
                }

                final String detailType = match.group(1);
                if (detailType.equals("ERROR")) {
                    isDetailError = true;
                } else if (detailType.equals("FAIL")) {
                    isDetailError = false;
                } else {
                    throw new PyTestException("Unknown detail type \"" +
                                              detailType + "\" for " +
                                              className + " test " + name);
                }

                state = IN_DETAIL_SEP;
                continue;
            }

            if (state == IN_DETAIL_SEP) {
                if (curCase == null) {
                    throw new PyTestException("No testcase for detail");
                } else if (detail != null) {
                    throw new PyTestException("Found detail for testcase");
                }

                Matcher match = sepMinusPat.matcher(line);
                if (!match.find()) {
                    throw new PyTestException("Expected \"---\" after" +
                                              " detail header");
                }

                detail = new ErrorData(isDetailError);
                state = IN_DETAIL_BODY;
                continue;
            }

            if (state == IN_DETAIL_BODY) {
                if (curCase == null) {
                    throw new PyTestException("No testcase for detail");
                } else if (detail == null) {
                    throw new PyTestException("No detail for testcase");
                }

                Matcher match;

                match = tracePat.matcher(line);
                if (match.find()) {
                    continue;
                }

                if (tracebackParser == null) {
                    tracebackParser = new TracebackParser();
                }

                if (tracebackParser.parse(detail, line)) {
                    continue;
                }

                match = detailTypePat.matcher(line);
                if (match.find()) {
                    detail.setExceptionName(match.group(1));
                    detail.setExceptionText(match.group(2));
                    state = IN_DETAIL_END;
                    continue;
                }

                throw new PyTestException("Unknown detail line \"" + line +
                                          "\"");
            }

            if (state == IN_DETAIL_END) {
                if (line.trim().length() != 0) {
                    throw new PyTestException("Expected blank line" +
                                              " at detail end");
                }

                if (curCase == null) {
                    throw new PyTestException("No testcase for detail");
                } else if (detail == null) {
                    throw new PyTestException("No detail for testcase");
                }

                if (isDetailError) {
                    curCase.setError(detail);
                } else {
                    curCase.setFailure(detail);
                }

                curCase = null;
                detail = null;

                state = INITIAL;
                continue;
            }

            if (state == IN_FINAL) {
                Matcher match;

                match = runPat.matcher(line);
                if (match.find()) {
                    int numTests;
                    try {
                        numTests = Integer.parseInt(match.group(1));
                    } catch (NumberFormatException nfe) {
                        throw new PyTestException("Bad number of tests in " +
                                                  line);
                    }
                    data.setNumTests(numTests);

                    double time;
                    try {
                        time = Double.parseDouble(match.group(2));
                    } catch (NumberFormatException nfe) {
                        throw new PyTestException("Bad test time in " + line);
                    }
                    data.setTime(time);

                    continue;
                }

                match = failPat.matcher(line);
                if (match.find()) {
                    for (int i = 0; i + 2 < match.groupCount(); i += 3) {
                        String fld = match.group(i + 1);

                        int val;
                        try {
                            val = Integer.parseInt(match.group(i + 2));
                        } catch (NumberFormatException nfe) {
                            throw new PyTestException("Bad number of " + fld +
                                                      " in \"" + line + "\"");
                        }

                        if (fld.equals("failures")) {
                            data.setNumFailures(val);
                        } else if (fld.equals("errors")) {
                            data.setNumErrors(val);
                        }
                    }

                    state = IN_STDOUT;
                    continue;
                }

                if (line.startsWith("OK")) {
                    state = IN_STDOUT;
                    continue;
                }

                if (line.trim().length() == 0) {
                    continue;
                }

                continue;
            }

            if (state == IN_STDOUT) {
                outData.addLine(line);
                continue;
            }

            if (line.trim().length() > 0) {
                System.out.println("BAD<" + state + ">: " + line);
            }
        }

        if (!outData.isEmpty()) {
            data.setSystemOut(outData);
        }

        if (!errData.isEmpty()) {
            data.setSystemErr(errData);
        }
    }
}

/**
 * An inefficient Reader which converts a List of lines to a single string
 * which is passed to a StringReader to 'read'.
 */
class ListReader
    extends StringReader
{
    /**
     * 'Read' a list of text lines.
     *
     * @param lines list of test lines
     */
    ListReader(List lines)
    {
        super(join(lines));
    }

    /**
     * Join the lines into a single string.
     *
     * @param lines list of text lines
     */
    private static String join(List lines)
    {
        StringBuilder buf = new StringBuilder();

        boolean needNewline = false;
        for (Iterator iter = lines.iterator(); iter.hasNext(); ) {
            Object obj = iter.next();

            if (!needNewline) {
                needNewline = true;
            } else {
                buf.append('\n');
            }

            buf.append(obj);
        }

        return buf.toString();
    }
}

/**
 * Python unit test output parser.
 */
public class PyTestParser
{
    /**
     * Parse Python XML or plain-text output.
     *
     * @param lines unit test output lines
     * @param data test suite data container
     *
     * @throws PyTestException if the lines cannot be parsed
     */
    public PyTestParser(List lines, SuiteData data)
        throws PyTestException
    {
        try {
            TestXMLParser txtParser = new TestXMLParser(data);
            txtParser.parse(lines);
        } catch (SAXException se) {
            TestTextParser txtParser = new TestTextParser(data);
            txtParser.parse(lines);
        }
    }
}
