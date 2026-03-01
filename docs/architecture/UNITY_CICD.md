# Unity CI/CD — game-ci + Multi-Target + UI Automation

> Unity CI/CD for ink-unity (OneJS bridge, asset events, UI Toolkit)
> game-ci Docker images + sdkman (GraalVM 25 + Node 24 + Python 3.11 + .NET 9)
>
> **PlantUML diagram**: [`unity-cicd.puml`](unity-cicd.puml)
> **Related**: [`JBANG_LAUNCHER.md`](JBANG_LAUNCHER.md) | [`KMP_CICD.md`](KMP_CICD.md) | [`MAUI_CICD.md`](MAUI_CICD.md)

---

## 1. Docker Image Architecture

Layered Docker images from [game-ci](https://game.ci/):

```
unityci/base (Ubuntu 22.04 + xvfb + git)
  └── unityci/hub (+ Unity Hub)
        └── unityci/editor:ubuntu-{version}-{component}-{gameci}
              └── inky-ci (+ sdkman + GraalVM 25 + Node 24 + Python 3.11 + .NET 9 + Vulkan)
```

### Tag Format

`unityci/editor:ubuntu-{UNITY_VERSION}-{COMPONENT}-{GAMECI_VERSION}`

Example: `unityci/editor:ubuntu-6000.3.10f1-linux-il2cpp-3.2.1`

### Versions (from `gradle/libs.versions.toml`)

| Key | Value | Used In |
|-----|-------|---------|
| `unity` | `6000.3.10f1` | Docker image tag, ProjectVersion.txt |
| `unity-ci-image` | `singtaa/unity-ci-node` | Current custom image (to be replaced by inky-ci) |
| `gameci` | `3.2.1` | game-ci Docker image version |
| `ubuntu` | `22.04` | Runner + Docker base |
| `node` | `24` | Node.js in Docker |
| `python` | `3.11` | Python in Docker |
| `jdk-sdkman` | `25.0.2-graal` | Oracle GraalVM in Docker |
| `kotlin` | `2.3.0` | Kotlin in Docker |

---

## 2. Dockerfile.ci

Custom Docker image extending `unityci/editor` with the full polyglot SDK stack:

```dockerfile
ARG UNITY_VERSION=6000.3.10f1
ARG COMPONENT=linux-il2cpp
ARG GAMECI_VERSION=3.2.1
FROM unityci/editor:ubuntu-${UNITY_VERSION}-${COMPONENT}-${GAMECI_VERSION}

SHELL ["/bin/bash", "-c"]

# ── System packages + software Vulkan ──
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl zip unzip \
    mesa-vulkan-drivers libvulkan1 vulkan-tools \
    python3.11 python3.11-venv \
    && rm -rf /var/lib/apt/lists/*

# ── Node.js ──
ARG NODE_VERSION=24
RUN curl -fsSL https://deb.nodesource.com/setup_${NODE_VERSION}.x | bash - \
    && apt-get install -y nodejs && rm -rf /var/lib/apt/lists/*

# ── .NET 9 SDK ──
RUN curl -fsSL https://dot.net/v1/dotnet-install.sh | bash -s -- --channel 9.0 \
    && ln -s $HOME/.dotnet/dotnet /usr/local/bin/dotnet

# ── sdkman + Oracle GraalVM 25 + Kotlin ──
RUN curl -s "https://get.sdkman.io?ci=true&rcupdate=false" | bash \
    && sed -i 's/sdkman_auto_answer=false/sdkman_auto_answer=true/' $HOME/.sdkman/etc/config \
    && sed -i 's/sdkman_selfupdate_feature=true/sdkman_selfupdate_feature=false/' $HOME/.sdkman/etc/config \
    && sed -i 's/sdkman_colour_enable=true/sdkman_colour_enable=false/' $HOME/.sdkman/etc/config

ENV BASH_ENV="$HOME/.sdkman/bin/sdkman-init.sh"

ARG JDK_VERSION=25.0.2-graal
ARG KOTLIN_VERSION=2.3.0
RUN sdk install java ${JDK_VERSION} && \
    sdk install kotlin ${KOTLIN_VERSION} && \
    sdk flush && rm -rf $HOME/.sdkman/archives/* $HOME/.sdkman/tmp/*

ENV JAVA_HOME="$HOME/.sdkman/candidates/java/current"
ENV KOTLIN_HOME="$HOME/.sdkman/candidates/kotlin/current"
ENV PATH="$JAVA_HOME/bin:$KOTLIN_HOME/bin:$PATH"
```

---

## 3. Multi-Target Build Matrix

| Project Target | Unity Component | Runner | Cross-compile? |
|---------------|----------------|--------|----------------|
| StandaloneLinux64 | `linux-il2cpp` | ubuntu-22.04 | No |
| Android | `android` | ubuntu-22.04 | Yes |
| WASM/WebGL | `webgl` | ubuntu-22.04 | Yes |
| Windows | `windows-mono` | ubuntu-22.04 | Yes |
| macOS | `mac-mono` | macos-latest only | No |
| iOS | `ios` | macos-latest only | No |
| JVM server | *(no Unity)* | ubuntu-22.04 | N/A |
| Electron / PWA | *(no Unity)* | ubuntu-22.04 | N/A |

### CI Workflow (Planned Multi-Target)

```yaml
jobs:
  unity-build:
    runs-on: ubuntu-22.04
    strategy:
      matrix:
        component: [linux-il2cpp, android, webgl, windows-mono]
        include:
          - component: linux-il2cpp
            target: StandaloneLinux64
          - component: android
            target: Android
          - component: webgl
            target: WebGL
          - component: windows-mono
            target: StandaloneWindows64
    steps:
      - uses: actions/checkout@v4
      - name: Read TOML
        id: sdk
        run: |
          TOML="gradle/libs.versions.toml"
          get() { grep "^$1 = " "$TOML" | sed 's/.*= "\(.*\)"/\1/'; }
          echo "unity=$(get unity)" >> "$GITHUB_OUTPUT"
          echo "gameci=$(get gameci)" >> "$GITHUB_OUTPUT"
      - uses: game-ci/unity-builder@v4
        env:
          UNITY_LICENSE: ${{ secrets.UNITY_LICENSE }}
        with:
          customImage: ghcr.io/vrdate/inky-ci:${{ steps.sdk.outputs.unity }}-${{ matrix.component }}
          targetPlatform: ${{ matrix.target }}
          projectPath: ${{ env.PROJECT_PATH }}
```

---

## 4. Unity CI Testing Patterns

| Test Type | GPU? | Approach | game-ci Support |
|-----------|------|----------|----------------|
| **Logic / data model** | No | EditMode + `-batchmode -nographics` | Out of box |
| **UI Toolkit layout** | Minimal | EditMode + xvfb (drop `-nographics`) | xvfb pre-installed in base |
| **UI click simulation** | Depends | PlayMode + xvfb + `InputTestFixture` | xvfb pre-installed in base |
| **Visual regression** | Soft | PlayMode + xvfb + `com.unity.testframework.graphics` | Add `mesa-vulkan-drivers` |
| **Shader / compute** | Hard | Self-hosted GPU + NVIDIA Container Toolkit | Custom image + GPU host |

### Test Runner Configuration

```yaml
# EditMode tests (no GPU needed)
- uses: game-ci/unity-test-runner@v4
  with:
    testMode: editmode
    customImage: ghcr.io/vrdate/inky-ci:${{ steps.sdk.outputs.unity }}-${{ matrix.component }}

# PlayMode tests (xvfb + software Vulkan)
- uses: game-ci/unity-test-runner@v4
  with:
    testMode: playmode
    customImage: ghcr.io/vrdate/inky-ci:${{ steps.sdk.outputs.unity }}-${{ matrix.component }}
```

### Software Vulkan

`mesa-vulkan-drivers` provides `llvmpipe` — a CPU-based Vulkan renderer. This enables:
- UI Toolkit rendering tests without GPU hardware
- Visual regression testing with deterministic output
- PlayMode tests that require a graphics context

**Not sufficient for**: Shader compilation testing, compute shader tests, performance benchmarks.

---

## 5. Existing Unity CI Workflow

`ink-unity/onejs/.github/workflows/unity-ci.yml` — OneJS CI (TOML-driven):

1. Parse versions from `gradle/libs.versions.toml`
2. Bootstrap minimal Unity project
3. Import OneJS package via rsync
4. Apply `csc.rsp` preprocessor symbols
5. Run PlayMode tests via `game-ci/unity-test-runner@v4`
6. Build StandaloneLinux64 via `game-ci/unity-builder@v4`
7. Upload artifacts

Currently uses `singtaa/unity-ci-node` custom image (Node.js + PuerTS). Will be replaced by `ghcr.io/vrdate/inky-ci`.

---

## 6. Multi-Target Docker Build Script

`docker-build-ci.sh` — reads all versions from TOML:

```bash
#!/usr/bin/env bash
set -euo pipefail
TOML="gradle/libs.versions.toml"
get() { grep "^$1 = " "$TOML" | sed 's/.*= "\(.*\)"/\1/'; }

REGISTRY="${REGISTRY:-ghcr.io/vrdate/inky-ci}"
components=("linux-il2cpp" "android" "webgl" "windows-mono")

for component in "${components[@]}"; do
  echo "Building $REGISTRY:$(get unity)-$component ..."
  docker build \
    --build-arg UNITY_VERSION=$(get unity) \
    --build-arg COMPONENT=$component \
    --build-arg GAMECI_VERSION=$(get gameci) \
    --build-arg NODE_VERSION=$(get node) \
    --build-arg JDK_VERSION=$(get jdk-sdkman) \
    --build-arg KOTLIN_VERSION=$(get kotlin) \
    -f Dockerfile.ci \
    -t "$REGISTRY:$(get unity)-$component" .
done

echo "Done. Push with:"
for component in "${components[@]}"; do
  echo "  docker push $REGISTRY:$(get unity)-$component"
done
```

---

## 7. Integration with KMP

GraalJS on the JVM server ≈ OneJS on Unity:

| Aspect | KMP Server | Unity |
|--------|------------|-------|
| **JS Engine** | GraalJS (Oracle GraalVM) | OneJS (PuerTS + V8) |
| **inkjs** | Loaded via polyglot API | Loaded via PuerTS |
| **Bridge** | `InkEngine.kt` → JS context | `InkOneJsBinding.cs` → `__inkBridge` |
| **Asset Events** | `AssetEventBus.kt` (origin) | `InkAssetEventReceiver.cs` (consumer) |
| **Transport** | RSocket + msgpack (server-side) | WebSocket or in-process |

MCP tools available to Unity OneJS scripts via localhost HTTP (`POST /message`).

---

## 8. Integration with MAUI

Unity UAAL (Unity as a Library) embedded in MAUI:

| Platform | MAUI Host | Unity UAAL | Bridge |
|----------|-----------|------------|--------|
| **Android** | MauiActivity | AAR (ARM64) | `UnitySendMessage` (in-process) |
| **iOS** | MauiViewController | .framework (ARM64) | `UnitySendMessage` (in-process) |
| **Windows** | WinUI3 | N/A | WebSocket to standalone Unity |
| **macOS** | Catalyst | N/A | WebSocket to standalone Unity |

See: [`MAUI_CICD.md`](MAUI_CICD.md), [`INK_MAUI_UNITY_INTEGRATION.md`](INK_MAUI_UNITY_INTEGRATION.md)

---

## 9. GHCR Image Publishing

Workflow to build and push Docker images on version bump:

```yaml
name: Build CI Images
on:
  push:
    paths:
      - 'Dockerfile.ci'
      - 'gradle/libs.versions.toml'
jobs:
  build-push:
    runs-on: ubuntu-22.04
    permissions:
      packages: write
    steps:
      - uses: actions/checkout@v4
      - uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - name: Build and push
        run: |
          REGISTRY=ghcr.io/${{ github.repository_owner }}/inky-ci
          ./docker-build-ci.sh
          for tag in $(docker images --format '{{.Repository}}:{{.Tag}}' | grep inky-ci); do
            docker push "$tag"
          done
```

### TOML Version Sync Check

CI step to verify TOML versions match Dockerfile/workflow:

```yaml
- name: Verify TOML sync
  run: |
    TOML="gradle/libs.versions.toml"
    get() { grep "^$1 = " "$TOML" | sed 's/.*= "\(.*\)"/\1/'; }
    # Verify Dockerfile ARG defaults match TOML
    grep -q "UNITY_VERSION=$(get unity)" Dockerfile.ci || exit 1
    grep -q "GAMECI_VERSION=$(get gameci)" Dockerfile.ci || exit 1
    echo "TOML versions in sync"
```

---

## 10. `.sdkmanrc`

Project-level SDK pinning at repo root:

```
java=25.0.2-graal
kotlin=2.3.0
```

Works on Ubuntu, WSL, macOS. Termux uses `pkg install openjdk-21` (JDK 25 when available).

---

## References

- [game-ci](https://game.ci/) — Unity CI/CD for GitHub Actions
- [unityci/editor Docker Hub](https://hub.docker.com/r/unityci/editor) — Pre-built Unity Docker images
- [Unity Test Framework](https://docs.unity3d.com/Packages/com.unity.test-framework@1.4/manual/index.html) — EditMode + PlayMode tests
- [mesa-vulkan-drivers](https://mesa3d.org/) — Software Vulkan (llvmpipe)
- [SDKMAN](https://sdkman.io/) — SDK manager
- [`unity-ci.yml`](../../ink-unity/onejs/.github/workflows/unity-ci.yml) — Existing Unity CI workflow
- [`build-executables.yaml`](../../.github/workflows/build-executables.yaml) — Electron build workflow
