# Instant Planning Poker Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add "Instant Planning Poker Session" feature that creates 1-hour sessions automatically and shows immediate voting dialog with real-time results and issue field updates.

**Architecture:** Add new InstantPokerSessionAction for auto-creation and dialog routing, create REST endpoint for live vote updates, enhance VoteAction with instant mode support, add JavaScript voting dialog with AUI dialog2, implement session completion with statistics and final value selection that updates customfield_10205.

**Tech Stack:** JIRA Webwork1 Actions, JAX-RS REST, AUI Dialog2, Velocity templates, jQuery, Active Objects (existing)

---

## Task 1: Add Session.instant Flag to Domain Model

**Files:**
- Modify: `src/main/java/com/redhat/engineering/plugins/domain/Session.java:11-58`

**Step 1: Add instant flag field to Session**

Add after line 17:

```java
private boolean instant = false;
```

**Step 2: Add getter method**

Add after line 57:

```java
public boolean isInstant() {
    return instant;
}

public void setInstant(boolean instant) {
    this.instant = instant;
}
```

**Step 3: Verify compilation**

Run: `docker-compose run --rm build mvn compile`
Expected: `BUILD SUCCESS`

**Step 4: Commit**

```bash
git add src/main/java/com/redhat/engineering/plugins/domain/Session.java
git commit -m "feat: add instant flag to Session domain model"
```

---

## Task 2: Update SessionService to Support Instant Sessions

**Files:**
- Modify: `src/main/java/com/redhat/engineering/plugins/services/SessionService.java`

**Step 1: Read current SessionService to understand structure**

Read: `src/main/java/com/redhat/engineering/plugins/services/SessionService.java`

**Step 2: Add getActiveInstantSession method**

Add new method after existing get() methods:

```java
/**
 * Get active instant session for an issue (if exists and not ended)
 * @param issueKey Issue key
 * @return Active instant session or null
 */
public Session getActiveInstantSession(String issueKey) {
    Session session = get(issueKey);
    if (session != null && session.isInstant() &&
        System.currentTimeMillis() < session.getEnd().getTime()) {
        return session;
    }
    return null;
}
```

**Step 3: Add createInstantSession method**

Add new method:

```java
/**
 * Create or reuse instant session for an issue
 * @param issue Issue
 * @param author Session creator
 * @return Created or existing instant session
 */
public Session createInstantSession(Issue issue, ApplicationUser author) {
    // Check for existing instant session
    Session existing = getActiveInstantSession(issue.getKey());
    if (existing != null) {
        return existing;
    }

    // Delete old session if exists (instant sessions replace any existing)
    Session oldSession = get(issue.getKey());
    if (oldSession != null) {
        delete(oldSession);
    }

    // Create new 1-hour instant session
    Session session = new Session();
    session.setIssue(issue);
    session.setAuthor(author);
    session.setCreated(new Date());
    session.setStart(new Date());
    session.setEnd(new Date(System.currentTimeMillis() + 3600000)); // +1 hour
    session.setInstant(true);
    save(session);

    return session;
}
```

**Step 4: Verify compilation**

Run: `docker-compose run --rm build mvn compile`
Expected: `BUILD SUCCESS`

**Step 5: Commit**

```bash
git add src/main/java/com/redhat/engineering/plugins/services/SessionService.java
git commit -m "feat: add instant session creation and retrieval to SessionService"
```

---

## Task 3: Create InstantPokerSessionAction

**Files:**
- Create: `src/main/java/com/redhat/engineering/plugins/actions/InstantPokerSessionAction.java`

**Step 1: Create InstantPokerSessionAction class**

Create file with this content:

```java
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

/**
 * Action for instant planning poker sessions
 * Creates/reuses 1-hour session and redirects to voting dialog
 * @author Planning Poker Plugin
 */
@SupportedMethods({RequestMethod.GET, RequestMethod.POST})
public class InstantPokerSessionAction extends AbstractAction {

    private final IssueService issueService;
    private final JiraAuthenticationContext authContext;
    private final SessionService sessionService;
    private final PermissionManager permissionManager;

    // properties
    private String key;

    public InstantPokerSessionAction(IssueService issueService,
                                    JiraAuthenticationContext authContext,
                                    SessionService sessionService,
                                    PermissionManager permissionManager) {
        this.issueService = issueService;
        this.authContext = authContext;
        this.sessionService = sessionService;
        this.permissionManager = permissionManager;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    @Override
    public String doDefault() throws Exception {
        if (!authContext.isLoggedInUser()) {
            addErrorMessage("You must be logged in to use instant poker sessions.");
            return ERROR;
        }

        Issue issue = getIssueObject();
        if (issue == null) {
            return ERROR;
        }

        if (!permissionManager.hasPermission(Permissions.EDIT_ISSUE, issue, getCurrentUser())) {
            addErrorMessage("You don't have permission to create poker sessions for this issue.");
            return ERROR;
        }

        // Create or reuse instant session
        Session session = sessionService.createInstantSession(issue, getCurrentUser());

        // Redirect to instant vote dialog
        return "instant-vote";
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
```

**Step 2: Verify compilation**

Run: `docker-compose run --rm build mvn compile`
Expected: `BUILD SUCCESS`

**Step 3: Commit**

```bash
git add src/main/java/com/redhat/engineering/plugins/actions/InstantPokerSessionAction.java
git commit -m "feat: add InstantPokerSessionAction for automatic session creation"
```

---

## Task 4: Register InstantPokerSessionAction in Plugin Descriptor

**Files:**
- Modify: `src/main/resources/atlassian-plugin.xml`

**Step 1: Add webwork1 action registration**

Add after the existing PokerSession action (around line 70):

```xml
<!-- Instant Planning Poker Session Action -->
<webwork1 key="instant-poker-session-action" name="Instant Poker Session Action" class="java.lang.Object">
    <description>Action for creating instant poker sessions and showing voting dialog</description>
    <actions>
        <action name="com.redhat.engineering.plugins.actions.InstantPokerSessionAction" alias="InstantPokerSession" roles-required="use">
            <view name="instant-vote">/views/vote/instant-dialog.vm</view>
        </action>
    </actions>
</webwork1>
```

**Step 2: Add web-item for instant poker menu**

Add after the existing "Planning Poker Session" web-item (around line 59):

```xml
<web-item name="Instant Poker Session" i18n-name-key="instant-poker-link.name" key="instant-poker-link"
          section="operations-operations" weight="1001">
    <label key="instant-poker-link.label">Instant Planning Poker</label>
    <tooltip>Start instant poker session with 1-hour duration</tooltip>
    <link linkId="instant-poker-link-id">/secure/InstantPokerSession!default.jspa?key=${issue.key}</link>
    <styleClass>instant-poker-trigger</styleClass>
    <condition class="com.atlassian.jira.plugin.webfragment.conditions.IsIssueEditableCondition"/>
    <condition class="com.atlassian.jira.plugin.webfragment.conditions.HasIssuePermissionCondition">
        <param name="permission">edit</param>
    </condition>
</web-item>
```

**Step 3: Verify XML syntax**

Run: `docker-compose run --rm build mvn validate`
Expected: `BUILD SUCCESS`

**Step 4: Commit**

```bash
git add src/main/resources/atlassian-plugin.xml
git commit -m "feat: register InstantPokerSession action and menu item"
```

---

## Task 5: Create InstantVoteDialogAction for Live Voting

**Files:**
- Create: `src/main/java/com/redhat/engineering/plugins/actions/InstantVoteDialogAction.java`

**Step 1: Create action class**

```java
package com.redhat.engineering.plugins.actions;

import com.atlassian.jira.security.request.RequestMethod;
import com.atlassian.jira.security.request.SupportedMethods;
import com.atlassian.jira.bc.issue.IssueService;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.ModifiedValue;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.util.DefaultIssueChangeHolder;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.security.Permissions;
import com.atlassian.jira.user.ApplicationUser;
import com.redhat.engineering.plugins.domain.Session;
import com.redhat.engineering.plugins.domain.Vote;
import com.redhat.engineering.plugins.services.SessionService;
import com.redhat.engineering.plugins.services.VoteService;
import com.redhat.engineering.plugins.services.ConfigService;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Action for instant vote dialog with live updates
 */
@SupportedMethods({RequestMethod.GET, RequestMethod.POST})
public class InstantVoteDialogAction extends AbstractAction {

    private final IssueService issueService;
    private final JiraAuthenticationContext authContext;
    private final SessionService sessionService;
    private final VoteService voteService;
    private final ConfigService configService;
    private final PermissionManager permissionManager;

    // properties
    private String key;
    private String voteVal;
    private String voteComment;
    private String action; // "vote", "endSession", "applyEstimate"
    private String finalValue;

    public InstantVoteDialogAction(IssueService issueService,
                                  JiraAuthenticationContext authContext,
                                  SessionService sessionService,
                                  VoteService voteService,
                                  ConfigService configService,
                                  PermissionManager permissionManager) {
        this.issueService = issueService;
        this.authContext = authContext;
        this.sessionService = sessionService;
        this.voteService = voteService;
        this.configService = configService;
        this.permissionManager = permissionManager;
    }

    // Getters and setters
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getVoteVal() { return voteVal; }
    public void setVoteVal(String voteVal) { this.voteVal = voteVal; }

    public String getVoteComment() { return voteComment; }
    public void setVoteComment(String voteComment) { this.voteComment = voteComment; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getFinalValue() { return finalValue; }
    public void setFinalValue(String finalValue) { this.finalValue = finalValue; }

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

    public String doVote() throws Exception {
        Session session = getSessionObject();
        if (session == null || !session.isInstant()) {
            addErrorMessage("Invalid instant session.");
            return ERROR;
        }

        if (!permissionManager.hasPermission(Permissions.EDIT_ISSUE, session.getIssue(), getCurrentUser())) {
            addErrorMessage("You don't have permission to vote.");
            return ERROR;
        }

        Vote vote = new Vote();
        vote.setValue(getVoteVal());
        vote.setVoter(getCurrentUser());
        vote.setSession(session);
        vote.setComment(getVoteComment());
        voteService.save(vote);

        addMessage("Vote saved successfully.");
        return SUCCESS;
    }

    public String doEndSession() throws Exception {
        Session session = getSessionObject();
        if (session == null || !session.isInstant()) {
            addErrorMessage("Invalid instant session.");
            return ERROR;
        }

        if (!session.getAuthor().equals(getCurrentUser())) {
            addErrorMessage("Only the session creator can end the session.");
            return ERROR;
        }

        // End session immediately
        session.setEnd(new Date());
        sessionService.update(session);

        addMessage("Session ended.");
        return "show-results";
    }

    public String doApplyEstimate() throws Exception {
        Session session = getSessionObject();
        if (session == null || !session.isInstant()) {
            addErrorMessage("Invalid instant session.");
            return ERROR;
        }

        if (!session.getAuthor().equals(getCurrentUser())) {
            addErrorMessage("Only the session creator can apply the estimate.");
            return ERROR;
        }

        if (System.currentTimeMillis() < session.getEnd().getTime()) {
            addErrorMessage("Cannot apply estimate until session is ended.");
            return ERROR;
        }

        // Update issue field customfield_10205
        Issue issue = session.getIssue();
        CustomField estimateField = ComponentAccessor.getCustomFieldManager()
            .getCustomFieldObject("customfield_10205");

        if (estimateField != null) {
            MutableIssue mutableIssue = (MutableIssue) issue;
            Double value = Double.parseDouble(getFinalValue());
            estimateField.updateValue(null, mutableIssue,
                new ModifiedValue(mutableIssue.getCustomFieldValue(estimateField), value),
                new DefaultIssueChangeHolder());
        }

        addMessage("Estimate applied to issue.");
        return SUCCESS;
    }

    public Map<String, Object> getSessionStats() {
        Session session = getSessionObject();
        if (session == null) {
            return Collections.emptyMap();
        }

        List<Vote> votes = voteService.getVotesBySession(session);
        List<Double> numericVotes = votes.stream()
            .map(Vote::getValue)
            .filter(v -> v != null && v.matches("\\d+(\\.\\d+)?"))
            .map(Double::parseDouble)
            .collect(Collectors.toList());

        if (numericVotes.isEmpty()) {
            return Collections.emptyMap();
        }

        DoubleSummaryStatistics stats = numericVotes.stream()
            .collect(Collectors.summarizingDouble(Double::doubleValue));

        Map<String, Object> result = new HashMap<>();
        result.put("min", stats.getMin());
        result.put("max", stats.getMax());
        result.put("average", stats.getAverage());
        result.put("count", stats.getCount());

        return result;
    }

    public List<String> getAllowedVotes() {
        return configService.getAllowedVotes();
    }

    public boolean isCreator() {
        Session session = getSessionObject();
        return session != null && session.getAuthor().equals(getCurrentUser());
    }

    public boolean isSessionEnded() {
        Session session = getSessionObject();
        return session != null && System.currentTimeMillis() >= session.getEnd().getTime();
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
}
```

**Step 2: Verify compilation**

Run: `docker-compose run --rm build mvn compile`
Expected: `BUILD SUCCESS`

**Step 3: Commit**

```bash
git add src/main/java/com/redhat/engineering/plugins/actions/InstantVoteDialogAction.java
git commit -m "feat: add InstantVoteDialogAction with vote, end session, and apply estimate"
```

---

## Task 6: Create Instant Vote Dialog View (Velocity Template)

**Files:**
- Create: `src/main/resources/views/vote/instant-dialog.vm`

**Step 1: Create Velocity template**

```velocity
$webResourceManager.requireResource("com.redhat.engineering.plugins.planning-poker:planning-poker-resources")
$webResourceManager.requireResource("com.redhat.engineering.plugins.planning-poker:instant-poker-dialog")

<html>
<head>
    <title>Instant Planning Poker</title>
    <meta name="decorator" content="atl.general"/>
</head>
<body>
<section id="instant-poker-dialog" class="aui-dialog2 aui-dialog2-large" role="dialog"
         data-issue-key="${key}"
         data-is-creator="${action.isCreator()}"
         data-session-ended="${action.isSessionEnded()}"
         aria-hidden="false">

    <header class="aui-dialog2-header">
        <h2 class="aui-dialog2-header-main">Instant Planning Poker - ${key}</h2>
        <a class="aui-dialog2-header-close">
            <span class="aui-icon aui-icon-small aui-iconfont-close-dialog">Close</span>
        </a>
    </header>

    <div class="aui-dialog2-content">

        <!-- Voting Section -->
        <div id="voting-section" #if($action.isSessionEnded())style="display:none"#end>
            <h3>Cast Your Vote</h3>

            <form id="instant-vote-form" class="aui">
                <input type="hidden" name="atl_token" value="$atl_token">
                <input type="hidden" name="key" value="${key}">
                <input type="hidden" name="action" value="vote">

                <div class="instant-poker-cards">
                    #foreach($allowedVote in $action.allowedVotes)
                        <button type="button" class="aui-button instant-poker-card" data-value="$allowedVote">
                            $allowedVote
                        </button>
                    #end
                </div>

                <div class="field-group">
                    <label for="instant-vote-comment">Comment (optional)</label>
                    <textarea id="instant-vote-comment" name="voteComment" class="textarea"
                              cols="40" rows="2"></textarea>
                </div>

                <input type="hidden" id="instant-vote-value" name="voteVal" value="">
            </form>

            <div id="live-votes-display">
                <h4>Current Votes: <span id="vote-count">0</span></h4>
                <div id="voter-list"></div>
            </div>

            #if($action.isCreator())
                <button id="end-session-btn" class="aui-button aui-button-primary">
                    End Session & Show Results
                </button>
            #end
        </div>

        <!-- Results Section -->
        <div id="results-section" #if(!$action.isSessionEnded())style="display:none"#end>
            #set($stats = $action.sessionStats)
            #if($stats && !$stats.isEmpty())
                <h3>Session Results</h3>

                <div class="instant-poker-stats">
                    <div class="stat-item">
                        <span class="stat-label">Minimum:</span>
                        <span class="stat-value">${stats.get("min")}</span>
                    </div>
                    <div class="stat-item">
                        <span class="stat-label">Maximum:</span>
                        <span class="stat-value">${stats.get("max")}</span>
                    </div>
                    <div class="stat-item">
                        <span class="stat-label">Average:</span>
                        <span class="stat-value">${stats.get("average")}</span>
                    </div>
                    <div class="stat-item">
                        <span class="stat-label">Total Votes:</span>
                        <span class="stat-value">${stats.get("count")}</span>
                    </div>
                </div>

                #if($action.isCreator())
                    <h4>Apply Final Estimate</h4>
                    <form id="apply-estimate-form" class="aui">
                        <input type="hidden" name="atl_token" value="$atl_token">
                        <input type="hidden" name="key" value="${key}">
                        <input type="hidden" name="action" value="applyEstimate">

                        <div class="instant-poker-final-buttons">
                            <button type="button" class="aui-button apply-estimate-btn"
                                    data-value="${stats.get('min')}">
                                Apply Min (${stats.get("min")})
                            </button>
                            <button type="button" class="aui-button apply-estimate-btn"
                                    data-value="${stats.get('average')}">
                                Apply Average (${stats.get("average")})
                            </button>
                            <button type="button" class="aui-button apply-estimate-btn"
                                    data-value="${stats.get('max')}">
                                Apply Max (${stats.get("max")})
                            </button>
                        </div>

                        <input type="hidden" id="final-value" name="finalValue" value="">
                    </form>
                #end
            #else
                <p>No votes cast yet.</p>
            #end
        </div>

        <div id="instant-poker-messages"></div>
    </div>

    <footer class="aui-dialog2-footer">
        <div class="aui-dialog2-footer-actions">
            <button id="close-dialog-btn" class="aui-button">Close</button>
        </div>
    </footer>
</section>

<script type="text/javascript">
    AJS.$(document).ready(function() {
        AJS.dialog2('#instant-poker-dialog').show();
    });
</script>
</body>
</html>
```

**Step 2: Verify template syntax**

Run: `docker-compose run --rm build mvn validate`
Expected: `BUILD SUCCESS`

**Step 3: Commit**

```bash
git add src/main/resources/views/vote/instant-dialog.vm
git commit -m "feat: add instant vote dialog Velocity template with voting and results UI"
```

---

## Task 7: Create JavaScript for Instant Poker Dialog Interactions

**Files:**
- Create: `src/main/resources/js/instant-poker-dialog.js`

**Step 1: Create JavaScript file**

```javascript
/**
 * Instant Planning Poker Dialog - Live voting and results
 */
(function($) {
    'use strict';

    var InstantPokerDialog = {
        issueKey: null,
        isCreator: false,
        pollInterval: null,

        init: function() {
            var $dialog = $('#instant-poker-dialog');
            this.issueKey = $dialog.data('issue-key');
            this.isCreator = $dialog.data('is-creator') === 'true';

            this.bindEvents();
            this.startLivePolling();
        },

        bindEvents: function() {
            var self = this;

            // Card selection
            $('.instant-poker-card').on('click', function() {
                var value = $(this).data('value');
                self.selectCard($(this), value);
            });

            // End session button
            $('#end-session-btn').on('click', function() {
                self.endSession();
            });

            // Apply estimate buttons
            $('.apply-estimate-btn').on('click', function() {
                var value = $(this).data('value');
                self.applyEstimate(value);
            });

            // Close dialog
            $('#close-dialog-btn, .aui-dialog2-header-close').on('click', function() {
                self.closeDialog();
            });
        },

        selectCard: function($card, value) {
            // Visual feedback
            $('.instant-poker-card').removeClass('selected');
            $card.addClass('selected');

            // Set hidden field
            $('#instant-vote-value').val(value);

            // Submit vote immediately
            this.submitVote(value, $('#instant-vote-comment').val());
        },

        submitVote: function(value, comment) {
            var self = this;
            var atl_token = $('input[name="atl_token"]').first().val();

            $.ajax({
                url: AJS.contextPath() + '/secure/InstantVoteDialog.jspa',
                type: 'POST',
                data: {
                    key: this.issueKey,
                    action: 'vote',
                    voteVal: value,
                    voteComment: comment || '',
                    atl_token: atl_token
                },
                success: function(response) {
                    self.showMessage('Vote saved successfully', 'success');
                    self.refreshVotes();
                },
                error: function(xhr) {
                    self.showMessage('Failed to save vote: ' + xhr.responseText, 'error');
                }
            });
        },

        endSession: function() {
            var self = this;
            var atl_token = $('input[name="atl_token"]').first().val();

            if (!confirm('End this session and show results?')) {
                return;
            }

            $.ajax({
                url: AJS.contextPath() + '/secure/InstantVoteDialog.jspa',
                type: 'POST',
                data: {
                    key: this.issueKey,
                    action: 'endSession',
                    atl_token: atl_token
                },
                success: function(response) {
                    self.showMessage('Session ended', 'success');
                    self.stopLivePolling();
                    self.showResults();
                },
                error: function(xhr) {
                    self.showMessage('Failed to end session: ' + xhr.responseText, 'error');
                }
            });
        },

        applyEstimate: function(value) {
            var self = this;
            var atl_token = $('input[name="atl_token"]').first().val();

            $.ajax({
                url: AJS.contextPath() + '/secure/InstantVoteDialog.jspa',
                type: 'POST',
                data: {
                    key: this.issueKey,
                    action: 'applyEstimate',
                    finalValue: value,
                    atl_token: atl_token
                },
                success: function(response) {
                    self.showMessage('Estimate applied to issue: ' + value, 'success');
                    setTimeout(function() {
                        self.closeDialog();
                        // Reload issue page to show updated estimate
                        window.location.reload();
                    }, 1500);
                },
                error: function(xhr) {
                    self.showMessage('Failed to apply estimate: ' + xhr.responseText, 'error');
                }
            });
        },

        startLivePolling: function() {
            var self = this;
            this.refreshVotes();
            this.pollInterval = setInterval(function() {
                self.refreshVotes();
            }, 3000); // Poll every 3 seconds
        },

        stopLivePolling: function() {
            if (this.pollInterval) {
                clearInterval(this.pollInterval);
                this.pollInterval = null;
            }
        },

        refreshVotes: function() {
            var self = this;

            $.ajax({
                url: AJS.contextPath() + '/rest/poker/1.0/votes/' + this.issueKey,
                type: 'GET',
                dataType: 'json',
                success: function(data) {
                    self.updateVoteDisplay(data);
                },
                error: function(xhr) {
                    console.error('Failed to refresh votes:', xhr);
                }
            });
        },

        updateVoteDisplay: function(data) {
            $('#vote-count').text(data.count || 0);

            var $voterList = $('#voter-list');
            $voterList.empty();

            if (data.voters && data.voters.length > 0) {
                var html = '<ul class="voter-list">';
                data.voters.forEach(function(voter) {
                    html += '<li>' + voter.displayName + ' - <span class="vote-hidden">Voted</span></li>';
                });
                html += '</ul>';
                $voterList.html(html);
            }
        },

        showResults: function() {
            $('#voting-section').hide();
            $('#results-section').show();
            // Reload page to get fresh statistics
            window.location.href = AJS.contextPath() + '/secure/InstantVoteDialog!default.jspa?key=' + this.issueKey;
        },

        showMessage: function(message, type) {
            var $messages = $('#instant-poker-messages');
            var className = type === 'error' ? 'aui-message-error' : 'aui-message-success';

            var html = '<div class="aui-message ' + className + '">' +
                       '<p class="title"><span class="aui-icon icon-' + type + '"></span>' + message + '</p>' +
                       '</div>';

            $messages.html(html);

            setTimeout(function() {
                $messages.empty();
            }, 3000);
        },

        closeDialog: function() {
            this.stopLivePolling();
            AJS.dialog2('#instant-poker-dialog').hide();
            // Navigate back to issue
            window.location.href = AJS.contextPath() + '/browse/' + this.issueKey;
        }
    };

    // Initialize when DOM is ready
    $(document).ready(function() {
        if ($('#instant-poker-dialog').length > 0) {
            InstantPokerDialog.init();
        }
    });

})(AJS.$);
```

**Step 2: Verify JavaScript syntax**

Run: `npx jshint src/main/resources/js/instant-poker-dialog.js` (if jshint available, otherwise skip)

**Step 3: Commit**

```bash
git add src/main/resources/js/instant-poker-dialog.js
git commit -m "feat: add JavaScript for instant poker dialog with live voting and results"
```

---

## Task 8: Create REST Endpoint for Live Vote Updates

**Files:**
- Create: `src/main/java/com/redhat/engineering/plugins/rest/VoteResource.java`

**Step 1: Create REST resource class**

```java
package com.redhat.engineering.plugins.rest;

import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.redhat.engineering.plugins.domain.Session;
import com.redhat.engineering.plugins.domain.Vote;
import com.redhat.engineering.plugins.services.SessionService;
import com.redhat.engineering.plugins.services.VoteService;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST endpoint for live vote updates in instant poker sessions
 */
@Path("/votes")
@Produces({MediaType.APPLICATION_JSON})
public class VoteResource {

    private final SessionService sessionService;
    private final VoteService voteService;
    private final JiraAuthenticationContext authContext;

    public VoteResource(SessionService sessionService,
                       VoteService voteService,
                       JiraAuthenticationContext authContext) {
        this.sessionService = sessionService;
        this.voteService = voteService;
        this.authContext = authContext;
    }

    @GET
    @Path("/{issueKey}")
    public Response getVotes(@PathParam("issueKey") String issueKey) {
        try {
            Session session = sessionService.get(issueKey);
            if (session == null) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\":\"Session not found\"}")
                    .build();
            }

            List<Vote> votes = voteService.getVotesBySession(session);

            Map<String, Object> result = new HashMap<>();
            result.put("count", votes.size());

            List<Map<String, String>> voters = votes.stream()
                .map(vote -> {
                    Map<String, String> voterInfo = new HashMap<>();
                    ApplicationUser voter = vote.getVoter();
                    voterInfo.put("username", voter.getUsername());
                    voterInfo.put("displayName", voter.getDisplayName());
                    // Don't show actual vote value until session ends
                    if (System.currentTimeMillis() >= session.getEnd().getTime()) {
                        voterInfo.put("value", vote.getValue());
                    }
                    return voterInfo;
                })
                .collect(Collectors.toList());

            result.put("voters", voters);
            result.put("sessionEnded", System.currentTimeMillis() >= session.getEnd().getTime());

            return Response.ok(result).build();

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("{\"error\":\"" + e.getMessage() + "\"}")
                .build();
        }
    }
}
```

**Step 2: Register REST resource in plugin descriptor**

Add to `src/main/resources/atlassian-plugin.xml`:

```xml
<!-- REST API for instant poker -->
<rest key="poker-rest" path="/poker" version="1.0">
    <description>REST API for Planning Poker votes and sessions</description>
</rest>

<component key="voteResource"
           class="com.redhat.engineering.plugins.rest.VoteResource"
           public="true">
    <description>REST resource for vote operations</description>
    <interface>com.redhat.engineering.plugins.rest.VoteResource</interface>
</component>
```

**Step 3: Verify compilation**

Run: `docker-compose run --rm build mvn compile`
Expected: `BUILD SUCCESS`

**Step 4: Commit**

```bash
git add src/main/java/com/redhat/engineering/plugins/rest/VoteResource.java
git add src/main/resources/atlassian-plugin.xml
git commit -m "feat: add REST endpoint for live vote updates in instant poker"
```

---

## Task 9: Add CSS Styling for Instant Poker Dialog

**Files:**
- Modify: `src/main/resources/css/planning-poker.css`

**Step 1: Add CSS styles**

Append to the existing CSS file:

```css
/* Instant Planning Poker Dialog Styles */

.instant-poker-cards {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    margin: 20px 0;
}

.instant-poker-card {
    width: 60px;
    height: 80px;
    font-size: 24px;
    font-weight: bold;
    border: 2px solid #ccc;
    border-radius: 8px;
    background: linear-gradient(to bottom, #fff 0%, #f5f5f5 100%);
    cursor: pointer;
    transition: all 0.2s ease;
}

.instant-poker-card:hover {
    background: linear-gradient(to bottom, #e3f2fd 0%, #bbdefb 100%);
    border-color: #2196f3;
    transform: translateY(-2px);
    box-shadow: 0 4px 8px rgba(0,0,0,0.2);
}

.instant-poker-card.selected {
    background: linear-gradient(to bottom, #1976d2 0%, #1565c0 100%);
    border-color: #0d47a1;
    color: white;
    transform: scale(1.1);
    box-shadow: 0 6px 12px rgba(0,0,0,0.3);
}

#live-votes-display {
    margin-top: 30px;
    padding: 15px;
    background-color: #f5f5f5;
    border-radius: 4px;
}

#live-votes-display h4 {
    margin-top: 0;
}

.voter-list {
    list-style: none;
    padding: 0;
}

.voter-list li {
    padding: 8px;
    border-bottom: 1px solid #ddd;
}

.vote-hidden {
    color: #999;
    font-style: italic;
}

.instant-poker-stats {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 15px;
    margin: 20px 0;
}

.stat-item {
    padding: 15px;
    background-color: #e3f2fd;
    border-left: 4px solid #2196f3;
    border-radius: 4px;
}

.stat-label {
    display: block;
    font-size: 12px;
    color: #666;
    text-transform: uppercase;
    margin-bottom: 5px;
}

.stat-value {
    display: block;
    font-size: 24px;
    font-weight: bold;
    color: #1976d2;
}

.instant-poker-final-buttons {
    display: flex;
    gap: 10px;
    margin: 20px 0;
}

.apply-estimate-btn {
    flex: 1;
    padding: 15px;
    font-size: 16px;
}

#end-session-btn {
    margin-top: 20px;
}

#instant-poker-messages {
    margin-top: 15px;
}
```

**Step 2: Verify CSS syntax**

Run: `docker-compose run --rm build mvn validate`
Expected: `BUILD SUCCESS`

**Step 3: Commit**

```bash
git add src/main/resources/css/planning-poker.css
git commit -m "feat: add CSS styling for instant poker dialog"
```

---

## Task 10: Register JavaScript and CSS Resources in Plugin Descriptor

**Files:**
- Modify: `src/main/resources/atlassian-plugin.xml`

**Step 1: Add web-resource for instant poker dialog**

Add after the existing planning-poker-resources web-resource:

```xml
<web-resource key="instant-poker-dialog" name="Instant Poker Dialog Resources">
    <description>JavaScript and CSS for instant planning poker dialog</description>
    <resource type="download" name="instant-poker-dialog.js" location="/js/instant-poker-dialog.js"/>
    <resource type="download" name="instant-poker.css" location="/css/planning-poker.css"/>
    <context>atl.general</context>
    <context>jira.view.issue</context>
    <dependency>com.atlassian.auiplugin:ajs</dependency>
    <dependency>com.atlassian.auiplugin:dialog2</dependency>
    <dependency>jira.webresources:autocomplete</dependency>
</web-resource>
```

**Step 2: Verify XML syntax**

Run: `docker-compose run --rm build mvn validate`
Expected: `BUILD SUCCESS`

**Step 3: Commit**

```bash
git add src/main/resources/atlassian-plugin.xml
git commit -m "feat: register instant poker dialog resources in plugin descriptor"
```

---

## Task 11: Register InstantVoteDialogAction in Plugin Descriptor

**Files:**
- Modify: `src/main/resources/atlassian-plugin.xml`

**Step 1: Add webwork1 action**

Add after the InstantPokerSession action:

```xml
<webwork1 key="instant-vote-dialog-action" name="Instant Vote Dialog Action" class="java.lang.Object">
    <description>Action for instant vote dialog with live updates</description>
    <actions>
        <action name="com.redhat.engineering.plugins.actions.InstantVoteDialogAction" alias="InstantVoteDialog" roles-required="use">
            <view name="success">/views/success.vm</view>
            <view name="error">/views/success.vm</view>
            <view name="show-results">/views/vote/instant-dialog.vm</view>
        </action>
    </actions>
</webwork1>
```

**Step 2: Verify XML syntax**

Run: `docker-compose run --rm build mvn validate`
Expected: `BUILD SUCCESS`

**Step 3: Commit**

```bash
git add src/main/resources/atlassian-plugin.xml
git commit -m "feat: register InstantVoteDialogAction in plugin descriptor"
```

---

## Task 12: Build and Test Complete Feature

**Files:**
- All modified files

**Step 1: Clean build**

Run: `docker-compose run --rm build mvn clean package`
Expected: `BUILD SUCCESS` with JAR at `target/planning-poker-2.0.0-SNAPSHOT.jar`

**Step 2: Create test checklist**

Create file `docs/testing/instant-poker-test-plan.md`:

```markdown
# Instant Planning Poker Test Plan

## Test 1: Create Instant Session
1. Navigate to an issue
2. Click "Instant Planning Poker" menu item
3. Verify instant session is created (check in database or logs)
4. Verify voting dialog appears immediately

## Test 2: Vote as Regular User
1. Open instant poker dialog
2. Click on a card value (e.g., "5")
3. Verify card is highlighted
4. Add optional comment
5. Verify vote is saved (check AJAX response)
6. Verify vote count increases in UI

## Test 3: Multiple Users Voting
1. Open issue in two different browsers (different users)
2. Both users click "Instant Planning Poker"
3. Both cast votes
4. Verify both see live vote count updates

## Test 4: End Session as Creator
1. As session creator, click "End Session & Show Results"
2. Verify session end time is updated
3. Verify statistics appear (min, max, average)
4. Verify voting section is hidden
5. Verify results section is shown

## Test 5: Apply Estimate as Creator
1. After ending session, verify three buttons appear: Min, Max, Average
2. Click "Apply Average"
3. Verify estimate is applied to customfield_10205
4. Verify dialog closes
5. Verify issue page reloads with updated estimate

## Test 6: Non-Creator Permissions
1. As non-creator user, verify no "End Session" button
2. After session ends, verify no "Apply Estimate" buttons
3. Verify can still see results

## Test 7: Reuse Existing Instant Session
1. Create instant session for issue A
2. Navigate away from issue A
3. Return to issue A and click "Instant Planning Poker" again
4. Verify same session is reused (check start time hasn't changed)
5. Verify previous votes are still there

## Test 8: Permission Checks
1. As user without EDIT_ISSUE permission, verify cannot access instant poker
2. Verify appropriate error message
```

**Step 3: Commit test plan**

```bash
git add docs/testing/instant-poker-test-plan.md
git commit -m "docs: add instant planning poker test plan"
```

**Step 4: Final verification**

Run: `docker-compose run --rm build mvn verify`
Expected: `BUILD SUCCESS`

---

## Task 13: Update Documentation

**Files:**
- Modify: `README.md` (or create if doesn't exist)

**Step 1: Add instant poker documentation**

Add section to README:

```markdown
## Features

### Planning Poker Sessions
Create scheduled planning poker sessions with custom start/end times and email notifications.

### Instant Planning Poker (NEW in v2.0)
Quick, real-time planning poker with automatic setup:
- **One-Click Start**: Click "Instant Planning Poker" to auto-create 1-hour session
- **Immediate Voting**: No form filling - voting dialog opens instantly
- **Live Updates**: See vote count update in real-time as team members vote
- **Quick Consensus**: Creator can end session anytime (not time-bound)
- **Auto-Apply**: Selected estimate (min/max/average) automatically updates issue field
- **Session Reuse**: Returns to existing instant session if already active

**How to Use:**
1. Open any issue
2. Click "Instant Planning Poker" from issue actions menu
3. Vote by clicking a card value
4. Creator clicks "End Session" when ready
5. Creator selects final estimate (min/max/average)
6. Estimate is applied to issue automatically

**Requirements:**
- EDIT_ISSUE permission required to create instant sessions
- Custom field `customfield_10205` ("Estimate") must exist in your JIRA instance
```

**Step 2: Commit documentation**

```bash
git add README.md
git commit -m "docs: add instant planning poker feature documentation"
```

---

## Verification Commands

After all tasks:

```bash
# Full clean build
docker-compose run --rm build mvn clean package

# Check JAR created
ls -lh target/planning-poker-2.0.0-SNAPSHOT.jar

# Verify all new files committed
git status

# Review commit history
git log --oneline -15
```

---

## Known Limitations & Future Enhancements

1. **Real-time Updates**: Currently polls every 3 seconds. Could be enhanced with WebSockets for true push updates.

2. **Custom Field ID**: Hardcoded to `customfield_10205`. Could be made configurable in plugin settings.

3. **Vote Visibility**: Votes are hidden during session. Could add option to show votes immediately.

4. **Session Duration**: Fixed at 1 hour. Could be made configurable.

5. **Concurrent Sessions**: Only one instant session per issue. Could support multiple concurrent instant sessions.

---

## Rollback Plan

If issues occur in production:

```bash
# Revert to previous version
git revert <commit-range>
mvn clean package
# Deploy previous JAR
```

Alternatively, disable instant poker feature by removing web-item from plugin descriptor without full code removal.
