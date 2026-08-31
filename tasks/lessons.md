# Lessons

## 2026-08-29

- A successful TCP connect does not prove that application bytes pass through a
  relay. Tailscale Funnel requires a public TLS connection, including when its
  local target is raw TCP. Verify the SSH banner and handshake, not only the port.
- Use SwiftNIO Transport Services on physical iOS so SSH and SFTP use Apple's
  Network.framework path. Keep the POSIX bootstrap out of both the app and the
  keyboard extension.

## 2026-08-31

- Before starting a large external diff review, verify that the reviewer can
  read the exact artifact location. One complete attempt and one access-fix
  continuation are the limit. If both cannot inspect the full input, stop and
  report the review gate as open instead of starting an unbounded review loop.
- Before a public-path device test, identify the active VPN provider and disable
  its on-demand rule. Turning off the top-level VPN switch can be temporary.
- Do not display raw networking-library errors. Map transport setup failures to
  a short action message while preserving authentication and host-key errors.
- Treat NIO future cancellation as explicit channel ownership. A canceled Swift
  task does not stop `EventLoopFuture.get()`; the cancellation handler must
  close the pending or active channel.
- Finish a network deadline with one locked state transition. A separate
  `didTimeOut` check and timer cancellation leave a race that can return success
  with a channel the timer has already closed.
- A first-use host-key probe must not hold usable credentials. Use a delegate
  that refuses authentication, then open a new pinned connection after trust.
- Keep copied-image host filtering and empty-state instructions aligned. A
  key-authenticated but unpinned host needs a host-key verification message.
- Plain `ssh-keyscan` cannot cross a TLS-terminated relay. Use the first-use
  fingerprint flow for that endpoint and require its certificate to match the
  configured hostname or IP address.
- A disabled Cancel button is not enough when another visible control can call
  the same dismissal path. Gate each user dismissal entry point with the
  operation state that protects the remote write.
- A PTY can echo the command before the server runs it. Do not place a complete
  success marker in the command text. Make the remote shell assemble the marker
  from separate arguments, and require content, byte-count, deletion, and
  absence checks before it prints success.
- Make live-test transport mode explicit. A missing TLS value must stop the test
  configuration instead of silently selecting plain SSH.
- Do not use `path` as a zsh loop or script variable. In zsh, `path` is tied to
  `PATH`; assigning to it can make standard commands unavailable. Use a
  task-specific name such as `doc_path` or `schema_file`.
- Scope release-state assertions to the named build and state. A broad ban on
  `not uploaded` can reject valid templates that list possible states without
  claiming that the current build has that state.
- Prove a credential test harness with a harmless local command before rotating
  a process-memory-only password. If protected storage does not complete, lock
  the account so an unknown active password does not remain.

## 2026-08-28

- Do not present a polling interval as review evidence. For every review, record
  its exact scope, inputs, elapsed time, findings, and reviewer. A targeted SSH
  configuration review does not replace the required full tracked-and-untracked
  repository diff review before archive.
- Before opening a local GUI app for device capture, name the app and the visible
  side effect. If the device source is not available, close the app and ask for
  a user-provided screenshot instead of leaving an unrelated camera preview open.
- Physical keyboard review must include portrait layout with the iPad system edit
  controls visible. Check for overlap, wasted key spacing, redundant actions, and
  access to swipe-down secondary characters before accepting visual polish.

## 2026-08-31

- When the release owner accepts prior hardware evidence and directs the rest
  of the work to a simulator, record a hardware-test waiver instead of a pass.
  Name each device-dependent path that remains unverified and carry that risk
  into the archive and submission evidence.
- Do not move from a narrow artifact check directly into fixes or upload on a
  large dirty candidate. Freeze the exact staged, unstaged, and untracked diff,
  review every line by scope, record reviewer time and findings, then define one
  bounded correction set.

## 2026-08-27

- Do not use external skills or plugins unless the user asks for them. Direct research and local repository tools are acceptable when the user permits research.
- Expand the user's server shorthand before remote work: `hoj` is `houseofjawn`, `ofj` is `officejawn`, and `loj` is `landofjawn`. Use only the named host.
- Verify each store track separately. A Play Console app record does not prove a
  production release. On August 27, 2026, KeyJawn Lite had active internal and
  closed testing, while open testing and production were inactive.
