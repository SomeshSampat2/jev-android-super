package com.jevfast.control.a11y

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

const val NAME_MAX_CHARS = 80
const val MAX_ELEMENTS = 40
const val CONTROL_APP_PKG = "com.jevfast.control"

/** One interactive on-screen element offered to Jev as a candidate target. */
class El(
    val index: Int,
    val name: String,
    val id: String,
    val cls: String,
    val cx: Int,
    val cy: Int,
    val editable: Boolean,
    val focused: Boolean,
    val scrollable: Boolean,
    val node: AccessibilityNodeInfo,
) {
    val key: String get() = "$id|$name|$cls"
    val shortName: String get() = name.take(NAME_MAX_CHARS)
    val ops: List<String> get() = if (editable) listOf("CLICK", "TYPE_TEXT") else listOf("CLICK")
}

class Screen(
    val elements: List<El>,
    val pkg: String,
) {
    /** Cheap screen identity — same construction as the Python fingerprint(). */
    val fingerprint: Int get() = elements.joinToString("|") { it.key }.hashCode()

    fun toState(
        goal: String,
        typedKeys: Set<String>,
        textToType: String?,
        pendingTexts: List<String>,
        recent: List<String>,
        hint: String? = null,
    ): JsonObject = buildJsonObject {
        putJsonObject("current_app") {
            put("package", pkg)
            // Explicit — Jev otherwise has to guess which package is ours,
            // and "the control app's interface" is ambiguous on system screens.
            put("is_control_app", pkg == CONTROL_APP_PKG)
        }
        putJsonArray("screen") {
            elements.take(MAX_ELEMENTS).forEach { el ->
                add(buildJsonObject {
                    put("index", el.index)
                    put("name", el.shortName)
                    put("id", el.id)
                    put("type", el.cls)
                    putJsonArray("at") { add(el.cx); add(el.cy) }
                    put("editable", el.editable)
                    put("focused", el.focused)
                    put("typed", el.key in typedKeys)
                    putJsonArray("ops") { el.ops.forEach { add(it) } }
                })
            }
        }
        put("goal", goal)
        hint?.let { put("hint", it) }
        if (recent.isNotEmpty()) {
            putJsonArray("recent_actions") { recent.takeLast(5).forEach { add(it) } }
        }
        if (elements.size > MAX_ELEMENTS) {
            put("screen_truncated", "showing first $MAX_ELEMENTS of ${elements.size} interactive elements")
        }
        textToType?.let { put("text_to_type", it) }
        if (pendingTexts.isNotEmpty()) {
            putJsonArray("pending_texts") { pendingTexts.forEach { add(it) } }
        }
    }

    /** Combined visible text, bounded — context for the text-generation helper. */
    fun pageText(): String = elements.joinToString(" ") { it.shortName }.take(6000)
}

object ScreenCapture {

    fun capture(service: ControlService): Screen {
        val root = service.rootInActiveWindow
            ?: return Screen(emptyList(), "")
        val elements = mutableListOf<El>()
        walk(root, elements)
        return Screen(elements, root.packageName?.toString() ?: "")
    }

    private fun walk(node: AccessibilityNodeInfo, out: MutableList<El>) {
        if (out.size >= MAX_ELEMENTS * 2) return
        if (node.isVisibleToUser && isInteractive(node)) {
            val name = nameOf(node)
            if (name.isNotBlank() || node.isEditable) {
                val r = Rect()
                node.getBoundsInScreen(r)
                val cls = node.className?.toString()?.substringAfterLast('.') ?: ""
                out += El(
                    index = out.size,
                    name = if (name.isBlank()) "(field)" else name,
                    id = node.viewIdResourceName?.substringAfterLast('/') ?: "",
                    cls = cls,
                    cx = r.centerX(),
                    cy = r.centerY(),
                    editable = node.isEditable,
                    focused = node.isFocused,
                    scrollable = node.isScrollable,
                    node = node,
                )
            }
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { walk(it, out) }
        }
    }

    private fun isInteractive(n: AccessibilityNodeInfo): Boolean =
        n.isClickable || n.isLongClickable || n.isEditable || n.isScrollable ||
            n.isFocusable || n.isCheckable || n.isSelected

    private fun nameOf(node: AccessibilityNodeInfo): String {
        node.text?.let { if (it.isNotBlank()) return it.toString() }
        node.contentDescription?.let { if (it.isNotBlank()) return it.toString() }
        node.hintText?.let { if (it.isNotBlank()) return it.toString() }
        // Aggregate text from non-actionable descendants (bounded depth).
        val texts = mutableListOf<String>()
        collectText(node, texts, depth = 0)
        return texts.joinToString(" ").take(300).trim()
    }

    private fun collectText(n: AccessibilityNodeInfo, out: MutableList<String>, depth: Int) {
        if (depth > 3 || out.size > 8) return
        val v = n.text ?: n.contentDescription ?: n.hintText
        if (v != null && v.isNotBlank()) out += v.toString()
        for (i in 0 until n.childCount) {
            n.getChild(i)?.let { child ->
                if (!(child.isClickable || child.isScrollable || child.isEditable)) {
                    collectText(child, out, depth + 1)
                }
            }
        }
    }
}
