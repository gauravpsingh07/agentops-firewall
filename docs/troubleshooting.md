# Troubleshooting

Companion to the short [Troubleshooting](../README.md#troubleshooting)
table in the root README. Every entry below was discovered while
bringing this stack up on a real machine — the symptom, the cause,
and the actual fix that worked. Reach for this file before opening
an issue.

---

## 1. Image layers extract as zero-byte files on Docker Desktop

**Symptom.** Containers fail immediately with messages like:

```
exec /__cacert_entrypoint.sh: exec format error
exec /usr/local/bin/docker-entrypoint.sh: exec format error
```

Inspect the image and the entrypoint script is present but empty:

```powershell
docker run --rm --entrypoint=/bin/sh rabbitmq:3-management `
  -c "wc -c /usr/local/bin/docker-entrypoint.sh"
# 0 /usr/local/bin/docker-entrypoint.sh
```

**Cause.** Docker Desktop's optional **containerd image store** has a
layer-extraction regression on some 4.6x builds that consistently
writes specific shell-script layers as zero bytes. `apache/kafka:3.9.0`
is a particularly bad case (in one repro, 243 of 243 files in
`/etc/kafka` and `/opt/kafka` extracted empty). The classic dockerd
image store does not have this bug.

**Fix.**

1. Docker Desktop → **Settings** → **General**.
2. Uncheck **"Use containerd for pulling and storing images"**.
3. Click **Apply & Restart**.
4. Wipe the corrupted images and re-pull cleanly:

   ```powershell
   docker compose down
   docker rmi <affected_image>
   docker compose pull
   docker compose up -d
   ```

---

## 2. Kafka refuses to start: `unable to find user appuser`

**Symptom.** Container creation errors out before Kafka logs anything:

```
Error response from daemon: unable to find user appuser:
  no matching entries in passwd file
```

**Cause.** The `apache/kafka:3.9.0` Dockerfile declares `USER appuser`,
but the user-creation step in the build doesn't always land an entry
in `/etc/passwd` on Docker Desktop / WSL2 image stores. containerd
then refuses to start the container because the username can't be
resolved.

**Fix.** Pin the UID/GID numerically in `docker-compose.yml` so the
runtime never consults `/etc/passwd`:

```yaml
kafka:
  image: apache/kafka:3.9.0
  user: "1000:1000"
```

This repo ultimately switched to `confluentinc/cp-kafka:7.7.1`
(see [commit `67d7d64`](../commits/67d7d64)) for unrelated
layer-extraction reasons (issue 1 above). `cp-kafka` ships an
`appuser` that actually exists in `/etc/passwd`, so the override is
not needed there.

---

## 3. Host-run backend can't reach Kafka in Docker

**Symptom.** A Spring Boot backend launched on the Windows host with
`./mvnw spring-boot:run` fails Kafka health-checks or times out on
publish. `telnet localhost 9092` succeeds, so the port mapping is
live — but actual Kafka operations hang.

**Cause.** A single-listener Kafka container advertises one address
(by default `kafka:9092`) to every client that connects. The TCP
socket succeeds on `localhost:9092`, but Kafka then tells the client
*"my real address is `kafka:9092` — reconnect there"*. A JVM running
on the host can't resolve the name `kafka`, so the second hop fails.

**Fix.** Dual-listener pattern: one internal listener for
container-to-container traffic, a second external listener for the
host.

```yaml
kafka:
  environment:
    KAFKA_LISTENERS: >-
      PLAINTEXT://0.0.0.0:29092,
      PLAINTEXT_HOST://0.0.0.0:9092,
      CONTROLLER://0.0.0.0:9093
    KAFKA_ADVERTISED_LISTENERS: >-
      PLAINTEXT://kafka:29092,
      PLAINTEXT_HOST://localhost:9092
    KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: >-
      CONTROLLER:PLAINTEXT,
      PLAINTEXT:PLAINTEXT,
      PLAINTEXT_HOST:PLAINTEXT
    KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
```

Container clients use `kafka:29092`. Host clients (including
`./mvnw spring-boot:run`) use `localhost:9092`. The compose-managed
`backend` service is configured with
`SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:29092` so its traffic stays on
the internal listener.

---

## 4. `/actuator/health` returns DOWN with no detail

**Symptom.**

```
$ curl http://localhost:8080/actuator/health
{"status":"DOWN"}
```

— but `docker compose ps` shows every container `(healthy)`.

**Cause.** This project sets
`management.endpoint.health.show-details: never` (the
production-safe default), so the response hides which component
failed. The dependency that fails most often during first bring-up
is RabbitMQ:

```
org.springframework.amqp.AmqpAuthenticationException:
  com.rabbitmq.client.AuthenticationFailureException:
    ACCESS_REFUSED - Login was refused using authentication
    mechanism PLAIN.
```

The RabbitMQ container is initialised with the custom user
`agentops:local_dev_only` (from `RABBITMQ_DEFAULT_USER` /
`RABBITMQ_DEFAULT_PASS`), but Spring's stock fallback is
`guest:guest`. RabbitMQ's built-in `guest` user is additionally
restricted to loopback connections, so a host-run backend hitting
the broker through the port mapping is always rejected.

**Fix.** The application's `application.yml` defaults now match the
compose env vars (`agentops:local_dev_only` — both are CLAUDE.md
approved local-dev placeholders), so a fresh checkout
"just works". For a temporary local override, expose details on
the actuator endpoint:

```powershell
$env:MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS = "always"
./mvnw spring-boot:run
```

— then `curl http://localhost:8080/actuator/health` returns a
nested JSON body naming every component and the one that's DOWN.

---

## 5. Login succeeds in curl but the dashboard never loads in a browser

**Symptom.** `POST /api/auth/login` returns a valid JWT when called
via `curl`, but in the browser the login form submits and just sits
there. Playwright e2e tests time out on
`page.waitForURL(/\/dashboard/)`.

**Cause.** The backend is responding `200 OK` with the token in the
body, but **without** an `Access-Control-Allow-Origin` header.
Browser security policy refuses to expose the response body to
JavaScript, so `AuthService.login().subscribe(...)` never sees the
token, and the router never navigates.

Reproduce by sending the same request curl normally would, but
adding the cross-origin headers a real browser sends:

```powershell
curl.exe -i -X POST `
  -H "Origin: http://localhost:4200" `
  -H "Content-Type: application/json" `
  -d "{\"username\":\"admin\",\"password\":\"admin123\"}" `
  http://localhost:8080/api/auth/login
```

If `Access-Control-Allow-Origin: http://localhost:4200` is missing
from the response headers, CORS is misconfigured server-side.

**Fix.** Spring Security needs a `CorsConfigurationSource` bean in
the context. `.cors(cors -> {})` in the filter chain enables the
CORS filter, but with no source bean the filter accepts the request
and adds no headers. This repo wires the bean in
[`SecurityConfig#corsConfigurationSource`](../backend/src/main/java/com/agentops/firewall/security/SecurityConfig.java),
keyed on the `agentops.security.cors.allowed-origins` property so
each deployment can lock the list down. Override it for additional
origins:

```bash
export AGENTOPS_SECURITY_CORS_ALLOWED_ORIGINS=\
http://localhost:4200,https://demo.example.com
```

---

## 6. Out of disk space — Docker corrupts, git can't write, Windows misbehaves

**Symptom.** `docker compose up` fails with `no space left on device`,
or `git commit` errors with `error: file write error (No space left
on device)`. `fsutil volume diskfree C:` (or `dir C:\`) shows under
2 GB free.

**Cause.** Docker Desktop's WSL VM disk image (`ext4.vhdx`,
typically at `%LOCALAPPDATA%\Docker\wsl\`) grows over time as you
pull images and create containers, and **does not release space
back to Windows when you delete those images**. The VM image alone
can be tens of GB even when `docker images` reports very little.

**Fix.** Walk the list from least to most aggressive — stop as soon
as you have enough headroom (10 GB free is a good target):

1. Empty Recycle Bin.
2. Run **Disk Cleanup** as administrator, including
   *"Previous Windows installations"*.
3. Reclaim Docker disk without touching the VM:

   ```powershell
   docker system prune -a --volumes
   ```

4. *Last resort.* Nuke the WSL VM. This destroys **every** Docker
   image, container, and named volume on the machine — including
   ones from unrelated projects. Anything in a named volume that
   isn't backed up is lost.

   ```powershell
   # Quit Docker Desktop from the system tray first.
   wsl --shutdown
   wsl --unregister docker-desktop
   # Start Docker Desktop — it rebuilds the VM from scratch.
   ```

   After this, every Docker project on your machine needs
   `docker compose up` again to re-pull images.

---

## 7. PowerShell's `curl` keeps prompting before each request

**Symptom.** Hitting an API from PowerShell prints:

```
Security Warning: Script Execution Risk
Invoke-WebRequest parses the content of the web page. Script code
in the web page might be run when the page is parsed.
  [Y] Yes  [A] Yes to All  [N] No  ...
```

**Cause.** PowerShell's `curl` is an alias for `Invoke-WebRequest`,
which is an HTML parser-aware client, not the curl most Unix muscle
memory expects.

**Fix.** Use the real curl binary that ships with Windows 10+:

```powershell
curl.exe http://localhost:8080/actuator/health
```

Or, for a typed JSON object instead of a raw response, use
`Invoke-RestMethod`:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

---

## 8. After `wsl --unregister`, every other Docker project is empty

**Symptom.** A different project that "was running yesterday" no
longer has any images or containers after a Docker recovery on a
sibling project.

**Cause.** `wsl --unregister docker-desktop` wipes the entire shared
Docker WSL VM — images, containers, and named volumes for **every**
project on the machine, not just the one you were debugging.

**Fix.** For each affected project, change into its directory and
re-run its bring-up:

```powershell
cd <other-project>
docker compose up -d
```

Compose re-pulls missing images and rebuilds local ones. The
project is back in seconds, **unless** it relied on data in a Docker
volume (Postgres data dir, Redis dump, uploaded files in a dev
container) — in which case that data is gone and has to be
restored from a backup or re-seeded from fixtures.

For future Docker corruption, prefer the less destructive
options first (`docker system prune -a --volumes` reclaims most
disk without nuking the VM). Reserve `wsl --unregister` for the
case where you genuinely need a fresh VM and accept the
cross-project blast radius.
