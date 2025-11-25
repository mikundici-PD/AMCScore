package com.amcscore.app

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

data class GameState(
    var teamA_name: String = "Squadra A",
    var teamB_name: String = "Squadra B",
    var teamA_score: Int = 0,
    var teamB_score: Int = 0,
    var teamA_sets: Int = 0,
    var teamB_sets: Int = 0,
    var current_set: Int = 1,
    var teamA_timeouts: Int = 0,
    var teamB_timeouts: Int = 0,
    var teamA_subs: Int = 0,
    var teamB_subs: Int = 0,
    var max_timeouts: Int = 2,
    var max_subs: Int = 6,
    var logoA: String? = null,
    var logoB: String? = null,
    var bgColor: String = "#000000",
    var bgImage: String? = null,
    var sideLeft: String = "A"
)

class SimpleHttpServer(
    private val context: Context,
    port: Int = 8080
) : NanoHTTPD(port) {

    private val states = ConcurrentHashMap<String, GameState>()

    init {
        states["Volley"] = GameState()
        states["Basket"] = GameState()
    }

    private fun getState(sport: String): GameState {
        return states.getOrPut(sport) { GameState() }
    }

    private fun readAsset(path: String): String? {
        return try {
            val input: InputStream = context.assets.open(path)
            input.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        }
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        return try {
            when {
                uri == "/" -> serveHome()
                uri.startsWith("/control/") -> serveControl(uri)
                uri.startsWith("/display/") -> serveDisplay(uri)
                uri.startsWith("/api/state/") -> serveState(uri)
                uri.startsWith("/api/update/") && method == Method.POST -> serveUpdate(session, uri)
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Internal error")
        }
    }

    private fun serveHome(): Response {
        val html = "<!doctype html><html lang=\"it\"><head><meta charset=\"utf-8\"><title>AMCScore</title></head>" +
                "<body style=\"background:#111;color:#fff;font-family:sans-serif;\">" +
                "<h1>AMCScore</h1>" +
                "<p>Collegati qui dal browser di un altro dispositivo sulla stessa rete/hotspot.</p>" +
                "<ul>" +
                "<li><a href='/control/Volley'>Controllo VOLLEY</a></li>" +
                "<li><a href='/display/Volley'>Tabellone VOLLEY</a></li>" +
                "<li><a href='/control/Basket'>Controllo BASKET</a></li>" +
                "<li><a href='/display/Basket'>Tabellone BASKET</a></li>" +
                "</ul></body></html>"
        val resp = newFixedLengthResponse(html)
        resp.addHeader("Content-Type", "text/html; charset=utf-8")
        return resp
    }

    private fun serveControl(uri: String): Response {
        val sport = uri.removePrefix("/control/").ifBlank { "Volley" }
        val file = when (sport.lowercase()) {
            "volley" -> "volley_control.html"
            "basket" -> "basket_control.html"
            else -> "volley_control.html"
        }
        val html = readAsset(file) ?: "<h1>File $file non trovato</h1>"
        val resp = newFixedLengthResponse(html)
        resp.addHeader("Content-Type", "text/html; charset=utf-8")
        return resp
    }

    private fun serveDisplay(uri: String): Response {
        val sport = uri.removePrefix("/display/").ifBlank { "Volley" }
        val file = when (sport.lowercase()) {
            "volley" -> "volley_display.html"
            "basket" -> "basket_display.html"
            else -> "volley_display.html"
        }
        val html = readAsset(file) ?: "<h1>File $file non trovato</h1>"
        val resp = newFixedLengthResponse(html)
        resp.addHeader("Content-Type", "text/html; charset=utf-8")
        return resp
    }

    private fun serveState(uri: String): Response {
        val sport = uri.removePrefix("/api/state/").ifBlank { "Volley" }
        val st = getState(sport)
        val json = "{\n" +
                "\"teamA_name\":\"${'$'}{st.teamA_name}\"," +
                "\"teamB_name\":\"${'$'}{st.teamB_name}\"," +
                "\"teamA_score\":${'$'}{st.teamA_score}," +
                "\"teamB_score\":${'$'}{st.teamB_score}," +
                "\"teamA_sets\":${'$'}{st.teamA_sets}," +
                "\"teamB_sets\":${'$'}{st.teamB_sets}," +
                "\"current_set\":${'$'}{st.current_set}," +
                "\"teamA_timeouts\":${'$'}{st.teamA_timeouts}," +
                "\"teamB_timeouts\":${'$'}{st.teamB_timeouts}," +
                "\"teamA_subs\":${'$'}{st.teamA_subs}," +
                "\"teamB_subs\":${'$'}{st.teamB_subs}," +
                "\"max_timeouts\":${'$'}{st.max_timeouts}," +
                "\"max_subs\":${'$'}{st.max_subs}," +
                "\"logoA\":null," +
                "\"logoB\":null," +
                "\"bgColor\":\"${'$'}{st.bgColor}\"," +
                "\"bgImage\":null," +
                "\"sideLeft\":\"${'$'}{st.sideLeft}\"}"
        val resp = newFixedLengthResponse(json)
        resp.addHeader("Content-Type", "application/json; charset=utf-8")
        return resp
    }

    private fun serveUpdate(session: IHTTPSession, uri: String): Response {
        val sport = uri.removePrefix("/api/update/").ifBlank { "Volley" }
        val st = getState(sport)

        val bodyMap = HashMap<String, String>()
        session.parseBody(bodyMap)
        val bodyStr = bodyMap["postData"] ?: "{}"

        fun getInt(key: String): Int? {
            val idx = bodyStr.indexOf("\"" + key + "\":")
            if (idx == -1) return null
            val sub = bodyStr.substring(idx + key.length + 3)
            val num = sub.takeWhile { it == '-' || it.isDigit() }
            return num.toIntOrNull()
        }
        fun getString(key: String): String? {
            val idx = bodyStr.indexOf("\"" + key + "\":\"")
            if (idx == -1) return null
            val start = idx + key.length + 4
            val end = bodyStr.indexOf("\"", start)
            if (end == -1) return null
            return bodyStr.substring(start, end)
        }

        val action = getString("action") ?: ""

        when (action) {
            "score" -> {
                val team = getString("team") ?: "A"
                val delta = getInt("delta") ?: 0
                if (team == "A") st.teamA_score = (st.teamA_score + delta).coerceIn(0, 199)
                if (team == "B") st.teamB_score = (st.teamB_score + delta).coerceIn(0, 199)
                checkSetWin(st)
            }
            "reset_set" -> {
                st.teamA_score = 0
                st.teamB_score = 0
            }
            else -> {
                // altre azioni ignorate in questa versione semplice
            }
        }

        val resp = newFixedLengthResponse("{\"ok\":true}")
        resp.addHeader("Content-Type", "application/json; charset=utf-8")
        return resp
    }

    private fun checkSetWin(st: GameState) {
        val a = st.teamA_score
        val b = st.teamB_score
        val target = if (st.current_set < 5) 25 else 15
        if (a >= target || b >= target) {
            if (abs(a - b) >= 2) {
                if (a > b) st.teamA_sets++ else st.teamB_sets++
                st.teamA_score = 0
                st.teamB_score = 0
                if (st.teamA_sets < 3 && st.teamB_sets < 3) {
                    st.current_set = st.teamA_sets + st.teamB_sets + 1
                }
            }
        }
    }
}
