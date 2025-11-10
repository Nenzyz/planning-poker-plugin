# Current Dependency Analysis

## Maven Dependencies
- JIRA API: 6.4
- AMPS: 5.0.13
- Java: 1.6
- Guava: 14.0.1
- GSON: 2.2.2-atlassian-1

## API Usage Patterns to Review
- [ ] com.atlassian.crowd.embedded.api.User usage
- [ ] Webwork1 actions
- [ ] Velocity templates
- [ ] JavaScript globals
- [ ] REST API endpoints

## Breaking Changes to Address
1. User → ApplicationUser migration
2. Webwork1 deprecation status
3. Java 6 → Java 17 compatibility
4. AMPS 5.x → 9.x migration
5. Spring 2 → Spring 3+

## Findings from Codebase Analysis

### Deprecated User API Search
**Result:** No deprecated `com.atlassian.crowd.embedded.api.User` imports found!

The codebase has ALREADY been migrated to `ApplicationUser`. Found in files:
- `/src/main/java/com/redhat/engineering/plugins/panels/PlanningPokerPanel.java:15` - uses ApplicationUser
- `/src/main/java/com/redhat/engineering/plugins/actions/SessionAction.java:12` - uses ApplicationUser
- `/src/main/java/com/redhat/engineering/plugins/actions/VoteAction.java:10` - uses ApplicationUser
- `/src/main/java/com/redhat/engineering/plugins/domain/Vote.java:3` - uses ApplicationUser
- `/src/main/java/com/redhat/engineering/plugins/domain/Session.java:4` - uses ApplicationUser
- `/src/main/java/com/redhat/engineering/plugins/services/SessionService.java:8` - uses UserManager (modern API)
- `/src/main/java/com/redhat/engineering/plugins/services/VoteService.java:5` - uses ApplicationUser

**Status:** ✅ User API migration already complete

### Webwork1 Usage Analysis
**Result:** Webwork1 actions are still in use.

Found in:
- `/src/main/resources/atlassian-plugin.xml` - contains `<webwork1>` plugin descriptor with planning poker actions

**Lines found:**
```
<!-- webworks -->
<webwork1 key="planning-poker-webwork" name="Planning Poker" i18n-name-key="planning-poker-webwork.name">
</webwork1>
```

**Status:** ⚠️ Webwork1 deprecated but still supported in JIRA 9.11.2

### Current POM Dependencies (Confirmed)
From `pom.xml` lines 115-121:
- `jira.version`: 6.4 (line 116)
- `amps.version`: 5.0.13 (line 117)
- `plugin.testrunner.version`: 1.2.0 (line 118)
- `testkit.version`: 5.2.26 (line 120)
- Java compiler: 1.6 (lines 109-110)
- JUnit: 4.10 (line 35)
- Guava: 14.0.1 (line 60)
- GSON: 2.2.2-atlassian-1 (line 65)
- Mockito: 1.8.5 (line 80)

### Files Requiring Updates for JIRA 9.11.2

#### Critical Updates Required:
1. **pom.xml** (lines 7, 35, 60, 65, 80, 109-110, 116-120)
   - Version: 1.2 → 2.0.0
   - JIRA version: 6.4 → 9.11.2
   - AMPS: 5.0.13 → 9.0.2
   - Java compiler: 1.6 → 17
   - JUnit: 4.10 → 4.13.2
   - Guava: 14.0.1 → 31.1-jre
   - GSON: 2.2.2-atlassian-1 → 2.10.1
   - Mockito: 1.8.5 → 4.x

#### Optional Updates (Future Consideration):
2. **src/main/resources/atlassian-plugin.xml**
   - Webwork1 actions could be migrated to REST API (not required for 9.11.2)

### Summary

**Good News:**
- ✅ User → ApplicationUser migration ALREADY COMPLETE
- ✅ No deprecated User API found in codebase
- ✅ Modern ApplicationUser already in use throughout

**Updates Required:**
- 🔧 POM dependencies must be updated
- 🔧 Java version must be upgraded to 17
- 🔧 AMPS and JIRA versions must be updated
- ⚠️ Webwork1 still works in JIRA 9.11.2 (can be kept for now)

**Risk Assessment:**
- **Low Risk:** Code already uses modern APIs
- **Main Work:** Build system and dependency updates
- **Testing Focus:** Verify compatibility with updated dependencies
