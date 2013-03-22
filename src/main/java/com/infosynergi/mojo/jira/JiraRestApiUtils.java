package com.infosynergi.mojo.jira;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.maven.model.IssueManagement;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import com.atlassian.jira.rest.client.JiraRestClient;
import com.atlassian.jira.rest.client.ProjectRestClient;
import com.atlassian.jira.rest.client.domain.Project;
import com.atlassian.jira.rest.client.domain.Version;
import com.atlassian.jira.rest.client.internal.jersey.JerseyJiraRestClientFactory;

public class JiraRestApiUtils {
    public static Project getProject(JiraRestClient jiraRestClient, Log log, MavenProject mavenProject) throws MojoFailureException {
        String jiraProjectName = getJIRAProjectName(log, mavenProject);
        
        ProjectRestClient projectRestClient = jiraRestClient.getProjectClient();
        Project project = projectRestClient.getProject(jiraProjectName, null);
        
        if (project == null) {
            throw new MojoFailureException("JIRA project, " + jiraProjectName + ", does NOT exist in JIRA.");
        }

        return project;
    }
    
    public static Version getVersion(JiraRestClient jiraRestClient, Project project, String version) throws MojoFailureException{
        version = version.replace("-SNAPSHOT", "");
        
        Version currentVersion = null;
        for (Version jiraVersion : project.getVersions()) {
            if (jiraVersion.getName().equals(version)) {
                currentVersion = jiraVersion;
            }
        }
        
        if (currentVersion == null) {
            throw new MojoFailureException("Version, " + version + ", for JIRA project, " + project.getName() + ", does NOT exist. Please add it in JIRA.");
        }
        
        return currentVersion;
    }
    
    private static String getIssueManagementURL(Log log, MavenProject mavenProject) throws MojoFailureException {
        IssueManagement issueManagement = mavenProject.getIssueManagement();
        if (issueManagement == null) {
            log.info("IssueManagement is null. Ignoring...");
            return null;
        }
        
        if (!issueManagement.getSystem().equals("JIRA")) {
            log.info("IssueManagement.system is '" + issueManagement.getSystem() + "'. Only support for 'JIRA'. Ignoring...");
            return null;
        }
        
        String issueManagementURL = issueManagement.getUrl();
        if (issueManagementURL == null || issueManagementURL.trim().length() == 0) { 
            throw new MojoFailureException("A JIRA IssueManagement has been specified, but the url is null or empty");
        }

        if (issueManagementURL != null) {
            issueManagementURL = issueManagementURL.trim();
        }
        
        // Remove, if existing, the trailing /
        if (issueManagementURL.lastIndexOf('/') == (issueManagementURL.length() - 1)) {
            issueManagementURL = issueManagementURL.substring(0, issueManagementURL.length() - 1);
        }

        return issueManagementURL;
    }
    
    public static String getJIRAProjectName(Log log, MavenProject mavenProject) throws MojoFailureException {
        String issueManagementURL = getIssueManagementURL(log, mavenProject);
        if (issueManagementURL == null) {
            return null;
        }
        return issueManagementURL.substring(issueManagementURL.lastIndexOf('/') + 1);
    }
    
    public static JiraRestClient getJiraRestClient(Log log, MavenProject mavenProject) throws MojoExecutionException, MojoFailureException {
        try {
            String issueManagementURL = getIssueManagementURL(log, mavenProject);
            if (issueManagementURL == null) {
                return null;
            }
            
            String jiraProjectName = getJIRAProjectName(log, mavenProject);
            String jiraBaseURL = issueManagementURL.replace("/browse/" + jiraProjectName, "");
            
            JerseyJiraRestClientFactory jerseyJiraRestClientFactory = new JerseyJiraRestClientFactory();
            JiraRestClient jc = jerseyJiraRestClientFactory.createWithBasicHttpAuthentication(new URI(jiraBaseURL), "buildmaster", "thepassword");
            return jc;
        } catch (URISyntaxException e) {
            throw new MojoExecutionException(e.toString(), e);
        }
    }    
}
