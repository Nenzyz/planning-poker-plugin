(function($) {
    'use strict';

    window.InstantPoker = {
        init: function() {
            // Use event delegation to handle dynamically loaded menu items
            $(document).on('click', '.instant-poker-trigger', function(e) {
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
