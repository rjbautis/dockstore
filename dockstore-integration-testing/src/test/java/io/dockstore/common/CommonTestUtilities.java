/*
 *    Copyright 2018 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.dockstore.common;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.Application;
import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.Executor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author xliu
 */
public final class CommonTestUtilities {

    public final static String OLD_DOCKSTORE_VERSION = "1.4.5";
    private static final Logger LOG = LoggerFactory.getLogger(CommonTestUtilities.class);

    // Travis is slow, need to wait up to 1 min for webservice to return
    public static final int WAIT_TIME = 60000;


    public static final String PUBLIC_CONFIG_PATH = ResourceHelpers.resourceFilePath("dockstore.yml");
    /**
     * confidential testing config, includes keys
     */
    public static final String CONFIDENTIAL_CONFIG_PATH = ResourceHelpers.resourceFilePath("dockstoreTest.yml");
    static final String DUMMY_TOKEN_1 = "08932ab0c9ae39a880905666902f8659633ae0232e94ba9f3d2094cb928397e7";

    private CommonTestUtilities() {

    }

    /**
     * Drops the database and recreates from migrations, not including any test data, using new application
     * @param support reference to testing instance of the dockstore web service
     * @throws Exception
     */
    public static void dropAndRecreateNoTestData(DropwizardTestSupport<DockstoreWebserviceConfiguration> support) throws Exception {
        LOG.info("Dropping and Recreating the database with no test data");
        Application<DockstoreWebserviceConfiguration> application = support.newApplication();
        application.run("db", "drop-all", "--confirm-delete-everything", CONFIDENTIAL_CONFIG_PATH);
        application.run("db", "migrate", CONFIDENTIAL_CONFIG_PATH, "--include", "1.3.0.generated,1.3.1.consistency,1.4.0,1.5.0");
    }

    /**
     * Drops the database and recreates from migrations for non-confidential tests
     * @param support reference to testing instance of the dockstore web service
     * @throws Exception
     */
    public static void dropAndCreateWithTestData(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, boolean isNewApplication) throws Exception {
        LOG.info("Dropping and Recreating the database with non-confidential test data");
        Application<DockstoreWebserviceConfiguration> application;
        if (isNewApplication) {
            application = support.newApplication();
        } else {
            application= support.getApplication();
        }
        application.run("db", "drop-all", "--confirm-delete-everything", CONFIDENTIAL_CONFIG_PATH);
        application.run("db", "migrate", CONFIDENTIAL_CONFIG_PATH, "--include", "1.3.0.generated");
        application.run("db", "migrate", CONFIDENTIAL_CONFIG_PATH, "--include", "1.3.1.consistency");
        application.run("db", "migrate", CONFIDENTIAL_CONFIG_PATH, "--include", "test");
        application.run("db", "migrate", CONFIDENTIAL_CONFIG_PATH, "--include", "1.4.0");
        application.run("db", "migrate", CONFIDENTIAL_CONFIG_PATH, "--include", "1.5.0");
        application.run("db", "migrate", CONFIDENTIAL_CONFIG_PATH, "--include", "test_1.5.0");

    }

    /**
     * Wrapper for dropping and recreating database from migrations for test confidential 1
     * @param support reference to testing instance of the dockstore web service
     * @throws Exception
     */
    public static void cleanStatePrivate1(DropwizardTestSupport<DockstoreWebserviceConfiguration> support) throws Exception {
        LOG.info("Dropping and Recreating the database with confidential 1 test data");
        cleanStatePrivate1(support, CONFIDENTIAL_CONFIG_PATH);
        // TODO: it looks like gitlab's API has gone totally unresponsive, delete after recovery
        // getTestingPostgres().runUpdateStatement("delete from token where tokensource = 'gitlab.com'");
    }

    /**
     * Drops and recreates database from migrations for test confidential 1
     * @param support reference to testing instance of the dockstore web service
     * @param configPath
     * @throws Exception
     */
    private static void cleanStatePrivate1(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, String configPath) throws Exception {
        Application<DockstoreWebserviceConfiguration> application = support.getApplication();
        application.run("db", "drop-all", "--confirm-delete-everything", configPath);
        application.run("db", "migrate", configPath, "--include", "1.3.0.generated");
        application.run("db", "migrate", configPath, "--include", "1.3.1.consistency");
        application.run("db", "migrate", configPath, "--include", "test.confidential1");
        application.run("db", "migrate", configPath, "--include", "1.4.0");
        application.run("db", "migrate", configPath, "--include", "1.5.0");
        application.run("db", "migrate", configPath, "--include", "test.confidential1_1.5.0");
    }

    public static void runMigration(List<String> migrationList, Application<DockstoreWebserviceConfiguration> application, String configPath) {
        migrationList.forEach(migration -> {
            try {
                application.run("db", "migrate", configPath, "--include", migration);
            } catch (Exception e) {
                Assert.fail();
            }
        });
    }

    /**
     * Wrapper fir dropping and recreating database from migrations for test confidential 2
     * @param support reference to testing instance of the dockstore web service
     * @throws Exception
     */
    public static void cleanStatePrivate2(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, boolean isNewApplication) throws Exception {
        LOG.info("Dropping and Recreating the database with confidential 2 test data");
        cleanStatePrivate2(support, CONFIDENTIAL_CONFIG_PATH, isNewApplication);
        // TODO: You can uncomment the following line to disable GitLab tool and workflow discovery
        // getTestingPostgres().runUpdateStatement("delete from token where tokensource = 'gitlab.com'");
    }

    /**
     * Drops and recreates database from migrations for test confidential 2
     * @param support reference to testing instance of the dockstore web service
     * @param configPath
     * @throws Exception
     */
    private static void cleanStatePrivate2(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, String configPath,
        boolean isNewApplication) throws Exception {
        Application<DockstoreWebserviceConfiguration> application;
        if (isNewApplication) {
            application = support.newApplication();
        } else {
            application = support.getApplication();
        }
        application.run("db", "drop-all", "--confirm-delete-everything", configPath);
        application.run("db", "migrate", configPath, "--include", "1.3.0.generated");
        application.run("db", "migrate", configPath, "--include", "1.3.1.consistency");
        application.run("db", "migrate", configPath, "--include", "test.confidential2");
        application.run("db", "migrate", configPath, "--include", "1.4.0");
        application.run("db", "migrate", configPath, "--include", "1.5.0");
        application.run("db", "migrate", configPath, "--include", "test.confidential2_1.5.0");
    }

    /**
     * Loads up a specific set of workflows into the database
     * Specifically for tests toolsIdGet4Workflows() in GA4GHV1IT.java and toolsIdGet4Workflows() in GA4GHV2IT.java
     * @param support reference to testing instance of the dockstore web service
     * @throws Exception
     */
    public static void setupSamePathsTest(DropwizardTestSupport<DockstoreWebserviceConfiguration> support) throws Exception {
        LOG.info("Migrating samepaths migrations");
        Application<DockstoreWebserviceConfiguration> application = support.getApplication();
        application.run("db", "migrate", CONFIDENTIAL_CONFIG_PATH, "--include", "samepaths");
    }


    /**
     * Loads up a specific set of workflows into the database
     * Specifically for tests cwlrunnerWorkflowRelativePathNotEncodedAdditionalFiles in GA4GHV2IT.java
     * @param support reference to testing instance of the dockstore web service
     * @throws Exception
     */
    public static void setupTestWorkflow(DropwizardTestSupport<DockstoreWebserviceConfiguration> support) throws Exception {
        LOG.info("Migrating testworkflow migrations");
        Application<DockstoreWebserviceConfiguration> application = support.getApplication();
        application.run("db", "migrate", CONFIDENTIAL_CONFIG_PATH, "--include", "testworkflow");
    }

    /**
     * Allows tests to clear the database but add basic testing data
     * @return
     */
    public static TestingPostgres getTestingPostgres() {
        final File configFile = FileUtils.getFile("src", "test", "resources", "config");
        final INIConfiguration parseConfig = Utilities.parseConfig(configFile.getAbsolutePath());
        return new TestingPostgres(parseConfig);
    }

    public static ImmutablePair<String, String> runOldDockstoreClient(File dockstore, String[] commandArray) throws RuntimeException {
        List<String> commandList = new ArrayList<>();
        commandList.add(dockstore.getAbsolutePath());
        commandList.addAll(Arrays.asList(commandArray));
        String commandString = String.join(" ", commandList);
        return Utilities.executeCommand(commandString);
    }

    /**
     * For running the old dockstore client when spaces are involved
     * @param dockstore
     * @param commandArray
     * @throws RuntimeException
     */
    public static void runOldDockstoreClientWithSpaces(File dockstore, String[] commandArray) throws RuntimeException {
        List<String> commandList;
        CommandLine commandLine = new CommandLine(dockstore.getAbsoluteFile());

        commandList = Arrays.asList(commandArray);
        commandList.forEach(command -> {
            commandLine.addArgument(command, false);
        });
        Executor executor = new DefaultExecutor();
        try {
            executor.execute(commandLine);
        } catch (IOException e) {
            LOG.error("Could not execute command. " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void checkToolList(String log) {
        Assert.assertTrue(log.contains("NAME"));
        Assert.assertTrue(log.contains("DESCRIPTION"));
        Assert.assertTrue(log.contains("Git Repo"));
    }

    public static class TestingPostgres extends BasicPostgreSQL {

        TestingPostgres(INIConfiguration config) {
            super(config);
        }

        @Override
        public void clearDatabase() {
            super.clearDatabase();
        }

        @Override
        public <T> T runSelectStatement(String query, ResultSetHandler<T> handler, Object... params) {
            return super.runSelectStatement(query, handler, params);
        }

        @Override
        public int runUpdateStatement(String query, Object... params) {
            return super.runUpdateStatement(query, params);
        }
    }
}
