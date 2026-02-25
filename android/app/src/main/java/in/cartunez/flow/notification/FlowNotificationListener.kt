package `in`.cartunez.flow.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import `in`.cartunez.flow.FlowApp
import `in`.cartunez.flow.R
import `in`.cartunez.flow.data.Transaction
import `in`.cartunez.flow.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Listens to WhatsApp notifications, detects financial amounts, and saves directly to DB.
 */
class FlowNotificationListener : NotificationListenerService() {

    private val CREDIT_KEYWORDS = setOf("received", "paid me", "sent you", "transferred to you", "payment received")
    private val DEBIT_KEYWORDS  = setOf("paid", "sent", "transferred", "invoice", "bill")
    private val WA_PACKAGES     = setOf("com.whatsapp", "com.whatsapp.w4b")

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in WA_PACKAGES) return

        val extras = sbn.notification?.extras ?: return
        val text = extras.getCharSequence("android.text")?.toString()?.lowercase() ?: return

        val parsed = parseWhatsApp(text) ?: return

        CoroutineScope(Dispatchers.IO).launch {
            val app = applicationContext as FlowApp
            val tx = Transaction(
                amount = parsed.amount,
                type   = parsed.type,
                note   = parsed.note,
                date   = LocalDate.now().toString()
            )
            app.database.transactionDao().insert(tx)
            showNotification(parsed)
        }
    }

    private fun parseWhatsApp(text: String): ParsedWA? {
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

    private fun showNotification(parsed: ParsedWA) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "WhatsApp Transactions", NotificationManager.IMPORTANCE_DEFAULT)
        )

        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val label = if (parsed.type == "sale") "Sale" else "Expense"
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_home)
            .setContentTitle("$label recorded — ₹${"%.0f".format(parsed.amount)}")
            .setContentText(parsed.note)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(System.currentTimeMillis().toInt(), notification)
    }

    data class ParsedWA(val amount: Double, val type: String, val note: String)

    override fun onNotificationRemoved(sbn: StatusBarNotification) { /* no-op */ }

    companion object {
        const val ACTION_WA_PARSED = "in.cartunez.flow.WA_PARSED"
        private const val CHANNEL_ID = "flow_wa_txns"
    }
}
