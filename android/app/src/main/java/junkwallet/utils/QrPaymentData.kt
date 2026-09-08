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

fun parseQrPaymentData(qrContent: String): QrPaymentData? {
    val trimmed = qrContent.trim()
    if (trimmed.isEmpty()) return null

    if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
        try {
            val json = JSONObject(trimmed)
            val address = json.optString("address", "")
            if (address.isNotBlank()) {
                val hexMemo = json.optString("opreturn_hex", null)
                return QrPaymentData(
                    address = address,
                    amount = json.optString("amount", null),
                    opReturnMemo = hexMemo
                        ?: json.optString("opreturn", null)
                        ?: json.optString("op_return", null)
                        ?: json.optString("memo", null)
                        ?: json.optString("message", null),
                    label = json.optString("label", null),
                    opReturnIsHex = hexMemo != null
                )
            }
        } catch (_: Exception) { }
    }

    val schemeRegex = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):(.*)$")
    val schemeMatch = schemeRegex.matchEntire(trimmed)
    if (schemeMatch != null) {
        val rest = schemeMatch.groupValues[2]
        val address = rest.substringBefore("?")
        val queryString = rest.substringAfter("?", "")
        val params = parseUriQueryString(queryString)

        val hexMemo = params["opreturn_hex"]
        return QrPaymentData(
            address = address,
            amount = params["amount"],
            opReturnMemo = hexMemo
                ?: params["opreturn"]
                ?: params["op_return"]
                ?: params["memo"]
                ?: params["message"],
            label = params["label"],
            opReturnIsHex = hexMemo != null
        )
    }

    return QrPaymentData(address = trimmed)
}

private fun parseUriQueryString(queryString: String): Map<String, String> {
    if (queryString.isBlank()) return emptyMap()
    val result = mutableMapOf<String, String>()
    val pairs = queryString.split("&")
    for (pair in pairs) {
        val idx = pair.indexOf("=")
        if (idx > 0) {
            val key = pair.substring(0, idx).lowercase()
            val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
            result[key] = value
        }
    }
    return result
}
