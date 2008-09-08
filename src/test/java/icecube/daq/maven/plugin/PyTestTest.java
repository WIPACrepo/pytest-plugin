package icecube.daq.maven.plugin;

//import org.codehaus.plexus.util.FileUtils;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;

import java.io.File;

public class PyTestTest
    extends AbstractMojoTestCase
{
    protected void setUp()
        throws Exception
    {
        // required for mojo lookups to work
        super.setUp();
    }

    public void testBasic()
        throws Exception
    {
        File pom = new File(getBasedir(),
                            "/target/test-classes/sample-pom.xml");

        PyTest pyTest = (PyTest) lookupMojo("pytest", pom);
        assertNotNull(pyTest);

        pyTest.execute();
    }
}
