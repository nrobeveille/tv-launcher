package fr.nrobeveille.tvlauncher.lights

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Ambiances WiZ (identifiant de scène → nom affiché). */
val WIZ_SCENES = listOf(
    6 to "Cosy",
    3 to "Coucher de soleil",
    5 to "Feu de cheminée",
    29 to "Bougie",
    16 to "Détente",
    18 to "Soirée TV",
    2 to "Romance",
    1 to "Océan",
    7 to "Forêt",
    4 to "Fête",
    26 to "Club",
    31 to "Pulsation",
    15 to "Concentration",
    14 to "Veilleuse",
)

/** Une couleur proposée. */
data class WizColor(val name: String, val r: Int, val g: Int, val b: Int)

val WIZ_COLORS = listOf(
    WizColor("Rouge", 255, 0, 0),
    WizColor("Orange", 255, 100, 0),
    WizColor("Jaune", 255, 200, 0),
    WizColor("Vert", 0, 255, 40),
    WizColor("Cyan", 0, 220, 255),
    WizColor("Bleu", 0, 60, 255),
    WizColor("Violet", 140, 0, 255),
    WizColor("Rose", 255, 0, 150),
)

val WIZ_NAMES = listOf(
    "Salon", "Chambre", "Cuisine", "Bureau", "Entrée", "Couloir",
    "Salle de bain", "Lampe", "Plafonnier", "Lampe de chevet", "Ambiance TV",
)

class LightsViewModel(application: Application) : AndroidViewModel(application) {

    private val store = WizStore(application)

    private val _bulbs = MutableStateFlow(store.loadBulbs())
    val bulbs: StateFlow<List<Bulb>> = _bulbs.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _partySync = MutableStateFlow(store.partySync)
    val partySync: StateFlow<Boolean> = _partySync.asStateFlow()

    private var discoverJob: Job? = null

    init {
        discover()
    }

    /** Cherche les ampoules sur le Wi-Fi, met à jour leurs IP et ajoute les nouvelles. */
    fun discover() {
        if (discoverJob?.isActive == true) return
        discoverJob = viewModelScope.launch {
            _searching.value = true
            _status.value = "Recherche des ampoules…"
            val found = runCatching { WizClient.discover() }.getOrDefault(emptyMap())
            val known = _bulbs.value.associateBy { it.mac }.toMutableMap()
            var added = 0
            for ((mac, ip) in found) {
                val existing = known[mac]
                if (existing != null) {
                    known[mac] = existing.copy(ip = ip)
                } else {
                    added++
                    known[mac] = Bulb(mac = mac, ip = ip, name = "Ampoule ${known.size + 1}")
                }
            }
            _bulbs.value = known.values.toList()
            store.saveBulbs(_bulbs.value)
            refreshAll()
            val total = _bulbs.value.size
            _status.value = when {
                total == 0 -> "Aucune ampoule trouvée. Le stick et les ampoules sont-ils sur le même Wi-Fi ?"
                added > 0 -> "$added nouvelle(s) ampoule(s) · $total au total"
                else -> "$total ampoule(s)"
            }
            _searching.value = false
        }
    }

    /** Relit l'état de toutes les ampoules. */
    fun refreshAll() {
        viewModelScope.launch {
            val results = _bulbs.value.map { bulb ->
                async { bulb.mac to WizClient.getPilot(bulb.ip) }
            }.awaitAll().toMap()
            _bulbs.update { list ->
                list.map { b ->
                    val pilot = results[b.mac]
                    if (b.mac in results) b.copy(pilot = pilot ?: b.pilot, reachable = pilot != null) else b
                }
            }
        }
    }

    // --- Commandes (mac = null : toutes les ampoules) ---------------------------

    fun power(mac: String?, on: Boolean) =
        send(mac, JSONObject().put("state", on)) { it.copy(on = on) }

    fun brightness(mac: String?, percent: Int) =
        send(mac, JSONObject().put("state", true).put("dimming", percent.coerceIn(10, 100))) {
            it.copy(on = true, dimming = percent)
        }

    fun temperature(mac: String?, kelvin: Int) =
        send(mac, JSONObject().put("state", true).put("temp", kelvin)) {
            it.copy(on = true, temp = kelvin, rgb = null, sceneId = null)
        }

    fun color(mac: String?, r: Int, g: Int, b: Int) =
        send(mac, JSONObject().put("state", true).put("r", r).put("g", g).put("b", b)) {
            it.copy(on = true, rgb = Triple(r, g, b), temp = null, sceneId = null)
        }

    fun scene(mac: String?, sceneId: Int) =
        send(mac, JSONObject().put("state", true).put("sceneId", sceneId)) {
            it.copy(on = true, sceneId = sceneId, rgb = null, temp = null)
        }

    /** Fait clignoter l'ampoule pour la repérer, puis la remet comme avant. */
    fun identify(mac: String) {
        val bulb = _bulbs.value.firstOrNull { it.mac == mac } ?: return
        viewModelScope.launch {
            val before = WizClient.getPilot(bulb.ip)
            repeat(3) {
                WizClient.setPilot(bulb.ip, JSONObject().put("state", true).put("r", 255).put("g", 255).put("b", 255).put("dimming", 100))
                delay(350)
                WizClient.setPilot(bulb.ip, JSONObject().put("state", false))
                delay(350)
            }
            WizClient.setPilot(bulb.ip, before?.toParams() ?: JSONObject().put("state", true))
        }
    }

    fun rename(mac: String, name: String) {
        _bulbs.update { list -> list.map { if (it.mac == mac) it.copy(name = name) else it } }
        store.saveBulbs(_bulbs.value)
    }

    fun forget(mac: String) {
        _bulbs.update { list -> list.filterNot { it.mac == mac } }
        store.saveBulbs(_bulbs.value)
    }

    fun setPartySync(enabled: Boolean) {
        store.partySync = enabled
        _partySync.value = enabled
    }

    private fun send(mac: String?, params: JSONObject, optimistic: (PilotState) -> PilotState) {
        val targets = _bulbs.value.filter { mac == null || it.mac == mac }
        if (targets.isEmpty()) return
        // Mise à jour immédiate de l'affichage, confirmée ensuite par l'ampoule.
        _bulbs.update { list ->
            list.map { b ->
                if (targets.any { it.mac == b.mac }) {
                    val base = b.pilot ?: PilotState(on = false, dimming = null, temp = null, rgb = null, sceneId = null)
                    b.copy(pilot = optimistic(base))
                } else {
                    b
                }
            }
        }
        viewModelScope.launch {
            val results = targets.map { bulb ->
                async { bulb.mac to WizClient.setPilot(bulb.ip, params) }
            }.awaitAll().toMap()
            _bulbs.update { list -> list.map { b -> results[b.mac]?.let { ok -> b.copy(reachable = ok) } ?: b } }
            val failed = results.count { !it.value }
            _status.value = if (failed > 0) "$failed ampoule(s) n'ont pas répondu" else null
        }
    }
}
