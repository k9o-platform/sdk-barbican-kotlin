package dev.k9o.barbican

import uniffi.k9o_keep_core_ffi_uniffi.KeepConfig
import uniffi.k9o_keep_core_ffi_uniffi.KeepCore
import uniffi.k9o_keep_core_ffi_uniffi.KeepException
import uniffi.k9o_keep_core_ffi_uniffi.KeepLeakReport
import uniffi.k9o_keep_core_ffi_uniffi.KeepManifestInfo
import uniffi.k9o_keep_core_ffi_uniffi.KeepRule
import uniffi.k9o_keep_core_ffi_uniffi.KeepRulesetBlob
import uniffi.k9o_keep_core_ffi_uniffi.KeepSignedRulesetBlob
import uniffi.k9o_keep_core_ffi_uniffi.KeepStreamFilter
import uniffi.k9o_keep_core_ffi_uniffi.KeepVerdict
import uniffi.k9o_keep_core_ffi_uniffi.usesTestTrustRoot as engineUsesTestTrustRoot

/** Re-exported binding result types: callers `import dev.k9o.barbican.*` only. */
typealias BarbicanVerdict = KeepVerdict
typealias BarbicanManifestInfo = KeepManifestInfo
typealias BarbicanLeakReport = KeepLeakReport

/** One deny rule. [severity] is one of low|medium|high|critical. */
data class BarbicanRule(val id: String, val pattern: String, val severity: String)

/** A signed ruleset as published. The engine checks [signature] itself: there is no key or verifier to pass. */
class SignedRuleset(
    val rulesetVersion: Long,
    val rules: List<BarbicanRule>,
    val signingKeyKid: String,
    /** Raw 64-byte signature. */
    val signature: ByteArray,
    val createdAt: Long = 0,
)

/** A streaming output redactor. Buffer-and-scan-before-flush: never emits a byte the engine has not cleared. */
class BarbicanOutputFilter internal constructor(private val handle: KeepStreamFilter) {
    /** Feed a chunk; returns the cleared (redacted) prefix safe to emit now. */
    fun push(chunk: String): String = handle.push(chunk)

    /** Flush the residual buffer (also scanned) at end of stream. */
    fun finish(): String = handle.finish()

    /** Leak report (counts only, never the leaked values). */
    fun report(): BarbicanLeakReport = handle.report()
}

/**
 * Barbican, the free embeddable L2 guardrail: a thin wrapper over the bound keep-core engine (native, via uniffi).
 *
 * ZERO guardrail logic and ZERO cryptography live here: normalization, deny matching, verdicts, redaction, signature
 * checks, the trust root, anti-rollback and the signed floor are all the engine's. This layer forwards typed
 * arguments to the generated binding and maps its error to [BarbicanException]. The wrapper holds no key and takes
 * no verifier or keyring argument.
 *
 * Claim boundary: advisory / defense-in-depth (edge filtration). Never the enforced sandbox boundary.
 *
 * Construction needs no network. If the native engine cannot be loaded, every entry point raises
 * [BarbicanEngineMissingException] (never a null deref).
 */
class Barbican(
    failOpen: Boolean = false,
    leet: Boolean = true,
    /** 0 uses the engine default (3): a bounded base64/leetspeak de-obfuscation depth (decode-bomb guard). */
    decodeDepth: Int = 0,
) : AutoCloseable {
    private val core: KeepCore

    init {
        require(decodeDepth in 0..255) { "decodeDepth must be 0..255" }
        core = engine { KeepCore(KeepConfig(failOpen, leet, decodeDepth.toUByte())) }
    }

    /**
     * Load a static ruleset bundled with your package (provenance is vouched for by your package, so signature
     * checking is skipped; the engine still enforces anti-rollback). Returns the accepted version.
     */
    fun loadRuleset(rulesetVersion: Long, rules: List<BarbicanRule>): Long =
        call("ruleset") {
            core.loadRulesetUnsigned(KeepRulesetBlob(u(rulesetVersion), rules.map(::rule))).toLong()
        }

    /**
     * Load a Barbican key manifest (compact JWS). The engine checks it against the root compiled into it and, only
     * if it verifies, replaces the trusted ruleset-signing keys. A refusal throws with `stage == "key_manifest"`.
     * Returns the manifest sequence.
     */
    fun loadKeyManifest(jws: String, nowSecs: Long = System.currentTimeMillis() / 1000): Long =
        call("key_manifest") { core.loadKeyManifest(jws, u(nowSecs)).toLong() }

    /** Verify a ruleset manifest (compact JWS) under a trusted key; returns its authenticated fields. */
    fun verifyManifest(jws: String): BarbicanManifestInfo = call("ruleset") { core.verifyManifest(jws) }

    /**
     * Load a signed ruleset. The engine verifies the signature ITSELF under a key from the loaded key manifest.
     * Any refusal throws [BarbicanException] with the engine's closed [BarbicanException.reason] and leaves the
     * current ruleset running. Returns the accepted version.
     */
    fun loadRulesetSigned(blob: SignedRuleset): Long = call("ruleset") { core.loadRulesetSigned(signed(blob)).toLong() }

    /** Lowercase hex digest of a ruleset's canonical signing bytes. A digest only; it decides no trust. */
    fun rulesetDigest(blob: SignedRuleset): String = call("ruleset") { core.rulesetDigest(signed(blob)) }

    /** The sequence of the key manifest in force (0 before one is loaded). */
    val keyManifestSequence: Long get() = engine { core.keyManifestSequence().toLong() }

    /** Highest VERIFIED ruleset version loaded (0 = none): the anti-rollback floor for signed updates. */
    val signedFloor: Long get() = engine { core.signedFloor().toLong() }

    /** Whether the ruleset in force came from a verified load. */
    val activeRulesetIsSigned: Boolean get() = engine { core.activeRulesetIsSigned() }

    /** The signing kids the loaded key manifest currently trusts, sorted. */
    fun trustedKids(): List<String> = engine { core.trustedKids() }

    /** The trusted kids the key manifest flags deprecated, sorted. */
    fun deprecatedKids(): List<String> = engine { core.deprecatedKids() }

    /**
     * Inspect an input (normalize, deny, local verdict). [waypointClass] is the manifest's meter hint: it NEVER
     * gates the decision.
     */
    fun inspect(text: String, waypointClass: String? = null): BarbicanVerdict =
        engine { core.inspectInput(text, waypointClass) }

    /** Local L3 stand-in classification: "clear" | "suspicious" | "malicious". No receipt. */
    fun classify(text: String): String = engine { core.classifyLocal(text) }

    /** Open an output stream-filter bound to the active ruleset's exfil markers. */
    fun createOutputFilter(): BarbicanOutputFilter = engine { BarbicanOutputFilter(core.outputFilter()) }

    /** The bound engine's semver (audit anchor; rides every verdict too). */
    val coreVersion: String get() = engine { core.coreVersion() }

    override fun close() = core.close()

    companion object {
        /**
         * False on every shipped build. True means this engine trusts the PUBLISHED TEST root and must never be
         * used outside tests.
         */
        fun usesTestTrustRoot(): Boolean = engine { engineUsesTestTrustRoot() }

        /** Whether the native engine can be loaded here. */
        fun engineAvailable(): Boolean =
            try {
                engine { engineUsesTestTrustRoot() }
                true
            } catch (_: BarbicanEngineMissingException) {
                false
            }

        private fun u(v: Long): ULong {
            require(v >= 0) { "negative value: $v" }
            return v.toULong()
        }

        private fun rule(r: BarbicanRule) = KeepRule(r.id, r.pattern, r.severity)

        private fun signed(b: SignedRuleset) =
            KeepSignedRulesetBlob(u(b.rulesetVersion), b.rules.map(::rule), b.signingKeyKid, b.signature.copyOf(), b.createdAt)

        /** Run a binding call, mapping a missing native engine to the typed error. */
        private inline fun <T> engine(block: () -> T): T =
            try {
                block()
            } catch (e: UnsatisfiedLinkError) {
                throw BarbicanEngineMissingException(e)
            } catch (e: NoClassDefFoundError) {
                throw BarbicanEngineMissingException(e)
            } catch (e: ExceptionInInitializerError) {
                throw BarbicanEngineMissingException(e)
            }

        /** [engine] plus the engine's refusal mapped to [BarbicanException]. */
        private inline fun <T> call(stage: String, block: () -> T): T =
            try {
                engine(block)
            } catch (e: KeepException.RulesetInvalid) {
                throw BarbicanException(
                    BarbicanException.CODE_RULESET_INVALID, e.reason, stage, "reason=${e.reason}", e,
                )
            } catch (e: KeepException) {
                throw BarbicanException(BarbicanException.CODE_ENGINE_ERROR, null, stage, e.toString(), e)
            }
    }
}
