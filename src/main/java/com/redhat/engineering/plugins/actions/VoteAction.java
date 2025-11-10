package com.redhat.engineering.plugins.actions;

import com.atlassian.jira.security.request.RequestMethod;
import com.atlassian.jira.security.request.SupportedMethods;

import com.atlassian.jira.avatar.Avatar;
import com.atlassian.jira.avatar.AvatarService;
import com.atlassian.jira.plugin.userformat.UserFormats;
import com.atlassian.jira.plugin.userformat.UserFormatter;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.security.Permissions;
import com.atlassian.jira.user.ApplicationUser;
import com.google.common.collect.Maps;
import com.redhat.engineering.plugins.domain.Session;
import com.redhat.engineering.plugins.domain.Vote;
import com.redhat.engineering.plugins.services.ConfigService;
import com.redhat.engineering.plugins.services.SessionService;
import com.redhat.engineering.plugins.services.VoteService;

import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.ModifiedValue;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.util.DefaultIssueChangeHolder;
import com.atlassian.jira.component.ComponentAccessor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author vdedik@redhat.com
 */
@SupportedMethods({RequestMethod.GET, RequestMethod.POST})
public class VoteAction extends AbstractAction {

    private final JiraAuthenticationContext authContext;
    private final SessionService sessionService;
    private final VoteService voteService;
    private final UserFormats userFormats;
    private final AvatarService avatarService;
    private final PermissionManager permissionManager;
    private final ConfigService configService;

    // properties
    private String key;
    private String voteVal;
    private String voteComment;
    private List<String> allowedVotes;
    private String action; // "vote", "endSession", "applyEstimate"
    private String finalValue;

    public VoteAction(JiraAuthenticationContext authContext, SessionService sessionService,
                      VoteService voteService, UserFormats userFormats, AvatarService avatarService,
                      PermissionManager permissionManager, ConfigService configService) {
        this.authContext = authContext;
        this.sessionService = sessionService;
        this.voteService = voteService;
        this.userFormats = userFormats;
        this.avatarService = avatarService;
        this.permissionManager = permissionManager;
        this.configService = configService;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getVoteVal() {
        return voteVal;
    }

    public void setVoteVal(String voteVal) {
        this.voteVal = voteVal;
    }

    public String getVoteComment() {
        return voteComment;
    }

    public void setVoteComment(String voteComment) {
        this.voteComment = voteComment;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getFinalValue() {
        return finalValue;
    }

    public void setFinalValue(String finalValue) {
        this.finalValue = finalValue;
    }

    public List<String> getAllowedVotes() {
        if (allowedVotes == null) {
            allowedVotes = configService.getAllowedVotes();
        }
        return allowedVotes;
    }

    @Override
    public String doDefault() throws Exception {

        if (!authContext.isLoggedInUser()) {
            addErrorMessage("You must be logged in to be able to vote.");
            return ERROR;
        }

        Session session = getSessionObject();
        if (session == null) {
            return ERROR;
        }
        if (System.currentTimeMillis() < session.getStart().getTime()) {
            this.addErrorMessage("You cannot vote because the planning poker session hasn't started yet.");
            return ERROR;
        }
        if (System.currentTimeMillis() > session.getEnd().getTime()) {
            this.addErrorMessage("You cannot vote because the planning poker session has already ended.");
            return ERROR;
        }

        if (voteService.isVoter(session, getCurrentUser())) {
            setVoteVal(voteService.getVoteVal(session, getCurrentUser()));
            setVoteComment(voteService.getVoteComment(session, getCurrentUser()));
        }

        return INPUT;
    }

    @Override
    public String doExecute() throws Exception {
        if ("endSession".equals(action)) {
            return doEndSession();
        } else if ("applyEstimate".equals(action)) {
            return doApplyEstimate();
        } else {
            return doVote();
        }
    }

    private String doVote() throws Exception {
        if (!permissionManager.hasPermission(Permissions.EDIT_ISSUE, getSessionObject().getIssue(), getCurrentUser())) {
            addErrorMessage("You don't have permission to vote.");
            return ERROR;
        }

        Vote vote = new Vote();
        vote.setValue(getVoteVal());
        vote.setVoter(getCurrentUser());
        vote.setSession(getSessionObject());
        vote.setComment(getVoteComment());
        voteService.save(vote);

        this.addMessage("Your vote has been successfully saved.");
        return SUCCESS;
    }

    private String doEndSession() throws Exception {
        Session session = getSessionObject();
        if (!session.getAuthor().equals(getCurrentUser())) {
            addErrorMessage("Only the session creator can end the session.");
            return ERROR;
        }

        // Set end date to NOW to close session immediately
        session.setEnd(new Date());
        sessionService.update(session);

        addMessage("Session ended successfully.");
        return SUCCESS;
    }

    private String doApplyEstimate() throws Exception {
        Session session = getSessionObject();
        if (!session.getAuthor().equals(getCurrentUser())) {
            addErrorMessage("Only the session creator can apply estimates.");
            return ERROR;
        }

        if (System.currentTimeMillis() < session.getEnd().getTime()) {
            addErrorMessage("Cannot apply estimate until session is ended.");
            return ERROR;
        }

        // Update customfield_10205
        Issue issue = session.getIssue();
        CustomField estimateField = ComponentAccessor.getCustomFieldManager()
            .getCustomFieldObject("customfield_10205");

        if (estimateField != null) {
            MutableIssue mutableIssue = ComponentAccessor.getIssueManager()
                .getIssueObject(issue.getId());
            Double value = Double.parseDouble(getFinalValue());
            estimateField.updateValue(null, mutableIssue,
                new ModifiedValue(mutableIssue.getCustomFieldValue(estimateField), value),
                new DefaultIssueChangeHolder());
        }

        addMessage("Estimate " + getFinalValue() + " applied to issue.");
        return SUCCESS;
    }

    public String doViewVotes() throws Exception {
        if (!authContext.isLoggedInUser()) {
            addErrorMessage("You must be logged in to be able to view votes.");
            return ERROR;
        }

        Session session = getSessionObject();
        if (session == null) {
            return ERROR;
        }
        if (System.currentTimeMillis() < session.getEnd().getTime()) {
            this.addErrorMessage("You cannot view votes because the planning poker session hasn't ended yet.");
            return ERROR;
        }

        return "viewVotes";
    }

    public String doViewVoters() throws Exception {
        if (!authContext.isLoggedInUser()) {
            addErrorMessage("You must be logged in to be able to view votes.");
            return ERROR;
        }

        Session session = getSessionObject();
        if (session == null) {
            return ERROR;
        }
        return "viewVoters";
    }

    public List<Vote> getVotes() {
        Session session = getSessionObject();
        return voteService.getVotesBySession(session);
    }

    public String getUserHtml(ApplicationUser user) {
        Map<String, Object> params = Maps.newHashMap();
        params.put("avatarURL", getAvatarURL(user));
        UserFormatter userFormatter = userFormats.formatter("avatarFullNameHover");
        return userFormatter.formatUserkey(user.getKey(), "poker-author", params);
    }

    public String getAvatarURL(ApplicationUser user) {
        return avatarService.getAvatarUrlNoPermCheck(user, Avatar.Size.NORMAL).toString();
    }

    public List<ApplicationUser> getVoters() {
        Session session = getSessionObject();
        return voteService.getVotersBySession(session);
    }

    private Session currentSession;

    private Session getSessionObject() {
        if (currentSession == null) {
            currentSession = sessionService.get(getKey());
        }
        return currentSession;
    }

    private ApplicationUser getCurrentUser() {
        return authContext.getUser();
    }

    public Map<String, Object> getSessionStats() {
        Session session = getSessionObject();
        List<Vote> votes = voteService.getVotesBySession(session);

        List<Double> numericVotes = new ArrayList<>();
        for (Vote vote : votes) {
            String val = vote.getValue();
            if (val != null && val.matches("\\d+(\\.\\d+)?")) {
                numericVotes.add(Double.parseDouble(val));
            }
        }

        if (numericVotes.isEmpty()) {
            return Collections.emptyMap();
        }

        double sum = 0;
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;

        for (Double v : numericVotes) {
            sum += v;
            if (v < min) min = v;
            if (v > max) max = v;
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("min", min);
        stats.put("max", max);
        stats.put("average", sum / numericVotes.size());
        stats.put("count", numericVotes.size());

        return stats;
    }

    public boolean isCreator() {
        Session session = getSessionObject();
        return session != null && session.getAuthor().equals(getCurrentUser());
    }

    public boolean isSessionEnded() {
        Session session = getSessionObject();
        return session != null && System.currentTimeMillis() >= session.getEnd().getTime();
    }
}
