package ai.openclaw.jarvis.screen

import ai.openclaw.jarvis.screen.model.AppCategorisation
import ai.openclaw.jarvis.screen.model.AppCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class AppCategoryTest {

    @Test fun `whatsapp is messaging`() =
        assertEquals(AppCategory.MESSAGING, AppCategorisation.classify("com.whatsapp"))

    @Test fun `chrome is browser`() =
        assertEquals(AppCategory.BROWSER, AppCategorisation.classify("com.android.chrome"))

    @Test fun `etsy is shopping`() =
        assertEquals(AppCategory.SHOPPING, AppCategorisation.classify("com.etsy.android"))

    @Test fun `monzo is sensitive`() =
        assertEquals(AppCategory.SENSITIVE, AppCategorisation.classify("co.uk.monzo"))

    @Test fun `bitwarden is sensitive`() =
        assertEquals(AppCategory.SENSITIVE, AppCategorisation.classify("com.x8bit.bitwarden"))

    @Test fun `random package is unknown`() =
        assertEquals(AppCategory.UNKNOWN, AppCategorisation.classify("org.example.unknown"))

    @Test fun `system services are system`() =
        assertEquals(AppCategory.SYSTEM, AppCategorisation.classify("com.android.systemui"))

    @Test fun `blank input is unknown`() =
        assertEquals(AppCategory.UNKNOWN, AppCategorisation.classify(""))
}
