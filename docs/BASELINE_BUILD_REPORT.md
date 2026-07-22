# Baseline Build Report

Generated on 2026-06-07.

## Backend

- Manual local verification from the user's PowerShell: PASS.
- Command: `mvn test` from `backend/`.
- Tests run: 19.
- Failures: 0.
- Errors: 0.
- Skipped: 0.

### Maven Wrapper Status

- Maven wrapper files exist in `backend/`: `mvnw`, `mvnw.cmd`, and `.mvn/wrapper/maven-wrapper.properties`.
- Wrapper distribution is pinned to Apache Maven `3.9.16`.
- The wrapper is usable and reports:
  - Apache Maven `3.9.16`.
  - Maven home under the local user `.m2/wrapper/dists` cache.

### Codex Environment Status

- `java -version` in the Codex shell resolves to Java 8:
  - `java version "1.8.0_461"`.
- `where.exe java` resolves Java 8 paths first:
  - `C:\Program Files (x86)\Common Files\Oracle\Java\java8path\java.exe`
  - `C:\Program Files\Java\jdk1.8.0_261\bin\java.exe`
- `mvn -v` is not visible in the Codex shell.
- `where.exe mvn` cannot find Maven in the Codex shell.
- Java 21 exists on this machine at `C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot`.
- When the Codex command session sets `JAVA_HOME` to that path and prepends `%JAVA_HOME%\bin` to `PATH`, `java -version` reports:
  - OpenJDK `21.0.11` Temurin.
- `backend\mvnw.cmd test` with the Java 21 session override: PASS.
  - Tests run: 19.
  - Failures: 0.
  - Errors: 0.
  - Skipped: 0.
- `backend\mvnw.cmd package` with the Java 21 session override: PASS.
- Note: Codex command invocations are isolated, so the Java 21 override must be included in each backend command unless the parent Codex environment is relaunched with Java 21 first on `PATH`.

## Web

- `npm run build`: PASS, previously verified in this workspace.
- `npm test`: PASS, previously verified in this workspace.
- `npm run lint`: PASS, previously verified in this workspace.

## Mobile

- `npm run typecheck`: PASS, previously verified in this workspace.

## Recommended Commands

Use the Maven wrapper instead of relying on a global Maven install:

```powershell
cd backend
.\mvnw.cmd test
.\mvnw.cmd package
```

Before running backend commands, confirm Java 21 is first on PATH:

```powershell
java -version
```

If Windows still resolves Java 8 first, use a session override:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
```
