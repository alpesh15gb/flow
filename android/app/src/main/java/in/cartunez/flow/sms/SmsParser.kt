package `in`.cartunez.flow.sms

/**
 * Parses UPI/bank SMS messages to extract transaction details.
 * Handles patterns from major Indian banks and UPI apps.
 */
object SmsParser {

    data class ParsedSms(val amount: Double, val type: String, val note: String)

    // Matches "Rs.1,234.56", "INR 1234", "Rs 500", "₹1,500"
    private val AMOUNT_RE = Regex(
        """(?:Rs\.?|INR|₹)\s*([\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    // Fallback: bare number when no currency prefix (e.g. "received 5000 from imps")
    private val AMOUNT_BARE_RE = Regex(
        """\b([\d,]+(?:\.\d{1,2})?)\b"""
    )

    // UPI credit keywords
    private val CREDIT_RE = Regex(
        """\b(credited|received|credit|deposited|added|refund)\b""",
        RegexOption.IGNORE_CASE
    )

    // UPI debit keywords
    private val DEBIT_RE = Regex(
        """\b(debited|paid|deducted|spent|withdrawn|payment)\b""",
        RegexOption.IGNORE_CASE
    )

    // UPI reference / VPA / UTR
    private val UPI_REF_RE = Regex(
        """(?:UPI|VPA|UTR\s*(?:no\.?)?|Ref(?:No)?\.?)\s*:?\s*([A-Za-z0-9@.\-_]+)""",
        RegexOption.IGNORE_CASE
    )

    // Sender name for credit: "from KEYSTONE I;" or "from john@upi"
    private val CREDIT_PARTY_RE = Regex(
        """\bfrom\s+(.+?)(?=\s*[;,]|\s+UTR|\s+on\s+\d|\s+via\b|\s*$)""",
        RegexOption.IGNORE_CASE
    )

    // Recipient for debit: "sent to bharatpe@yes" / "paid to john@upi" / "transferred to X"
    private val DEBIT_PARTY_RE = Regex(
        """\b(?:sent\s+to|paid\s+to|transferred\s+to)\s+(\S+)""",
        RegexOption.IGNORE_CASE
    )

    // Known bank / UPI sender patterns
    private val BANK_KEYWORDS = listOf("upi", "imps", "neft", "rtgs", "bank", "paytm", "phonepe", "gpay", "google pay", "bhim")

    fun parse(body: String): ParsedSms? {
        val lower = body.lowercase()

        // Must look like a financial SMS
        val isFinancial = BANK_KEYWORDS.any { lower.contains(it) }
                || AMOUNT_RE.containsMatchIn(body)
        if (!isFinancial) return null

        val amountMatch = AMOUNT_RE.find(body)
            ?: AMOUNT_BARE_RE.find(body)
            ?: return null
        val amount = amountMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        if (amount <= 0) return null

        val isCredit = CREDIT_RE.containsMatchIn(body)
        val isDebit  = DEBIT_RE.containsMatchIn(body)

        val type = when {
            isCredit -> "sale"
            isDebit  -> "expense"
            else     -> return null   // ambiguous — skip
        }

        val party = when (type) {
            "sale"    -> CREDIT_PARTY_RE.find(body)?.groupValues?.getOrNull(1)?.trim()
            "expense" -> DEBIT_PARTY_RE.find(body)?.groupValues?.getOrNull(1)
                            ?.trimEnd('.', ',', ';')
            else      -> null
        }
        val ref = UPI_REF_RE.find(body)?.groupValues?.getOrNull(1)

        val note = when {
            type == "sale"    && party != null -> "From $party"
            type == "expense" && party != null -> "To $party"
            type == "sale"                     -> "Received${if (ref != null) " ($ref)" else ""}"
            else                               -> "Paid${if (ref != null) " ($ref)" else ""}"
        }

        return ParsedSms(amount, type, note)
    }
}
