package ai.openclaw.jarvis.githubissues.integration

import ai.openclaw.jarvis.githubissues.GitHubIssueLogger
import ai.openclaw.jarvis.githubissues.model.IssueContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hook called from the action-executor layer for each of the action types
 * the spec calls out (SMS, WhatsApp, call, screenshot, location, app launch,
 * contact lookup) plus a generic catch-all.
 */
@Singleton
class ActionExecutorHook @Inject constructor(
    private val logger: GitHubIssueLogger,
) {

    fun onSmsFailure(errorCode: String?, message: String?, context: IssueContext) =
        logger.onActionFailure(ACTION_SMS, errorCode, message, context)

    fun onWhatsAppFailure(errorCode: String?, message: String?, context: IssueContext) =
        logger.onActionFailure(ACTION_WHATSAPP, errorCode, message, context)

    fun onCallFailure(errorCode: String?, message: String?, context: IssueContext) =
        logger.onActionFailure(ACTION_CALL, errorCode, message, context)

    fun onAppNotFound(appName: String, context: IssueContext) =
        logger.onActionFailure(ACTION_OPEN_APP, "app_not_found", "$appName not installed", context)

    fun onScreenshotFailure(errorCode: String?, message: String?, context: IssueContext) =
        logger.onActionFailure(ACTION_SCREENSHOT, errorCode, message, context)

    fun onLocationFailure(errorCode: String?, message: String?, context: IssueContext) =
        logger.onActionFailure(ACTION_LOCATION, errorCode, message, context)

    fun onContactAmbiguousAfterClarification(query: String, context: IssueContext) =
        logger.onActionFailure(
            actionType = ACTION_CONTACT_LOOKUP,
            errorCode = "ambiguous_after_clarification",
            message = "Could not disambiguate \"$query\" after clarification",
            context = context
        )

    fun onPermissionDenied(permission: String, action: String?, context: IssueContext) =
        logger.onPermissionDenied(permission, action, context)

    companion object {
        const val ACTION_SMS = "sms"
        const val ACTION_WHATSAPP = "whatsapp"
        const val ACTION_CALL = "call"
        const val ACTION_OPEN_APP = "open_app"
        const val ACTION_SCREENSHOT = "screenshot"
        const val ACTION_LOCATION = "location"
        const val ACTION_CONTACT_LOOKUP = "contact_lookup"
    }
}
