/*
 * #%L
 * Wisdom-Framework
 * %%
 * Copyright (C) 2013 - 2014 Wisdom Framework
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

package org.wisdom.ractivejs;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.wisdom.maven.Constants;
import org.wisdom.maven.WatchingException;
import org.wisdom.maven.mojos.AbstractWisdomWatcherMojo;
import org.wisdom.maven.node.LoggedOutputStream;
import org.wisdom.maven.node.NPM;
import org.wisdom.maven.utils.WatcherUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import static org.apache.commons.io.FileUtils.copyInputStreamToFile;
import static org.wisdom.maven.node.NPM.npm;

/**
 * Compiles Ractive.js template to JavaScript.
 * <p/>
 * All '.ract' files from the 'src/main/resources/assets' directory are compiled to 'target/classes/',
 * while the one from 'src/main/assets/' are compiled to 'target/wisdom/assets'.
 *
 * @author barjo
 */
@Mojo(name = "compile-ractivejs", threadSafe = false,
        requiresDependencyResolution = ResolutionScope.COMPILE,
        requiresProject = true,
        defaultPhase = LifecyclePhase.COMPILE)
public class RactiveJsTemplateCompilerMojo extends AbstractWisdomWatcherMojo implements Constants {

    public static final String RACTIVE_EXTENSION = "ract";
    public static final String RACTIVE_SCRIPT_NPM_NAME = "ractive";
    public static final String RACTIVE_SCRIPT_FILE = File.separator+"ractive";
    private File ractiveExec ;
    private File ractiveModule;

    private File internalSources;
    private File destinationForInternals;
    private File externalSources;
    private File destinationForExternals;
    private NPM ractive;

    /**
     * The Ractive.js version.
     * It must be a version available from the NPM registry
     *
     * @see <a href="https://www.npmjs.org/">NPM Web Site</a>.
     */
    @Parameter(defaultValue = "0.7.3")
    String ractiveJsVersion;



    /**
     * Install ractive npm module.
     * Parse all ractive template available in the assets and resources assets directories.
     *
     * @throws MojoExecutionException
     */
    @Override
    public void execute() throws MojoExecutionException {
        //Copy ractiveExec to .wisdom folder
        copyScriptInDotWisdom();

        this.internalSources = new File(basedir, MAIN_RESOURCES_DIR);
        this.destinationForInternals = new File(buildDirectory, "classes");

        this.externalSources = new File(basedir, ASSETS_SRC_DIR);
        this.destinationForExternals = new File(getWisdomRootDirectory(), ASSETS_DIR);

        //load the ractivejs from npm
        ractive = npm(this, RACTIVE_SCRIPT_NPM_NAME, ractiveJsVersion);

        ractiveModule = new File(getNodeManager().getNodeModulesDirectory(),RACTIVE_SCRIPT_NPM_NAME);

        if (!ractiveModule.isDirectory()) {
            throw new IllegalStateException("NPM " + RACTIVE_SCRIPT_NPM_NAME + " not installed");
        }

        try {
            if (internalSources.isDirectory()) {
                invokeRactiveParser(internalSources);
            }

            if (externalSources.isDirectory()) {
                invokeRactiveParser(externalSources);
            }
        } catch (WatchingException we) {
            throw new MojoExecutionException(we.getMessage(), we);
        }
    }

    /**
     * Copy the ractive parsing script into .wisdom
     * @throws MojoExecutionException
     */
    private void copyScriptInDotWisdom() throws MojoExecutionException {
        try {
            ractiveExec = new File(System.getProperty("user.home"), ".wisdom"+RACTIVE_SCRIPT_FILE);

            if(!ractiveExec.exists()){
                copyInputStreamToFile(getClass().getResourceAsStream(RACTIVE_SCRIPT_FILE), ractiveExec);
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Cannot load ractive script from resource",e);
        }
    }

    /**
     * @param source The folder containing the ractive templates (.ract files).
     * @throws WatchingException If the parsing failed.
     */
    private void invokeRactiveParser(File source) throws WatchingException {
        getLog().info("Compiling Ractive.js templates from " + source.getAbsolutePath());
        Collection<File> files = FileUtils.listFiles(source, new String[]{RACTIVE_EXTENSION}, true);
        for (File file : files) {
            parseTemplate(file);
        }
    }

    /**
     * Parse the ractive template into a JavaScript file.
     * Run the ractive script from the plugin resource with node, and ractive module.
     *
     * @param template the ractive template file
     * @throws WatchingException if the template cannot be parsed
     */
    private void parseTemplate(File template) throws WatchingException{
        File destination = getOutputJSFile(template);

        // Create the destination folder.
        if (!destination.getParentFile().isDirectory()) {
            destination.getParentFile().mkdirs();
        }

        // Parse with Ractive.js
        CommandLine cmdLine = new CommandLine(getNodeManager().getNodeExecutable());
        cmdLine.addArgument(ractiveExec.getAbsolutePath(), false);
        cmdLine.addArgument(ractiveModule.getAbsolutePath(),false);
        cmdLine.addArgument(template.getAbsolutePath(),false);
        cmdLine.addArgument(destination.getAbsolutePath(),false);

        DefaultExecutor executor = new DefaultExecutor();
        executor.setExitValue(0);

        PumpStreamHandler streamHandler = new PumpStreamHandler(
                new LoggedOutputStream(getLog(), false),
                new LoggedOutputStream(getLog(), true));

        executor.setStreamHandler(streamHandler);

        getLog().info("Executing " + cmdLine.toString());

        try {
            executor.execute(cmdLine);
        } catch (IOException e) {
            throw new WatchingException("Error during the execution of "+RACTIVE_SCRIPT_NPM_NAME, e);
        }
    }

    /**
     * Compute the javascript file from the input template.
     * @param input The file containing the ractive template
     * @return the output javascript File containing the parsed template.
     */
    private File getOutputJSFile(File input) {
        File source;
        File destination;
        if (input.getAbsolutePath().startsWith(internalSources.getAbsolutePath())) {
            source = internalSources;
            destination = destinationForInternals;
        } else if (input.getAbsolutePath().startsWith(externalSources.getAbsolutePath())) {
            source = externalSources;
            destination = destinationForExternals;
        } else {
            return null;
        }

        String jsFileName = input.getName().substring(0, input.getName().length() - RACTIVE_EXTENSION.length()) + "js";
        String path = input.getParentFile().getAbsolutePath().substring(source.getAbsolutePath().length());
        return new File(destination, path + File.separator + jsFileName);
    }

    /**
     * Check if the plugin should parse the given file.
     *
     * @param file the file
     * @return true if the file is a ractive template (.ract extension)
     */
    @Override
    public boolean accept(File file) {
        return (
                WatcherUtils.isInDirectory(file, WatcherUtils.getInternalAssetsSource(basedir)) ||
                        WatcherUtils.isInDirectory(file, WatcherUtils.getExternalAssetsSource(basedir))
        ) &&
                WatcherUtils.hasExtension(file, RACTIVE_EXTENSION);
    }

    @Override
    public boolean fileCreated(File file) throws WatchingException {
        parseTemplate(file);
        return true;
    }

    @Override
    public boolean fileUpdated(File file) throws WatchingException {
        parseTemplate(file);
        return true;
    }

    @Override
    public boolean fileDeleted(File file) throws WatchingException {
        File theFile = getOutputJSFile(file);
        FileUtils.deleteQuietly(theFile);
        return true;
    }
}
