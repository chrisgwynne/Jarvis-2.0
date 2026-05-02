package ai.openclaw.jarvis.network

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dispatcher's main behaviour requires Hilt + an OpenClawClient
 * instance, both of which need Android to instantiate. Until we wire
 * Robolectric, this test pins the parameter-flattening rule that turns
 * OpenClaw's `JsonObject?` params into the legacy `Map<String, String>`
 * shape the executor expects (mirror of
 * [NodeInvokeDispatcher.paramsToStringMap]).
 */
class NodeInvokeDispatcherParamFlatteningTest {

    @Test fun `flatten extracts string primitives`() {
        val obj = buildJsonObject {
            put("contact", JsonPrimitive("Cath"))
            put("number", JsonPrimitive("01234"))
            put("isUrgent", JsonPrimitive(true))
        }
        val result = flatten(obj)
        assertEquals("Cath", result["contact"])
        assertEquals("01234", result["number"])
        assertEquals("true", result["isUrgent"])
    }

    @Test fun `flatten ignores non-primitive values`() {
        val obj = buildJsonObject {
            put("contact", JsonPrimitive("Cath"))
            put("nested", buildJsonObject { put("x", JsonPrimitive("y")) })
        }
        val result = flatten(obj)
        assertEquals("Cath", result["contact"])
        assertTrue("nested object should be skipped", "nested" !in result)
    }

    @Test fun `flatten of null returns empty map`() {
        assertTrue(flatten(null).isEmpty())
    }

    private fun flatten(obj: JsonObject?): Map<String, String> {
        if (obj == null) return emptyMap()
        val out = mutableMapOf<String, String>()
        for ((k, v) in obj) {
            runCatching { v.jsonPrimitive.content }.getOrNull()?.let { out[k] = it }
        }
        return out
    }
}
