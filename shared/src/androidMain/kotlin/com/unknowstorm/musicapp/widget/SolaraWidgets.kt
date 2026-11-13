package com.unknowstorm.musicapp.widget

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.text.SpannableString
import android.text.style.StyleSpan
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.unknowstorm.musicapp.shared.R

/** Opens the app when a part of the widget without a button is tapped. */
private fun openApp(context: Context) = actionStartActivity(
    context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP) }
        ?: Intent(),
)

/** Scrolling (marquee) text. Glance has no marquee, so a plain TextView is embedded. */
@Composable
private fun MarqueeText(text: String, sizeSp: Float, bold: Boolean, color: Int) {
    if (text.isBlank()) return
    val context = LocalContext.current
    val views = RemoteViews(context.packageName, R.layout.widget_marquee_text).apply {
        val content: CharSequence = if (bold) {
            SpannableString(text).apply { setSpan(StyleSpan(Typeface.BOLD), 0, length, 0) }
        } else {
            text
        }
        setTextViewText(R.id.marquee_text, content)
        setTextViewTextSize(R.id.marquee_text, TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(R.id.marquee_text, color)
    }
    AndroidRemoteViews(views, GlanceModifier.fillMaxWidth())
}

@Composable
private fun CoverOrPlaceholder(data: WidgetData, modifier: GlanceModifier) {
    val cover = data.cover
    if (cover != null) {
        Image(
            provider = ImageProvider(cover),
            contentDescription = data.title,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Box(
            modifier = modifier
                .background(GlanceTheme.colors.secondaryContainer)
                .cornerRadius(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_music_note),
                contentDescription = null,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
                modifier = GlanceModifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun RoundButton(
    icon: Int,
    description: String,
    action: androidx.glance.action.Action,
    big: Boolean,
    primary: Boolean,
    compact: Boolean = false,
    small: Boolean = false,
) {
    val box = when {
        small && big -> 36.dp
        small -> 28.dp
        compact -> 36.dp
        big -> 52.dp
        else -> 40.dp
    }
    val glyph = when {
        small && big -> 20.dp
        small -> 16.dp
        compact -> 20.dp
        big -> 28.dp
        else -> 22.dp
    }
    val bg = if (primary) GlanceTheme.colors.primary else GlanceTheme.colors.secondaryContainer
    val fg = if (primary) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSecondaryContainer
    Box(
        modifier = GlanceModifier
            .size(box)
            .background(bg)
            .cornerRadius(box / 2)
            .clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = description,
            colorFilter = ColorFilter.tint(fg),
            modifier = GlanceModifier.size(glyph),
        )
    }
}

private fun playPauseIcon(isPlaying: Boolean) =
    if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play

// ---------------------------------------------------------------- 2x2

class SolaraWidget2x2 : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                val data by WidgetStore.data.collectAsState()
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .appWidgetBackground()
                        .background(GlanceTheme.colors.widgetBackground)
                        .cornerRadius(28.dp)
                        .padding(8.dp)
                        .clickable(openApp(context)),
                ) {
                    CoverOrPlaceholder(data, GlanceModifier.fillMaxSize().cornerRadius(20.dp))

                    // Title and artist on top of the cover
                    Column(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .background(ImageProvider(R.drawable.widget_scrim_top))
                            .cornerRadius(20.dp)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        MarqueeText(
                            text = data.title ?: context.getString(R.string.widget_nothing_playing),
                            sizeSp = 13f,
                            bold = true,
                            color = 0xFFFFFFFF.toInt(),
                        )
                        MarqueeText(
                            text = data.artist.orEmpty(),
                            sizeSp = 11f,
                            bold = false,
                            color = 0xCCFFFFFF.toInt(),
                        )
                    }

                    // Controls at the bottom
                    Box(
                        modifier = GlanceModifier.fillMaxSize().padding(bottom = 6.dp),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RoundButton(
                                icon = R.drawable.ic_widget_previous,
                                description = context.getString(R.string.widget_previous),
                                action = actionRunCallback<PreviousAction>(),
                                big = false,
                                primary = false,
                                small = true,
                            )
                            Spacer(GlanceModifier.width(4.dp))
                            RoundButton(
                                icon = playPauseIcon(data.isPlaying),
                                description = context.getString(
                                    if (data.isPlaying) R.string.widget_pause else R.string.widget_play,
                                ),
                                action = actionRunCallback<PlayPauseAction>(),
                                big = true,
                                primary = true,
                                small = true,
                            )
                            Spacer(GlanceModifier.width(4.dp))
                            RoundButton(
                                icon = R.drawable.ic_widget_next,
                                description = context.getString(R.string.widget_next),
                                action = actionRunCallback<NextAction>(),
                                big = false,
                                primary = false,
                                small = true,
                            )
                        }
                    }
                }
            }
        }
    }
}

class SolaraWidget2x2Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SolaraWidget2x2()
}

// ---------------------------------------------------------------- 4x1

class SolaraWidget4x1 : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                val data by WidgetStore.data.collectAsState()
                val coverSide = (LocalSize.current.height - 16.dp).coerceAtLeast(40.dp)
                Row(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .appWidgetBackground()
                        .background(GlanceTheme.colors.widgetBackground)
                        .cornerRadius(28.dp)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Cover position and size are intentionally unchanged.
                    CoverOrPlaceholder(
                        data,
                        GlanceModifier.size(coverSide).cornerRadius(14.dp).clickable(openApp(context)),
                    )
                    Spacer(GlanceModifier.width(10.dp))

                    // Track information stays at the top; playback controls stay at the bottom.
                    Column(
                        modifier = GlanceModifier
                            .fillMaxHeight()
                            .defaultWeight()
                            .clickable(openApp(context)),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = data.title ?: context.getString(R.string.widget_nothing_playing),
                            maxLines = 1,
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            modifier = GlanceModifier.fillMaxWidth(),
                        )
                        Text(
                            text = data.artist.orEmpty(),
                            maxLines = 1,
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 12.sp,
                            ),
                            modifier = GlanceModifier.fillMaxWidth(),
                        )

                        Spacer(GlanceModifier.defaultWeight())

                        Box(
                            modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RoundButton(
                                    icon = R.drawable.ic_widget_previous,
                                    description = context.getString(R.string.widget_previous),
                                    action = actionRunCallback<PreviousAction>(),
                                    big = false,
                                    primary = false,
                                    compact = true,
                                )
                                Spacer(GlanceModifier.width(4.dp))
                                RoundButton(
                                    icon = playPauseIcon(data.isPlaying),
                                    description = context.getString(
                                        if (data.isPlaying) R.string.widget_pause else R.string.widget_play,
                                    ),
                                    action = actionRunCallback<PlayPauseAction>(),
                                    big = false,
                                    primary = true,
                                )
                                Spacer(GlanceModifier.width(4.dp))
                                RoundButton(
                                    icon = R.drawable.ic_widget_next,
                                    description = context.getString(R.string.widget_next),
                                    action = actionRunCallback<NextAction>(),
                                    big = false,
                                    primary = false,
                                    compact = true,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

class SolaraWidget4x1Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SolaraWidget4x1()
}
