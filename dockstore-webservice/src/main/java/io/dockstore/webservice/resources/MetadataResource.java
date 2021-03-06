/*
 *    Copyright 2017 OICR
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

package io.dockstore.webservice.resources;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.codahale.metrics.annotation.Timed;
import com.google.common.io.Resources;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.PipHelper;
import io.dockstore.common.Registry;
import io.dockstore.common.SourceControl;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.dockstore.webservice.resources.rss.RSSEntry;
import io.dockstore.webservice.resources.rss.RSSFeed;
import io.dockstore.webservice.resources.rss.RSSHeader;
import io.dockstore.webservice.resources.rss.RSSWriter;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import okhttp3.Cache;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author dyuen
 */
@Path("/metadata")
@Api("metadata")
@Produces({MediaType.TEXT_HTML, MediaType.TEXT_XML})
public class MetadataResource {

    private static final Logger LOG = LoggerFactory.getLogger(MetadataResource.class);

    private final ToolDAO toolDAO;
    private final WorkflowDAO workflowDAO;
    private final DockstoreWebserviceConfiguration config;

    public MetadataResource(SessionFactory sessionFactory, DockstoreWebserviceConfiguration config) {
        this.toolDAO = new ToolDAO(sessionFactory);
        this.workflowDAO = new WorkflowDAO(sessionFactory);
        this.config = config;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("sitemap")
    @ApiOperation(value = "List all published workflow and tool paths.", notes = "NO authentication")
    public String sitemap() {
        //TODO needs to be more efficient via JPA query
        List<Tool> tools = toolDAO.findAllPublished();
        List<Workflow> workflows = workflowDAO.findAllPublished();
        StringBuilder builder = new StringBuilder();
        for (Tool tool : tools) {
            builder.append(createToolURL(tool));
            builder.append(System.lineSeparator());
        }
        for (Workflow workflow : workflows) {
            builder.append(createWorkflowURL(workflow));
            builder.append(System.lineSeparator());
        }
        return builder.toString();
    }

    private String createWorkflowURL(Workflow workflow) {
        return config.getExternalConfig().getScheme() + "://" + config.getExternalConfig().getHostname() + (config.getExternalConfig().getUiPort() == null ? "" : ":" + config.getExternalConfig().getUiPort()) + "/workflows/"
                + workflow.getWorkflowPath();
    }

    private String createToolURL(Tool tool) {
        return config.getExternalConfig().getScheme() + "://" + config.getExternalConfig().getHostname() + (config.getExternalConfig().getUiPort() == null ? "" : ":" + config.getExternalConfig().getUiPort())
            + "/containers/" + tool.getToolPath();
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("rss")
    @Produces(MediaType.TEXT_XML)
    @ApiOperation(value = "List all published tools and workflows in creation order.", notes = "NO authentication")
    public String rssFeed() {

        final int limit = 50;
        List<Tool> tools = toolDAO.findAllPublished("0", limit, null, "dbUpdateDate", "desc");
        List<Workflow> workflows = workflowDAO.findAllPublished("0", limit, null, "dbUpdateDate", "desc");

        List<Entry> dbEntries =  new ArrayList<>();
        dbEntries.addAll(tools);
        dbEntries.addAll(workflows);
        dbEntries.sort(Comparator.comparingLong(entry -> entry.getLastUpdated().getTime()));

        // TODO: after seeing if this works, make this more efficient than just returning everything
        RSSFeed feed = new RSSFeed();

        RSSHeader header = new RSSHeader();
        header.setCopyright("Copyright 2018 OICR");
        header.setTitle("Dockstore");
        header.setDescription("Dockstore, developed by the Cancer Genome Collaboratory, is an open platform used by the GA4GH for sharing Docker-based tools described with either the Common Workflow Language (CWL) or the Workflow Description Language (WDL).");
        header.setLanguage("en");
        header.setLink("https://dockstore.org/");
        header.setPubDate(RSSFeed.formatDate(Calendar.getInstance()));

        feed.setHeader(header);

        List<RSSEntry> entries = new ArrayList<>();
        for (Entry dbEntry : dbEntries) {
            RSSEntry entry = new RSSEntry();
            if (dbEntry instanceof Workflow) {
                Workflow workflow = (Workflow)dbEntry;
                Optional<WorkflowVersion> max = workflow.getWorkflowVersions().stream().filter(v -> v.getDbUpdateDate() != null)
                    .max(Comparator.comparing(Version::getDbUpdateDate));
                entry.setTitle(workflow.getWorkflowPath() + (max.map(workflowVersion -> ":" + workflowVersion.getName()).orElse("")));
                String workflowURL = createWorkflowURL(workflow);
                entry.setGuid(workflowURL);
                entry.setLink(workflowURL);
            } else if (dbEntry instanceof Tool) {
                Tool tool = (Tool)dbEntry;
                Optional<Tag> max = tool.getTags().stream().filter(v -> v.getDbUpdateDate() != null)
                    .max(Comparator.comparing(Version::getDbUpdateDate));
                entry.setTitle(tool.getPath() + (max.map(tag -> ":" + tag.getName()).orElse("")));
                String toolURL = createToolURL(tool);
                entry.setGuid(toolURL);
                entry.setLink(toolURL);
            } else {
                throw new CustomWebApplicationException("Unknown data type unsupported for RSS feed.", HttpStatus.SC_INTERNAL_SERVER_ERROR);
            }
            final int arbitraryDescriptionLimit = 200;
            entry.setDescription(StringUtils.truncate(dbEntry.getDescription(), arbitraryDescriptionLimit));
            Calendar instance = Calendar.getInstance();
            instance.setTime(dbEntry.getLastUpdated());
            entry.setPubDate(RSSFeed.formatDate(instance));
            entries.add(entry);
        }
        feed.setEntries(entries);

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            RSSWriter.write(feed, byteArrayOutputStream);
            return byteArrayOutputStream.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            throw new CustomWebApplicationException("Could not write RSS feed.", HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @GET
    @Produces({ "text/plain", "application/json" })
    @Path("/runner_dependencies")
    @ApiOperation(value = "Returns the file containing runner dependencies.", response = String.class)
    public Response getRunnerDependencies(
            @ApiParam(value = "The Dockstore client version") @QueryParam("client_version") String clientVersion,
            @ApiParam(value = "Python version, only relevant for the cwltool runner") @DefaultValue("2") @QueryParam("python_version") String pythonVersion,
            @ApiParam(value = "The tool runner", allowableValues = "cwltool") @DefaultValue("cwltool") @QueryParam("runner") String runner,
            @ApiParam(value = "Response type", allowableValues = "json, text") @DefaultValue("text") @QueryParam("output") String output,
            @Context ContainerRequestContext containerRequestContext) {
        if (!("cwltool").equals(runner)) {
            return Response.noContent().build();
        }
        boolean unwrap = !("json").equals(output);
        String fileVersion = PipHelper.convertSemVerToAvailableVersion(clientVersion);
        try {
            String content = Resources.toString(this.getClass().getClassLoader()
                    .getResource("requirements/" + fileVersion + "/requirements" + (pythonVersion.startsWith("3") ? "3" : "") + ".txt"), StandardCharsets.UTF_8);
            Map<String, String> pipDepMap = PipHelper.convertPipRequirementsStringToMap(content);
            return Response.status(Response.Status.OK).type(unwrap ? MediaType.TEXT_PLAIN : MediaType.APPLICATION_JSON)
                    .entity(unwrap ? content : pipDepMap).build();
        } catch (IOException e) {
            throw new CustomWebApplicationException("Could not retrieve runner dependencies file: " + e.getMessage(), HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/sourceControlList")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get the list of source controls supported on Dockstore.", notes = "NO authentication", response = SourceControl.SourceControlBean.class, responseContainer = "List")
    public List<SourceControl.SourceControlBean> getSourceControlList() {
        List<SourceControl.SourceControlBean> sourceControlList = new ArrayList<>();
        Arrays.asList(SourceControl.values()).forEach(sourceControl -> sourceControlList.add(new SourceControl.SourceControlBean(sourceControl)));
        return sourceControlList;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/dockerRegistryList")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get the list of docker registries supported on Dockstore.", notes = "NO authentication", response = Registry.RegistryBean.class, responseContainer = "List")
    public List<Registry.RegistryBean> getDockerRegistries() {
        List<Registry.RegistryBean> registryList = new ArrayList<>();
        Arrays.asList(Registry.values()).forEach(registry -> registryList.add(new Registry.RegistryBean(registry)));
        return registryList;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/descriptorLanguageList")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get the list of descriptor languages supported on Dockstore.", notes = "NO authentication", response = DescriptorLanguage.DescriptorLanguageBean.class, responseContainer = "List")
    public List<DescriptorLanguage.DescriptorLanguageBean> getDescriptorLanguages() {
        List<DescriptorLanguage.DescriptorLanguageBean> descriptorLanguageList = new ArrayList<>();
        Arrays.asList(DescriptorLanguage.values()).forEach(descriptorLanguage -> descriptorLanguageList.add(new DescriptorLanguage.DescriptorLanguageBean(descriptorLanguage)));
        return descriptorLanguageList;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/okHttpCachePerformance")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get measures of cache performance.", notes = "NO authentication", response = Map.class, responseContainer = "List")
    public Map<String, String> getCachePerformance() {
        return extractCacheStatistics();
    }

    public static Map<String, String> extractCacheStatistics() {
        Cache cache = DockstoreWebserviceApplication.getCache();
        Map<String, String> results = new HashMap<>();
        results.put("requestCount", String.valueOf(cache.requestCount()));
        results.put("networkCount", String.valueOf(cache.networkCount()));
        results.put("hitCount", String.valueOf(cache.hitCount()));
        results.put("maxSize", String.valueOf(cache.maxSize()) + " bytes");
        try {
            results.put("size", String.valueOf(cache.size()) + " bytes");
        } catch (IOException e) {
            /* do nothing if we cannot report size */
            LOG.warn("unable to determine cache size, may not have initialized yet");
        }
        return results;
    }

}
