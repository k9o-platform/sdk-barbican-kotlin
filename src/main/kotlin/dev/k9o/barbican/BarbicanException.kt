package dev.k9o.barbican

/**
 * Typed errors for Barbican, in the public `dev.k9o.barbican` namespace so the contract is stable across binding
 * regeneration (callers never import the generated `uniffi.*` package).
 *
 * [code] mirrors the other SDKs: [CODE_RULESET_INVALID] for an engine refusal (a ruleset, manifest or key
 * manifest), [CODE_ENGINE_ERROR] for anything else from the native layer, including the engine being absent.
 * [reason] is the engine's own closed token (`unknown_kid`, `signature_invalid`, `version_stale`, `malformed`,
 * `trust_root_unavailable`, ...), passed through verbatim and never invented here. [stage] says whether the
 * refusal came from the `ruleset` or the `key_manifest`.
 */
open class BarbicanException(
    val code: String,
    val reason: String?,
    val stage: String,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    companion object {
        const val CODE_RULESET_INVALID = "K9O_BARBICAN_RULESET_INVALID"
        const val CODE_ENGINE_ERROR = "K9O_BARBICAN_WASM_ERROR"
    }
}

/**
 * The keep-core native engine (libk9o_keep_core_ffi_uniffi) could not be loaded on this platform. Raised instead
 * of a null deref or a raw `UnsatisfiedLinkError`; the cause is attached.
 */
class BarbicanEngineMissingException(cause: Throwable?) : BarbicanException(
    code = CODE_ENGINE_ERROR,
    reason = null,
    stage = "engine",
    message = "barbican native engine (libk9o_keep_core_ffi_uniffi) is not available for this platform/ABI: " +
        "the engine is missing; install a build of the SDK that packages it for this ABI.",
    cause = cause,
)
