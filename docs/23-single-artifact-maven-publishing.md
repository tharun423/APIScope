# 23 — Single Artifact Maven Publishing

## Problem

APIScope is a multi-module project:

```
apiscope-parent
├── apiscope-core
├── apiscope-flow
├── apiscope-spring-boot-starter
└── apiscope-sample-app
```

Without any changes, running `mvn clean deploy -P release` publishes **3 separate artifacts** to Maven Central:

```
io.github.tharun423 : apiscope-core                 : 1.0.0
io.github.tharun423 : apiscope-flow                 : 1.0.0
io.github.tharun423 : apiscope-spring-boot-starter  : 1.0.0
```

This forces users to understand the internal module structure and potentially add multiple dependencies. `apiscope-core` and `apiscope-flow` are internal implementation details — they should never be added directly by users.

---

## Solution — Maven Shade Plugin (Option A)

Merge `apiscope-core` and `apiscope-flow` classes **physically into** the starter JAR at build time, then skip deploying them to Maven Central.

### What changes

**`apiscope-core/pom.xml` and `apiscope-flow/pom.xml`** — skip deployment:

```xml
<properties>
    <maven.deploy.skip>true</maven.deploy.skip>
</properties>
```

**`apiscope-spring-boot-starter/pom.xml`** — shade core + flow into the starter JAR:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-shade-plugin</artifactId>
            <version>3.6.0</version>
            <executions>
                <execution>
                    <phase>package</phase>
                    <goals><goal>shade</goal></goals>
                    <configuration>
                        <createDependencyReducedPom>true</createDependencyReducedPom>
                        <artifactSet>
                            <includes>
                                <include>io.github.tharun423:apiscope-core</include>
                                <include>io.github.tharun423:apiscope-flow</include>
                            </includes>
                        </artifactSet>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

---

## What happens during `mvn clean deploy -P release`

### Before (without shading)

| Phase | What happens |
|-------|-------------|
| Build | All modules compile |
| Package | Each module produces its own JAR |
| Deploy | 3 artifacts uploaded to Maven Central |

### After (with shading)

| Phase | What happens |
|-------|-------------|
| Build | All modules compile |
| Package | Shade plugin merges core + flow classes **into** the starter JAR |
| Deploy | Only starter uploaded — core and flow skipped |

### Starter JAR contents after shading

```
apiscope-spring-boot-starter-1.0.0.jar
├── io/github/tharun423/core/...       ← merged from apiscope-core
├── io/github/tharun423/flow/...       ← merged from apiscope-flow
└── io/github/tharun423/starter/...    ← starter's own classes
```

### Published POM after shading

`createDependencyReducedPom=true` removes `apiscope-core` and `apiscope-flow` from the starter's published `<dependencies>` block. Maven Central will not show them as transitive dependencies.

---

## Result

Only one artifact lands on Maven Central:

```
io.github.tharun423 : apiscope-spring-boot-starter : 1.0.0  ✅ published
io.github.tharun423 : apiscope-core                : 1.0.0  ❌ skipped
io.github.tharun423 : apiscope-flow                : 1.0.0  ❌ skipped
```

Users add a single dependency and get everything:

```xml
<dependency>
    <groupId>io.github.tharun423</groupId>
    <artifactId>apiscope-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

## Why not Option B (skip deploy only)?

Skipping deploy without shading would still leave `apiscope-core` and `apiscope-flow` listed as `<dependencies>` in the starter's published POM. Maven would try to resolve them from Central, fail, and break the user's build. Shading is required to make the single-artifact approach work correctly.
