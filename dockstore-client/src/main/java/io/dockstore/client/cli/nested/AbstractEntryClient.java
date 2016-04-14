/*
 *    Copyright 2016 OICR
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

package io.dockstore.client.cli.nested;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.tuple.ImmutablePair;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.gson.Gson;

import io.cwl.avro.CWL;
import io.dockstore.client.Bridge;
import io.swagger.client.model.Label;
import io.swagger.client.ApiException;
import io.swagger.client.model.SourceFile;


import static io.dockstore.client.cli.ArgumentUtility.CWL_STRING;
import static io.dockstore.client.cli.ArgumentUtility.WDL_STRING;
import static io.dockstore.client.cli.ArgumentUtility.LAUNCH;
import static io.dockstore.client.cli.ArgumentUtility.CONVERT;
import static io.dockstore.client.cli.ArgumentUtility.containsHelpRequest;
import static io.dockstore.client.cli.ArgumentUtility.errorMessage;
import static io.dockstore.client.cli.ArgumentUtility.invalid;
import static io.dockstore.client.cli.ArgumentUtility.optVal;
import static io.dockstore.client.cli.ArgumentUtility.optVals;
import static io.dockstore.client.cli.ArgumentUtility.out;
import static io.dockstore.client.cli.ArgumentUtility.printHelpFooter;
import static io.dockstore.client.cli.ArgumentUtility.printHelpHeader;
import static io.dockstore.client.cli.ArgumentUtility.reqVal;
import static io.dockstore.client.cli.Client.CLIENT_ERROR;

/**
 * Handles the commands for a particular type of entry. (e.g. Workflows, Tools) Not a great abstraction, but enforces some structure for
 * now.
 *
 * The goal here should be to gradually work toward an interface that removes those pesky command line arguments (List<String> args) from
 * implementing classes that do not need to reference to the command line arguments directly.
 *
 * Note that many of these methods depend on a unique identifier for an entry called a path for workflows and tools.
 * For example, a tool path looks like quay.io/collaboratory/bwa-tool:develop wheras a workflow path looks like
 * collaboratory/bwa-workflow:develop
 *
 * @author dyuen
 */
public abstract class AbstractEntryClient {
    protected final CWL cwlUtil = new CWL();

    /**
     * Print help for this group of commands.
     */
    public void printGeneralHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " [flags] [command] [command parameters]");
        out("");
        out("Commands:");
        out("");
        out("  list             :  lists all the " + getEntryType() + "s published by the user");
        out("");
        out("  search           :  allows a user to search for all published " + getEntryType() + "s that match the criteria");
        out("");
        out("  publish          :  publish/unpublish a " + getEntryType() + " in the dockstore");
        out("");
        out("  info             :  print detailed information about a particular published " + getEntryType());
        out("");
        out("  " + CWL_STRING + "              :  returns the Common Workflow Language " + getEntryType() + " definition for this entry");
        out("                      which enables integration with Global Alliance compliant systems");
        out("");
        out("  " + WDL_STRING + "              :  returns the Workflow Descriptor Langauge definition for this Docker image.");
        out("");
        out("  refresh          :  updates your list of " + getEntryType() + "s stored on Dockstore or an individual " + getEntryType());
        out("");
        out("  label            :  updates labels for an individual " + getEntryType() + "");
        out("");
        out("  " + CONVERT + "          :  utilities that allow you to convert file types");
        out("");
        out("  " + LAUNCH + "           :  launch " + getEntryType() + "s (locally)");
        out("");
        printClientSpecificHelp();
        out("------------------");
        out("");
        out("Flags:");
        out("  --help               Print help information");
        out("                       Default: false");
        out("  --debug              Print debugging information");
        out("                       Default: false");
        out("  --version            Print dockstore's version");
        out("                       Default: false");
        out("  --server-metadata    Print metdata describing the dockstore webservice");
        out("                       Default: false");
        out("  --upgrade            Upgrades to the latest stable release of Dockstore");
        out("                       Default: false");
        out("  --config <file>      Override config file");
        out("                       Default: ~/.dockstore/config");
        out("  --script             Will not check Github for newer versions of Dockstore");
        out("                       Default: false");
        printHelpFooter();
    }

    /**
     * Print help for commands specific to this client type.
     */
    protected abstract void printClientSpecificHelp();

    /**
     * A friendly description for the type of entry that this handles. Damn you type erasure.
     *
     * @return string to use in descriptions and help output
     */
    protected abstract String getEntryType();

    /**
     * A default implementation to process the commands that are common between types of entries. (i.e. both workflows and tools need to be
     * published and labelled)
     *
     * @param args
     *            the arguments yet to be processed
     * @param activeCommand
     *            the current command that we're interested in
     * @return whether this interface handled the active command
     */
    public final boolean processEntryCommands(List<String> args, String activeCommand) throws IOException, ApiException{
        if (null != activeCommand) {
            // see if it is a command specific to this kind of Entry
            boolean processed = processEntrySpecificCommands(args, activeCommand);
            if (processed) {
                return true;
            }

            switch (activeCommand) {
            case "info":
                info(args);
                break;
            case "list":
                list(args);
                break;
            case "search":
                search(args);
                break;
            case "publish":
                publish(args);
                break;
            case WDL_STRING:
                descriptor(args, WDL_STRING);
                break;
            case CWL_STRING:
                descriptor(args, CWL_STRING);
                break;
            case "refresh":
                refresh(args);
                break;
            case "label":
                label(args);
                break;
            case "manual_publish":
                manualPublish(args);
                break;
            case "convert":
                convert(args);
                break;
            default:
                return false;
            }
            return true;
        }
        return false;
    }

    /**
     * Handle search for an entry
     *
     * @param pattern
     *            a pattern, currently a subtring for searching
     */
    protected abstract void handleSearch(String pattern);

    /**
     * Handle the actual labelling
     *
     * @param entryPath
     *            a unique identifier for an entry, called a path for workflows and tools
     * @param addsSet
     *            the set of labels that we wish to add
     * @param removesSet
     *            the set of labels that we wish to delete
     */
    protected abstract void handleLabels(String entryPath, Set<String> addsSet, Set<String> removesSet);

    /**
     * Handle output for a type of entry
     *
     * @param entryPath
     *            a unique identifier for an entry, called a path for workflows and tools
     */
    protected abstract void handleInfo(String entryPath);

    /**
     * Refresh all entries of this type.
     */
    protected abstract void refreshAllEntries();

    /**
     * Refresh a specific entry of this type.
     *
     * @param toolpath
     *            a unique identifier for an entry, called a path for workflows and tools
     */
    protected abstract void refreshTargetEntry(String toolpath);

    /**
     * Grab the descriptor for an entry. TODO: descriptorType should probably be an enum, may need to play with generics to make it
     * dependent on the type of entry
     *
     * @param descriptorType
     *            type of descriptor
     * @param entry
     *            a unique identifier for an entry, called a path for workflows and tools ex:
     *            quay.io/collaboratory/seqware-bwa-workflow:develop for a tool
     */
    protected abstract void handleDescriptor(String descriptorType, String entry);

    /**
     *
     * @param entryPath a unique identifier for an entry, called a path for workflows and tools
     * @param newName take entryPath and rename its most specific name (ex: toolName for tools) to newName
     * @param unpublishRequest true to publish, false to unpublish
     */
    protected abstract void handlePublishUnpublish(String entryPath, String newName, boolean unpublishRequest);

    /**
     * List all of the entries published and unpublished for this user
     */
    protected abstract void handleListNonpublishedEntries();

    /**
     * List all of the published entries of this type for this user
     */
    protected abstract void handleList();

    /**
     * Process commands that are specific to this kind of entry (tools, workflows).
     *
     * @param args
     *            remaining command segment
     * @return true iff this handled the command
     */
    protected abstract boolean processEntrySpecificCommands(List<String> args, String activeCommand);

    /**
     * Manually publish a given entry
     *
     * @param args
     */
    protected abstract void manualPublish(final List<String> args);

    protected abstract SourceFile getDescriptorFromServer(String entry, String descriptorType) throws
            ApiException, IOException;

    protected abstract String getEntryGitRegistry(String entry) throws ApiException;

    /** private helper methods */

    public void publish(List<String> args) {
        if (args.isEmpty()) {
            handleListNonpublishedEntries();
        } else if (containsHelpRequest(args)) {
            publishHelp();
        } else {
            String first = reqVal(args, "--entry");
            String entryname = optVal(args, "--entryname", null);
            final boolean unpublishRequest = isUnpublishRequest(args);
            handlePublishUnpublish(first, entryname, unpublishRequest);
        }
    }

    private static boolean isUnpublishRequest(List<String> args) {
        boolean unpublish = false;
        for (String arg : args) {
            if ("--unpub".equals(arg)) {
                unpublish = true;
            }
        }
        return unpublish;
    }

    private void list(List<String> args) {
        if (containsHelpRequest(args)) {
            listHelp();
        } else {
            handleList();
        }
    }

    private void descriptor(List<String> args, String descriptorType) {
        if (args.isEmpty() || containsHelpRequest(args)) {
            descriptorHelp(descriptorType);
        } else {
            final String entry = reqVal(args, "--entry");
            handleDescriptor(descriptorType, entry);
        }
    }

    private void refresh(List<String> args) {
        if (containsHelpRequest(args)) {
            refreshHelp();
        } else if (!args.isEmpty()) {
            final String toolpath = reqVal(args, "--entry");
            refreshTargetEntry(toolpath);
        } else {
            // check user info after usage so that users can get usage without live webservice
            refreshAllEntries();
        }
    }

    private void info(List<String> args) {
        if (args.isEmpty() || containsHelpRequest(args)) {
            infoHelp();
        } else {
            String path = reqVal(args, "--entry");
            handleInfo(path);
        }
    }

    private void label(List<String> args) {
        if (args.isEmpty() || containsHelpRequest(args)) {
            labelHelp();
        } else {
            final String toolpath = reqVal(args, "--entry");
            final List<String> adds = optVals(args, "--add");
            final Set<String> addsSet = adds.isEmpty() ? new HashSet<>() : new HashSet<>(adds);
            final List<String> removes = optVals(args, "--remove");
            final Set<String> removesSet = removes.isEmpty() ? new HashSet<>() : new HashSet<>(removes);

            // Do a check on the input
            final String labelStringPattern = "^[a-zA-Z0-9]+(-[a-zA-Z0-9]+)*$";

            for (String add : addsSet) {
                if (!add.matches(labelStringPattern)) {
                    errorMessage("The following label does not match the proper label format : " + add, CLIENT_ERROR);
                } else if (removesSet.contains(add)) {
                    errorMessage("The following label is present in both add and remove : " + add, CLIENT_ERROR);
                }
            }

            for (String remove : removesSet) {
                if (!remove.matches(labelStringPattern)) {
                    errorMessage("The following label does not match the proper label format : " + remove, CLIENT_ERROR);
                }
            }
            handleLabels(toolpath, addsSet, removesSet);
        }
    }

    /*
    Generate label string given add set, remove set, and existing labels
      */
    public String generateLabelString(Set<String> addsSet, Set<String> removesSet, List<Label> existingLabels) {
        Set<String> newLabelSet = new HashSet<String>();

        // Get existing labels and store in a List
        for (Label existingLabel : existingLabels) {
            newLabelSet.add(existingLabel.getValue());
        }

        // Add new labels to the List of labels
        for (String add : addsSet) {
            final String label = add.toLowerCase();
            newLabelSet.add(label);
        }
        // Remove labels from the list of labels
        for (String remove : removesSet) {
            final String label = remove.toLowerCase();
            newLabelSet.remove(label);
        }

        return Joiner.on(",").join(newLabelSet);
    }

    private void search(List<String> args) {
        if (args.isEmpty() || containsHelpRequest(args)) {
            searchHelp();
        } else {
            String pattern = reqVal(args, "--pattern");
            handleSearch(pattern);
        }
    }

    private void convert(final List<String> args) throws ApiException, IOException {
        if (args.isEmpty()
                || (containsHelpRequest(args) && !args.contains("cwl2json") && !args.contains("wdl2json") && !args.contains("entry2json") && !args
                .contains("entry2tsv"))) {
            convertHelp(); // Display general help
        } else {
            final String cmd = args.remove(0);
            if (null != cmd) {
                switch (cmd) {
                case "cwl2json":
                    cwl2json(args);
                    break;
                case "wdl2json":
                    wdl2json(args);
                    break;
                case "entry2json":
                    handleEntry2json(args);
                    break;
                case "entry2tsv":
                    handleEntry2tsv(args);
                    break;
                default:
                    invalid(cmd);
                    break;
                }
            }
        }
    }

    private void cwl2json(final List<String> args) throws ApiException, IOException {
        if (args.isEmpty() || containsHelpRequest(args)) {
            cwl2jsonHelp();
        } else {
            final String cwlPath = reqVal(args, "--cwl");
            final ImmutablePair<String, String> output = cwlUtil.parseCWL(cwlPath, true);

            final Gson gson = io.cwl.avro.CWL.getTypeSafeCWLToolDocument();
            final Map<String, Object> runJson = cwlUtil.extractRunJson(output.getLeft());
            out(gson.toJson(runJson));
        }
    }

    private void wdl2json(final List<String> args) throws ApiException, IOException {
        if (args.isEmpty() || containsHelpRequest(args)) {
            wdl2jsonHelp();
        } else {
            // Will eventually need to update this to use wdltool
            final String wdlPath = reqVal(args, "--wdl");
            File wdlFile = new File(wdlPath);
            final List<String> wdlDocuments = Lists.newArrayList(wdlFile.getAbsolutePath());
            final scala.collection.immutable.List<String> wdlList = scala.collection.JavaConversions.asScalaBuffer(wdlDocuments).toList();
            Bridge bridge = new Bridge();
            String inputs = bridge.inputs(wdlList);
            out(inputs);
        }
    }

    private void handleEntry2json(List<String> args) throws ApiException, IOException {
        if (args.isEmpty() || containsHelpRequest(args)) {
            entry2jsonHelp();
        } else {
            final String runString = runString(args, true);
            out(runString);
        }
    }

    private void handleEntry2tsv(List<String> args) throws ApiException, IOException {
        if (args.isEmpty() || containsHelpRequest(args)) {
            entry2tsvHelp();
        } else {
            final String runString = runString(args, false);
            out(runString);
        }
    }

    /**
     * Wrapper for finding and downloading import files
     * @param entryPath
     * @param descriptor
     * @param entry
         * @throws ApiException
         */
    protected void handleImports(String entryPath, String descriptor, String entry) throws ApiException{
        List<String> imports = new ArrayList<>();
        if (descriptor.equals(CWL_STRING)) {
            imports = getWdlImports(entryPath);
        } else if (descriptor.equals(WDL_STRING)) {
            imports = getCwlImports(entryPath);
        }

        String gitRegistry = getEntryGitRegistry(entry.split(":")[0]);
        out("Git registry is - " + gitRegistry);
        importFilesDownload(imports, gitRegistry);
    }

    /**
     * Given a list of file paths (assumes github or bitbucket), will download the files to the current working directory
     * TODO : an idea might be for it to take the 'base path' of a site, that will make it easier to find
     * or base path can be the path minus the given cwl or wdl
     * @param filesToDownload
         */
    private void importFilesDownload(List<String> filesToDownload, String gitRegistry) {
        if (gitRegistry.equals("bitbucket")) {
            for (String file : filesToDownload) {
                out("Downloading - " + file);
            }
        } else if (gitRegistry.equals("github")) {
            for (String file : filesToDownload) {
                out("Downloading - " + file);
            }
        }
    }

    /**
     * Parses WDL file to make a list of files that need importing (relative paths and absolute)
     * @param filepath
     * @return
         */
    private List<String> getWdlImports(String filepath) {

        return new ArrayList<>();
    }

    /**
     * Parses CWL file to make a list of files that need importing (relative paths and absolute)
     * @param filepath
     * @return
         */
    private List<String> getCwlImports(String filepath) {
        return new ArrayList<>();
    }

    protected String runString(List<String> args, final boolean json) throws
            ApiException, IOException {
        final String entry = reqVal(args, "--entry");
        final String descriptor = optVal(args, "--descriptor", CWL_STRING);

        final SourceFile descriptorFromServer = getDescriptorFromServer(entry, descriptor);
        final File tempDescriptor = File.createTempFile("temp", ".cwl", Files.createTempDir());
        Files.write(descriptorFromServer.getContent(), tempDescriptor, StandardCharsets.UTF_8);

        handleImports(tempDescriptor.getAbsolutePath(), descriptor, entry);

        if (descriptor.equals(CWL_STRING)) {
            // need to suppress output
            final ImmutablePair<String, String> output = cwlUtil.parseCWL(tempDescriptor.getAbsolutePath(), true);
            final Map<String, Object> stringObjectMap = cwlUtil.extractRunJson(output.getLeft());
            if (json) {
                final Gson gson = CWL.getTypeSafeCWLToolDocument();
                return gson.toJson(stringObjectMap);
            } else {
                // re-arrange as rows and columns
                final Map<String, String> typeMap = cwlUtil.extractCWLTypes(output.getLeft());
                final List<String> headers = new ArrayList<>();
                final List<String> types = new ArrayList<>();
                final List<String> entries = new ArrayList<>();
                for (final Map.Entry<String, Object> objectEntry : stringObjectMap.entrySet()) {
                    headers.add(objectEntry.getKey());
                    types.add(typeMap.get(objectEntry.getKey()));
                    Object value = objectEntry.getValue();
                    if (value instanceof Map) {
                        Map map = (Map) value;
                        if (map.containsKey("class") && "File".equals(map.get("class"))) {
                            value = map.get("path");
                        }

                    }
                    entries.add(value.toString());
                }
                final StringBuffer buffer = new StringBuffer();
                try (CSVPrinter printer = new CSVPrinter(buffer, CSVFormat.DEFAULT)) {
                    printer.printRecord(headers);
                    printer.printComment("do not edit the following row, describes CWL types");
                    printer.printRecord(types);
                    printer.printComment("duplicate the following row and fill in the values for each run you wish to set parameters for");
                    printer.printRecord(entries);
                }
                return buffer.toString();
            }
        } else if (descriptor.equals(WDL_STRING)) {
            if (json) {
                final List<String> wdlDocuments = Lists.newArrayList(tempDescriptor.getAbsolutePath());
                final scala.collection.immutable.List<String> wdlList = scala.collection.JavaConversions.asScalaBuffer(wdlDocuments)
                        .toList();
                Bridge bridge = new Bridge();
                return bridge.inputs(wdlList);
            }
        }
        return null;
    }


    /** help text output */

    private void publishHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " publish --help");
        out("       dockstore " + getEntryType().toLowerCase() + " publish");
        out("       dockstore " + getEntryType().toLowerCase() + " publish [parameters]");
        out("       dockstore " + getEntryType().toLowerCase() + " publish --unpub [parameters]");
        out("");
        out("Description:");
        out("  Publish/unpublish a registered " + getEntryType() + ".");
        out("  No arguments will list the current and potential " + getEntryType() + "s to share.");
        out("Optional Parameters:");
        out("  --entry <entry>             Complete " + getEntryType() + " path in the Dockstore");
        out("  --entryname <" + getEntryType() + "name>       " + getEntryType() + "name of new entry");
        printHelpFooter();
    }

    private void listHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " list --help");
        out("       dockstore " + getEntryType().toLowerCase() + " list");
        out("");
        out("Description:");
        out("  lists all the " + getEntryType() + " published by the user");
        printHelpFooter();
    }

    private void labelHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " label --help");
        out("       dockstore " + getEntryType().toLowerCase() + " label [parameters]");
        out("");
        out("Description:");
        out("  Add or remove labels from a given Dockstore " + getEntryType());
        out("");
        out("Required Parameters:");
        out("  --entry <entry>                             Complete " + getEntryType() + " path in the Dockstore");
        out("");
        out("Optional Parameters:");
        out("  --add <label> (--add <label>)               Add given label(s)");
        out("  --remove <label> (--remove <label>)         Remove given label(s)");
        printHelpFooter();
    }

    private void infoHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " info --help");
        out("       dockstore " + getEntryType().toLowerCase() + " info [parameters]");
        out("");
        out("Description:");
        out("  Get information related to a published " + getEntryType());
        out("");
        out("Required Parameters:");
        out("  --entry <entry>     The complete " + getEntryType() + " path in the Dockstore.");
        printHelpFooter();
    }

    private void descriptorHelp(String descriptorType) {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " " + descriptorType + " --help");
        out("       dockstore " + getEntryType().toLowerCase() + " " + descriptorType + " [parameters]");
        out("");
        out("Description:");
        out("  Grab a " + descriptorType + " document for a particular entry");
        out("");
        out("Required parameters:");
        out("  --entry <entry>              Complete " + getEntryType()
                + " path in the Dockstore ex: quay.io/collaboratory/seqware-bwa-workflow:develop ");
        printHelpFooter();
    }

    private void refreshHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " refresh --help");
        out("       dockstore " + getEntryType().toLowerCase() + " refresh");
        out("       dockstore " + getEntryType().toLowerCase() + " refresh [parameters]");
        out("");
        out("Description:");
        out("  Refresh an individual " + getEntryType() + " or all your " + getEntryType() + ".");
        out("");
        out("Optional Parameters:");
        out("  --entry <entry>         Complete tool path in the Dockstore");
        printHelpFooter();
    }

    private void searchHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " search --help");
        out("       dockstore " + getEntryType().toLowerCase() + " search [parameters]");
        out("");
        out("Description:");
        out("  Search for published " + getEntryType() + " on Dockstore.");
        out("");
        out("Required Parameters:");
        out("  --pattern <pattern>         Pattern to search Dockstore with.");
        printHelpFooter();
    }

    private void cwl2jsonHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " --help");
        out("       dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " cwl2json [parameters]");
        out("");
        out("Description:");
        out("  Spit out a json run file for a given cwl document.");
        out("");
        out("Required parameters:");
        out("  --cwl <file>                Path to cwl file");
        printHelpFooter();
    }

    private void wdl2jsonHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " --help");
        out("       dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " wdl2json [parameters]");
        out("");
        out("Description:");
        out("  Spit out a json run file for a given wdl document.");
        out("");
        out("Required parameters:");
        out("  --wdl <file>                Path to wdl file");
        printHelpFooter();
    }

    private void convertHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " --help");
        out("       dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " cwl2json [parameters]");
        out("       dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " wdl2json [parameters]");
        out("       dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " entry2json [parameters]");
        out("       dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " entry2tsv [parameters]");
        out("");
        out("Description:");
        out("  These are preview features that will be finalized for the next major release.");
        out("  They allow you to convert between file representations.");
        printHelpFooter();
    }

    private void entry2tsvHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " entry2tsv --help");
        out("       dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " entry2tsv [parameters]");
        out("");
        out("Description:");
        out("  Spit out a tsv run file for a given cwl document.");
        out("");
        out("Required parameters:");
        out("  --entry <entry>                Complete " + getEntryType().toLowerCase() + " path in the Dockstore");
        printHelpFooter();
    }

    private void entry2jsonHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " entry2json --help");
        out("       dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " entry2json [parameters]");
        out("");
        out("Description:");
        out("  Spit out a json run file for a given cwl document.");
        out("");
        out("Required parameters:");
        out("  --entry <entry>                Complete " + getEntryType().toLowerCase() + " path in the Dockstore");
        out("  --descriptor <descriptor>      Type of descriptor language used. Defaults to cwl");
        printHelpFooter();
    }

}
