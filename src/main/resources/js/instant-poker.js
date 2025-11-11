(function() {
    'use strict';

    function initInstantPoker() {
        // Only run in instant mode
        var wrapper = AJS.$('#instant-vote-wrapper');
        if (wrapper.length === 0) return;

        var isInstant = wrapper.attr('data-is-instant') === 'true';
        var isCreator = wrapper.attr('data-is-creator') === 'true';
        var issueKey = wrapper.attr('data-issue-key');

        console.log('Instant Poker Init - isInstant:', isInstant, 'isCreator:', isCreator, 'issueKey:', issueKey, 'issueKey type:', typeof issueKey, 'issueKey length:', issueKey ? issueKey.length : 'null/undefined');

        // Only add AJAX handler if in instant mode
        if (!isInstant) return;

        // Card click handler using event delegation
        wrapper.on('click', '.card', function(e) {
            e.preventDefault();
            var $this = AJS.$(this);
            var voteValue = $this.attr('data-value');

            // Remove active from all cards
            wrapper.find('.card').removeClass('active');
            // Add active to clicked card
            $this.addClass('active');

            // Submit vote via AJAX in instant mode
            var comment = AJS.$('#voteComment').val() || '';

            // Read key dynamically from wrapper
            var key = wrapper.attr('data-issue-key');
            console.log('Submitting vote with key:', key);

            AJS.$.ajax({
                url: AJS.contextPath() + '/secure/PokerVote.jspa',
                type: 'POST',
                data: {
                    key: key,
                    voteVal: voteValue,
                    voteComment: comment,
                    atl_token: AJS.$('meta[name="atlassian-token"]').attr('content')
                },
                success: function() {
                    AJS.flag({
                        type: 'success',
                        title: 'Vote Submitted',
                        body: 'Your vote has been recorded!',
                        close: 'auto'
                    });

                    // Show end session button for creator
                    if (isCreator) {
                        AJS.$('#end-session-container').show();
                    }
                },
                error: function(xhr) {
                    AJS.flag({
                        type: 'error',
                        title: 'Error',
                        body: 'Failed to submit vote: ' + (xhr.responseText || 'Unknown error')
                    });
                }
            });
        });

        // TODO: Real-time voter updates - disabled for now
        // Can be implemented later with a dedicated REST endpoint
        var pollInterval = null;

        // End session button handler (use event delegation on wrapper, not document)
        wrapper.on('click', '#end-session-btn', function() {
            if (!confirm('End this session and show results?')) {
                return;
            }

            // Read key dynamically from wrapper
            var key = wrapper.attr('data-issue-key');
            if (!key) {
                console.error('Issue key not found');
                return;
            }

            console.log('Ending session for key:', key);

            AJS.$.ajax({
                url: AJS.contextPath() + '/secure/PokerVote.jspa',
                type: 'POST',
                data: {
                    key: key,
                    action: 'endSession',
                    atl_token: AJS.$('meta[name="atlassian-token"]').attr('content')
                },
                success: function(response, status, xhr) {
                    console.log('End session response:', response, 'Status:', status, 'Status code:', xhr.status);

                    // Stop polling
                    if (pollInterval) {
                        clearInterval(pollInterval);
                    }

                    // Reload dialog content to show results
                    AJS.$.ajax({
                        url: AJS.contextPath() + '/secure/InstantPoker!default.jspa',
                        type: 'GET',
                        data: {
                            key: key,
                            instant: 'true'
                        },
                        success: function(html) {
                            console.log('Fetched fresh content, length:', html.length);

                            // Parse HTML and find wrapper (check root element and descendants)
                            var $parsed = AJS.$(html);
                            var newContent = $parsed.filter('#instant-vote-wrapper');
                            if (newContent.length === 0) {
                                newContent = $parsed.find('#instant-vote-wrapper');
                            }

                            console.log('Found wrapper elements:', newContent.length);

                            if (newContent.length > 0) {
                                console.log('Replacing content with results view');
                                var oldWrapper = AJS.$('#instant-vote-wrapper');
                                oldWrapper.replaceWith(newContent);
                                console.log('Content replaced, re-initializing');
                                // Re-initialize for the new content
                                initInstantPoker();
                            } else {
                                console.warn('Could not find wrapper in response');
                                AJS.flag({
                                    type: 'error',
                                    title: 'Error',
                                    body: 'Failed to refresh dialog content. Please close and reopen the dialog.',
                                    close: 'manual'
                                });
                            }
                        },
                        error: function(xhr, status, error) {
                            console.error('Failed to fetch fresh content:', status, error);
                            AJS.flag({
                                type: 'error',
                                title: 'Error',
                                body: 'Failed to fetch session results: ' + (error || 'Unknown error'),
                                close: 'manual'
                            });
                        }
                    });
                },
                error: function(xhr) {
                    AJS.flag({
                        type: 'error',
                        title: 'Error',
                        body: 'Failed to end session: ' + (xhr.responseText || 'Unknown error')
                    });
                }
            });
        });

        // Apply estimate button handler (use event delegation on wrapper, not document)
        wrapper.on('click', '.apply-estimate', function() {
            var value = AJS.$(this).data('value');

            // Read key dynamically from wrapper
            var key = wrapper.attr('data-issue-key');
            if (!key) {
                console.error('Issue key not found');
                return;
            }

            AJS.$.ajax({
                url: AJS.contextPath() + '/secure/PokerVote.jspa',
                type: 'POST',
                data: {
                    key: key,
                    action: 'applyEstimate',
                    finalValue: value,
                    atl_token: AJS.$('meta[name="atlassian-token"]').attr('content')
                },
                success: function() {
                    AJS.flag({
                        type: 'success',
                        title: 'Success',
                        body: 'Estimate ' + value + ' applied to issue!',
                        close: 'auto'
                    });

                    // Close dialog and reload page after 1.5 seconds
                    setTimeout(function() {
                        // Close the dialog if in one
                        var dialog = AJS.$('.jira-dialog');
                        if (dialog.length > 0) {
                            dialog.remove();
                        }

                        // Reload parent page
                        if (window.parent && window.parent.location) {
                            window.parent.location.reload();
                        } else {
                            window.location.reload();
                        }
                    }, 1500);
                },
                error: function(xhr) {
                    AJS.flag({
                        type: 'error',
                        title: 'Error',
                        body: 'Failed to apply estimate: ' + (xhr.responseText || 'Unknown error')
                    });
                }
            });
        });
    }

    // Initialize on document ready
    AJS.$(document).ready(initInstantPoker);

    // Initialize when JIRA dialog content is loaded
    JIRA.bind(JIRA.Events.NEW_CONTENT_ADDED, function(e, context) {
        initInstantPoker();
    });
})();
