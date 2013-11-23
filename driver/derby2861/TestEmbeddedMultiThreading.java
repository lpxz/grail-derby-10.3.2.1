package derby2861;

/**
 * This class tests the thread-safeness of the Derby database system, using the embedded driver.  With the
 * proper choice of main arguments, which are probably different for different machines, it will sometimes show an
 * SQLException with the following output from printStackTrace().  It may need to be run more than once, as having
 * leftover views existing from prior runs sometimes seems to have an effect.
 *  
 * <pre>
 * java.lang.NullPointerException
 *     at org.apache.derby.iapi.sql.dictionary.TableDescriptor.getObjectName(TableDescriptor.java:758)
 *    at org.apache.derby.impl.sql.depend.BasicDependencyManager.getPersistentProviderInfos(BasicDependencyManager.java:677)
 *     at org.apache.derby.impl.sql.compile.CreateViewNode.bindViewDefinition(CreateViewNode.java:287)
 *     at org.apache.derby.impl.sql.compile.CreateViewNode.bind(CreateViewNode.java:183)
 *     at org.apache.derby.impl.sql.GenericStatement.prepMinion(GenericStatement.java:345)
 *     at org.apache.derby.impl.sql.GenericStatement.prepare(GenericStatement.java:119)
 *     at org.apache.derby.impl.sql.conn.GenericLanguageConnectionContext.prepareInternalStatement(GenericLanguageConnectionContext.java:745)
 *     at org.apache.derby.impl.jdbc.EmbedStatement.execute(EmbedStatement.java:568)
 *     at org.apache.derby.impl.jdbc.EmbedStatement.execute(EmbedStatement.java:517)
 *     at TestEmbeddedMultiThreading.executeStatement(TestEmbeddedMultiThreading.java:109)
 *     at TestEmbeddedMultiThreading.access$100(TestEmbeddedMultiThreading.java:10)
 *     at TestEmbeddedMultiThreading$ViewCreatorDropper.run(TestEmbeddedMultiThreading.java:173)
 *     at java.lang.Thread.run(Thread.java:534)
 * Stop here.
 * </pre>
 */
public class TestEmbeddedMultiThreading
{
    /**
     * Invoke the test, providing a number of threads and a number of iterations.
     * @param args arguments to the function, must be two integers: number of thread and number of iterations
     */
    static public void main(String[] args)
    {
        try
        {
            TestEmbeddedMultiThreading instance = new TestEmbeddedMultiThreading();

            if (args.length < 2)
            {
                System.out.println("Usage: main NUMBER_OF_THREADS NUMBER_OF_ITERATIONS");
                System.exit(-1);
            }
            instance.doit(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
        }
        catch (Throwable e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Creates the test object, registering the Derby embedded driver.  If the test database does not already exists,
     * creates that Derby database.
     * @throws ClassNotFoundException thrown if the driver class could not be found, probably due to classpath problems
     */
    private TestEmbeddedMultiThreading() throws ClassNotFoundException
    {
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");

        try
        {
            java.sql.DriverManager.getConnection("jdbc:derby:MTTESTDB;create=true");
        }
        catch (java.sql.SQLException e)
        {
            System.out.println("Exception creating database...assuming already exists");
        }
    }

    /**
     * Runs the thread-safeness test.
     * @param numThreads the number of threads to spawn for the test
     * @param numIterations the number of iterations to run each thread
     * @throws java.sql.SQLException thrown if there is an SQL error setting up the test
     */
    private void doit(int numThreads, int numIterations) throws java.sql.SQLException
    {
        try
        {
            executeStatement(getConnection(), "CREATE TABLE schemamain.SOURCETABLE (col1 int, col2 char(10), col3 varchar(20), col4 decimal(10,5))");
            executeStatement(getConnection(), "CREATE VIEW viewSource AS SELECT col1, col2 FROM schemamain.SOURCETABLE");
        }
        catch (java.sql.SQLException e)
        {
            // Just report this...probably the table or view already exists
            System.out.println(e.getMessage());
        }

        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++)
        {
            threads[i] = new Thread(new ViewCreatorDropper("schema1.VIEW" + i, "viewSource", "*", numIterations));
        }
        for (int i = 0; i < numThreads; i++)
        {
            threads[i].start();
        }

    }

    /**
     *  Returns a new connection to the test database
     * @return a newly create connection
     * @throws java.sql.SQLException thrown if the connection cannot be created
     */
    private java.sql.Connection getConnection() throws java.sql.SQLException
    {
        return java.sql.DriverManager.getConnection("jdbc:derby:MTTESTDB");
    }

    /**
     * Creates and executes a new SQL statement on the connection, ensuring that the statement is closed, regardless
     * of whether the statement execution throws an exception
     * @param conn the connection against which to run the statement
     * @param sql the SQL to execute
     * @throws java.sql.SQLException thrown if there is any SQL error executing the statement (or creating it)
     */
    private void executeStatement(java.sql.Connection conn, String sql) throws java.sql.SQLException
    {
        //System.out.println("" + Thread.currentThread() + " executing " + sql);
        java.sql.Statement stmt = null;
        try
        {
            stmt = conn.createStatement();
            stmt.execute(sql);
        }
        finally
        {
            if (stmt != null)
            {
                try
                {
                    stmt.close();
                }
                catch (java.sql.SQLException e)
                {
                    System.out.println("Eating close() exception: " + e.getMessage());
                }
            }
        }
    }

    /**
     * This class implements a run procedure to repeatedly create and drop a Derby view.  It is intended to be run
     * on a thread to test whether it is thread-safe in derby to be concurrently creating and dropping views that
     * reference the same
     * underlying database object.
     */
    private class ViewCreatorDropper implements Runnable
    {
        /** The name of the view to create and drop */
        String m_viewName;
        /** The source (view) referenced by the created view */
        String m_sourceName;
        /** The SQL fragment specifying the columns to included in the created view */
        String m_columns;
        /** How many times to create/drop the view */
        int m_iterations;

        /**
         * Constructs the runnable object for the test.
         * @param viewName the name of the view to create and drop
         * @param sourceName the source (view) referenced by the created view
         * @param columns the SQL fragment specifying the columns to included in the created view
         * @param iterations how many times to create/drop the view
         * @throws java.sql.SQLException
         */
        public ViewCreatorDropper(String viewName, String sourceName, String columns, int iterations)
        {
            m_viewName = viewName;
            m_sourceName = sourceName;
            m_columns = columns;
            m_iterations = iterations;
        }

        /**
         * @see Thread#run()
         */
        public void run()
        {
            int i = 0;
            try
            {
                java.sql.Connection conn = getConnection();
                for (i = 0; i < m_iterations; i++)
                {
                    assert conn != null;
                    //if (i % 5 == 0) System.out.println(" (" + Thread.currentThread() + " iteration " + i + ") ");
                    executeStatement(conn, "CREATE VIEW " + m_viewName + " AS SELECT " + m_columns + " FROM " + m_sourceName);
                    executeStatement(conn, "DROP VIEW " + m_viewName);
                }
            }
            catch (java.sql.SQLException e)
            {
                // Grab up all the error message in one string, to guard against output from different threads being
                // interleaved in the console.  (That might happen anyway, but so it goes.)
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                java.io.PrintStream p = new java.io.PrintStream(bos);
                //p.println("" + Thread.currentThread() + " exception after " + i + "iterations:");
                e.printStackTrace(p);
                p.flush();

                String msg = bos.toString();
                System.out.print(msg);

                // If we got the exception we were testing for, just quit.
                if (msg.startsWith("java.lang.NullP"))
                {
                    System.out.println("Stop here.");
                    System.exit(-1);
                }
            }
        }
    }
}
