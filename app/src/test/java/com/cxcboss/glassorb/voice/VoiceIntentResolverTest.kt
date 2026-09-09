package com.cxcboss.glassorb.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceIntentResolverTest {
    @Test
    fun resolvesTorchWithSpeechFillersAndAsrVariants() {
        val result = VoiceIntentResolver.resolve("请帮我打开手电灯")

        assertTrue(result.isExecutable)
        assertEquals(VoiceCommand.Torch(true), result.command)
    }

    @Test
    fun resolvesVolumeAndDoesNotGuessAmbiguousDirection() {
        val increase = VoiceIntentResolver.resolve("把声音调大一点")
        val ambiguous = VoiceIntentResolver.resolve("处理一下音量")

        assertEquals(VoiceCommand.MediaVolume(VoiceCommand.VolumeAction.Raise), increase.command)
        assertFalse(ambiguous.isExecutable)
    }

    @Test
    fun resolvesOnlyKnownAppLaunches() {
        val known = VoiceIntentResolver.resolve("打开微信")
        val unknown = VoiceIntentResolver.resolve("打开相册")

        assertEquals(
            VoiceCommand.LaunchApp("com.tencent.mm", "微信"),
            known.command,
        )
        assertFalse(unknown.isExecutable)
    }
}
