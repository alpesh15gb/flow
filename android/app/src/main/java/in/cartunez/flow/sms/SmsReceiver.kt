package `in`.cartunez.flow.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/**
 * Receives incoming SMS, parses UPI/bank messages,
 * and broadcasts the result for the UI to confirm.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        for (msg in messages) {
            val body = msg.messageBody ?: continue
            val parsed = SmsParser.parse(body) ?: continue

            // Broadcast to UI so user can confirm
            Intent(ACTION_SMS_PARSED).also {
                it.setPackage(context.packageName)
                it.putExtra(EXTRA_AMOUNT, parsed.amount)
                it.putExtra(EXTRA_TYPE,   parsed.type)
                it.putExtra(EXTRA_NOTE,   parsed.note)
                context.sendBroadcast(it)
            }
        }
    }

    companion object {
        const val ACTION_SMS_PARSED = "in.cartunez.flow.SMS_PARSED"
        const val EXTRA_AMOUNT = "amount"
        const val EXTRA_TYPE   = "type"
        const val EXTRA_NOTE   = "note"
    }
}
