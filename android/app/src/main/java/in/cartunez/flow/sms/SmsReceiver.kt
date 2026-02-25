package `in`.cartunez.flow.sms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import `in`.cartunez.flow.FlowApp
import `in`.cartunez.flow.R
import `in`.cartunez.flow.data.Transaction
import `in`.cartunez.flow.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        for (msg in messages) {
            val body = msg.messageBody ?: continue
            val parsed = SmsParser.parse(body) ?: continue

            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val app = context.applicationContext as FlowApp
                    val tx = Transaction(
                        amount = parsed.amount,
                        type   = parsed.type,
                        note   = parsed.note,
                        date   = LocalDate.now().toString()
                    )
                    app.database.transactionDao().insert(tx)
                    showNotification(context, parsed)
                } finally {
                    pending.finish()
                }
            }
        }
    }

    private fun showNotification(context: Context, parsed: SmsParser.ParsedSms) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create channel (no-op if already exists)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "SMS Transactions", NotificationManager.IMPORTANCE_DEFAULT)
        )

        val tapIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val label = if (parsed.type == "sale") "Sale" else "Expense"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_home)
            .setContentTitle("$label recorded — ₹${"%,.0f".format(parsed.amount)}")
            .setContentText(parsed.note)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(System.currentTimeMillis().toInt(), notification)
    }

    companion object {
        const val ACTION_SMS_PARSED = "in.cartunez.flow.SMS_PARSED"
        private const val CHANNEL_ID = "flow_sms_txns"
    }
}
