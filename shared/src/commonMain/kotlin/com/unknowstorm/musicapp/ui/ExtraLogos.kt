package com.unknowstorm.musicapp.ui

import androidx.compose.ui.geometry.Offset

// Outlines of logos that are not in Simple Icons. Each is set in its own grid (minX, minY, size).
// Trademarks belong to the service owners. Before a public release replace
// them with materials from their brand books.
internal class LogoSpec(val path: String, val minX: Float, val minY: Float, val size: Float)

internal val EXTRA_LOGOS: Map<String, LogoSpec> = mapOf(
    "AUDIUS" to LogoSpec("M47.849 43.252 36.424 23.427l-5.36-9.294L24.997 3.61a1.144 1.144 0 0 0-1.98 0L11.564 23.412l-4.68 8.092a1.144 1.144 0 0 0 .991 1.717l8.84.006a1.15 1.15 0 0 0 .991-.57L22.3 24.71l.703-1.216q.04-.07.092-.134a1.144 1.144 0 0 1 1.89.136l4.767 8.27.883 1.527q.072.128.112.269a1.145 1.145 0 0 1-1.106 1.446l-10.4-.006a1.15 1.15 0 0 0-.99.57l-4.427 7.654a1.143 1.143 0 0 0 .99 1.716l9.164.006 11.428.008 11.45.007a1.144 1.144 0 0 0 .993-1.711", 0f, 0f, 48f),
    "JAMENDO" to LogoSpec("M297.3166504,25.350893l108.1621704,62.4474792v124.8949585l-108.1621704,62.4474792 l-108.1622467-62.4475098V87.7984009L297.3166504,25.350893 M297.3166504,4.2646809L170.8932343,77.2552795v145.9811401 l126.4234161,72.9906006l126.4233398-72.9906006V77.2552795L297.3166504,4.2646809L297.3166504,4.2646809z M268.0452271,192.8660736h-17.7473907l42.4290619-85.2404556h17.7473755L268.0452271,192.8660736 z M335.7229309,192.8660736h-17.747406l42.4290771-85.2404556h17.7473755L335.7229309,192.8660736z M234.228653,192.8660736h-17.7473907l21.2146454-42.6204834h17.747406L234.228653,192.8660736z M323.1100464,150.2536469h-17.7473755l21.2146301-42.620491h17.747406L323.1100464,150.2536469z", 151.3167f, 4.2647f, 292f),
)

internal fun logoPathData(name: String): String? = LOGO_PATHS[name] ?: EXTRA_LOGOS[name]?.path

internal fun logoBox(name: String): Float =
    if (LOGO_PATHS.containsKey(name)) 24f else EXTRA_LOGOS[name]?.size ?: 24f

internal fun logoOrigin(name: String): Offset =
    if (LOGO_PATHS.containsKey(name)) Offset.Zero
    else EXTRA_LOGOS[name]?.let { Offset(it.minX, it.minY) } ?: Offset.Zero
