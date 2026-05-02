package ai.openclaw.jarvis.awareness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AwarenessQuestionDetectorTest {

    @Test fun `what can you do is recognised`() {
        assertTrue(AwarenessQuestionDetector.detect("hey jarvis what can you do") is AwarenessQuestion.WhatCanYouDo)
        assertTrue(AwarenessQuestionDetector.detect("Tell me what you can do") is AwarenessQuestion.WhatCanYouDo)
    }

    @Test fun `can you whatsapp is matched`() {
        val q = AwarenessQuestionDetector.detect("can you send whatsapp to Cath")
        assertEquals(AwarenessQuestion.CanYou(AwarenessQuestion.Topic.WHATSAPP), q)
    }

    @Test fun `can you email is matched`() {
        val q = AwarenessQuestionDetector.detect("can you email Dave")
        assertEquals(AwarenessQuestion.CanYou(AwarenessQuestion.Topic.EMAIL), q)
    }

    @Test fun `can you see my screen is matched`() {
        val q = AwarenessQuestionDetector.detect("can you see my screen right now")
        assertEquals(AwarenessQuestion.CanYou(AwarenessQuestion.Topic.SCREEN), q)
    }

    @Test fun `can you use my location is matched`() {
        val q = AwarenessQuestionDetector.detect("can you use my location")
        assertEquals(AwarenessQuestion.CanYou(AwarenessQuestion.Topic.LOCATION), q)
    }

    @Test fun `can you access openclaw is matched`() {
        val q = AwarenessQuestionDetector.detect("can you access openclaw")
        assertEquals(AwarenessQuestion.CanYou(AwarenessQuestion.Topic.OPEN_CLAW), q)
    }

    @Test fun `what permissions are missing is matched`() {
        assertTrue(AwarenessQuestionDetector.detect("what permissions are missing") is AwarenessQuestion.MissingPermissions)
    }

    @Test fun `why cant you is matched`() {
        assertTrue(AwarenessQuestionDetector.detect("why can't you do that") is AwarenessQuestion.WhyCantYouDoThat)
    }

    @Test fun `imperative commands are not awareness questions`() {
        assertNull(AwarenessQuestionDetector.detect("send Cath a message"))
        assertNull(AwarenessQuestionDetector.detect("call Mum"))
        assertNull(AwarenessQuestionDetector.detect("open Spotify"))
    }
}
