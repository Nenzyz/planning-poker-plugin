package com.redhat.engineering.plugins.actions;

import com.atlassian.jira.security.request.RequestMethod;
import com.atlassian.jira.security.request.SupportedMethods;
import com.atlassian.jira.avatar.AvatarService;
import com.atlassian.jira.plugin.userformat.UserFormats;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.PermissionManager;
import com.redhat.engineering.plugins.domain.Session;
import com.redhat.engineering.plugins.services.SessionService;
import com.redhat.engineering.plugins.services.ConfigService;
import com.redhat.engineering.plugins.services.VoteService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.bc.issue.IssueService;
import com.atlassian.jira.issue.Issue;

import java.util.Date;

/**
 * Instant poker: auto-create session with defaults and show voting interface
 * Extends VoteAction to reuse voting template and methods
 */
@SupportedMethods({RequestMethod.GET, RequestMethod.POST})
public class InstantPokerAction extends VoteAction {

    private final SessionService sessionService;
    private final PermissionManager permissionManager;

    public InstantPokerAction(JiraAuthenticationContext authContext,
                             SessionService sessionService,
                             VoteService voteService,
                             UserFormats userFormats,
                             AvatarService avatarService,
                             PermissionManager permissionManager,
                             ConfigService configService) {
        super(authContext, sessionService, voteService, userFormats, avatarService, permissionManager, configService);
        this.sessionService = sessionService;
        this.permissionManager = permissionManager;
    }

    @Override
    public String doDefault() throws Exception {
        // Get or create session with instant defaults
        Session session = findOrCreateSession();

        // Now call parent's doDefault to set up vote state and return INPUT
        return super.doDefault();
    }

    @Override
    public boolean isInstantMode() {
        return true;  // InstantPokerAction is ALWAYS instant mode
    }

    private Session findOrCreateSession() {
        // Use injected sessionService
        Session session = sessionService.get(getKey());

        if (session == null) {
            // Create new session with 1-hour defaults
            session = new Session();
            session.setIssue(getIssueObject());
            session.setAuthor(getLoggedInApplicationUser());
            session.setCreated(new Date());
            session.setStart(new Date());
            session.setEnd(new Date(System.currentTimeMillis() + 3600000)); // +1 hour
            sessionService.save(session);
        } else {
            // If session exists but has ended, extend it by 1 hour from now
            if (System.currentTimeMillis() >= session.getEnd().getTime()) {
                session.setStart(new Date());
                session.setEnd(new Date(System.currentTimeMillis() + 3600000)); // +1 hour
                sessionService.update(session);
            }
        }

        return session;
    }

    private Issue getIssueObject() {
        IssueService issueService = ComponentAccessor.getIssueService();
        // Use inherited getLoggedInApplicationUser() from parent class
        IssueService.IssueResult issueResult = issueService.getIssue(getLoggedInApplicationUser(), getKey());
        if (!issueResult.isValid()) {
            addErrorCollection(issueResult.getErrorCollection());
            return null;
        }
        return issueResult.getIssue();
    }
}
