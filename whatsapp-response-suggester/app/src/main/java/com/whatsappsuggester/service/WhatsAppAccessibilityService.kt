package com.whatsappsuggester.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.whatsappsuggester.api.ChatMessage
import com.whatsappsuggester.utils.Prefs

class WhatsAppAccessibilityService : AccessibilityService() {

    companion object {
        const val TAG = "WAAccessibility"
        const val ACTION_MESSAGES_READY = "com.whatsappsuggester.MESSAGES_READY"
        const val EXTRA_MESSAGES = "messages"
        const val EXTRA_CONTACT_NAME = "contact_name"
        const val WHATSAPP_PKG = "com.whatsapp"
        const val WHATSAPP_BUSINESS_PKG = "com.whatsapp.w4b"

        // Resource IDs in WhatsApp (may vary across versions)
        private const val CONVERSATION_TITLE = "com.whatsapp:id/conversation_contact_name"
        private const val MESSAGE_LIST = "com.whatsapp:id/conversation_recycler_view"
        private const val TEXT_INPUT = "com.whatsapp:id/entry"
        private const val TEXT_INPUT_ALT = "com.whatsapp:id/conversation_entry"

        @Volatile
        var instance: WhatsAppAccessibilityService? = null

        fun isRunning(): Boolean = instance != null
    }

    private val prefs by lazy { Prefs(this) }
    private var currentContactName: String = ""
    private var isInConversation = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility service connected")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        if (pkg != WHATSAPP_PKG && pkg != WHATSAPP_BUSINESS_PKG) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                updateConversationState()
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    private fun updateConversationState() {
        val root = rootInActiveWindow ?: return
        val textInput = findTextInput(root)
        val inConversation = textInput != null

        if (inConversation != isInConversation) {
            isInConversation = inConversation
            if (inConversation) {
                currentContactName = getContactName(root)
                notifyOverlayService(show = true)
            } else {
                notifyOverlayService(show = false)
            }
        }
        root.recycle()
    }

    private fun notifyOverlayService(show: Boolean) {
        val intent = Intent(this, OverlayService::class.java)
        intent.action = if (show) OverlayService.ACTION_SHOW else OverlayService.ACTION_HIDE
        startService(intent)
    }

    fun fillTextInput(text: String) {
        val root = rootInActiveWindow ?: return
        val textInput = findTextInput(root)
        if (textInput != null) {
            val args = Bundle()
            args.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text
            )
            textInput.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            textInput.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        }
        root.recycle()
    }

    fun collectMessages(): Pair<List<ChatMessage>, String> {
        val root = rootInActiveWindow ?: return Pair(emptyList(), "")
        val contactName = getContactName(root)
        val messages = mutableListOf<ChatMessage>()

        try {
            val messageList = findNodeById(root, MESSAGE_LIST)
                ?: findNodesByClass(root, "androidx.recyclerview.widget.RecyclerView")
                    .firstOrNull()

            if (messageList != null) {
                collectMessagesFromList(messageList, messages, contactName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting messages", e)
        }

        root.recycle()
        return Pair(messages, contactName)
    }

    private fun collectMessagesFromList(
        node: AccessibilityNodeInfo,
        messages: MutableList<ChatMessage>,
        contactName: String
    ) {
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val text = extractTextFromNode(child)
            if (text.isNotBlank()) {
                val isOutgoing = isOutgoingMessage(child)
                messages.add(ChatMessage(
                    sender = if (isOutgoing) "Me" else contactName,
                    text = text,
                    isMe = isOutgoing
                ))
            }
            child.recycle()
        }
    }

    private fun extractTextFromNode(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        collectTextRecursive(node, sb)
        return sb.toString().trim()
    }

    private fun collectTextRecursive(node: AccessibilityNodeInfo, sb: StringBuilder) {
        val text = node.text?.toString()
        if (!text.isNullOrBlank() && !looksLikeTimestamp(text)) {
            if (sb.isNotEmpty()) sb.append(" ")
            sb.append(text)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectTextRecursive(child, sb)
            child.recycle()
        }
    }

    private fun looksLikeTimestamp(text: String): Boolean {
        return text.matches(Regex("""\d{1,2}:\d{2}(\s*(AM|PM|am|pm))?""")) ||
                text.matches(Regex("""\d{1,2}/\d{1,2}/\d{2,4}""")) ||
                text in setOf("Delivered", "Read", "Sent", "Pending") ||
                text.matches(Regex("""(Yesterday|Today|\w+, \w+ \d+)"""))
    }

    private fun isOutgoingMessage(node: AccessibilityNodeInfo): Boolean {
        val desc = node.contentDescription?.toString() ?: ""
        if (desc.contains("You:", ignoreCase = true) ||
            desc.contains("Ty:", ignoreCase = true)) return true

        // WhatsApp typically places outgoing messages with a specific view ID pattern
        val viewId = node.viewIdResourceName ?: ""
        if (viewId.contains("outgoing", ignoreCase = true)) return true

        // Check layout direction / position heuristic via class names
        val className = node.className?.toString() ?: ""
        return className.contains("out", ignoreCase = true)
    }

    private fun getContactName(root: AccessibilityNodeInfo): String {
        // Try resource ID first
        val nameNode = findNodeById(root, CONVERSATION_TITLE)
        if (nameNode != null) {
            val name = nameNode.text?.toString() ?: ""
            nameNode.recycle()
            if (name.isNotBlank()) return name
        }
        // Fallback: look for the title in the action bar area
        val candidates = findNodesByClass(root, "android.widget.TextView")
        for (node in candidates) {
            val text = node.text?.toString() ?: ""
            node.recycle()
            if (text.isNotBlank() && text.length > 1 && !text.contains(":")) {
                return text
            }
        }
        return currentContactName.ifBlank { "contact" }
    }

    private fun findTextInput(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return findNodeById(root, TEXT_INPUT)
            ?: findNodeById(root, TEXT_INPUT_ALT)
            ?: findNodesByClass(root, "android.widget.EditText").firstOrNull()
    }

    private fun findNodeById(root: AccessibilityNodeInfo, id: String): AccessibilityNodeInfo? {
        val results = root.findAccessibilityNodeInfosByViewId(id)
        return results?.firstOrNull()
    }

    private fun findNodesByClass(
        root: AccessibilityNodeInfo,
        className: String
    ): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()
        findNodesByClassRecursive(root, className, results)
        return results
    }

    private fun findNodesByClassRecursive(
        node: AccessibilityNodeInfo,
        className: String,
        results: MutableList<AccessibilityNodeInfo>
    ) {
        if (node.className?.toString()?.contains(className, ignoreCase = true) == true) {
            results.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findNodesByClassRecursive(child, className, results)
        }
    }
}
