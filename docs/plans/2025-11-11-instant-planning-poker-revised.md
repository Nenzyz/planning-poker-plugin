# Instant Planning Poker Implementation Plan (Revised)

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add "Instant Planning Poker" menu item that auto-creates sessions with 1-hour defaults and immediately opens voting dialog, plus add "End Now" and "Apply Estimate" features that work for ALL sessions.

**Architecture:** Reuse existing Session/Vote infrastructure. Add new action that auto-creates session with defaults and redirects to enhanced voting dialog. Add session end override, statistics calculation, and issue field update for any completed session.

**Tech Stack:** Existing JIRA Webwork1 Actions, Velocity templates, jQuery, AUI Dialog2, existing Active Objects

---

## Task 1: Create InstantPokerAction for Auto-Session Creation

**Files:**
- Create: `src/main/java/com/redhat/engineering/plugins/actions/InstantPokerAction.java`

**Step 1: Create action class**

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

        // Redirect to voting dialog (will enhance VoteAction to show dialog)
        return "redirect-vote";
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
git add src/main/java/com/redhat/engineering/plugins/actions/InstantPokerAction.java
git commit -m "feat: add InstantPokerAction for auto-session creation"
```

---

## Task 2: Register InstantPokerAction and Menu Item

**Files:**
- Modify: `src/main/resources/atlassian-plugin.xml`

**Step 1: Add webwork1 action**

Add after existing PokerSession action:

```xml
<webwork1 key="instant-poker-action" name="Instant Poker Action" class="java.lang.Object">
    <description>Auto-create session and redirect to voting</description>
    <actions>
        <action name="com.redhat.engineering.plugins.actions.InstantPokerAction" alias="InstantPoker" roles-required="use">
            <view name="redirect-vote">/views/vote/instant-redirect.vm</view>
        </action>
    </actions>
</webwork1>
```

**Step 2: Add menu item**

Add after existing "Planning Poker Session" web-item:

```xml
<web-item name="Instant Poker" key="instant-poker-link"
          section="operations-operations" weight="1001">
    <label>Instant Planning Poker</label>
    <tooltip>Quick start: auto-create 1-hour session and vote now</tooltip>
    <link linkId="instant-poker-link">/secure/InstantPoker!default.jspa?key=${issue.key}</link>
    <styleClass>instant-poker-trigger</styleClass>
    <condition class="com.atlassian.jira.plugin.webfragment.conditions.IsIssueEditableCondition"/>
    <condition class="com.atlassian.jira.plugin.webfragment.conditions.HasIssuePermissionCondition">
        <param name="permission">edit</param>
    </condition>
</web-item>
```

**Step 3: Verify XML**

Run: `docker-compose run --rm build mvn validate`
Expected: `BUILD SUCCESS`

**Step 4: Commit**

```bash
git add src/main/resources/atlassian-plugin.xml
git commit -m "feat: register InstantPoker action and menu item"
```

---

## Task 3: Create Instant Redirect Template

**Files:**
- Create: `src/main/resources/views/vote/instant-redirect.vm`

**Step 1: Create template that redirects to voting with dialog trigger**

```velocity
<html>
<head>
    <title>Redirecting to Vote</title>
    <meta http-equiv="refresh" content="0;url=${baseurl}/secure/PokerVote!default.jspa?key=${key}&instant=true">
</head>
<body>
<p>Redirecting to voting...</p>
</body>
</html>
```

**Step 2: Commit**

```bash
git add src/main/resources/views/vote/instant-redirect.vm
git commit -m "feat: add instant redirect template"
```

---

## Task 4: Enhance VoteAction with End Session and Apply Estimate

**Files:**
- Modify: `src/main/java/com/redhat/engineering/plugins/actions/VoteAction.java`

**Step 1: Add new properties and methods**

Add these fields after line 42:

```java
private String action; // "vote", "endSession", "applyEstimate"
private String finalValue;
```

Add getters/setters after line 78:

```java
public String getAction() { return action; }
public void setAction(String action) { this.action = action; }

public String getFinalValue() { return finalValue; }
public void setFinalValue(String finalValue) { this.finalValue = finalValue; }
```

**Step 2: Modify doExecute to handle actions**

Replace doExecute method (around line 117):

```java
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
```

**Step 3: Add imports**

Add at top of file:

```java
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.ModifiedValue;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.util.DefaultIssueChangeHolder;
import com.atlassian.jira.component.ComponentAccessor;
import java.util.Date;
```

**Step 4: Add statistics helper method**

Add new method:

```java
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
```

**Step 5: Verify compilation**

Run: `docker-compose run --rm build mvn compile`
Expected: `BUILD SUCCESS`

**Step 6: Commit**

```bash
git add src/main/java/com/redhat/engineering/plugins/actions/VoteAction.java
git commit -m "feat: add endSession and applyEstimate actions to VoteAction"
```

---

## Task 5: Enhance Vote Input Template with Instant Dialog

**Files:**
- Modify: `src/main/resources/views/vote/input.vm`

**Step 1: Add instant mode detection and dialog wrapper**

Replace the entire file starting from line 7:

```velocity
#macro(activate $voteVal $actualVal)
    #if ($voteVal == $actualVal)
        active
    #end
#end

$webResourceManager.requireResource("com.redhat.engineering.plugins.planning-poker:planning-poker-resources")

<html>
<head>
    <title>Vote</title>
    #if($request.getParameter("instant"))
        <meta name="decorator" content="atl.general"/>
    #else
        <meta name="decorator" content="issueaction" />
    #end
</head>
<body>

#set($isInstant = $request.getParameter("instant") == "true")
#set($sessionEnded = $action.isSessionEnded())
#set($isCreator = $action.isCreator())

<!-- Instant Dialog Mode -->
#if($isInstant)
<section id="instant-poker-dialog" class="instant-poker-wrapper"
         data-issue-key="${key}"
         data-is-creator="${isCreator}"
         data-session-ended="${sessionEnded}">

    <div class="instant-poker-header">
        <h2>Instant Planning Poker - ${key}</h2>
        <a href="${baseurl}/browse/${key}" class="close-instant">✕</a>
    </div>

    <div class="instant-poker-content">
#end

<!-- Voting Section (always shown unless session ended) -->
#if(!$sessionEnded)
<form action="PokerVote.jspa" id="vote-poker" class="aui" method="post">
    <input type="hidden" name="atl_token" value="$atl_token">
    <input type="hidden" name="action" value="vote">

    <div class="form-body">
        <h2 class="dialog-title">Vote</h2>

        <style>
            .card {
                display: inline-block;
                width: 2.1em;
                height: 3em;
                margin: 0 5px 5px 0;
                -webkit-box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.2), 0 1px 2px rgba(0, 0, 0, 0.05);
                box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.2), 0 1px 2px rgba(0, 0, 0, 0.05);
                border: 1px solid transparent;
                border-color: rgba(0, 0, 0, 0.1) rgba(0, 0, 0, 0.1) rgba(0, 0, 0, 0.25);
                border-bottom-color: #A2A2A2;
                position: relative;
                border-radius: 0.2em;
                color: #25201c;
                font-size: 1.5em;
                text-align: center;
                text-shadow: 0 1px 1px rgba(255, 255, 255, 0.75);
                line-height: 3em;
                cursor: pointer;
                background-color: rgb(224, 230, 230);
            }
            .card:hover {
                background-color: #6baff4;
            }
            .card.active {
                background-color: #3b7fc4;
                color: white;
            }
        </style>

        <script type="text/javascript">
            function chooseCard(card) {
                var cards = document.getElementsByClassName("card")
                for (var i = 0; i < cards.length; i++) {
                    cards[i].classList.remove("active")
                }
                card.classList.add("active")
                var voteVal = document.getElementById("poker-vote-value")
                voteVal.value = card.innerHTML
                document.getElementById("poker-vote-submit").removeAttribute("disabled")
            }
        </script>

        <div id="poker-vote-cards" class="cards">
            <div class="cards-row">
                #foreach($allowedVote in $action.allowedVotes)
                    <div class="card #activate($voteVal $allowedVote)" onclick="javascript:chooseCard(this)">$allowedVote</div>
                #end
            </div>
        </div>

        <div class="field-group">
            <label for="voteComment">Comment</label>
            <textarea id="voteComment" name="voteComment" class="textarea"
                      cols="40" rows="3">#if($voteComment)$voteComment#end</textarea>
        </div>

        <div class="hidden">
            <input type="hidden" name="voteVal" class="text" id="poker-vote-value" value="${voteVal}"/>
            <input type="hidden" name="key" value="${key}" />
        </div>
    </div>

    <div class="buttons-container form-footer">
        <div class="buttons">
            <input accesskey="s" class="button" id="poker-vote-submit"
                   name="Vote" title="Press Ctrl+Alt+s to submit this form"
                   type="submit" value="Vote" disabled="disabled">
            <a accesskey="`" class="cancel" href="/browse/${key}"
               title="Press Ctrl+Alt+` to cancel">Cancel</a>

            #if($isCreator && $isInstant)
                <button type="button" id="end-session-btn" class="button">
                    End Session & Show Results
                </button>
            #end
        </div>
    </div>
</form>
#end

<!-- Results Section (shown when session ended) -->
#if($sessionEnded)
#set($stats = $action.sessionStats)
<div id="results-section">
    <h2>Session Results</h2>

    #if($stats && !$stats.isEmpty())
        <div class="instant-poker-stats">
            <div class="stat-item">
                <span class="stat-label">Minimum:</span>
                <span class="stat-value">$stats.get("min")</span>
            </div>
            <div class="stat-item">
                <span class="stat-label">Maximum:</span>
                <span class="stat-value">$stats.get("max")</span>
            </div>
            <div class="stat-item">
                <span class="stat-label">Average:</span>
                <span class="stat-value">$stats.get("average")</span>
            </div>
            <div class="stat-item">
                <span class="stat-label">Total Votes:</span>
                <span class="stat-value">$stats.get("count")</span>
            </div>
        </div>

        #if($isCreator)
            <h3>Apply Final Estimate</h3>
            <form action="PokerVote.jspa" method="post" class="aui">
                <input type="hidden" name="atl_token" value="$atl_token">
                <input type="hidden" name="key" value="${key}">
                <input type="hidden" name="action" value="applyEstimate">
                <input type="hidden" name="finalValue" id="final-value">

                <div class="buttons-container">
                    <button type="button" class="button apply-estimate"
                            onclick="applyEstimate($stats.get('min'))">
                        Apply Min ($stats.get("min"))
                    </button>
                    <button type="button" class="button apply-estimate"
                            onclick="applyEstimate($stats.get('average'))">
                        Apply Average ($stats.get("average"))
                    </button>
                    <button type="button" class="button apply-estimate"
                            onclick="applyEstimate($stats.get('max'))">
                        Apply Max ($stats.get("max"))
                    </button>
                </div>
            </form>

            <script>
                function applyEstimate(value) {
                    document.getElementById('final-value').value = value;
                    document.forms[document.forms.length-1].submit();
                }
            </script>
        #end
    #else
        <p>No votes cast yet.</p>
    #end
</div>
#end

#if($isInstant)
    </div> <!-- end instant-poker-content -->
</section>

<script>
    document.getElementById('end-session-btn')?.addEventListener('click', function() {
        if (confirm('End this session and show results?')) {
            var form = document.createElement('form');
            form.method = 'POST';
            form.action = '${baseurl}/secure/PokerVote.jspa';

            var fields = {
                'atl_token': '$atl_token',
                'key': '${key}',
                'action': 'endSession'
            };

            for (var key in fields) {
                var input = document.createElement('input');
                input.type = 'hidden';
                input.name = key;
                input.value = fields[key];
                form.appendChild(input);
            }

            document.body.appendChild(form);
            form.submit();
        }
    });
</script>
#end

</body>
</html>
```

**Step 2: Verify template**

Run: `docker-compose run --rm build mvn validate`
Expected: `BUILD SUCCESS`

**Step 3: Commit**

```bash
git add src/main/resources/views/vote/input.vm
git commit -m "feat: enhance vote template with instant mode and end session"
```

---

## Task 6: Add CSS for Instant Poker Styling

**Files:**
- Modify: `src/main/resources/css/planning-poker.css`

**Step 1: Append CSS**

```css
/* Instant Poker Styling */

.instant-poker-wrapper {
    max-width: 800px;
    margin: 20px auto;
    padding: 20px;
    background: white;
    border-radius: 3px;
    box-shadow: 0 2px 4px rgba(0,0,0,0.2);
}

.instant-poker-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    border-bottom: 1px solid #ddd;
    padding-bottom: 10px;
    margin-bottom: 20px;
}

.instant-poker-header h2 {
    margin: 0;
}

.close-instant {
    font-size: 24px;
    text-decoration: none;
    color: #666;
    cursor: pointer;
}

.close-instant:hover {
    color: #000;
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

.apply-estimate {
    margin: 5px;
}

#end-session-btn {
    margin-left: 20px;
    background-color: #f44336;
    color: white;
}

#end-session-btn:hover {
    background-color: #d32f2f;
}
```

**Step 2: Commit**

```bash
git add src/main/resources/css/planning-poker.css
git commit -m "feat: add CSS styling for instant poker"
```

---

## Task 7: Build and Test

**Files:**
- All modified files

**Step 1: Clean build**

Run: `docker-compose run --rm build mvn clean package`
Expected: `BUILD SUCCESS` with JAR at `target/planning-poker-2.0.0-SNAPSHOT.jar`

**Step 2: Create test checklist**

Create: `docs/testing/instant-poker-simple-test.md`:

```markdown
# Instant Planning Poker Test Plan

## Test 1: Auto-Create Session
1. Open issue without existing session
2. Click "Instant Planning Poker"
3. Verify session created with start=NOW, end=NOW+1hour
4. Verify voting page opens immediately

## Test 2: Reuse Existing Session
1. Create regular session via "Create Poker Session"
2. Click "Instant Planning Poker"
3. Verify same session is reused (check start date)
4. Verify voting page opens

## Test 3: Vote
1. Click card value
2. Add comment
3. Click Vote
4. Verify vote saved

## Test 4: End Session (Creator Only)
1. As creator, click "End Session & Show Results"
2. Verify session end date set to NOW
3. Verify stats appear (min/max/average)
4. Verify voting section hidden

## Test 5: Apply Estimate (Creator Only)
1. After ending session, see 3 buttons: Min, Average, Max
2. Click "Apply Average"
3. Verify customfield_10205 updated on issue
4. Verify redirect to issue page

## Test 6: Non-Creator View
1. As non-creator, open instant poker
2. Verify can vote
3. Verify NO "End Session" button
4. After creator ends session, verify can see stats
5. Verify NO "Apply Estimate" buttons
```

**Step 3: Commit**

```bash
git add docs/testing/instant-poker-simple-test.md
git commit -m "docs: add instant poker test plan"
```

---

## Task 8: Update README

**Files:**
- Modify: `README.md`

**Step 1: Add feature documentation**

```markdown
## Instant Planning Poker

Quick workflow for fast estimation:

**Features:**
- One-click session creation (1-hour default)
- Reuses existing session if present
- Immediate voting - no page navigation
- Creator can end session anytime (overrides scheduled end)
- Shows statistics: min, max, average
- One-click apply estimate to issue field

**Usage:**
1. Click "Instant Planning Poker" on any issue
2. Vote by clicking card value
3. Creator clicks "End Session & Show Results"
4. Creator selects Min/Average/Max
5. Estimate applied to customfield_10205

**Note:** Works with same Session objects as regular poker - no separate session type.
```

**Step 2: Commit**

```bash
git add README.md
git commit -m "docs: add instant poker documentation"
```

---

## Verification

```bash
# Full build
docker-compose run --rm build mvn clean package

# Check JAR
ls -lh target/planning-poker-2.0.0-SNAPSHOT.jar

# Verify commits
git log --oneline -10
```

Expected commits:
1. feat: add InstantPokerAction for auto-session creation
2. feat: register InstantPoker action and menu item
3. feat: add instant redirect template
4. feat: add endSession and applyEstimate actions to VoteAction
5. feat: enhance vote template with instant mode and end session
6. feat: add CSS styling for instant poker
7. docs: add instant poker test plan
8. docs: add instant poker documentation

---

## Summary

This implementation:
- ✅ Reuses existing Session/Vote domain - no new types
- ✅ Auto-creates session with 1-hour defaults
- ✅ Reuses existing session if present
- ✅ Immediate redirect to voting
- ✅ End session early (set end=NOW)
- ✅ Statistics for ANY ended session
- ✅ Apply estimate updates customfield_10205
- ✅ Works for both instant and regular sessions
