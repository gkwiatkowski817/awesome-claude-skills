package com.whatsappsuggester.service

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.whatsappsuggester.api.ChatMessage

class WhatsAppAccessibilityService : AccessibilityService() {

    companion object {
        const val WHATSAPP_PKG = "com.whatsapp"
        const val WHATSAPP_BUSINESS_PKG = "com.whatsapp.w4b"

        private const val TEXT_INPUT_ID  = "com.whatsapp:id/entry"
        private const val TEXT_INPUT_ID2 = "com.whatsapp:id/conversation_entry"
        private const val MSG_LIST_ID    = "com.whatsapp:id/conversation_recycler_view"
        private const val TITLE_ID       = "com.whatsapp:id/conversation_contact_name"

        @Volatile var instance: WhatsAppAccessibilityService? = null
        @Volatile var currentPackage: String = ""

        fun isRunning() = instance != null

        fun isWhatsAppActive() =
            currentPackage == WHATSAPP_PKG || currentPackage == WHATSAPP_BUSINESS_PKG
    }

    private var savedContactName = "contact"

    override fun onServiceConnected() {
        instance = this
        Log.d("WASuggester", "Accessibility connected")
    }

    override fun onDestroy() {
        instance = null
        currentPackage = ""
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            currentPackage = event.packageName?.toString() ?: ""
        }
    }

    /** Must be called from main thread — reads WhatsApp UI tree */
    fun collectMessages(): Triple<List<ChatMessage>, String, String> {
        val root = rootInActiveWindow
            ?: return Triple(emptyList(), savedContactName, "rootInActiveWindow=null (usługa dostępności nie ma dostępu do okna)")

        val messages = mutableListOf<ChatMessage>()
        var diagnostics = ""

        try {
            val contactName = getContactName(root).also {
                if (it.isNotEmpty()) savedContactName = it
            }.ifEmpty { savedContactName }

            val msgList = findNodeById(root, MSG_LIST_ID)
                ?: findByClass(root, "RecyclerView").firstOrNull()

            if (msgList == null) {
                diagnostics = "Nie znaleziono listy wiadomości (RecyclerView). Upewnij się że jesteś w rozmowie."
            } else {
                val count = msgList.childCount
                diagnostics = "Znaleziono kontener z $count elementami"
                for (i in 0 until count) {
                    val child = msgList.getChild(i) ?: continue
                    val text = extractText(child)
                    if (text.isNotEmpty()) {
                        messages.add(ChatMessage(
                            sender = if (isOutgoing(child)) "Me" else contactName,
                            text = text,
                            isMe = isOutgoing(child)
                        ))
                    }
                    child.recycle()
                }
            }
        } catch (e: Exception) {
            diagnostics = "Błąd odczytu: ${e.message}"
            Log.e("WASuggester", "collectMessages error", e)
        }

        root.recycle()
        return Triple(messages, savedContactName, diagnostics)
    }

    /** Must be called from main thread */
    fun fillTextInput(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val input = findNodeById(root, TEXT_INPUT_ID)
            ?: findNodeById(root, TEXT_INPUT_ID2)
            ?: findByClass(root, "EditText").firstOrNull()
        val ok = if (input != null) {
            val args = Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } else false
        root.recycle()
        return ok
    }

    private fun getContactName(root: AccessibilityNodeInfo): String {
        val node = findNodeById(root, TITLE_ID) ?: return ""
        val name = node.text?.toString() ?: ""
        node.recycle()
        return name
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
        t in setOf("Delivered", "Read", "Sent", "Today", "Yesterday", "Dzisiaj", "Wczoraj")

    private fun isOutgoing(node: AccessibilityNodeInfo): Boolean {
        val desc = node.contentDescription?.toString() ?: ""
        if (desc.contains("You:", ignoreCase = true)) return true
        val id = node.viewIdResourceName ?: ""
        return id.contains("out", ignoreCase = true)
    }

    private fun findNodeById(root: AccessibilityNodeInfo, id: String) =
        root.findAccessibilityNodeInfosByViewId(id)?.firstOrNull()

    private fun findByClass(root: AccessibilityNodeInfo, cls: String): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun scan(n: AccessibilityNodeInfo) {
            if (n.className?.toString()?.contains(cls, true) == true) result.add(n)
            for (i in 0 until n.childCount) { val c = n.getChild(i) ?: continue; scan(c) }
        }
        scan(root)
        return result
    }
}
