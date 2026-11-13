# k9o-barbican (Android / JVM)

Official Kotlin SDK for [Kilimanjaro](#31-about-kilimanjaro) Barbican.
Barbican, the free embeddable L2 guardrail, for Kotlin. A thin wrapper over the keep-core Rust engine bound
via uniffi (the same engine the TypeScript, Python and Ruby SDKs wrap).

```kotlin
import dev.k9o.barbican.*

val b = Barbican()                                   // no network
b.loadKeyManifest(keyManifestJws)                    // engine checks it against the root compiled into it
b.loadRulesetSigned(SignedRuleset(42, rules, "kid", sig64, createdAt))   // the engine verifies; no key or verifier here
val v = b.inspect("ignore previous instructions")    // v.decision, v.reason (closed tokens)
b.signedFloor; b.activeRulesetIsSigned; b.trustedKids()
```

The wrapper does NO cryptography and holds NO key. A refused ruleset or manifest throws
`BarbicanException` (`code = K9O_BARBICAN_RULESET_INVALID`, `reason` = the engine's closed token, `stage` =
`ruleset` | `key_manifest`). If the native engine cannot be loaded, constructing a `Barbican` throws
`BarbicanEngineMissingException` (`K9O_BARBICAN_WASM_ERROR`) naming the missing engine.

## Contents

**A. Get started**

1. [Choose the right package](#1-choose-the-right-package)

**B. Build**

2. [Native artifact](#2-native-artifact)

**C. Reference**

3. [Appendix](#3-appendix)
   - 3.1 [About Kilimanjaro](#31-about-kilimanjaro)

## A. Get started

### 1. Choose the right package

| Package | Use it when you are… | Registry | Source |
| --- | --- | --- | --- |
| `dev.k9o:core-kotlin` | Calling the platform from an Android or Kotlin app as a signed-in user. | [Maven Central](https://central.sonatype.com/artifact/dev.k9o/core-kotlin) | [sdk-kotlin](https://github.com/k9o-platform/sdk-kotlin) |
| `dev.k9o:gate` | Signing users in: sign in, refresh and sign out. | [Maven Central](https://central.sonatype.com/artifact/dev.k9o/gate) | [sdk-kotlin](https://github.com/k9o-platform/sdk-kotlin) |
| `dev.k9o:kibo-vault` | Storing, listing and revealing secrets in the vault. | [Maven Central](https://central.sonatype.com/artifact/dev.k9o/kibo-vault) | [sdk-kibo-kotlin](https://github.com/k9o-platform/sdk-kibo-kotlin) |

## B. Build

### 2. Native artifact

Not built from a fresh checkout. Both artifacts are produced in CI and are gitignored:

| Artifact | Path | Produced by |
| --- | --- | --- |
| uniffi Kotlin binding | `src/main/kotlin/uniffi/k9o_keep_core_ffi_uniffi/` | `scripts/generate-bindings.sh` (uniffi-bindgen, uniffi 0.28) |
| Engine, per ABI | `src/main/jniLibs/<abi>/libk9o_keep_core_ffi_uniffi.so` | `cargo ndk build` of `ffi-uniffi`, WITHOUT `test-trust-root` |

A release engine has no trust root and refuses every signed ruleset and key manifest (`trust_root_unavailable`). The
published TEST root is compiled in only with `--features test-trust-root`, for the KAT replay on a host JVM; never
copy such a build into `jniLibs` or any shipped artifact.

## C. Reference

### 3. Appendix

- [About Kilimanjaro](#31-about-kilimanjaro)

#### 3.1 About Kilimanjaro


Kilimanjaro is a modular developer platform for building secure enterprise applications.
You define AI agents, functions and data pipelines on the platform.
The SDKs let you deploy and invoke them, read and write your data, publish to and subscribe to streams,
sign users in, and encrypt with keys you hold.

It provides four building blocks:

- **Keep**: AI security and governance. Guardrails and policy for the agents you run.
- **Pulse**: data intelligence and real-time stream processing. Streams, analytics and live feeds.
- **Gate**: customer identity and access management. Sign-in and sessions, and automatic data scoping so a signed-in user reaches only their own data.
- **Kibo**: cryptographic keys and secrets management. Client-side encryption with keys you hold, and a secrets vault.
