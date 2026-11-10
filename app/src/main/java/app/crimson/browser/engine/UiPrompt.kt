package app.crimson.browser.engine

/** Dialogs requested by web pages, extensions and the engine, shown by the Compose UI. */
sealed class UiPrompt {
    abstract val title: String

    class Alert(override val title: String, val message: String, val onClose: () -> Unit) : UiPrompt()

    class Confirm(
        override val title: String,
        val message: String,
        val yes: String,
        val no: String,
        val onResult: (Boolean) -> Unit,
    ) : UiPrompt()

    class Text(
        override val title: String,
        val message: String,
        val initial: String,
        val onResult: (String?) -> Unit,
    ) : UiPrompt()

    class Choice(
        override val title: String,
        val message: String,
        val labels: List<String>,
        val ids: List<String>,
        val multiple: Boolean,
        val onResult: (List<String>?) -> Unit,
    ) : UiPrompt()
}

class ContextInfo(
    val linkUri: String?,
    val linkText: String?,
    val srcUri: String?,
    val isImage: Boolean,
    val isVideoOrAudio: Boolean,
)

class MediaItem(val url: String, val mime: String, val size: Long)
