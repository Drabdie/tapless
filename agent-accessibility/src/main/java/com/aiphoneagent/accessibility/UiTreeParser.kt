package com.aiphoneagent.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.aiphoneagent.common.UiNode
import com.aiphoneagent.common.UiRect
import com.aiphoneagent.common.UiTreeLimiter
import javax.inject.Inject

class UiTreeParser @Inject constructor() {
    fun parse(root: AccessibilityNodeInfo): UiNode {
        var count = 0
        fun walk(node: AccessibilityNodeInfo, depth: Int): UiNode? {
            if (depth > UiTreeLimiter.MAX_DEPTH || count >= UiTreeLimiter.MAX_NODES) return null
            val rect = Rect()
            node.getBoundsInScreen(rect)
            val children = ArrayList<UiNode>(node.childCount)
            for (index in 0 until node.childCount) {
                if (count >= UiTreeLimiter.MAX_NODES) break
                val child = node.getChild(index) ?: continue
                try {
                    walk(child, depth + 1)?.let(children::add)
                } finally {
                    child.recycle()
                }
            }
            val visible = node.isVisibleToUser
            if (!visible && children.isEmpty()) return null
            if (rect.width() <= 0 && rect.height() <= 0 && children.isEmpty()) return null
            count += 1
            val fullId = node.viewIdResourceName
            return UiNode(
                text = node.text?.toString(),
                contentDescription = node.contentDescription?.toString(),
                className = node.className?.toString(),
                resourceId = fullId?.substringAfter(":id/", fullId),
                bounds = UiRect(rect.left, rect.top, rect.right, rect.bottom),
                clickable = node.isClickable,
                scrollable = node.isScrollable,
                enabled = node.isEnabled,
                visible = visible,
                focused = node.isFocused,
                editable = node.isEditable,
                password = node.isPassword,
                children = children,
            )
        }
        return walk(root, 0) ?: UiNode()
    }
}
