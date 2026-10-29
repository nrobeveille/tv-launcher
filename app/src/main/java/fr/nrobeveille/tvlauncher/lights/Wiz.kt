package fr.nrobeveille.tvlauncher.lights

import android.content.Context
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** État d'une ampoule, tel que renvoyé par getPilot. */
@Immutable
data class PilotState(
    val on: Boolean,
    val dimming: Int?,
    val temp: Int?,
    val rgb: Triple<Int, Int, Int>?,
    val sceneId: Int?,
) {
    /** Paramètres setPilot qui remettent l'ampoule dans cet état. */
    fun toParams(): JSONObject = JSONObject().apply {
        if (!on) {
            put("state", false)
            return@apply
        }
        put("state", true)
        when {
            sceneId != null && sceneId > 0 -> put("sceneId", sceneId)
            temp != null && temp > 0 -> put("temp", temp)
            rgb != null -> {
                put("r", rgb.first)
                put("g", rgb.second)
                put("b", rgb.third)
            }
        }
        if (dimming != null) put("dimming", dimming.coerceIn(10, 100))
    }

    companion object {
        fun parse(result: JSONObject): PilotState = PilotState(
            on = result.optBoolean("state", false),
            dimming = result.optInt("dimming", -1).takeIf { it >= 0 },
            temp = result.optInt("temp", 0).takeIf { it > 0 },
            rgb = if (result.has("r")) {
                Triple(result.optInt("r"), result.optInt("g"), result.optInt("b"))
            } else {
                null
            },
            sceneId = result.optInt("sceneId", 0).takeIf { it > 0 },
        )
    }
}

/** Une ampoule connue (mémorisée par son adresse MAC, l'IP peut changer). */
@Immutable
data class Bulb(
    val mac: String,
    val ip: String,
    val name: String,
    val pilot: PilotState? = null,
    val reachable: Boolean = true,
)

/** Protocole local des ampoules WiZ : JSON en UDP sur le port 38899. */
object WizClient {
    private const val PORT = 38_899

    /** Diffuse une demande sur le réseau et renvoie les ampoules qui répondent (MAC → IP). */
    suspend fun discover(timeoutMs: Long = 3_000): Map<String, String> = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<String, String>()
        val withoutMac = LinkedHashSet<String>()
        val message = JSONObject()
            .put("method", "registration")
            .put(
                "params",
                JSONObject()
                    .put("phoneMac", "AAAAAAAAAAAA")
                    .put("register", false)
                    .put("phoneIp", "1.2.3.4")
                    .put("id", "1"),
            )
            .toString()
            .toByteArray()
        val targets = broadcastAddresses() + InetAddress.getByName("255.255.255.255")

        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.soTimeout = 250
            val buffer = ByteArray(2_048)
            val end = System.currentTimeMillis() + timeoutMs
            var nextSend = 0L
            while (System.currentTimeMillis() < end) {
                ensureActive()
                if (System.currentTimeMillis() >= nextSend) {
                    for (target in targets) {
                        runCatching { socket.send(DatagramPacket(message, message.size, target, PORT)) }
                    }
                    nextSend = System.currentTimeMillis() + 800
                }
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (e: SocketTimeoutException) {
                    continue
                } catch (e: IOException) {
                    continue
                }
                val ip = packet.address?.hostAddress ?: continue
                val json = runCatching { JSONObject(String(packet.data, 0, packet.length)) }.getOrNull() ?: continue
                val mac = json.optJSONObject("result")?.optString("mac").orEmpty()
                if (mac.isNotEmpty()) found[mac] = ip else withoutMac += ip
            }
        }
        // Réponse sans MAC : on la demande directement à l'ampoule.
        for (ip in withoutMac) {
            if (ip in found.values) continue
            val mac = requestBlocking(ip, getPilotMessage())?.optJSONObject("result")?.optString("mac").orEmpty()
            found[mac.ifEmpty { ip }] = ip
        }
        found
    }

    suspend fun getPilot(ip: String): PilotState? = withContext(Dispatchers.IO) {
        requestBlocking(ip, getPilotMessage())?.optJSONObject("result")?.let { PilotState.parse(it) }
    }

    suspend fun setPilot(ip: String, params: JSONObject): Boolean = withContext(Dispatchers.IO) {
        setPilotBlocking(ip, params)
    }

    fun setPilotBlocking(ip: String, params: JSONObject): Boolean {
        val message = JSONObject().put("id", 1).put("method", "setPilot").put("params", params)
        val reply = requestBlocking(ip, message) ?: return false
        return reply.optJSONObject("result")?.optBoolean("success", true) ?: !reply.has("error")
    }

    fun getPilotBlocking(ip: String): PilotState? =
        requestBlocking(ip, getPilotMessage())?.optJSONObject("result")?.let { PilotState.parse(it) }

    /** Envoi sans attendre de réponse (Mode soirée : rapide, une perte n'est pas grave). */
    fun fire(socket: DatagramSocket, ip: String, params: JSONObject) {
        val data = JSONObject().put("id", 1).put("method", "setPilot").put("params", params).toString().toByteArray()
        runCatching { socket.send(DatagramPacket(data, data.size, InetAddress.getByName(ip), PORT)) }
    }

    private fun getPilotMessage() = JSONObject().put("method", "getPilot").put("params", JSONObject())

    private fun requestBlocking(ip: String, message: JSONObject, timeoutMs: Int = 700, attempts: Int = 2): JSONObject? {
        val data = message.toString().toByteArray()
        val address = runCatching { InetAddress.getByName(ip) }.getOrNull() ?: return null
        return try {
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs
                val buffer = ByteArray(2_048)
                repeat(attempts) {
                    socket.send(DatagramPacket(data, data.size, address, PORT))
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                        return runCatching { JSONObject(String(buffer, 0, packet.length)) }.getOrNull()
                    } catch (e: SocketTimeoutException) {
                        // nouvel essai
                    }
                }
                null
            }
        } catch (e: IOException) {
            null
        }
    }

    private fun broadcastAddresses(): List<InetAddress> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.interfaceAddresses }
            .mapNotNull { it.broadcast }
            .distinct()
    }.getOrDefault(emptyList())
}

/** Ampoules et réglages mémorisés. */
class WizStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("wiz", Context.MODE_PRIVATE)

    fun loadBulbs(): List<Bulb> = runCatching {
        val array = JSONArray(prefs.getString("bulbs", "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Bulb(mac = o.getString("mac"), ip = o.getString("ip"), name = o.getString("name"))
        }
    }.getOrDefault(emptyList())

    fun saveBulbs(bulbs: List<Bulb>) {
        val array = JSONArray()
        bulbs.forEach { array.put(JSONObject().put("mac", it.mac).put("ip", it.ip).put("name", it.name)) }
        prefs.edit().putString("bulbs", array.toString()).apply()
    }

    var partySync: Boolean
        get() = prefs.getBoolean("party_sync", false)
        set(value) = prefs.edit().putBoolean("party_sync", value).apply()
}

/**
 * Mode soirée → ampoules : envoie une couleur à chaque temps fort (4 fois par seconde au plus),
 * puis remet les ampoules dans leur état d'avant en quittant.
 */
class WizPartySync(context: Context) {
    private val store = WizStore(context)
    private var bulbs: List<Bulb> = emptyList()
    private var executor: ExecutorService? = null
    private var socket: DatagramSocket? = null
    private val snapshots = ConcurrentHashMap<String, PilotState>()
    @Volatile private var lastSend = 0L

    /** Nombre d'ampoules synchronisées (0 si la synchro est désactivée). */
    var activeCount: Int = 0
        private set

    fun start() {
        stop()
        bulbs = store.loadBulbs()
        if (!store.partySync || bulbs.isEmpty()) {
            activeCount = 0
            return
        }
        activeCount = bulbs.size
        val ex = Executors.newSingleThreadExecutor()
        executor = ex
        ex.execute {
            socket = runCatching { DatagramSocket() }.getOrNull()
            snapshots.clear()
            for (bulb in bulbs) {
                WizClient.getPilotBlocking(bulb.ip)?.let { snapshots[bulb.ip] = it }
            }
        }
    }

    /** Temps fort : couleur [rgb] (format Android ARGB) envoyée à toutes les ampoules. */
    fun onBeat(rgb: Int) {
        val ex = executor ?: return
        val now = System.currentTimeMillis()
        if (now - lastSend < 250) return
        lastSend = now
        val params = JSONObject()
            .put("r", (rgb shr 16) and 0xFF)
            .put("g", (rgb shr 8) and 0xFF)
            .put("b", rgb and 0xFF)
            .put("dimming", 100)
        val targets = bulbs
        runCatching {
            ex.execute {
                val s = socket ?: return@execute
                targets.forEach { WizClient.fire(s, it.ip, params) }
            }
        }
    }

    fun stop() {
        val ex = executor ?: return
        executor = null
        val targets = bulbs
        runCatching {
            ex.execute {
                for (bulb in targets) {
                    snapshots[bulb.ip]?.let { WizClient.setPilotBlocking(bulb.ip, it.toParams()) }
                }
                socket?.close()
                socket = null
            }
        }
        ex.shutdown()
    }
}
