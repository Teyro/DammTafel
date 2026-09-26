package de.oejendorferdamm.dammboard.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Tafelgrün, das Standard-Erscheinungsbild einer klassischen Kreidetafel. */
val TafelGruen = Color(0xFF5E8C6A)
val TafelSchwarz = Color(0xFF262A26)
val TafelWeiss = Color(0xFFF7F5EF)
val TafelGrau = Color(0xFFB9BDB8)

val HintergrundOptionen = listOf(TafelGruen, TafelSchwarz, TafelWeiss, TafelGrau)

/** Musterüberlagerung für den Tafelhintergrund (zusätzlich zur reinen Farbe). */
enum class MusterTyp { KEIN, LINIERT, KARIERT, GEPUNKTET, NOTENLINIEN, FUSSBALLFELD, STUNDENPLAN }

data class HintergrundStil(val farbe: Color, val muster: MusterTyp = MusterTyp.KEIN)

/** Die 12 Kreide-/Stiftfarben aus der Werkzeugleiste (4 Spalten x 3 Zeilen). */
val KreidePalette = listOf(
    Color(0xFFFDFCF9), Color(0xFF1A1A1A), Color(0xFFE33B3B), Color(0xFFFBD936),
    Color(0xFFF08A1C), Color(0xFF7A4A28), Color(0xFFB6E13B), Color(0xFF3FAE3A),
    Color(0xFF3BD9E8), Color(0xFF9C3FC9), Color(0xFF5A1E8C), Color(0xFF1E3AA8),
)

enum class Werkzeug {
    STIFT, FORMEN, RADIERER, LASSO, GEOMETRIE, AUSWAHL, WERKZEUGKASTEN
}

enum class StiftArt { FEIN, LEUCHT }

enum class FormTyp {
    DREIECK_RECHTS, DREIECK, KREIS, ELLIPSE, QUADRAT,
    SECHSECK, ABGERUNDET, FUENFECK, STERN, WELLE,
    LINIE, PFEIL, DOPPELPFEIL, FREIHANDPFEIL,
    LINIE_GESTRICHELT, PFEIL_GESTRICHELT, DOPPELPFEIL_GESTRICHELT, FREIHANDPFEIL_GESTRICHELT
}

enum class RadiererGroesse(val radius: Float) { KLEIN(18f), MITTEL(34f), GROSS(56f) }

enum class GeometrieWerkzeug { LINEAL, WINKELDREIECK, WINKELMESSER, RECHTWINKLIG, ZIRKEL, GLEICHSCHENKLIG }

sealed interface BoardItem {
    val id: Long
}

private fun grenzenVon(punkte: List<Offset>): Pair<Offset, Offset> {
    var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
    punkte.forEach { p ->
        minX = min(minX, p.x); minY = min(minY, p.y)
        maxX = max(maxX, p.x); maxY = max(maxY, p.y)
    }
    return Offset(minX, minY) to Offset(maxX, maxY)
}

/** Achsenparalleles Begrenzungsrechteck (min, max) eines Elements. */
fun BoardItem.begrenzendesRechteck(): Pair<Offset, Offset> = when (this) {
    is StrichItem -> grenzen
    is FormItem -> Offset(min(start.x, ende.x), min(start.y, ende.y)) to
        Offset(max(start.x, ende.x), max(start.y, ende.y))
    is LaengenEtikett -> position to position
}

private fun abstandZuStrecke(p: Offset, a: Offset, b: Offset): Float {
    val abx = b.x - a.x; val aby = b.y - a.y
    val laengeQuadrat = abx * abx + aby * aby
    if (laengeQuadrat == 0f) return hypot(p.x - a.x, p.y - a.y)
    var t = ((p.x - a.x) * abx + (p.y - a.y) * aby) / laengeQuadrat
    t = t.coerceIn(0f, 1f)
    val naechster = Offset(a.x + t * abx, a.y + t * aby)
    return hypot(p.x - naechster.x, p.y - naechster.y)
}

/** Prüft, ob ein Punkt (z. B. der Radierer) dieses Element berührt. */
fun BoardItem.beruehrtVon(punkt: Offset, radius: Float): Boolean = when (this) {
    is StrichItem -> {
        // Erst das (zwischengespeicherte) Begrenzungsrechteck prüfen: Der Radierer fragt bei
        // jeder Bewegung ALLE Striche ab – ohne diese Vorprüfung würde jeder Punkt jedes
        // Strichs auf der Seite nachgerechnet, was auf vollen Tafeln spürbar ruckelt.
        val reichweite = radius + breite / 2
        val (min, max) = grenzen
        if (punkt.x < min.x - reichweite || punkt.x > max.x + reichweite ||
            punkt.y < min.y - reichweite || punkt.y > max.y + reichweite
        ) {
            false
        } else if (punkte.size < 2) {
            punkte.firstOrNull()?.let { hypot(punkt.x - it.x, punkt.y - it.y) <= reichweite } ?: false
        } else {
            (0 until punkte.size - 1).any { i -> abstandZuStrecke(punkt, punkte[i], punkte[i + 1]) <= reichweite }
        }
    }
    is FormItem -> {
        val (min, max) = begrenzendesRechteck()
        punkt.x >= min.x - radius && punkt.x <= max.x + radius &&
            punkt.y >= min.y - radius && punkt.y <= max.y + radius
    }
    is LaengenEtikett -> hypot(punkt.x - position.x, punkt.y - position.y) <= radius
}

data class StrichItem(
    override val id: Long,
    val punkte: List<Offset>,
    val farbe: Color,
    val breite: Float,
    val gestrichelt: Boolean = false
) : BoardItem {
    /** Einmal berechnet: Striche ändern sich nie (Verschieben erzeugt per copy() einen neuen).
     *  Steht im Rumpf, damit es nicht zu equals/hashCode/copy zählt. */
    internal val grenzen: Pair<Offset, Offset> by lazy(LazyThreadSafetyMode.NONE) { grenzenVon(punkte) }
}

data class FormItem(
    override val id: Long,
    val typ: FormTyp,
    val start: Offset,
    val ende: Offset,
    val randFarbe: Color,
    val fuellFarbe: Color?,
    val randBreite: Float,
    val gestrichelt: Boolean
) : BoardItem

data class LaengenEtikett(
    override val id: Long,
    val position: Offset,
    val text: String
) : BoardItem

/** Eine Seite der Tafel: eigener Inhalt, eigener Hintergrund, eigene Undo/Redo-Historie. */
class Seite(hintergrundStart: HintergrundStil = HintergrundStil(TafelGruen)) {
    val items: SnapshotStateList<BoardItem> = mutableStateListOf()
    val ausgewaehlteIds: SnapshotStateList<Long> = mutableStateListOf()
    val hintergrund = mutableStateOf(hintergrundStart)
    val geteilteAnsicht = mutableStateOf(false)

    /**
     * Zählt jede inhaltliche Änderung an [items]. Die Zeichenfläche nutzt das, um fertige
     * Striche/Formen in einer Ebene zwischenzuspeichern, statt sie bei jedem Zeichen-Frame neu
     * zu rendern (siehe TafelCanvas.kt) – wichtig für die Performance bei vielen Strichen.
     */
    var versionsZaehler by mutableIntStateOf(0)
        private set

    private val rueckgaengigStapel = mutableStateListOf<Aktion>()
    private val wiederholenStapel = mutableStateListOf<Aktion>()
    private val ausstehendRadiert = mutableListOf<BoardItem>()

    val kannRueckgaengig get() = rueckgaengigStapel.isNotEmpty()
    val kannWiederholen get() = wiederholenStapel.isNotEmpty()

    private sealed interface Aktion
    private data class Hinzugefuegt(val hinzugefuegteItems: List<BoardItem>) : Aktion
    private data class Entfernt(val entfernteItems: List<BoardItem>) : Aktion
    private data class Verschoben(val ids: List<Long>, val delta: Offset) : Aktion

    /** Entfernt sofort alle Elemente unter dem Radierer; die Aktion wird erst bei [radierenAbschliessen] auf den Undo-Stapel gelegt. */
    fun radiereBeruehrte(punkt: Offset, radius: Float) {
        val treffer = items.filter { it.beruehrtVon(punkt, radius) }
        if (treffer.isEmpty()) return
        entferne(treffer)
        ausstehendRadiert.addAll(treffer)
        versionsZaehler++
    }

    fun radierenAbschliessen() {
        if (ausstehendRadiert.isEmpty()) return
        rueckgaengigStapel.add(Entfernt(ausstehendRadiert.toList()))
        wiederholenStapel.clear()
        ausstehendRadiert.clear()
    }

    fun hinzufuegen(item: BoardItem) {
        items.add(item)
        rueckgaengigStapel.add(Hinzugefuegt(listOf(item)))
        wiederholenStapel.clear()
        versionsZaehler++
    }

    fun allesLoeschen() {
        if (items.isEmpty()) return
        val alle = items.toList()
        items.clear()
        ausgewaehlteIds.clear()
        rueckgaengigStapel.add(Entfernt(alle))
        wiederholenStapel.clear()
        versionsZaehler++
    }

    fun entfernenAusgewaehlteOderAlles() {
        val zielItems = if (ausgewaehlteIds.isNotEmpty()) {
            val ids = ausgewaehlteIds.toHashSet()
            items.filter { it.id in ids }
        } else {
            items.toList()
        }
        if (zielItems.isEmpty()) return
        entferne(zielItems)
        ausgewaehlteIds.clear()
        rueckgaengigStapel.add(Entfernt(zielItems))
        wiederholenStapel.clear()
        versionsZaehler++
    }

    fun rueckgaengig() {
        val aktion = rueckgaengigStapel.removeLastOrNull() ?: return
        when (aktion) {
            is Hinzugefuegt -> {
                entferne(aktion.hinzugefuegteItems)
                wiederholenStapel.add(aktion)
            }
            is Entfernt -> {
                items.addAll(aktion.entfernteItems)
                wiederholenStapel.add(aktion)
            }
            is Verschoben -> {
                verschiebeItems(aktion.ids, -aktion.delta)
                wiederholenStapel.add(aktion)
            }
        }
        versionsZaehler++
    }

    fun wiederholen() {
        val aktion = wiederholenStapel.removeLastOrNull() ?: return
        when (aktion) {
            is Hinzugefuegt -> {
                items.addAll(aktion.hinzugefuegteItems)
                rueckgaengigStapel.add(aktion)
            }
            is Entfernt -> {
                entferne(aktion.entfernteItems)
                rueckgaengigStapel.add(aktion)
            }
            is Verschoben -> {
                verschiebeItems(aktion.ids, aktion.delta)
                rueckgaengigStapel.add(aktion)
            }
        }
        versionsZaehler++
    }

    /** Entfernt [weg] in EINEM Durchgang über die IDs – removeAll(Liste) würde jedes Element
     *  mit jedem vergleichen, was z. B. beim Wiederholen von "Alles löschen" quadratisch wird. */
    private fun entferne(weg: List<BoardItem>) {
        if (weg.isEmpty()) return
        val ids = weg.mapTo(HashSet(weg.size * 2)) { it.id }
        val rest = items.filterNot { it.id in ids }
        if (rest.size == items.size) return
        items.clear()
        items.addAll(rest)
    }

    private fun verschiebeItems(idListe: List<Long>, delta: Offset) {
        val ids = idListe.toHashSet()
        for (i in items.indices) {
            val item = items[i]
            if (item.id !in ids) continue
            items[i] = when (item) {
                is StrichItem -> item.copy(punkte = item.punkte.map { it + delta })
                is FormItem -> item.copy(start = item.start + delta, ende = item.ende + delta)
                is LaengenEtikett -> item.copy(position = item.position + delta)
            }
        }
    }

    /** Bewegt die ausgewählten Elemente sofort sichtbar – wird während einer laufenden Ziehgeste
     *  bei jedem Bewegungsschritt aufgerufen. Für die Undo-Historie zählt erst der gesamte Weg
     *  der ganzen Geste, siehe [protokolliereVerschiebung] – sonst würde jeder einzelne
     *  Bewegungsschritt einen eigenen Rückgängig-Schritt erzeugen. */
    fun verschiebeAusgewaehlte(delta: Offset) {
        if (ausgewaehlteIds.isEmpty()) return
        verschiebeItems(ausgewaehlteIds.toList(), delta)
        versionsZaehler++
    }

    /** Trägt eine abgeschlossene Verschiebung (Summe aller Einzelschritte einer Ziehgeste) als
     *  EINEN Rückgängig-Schritt ein. Verschiebt dabei selbst nichts mehr – das ist während der
     *  Geste bereits über [verschiebeAusgewaehlte] passiert. */
    fun protokolliereVerschiebung(ids: List<Long>, gesamtDelta: Offset) {
        if (ids.isEmpty() || gesamtDelta == Offset.Zero) return
        rueckgaengigStapel.add(Verschoben(ids, gesamtDelta))
        wiederholenStapel.clear()
    }
}
