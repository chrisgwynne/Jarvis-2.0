package ai.openclaw.jarvis.router

/** Canonical intent categories — replaces the free-form string intent names. */
enum class IntentType {
    /** "stop", "cancel", "quiet", "never mind" */
    CANCEL_STOP,

    /** "text/message/tell/whatsapp [contact] [body]" */
    COMMUNICATION_SEND,

    /** "call/ring/dial [contact]" */
    COMMUNICATION_CALL,

    /** torch, volume, mute, media controls */
    DEVICE_CONTROL,

    /** "open/launch [app]" */
    APP_OPEN,

    /** "where am I", "what's my location" */
    LOCATION_QUERY,

    /** "take a photo/selfie" */
    CAMERA_ACTION,

    /** "screenshot", "look at this" */
    SCREEN_CAPTURE,

    /** "set a timer", "set alarm" */
    TIME_ACTION,

    /** All natural conversation, questions, business tasks, planning */
    OPENCLAW_REQUEST,

    /** Local capture + OpenClaw reasoning (screenshot + context, "send this") */
    MIXED_ACTION,

    /** "enrol my voice", "add voice profile", "train my voice" */
    ENROL_VOICE,

    /** "start recording conversation" */
    RECORDING_START,

    /** "stop recording" */
    RECORDING_STOP,
}

/** Communication channel preference extracted from the utterance. */
enum class MessageChannel { SMS, WHATSAPP, EMAIL, BEST_AVAILABLE }

/** Specific sub-actions within DEVICE_CONTROL. */
enum class DeviceControlAction {
    TORCH_ON, TORCH_OFF,
    VOLUME_UP, VOLUME_DOWN, MUTE, UNMUTE,
    MEDIA_PLAY_PAUSE, MEDIA_NEXT, MEDIA_PREVIOUS, MEDIA_STOP,
}

/** Specific sub-actions within CAMERA_ACTION. */
enum class CameraSubAction { PHOTO, SELFIE }

/** Specific sub-actions within TIME_ACTION. */
enum class TimeSubAction { TIMER, ALARM, REMINDER }
