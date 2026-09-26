package de.oejendorferdamm.dammboard.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import de.oejendorferdamm.dammboard.BuildConfig
import de.oejendorferdamm.dammboard.data.EinstellungenSpeicher
import de.oejendorferdamm.dammboard.data.UpdateClient
import de.oejendorferdamm.dammboard.model.AnimationsModus
import de.oejendorferdamm.dammboard.model.HintergrundStil
import de.oejendorferdamm.dammboard.model.IServZugang
import de.oejendorferdamm.dammboard.model.SymbolGroesse
import de.oejendorferdamm.dammboard.model.TafelGruen
import de.oejendorferdamm.dammboard.model.UpdateInfo
import de.oejendorferdamm.dammboard.model.istNeuereVersion
import de.oejendorferdamm.dammboard.ui.filemanager.DateiManagerScreen
import de.oejendorferdamm.dammboard.ui.settings.EinstellungenScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

private sealed interface Bildschirm {
    data object Brett : Bildschirm
    data object Einstellungen : Bildschirm
    data class Dateimanager(val bild: Bitmap) : Bildschirm
}

/** Wie lange nach dem App-Start die automatische Update-Prüfung im Hintergrund läuft. */
private const val UPDATE_PRUEFUNG_VERZOEGERUNG_MS = 10_000L

// Platzbedarf bei Faktor 1 (in dp): Werkzeugleiste mit dem breitesten geöffneten Panel bzw.
// die Menü-Karte von Einstellungen/Dateimanager. Größer als der Bildschirm wird nie skaliert.
private const val TAFEL_INHALT_BREITE_DP = 930f
private const val TAFEL_INHALT_HOEHE_DP = 350f
private const val MENUE_INHALT_BREITE_DP = 540f
private const val MENUE_INHALT_HOEHE_DP = 380f

/** Wurzel der App: schaltet zwischen Tafel, Einstellungen und IServ-Dateimanager um und hält die geteilten Zustände. */
@Composable
fun AppWurzel(onAppSchliessen: () -> Unit) {
    val context = LocalContext.current
    val speicher = remember { EinstellungenSpeicher(context) }
    val updateClient = remember { UpdateClient() }
    val scope = rememberCoroutineScope()

    val iservZugang by speicher.iservZugang.collectAsState(initial = IServZugang())
    val animationsModus by speicher.animationsModus.collectAsState(initial = AnimationsModus.NORMAL)
    val symbolGroesse by speicher.symbolGroesse.collectAsState(initial = SymbolGroesse.STANDARD)
    val autoUpdatePruefung by speicher.autoUpdatePruefung.collectAsState(initial = true)
    val zeichenPraezision by speicher.zeichenPraezision.collectAsState(initial = 0.7f)
    val hintergrundMerken by speicher.hintergrundMerken.collectAsState(initial = false)
    val gespeicherterHintergrund by speicher.gespeicherterHintergrund.collectAsState(initial = null)

    val tafelState = rememberTafelState()
    var bildschirm by remember { mutableStateOf<Bildschirm>(Bildschirm.Brett) }

    // Bedienelemente wachsen automatisch mit der Bildschirmgröße (in dp) und zusätzlich mit der
    // eingestellten Symbolgröße – siehe Skalierung.kt, warum das gerade auf alten Boards nötig ist.
    val konfiguration = LocalConfiguration.current
    val breiteDp = konfiguration.screenWidthDp.toFloat()
    val hoeheDp = konfiguration.screenHeightDp.toFloat()
    val gewuenschterFaktor = automatischerBildschirmFaktor(breiteDp, hoeheDp) * symbolGroesse.skalierung
    val tafelFaktor = passenderFaktor(gewuenschterFaktor, breiteDp, hoeheDp, TAFEL_INHALT_BREITE_DP, TAFEL_INHALT_HOEHE_DP)
    val menueFaktor = passenderFaktor(gewuenschterFaktor, breiteDp, hoeheDp, MENUE_INHALT_BREITE_DP, MENUE_INHALT_HOEHE_DP)
    val anzeige = context.resources.displayMetrics
    val bildschirmInfo = "Bildschirm: ${anzeige.widthPixels}×${anzeige.heightPixels} px, ${anzeige.densityDpi} dpi " +
        "(${breiteDp.toInt()}×${hoeheDp.toInt()} dp) · Bedienfaktor " + "%.2f".format(tafelFaktor)

    // Beim allerersten Laden der gespeicherten Werte (falls "Hintergrund merken" aktiv ist)
    // einmalig den zuletzt genutzten Hintergrund übernehmen – danach merkt sich jede weitere
    // Änderung automatisch selbst (siehe LaunchedEffect weiter unten).
    var anfangsHintergrundGesetzt by remember { mutableStateOf(false) }
    LaunchedEffect(hintergrundMerken, gespeicherterHintergrund) {
        val stil = gespeicherterHintergrund
        // Auch jede neu angelegte Seite startet dann mit dem zuletzt gewählten Hintergrund –
        // bis 0.4.6 galt die Option nur für die allererste Seite nach dem Start.
        tafelState.neueSeitenHintergrund = if (hintergrundMerken && stil != null) stil else HintergrundStil(TafelGruen)
        if (!anfangsHintergrundGesetzt && hintergrundMerken && stil != null) {
            tafelState.seite.hintergrund.value = stil
            anfangsHintergrundGesetzt = true
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { tafelState.seite.hintergrund.value }
            .drop(1)
            .collect { stil -> if (hintergrundMerken) speicher.speichereHintergrund(stil) }
    }

    // Automatisch (unauffällig, nur roter Punkt) oder manuell über den Knopf im
    // Einstellungsmenü – beides läuft über dieselbe Prüfung.
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updatePruefungLaeuft by remember { mutableStateOf(false) }
    var updateBereitsAktuell by remember { mutableStateOf(false) }

    suspend fun pruefeAufUpdate() {
        updatePruefungLaeuft = true
        updateBereitsAktuell = false
        updateClient.neuesteVersionAbrufen()
            .onSuccess { info ->
                if (istNeuereVersion(BuildConfig.VERSION_NAME, info.version)) {
                    updateInfo = info
                } else {
                    updateBereitsAktuell = true
                }
            }
        updatePruefungLaeuft = false
    }

    LaunchedEffect(autoUpdatePruefung) {
        if (autoUpdatePruefung) {
            delay(UPDATE_PRUEFUNG_VERZOEGERUNG_MS)
            pruefeAufUpdate()
        }
    }

    when (val aktuell = bildschirm) {
        is Bildschirm.Brett -> TafelScreen(
            state = tafelState,
            animationsModus = animationsModus,
            bedienFaktor = tafelFaktor,
            zeichenPraezision = zeichenPraezision,
            zeigeUpdatePunkt = updateInfo != null,
            onSchliessenApp = onAppSchliessen,
            onOeffneEinstellungen = { bildschirm = Bildschirm.Einstellungen },
            onIServAnfrage = { bitmap -> bildschirm = Bildschirm.Dateimanager(bitmap) }
        )
        is Bildschirm.Einstellungen -> SkalierteDichte(menueFaktor) {
            EinstellungenScreen(
                aktuellerZugang = iservZugang,
                aktuellerModus = animationsModus,
                aktuelleSymbolGroesse = symbolGroesse,
                aktuelleVersion = BuildConfig.VERSION_NAME,
                bildschirmInfo = bildschirmInfo,
                updateInfo = updateInfo,
                autoUpdatePruefung = autoUpdatePruefung,
                updatePruefungLaeuft = updatePruefungLaeuft,
                updateBereitsAktuell = updateBereitsAktuell,
                zeichenPraezision = zeichenPraezision,
                hintergrundMerken = hintergrundMerken,
                onZugangSpeichern = { neu -> scope.launch { speicher.speichereIServZugang(neu) } },
                onModusGeaendert = { neu -> scope.launch { speicher.speichereAnimationsModus(neu) } },
                onSymbolGroesseGeaendert = { neu -> scope.launch { speicher.speichereSymbolGroesse(neu) } },
                onAutoUpdateGeaendert = { neu -> scope.launch { speicher.speichereAutoUpdatePruefung(neu) } },
                onUpdatePruefungAnfordern = { scope.launch { pruefeAufUpdate() } },
                onZeichenPraezisionGeaendert = { neu -> scope.launch { speicher.speichereZeichenPraezision(neu) } },
                onHintergrundMerkenGeaendert = { neu -> scope.launch { speicher.speichereHintergrundMerken(neu) } },
                onZurueck = { bildschirm = Bildschirm.Brett }
            )
        }
        is Bildschirm.Dateimanager -> SkalierteDichte(menueFaktor) {
            DateiManagerScreen(
                zugang = iservZugang,
                bild = aktuell.bild,
                onFertig = { bildschirm = Bildschirm.Brett }
            )
        }
    }
}
