# Instant Planning Poker Implementation Plan (Revised v2 - Modal Dialog)

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add "Instant Planning Poker" that opens modal dialog on issue page, auto-creates 1-hour sessions, allows voting without page navigation, shows "End Session" button to creator, and displays results with estimate application.

**Architecture:** Use AUI Dialog2 modal, AJAX for session creation and voting, existing Session/Vote backend. JavaScript handles dialog lifecycle, AJAX loads vote form content, all actions happen without page navigation.

**Tech Stack:** AUI Dialog2, jQuery AJAX, existing JIRA Webwork1 Actions, Velocity templates

---

## Completed Tasks (Keep as-is)

✅ **Task 1**: InstantPokerAction created
✅ **Task 2**: Menu item and action registered

---

## Task 3 (REVISED): Create JavaScript for Dialog Handling

**Files:**
- Create: `src/main/resources/js/instant-poker-dialog.js`
- Modify: `src/main/resources/atlassian-plugin.xml` (web-resource)

**Step 1: Create JavaScript file**

```javascript
(function($) {
    'use strict';

    window.InstantPoker = {
        init: function() {
            // Bind to instant poker link clicks
            $('.instant-poker-trigger').on('click', function(e) {
                e.preventDefault();
                var issueKey = $(this).attr('href').match(/key=([^&]+)/)[1];
                InstantPoker.openDialog(issueKey);
            });
        },

        openDialog: function(issueKey) {
            // Create or reuse session via AJAX
            $.ajax({
                url: AJS.contextPath() + '/secure/InstantPoker!default.jspa',
                type: 'POST',
                data: {
                    key: issueKey,
                    atl_token: $('#atlassian-token').attr('content')
                },
                success: function() {
                    InstantPoker.showVotingDialog(issueKey);
                },
                error: function(xhr) {
                    AJS.flag({
                        type: 'error',
                        title: 'Error',
                        body: 'Failed to create session: ' + xhr.responseText
                    });
                }
            });
        },

        showVotingDialog: function(issueKey) {
            // Create dialog
            var dialog = new AJS.Dialog({
                width: 800,
                height: 600,
                id: 'instant-poker-dialog',
                closeOnOutsideClick: false
            });

            dialog.addHeader('Instant Planning Poker - ' + issueKey);

            // Load voting content via AJAX
            dialog.addPanel('Vote Panel', '<div id="instant-vote-content">Loading...</div>', 'panel-body');

            dialog.addButton('Close', function() {
                dialog.hide();
                location.reload(); // Refresh to show updated panel
            });

            dialog.show();

            // Load vote form
            $('#instant-vote-content').load(
                AJS.contextPath() + '/secure/PokerVote!default.jspa?key=' + issueKey + '&instant=true',
                function() {
                    InstantPoker.bindVoteHandlers(issueKey, dialog);
                }
            );
        },

        bindVoteHandlers: function(issueKey, dialog) {
            var $content = $('#instant-vote-content');

            // Vote card selection
            $content.find('.card').on('click', function() {
                InstantPoker.selectCard($(this), issueKey, dialog);
            });

            // End session button
            $content.find('#end-session-btn').on('click', function() {
                InstantPoker.endSession(issueKey, dialog);
            });

            // Apply estimate buttons
            $content.find('.apply-estimate').on('click', function() {
                var value = $(this).data('value');
                InstantPoker.applyEstimate(value, issueKey, dialog);
            });
        },

        selectCard: function($card, issueKey, dialog) {
            var value = $card.text().trim();
            var comment = $('#instant-vote-content').find('#voteComment').val();

            $.ajax({
                url: AJS.contextPath() + '/secure/PokerVote.jspa',
                type: 'POST',
                data: {
                    key: issueKey,
                    action: 'vote',
                    voteVal: value,
                    voteComment: comment,
                    atl_token: $('#atlassian-token').attr('content')
                },
                success: function() {
                    $card.addClass('active').siblings().removeClass('active');
                    AJS.flag({
                        type: 'success',
                        title: 'Success',
                        body: 'Vote saved',
                        close: 'auto'
                    });
                },
                error: function(xhr) {
                    AJS.flag({
                        type: 'error',
                        title: 'Error',
                        body: 'Failed to save vote'
                    });
                }
            });
        },

        endSession: function(issueKey, dialog) {
            if (!confirm('End this session and show results?')) {
                return;
            }

            $.ajax({
                url: AJS.contextPath() + '/secure/PokerVote.jspa',
                type: 'POST',
                data: {
                    key: issueKey,
                    action: 'endSession',
                    atl_token: $('#atlassian-token').attr('content')
                },
                success: function() {
                    // Reload dialog content to show results
                    $('#instant-vote-content').load(
                        AJS.contextPath() + '/secure/PokerVote!default.jspa?key=' + issueKey + '&instant=true',
                        function() {
                            InstantPoker.bindVoteHandlers(issueKey, dialog);
                        }
                    );
                },
                error: function(xhr) {
                    AJS.flag({
                        type: 'error',
                        title: 'Error',
                        body: 'Failed to end session'
                    });
                }
            });
        },

        applyEstimate: function(value, issueKey, dialog) {
            $.ajax({
                url: AJS.contextPath() + '/secure/PokerVote.jspa',
                type: 'POST',
                data: {
                    key: issueKey,
                    action: 'applyEstimate',
                    finalValue: value,
                    atl_token: $('#atlassian-token').attr('content')
                },
                success: function() {
                    AJS.flag({
                        type: 'success',
                        title: 'Success',
                        body: 'Estimate applied: ' + value,
                        close: 'auto'
                    });
                    setTimeout(function() {
                        dialog.hide();
                        location.reload();
                    }, 1500);
                },
                error: function(xhr) {
                    AJS.flag({
                        type: 'error',
                        title: 'Error',
                        body: 'Failed to apply estimate'
                    });
                }
            });
        }
    };

    // Initialize on DOM ready
    $(document).ready(function() {
        InstantPoker.init();
    });

})(AJS.$);
```

**Step 2: Register web-resource in plugin.xml**

Add after existing `planning-poker-resources`:

```xml
<web-resource key="instant-poker-dialog-js" name="Instant Poker Dialog JavaScript">
    <description>JavaScript for instant poker modal dialog</description>
    <resource type="download" name="instant-poker-dialog.js" location="/js/instant-poker-dialog.js"/>
    <context>jira.view.issue</context>
    <dependency>com.atlassian.auiplugin:ajs</dependency>
    <dependency>com.atlassian.auiplugin:dialog2</dependency>
</web-resource>
```

**Step 3: Verify and commit**

Run: `docker-compose run --rm build mvn compile`
Expected: `BUILD SUCCESS`

```bash
git add src/main/resources/js/instant-poker-dialog.js
git add src/main/resources/atlassian-plugin.xml
git commit -m "feat: add JavaScript for instant poker modal dialog"
```

---

## Task 4: Enhance VoteAction (Keep Most of Original Plan)

**Files:**
- Modify: `src/main/java/com/redhat/engineering/plugins/actions/VoteAction.java`

**Follow original Task 4 from plan** - Add:
- `action` and `finalValue` properties
- `doVote()`, `doEndSession()`, `doApplyEstimate()` methods
- `getSessionStats()`, `isCreator()`, `isSessionEnded()` helper methods
- Required imports

(Use exact code from original plan Task 4)

---

## Task 5 (REVISED): Update Vote Template for Dialog Content

**Files:**
- Modify: `src/main/resources/views/vote/input.vm`

**Step 1: Add instant mode that returns minimal content (not full HTML page)**

At the top of file, detect instant mode:

```velocity
#set($isInstant = $request.getParameter("instant") == "true")
#set($sessionEnded = $action.isSessionEnded())
#set($isCreator = $action.isCreator())

#if($isInstant)
    ## Return just the content div (no HTML/HEAD/BODY wrapper)
    #set($layoutMode = "dialog-content")
#end
```

**Step 2: Wrap main content in conditional HTML wrapper**

```velocity
#if(!$isInstant)
<html>
<head>
    <title>Vote</title>
    <meta name="decorator" content="issueaction" />
</head>
<body>
#end

<!-- Main voting content (always shown) -->
<div id="instant-vote-wrapper">
#if(!$sessionEnded)
    <!-- Voting cards -->
    <div class="cards">
        #foreach($allowedVote in $action.allowedVotes)
            <div class="card" data-value="$allowedVote">$allowedVote</div>
        #end
    </div>

    <!-- Comment field -->
    <div class="field-group">
        <label for="voteComment">Comment</label>
        <textarea id="voteComment" name="voteComment" class="textarea" cols="40" rows="3">#if($voteComment)$voteComment#end</textarea>
    </div>

    <!-- End session button (creator only) -->
    #if($isCreator)
        <button type="button" id="end-session-btn" class="aui-button aui-button-primary">
            End Session & Show Results
        </button>
    #end
#end

<!-- Results section -->
#if($sessionEnded)
    #set($stats = $action.sessionStats)
    <div id="results-section">
        <h3>Session Results</h3>

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
                <h4>Apply Final Estimate</h4>
                <div class="buttons-container">
                    <button type="button" class="aui-button apply-estimate" data-value="$stats.get('min')">
                        Apply Min ($stats.get("min"))
                    </button>
                    <button type="button" class="aui-button apply-estimate" data-value="$stats.get('average')">
                        Apply Average ($stats.get("average"))
                    </button>
                    <button type="button" class="aui-button apply-estimate" data-value="$stats.get('max')">
                        Apply Max ($stats.get("max"))
                    </button>
                </div>
            #end
        #else
            <p>No votes cast yet.</p>
        #end
    </div>
#end
</div>

#if(!$isInstant)
</body>
</html>
#end
```

**Step 3: Verify and commit**

```bash
git add src/main/resources/views/vote/input.vm
git commit -m "feat: update vote template for dialog content mode"
```

---

## Task 6: Add CSS (Keep from Original Plan)

**Follow original Task 6** - Append CSS to `planning-poker.css`

---

## Task 7: Build and Test (Keep from Original Plan)

**Follow original Task 7** - Clean build and create test documentation

---

## Task 8: Update README (Keep from Original Plan)

**Follow original Task 8** - Add feature documentation

---

## Summary of Changes from v1

**What changed:**
- ❌ Removed: `instant-redirect.vm` (no page redirect)
- ✅ Added: `instant-poker-dialog.js` (JavaScript dialog handling)
- ✅ Modified: Vote template returns content-only for AJAX (no full page wrapper)
- ✅ Modified: Web-resource for JavaScript on issue pages

**User experience:**
1. Click "Instant Planning Poker" → JavaScript intercepts
2. AJAX creates session
3. AUI Dialog2 opens with vote form (no page navigation)
4. Vote via AJAX (stay in dialog)
5. Creator sees "End Session" button after voting
6. Results shown in same dialog
7. Apply estimate closes dialog and reloads issue
