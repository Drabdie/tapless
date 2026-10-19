package com.aiphoneagent.vision

import com.aiphoneagent.common.ScreenSnapshot
import com.aiphoneagent.common.UiNode
import com.aiphoneagent.common.UiRect
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VisionUnderstandingTest {

    private fun snapshot(): ScreenSnapshot {
        val root = UiNode(
            text = null,
            className = "android.widget.FrameLayout",
            children = listOf(
                UiNode(text = "Sign in", resourceId = "com.x:id/title", className = "android.widget.TextView"),
                UiNode(
                    text = "Continue",
                    resourceId = "com.x:id/continue",
                    className = "android.widget.Button",
                    clickable = true,
                    bounds = UiRect(40, 900, 680, 1000),
                ),
            ),
        )
        return ScreenSnapshot(packageName = "com.x", root = root, timestamp = 1L, width = 1080, height = 1920)
    }

    @Test fun parsesStructuredVisionJson() {
        val raw = """
            {
              "summary": "A login screen with a Continue button",
              "elements": [
                {"label": "Continue", "type": "button", "x": 40, "y": 900, "w": 640, "h": 100,
                 "confidence": 0.94, "actionable": true},
                {"label": "Logo", "type": "image", "x": 400, "y": 200, "w": 280, "h": 280,
                 "confidence": 0.8, "actionable": false}
              ]
            }
        """.trimIndent()

        val analysis = VisionAnalyzer.parse(raw, provider = "openai", model = "gpt-4o")

        assertThat(analysis.usedVision).isTrue()
        assertThat(analysis.summary).contains("login screen")
        assertThat(analysis.elements).hasSize(2)
        val button = analysis.elements.first()
        assertThat(button.label).isEqualTo("Continue")
        assertThat(button.actionable).isTrue()
        assertThat(button.centerX).isEqualTo(360)
        assertThat(button.centerY).isEqualTo(950)
    }

    @Test fun toleratesNonJsonOutput() {
        val analysis = VisionAnalyzer.parse("I cannot see a screen right now.", "anthropic", "claude")
        assertThat(analysis.usedVision).isFalse()
        assertThat(analysis.elements).isEmpty()
        assertThat(analysis.summary).contains("cannot see")
    }

    @Test fun extractsJsonEmbeddedInProse() {
        val raw = "Here is the JSON:\n{\"summary\":\"ok\",\"elements\":[]}\nThanks!"
        val analysis = VisionAnalyzer.parse(raw, "gemini", "gemini-1.5-pro")
        assertThat(analysis.usedVision).isTrue()
        assertThat(analysis.summary).isEqualTo("ok")
    }

    @Test fun mergesTreeAndVisionText() {
        val analysis = VisionAnalysis(
            summary = "Login screen",
            elements = listOf(
                VisionElement(label = "Continue", type = "button", x = 40, y = 900, w = 640, h = 100, actionable = true),
            ),
            rawText = "{}",
            provider = "openai",
            model = "gpt-4o",
        )

        val understanding = ScreenUnderstandingBuilder.build(snapshot(), analysis)

        assertThat(understanding.usedVision).isTrue()
        assertThat(understanding.packageName).isEqualTo("com.x")
        assertThat(understanding.treeText).contains("Continue")
        assertThat(understanding.visionText).contains("Login screen")
        assertThat(understanding.mergedText).contains("ACCESSIBILITY TREE:")
        assertThat(understanding.mergedText).contains("VISION ANALYSIS:")
        assertThat(understanding.elements).hasSize(1)
    }

    @Test fun treeOnlyWhenVisionDisabled() {
        val disabled = VisionAnalysis(
            summary = "no vision",
            elements = emptyList(),
            rawText = "",
            provider = "x",
            model = "y",
            usedVision = false,
        )
        val understanding = ScreenUnderstandingBuilder.build(snapshot(), disabled)
        assertThat(understanding.usedVision).isFalse()
        assertThat(understanding.visionText).isEmpty()
        assertThat(understanding.mergedText).doesNotContain("VISION ANALYSIS:")
        assertThat(understanding.mergedText).contains("Continue")
    }

    @Test fun findsActionableVisionElementByLabel() {
        val analysis = VisionAnalysis(
            summary = "",
            elements = listOf(
                VisionElement(label = "Cancel", type = "button", x = 0, y = 0, w = 100, h = 50, actionable = true),
                VisionElement(label = "Continue", type = "button", x = 40, y = 900, w = 640, h = 100, actionable = true),
                VisionElement(label = "Continue", type = "text", x = 0, y = 0, w = 10, h = 10, actionable = false),
            ),
            rawText = "{}",
            provider = "openai",
            model = "gpt-4o",
        )

        val match = ScreenUnderstandingBuilder.findVisionElement(analysis, "continue")
        assertThat(match).isNotNull()
        assertThat(match!!.actionable).isTrue()
        assertThat(match.centerX).isEqualTo(360)
    }

    @Test fun findVisionElementReturnsNullForBlankQuery() {
        assertThat(ScreenUnderstandingBuilder.findVisionElement(null, "x")).isNull()
        val analysis = VisionAnalysis("", emptyList(), "", "p", "m")
        assertThat(ScreenUnderstandingBuilder.findVisionElement(analysis, "  ")).isNull()
    }
}
