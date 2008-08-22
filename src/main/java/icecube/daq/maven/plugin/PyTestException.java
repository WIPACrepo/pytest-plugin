package icecube.daq.maven.plugin;

/**
 * Exception for PyTest problems.
 */
public class PyTestException
    extends Exception
{
    /**
     * Create an exception with the specified message.
     *
     * @param msg error message
     */
    public PyTestException(String msg)
    {
        super(msg);
    }

    /**
     * Create an exception with the specified original cause.
     *
     * @param thr original cause
     */
    public PyTestException(Throwable thr)
    {
        super(thr);
    }

    /**
     * Create an exception with the specified message and original cause.
     *
     * @param msg error message
     * @param thr original cause
     */
    public PyTestException(String msg, Throwable thr)
    {
        super(msg, thr);
    }
}
