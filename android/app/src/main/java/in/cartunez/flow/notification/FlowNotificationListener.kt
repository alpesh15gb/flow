package `in`.cartunez.flow.notification

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Listens to WhatsApp notifications and detects financial keywords.
 * Only SUGGESTS — never auto-saves. User must confirm.
 */
class FlowNotificationListener : NotificationListenerService() {

    // Keywords that suggest a financial transaction in WhatsApp messages
    private val CREDIT_KEYWORDS = setOf("received", "paid me", "sent you", "transferred to you", "payment received")
    private val DEBIT_KEYWORDS  = setOf("paid", "sent", "transferred", "invoice", "bill")

    // WhatsApp package names
    private val WA_PACKAGES = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b"   // WhatsApp Business
    )

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in WA_PACKAGES) return

        val extras = sbn.notification?.extras ?: return
        val text = extras.getCharSequence("android.text")?.toString()?.lowercase() ?: return

        val parsed = parseWhatsApp(text) ?: return

        // Broadcast to UI for confirmation
        Intent(ACTION_WA_PARSED).also {
            it.setPackage(packageName)
            it.putExtra(EXTRA_AMOUNT, parsed.amount)
            it.putExtra(EXTRA_TYPE,   parsed.type)
            it.putExtra(EXTRA_NOTE,   parsed.note)
            sendBroadcast(it)
        }
    }

    private fun parseWhatsApp(text: String): ParsedWA? {
        // Look for amount pattern (₹100, Rs.500, 1000 rs)
        val amountRe = Regex("""(?:rs\.?|₹|inr)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
        val amountMatch = amountRe.find(text) ?: return null
        val amount = amountMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        if (amount <= 0) return null

        val isCredit = CREDIT_KEYWORDS.any { text.contains(it) }
        val isDebit  = DEBIT_KEYWORDS.any { text.contains(it) }

        val type = when {
            isCredit -> "sale"
            isDebit  -> "expense"
            else     -> return null
        }

        return ParsedWA(amount, type, "WhatsApp: ${if (type == "sale") "Payment received" else "Payment sent"}")
    }

    data class ParsedWA(val amount: Double, val type: String, val note: String)

    override fun onNotificationRemoved(sbn: StatusBarNotification) { /* no-op */ }

    companion object {
        const val ACTION_WA_PARSED = "in.cartunez.flow.WA_PARSED"
        const val EXTRA_AMOUNT = "amount"
        const val EXTRA_TYPE   = "type"
        const val EXTRA_NOTE   = "note"
    }
}
