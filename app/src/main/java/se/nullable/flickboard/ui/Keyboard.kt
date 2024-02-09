package se.nullable.flickboard.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import se.nullable.flickboard.model.Action
import se.nullable.flickboard.model.Layer
import se.nullable.flickboard.model.Layout
import se.nullable.flickboard.model.ShiftState

@Composable
fun Keyboard(
    layout: Layout,
    onAction: ((Action) -> Unit)?,
    modifier: Modifier = Modifier,
    enterKeyLabel: String? = null,
) {
    val enabledLayers = LocalAppSettings.current.enabledLayers.state
    val handedness = LocalAppSettings.current.handedness.state
    var shiftState: ShiftState by remember(layout) { mutableStateOf(ShiftState.Normal) }
    val shiftLayer = remember(layout) { layout.shiftLayer.mergeFallback(layout.numericLayer) }
    val mainLayer =
        remember(layout) {
            layout.mainLayer.mergeFallback(layout.numericLayer).mergeShift(shiftLayer)
        }
    val layer by remember(layout) {
        derivedStateOf {
            val activeLayer = when {
                shiftState.isShifted -> shiftLayer
                else -> mainLayer
            }
            listOfNotNull(
                when (enabledLayers.value) {
                    EnabledLayers.All -> layout.numericLayer
                    else -> null
                },
                layout.controlLayer?.let { it.mergeShift(it.autoShift()) },
                when (enabledLayers.value) {
                    EnabledLayers.Numbers -> layout.numericLayer
                    EnabledLayers.Letters, EnabledLayers.All -> activeLayer
                },
            )
                .let {
                    when (handedness.value) {
                        Handedness.RightHanded -> it
                        Handedness.LeftHanded -> it.asReversed()
                    }
                }
                .fold(Layer.empty, Layer::chain)
        }
    }
    val columns = layer.keyRows.maxOf { row -> row.sumOf { it.colspan } }
    BoxWithConstraints(modifier.background(MaterialTheme.colorScheme.surface)) {
        // Enforce portrait aspect ratio in landscape mode
        var thisWidth = maxWidth
        LocalDisplayLimits.current?.let { limits ->
            thisWidth = min(thisWidth, limits.portraitWidth)
        }
        val columnWidth = thisWidth / columns
        Column(Modifier.width(thisWidth)) {
            layer.keyRows.forEachIndexed { rowI, row ->
                Row(Modifier.padding(top = rowI.coerceAtMost(1).dp)) {
                    row.forEachIndexed { keyI, key ->
                        Key(
                            key,
                            onAction = onAction?.let { onAction ->
                                { action ->
                                    shiftState = when (action) {
                                        is Action.Shift -> action.state
                                        else -> shiftState.next()
                                    }
                                    onAction(action)
                                }
                            },
                            modifier = Modifier
                                .width(columnWidth * key.colspan)
                                .padding(start = keyI.coerceAtMost(1).dp),
                            enterKeyLabel = enterKeyLabel
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConfiguredKeyboard(
    onAction: ((Action) -> Unit)?,
    modifier: Modifier = Modifier,
    enterKeyLabel: String? = null,
) {
    Keyboard(
        layout = LocalAppSettings.current.layout.state.value.layout,
        onAction = onAction,
        modifier = modifier,
        enterKeyLabel = enterKeyLabel,
    )
}

@Composable
@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
fun KeyboardPreview() {
    var lastAction by remember { mutableStateOf<Action?>(null) }
    FlickBoardParent {
        Surface {
            Column {
                Row {
                    Text(text = "Tapped: $lastAction")
                }
                ConfiguredKeyboard(onAction = { lastAction = it })
            }
        }
    }
}