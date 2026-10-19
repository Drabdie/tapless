package com.aiphoneagent.accessibility

import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.aiphoneagent.common.ElementSelector
import com.aiphoneagent.common.FoundTarget
import com.aiphoneagent.common.NodeSource
import com.aiphoneagent.common.UiRect

class LiveNodeSource : NodeSource {
    override fun packageName(): String? = withRoot { it.packageName?.toString() }

    override fun centerOf(selector: ElementSelector): FoundTarget? {
        return withMatch(selector, editableOnly = false) { node ->
            val rect = Rect()
            node.getBoundsInScreen(rect)
            FoundTarget(UiRect(rect.left, rect.top, rect.right, rect.bottom), node.text?.toString())
        }
    }

    override fun setText(selector: ElementSelector, text: String): Boolean {
        if (withMatch(selector, editableOnly = true) { applyText(it, text) } == true) return true
        if (selector !is ElementSelector.ByFocusedEditable) {
            withMatch(selector, editableOnly = false) { node ->
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                true
            }
        }
        if (withMatch(ElementSelector.ByFocusedEditable, editableOnly = true) { applyText(it, text) } == true) {
            return true
        }
        return withFirstEditable { applyText(it, text) } == true
    }

    override fun scroll(selector: ElementSelector, forward: Boolean): Boolean {
        val action = if (forward) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        val targeted = withMatch(selector, editableOnly = false) { node ->
            if (node.isScrollable) node.performAction(action) else false
        }
        if (targeted == true) return true
        return withFirstScrollable { it.performAction(action) } == true
    }

    override fun imeEnter(): Boolean {
        if (Build.VERSION.SDK_INT < 30) return false
        return withFirstEditable { node ->
            node.performAction(0x000000A6 /* ACTION_IME_ENTER API 30 */)
        } == true
    }

    private fun applyText(node: AccessibilityNodeInfo, text: String): Boolean {
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun <T> withRoot(block: (AccessibilityNodeInfo) -> T): T? {
        val root = AccessibilityBridge.service?.rootInActiveWindow ?: return null
        return try {
            block(root)
        } finally {
            root.recycle()
        }
    }

    private fun <T> withMatch(selector: ElementSelector, editableOnly: Boolean, block: (AccessibilityNodeInfo) -> T): T? {
        return visitRoots { root ->
            val found = find(root, selector, editableOnly) ?: return@visitRoots null
            try {
                block(found)
            } finally {
                if (found !== root) found.recycle()
            }
        }
    }

    private fun <T> withFirstEditable(block: (AccessibilityNodeInfo) -> T): T? {
        return visitRoots { root ->
            val found = findFirst(root) { it.isEditable } ?: return@visitRoots null
            try {
                block(found)
            } finally {
                if (found !== root) found.recycle()
            }
        }
    }

    private fun <T> withFirstScrollable(block: (AccessibilityNodeInfo) -> T): T? {
        return visitRoots { root ->
            val found = findFirst(root) { it.isScrollable } ?: return@visitRoots null
            try {
                block(found)
            } finally {
                if (found !== root) found.recycle()
            }
        }
    }

    private fun <T> visitRoots(block: (AccessibilityNodeInfo) -> T?): T? {
        val service = AccessibilityBridge.service ?: return null
        val roots = ArrayList<AccessibilityNodeInfo>()
        service.windows?.forEach { window -> window.root?.let(roots::add) }
        if (roots.isEmpty()) service.rootInActiveWindow?.let(roots::add)
        try {
            for (root in roots) {
                val value = block(root)
                if (value != null) return value
            }
            return null
        } finally {
            roots.forEach { runCatching { it.recycle() } }
        }
    }

    private fun find(node: AccessibilityNodeInfo, selector: ElementSelector, editableOnly: Boolean): AccessibilityNodeInfo? {
        if (matches(node, selector) && (!editableOnly || node.isEditable)) return node
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val found = find(child, selector, editableOnly)
            if (found != null) {
                if (found !== child) child.recycle()
                return found
            }
            child.recycle()
        }
        if (selector is ElementSelector.ByFocusedEditable && node.isEditable) return node
        return null
    }

    private fun findFirst(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val found = findFirst(child, predicate)
            if (found != null) {
                if (found !== child) child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    private fun matches(node: AccessibilityNodeInfo, selector: ElementSelector): Boolean = when (selector) {
        is ElementSelector.ByText -> {
            val value = node.text?.toString().orEmpty()
            selector.text.isNotBlank() && if (selector.exact) {
                value.equals(selector.text, ignoreCase = true)
            } else {
                value.contains(selector.text, ignoreCase = true)
            }
        }
        is ElementSelector.ByResourceId -> {
            val full = node.viewIdResourceName.orEmpty()
            val short = full.substringAfter(":id/", full)
            short.equals(selector.id, ignoreCase = true) || full.endsWith(selector.id, ignoreCase = true)
        }
        is ElementSelector.ByContentDescription -> {
            val value = node.contentDescription?.toString().orEmpty()
            selector.desc.isNotBlank() && value.contains(selector.desc, ignoreCase = true)
        }
        is ElementSelector.ByCoordinates -> {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            selector.x in rect.left..rect.right && selector.y in rect.top..rect.bottom
        }
        is ElementSelector.ByAnyText -> selector.options.any { option ->
            option.isNotBlank() && (
                node.text?.toString()?.contains(option, ignoreCase = true) == true ||
                    node.contentDescription?.toString()?.contains(option, ignoreCase = true) == true
                )
        }
        ElementSelector.ByFocusedEditable -> node.isEditable && (node.isFocused || node.isAccessibilityFocused)
    }
}
