package com.redhat.engineering.plugins.actions;

import com.atlassian.jira.security.request.RequestMethod;
import com.atlassian.jira.security.request.SupportedMethods;
import com.atlassian.jira.bc.issue.IssueService;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.security.Permissions;
import com.atlassian.jira.user.ApplicationUser;
import com.redhat.engineering.plugins.domain.Session;
import com.redhat.engineering.plugins.services.SessionService;

import java.util.Date;

/**
 * Instant poker: auto-create session with defaults and redirect to voting
 */
@SupportedMethods({RequestMethod.GET, RequestMethod.POST})
public class InstantPokerAction extends AbstractAction {

    private final IssueService issueService;
    private final JiraAuthenticationContext authContext;
    private final SessionService sessionService;
    private final PermissionManager permissionManager;

    private String key;

    public InstantPokerAction(IssueService issueService,
                             JiraAuthenticationContext authContext,
                             SessionService sessionService,
                             PermissionManager permissionManager) {
        this.issueService = issueService;
        this.authContext = authContext;
        this.sessionService = sessionService;
        this.permissionManager = permissionManager;
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    @Override
    public String doDefault() throws Exception {
        if (!authContext.isLoggedInUser()) {
            addErrorMessage("You must be logged in.");
            return ERROR;
        }

        Issue issue = getIssueObject();
        if (issue == null) return ERROR;

        if (!permissionManager.hasPermission(Permissions.EDIT_ISSUE, issue, getCurrentUser())) {
            addErrorMessage("You don't have permission to create sessions.");
            return ERROR;
        }

        // Check for existing session - reuse if found
        Session session = sessionService.get(getKey());
        if (session == null) {
            // Create new session with 1-hour defaults
            session = new Session();
            session.setIssue(issue);
            session.setAuthor(getCurrentUser());
            session.setCreated(new Date());
            session.setStart(new Date());
            session.setEnd(new Date(System.currentTimeMillis() + 3600000)); // +1 hour
            sessionService.save(session);
        }

        // Return success for AJAX calls (JavaScript will open modal dialog)
        return SUCCESS;
    }

    private Issue getIssueObject() {
        IssueService.IssueResult issueResult = issueService.getIssue(getCurrentUser(), getKey());
        if (!issueResult.isValid()) {
            this.addErrorCollection(issueResult.getErrorCollection());
            return null;
        }
        return issueResult.getIssue();
    }

    private ApplicationUser getCurrentUser() {
        return authContext.getUser();
    }
}
