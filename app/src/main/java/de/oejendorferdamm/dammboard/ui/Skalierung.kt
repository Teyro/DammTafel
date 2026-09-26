package de.oejendorferdamm.dammboard.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Bildschirmgröße (in dp), auf die die Oberfläche ausgelegt ist – ein übliches Touch-Board mit
 * 1920×1080 dp. Hintergrund: Die Oberfläche misst alles in dp. Viele ältere Touch-Boards
 * (gerade die mit Android 8) melden Android aber eine niedrige Pixeldichte bei hoher
 * Auflösung, z. B. 3840×2160 Pixel bei 160 dpi – das sind dann 3840×2160 dp, und jeder Knopf
 * erschien dort nur halb so groß wie auf einem neueren Board, egal welche Symbolgröße
 * eingestellt war.
 */
private const val BEZUG_BREITE_DP = 1920f
private const val BEZUG_HOEHE_DP = 1080f

/**
 * Automatischer Vergrößerungsfaktor für Bildschirme, die (in dp) größer sind als der
 * Bezugsbildschirm – nie verkleinernd, damit Tablets und kleinere Boards unverändert bleiben.
 */
fun automatischerBildschirmFaktor(breiteDp: Float, hoeheDp: Float): Float {
    if (breiteDp <= 0f || hoeheDp <= 0f) return 1f
    return minOf(breiteDp / BEZUG_BREITE_DP, hoeheDp / BEZUG_HOEHE_DP).coerceIn(1f, 3f)
}

/**
 * Begrenzt einen gewünschten Faktor so, dass ein Inhalt, der bei Faktor 1 [inhaltBreiteDp] ×
 * [inhaltHoeheDp] groß ist, noch vollständig auf den Bildschirm passt – sonst würde z. B. die
 * Werkzeugleiste bei "Sehr groß" auf einem kleineren Bildschirm über den Rand hinausragen.
 */
fun passenderFaktor(
    gewuenscht: Float,
    breiteDp: Float,
    hoeheDp: Float,
    inhaltBreiteDp: Float,
    inhaltHoeheDp: Float
): Float {
    if (breiteDp <= 0f || hoeheDp <= 0f) return gewuenscht
    val hoechstens = minOf(breiteDp * 0.98f / inhaltBreiteDp, hoeheDp * 0.95f / inhaltHoeheDp)
    return minOf(gewuenscht, hoechstens).coerceAtLeast(0.75f)
}

/**
 * Vergrößert alles darin (Symbole, Abstände, Schrift UND Tippflächen) um [faktor] – über die
 * Dichte statt über Modifier.scale(), das nur optisch vergrößern und die Tippflächen klein
 * lassen würde. System-Insets (Navigationsleiste) sind echte Pixelwerte und bleiben davon
 * unberührt.
 */
@Composable
fun SkalierteDichte(faktor: Float, inhalt: @Composable () -> Unit) {
    val basis = LocalDensity.current
    val skaliert = remember(basis, faktor) { Density(density = basis.density * faktor, fontScale = basis.fontScale) }
    CompositionLocalProvider(LocalDensity provides skaliert, content = inhalt)
}
