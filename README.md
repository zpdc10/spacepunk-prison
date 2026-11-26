# Spacepunk Prison

A Minecraft Java prison server with deep progression, set on a grungy derelict penal space station.
Players need only a vanilla client; the server sends a required resource pack on join.

- Server: Paper 26.3 (Java 25)
- Gameplay: one custom plugin (this repo)
- Design doc: https://claude.ai/artifact/AJtY7m7ZK2CK9ygxJhHukC
- Architecture and module boundaries: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- Player wiki (every system, with real numbers): [wiki/](wiki/README.md), hosted on the Unraid server at port 8085

## Build

```
./gradlew build
```

Needs JDK 25 (Gradle downloads one via toolchains if missing). Output: `build/libs/SpacepunkPrison.jar`.
CI builds every push and PR and uploads the jar as an artifact.
