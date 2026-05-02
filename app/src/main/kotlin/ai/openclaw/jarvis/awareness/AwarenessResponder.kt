package ai.openclaw.jarvis.awareness

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns an [AwarenessQuestion] + [AwarenessSnapshot] into the spoken
 * answer Jarvis will play through TTS.
 *
 * All phrasing comes from the snapshot — we intentionally never fall
 * back to a hard-coded "I can do X" string. If a capability isn't in the
 * snapshot as AVAILABLE, the answer says so.
 */
@Singleton
class AwarenessResponder @Inject constructor() {

    fun answer(q: AwarenessQuestion, snap: AwarenessSnapshot): String = when (q) {
        AwarenessQuestion.WhatCanYouDo -> answerWhatCanYouDo(snap)
        AwarenessQuestion.MissingPermissions -> answerMissingPermissions(snap)
        AwarenessQuestion.WhyCantYouDoThat -> answerWhyCantYouDoThat(snap)
        is AwarenessQuestion.CanYou -> answerCanYou(q.topic, snap)
    }

    // ─── "What can you do?" ──────────────────────────────────────────────────

    private fun answerWhatCanYouDo(snap: AwarenessSnapshot): String {
        val available = snap.androidActions.filter { it.state == AvailabilityState.AVAILABLE }
        val openClaw = snap.openClawSkills.filter { it.state == AvailabilityState.AVAILABLE }

        // Voice-friendly grouping. Roll local items into one clause, then
        // an OpenClaw clause if anything's actually online.
        val localClause = if (available.isEmpty()) {
            "Right now I can't run any local actions."
        } else {
            "I can " + available.joinToString(", ") { it.label.lowercase() } + "."
        }

        val openClawClause = when {
            !snap.openClawConnected ->
                " OpenClaw skills like email, research, memory, and business tasks are offline."
            openClaw.isEmpty() ->
                " OpenClaw is connected but hasn't published any skills yet."
            else -> {
                val skills = openClaw.joinToString(", ") { it.name.lowercase() }
                " I can also use OpenClaw for $skills."
            }
        }

        // Trust-level qualifier.
        val trustClause = when (snap.trustLevel) {
            "UNKNOWN" -> " Owner verification is needed before I can send messages, share location, or use private data."
            "GUEST" -> " You're not the owner, so I'll keep things to safe phone actions."
            else -> ""
        }

        // One-shot caveat for the most common missing-permission cases.
        val caveat = buildList {
            snap.androidActions.firstOrNull { it.id == "screenshot" && it.state == AvailabilityState.PERMISSION_MISSING }
                ?.let { add("Screenshot needs permission first.") }
            snap.androidActions.firstOrNull { it.id == "sms" && it.state == AvailabilityState.PERMISSION_MISSING }
                ?.let { add("SMS needs permission first.") }
            snap.androidActions.firstOrNull { it.id == "location" && it.state == AvailabilityState.PERMISSION_MISSING }
                ?.let { add("Location needs permission first.") }
        }.joinToString(" ").let { if (it.isNotEmpty()) " $it" else "" }

        return localClause + openClawClause + trustClause + caveat
    }

    // ─── "Can you X?" ────────────────────────────────────────────────────────

    private fun answerCanYou(topic: AwarenessQuestion.Topic, snap: AwarenessSnapshot): String {
        return when (topic) {
            AwarenessQuestion.Topic.SMS -> rowAnswer(
                find(snap, "sms"),
                yes = "I can send SMS, and I'll ask before sending.",
                noByPermission = "I can't send SMS until SMS permission is granted.",
                noByTrust = "I can send SMS once you're verified as the owner.",
                fallback = "I can't send SMS right now.",
            )
            AwarenessQuestion.Topic.WHATSAPP -> {
                val row = find(snap, "whatsapp")
                when (row?.state) {
                    AvailabilityState.AVAILABLE -> "I can send WhatsApps, but I'll ask before sending."
                    AvailabilityState.NOT_INSTALLED -> "I can't send WhatsApps because WhatsApp isn't installed. I can use SMS instead if SMS is available."
                    AvailabilityState.DISABLED_BY_TRUST -> "WhatsApp is available, but I need owner verification before sending."
                    else -> "I can't send WhatsApps right now."
                }
            }
            AwarenessQuestion.Topic.CALL -> rowAnswer(
                find(snap, "calls"),
                yes = "I can make calls, with a confirmation step.",
                noByPermission = "I can't make calls until call permission is granted.",
                noByTrust = "I can make calls once you're verified as the owner.",
                fallback = "I can't make calls right now.",
            )
            AwarenessQuestion.Topic.EMAIL -> {
                val openClaw = snap.openClawSkills.firstOrNull { it.id.startsWith("email") }
                when {
                    openClaw?.state == AvailabilityState.AVAILABLE ->
                        "Yes, I can send email through OpenClaw."
                    openClaw != null && !snap.openClawConnected ->
                        "Email goes through OpenClaw, and OpenClaw is offline right now."
                    !snap.openClawConnected ->
                        "Email needs OpenClaw, and OpenClaw is offline right now."
                    else ->
                        "OpenClaw is connected but hasn't published an email skill, so I can't send email."
                }
            }
            AwarenessQuestion.Topic.SCREENSHOT, AwarenessQuestion.Topic.SCREEN -> {
                val row = find(snap, "screenshot")
                when (row?.state) {
                    AvailabilityState.AVAILABLE -> "I can capture screenshots."
                    AvailabilityState.PERMISSION_MISSING ->
                        "I can analyse your screen after screenshot permission is granted."
                    else -> "I can't capture screenshots right now."
                }
            }
            AwarenessQuestion.Topic.LOCATION -> rowAnswer(
                find(snap, "location"),
                yes = "I can get your last known location.",
                noByPermission = "I can use your location once location permission is granted.",
                noByTrust = "I can share your location once you're verified as the owner.",
                fallback = "I can't get your location right now.",
            )
            AwarenessQuestion.Topic.OPEN_CLAW -> if (snap.openClawConnected) {
                if (snap.openClawSkills.isEmpty())
                    "Yes, OpenClaw is connected, but it hasn't published any skills yet."
                else
                    "Yes, OpenClaw is connected. I can use ${
                        snap.openClawSkills.filter { it.state == AvailabilityState.AVAILABLE }
                            .joinToString(", ") { it.name.lowercase() }
                    }."
            } else "No, OpenClaw is offline right now."
            AwarenessQuestion.Topic.OPEN_APP -> rowAnswer(
                find(snap, "open_app"),
                yes = "I can open installed apps by name.",
                noByPermission = "I can't open apps right now.",
                noByTrust = "I can open apps once you're verified as the owner.",
                fallback = "I can't open apps right now.",
            )
            AwarenessQuestion.Topic.PHOTO -> rowAnswer(
                find(snap, "camera"),
                yes = "I can take photos through the camera.",
                noByPermission = "I can take photos once camera permission is granted.",
                noByTrust = "I can take photos once you're verified as the owner.",
                fallback = "I can't take photos right now.",
            )
        }
    }

    private fun rowAnswer(
        row: LocalAction?,
        yes: String,
        noByPermission: String,
        noByTrust: String,
        fallback: String,
    ): String = when (row?.state) {
        AvailabilityState.AVAILABLE -> yes
        AvailabilityState.PERMISSION_MISSING -> noByPermission
        AvailabilityState.DISABLED_BY_TRUST -> noByTrust
        else -> fallback
    }

    private fun find(snap: AwarenessSnapshot, id: String): LocalAction? =
        snap.androidActions.firstOrNull { it.id == id }

    // ─── "What permissions are missing?" ─────────────────────────────────────

    private fun answerMissingPermissions(snap: AwarenessSnapshot): String {
        if (snap.missingPermissions.isEmpty()) {
            return "All Android permissions Jarvis needs are granted."
        }
        val rows = snap.missingPermissions.distinctBy { it.capabilityId }
        val phrasing = rows.joinToString(", ") { it.rationale.lowercase() }
        return "I'm missing permission for $phrasing."
    }

    // ─── "Why can't you do that?" ────────────────────────────────────────────

    private fun answerWhyCantYouDoThat(snap: AwarenessSnapshot): String {
        val unavailable = snap.androidActions.filter { it.state != AvailabilityState.AVAILABLE }
        val first = unavailable.firstOrNull()
            ?: return "Everything's available — could you say what you'd like me to do?"
        val reason = first.reason ?: when (first.state) {
            AvailabilityState.PERMISSION_MISSING -> "the permission isn't granted"
            AvailabilityState.NOT_INSTALLED -> "the app isn't installed"
            AvailabilityState.DISABLED_BY_TRUST -> "owner verification is required"
            AvailabilityState.OFFLINE -> "OpenClaw is offline"
            else -> "the capability isn't available"
        }
        return "I can't ${first.label.lowercase()} because $reason."
    }
}
