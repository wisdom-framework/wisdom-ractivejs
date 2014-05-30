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
/*
 * Copyright 2014, Technologic Arts Vietnam.
 * All right reserved.
 */

package org.wisdom.ractivejs;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.wisdom.maven.WatchingException;
import org.wisdom.maven.node.NodeManager;

import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * created: 5/27/14.
 *
 * @author <a href="mailto:jbardin@tech-arts.com">Jonathan M. Bardin</a>
 */
public class RactiveJSTemplateCompilerMojoTest {

    public static final String FAKE_PROJECT = "target/test-classes/fake-project";
    public static final String FAKE_PROJECT_TARGET = "target/test-classes/fake-project/target";
    private static final String PARSED_TEMPLATE = "Ractive.templates.view = " +
                                                  "[{\"t\":7,\"e\":\"h1\",\"f\":[\"Hello \",{\"t\":2,\"r\":\"name\"}]}];";
    File nodeDirectory;
    private RactiveJsTemplateCompilerMojo mojo;

    @Before
    public void setUp() throws IOException {
        nodeDirectory = new File("target/test/node");
        nodeDirectory.mkdirs();
        Log log = new SystemStreamLog();
        NodeManager manager = new NodeManager(log, nodeDirectory);
        manager.installIfNotInstalled();
        mojo = new RactiveJsTemplateCompilerMojo();
        mojo.basedir = new File(FAKE_PROJECT);
        mojo.buildDirectory = new File(FAKE_PROJECT_TARGET);
        mojo.buildDirectory.mkdirs();
        mojo.ractiveJsVersion = "0.4.0";
    }

    @After
    public void tearDown(){
        FileUtils.deleteQuietly(mojo.buildDirectory);
    }

    @Test
    public void testProcessingOfRactiveTemplateFiles() throws MojoFailureException, MojoExecutionException, IOException {
        mojo.execute();

        final File var = new File(FAKE_PROJECT_TARGET, "classes/view.js");
        assertThat(var).isFile();
        String content = FileUtils.readFileToString(var);
        assertThat(content).isEqualTo(PARSED_TEMPLATE);

        final File link = new File(FAKE_PROJECT_TARGET, "wisdom/assets/view.js");
        assertThat(link).isFile();
        content = FileUtils.readFileToString(link);
        assertThat(content).isEqualTo(PARSED_TEMPLATE);
    }

    @Test
    public void testWatching() throws MojoFailureException, MojoExecutionException, IOException, WatchingException, InterruptedException {
        // Copy var to var2 (do not modify var as it is used by other tests).
        final File originalView = new File(FAKE_PROJECT, "src/main/resources/view.ract");
        final File newView = new File(FAKE_PROJECT, "src/main/resources/newView.ract");
        String originalVarContent = FileUtils.readFileToString(originalView);
        FileUtils.copyFile(originalView, newView);

        mojo.execute();

        final File var = new File(FAKE_PROJECT_TARGET, "classes/newView.js");
        assertThat(var).isFile();
        String content = FileUtils.readFileToString(var);
        assertThat(content).isEqualTo(PARSED_TEMPLATE.replace(".view",".newView"));

        final File link = new File(FAKE_PROJECT_TARGET, "classes/view.js");
        assertThat(link).isFile();
        content = FileUtils.readFileToString(link);
        assertThat(content).isEqualTo(PARSED_TEMPLATE);

        // Delete view
        newView.delete();
        mojo.fileDeleted(newView);

        assertThat(var.isFile()).isFalse();

        // Recreate the file with another name (same content)
        File newFile = new File(FAKE_PROJECT, "src/main/resources/view2.ract");
        FileUtils.write(newFile, originalVarContent);
        mojo.fileCreated(newFile);
        File view2 = new File(FAKE_PROJECT_TARGET, "classes/view2.js");
        assertThat(view2).isFile();
        content = FileUtils.readFileToString(view2);
        assertThat(content).isEqualTo(PARSED_TEMPLATE.replace(".view",".view2"));

        // Update view
        long lastModified = newFile.lastModified();
        FileUtils.touch(newFile);
        mojo.fileUpdated(newFile);
        // The file should have been updated
        assertThat(newFile.lastModified()).isGreaterThanOrEqualTo(lastModified);
    }
}
