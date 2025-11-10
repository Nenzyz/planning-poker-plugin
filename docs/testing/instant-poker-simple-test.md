# Instant Planning Poker Test Plan

## Test 1: Auto-Create Session (Modal Dialog)
1. Open issue without existing session
2. Click "Instant Planning Poker" button
3. Verify modal dialog opens without page navigation
4. Verify session created with start=NOW, end=NOW+1hour
5. Verify voting cards visible in dialog

## Test 2: Reuse Existing Session (Modal Dialog)
1. Create regular session via "Create Poker Session"
2. Click "Instant Planning Poker"
3. Verify same session is reused (check start date)
4. Verify modal dialog opens with voting interface

## Test 3: Vote in Modal Dialog
1. In instant poker dialog, click card value
2. Add comment in textarea
3. Verify card becomes active (highlighted)
4. Verify vote saved via AJAX (success flag appears)
5. Verify dialog stays open (no page navigation)

## Test 4: End Session (Creator Only)
1. As creator, vote first
2. Verify "End Session & Show Results" button appears
3. Click "End Session & Show Results"
4. Confirm the confirmation prompt
5. Verify session end date set to NOW
6. Verify stats appear in dialog (min/max/average)
7. Verify dialog content updates without closing

## Test 5: Apply Estimate (Creator Only)
1. After ending session, verify stats shown
2. Verify three buttons visible:
   - Apply Min (X)
   - Apply Average (Y)
   - Apply Max (Z)
3. Click one button (e.g., Apply Average)
4. Verify success flag appears
5. Verify dialog closes after 1.5 seconds
6. Verify page reloads
7. Verify customfield_10205 updated with selected value

## Test 6: Non-Creator Experience
1. As non-creator user, click "Instant Planning Poker"
2. Verify dialog opens with voting interface
3. Vote and verify vote saved
4. Verify NO "End Session" button visible
5. If session ended by creator, verify stats visible
6. Verify NO apply estimate buttons visible

## Test 7: Page Stays Unchanged
1. Note current URL before clicking "Instant Planning Poker"
2. Click "Instant Planning Poker"
3. Verify URL unchanged (dialog opens, no navigation)
4. Vote and verify URL still unchanged
5. Close dialog and verify back on original issue page

## Test 8: Session Already Ended
1. Create session with end date in past
2. Click "Instant Planning Poker"
3. Verify dialog shows results section immediately
4. Verify voting section NOT shown
5. If creator, verify apply estimate buttons visible
