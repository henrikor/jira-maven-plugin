package com.infosynergi.mojo.jira;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;
import org.joda.time.DateTime;

import com.atlassian.jira.rest.client.JiraRestClient;
import com.atlassian.jira.rest.client.VersionRestClient;
import com.atlassian.jira.rest.client.domain.Project;
import com.atlassian.jira.rest.client.domain.Version;
import com.atlassian.jira.rest.client.domain.input.VersionInput;
import com.atlassian.jira.rest.client.domain.input.VersionInputBuilder;

@Mojo(name = "release", threadSafe = false, requiresProject = true, requiresOnline = true, aggregator = true)
public class Release extends AbstractMojo {
    @Component
    protected MavenProject mavenProject;

    @Component
    protected MavenSession mavenSession;

    public void execute() throws MojoExecutionException, MojoFailureException {
        String dryRunAsString = (String) System.getProperties().get("dryRun");

        if (dryRunAsString != null) {
            if (Boolean.parseBoolean(dryRunAsString.trim())) {
                getLog().info("dryRun specified. Not updating JIRA");
                return;
            }
        }
        
        JiraRestClient jiraRestClient = JiraRestApiUtils.getJiraRestClient(getLog(), mavenProject);
        if (jiraRestClient == null) {
            return;
        }
        Project project = JiraRestApiUtils.getProject(jiraRestClient, getLog(), mavenProject);

        VersionRestClient versionRestClient = jiraRestClient.getVersionRestClient();
        markAsReleased(jiraRestClient, versionRestClient, project);
        createNewVersion(jiraRestClient, versionRestClient, project);
    }

    private void createNewVersion(JiraRestClient jiraRestClient, VersionRestClient versionRestClient, Project project) {
        String versionAsString = mavenProject.getVersion().replace("-SNAPSHOT", "");
        
        for (Version version : project.getVersions()) {
            if (version.equals(versionAsString)) {
                getLog().info("Version, " + versionAsString + ", already exists. Not creating version in JIRA");
                return;
            }
        }
        VersionInputBuilder versionInputBuilder = new VersionInputBuilder(project.getKey());
        versionInputBuilder.setName(versionAsString);
        
        Version createdVersion = versionRestClient.createVersion(versionInputBuilder.build(), null);
        getLog().info("Version, " + versionAsString + ", created in JIRA. " + createdVersion);
    }

    private void markAsReleased(JiraRestClient jiraRestClient, VersionRestClient versionRestClient, Project project) throws MojoExecutionException, MojoFailureException {
        String releasedVersionAsString = getReleasedVersion(project);
        Version version = JiraRestApiUtils.getVersion(jiraRestClient, project, releasedVersionAsString);
        
        VersionInputBuilder versionInputBuilder = new VersionInputBuilder(project.getKey(), version);
        versionInputBuilder.setReleased(true);
        versionInputBuilder.setReleaseDate(new DateTime());
        VersionInput versionInput = versionInputBuilder.build();

        getLog().info("Setting " + project.getName() + "/" + version.getName() + " as released");
        versionRestClient.updateVersion(version.getSelf(), versionInput, null);
    }
    
    private String getReleasedVersion(Project project) throws MojoExecutionException {
        File file = new File(FileUtils.getTempDirectory(), project.getKey() + "_releasedVersion.txt");
        if (!file.exists()) {
            throw new MojoExecutionException(file.getAbsolutePath() + " does not exist. Has com.infosynergi.mojo.jira:jira-maven-plugin:verify been run preparationGoal in the Maven Release plugin?");
        }

        try {
            String releaseVersion = FileUtils.readFileToString(file);
            file.delete();
            return releaseVersion;
        } catch (IOException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }
}
