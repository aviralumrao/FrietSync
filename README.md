# FrietSync - Authentication

This is the authentication part of the FrietSync backend. It includes signup, login, JWT authentication, refresh tokens, logout, forgot-password functionality with OTP, and role-based access control.

## What is in this part

- Signup and login
- Passwords are stored as BCrypt hashes, never as plain text
- JWT access tokens are issued after login
- Refresh tokens are used to obtain new access tokens
- A filter validates the access token on protected requests
- Role-based access control for admin routes
- Forgot-password flow using OTP sent to the user's email
- Logout and refresh-token handling
- Invite management for project members

## Folders

- `common/security` contains `JwtUtil`, `JwtAuthFilter`, and `SecurityConfig`
- `common/exception` contains custom exceptions, `ErrorResponse`, and `GlobalExceptionHandler`
- `common/mail` contains the classes responsible for sending emails
- `otp` contains `Otp`, `OtpPurpose`, `OtpRepository`, and `OtpService`
- `user` contains `AuthController`, DTOs, `User`, `Role`, `UserRepository`, and `AuthService`

## What each main class does

- **AuthController** handles authentication requests such as signup, login, OTP verification, password reset, refresh, and logout
- **AuthServiceImpl** contains the business logic for signup, login, OTP verification, password reset, token refresh, and logout
- **JwtUtil** creates and validates JWT access tokens
- **JwtAuthFilter** reads the access token from the request, validates it, and identifies the authenticated user
- **SecurityConfig** defines which URLs are public and which require authentication or specific roles
- **OtpService** generates OTPs, sends them by email, validates them, and handles OTP expiration and usage
- **GlobalExceptionHandler** converts application exceptions into consistent API error responses

## APIs

The backend routes are grouped under `/api/v1`, with selected v2 endpoints under `/api/v2`. Protected endpoints require `Authorization: Bearer <access-token>` unless noted otherwise.

For copyable JSON request and response examples, including multipart, binary-download, OAuth, and WebSocket operations, see the [API request and response examples](docs/API_EXAMPLES.md). The reference covers every controller route and identifies endpoints that have no JSON body.

| Method | URL | Access | Request body / notes |
|---|---|---|---|
| GET | `/api/v1/check` | Public | Health check |
| POST | `/api/v1/auth/signup` | Public | `name, email, password` |
| POST | `/api/v1/auth/resend-otp` | Public | `email` |
| POST | `/api/v1/auth/verify-otp` | Public | `email, code` |
| POST | `/api/v1/auth/login` | Public | `email, password`; sets the refresh-token cookie |
| POST | `/api/v1/auth/forgot-password` | Public | `email` |
| POST | `/api/v1/auth/reset-password` | Public | `email, code, newPassword` |
| POST | `/api/v1/auth/refresh` | Refresh-token cookie | No body; returns a new `accessToken` and refresh-token cookie |
| POST | `/api/v1/auth/logout` | Refresh-token cookie | No body; invalidates the refresh token and clears the cookie |
| POST | `/api/v1/admin/invites` | Admin | `email, role`, optional `expiresAt` |
| POST | `/api/v1/admin/invites/revoke` | Admin | `inviteId` |
| GET | `/api/v1/invites/me` | Authenticated | Lists the current user's pending invites |
| POST | `/api/v1/invites/accept` | Authenticated | `inviteId` |
| POST | `/api/v1/invites/reject` | Authenticated | `inviteId` |
| POST | `/api/v1/projects/{projectId}/sprints` | Authenticated | `name, startDate, endDate`, optional `goal` |
| GET | `/api/v1/projects/{projectId}/sprints` | Authenticated | Lists a project's sprints |
| GET | `/api/v1/sprints/{id}` | Authenticated | Gets a sprint |
| PATCH | `/api/v1/sprints/{id}` | Authenticated | Optional `name, goal, startDate, endDate` fields |
| POST | `/api/v1/sprints/{id}/start` | Authenticated | Starts a planned sprint |
| POST | `/api/v1/sprints/{id}/complete` | Authenticated | Completes an active sprint |
| DELETE | `/api/v1/sprints/{id}` | Authenticated | Deletes a sprint |

## Additional backend endpoints

The backend also exposes authenticated routes for workspaces, projects, issues, meetings, polls, releases, milestones, notifications, and chat. Unless specifically listed as public above, these endpoints require a valid access token.

### GitHub integration

Configure `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, `GITHUB_REDIRECT_URI` (the backend callback URL), `GITHUB_OAUTH_ENCRYPTION_KEY` (base64 for 32 random bytes), and `GITHUB_OAUTH_FRONTEND_RESULT_URI`. `GITHUB_OAUTH_STATE_TTL_SECONDS` is optional and defaults to 600. The OAuth app requests `read:user` and `repo` so the chosen project repository can be read, including private repositories. Access tokens are encrypted at rest.

| Method | Route | Purpose |
| --- | --- | --- |
| GET | `/api/v1/integrations/github/connect` | Return `{ "authorizationUrl": "..." }` for the signed-in user's OAuth flow |
| GET | `/api/v1/integrations/github/callback` | GitHub OAuth callback; redirects to the frontend with `?github=connected`, `denied`, or `failed` |
| DELETE | `/api/v1/integrations/github/disconnect` | Remove the signed-in user's GitHub connection |
| PUT | `/api/v1/integrations/github/projects/{projectId}/repository` | Link `{ "owner": "...", "repository": "..." }`; project admin, manager, or team lead only |
| DELETE | `/api/v1/integrations/github/projects/{projectId}/repository` | Unlink the project's repository; project admin, manager, or team lead only |
| GET | `/api/v1/integrations/github/projects/{projectId}/contributions` | Return commit totals and daily counts, pull request totals, and merged pull request totals for project members |

Each member connects their own GitHub account to associate a GitHub login with their FrietSync user. A project admin, manager, or team lead can link a repository that belongs to another person or organization, provided their connected GitHub account has access to it. The repository must be readable by the account performing the contribution request; users without repository access cannot fetch its activity. The member list includes users without a GitHub connection with zero activity. GitHub activity is fetched live and currently paginates up to the most recent 1,000 commits and 1,000 pull requests.

### Workspaces and projects

| Method | URL | Access | Notes |
|---|---|---|---|
| POST | `/api/v1/workspaces` | Authenticated | Create a workspace |
| GET | `/api/v1/workspaces/me` | Authenticated | Get the current workspace |
| GET | `/api/v1/workspaces/{workspaceId}` | Authenticated | Get workspace details |
| PATCH | `/api/v1/workspaces/{workspaceId}` | Authenticated | Update a workspace |
| GET | `/api/v1/workspaces/{workspaceId}/members` | Authenticated | List workspace members |
| POST | `/api/v1/projects` | Authenticated | Create a project |
| GET | `/api/v1/projects/me` | Authenticated | List the user's projects |
| GET | `/api/v1/projects/{projectId}` | Authenticated | Get a project, including `githubRepositoryOwner` and `githubRepositoryName` when a GitHub repository is linked (both are `null` otherwise) |
| PUT | `/api/v1/projects/{projectId}` | Authenticated | Update a project (`multipart/form-data`) |
| POST | `/api/v1/projects/{projectId}/profile-picture` | Authenticated | Upload a project profile picture (`multipart/form-data`) |
| DELETE | `/api/v1/projects/{projectId}/profile-picture` | Authenticated | Remove a project profile picture |
| DELETE | `/api/v1/projects/{projectId}` | Authenticated | Delete a project |
| GET | `/api/v1/projects/{projectId}/members` | Authenticated | List project members |
| POST | `/api/v1/projects/{projectId}/members` | Authenticated | Add a project member |
| DELETE | `/api/v1/projects/{projectId}/members/{memberUserId}` | Authenticated | Remove a project member |
| POST | `/api/v1/projects/{projectId}/milestones` | Project admin or project manager | Create a milestone |
| GET | `/api/v1/projects/{projectId}/milestones` | Project member or project leader | List milestones |
| PATCH | `/api/v1/milestones/{milestoneId}` | Project admin or project manager | Update a milestone |
| DELETE | `/api/v1/milestones/{milestoneId}` | Project admin or project manager | Delete a milestone |
| POST | `/api/v1/projects/{projectId}/releases` | Authenticated | Create a release |
| GET | `/api/v1/projects/{projectId}/releases` | Authenticated | List releases |
| PATCH | `/api/v1/releases/{releaseId}` | Authenticated | Update a release |
| DELETE | `/api/v1/releases/{releaseId}` | Authenticated | Delete a release |
| POST | `/api/v1/releases/{releaseId}/issues` | Authenticated | Attach issues to a release |
| POST | `/api/v1/releases/{releaseId}/file` | Authenticated | Upload a release file |

### Issues, assignments, and links

| Method | URL | Access | Notes |
|---|---|---|---|
| POST | `/api/v1/projects/{projectId}/issues` | Authenticated | Create an issue |
| GET | `/api/v1/projects/{projectId}/issues` | Authenticated | List project issues |
| GET | `/api/v1/issues/{issueId}` | Authenticated | Get an issue |
| PATCH | `/api/v1/issues/{issueId}` | Authenticated | Update an issue |
| PATCH | `/api/v1/issues/{issueId}/status` | Project admin or project manager | Set the issue status to any `IssueStatus` value |
| DELETE | `/api/v1/issues/{issueId}` | Authenticated | Delete an issue |
| POST | `/api/v1/issues/{issueId}/resolve` | Authenticated | Resolve an issue |
| GET | `/api/v1/issues/{issueId}/history` | Authenticated | View issue history |
| POST | `/api/v1/issues/{issueId}/review/approve` | Authenticated | Approve an issue review |
| POST | `/api/v1/issues/{issueId}/review/reject` | Authenticated | Reject an issue review |
| POST | `/api/v1/issues/{issueId}/labels` | Authenticated | Add an issue label |
| GET | `/api/v1/issues/{issueId}/labels` | Authenticated | List labels |
| DELETE | `/api/v1/issues/{issueId}/labels/{labelId}` | Authenticated | Remove an issue label |
| GET | `/api/v1/issues/me` | Authenticated | List issues assigned to the current user |
| PUT | `/api/v1/issues/{issueId}/github-issue` | Authenticated | Link an issue to a GitHub issue |
| DELETE | `/api/v1/issues/{issueId}/github-issue` | Authenticated | Remove the GitHub issue link |
| POST | `/api/v1/projects/{projectId}/issue` | Authenticated | Singular alias for issue creation |
| POST | `/api/v1/issues/{issueId}/assign` | Authenticated | Assign an issue directly |

| POST | `/api/v1/issues/{issueId}/request` | Authenticated | Request issue assignment |
| GET | `/api/v1/issues/{issueId}/request` | Authenticated | Get your assignment request |
| DELETE | `/api/v1/issues/{issueId}/request` | Authenticated | Cancel your assignment request |
| GET | `/api/v1/projects/{projectId}/issue-requests` | Authenticated | List assignment requests |
| POST | `/api/v1/issues/{issueId}/issue-requests` | Authenticated | Decide on a request |
| DELETE | `/api/v1/projects/{projectId}/issue-requests?requestId={requestId}` | Authenticated | Reject a request |
| POST | `/api/v1/issues/{issueId}/links` | Authenticated | Link two issues |
| GET | `/api/v1/issues/{issueId}/links` | Authenticated | List issue links |
| DELETE | `/api/v1/issue-links/{linkId}` | Authenticated | Remove an issue link |
| GET | `/api/v1/projects/{projectId}/dependency-graph` | Authenticated | Get project issues and their links |
| POST | `/api/v1/issues/{issueId}/attachments` | Authenticated | Upload attached files |
| GET | `/api/v1/issues/{issueId}/attachments` | Authenticated | List issue attachments |
| DELETE | `/api/v1/attachments/{attachmentId}` | Authenticated | Delete an attachment |
| GET | `/api/v1/attachments/{attachmentId}/download` | Authenticated | Download an attachment |

Project admins can perform all project-manager actions while retaining their existing admin-only actions. Issue creation is allowed for the project's Admin, Project Manager, Team Lead, or a Reporter. Sprint creation is allowed for the Admin, assigned Project Manager, or Team Lead. Project Managers, Admins, and Team Leads can assign issues to contributors; only the Project Manager or Admin can assign a sprint to a contributor. Contributors can request sprint assignment, which the Project Manager or Admin can approve.

Project admins and project managers can set issue and sprint statuses directly through the status endpoints. Existing contributor issue-resolution and assigned-contributor sprint start/complete endpoints keep their current permissions.

### Sprint, meeting, poll, notifications, and chat

| Method | URL | Access | Notes |
|---|---|---|---|
| PATCH | `/api/v1/sprints/{sprintId}/status` | Project admin or project manager | Set the sprint status to any `SprintStatus` value |
| POST | `/api/v1/projects/{projectId}/polls` | Authenticated | Create a poll |
| GET | `/api/v1/projects/{projectId}/polls` | Authenticated | List project polls |
| GET | `/api/v1/polls/{pollId}` | Authenticated | Get a poll |
| PUT | `/api/v1/polls/{pollId}/votes` | Authenticated | Cast a vote |
| POST | `/api/v1/polls/{pollId}/close` | Authenticated | Close a poll |
| POST | `/api/v1/projects/{projectId}/meetings` | Authenticated | Schedule a meeting |
| GET | `/api/v1/meetings` | Authenticated | List your meetings |
| GET | `/api/v1/projects/{projectId}/meetings` | Authenticated | List project meetings |
| PATCH | `/api/v1/meetings/{meetingId}` | Authenticated | Update a meeting |
| DELETE | `/api/v1/meetings/{meetingId}` | Authenticated | Cancel a meeting |
| GET | `/api/v1/projects/{projectId}/chat/messages?page=0&size=50` | Authenticated | Fetch chat messages; `size` is 1–100 |
| GET | `/api/v1/projects/{projectId}/chat/messages/search?q=term&page=0&size=20` | Authenticated | Search project chat messages |
| GET | `/api/v1/projects/{projectId}/chat/messages/around/{messageId}?before=25&after=25` | Authenticated | Fetch a message window; each count is 0–50 |
| PATCH | `/api/v1/projects/{projectId}/chat/messages/{messageId}` | Authenticated | Edit a chat message |
| DELETE | `/api/v1/projects/{projectId}/chat/messages/{messageId}` | Authenticated | Delete a chat message |
| POST | `/api/v1/projects/{projectId}/chat/attachments` | Authenticated | Upload multipart `file` |
| GET | `/api/v1/projects/{projectId}/chat/attachments?page=0` | Authenticated | List project chat attachments |
| GET | `/api/v1/projects/{projectId}/chat/attachments/{attachmentId}/download` | Authenticated | Download a chat attachment |
| GET | `/api/v1/projects/{projectId}/chat/mute` | Authenticated | Get your project chat mute state |
| PUT | `/api/v1/projects/{projectId}/chat/mute` | Authenticated | Update your project chat mute state |
| DELETE | `/api/v1/projects/{projectId}/chat/mute` | Authenticated | Clear your project chat mute state |
| POST | `/app/projects/{projectId}/chat` | Authenticated | WebSocket destination for chat messages |
| SUBSCRIBE | `/app/projects/{projectId}/activity` | Authenticated | Receive the latest 10 issue, milestone, and poll-creation activity events once |
| SUBSCRIBE | `/topic/projects/{projectId}/activity` | Authenticated | Receive the refreshed latest 10 issue, milestone, and poll-creation events |
| GET | `/api/v1/notifications` | Authenticated | List notifications |
| POST | `/api/v1/notifications/{id}/read` | Authenticated | Mark a notification as read |
| POST | `/api/v1/notifications/read-all` | Authenticated | Mark all notifications as read |
| GET | `/api/v1/notifications/unread-count` | Authenticated | Get unread count |
| GET | `/api/v1/notifications/preferences` | Authenticated | Get notification preferences |
| PUT | `/api/v1/notifications/preferences` | Authenticated | Update notification preferences |
| DELETE | `/api/v1/users/workspace/leave` | Authenticated | Leave the current workspace |

### Users, workspace lifecycle, comments, documents, and reporting

| Method | URL | Access | Notes |
|---|---|---|---|
| GET | `/api/v1/users/me` | Authenticated | Get the current user's profile |
| PATCH | `/api/v1/users/me` | Authenticated | Update the current user's profile |
| POST | `/api/v1/users/me/profile-picture` | Authenticated | Upload a profile picture (`multipart/form-data`) |
| DELETE | `/api/v1/users/me/profile-picture` | Authenticated | Remove the current user's profile picture |
| DELETE | `/api/v1/users/me` | Authenticated | Delete the current user's account |
| DELETE | `/api/v1/workspaces/{workspaceId}` | Authenticated | Delete a workspace |
| POST | `/api/v1/workspaces/{workspaceId}/restore` | Authenticated | Restore a deleted workspace |
| PATCH | `/api/v2/workspaces/me` | Authenticated | Update the current workspace using the v2 API |
| DELETE | `/api/v2/workspaces/me` | Authenticated | Delete the current workspace using the v2 API |
| GET | `/api/v2/workspaces/me/members` | Authenticated | List current workspace members using the v2 API |
| POST | `/api/v1/issues/{issueId}/comments` | Authenticated | Add an issue comment; accepts optional `mentionedUserIds` |
| GET | `/api/v1/issues/{issueId}/comments` | Authenticated | List issue comments |
| PATCH | `/api/v1/issues/{issueId}/comments/{commentId}` | Authenticated | Edit an issue comment |
| DELETE | `/api/v1/issues/{issueId}/comments/{commentId}` | Authenticated | Delete an issue comment |
| POST | `/api/v1/sprints/{sprintId}/comments` | Authenticated | Add a sprint comment; accepts optional `mentionedUserIds` |
| GET | `/api/v1/sprints/{sprintId}/comments` | Authenticated | List sprint comments |
| PATCH | `/api/v1/sprints/{sprintId}/comments/{commentId}` | Authenticated | Edit a sprint comment |
| DELETE | `/api/v1/sprints/{sprintId}/comments/{commentId}` | Authenticated | Delete a sprint comment |
| GET | `/api/v1/projects/{projectId}/documents` | Authenticated | List project documents |
| POST | `/api/v1/projects/{projectId}/documents` | Authenticated | Create a project document |
| GET | `/api/v1/documents/{documentId}` | Authenticated | Get a document |
| PATCH | `/api/v1/documents/{documentId}` | Authenticated | Update a document |
| DELETE | `/api/v1/documents/{documentId}` | Authenticated | Delete a document |
| GET | `/api/v1/projects/{projectId}/presence` | Authenticated | Get current project presence |
| GET | `/api/v1/projects/{projectId}/activity` | Authenticated | Get the latest 10 issue, milestone, and poll-creation events |
| GET | `/api/v1/projects/{projectId}/events` | Authenticated | List project events |
| GET | `/api/v1/projects/{projectId}/summary` | Authenticated | Get project summary |
| GET | `/api/v1/projects/{projectId}/summary/pdf` | Authenticated | Download project summary as PDF |
| GET | `/api/v1/projects/{projectId}/board` | Authenticated | Get the project issue board |
| GET | `/api/v1/projects/{projectId}/calendar` | Authenticated | Get project calendar data |
| GET | `/api/v1/projects/{projectId}/timeline` | Authenticated | Get project timeline data |
| GET | `/api/v1/users/{userId}/record` | Authenticated | Get an employee record |
| GET | `/api/v1/users/{userId}/record/issues` | Authenticated | Get issue history for an employee record |
| GET | `/api/v1/users/{userId}/record/sprints` | Authenticated | Get sprint history for an employee record |
| GET | `/api/v1/users/{userId}/record/activity` | Authenticated | Get activity for an employee record |

### Budget, briefing, replay, and integrations

| Method | URL | Access | Notes |
|---|---|---|---|
| PUT | `/api/v1/projects/{projectId}/budget` | Authenticated | Set the project budget |
| GET | `/api/v1/projects/{projectId}/budget` | Authenticated | Get the project budget |
| GET | `/api/v1/projects/{projectId}/budget/report` | Authenticated | Get the project cost report |
| POST | `/api/v1/projects/{projectId}/expenses` | Authenticated | Add a project expense |
| GET | `/api/v1/projects/{projectId}/expenses` | Authenticated | List project expenses |
| PATCH | `/api/v1/expenses/{expenseId}` | Authenticated | Update an expense |
| DELETE | `/api/v1/expenses/{expenseId}` | Authenticated | Delete an expense |
| PUT | `/api/v1/users/{userId}/salary` | Admin | Set salary data for a user |
| GET | `/api/v1/briefing/today` | Authenticated | Get today's daily briefing |
| GET | `/api/v1/briefing?date=YYYY-MM-DD` | Authenticated | Get a briefing for a specific date |
| GET | `/api/v1/briefing/preferences` | Authenticated | Get briefing preferences |
| PUT | `/api/v1/briefing/preferences` | Authenticated | Update briefing preferences |
| GET | `/api/v1/projects/{projectId}/replay/timeline` | Authenticated | Get project replay phases |
| PATCH | `/api/v1/projects/{projectId}/replay/phases/{phaseId}` | Authenticated | Update a replay phase |
| GET | `/api/v1/projects/{projectId}/replay/snapshot?at=2026-10-10T12:00:00Z` | Authenticated | Get a replay snapshot at an optional ISO instant or date |
| POST | `/api/v2/projects/{projectId}/issues` | Authenticated | Create an issue using the v2 API |
| GET | `/api/v1/integrations/google/connect` | Authenticated | Start Google OAuth and return the authorization URL |
| GET | `/api/v1/integrations/google/callback` | OAuth callback | Complete Google OAuth and redirect to the frontend |
| DELETE | `/api/v1/integrations/google/disconnect` | Authenticated | Disconnect the Google account |

The application also supports STOMP over `/ws`. The chat send destination is `/app/projects/{projectId}/chat`; clients receive project chat updates from `/topic/projects/{projectId}/chat`. For project activity, subscribe to `/topic/projects/{projectId}/activity` for refreshed latest-10 lists, and `/app/projects/{projectId}/activity` for the current snapshot. The feed includes issue creation, edits, and completion; milestone creation, edits, and completion; and poll creation. WebSocket connections use the authenticated access token and project subscriptions require project membership. See the poll and notification sections for their topic and queue destinations.

Notifications are generated for issue assignment and review activity, project membership and role changes, comments and mentions, sprint assignment requests and assignments, poll creation, meetings, invites, and chat messages. To mention users in an issue or sprint comment, include their project participant IDs in the optional `mentionedUserIds` array alongside `content` (up to 50 IDs). Notifications are available through the notifications API and the `/user/queue/notifications` WebSocket destination. Email delivery follows each user's notification preferences.

#### Poll API request and response formats

Poll routes require `Authorization: Bearer <access-token>`. Any authenticated project member can create, view, and vote in polls. Only the poll creator, project manager, or project admin can close one.

Create a poll with `POST /api/v1/projects/{projectId}/polls`:

```json
{
  "question": "Which approach should we use?",
  "description": "Optional background",
  "options": ["Approach A", "Approach B"],
  "endsAt": "2026-10-20T12:00:00Z",
  "mode": "SINGLE",
  "issueId": null,
  "sprintId": null,
  "milestoneId": null
}
```

`question` is required (up to 200 characters). `options` must contain 2–20 unique, nonblank values, each up to 300 characters. `description` is optional (up to 400 characters). `endsAt` is an optional future ISO-8601 timestamp; the server rejects votes at or after this deadline. `mode` is `SINGLE` or `MULTIPLE`; if omitted, it defaults to `SINGLE`. At most one of `issueId`, `sprintId`, or `milestoneId` can be provided, and the linked item must belong to the specified project. Creation returns **201 Created**.

Other poll routes:

| Method | Endpoint | Purpose | Request body |
|---|---|---|---|
| GET | `/api/v1/projects/{projectId}/polls` | List polls in the project | None |
| GET | `/api/v1/polls/{pollId}` | Get a poll and its current results | None |
| PUT | `/api/v1/polls/{pollId}/votes` | Cast or replace your vote | One of the vote bodies below |
| POST | `/api/v1/polls/{pollId}/close` | Close a poll and save its decision record | Optional `{"decisionRecord":"The selected approach and rationale"}` |

Single-choice vote body:

```json
{
  "optionId": "<option-uuid>"
}
```

Multiple-choice vote body:

```json
{
  "optionIds": ["<option-uuid-1>", "<option-uuid-2>"]
}
```

Use either `optionId` or `optionIds`, not both. The selected options must belong to that poll. A vote can be changed by sending another vote request while the poll remains open.

Poll endpoints return a `PollResponse` (or a list of them) with this shape:

```json
{
  "id": "<poll-uuid>",
  "projectId": "<project-uuid>",
  "question": "Which approach should we use?",
  "description": "Optional background",
  "status": "OPEN",
  "createdBy": "<user-uuid>",
  "issueId": null,
  "sprintId": null,
  "milestoneId": null,
  "endsAt": "2026-10-20T12:00:00Z",
  "closedAt": null,
  "decisionRecord": null,
  "createdAt": "2026-10-15T09:00:00Z",
  "options": [
    {"id": "<option-uuid-1>", "label": "Approach A", "order": 0, "votes": 2},
    {"id": "<option-uuid-2>", "label": "Approach B", "order": 1, "votes": 1}
  ],
  "myVote": "<option-uuid-1>",
  "myVotes": ["<option-uuid-1>"]
}
```

`myVote` is the current user's first selected option (or `null` when they have not voted); `myVotes` contains all of their selected options. A background scheduler checks for expired polls every 10 seconds and closes them. `decisionRecord` is optional and limited to 4,000 characters.

Polls also publish live updates over the authenticated STOMP connection at `/ws`. Subscribe to `/topic/polls/{pollId}/results` to receive events with `type` (`POLL_CREATED`, `POLL_UPDATED`, or `POLL_CLOSED`) and the updated `poll` response. `POLL_CLOSED` includes final option vote totals. Continue using REST to create polls, vote, and close; successful changes and deadline closures are broadcast after the database transaction commits. Use REST list/get endpoints for initial state, then apply WebSocket events for live updates.

Protected routes use `Authorization: Bearer <access-token>`, except refresh and logout, which use the `refreshToken` HTTP-only cookie set during login. 

### Project dashboard data

The project page can use the existing endpoints below; no dashboard-specific route is required:

| Endpoint | Dashboard data |
|---|---|
| `GET /api/v1/projects/{projectId}` | Project details and progress, plus `projectManager` and `teamLead` summaries (`id`, `name`, `role`) when assigned |
| `GET /api/v1/projects/{projectId}/members` | Project member names, emails, roles, and IDs |
| `GET /api/v1/projects/{projectId}/board` | Issue columns and card data, including assignee (`id`, `name`), labels (`id`, `name`, `color`), `assignedToCurrentUser`, and top-level `myTasksCount` |
| `GET /api/v1/projects/{projectId}/summary` | Issue and sprint totals, status counts, overdue counts, and estimated-hour summaries; use issue counts for task overview charts |
| `GET /api/v1/projects/{projectId}/sprints` | Sprint details including status and assigned contributor name and ID |
| `GET /api/v1/projects/{projectId}/milestones` | Milestone due date, progress, and completion state |
| `GET /api/v1/projects/{projectId}/activity` | Latest 10 issue, milestone, and poll-creation events with actor and message details |
| `GET /api/v1/projects/{projectId}/polls` | Polls ordered newest first, with option vote totals; the first entry is the latest poll |

The board endpoint reports issue status and priority but does not track a separate issue completion percentage or link issues to sprints. Render task progress from the project summary status counts, and render sprint counts from sprint statuses. The project manager and team lead summaries contain names but not profile image URLs.

Each board response now includes a project-wide `myTasksCount`; each issue card includes its first assignee (or `null`), all labels, and whether the authenticated user is assigned:

```json
{
  "columns": [
    {
      "status": "OPEN",
      "allowedTransitions": ["IN_PROGRESS"],
      "issues": [
        {
          "id": "<issue-uuid>",
          "issueNumber": 205,
          "title": "Setup analytics tracking",
          "priority": "MEDIUM",
          "dueDate": "2026-10-22",
          "assignee": {"id": "<user-uuid>", "name": "Alex Morgan"},
          "labels": [{"id": "<label-uuid>", "name": "Analytics", "color": "#21a5d8"}],
          "assignedToCurrentUser": true
        }
      ]
    }
  ],
  "myTasksCount": 6
}
```

Project responses include `projectManager` and `teamLead` as `null` when unassigned, or as `{ "id", "name", "role" }` when assigned. Sprint responses add `assignedContributorName`, which is `null` when no contributor is assigned.

`GET /api/v1/check` returns:

```json
{
  "status": "ok"
}
```

This endpoint can be used to check whether the server is running.

## Invites

Admins can create invites using:

```http
POST /api/v1/admin/invites
```

The admin must provide `email` and `purpose`. For workspace and project invites, include an allowed `role`; project invites also include `projectId`. `expiresAt` is optional; if omitted, the invite expires after the default validity period.

Supported `purpose` values are `WORKSPACE_INVITE`, `PROJECT_INVITE`, and `OWNERSHIP_TRANSFER`.

Allowed invite roles are:

- `PROJECT_MANAGER`
- `TEAM_LEAD`
- `CONTRIBUTOR`
- `REPORTER`

`ADMIN` cannot be assigned through an invite.

If provided, `expiresAt` must be an ISO-8601 timestamp in the future.

Example:

```json
{
  "email": "collaborator@example.com",
  "purpose": "WORKSPACE_INVITE",
  "role": "PROJECT_MANAGER",
  "expiresAt": "2030-05-01T12:00:00Z"
}
```

The specified expiration date is used for the invite.

Authenticated users can manage their pending invites using:

```http
GET  /api/v1/invites/me
POST /api/v1/invites/accept
POST /api/v1/invites/reject
```

Accepting or rejecting an invite requires:

```json
{
  "inviteId": "<invite UUID>"
}
```

Admins can revoke a pending invite using:

```http
POST /api/v1/admin/invites/revoke
```

This endpoint requires an admin access token:

```http
Authorization: Bearer <admin-access-token>
```

All routes under `/api/v1/admin/**` require the `ADMIN` role.

Invite responses include:

- `createdAt`
- `acceptedAt` — `null` until the invite is accepted
- `expiresAt`

Expired invites are marked as `EXPIRED` when the recipient loads their pending invites.

> **Default expiration:** New invites without `expiresAt` use the default invite validity period. Older invites without an expiration are also treated as expiring after seven days.

## Sprints

Sprint endpoints require authentication and use the `/api/v1` prefix.

When a sprint is linked to a milestone, its end date must be strictly earlier than the milestone due date. Creating or updating a sprint with an end date on or after that due date returns **400 Bad Request**. Moving a milestone due date to the same day as, or earlier than, any linked sprint's end date also returns **400 Bad Request** with `Sprint due date needs to be before the milestone date`.

A milestone is automatically marked complete when it has at least one linked sprint and every linked sprint is `COMPLETED`. Its response includes `completed` and `completedAt`; reopening, deleting, or moving a sprint out of the milestone clears its completion state if the condition no longer holds.

The assigned project manager or team lead can create a sprint. Only the project manager can assign a sprint to a project contributor.

Create a sprint:

```http
POST /api/v1/projects/{projectId}/sprints
```

```json
{
  "name": "Sprint 1",
  "goal": "Deliver the initial feature set",
  "startDate": "2026-10-05",
  "endDate": "2026-10-19"
}
```

`name`, `goal`, `startDate`, and `endDate` are required. Dates use the `YYYY-MM-DD` format. New sprints start with status `PLANNED`; use `POST /api/v1/sprints/{id}/start` to move one to `ACTIVE`, then `POST /api/v1/sprints/{id}/complete` to move it to `COMPLETED`.

`PATCH /api/v1/sprints/{id}` accepts any subset of `name`, `goal`, `startDate`, `endDate`, and `milestoneId`. `DELETE /api/v1/sprints/{id}` deletes the sprint.

---

## Signup

```http
POST /api/v1/auth/signup
```

Request body:

```json
{
  "name": "Test User",
  "email": "test@example.com",
  "password": "Secure1!"
}
```

The name may contain letters and spaces. The password must be 6–20 characters and contain at least one uppercase letter, one lowercase letter, one number, and one symbol.

If the email is already registered, the API returns an error.

---

## Login

```http
POST /api/v1/auth/login
```

Request body:

```json
{
  "email": "test@example.com",
  "password": "Secure1!"
}
```

If the credentials are correct, the API returns the user details and an access token. The refresh token is set as an HTTP-only cookie and is not included in the JSON response.

Example:

```json
{
  "user": {
    "id": "...",
    "name": "Test User",
    "email": "test@example.com",
    "role": "ADMIN"
  },
  "accessToken": "eyJ..."
}
```

## How JWT authentication works

The authentication flow works as follows:

1. The user logs in with their email and password.
2. The server validates the credentials.
3. The server returns an access token and sets the refresh token as an HTTP-only cookie.
4. The frontend sends the access token with requests to protected endpoints.

The access token is sent in the `Authorization` header:

```http
Authorization: Bearer <access-token>
```

5. `JwtAuthFilter` reads the token from the request.
6. `JwtUtil` validates the token.
7. If the token is valid, the authenticated user's information is placed into the Spring Security context.
8. `SecurityConfig` checks whether the authenticated user is allowed to access the requested endpoint.

### Access control

- `/api/v1/auth/**` is publicly accessible because users need to access authentication endpoints before they have an access token
- `/api/v1/check` is publicly accessible
- `/api/v1/admin/**` requires the `ADMIN` role
- All other endpoints require a valid access token

### Access token and refresh token

The **access token** is used to authenticate normal API requests.

When the access token expires, the frontend can send the **refresh token** to:

```http
POST /api/v1/auth/refresh
```

with the refresh-token cookie automatically included in the request. The endpoint does not accept a JSON refresh-token body.

If the refresh token is valid, the server issues a new access token.

The user does not need to log in again every time the access token expires, as long as the refresh token is still valid.

Logout invalidates the refresh token so that it cannot be used to obtain new access tokens.

> The exact expiration times for the access token and refresh token should be kept in the application's configuration. Do not assume the access token is valid for 24 hours unless that is the configured value.

---

## Forgot Password

The forgot-password flow works in three steps.

### 1. Request an OTP

```http
POST /api/v1/auth/forgot-password
```

The user provides their email address.

The server:

1. Checks whether the account exists.
2. Generates a 6-digit OTP.
3. Saves the OTP.
4. Sends the OTP to the user's email.

### 2. Verify the OTP

```http
POST /api/v1/auth/verify-otp
```

Request body:

```json
{
  "email": "test@example.com",
  "code": "123456"
}
```

The server checks whether the OTP:

- belongs to the specified email
- has the correct purpose
- has not expired
- has not already been used
- matches the provided code

### 3. Reset the password

```http
POST /api/v1/auth/reset-password
```

Request body:

```json
{
  "email": "test@example.com",
  "code": "123456",
  "newPassword": "newPassword123"
}
```

The server validates the OTP again before allowing the password to be changed.

After successful validation, the new password is BCrypt-hashed and saved.

The OTP is then marked as used.

## OTP Rules

- OTPs contain 6 digits
- OTPs are valid for 10 minutes
- Each OTP has a purpose, such as `SIGNUP` or `RESET_PASSWORD`
- An OTP can only be used for its intended purpose
- When a new OTP is generated for the same email and purpose, the previous OTP is invalidated
- Used OTPs cannot be reused
- Expired OTPs cannot be used

## Security Notes

- Passwords are never stored as plain text
- Access tokens must be sent using the `Authorization: Bearer <token>` header
- Admin endpoints require the `ADMIN` role
- Refresh tokens are required to obtain new access tokens after access-token expiration
- Refresh tokens are invalidated during logout
- OTPs expire after their configured validity period
- OTPs are single-use
