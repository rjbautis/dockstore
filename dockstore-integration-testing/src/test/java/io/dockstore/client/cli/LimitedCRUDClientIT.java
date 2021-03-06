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

package io.dockstore.client.cli;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.google.common.collect.Lists;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.HostedApi;
import io.swagger.client.model.DockstoreTool;
import io.swagger.client.model.SourceFile;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests CRUD style operations for tools and workflows hosted directly on Dockstore
 *
 * @author dyuen,agduncan
 */
@Category(ConfidentialTest.class)
public class LimitedCRUDClientIT {

    public static final DropwizardTestSupport<DockstoreWebserviceConfiguration> SUPPORT = new DropwizardTestSupport<>(
        DockstoreWebserviceApplication.class, CommonTestUtilities.PUBLIC_CONFIG_PATH);

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    //TODO: duplicates BaseIT but with a different config file, attempt to simplify after release

    @BeforeClass
    public static void dropAndRecreateDB() throws Exception {
        CommonTestUtilities.dropAndRecreateNoTestData(SUPPORT);
        SUPPORT.before();
    }

    @AfterClass
    public static void afterClass(){
        SUPPORT.after();
    }

    @Before
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.dropAndCreateWithTestData(SUPPORT, false);
    }

    @Rule
    public TestRule watcher = new TestWatcher() {
        protected void starting(Description description) {
            System.out.println("Starting test: " + description.getMethodName());
        }
    };


    @Test
    public void testToolCreation(){
        ApiClient webClient = BaseIT.getWebClient(BaseIT.ADMIN_USERNAME);
        HostedApi api = new HostedApi(webClient);
        DockstoreTool hostedTool = api.createHostedTool("awesomeTool", "cwl", "quay.io", "coolNamespace");
        assertNotNull("tool was not created properly", hostedTool);
        // createHostedTool() endpoint is safe to have user profiles because that profile is your own
        assertEquals("One user should belong to this tool, yourself",1, hostedTool.getUsers().size());
        hostedTool.getUsers().forEach(user -> {
            assertNotNull("createHostedTool() endpoint should have user profiles", user.getUserProfiles());
            // Setting it to null afterwards to compare with the getContainer endpoint since that one doesn't return user profiles
            user.setUserProfiles(null);
        });

        assertTrue("tool was not created with a valid id", hostedTool.getId() != 0);
        // can get it back with regular api
        ContainersApi oldApi = new ContainersApi(webClient);
        DockstoreTool container = oldApi.getContainer(hostedTool.getId());
        // clear lazy fields for now till merge
        hostedTool.setAliases(null);
        container.setAliases(null);
        assertEquals(container, hostedTool);
        assertEquals(1, container.getUsers().size());
        container.getUsers().forEach(user -> assertNull("getContainer() endpoint should not have user profiles", user.getUserProfiles()));

        // test repeated workflow creation up to limit
        for(int i = 1; i <= 9; i++) {
            api.createHostedTool("awesomeTool" + i, "cwl", "quay.io", "coolNamespace");
        }

        thrown.expect(ApiException.class);
        api.createHostedTool("awesomeTool" + 10, "cwl", "quay.io", "coolNamespace");
    }

    @Test
    public void testToolVersionCreation() throws IOException {
        ApiClient webClient = BaseIT.getWebClient(BaseIT.ADMIN_USERNAME);
        HostedApi api = new HostedApi(webClient);
        DockstoreTool hostedTool = api.createHostedTool("awesomeTool", "cwl", "quay.io", "coolNamespace");

        SourceFile descriptorFile = new SourceFile();
        descriptorFile.setContent(FileUtils.readFileToString(new File(ResourceHelpers.resourceFilePath("tar-param.cwl")), StandardCharsets.UTF_8));
        descriptorFile.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        descriptorFile.setPath("/Dockstore.cwl");
        SourceFile dockerfile = new SourceFile();
        dockerfile.setContent("FROM ubuntu:latest");
        dockerfile.setType(SourceFile.TypeEnum.DOCKERFILE);
        dockerfile.setPath("/Dockerfile");
        api.editHostedTool(hostedTool.getId(), Lists.newArrayList(descriptorFile, dockerfile));

        // test repeated workflow version creation up to limit
        for(int i = 1; i <= 9; i++) {
            //TODO: this is kind of dumb, we should check for no-change versions as a hotfix
            api.editHostedTool(hostedTool.getId(), Lists.newArrayList(descriptorFile, dockerfile));
        }

        thrown.expect(ApiException.class);
        api.editHostedTool(hostedTool.getId(), Lists.newArrayList(descriptorFile, dockerfile));
    }
}
