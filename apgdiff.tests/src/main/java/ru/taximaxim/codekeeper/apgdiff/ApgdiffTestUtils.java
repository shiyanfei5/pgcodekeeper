package ru.taximaxim.codekeeper.apgdiff;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;

import org.junit.Assert;

import cz.startnet.utils.pgdiff.PgDiffArguments;
import cz.startnet.utils.pgdiff.TEST;
import cz.startnet.utils.pgdiff.loader.JdbcConnector;
import cz.startnet.utils.pgdiff.loader.JdbcLoaderTest;
import cz.startnet.utils.pgdiff.loader.JdbcRunner;
import cz.startnet.utils.pgdiff.loader.PgDumpLoader;
import cz.startnet.utils.pgdiff.schema.PgDatabase;
import ru.taximaxim.codekeeper.apgdiff.ApgdiffConsts.JDBC_CONSTS;
import ru.taximaxim.codekeeper.apgdiff.licensing.License;
import ru.taximaxim.codekeeper.apgdiff.licensing.LicenseException;

public final class ApgdiffTestUtils {

    private static final String TEST_LICENSE = "testlic";

    public static PgDatabase loadTestDump(String resource, Class<?> c, PgDiffArguments args)
            throws IOException, InterruptedException, LicenseException {
        try (PgDumpLoader loader = new PgDumpLoader(c.getResourceAsStream(resource),
                "test:/" + c.getName() + '/' + resource, args)) {
            return loader.load();
        }
    }

    public static void fillDB(String dbName) throws IOException {
        fillDB(dbName, JdbcLoaderTest.class.getResourceAsStream(TEST.RESOURCE_DUMP));
    }

    public static void fillDB(String dbName, InputStream in) throws IOException {
        JdbcConnector connector = new JdbcConnector(TEST.REMOTE_HOST, TEST.REMOTE_PORT,
                TEST.REMOTE_USERNAME, TEST.REMOTE_PASSWORD, dbName, ApgdiffConsts.UTC);
        // dump schemas back
        try (InputStreamReader isr = new InputStreamReader(in, "UTF-8");
                BufferedReader reader = new BufferedReader(isr)) {

            StringBuilder script = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                script.append(line);
                script.append("\n");
            }

            String res = new JdbcRunner(connector).runScript(script.toString());
            Assert.assertEquals("DDL update over JDBC exited with an error: "
                    + res, JDBC_CONSTS.JDBC_SUCCESS, res);
        }
    }

    public static void dropDB(String dbName) throws IOException {
        JdbcConnector connector = new JdbcConnector(TEST.REMOTE_HOST, TEST.REMOTE_PORT,
                TEST.REMOTE_USERNAME, TEST.REMOTE_PASSWORD, TEST.REMOTE_DB, ApgdiffConsts.UTC);
        String res = new JdbcRunner(connector).runScript("DROP DATABASE " + dbName);
        Assert.assertEquals("DB cleanup script returned an error: " + res,
                JDBC_CONSTS.JDBC_SUCCESS, res);
    }

    public static PgDiffArguments getArgsLicensed() throws IOException, LicenseException {
        PgDiffArguments args = new PgDiffArguments();
        setLicense(args);
        return args;
    }

    public static URL getTestLicenseUrl() {
        return ApgdiffTestUtils.class.getResource(TEST_LICENSE);
    }

    public static void setLicense(PgDiffArguments args) throws IOException, LicenseException {
        // NOTE: TEST_LICENSE must provide full capabilities
        // so that GUI/CLI and other potential mode selections won't matter
        args.setLicense(new License(ApgdiffTestUtils.class.getResourceAsStream(TEST_LICENSE)));
    }

    private ApgdiffTestUtils() {
    }
}
