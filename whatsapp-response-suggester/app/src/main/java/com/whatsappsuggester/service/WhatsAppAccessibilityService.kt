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
        const val WHATSAPP_PKG = "com.whatsapp"
        const val WHATSAPP_BUSINESS_PKG = "com.whatsapp.w4b"

        private const val TEXT_INPUT_ID = "com.whatsapp:id/entry"
        private const val TEXT_INPUT_ID2 = "com.whatsapp:id/conversation_entry"
        private const val MSG_LIST_ID = "com.whatsapp:id/conversation_recycler_view"
        private const val TITLE_ID = "com.whatsapp:id/conversation_contact_name"

        @Volatile
        var instance: WhatsAppAccessibilityService? = null

        fun isRunning(): Boolean = instance != null
    }

    private val prefs by lazy { Prefs(this) }
    private var currentContactName: String = "contact"
    private var whatsAppVisible = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility service connected")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val isWhatsApp = (pkg == WHATSAPP_PKG || pkg == WHATSAPP_BUSINESS_PKG)
                if (isWhatsApp && !whatsAppVisible) {
                    // Entered WhatsApp
                    whatsAppVisible = true
                    checkAndShowOverlay()
                } else if (!isWhatsApp && whatsAppVisible) {
                    // Left WhatsApp
                    whatsAppVisible = false
                    sendOverlayCommand(false)
                }
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (pkg == WHATSAPP_PKG || pkg == WHATSAPP_BUSINESS_PKG) {
                    checkAndShowOverlay()
                }
            }
        }
    }

    private fun checkAndShowOverlay() {
        val root = rootInActiveWindow ?: return
        val hasInput = findTextInput(root) != null
        root.recycle()
        sendOverlayCommand(hasInput)
    }

    private fun sendOverlayCommand(show: Boolean) {
        val intent = Intent(this, OverlayService::class.java)
        intent.action = if (show) OverlayService.ACTION_SHOW else OverlayService.ACTION_HIDE
        startService(intent)
    }

    // Called from main thread by OverlayService button click
    fun collectMessages(): Pair<List<ChatMessage>, String> {
        val root = rootInActiveWindow ?: return Pair(emptyList(), currentContactName)
        val messages = mutableListOf<ChatMessage>()
        try {
            val contactName = getContactName(root)
            if (contactName.isNotEmpty()) currentContactName = contactName

            val msgList = findNodeById(root, MSG_LIST_ID)
                ?: findNodesByClass(root, "RecyclerView").firstOrNull()
                ?: findNodesByClass(root, "ListView").firstOrNull()

            if (msgList != null) {
                for (i in 0 until msgList.childCount) {
                    val child = msgList.getChild(i) ?: continue
                    val text = extractText(child)
                    if (text.isNotEmpty()) {
                        messages.add(ChatMessage(
                            sender = if (isOutgoing(child)) "Me" else currentContactName,
                            text = text,
                            isMe = isOutgoing(child)
                        ))
                    }
                    child.recycle()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "collectMessages failed", e)
        }
        root.recycle()
        return Pair(messages, currentContactName)
    }

    fun fillTextInput(text: String) {
        val root = rootInActiveWindow ?: return
        val input = findTextInput(root)
        if (input != null) {
            val args = Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
        root.recycle()
    }

    private fun getContactName(root: AccessibilityNodeInfo): String {
        val node = findNodeById(root, TITLE_ID)
        if (node != null) {
            val name = node.text?.toString() ?: ""
            node.recycle()
            if (name.isNotBlank()) return name
        }
        return ""
    }

    private fun findTextInput(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return findNodeById(root, TEXT_INPUT_ID)
            ?: findNodeById(root, TEXT_INPUT_ID2)
            ?: findNodesByClass(root, "EditText").firstOrNull()
    }

    private fun extractText(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        collectText(node, sb)
        return sb.toString().trim()
    }

    private fun collectText(node: AccessibilityNodeInfo, sb: StringBuilder) {
        val t = node.text?.toString()
        if (!t.isNullOrBlank() && !isTimestamp(t)) {
            if (sb.isNotEmpty()) sb.append(" ")
            sb.append(t)
        }
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            collectText(c, sb)
            c.recycle()
        }
    }

    private fun isTimestamp(t: String) =
        t.matches(Regex("""\d{1,2}:\d{2}(\s*(AM|PM))?""")) ||
        t in setOf("Delivered", "Read", "Sent", "Today", "Yesterday")

    private fun isOutgoing(node: AccessibilityNodeInfo): Boolean {
        val desc = node.contentDescription?.toString() ?: ""
        if (desc.contains("You:", ignoreCase = true)) return true
        val id = node.viewIdResourceName ?: ""
        return id.contains("out", ignoreCase = true)
    }

    private fun findNodeById(root: AccessibilityNodeInfo, id: String): AccessibilityNodeInfo? =
        root.findAccessibilityNodeInfosByViewId(id)?.firstOrNull()

    private fun findNodesByClass(root: AccessibilityNodeInfo, cls: String): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        findByClassRec(root, cls, result)
        return result
    }

    private fun findByClassRec(node: AccessibilityNodeInfo, cls: String, result: MutableList<AccessibilityNodeInfo>) {
        if (node.className?.toString()?.contains(cls, ignoreCase = true) == true) result.add(node)
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            findByClassRec(c, cls, result)
        }
    }
}
