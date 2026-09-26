package de.oejendorferdamm.dammboard.model

/** Steuert, ob die Oberfläche mit sanften Übergängen (NORMAL) oder ohne jede Animation (PERFORMANCE) läuft. */
enum class AnimationsModus { NORMAL, PERFORMANCE }

/**
 * Größe aller Bedienelemente (Werkzeugleiste, Seitenanzeige, Menüs – inkl. Tippflächen).
 * Wirkt ZUSÄTZLICH zur automatischen Anpassung an die Bildschirmgröße (siehe
 * ui/Skalierung.kt): "Groß" sieht dadurch auf jedem Board gleich groß aus, egal welche
 * Pixeldichte das Gerät meldet.
 */
enum class SymbolGroesse(val skalierung: Float, val bezeichnung: String) {
    KOMPAKT(1.0f, "Kompakt"),
    STANDARD(1.15f, "Standard"),
    GROSS(1.35f, "Groß"),
    SEHR_GROSS(1.6f, "Sehr groß")
}

/** Zugangsdaten für den schuleigenen IServ-WebDAV-Speicher. */
data class IServZugang(
    val serverUrl: String = "",
    val benutzername: String = "",
    val passwort: String = ""
) {
    val istEingerichtet: Boolean
        get() = serverUrl.isNotBlank() && benutzername.isNotBlank() && passwort.isNotBlank()
}

/** Ein Eintrag (Ordner oder Datei) aus einer IServ-WebDAV-Verzeichnisliste. */
data class IServEintrag(
    val name: String,
    val pfad: String,
    val istOrdner: Boolean
)
