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

The backend routes are grouped under `/api/v1`, with additional project-scoped endpoints for sprints, issues, meetings, and chat.

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
| POST | `/projects/{projectId}/sprints` | Authenticated | `name, startDate, endDate`, optional `goal` |
| GET | `/projects/{projectId}/sprints` | Authenticated | Lists a project's sprints |
| GET | `/sprints/{id}` | Authenticated | Gets a sprint |
| PATCH | `/sprints/{id}` | Authenticated | Optional `name, goal, startDate, endDate` fields |
| POST | `/sprints/{id}/start` | Authenticated | Starts a planned sprint |
| POST | `/sprints/{id}/complete` | Authenticated | Completes an active sprint |
| DELETE | `/sprints/{id}` | Authenticated | Deletes a sprint |

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

Each member connects their own GitHub account to associate a GitHub login with their FrietSync user. The member list includes users without a connection with zero activity. The repository must be readable by the account performing the contribution request. GitHub activity is fetched live and currently paginates up to the most recent 1,000 commits and 1,000 pull requests.

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
| GET | `/api/v1/projects/{projectId}` | Authenticated | Get a project |
| PUT | `/api/v1/projects/{projectId}` | Authenticated | Update a project |
| DELETE | `/api/v1/projects/{projectId}` | Authenticated | Delete a project |
| GET | `/api/v1/projects/{projectId}/members` | Authenticated | List project members |
| POST | `/api/v1/projects/{projectId}/members` | Authenticated | Add a project member |
| DELETE | `/api/v1/projects/{projectId}/members/{memberUserId}` | Authenticated | Remove a project member |
| POST | `/api/v1/projects/{projectId}/milestones` | Authenticated | Create a milestone |
| GET | `/api/v1/projects/{projectId}/milestones` | Authenticated | List milestones |
| PATCH | `/api/v1/milestones/{milestoneId}` | Authenticated | Update a milestone |
| DELETE | `/api/v1/milestones/{milestoneId}` | Authenticated | Delete a milestone |
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
| DELETE | `/api/v1/issues/{issueId}` | Authenticated | Delete an issue |
| POST | `/api/v1/issues/{issueId}/resolve` | Authenticated | Resolve an issue |
| GET | `/api/v1/issues/{issueId}/history` | Authenticated | View issue history |
| POST | `/api/v1/issues/{issueId}/review/approve` | Authenticated | Approve an issue review |
| POST | `/api/v1/issues/{issueId}/review/reject` | Authenticated | Reject an issue review |
| POST | `/api/v1/issues/{issueId}/labels` | Authenticated | Add an issue label |
| GET | `/api/v1/issues/{issueId}/labels` | Authenticated | List labels |
| POST | `/api/v1/issues/{issueId}/assign` | Authenticated | Assign an issue directly |

| POST | `/api/v1/issues/{issueId}/request` | Authenticated | Request issue assignment |
| GET | `/api/v1/issues/{issueId}/request` | Authenticated | Get your assignment request |
| DELETE | `/api/v1/issues/{issueId}/request` | Authenticated | Cancel your assignment request |
| GET | `/api/v1/projects/{projectId}/issue-requests` | Authenticated | List assignment requests |
| POST | `/api/v1/issues/{issueId}/issue-requests` | Authenticated | Decide on a request |
| DELETE | `/api/v1/projects/{projectId}/issue-requests?requestId={requestId}` | Authenticated | Reject a request |
| POST | `/api/v1/issues/{issueId}/links` | Authenticated | Create a dependency / link |
| GET | `/api/v1/issues/{issueId}/links` | Authenticated | List issue links |
| DELETE | `/api/v1/issue-links/{linkId}` | Authenticated | Remove an issue link |
| GET | `/api/v1/issues/{issueId}/impact` | Authenticated | Get issue impact |
| GET | `/api/v1/issues/{issueId}/dependencies` | Authenticated | Get issue dependencies |
| GET | `/api/v1/projects/{projectId}/dependency-graph` | Authenticated | Get dependency graph |
| POST | `/api/v1/issues/{issueId}/attachments` | Authenticated | Upload attached files |
| GET | `/api/v1/issues/{issueId}/attachments` | Authenticated | List issue attachments |
| DELETE | `/api/v1/attachments/{attachmentId}` | Authenticated | Delete an attachment |
| GET | `/api/v1/attachments/{attachmentId}/download` | Authenticated | Download an attachment |

Issue creation is allowed for the project's Project Manager, Team Lead, or a Reporter. Sprint creation is allowed for the assigned Project Manager or Team Lead. Project Managers and Team Leads (and Admins) can assign issues to contributors; only the Project Manager can assign a sprint to a contributor. Contributors can request sprint assignment, which the Project Manager can approve.

### Sprint, meeting, poll, notifications, and chat

| Method | URL | Access | Notes |
|---|---|---|---|
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
| GET | `/api/v1/projects/{projectId}/chat/messages?limit=50` | Authenticated | Fetch recent project chat messages |
| POST | `/app/projects/{projectId}/chat` | Authenticated | WebSocket destination for chat messages |
| GET | `/api/v1/notifications` | Authenticated | List notifications |
| POST | `/api/v1/notifications/{id}/read` | Authenticated | Mark a notification as read |
| POST | `/api/v1/notifications/read-all` | Authenticated | Mark all notifications as read |
| GET | `/api/v1/notifications/unread-count` | Authenticated | Get unread count |
| GET | `/api/v1/notifications/preferences` | Authenticated | Get notification preferences |
| PUT | `/api/v1/notifications/preferences` | Authenticated | Update notification preferences |
| DELETE | `/api/v1/users/workspace/leave` | Authenticated | Leave the current workspace |

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
| `GET /api/v1/projects/{projectId}/milestones` | Milestone due date and `progressPercentage`; the existing service calculates progress from its sprints |
| `GET /api/v1/projects/{projectId}/activity?limit=10` | Recent events with actor name and issue details; `limit` can be 1 through 100 |
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

The admin must provide `email` and an allowed `role`. `expiresAt` is optional; if omitted, the invite expires after the default validity period.

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

Sprint endpoints require authentication and currently use the paths shown in the API table (without the `/api/v1` prefix).

The assigned project manager or team lead can create a sprint. Only the project manager can assign a sprint to a project contributor.

Create a sprint:

```http
POST /projects/{projectId}/sprints
```

```json
{
  "name": "Sprint 1",
  "goal": "Deliver the initial feature set",
  "startDate": "2026-10-05",
  "endDate": "2026-10-19"
}
```

`name`, `startDate`, and `endDate` are required. Dates use the `YYYY-MM-DD` format. New sprints start with status `PLANNED`; use `POST /sprints/{id}/start` to move one to `ACTIVE`, then `POST /sprints/{id}/complete` to move it to `COMPLETED`.

`PATCH /sprints/{id}` accepts any subset of `name`, `goal`, `startDate`, and `endDate`. `DELETE /sprints/{id}` deletes the sprint.

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
  "password": "test123"
}
```

The name must contain letters only. The password must be between 6 and 20 characters and may contain letters, numbers, and symbols.

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
  "password": "test123"
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
