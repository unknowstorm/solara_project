package com.unknowstorm.musicapp.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Ключ в текстовом хранилище, под которым лежит выбранная палитра. */
const val PALETTE_KEY = "palette"

/** Ключ в текстовом хранилище для режима темы (системная / светлая / тёмная). */
const val THEME_MODE_KEY = "theme_mode"

/** Режим темы: по умолчанию следует за системой, пока пользователь явно не выбрал светлую или тёмную. */
enum class ThemeMode {
    System, Light, Dark;

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        System -> systemDark
        Light -> false
        Dark -> true
    }

    fun encode(): String = name.lowercase()

    companion object {
        fun decode(raw: String?): ThemeMode? = entries.firstOrNull { it.name.lowercase() == raw }
    }
}

/** Выбор палитры: цвета системы (Material You, только Android 12+) или свой основной цвет. */
sealed interface PaletteChoice {
    data object Dynamic : PaletteChoice
    data class Seed(val argb: Int) : PaletteChoice

    fun encode(): String = when (this) {
        Dynamic -> "system"
        is Seed -> "seed:" + (argb.toLong() and 0xFFFFFFFFL).toString(16)
    }

    companion object {
        fun decode(raw: String?): PaletteChoice? = when {
            raw == null -> null
            raw == "system" -> Dynamic
            raw.startsWith("seed:") -> raw.removePrefix("seed:").toLongOrNull(16)?.let { Seed(it.toInt()) }
            else -> null
        }
    }
}

/** Готовые основные цвета в шторке палитры. Первый — стандартный фиолетовый Material 3. */
val PALETTE_SEEDS: List<Color> = listOf(
    Color(0xFF6750A4),
    Color(0xFF1E6FE0),
    Color(0xFF00897B),
    Color(0xFF2E7D32),
    Color(0xFFF2A100),
    Color(0xFFEF6C00),
    Color(0xFFD81B60),
    Color(0xFFD32F2F),
)

/** Текущая палитра приложения. Глобальное состояние, как и I18n.language: тема читает его при каждой перерисовке. */
object PaletteSettings {
    var choice: PaletteChoice by mutableStateOf(PaletteChoice.Dynamic)
        private set

    /** Режим темы. Тема читает его при каждой перерисовке, как и [choice]. */
    var mode: ThemeMode by mutableStateOf(ThemeMode.System)
        private set

    /** Вызывается один раз при старте с сохранённой строкой (или null). */
    fun restore(saved: String?) {
        choice = PaletteChoice.decode(saved) ?: PaletteChoice.Dynamic
    }

    /** Применяет выбор сразу и сохраняет его через [persist]. */
    fun update(new: PaletteChoice, persist: (String) -> Unit) {
        choice = new
        persist(new.encode())
    }

    /** Вызывается один раз при старте с сохранённым режимом темы (или null). */
    fun restoreMode(saved: String?) {
        mode = ThemeMode.decode(saved) ?: ThemeMode.System
    }

    /** Применяет режим темы сразу и сохраняет его через [persist]. */
    fun updateMode(new: ThemeMode, persist: (String) -> Unit) {
        mode = new
        persist(new.encode())
    }
}

// ---------- Схема из одного цвета ----------

private data class Hsl(val h: Float, val s: Float, val l: Float)

private fun hslOf(c: Color): Hsl {
    val r = c.red
    val g = c.green
    val b = c.blue
    val mx = max(r, max(g, b))
    val mn = min(r, min(g, b))
    val d = mx - mn
    val l = (mx + mn) / 2f
    if (d == 0f) return Hsl(0f, 0f, l)
    val s = d / (1f - abs(2f * l - 1f))
    var h = when (mx) {
        r -> ((g - b) / d) % 6f
        g -> (b - r) / d + 2f
        else -> (r - g) / d + 4f
    } * 60f
    if (h < 0f) h += 360f
    return Hsl(h, s, l)
}

private fun hsl(h: Float, s: Float, l: Float): Color {
    val hue = ((h % 360f) + 360f) % 360f
    val sat = s.coerceIn(0f, 1f)
    val light = l.coerceIn(0f, 1f)
    val c = (1f - abs(2f * light - 1f)) * sat
    val x = c * (1f - abs((hue / 60f) % 2f - 1f))
    val m = light - c / 2f
    val (r, g, b) = when {
        hue < 60f -> Triple(c, x, 0f)
        hue < 120f -> Triple(x, c, 0f)
        hue < 180f -> Triple(0f, c, x)
        hue < 240f -> Triple(0f, x, c)
        hue < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m)
}

/**
 * Строит светлую или тёмную схему Material 3 из одного основного цвета.
 * Это приближение тональных палитр через HSL: для точных тонов Material можно позже
 * подключить библиотеку material-kolor, интерфейс этой функции не изменится.
 */
fun schemeFromSeed(seed: Color, dark: Boolean): ColorScheme {
    val base = hslOf(seed)
    val h = base.h
    val s = base.s.coerceIn(0.45f, 0.9f)
    val sSec = (s * 0.4f).coerceIn(0.2f, 0.4f)
    val hTer = h + 60f
    val sTer = (s * 0.6f).coerceIn(0.3f, 0.55f)

    fun pri(l: Float) = hsl(h, s, l)
    fun sec(l: Float) = hsl(h, sSec, l)
    fun ter(l: Float) = hsl(hTer, sTer, l)
    fun neu(l: Float) = hsl(h, 0.08f, l)
    fun nvar(l: Float) = hsl(h, 0.12f, l)

    return if (!dark) {
        lightColorScheme().copy(
            primary = pri(0.40f), onPrimary = Color.White,
            primaryContainer = pri(0.90f), onPrimaryContainer = pri(0.12f),
            inversePrimary = pri(0.80f),
            secondary = sec(0.40f), onSecondary = Color.White,
            secondaryContainer = sec(0.90f), onSecondaryContainer = sec(0.12f),
            tertiary = ter(0.40f), onTertiary = Color.White,
            tertiaryContainer = ter(0.90f), onTertiaryContainer = ter(0.12f),
            background = neu(0.98f), onBackground = neu(0.10f),
            surface = neu(0.98f), onSurface = neu(0.10f),
            surfaceVariant = nvar(0.90f), onSurfaceVariant = nvar(0.30f),
            surfaceTint = pri(0.40f),
            inverseSurface = neu(0.20f), inverseOnSurface = neu(0.95f),
            outline = nvar(0.50f), outlineVariant = nvar(0.80f),
            surfaceBright = neu(0.98f), surfaceDim = neu(0.87f),
            surfaceContainerLowest = Color.White, surfaceContainerLow = neu(0.96f),
            surfaceContainer = neu(0.94f), surfaceContainerHigh = neu(0.92f),
            surfaceContainerHighest = neu(0.90f),
        )
    } else {
        darkColorScheme().copy(
            primary = pri(0.80f), onPrimary = pri(0.20f),
            primaryContainer = pri(0.30f), onPrimaryContainer = pri(0.90f),
            inversePrimary = pri(0.40f),
            secondary = sec(0.80f), onSecondary = sec(0.20f),
            secondaryContainer = sec(0.30f), onSecondaryContainer = sec(0.90f),
            tertiary = ter(0.80f), onTertiary = ter(0.20f),
            tertiaryContainer = ter(0.30f), onTertiaryContainer = ter(0.90f),
            background = neu(0.07f), onBackground = neu(0.90f),
            surface = neu(0.07f), onSurface = neu(0.90f),
            surfaceVariant = nvar(0.25f), onSurfaceVariant = nvar(0.80f),
            surfaceTint = pri(0.80f),
            inverseSurface = neu(0.90f), inverseOnSurface = neu(0.20f),
            outline = nvar(0.60f), outlineVariant = nvar(0.30f),
            surfaceBright = neu(0.22f), surfaceDim = neu(0.07f),
            surfaceContainerLowest = neu(0.05f), surfaceContainerLow = neu(0.09f),
            surfaceContainer = neu(0.12f), surfaceContainerHigh = neu(0.16f),
            surfaceContainerHighest = neu(0.20f),
        )
    }
}

// ---------- Рекомендуемые и свои основные цвета ----------

/** Оттенок основного цвета, 0..360. */
fun seedHue(color: Color): Float = hslOf(color).h

/** Насыщенность основного цвета, 0..1. */
fun seedSaturation(color: Color): Float = hslOf(color).s

/** Основной цвет из оттенка и насыщенности: яркость фиксирована, чтобы цвета были сопоставимы. */
fun seedColor(hue: Float, saturation: Float): Color = hsl(hue, saturation, 0.5f)

/**
 * Рекомендуемые основные цвета рядом с [base]: тот же цветовой охват, оттенок смещён в обе стороны.
 * Результат зависит только от [base], поэтому выбранный цвет можно узнать по значению ARGB.
 */
fun recommendedSeeds(base: Color): List<Color> {
    val b = hslOf(base)
    val s = b.s.coerceIn(0.55f, 0.85f)
    return listOf(-60f, -30f, 30f, 60f).map { shift -> hsl(b.h + shift, s, 0.5f) }
}