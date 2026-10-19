package com.aiphoneagent.vision

/** Prompts used to turn a screenshot into structured UI understanding. */
object VisionPrompts {
    const val SYSTEM: String = """You are the vision module of an Android automation agent.
You receive a screenshot of a phone screen and must describe what is on it precisely
enough that another agent can decide where to tap.

Return ONLY a JSON object (no markdown, no prose) with this exact shape:
{
  "summary": "one or two sentences describing the current screen and what the user can do",
  "elements": [
    {
      "label": "visible text of the element",
      "type": "button|input|text|icon|list_item|image|tab|toggle|link",
      "x": 0, "y": 0, "w": 0, "h": 0,
      "confidence": 0.0,
      "actionable": true
    }
  ]
}

Rules:
- Coordinates are pixels in the ORIGINAL screenshot (top-left origin).
- Include every tappable control (buttons, tabs, list rows, icons) and every input field.
- "actionable" is true only for elements the user can tap or type into.
- Never invent elements that are not visible. If unsure, omit them.
- Never include passwords, one-time codes, card numbers or other secrets in "label".
"""

    fun userPrompt(task: String?): String = buildString {
        append("Analyse this screenshot.")
        if (!task.isNullOrBlank()) {
            append(" The user is trying to: \"")
            append(task.take(300))
            append("\". Highlight the elements relevant to that goal.")
        }
    }
}
