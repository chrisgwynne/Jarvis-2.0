package ai.openclaw.jarvis.screen

import ai.openclaw.jarvis.screen.model.AppCategory
import ai.openclaw.jarvis.screen.model.PageType
import ai.openclaw.jarvis.screen.model.ScreenContextEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextInterpreterTest {

    private val interpreter = ContextInterpreter()

    @Test fun `spotify is interpreted as listening to music`() {
        val out = interpreter.interpret(event(pkg = "com.spotify.music", cat = AppCategory.MEDIA))
        assertEquals(PageType.LISTENING_TO_MUSIC, out.pageType)
        assertTrue(out.summary, out.summary.contains("listening"))
    }

    @Test fun `etsy in browser is browsing listings`() {
        val out = interpreter.interpret(event(
            pkg = "com.android.chrome",
            cat = AppCategory.BROWSER,
            url = "https://www.etsy.com/uk/shop/example",
        ))
        assertEquals(PageType.BROWSING_LISTINGS, out.pageType)
    }

    @Test fun `etsy app maps to shopping listings via category`() {
        val out = interpreter.interpret(event(pkg = "com.etsy.android", cat = AppCategory.SHOPPING))
        assertEquals(PageType.BROWSING_LISTINGS, out.pageType)
    }

    @Test fun `gmail draft is writing email`() {
        val out = interpreter.interpret(event(
            pkg = "com.google.android.gm", cat = AppCategory.EMAIL,
            pageTitle = "Compose",
        ))
        assertEquals(PageType.WRITING_EMAIL, out.pageType)
    }

    @Test fun `gmail inbox without compose is reading email`() {
        val out = interpreter.interpret(event(pkg = "com.google.android.gm", cat = AppCategory.EMAIL))
        assertEquals(PageType.READING_EMAIL, out.pageType)
    }

    @Test fun `whatsapp is messaging`() {
        val out = interpreter.interpret(event(pkg = "com.whatsapp", cat = AppCategory.MESSAGING))
        assertEquals(PageType.MESSAGING, out.pageType)
    }

    @Test fun `sensitive app falls through to unknown summary`() {
        val out = interpreter.interpret(event(pkg = "co.uk.monzo", cat = AppCategory.SENSITIVE))
        assertEquals(PageType.UNKNOWN, out.pageType)
    }

    private fun event(
        pkg: String,
        cat: AppCategory,
        url: String? = null,
        pageTitle: String? = null,
    ) = ScreenContextEvent(
        packageName = pkg,
        appLabel = pkg,
        category = cat,
        pageTitle = pageTitle,
        url = url,
    )
}
