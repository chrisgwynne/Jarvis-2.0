package ai.openclaw.jarvis.capabilities.base

/**
 * Base capability interface.
 *
 * Every capability must:
 *   - declare its required Android permissions
 *   - report whether it is currently available (permission granted + hardware present)
 *   - fail safely with a structured CapabilityError
 *   - never fake success
 */
interface Capability {
    val id: String
    val description: String
    val requiredPermissions: List<String>
    fun isAvailable(): Boolean
}

data class CapabilityError(
    val code: String,
    val message: String,
    val requiresPermission: Boolean = false,
)

sealed class CapabilityResult<out T> {
    data class Success<T>(val value: T) : CapabilityResult<T>()
    data class Failure(val error: CapabilityError) : CapabilityResult<Nothing>()
}

fun <T> capabilitySuccess(value: T): CapabilityResult<T> = CapabilityResult.Success(value)
fun capabilityFailure(code: String, message: String, requiresPermission: Boolean = false): CapabilityResult<Nothing> =
    CapabilityResult.Failure(CapabilityError(code, message, requiresPermission))
