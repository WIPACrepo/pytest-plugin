package icecube.daq.maven.plugin;

import java.io.PrintStream;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Output/error stream data.
 */
class StreamData
{
    /** List of lines read from stream. */
    private ArrayList lines;

    /**
     * Add a line to the list.
     *
     * @param line new line
     */
    void addLine(String line)
    {
        if (lines == null) {
            lines = new ArrayList();
        }

        lines.add(line);
    }

    /**
     * Dump lines to output stream.
     *
     * @param out output stream
     */
    void dump(PrintStream out)
    {
        if (lines != null) {
            for (Iterator it = lines.iterator(); it.hasNext(); ) {
                out.println(it.next());
            }
        }
    }

    /**
     * Get the lines from this stream.
     *
     * @return list of lines
     */
    List getLines()
    {
        return lines;
    }

    /**
     * Insert a line at the front of the list.
     *
     * @param line new line
     */
    void insertLine(String line)
    {
        if (lines == null) {
            lines = new ArrayList();
        }

        lines.add(0, line);
    }

    /**
     * Have any lines been added?
     *
     * @return <tt>true</tt> if no lines have been added.
     */
    boolean isEmpty()
    {
        return lines == null || lines.size() == 0;
    }
}
