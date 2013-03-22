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

import com.atlassian.jira.rest.client.JiraRestClient;
import com.atlassian.jira.rest.client.VersionRestClient;
import com.atlassian.jira.rest.client.domain.Project;
import com.atlassian.jira.rest.client.domain.Version;

@Mojo(name = "verify", threadSafe = false, requiresProject = true, requiresOnline = true, aggregator = true)
public class Verify extends AbstractMojo {
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
        
        String versionAsString = mavenProject.getVersion();

        JiraRestClient jiraRestClient = JiraRestApiUtils.getJiraRestClient(getLog(), mavenProject);
        if (jiraRestClient == null) {
            return;
        }

        Project project = JiraRestApiUtils.getProject(jiraRestClient, getLog(), mavenProject);
        Version version = JiraRestApiUtils.getVersion(jiraRestClient, project, versionAsString);
        
        try {
            FileUtils.writeStringToFile(new File(FileUtils.getTempDirectory(), project.getKey() + "_releasedVersion.txt"), version.getName().replace("-SNAPSHOT", ""));
        } catch (IOException e) {
            throw new MojoExecutionException(e.toString(), e);
        }
        
        if (version.isReleased()) {
            getLog().warn(project.getName() + "/" + version.getName() + " has already been marked as released.");
        }
        
        VersionRestClient versionRestClient = jiraRestClient.getVersionRestClient();
        int count = versionRestClient.getNumUnresolvedIssues(version.getSelf(), null);
        if (count == 0) {
            getLog().info("JIRA project, " + project.getName() + "/" + version.getName() + ", has 0 unresolved issues. Releasing is possible.");
        } else {
            throw new MojoFailureException("JIRA project, " + project.getName() + "/" + version.getName() + ", has " + count + " unresolved issues. Releasing is now allowed unless it's 0. Please do so in JIRA.");
        }
    }    
}
