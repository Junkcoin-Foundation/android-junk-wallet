package junkwallet.utils

import org.json.JSONObject
import java.net.URLDecoder

data class QrPaymentData(
    val address: String,
    val amount: String? = null,
    val opReturnMemo: String? = null,
    val label: String? = null,
    val opReturnIsHex: Boolean = false
)

/**
 * Parses payment QR payloads.
 *
 * Primary target is the bridge deposit QR (JKC-Multisig-mpc-bridge):
 *
 *   junkcoin:<deposit_address>?amount=<coins>&opreturn=<40hex>&opreturn_hex=<40hex>
 *
 * where `opreturn`/`opreturn_hex` carry the RAW 20-byte EVM routing payload
 * (40 hex chars, no `0x`, no OP_RETURN opcodes). Also accepts JSON payloads,
 * BIP21-style plain URIs and bare addresses.
 */
fun parseQrPaymentData(qrContent: String): QrPaymentData? {
    val trimmed = qrContent.trim()
    if (trimmed.isEmpty()) return null

    return try {
        parseInternal(trimmed)
    } catch (_: Exception) {
        // Never let a malformed QR crash the scanner callback — fall back to raw text.
        if (looksLikeAddress(trimmed)) QrPaymentData(address = trimmed) else null
    }
}

private fun parseInternal(trimmed: String): QrPaymentData? {
    if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
        try {
            val json = JSONObject(trimmed)
            val address = json.optString("address", "")
            if (address.isNotBlank()) {
                val raw = json.optString("opreturn_hex", null)
                    ?: json.optString("opreturn", null)
                    ?: json.optString("op_return", null)
                    ?: json.optString("memo", null)
                    ?: json.optString("message", null)
                val memo = normalizeOpReturn(raw)
                return QrPaymentData(
                    address = address,
                    amount = json.optString("amount", null)?.ifBlank { null },
                    opReturnMemo = memo.hex,
                    label = json.optString("label", null)?.ifBlank { null },
                    opReturnIsHex = memo.isHex
                )
            }
        } catch (_: Exception) { }
    }

    val schemeRegex = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):(.*)$")
    val schemeMatch = schemeRegex.matchEntire(trimmed)
    if (schemeMatch != null) {
        val rest = schemeMatch.groupValues[2].removePrefix("//")
        val address = rest.substringBefore("?").trim()
        val queryString = rest.substringAfter("?", "")
        val params = parseUriQueryString(queryString)

        // Prefer the explicit `opreturn_hex`, but `opreturn` on its own is hex in
        // the bridge QR — detect that instead of blindly UTF-8 encoding it.
        val memo = normalizeOpReturn(params["opreturn_hex"] ?: params["opreturn"]
            ?: params["op_return"] ?: params["memo"] ?: params["message"])

        if (address.isBlank()) return null

        return QrPaymentData(
            address = address,
            amount = params["amount"]?.ifBlank { null },
            opReturnMemo = memo.hex,
            label = params["label"]?.ifBlank { null },
            opReturnIsHex = memo.isHex
        )
    }

    if (looksLikeAddress(trimmed)) return QrPaymentData(address = trimmed)
    return null
}

private data class NormalizedMemo(val hex: String?, val isHex: Boolean)

/**
 * Normalises OP_RETURN data to a plain hex payload:
 *  - strips `0x` prefixes
 *  - strips a leading `6a` / `6a14` OP_RETURN script (avoids double-encoding,
 *    which the bridge relayer cannot decode)
 *  - accepts hex or falls back to UTF-8 -> hex
 *  - enforces the 80-byte OP_RETURN limit
 */
private fun normalizeOpReturn(raw: String?): NormalizedMemo {
    if (raw.isNullOrBlank()) return NormalizedMemo(null, false)
    var value = raw.trim()

    if (value.startsWith("0x", ignoreCase = true)) value = value.substring(2)
    value = value.replace("\\s".toRegex(), "")

    // ScriptPubKey forms of the 40-char bridge payload. Only stripped when the
    // total length proves it is a script (a genuine 40-hex payload is untouched,
    // even if it happens to start with "6a").
    val lower = value.lowercase()
    when {
        value.length == 44 && lower.startsWith("6a14") -> value = value.substring(4)
        // double-encoded: 6a 16 6a 14 <payload> — the relayer cannot decode this
        value.length == 48 && lower.startsWith("6a166a14") -> value = value.substring(8)
        value.length == 2 && lower == "6a" -> return NormalizedMemo(null, false)
    }

    val isHex = value.isNotEmpty() && value.length % 2 == 0 && value.all { it.isHexDigit() }
    val hex = if (isHex) {
        val capped = if (value.length > MAX_OP_RETURN_BYTES * 2) value.substring(0, MAX_OP_RETURN_BYTES * 2) else value
        capped.lowercase()
    } else {
        // Not hex — treat as UTF-8 text and hex-encode it (same as before).
        val bytes = value.toByteArray(Charsets.UTF_8)
        val capped = if (bytes.size > MAX_OP_RETURN_BYTES) bytes.copyOf(MAX_OP_RETURN_BYTES) else bytes
        capped.joinToString("") { "%02x".format(it) }
    }

    return NormalizedMemo(hex.ifBlank { null }, isHex)
}

private fun Char.isHexDigit(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

private fun looksLikeAddress(value: String): Boolean =
    value.length in 26..128 && !value.contains(" ") && value.all {
        it.isLetterOrDigit()
    }

private fun parseUriQueryString(queryString: String): Map<String, String> {
    if (queryString.isBlank()) return emptyMap()
    val result = mutableMapOf<String, String>()
    val pairs = queryString.split("&")
    for (pair in pairs) {
        val idx = pair.indexOf("=")
        if (idx > 0) {
            val key = pair.substring(0, idx).lowercase()
            val rawValue = pair.substring(idx + 1)
            val value = try {
                URLDecoder.decode(rawValue, "UTF-8")
            } catch (_: Exception) {
                rawValue
            }
            result[key] = value
        }
    }
    return result
}

private const val MAX_OP_RETURN_BYTES = 80
