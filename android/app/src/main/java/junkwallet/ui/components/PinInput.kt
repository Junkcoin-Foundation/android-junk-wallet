package junkwallet.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import junkwallet.ui.theme.ErrorCrimson
import junkwallet.ui.theme.PrimaryCyan
import junkwallet.ui.theme.SurfaceContainerHigh
import junkwallet.ui.theme.TextHighEmphasis

/**
 * PIN input rendered as one box per digit (OTP style), so every keystroke is
 * visible in its own slot instead of hiding in a single text field.
 *
 * The real [BasicTextField] sits underneath with transparent text: it owns the
 * IME/keyboard state, while the boxes on top handle drawing. The boxes also
 * consume taps (requesting focus instead), so a tap can never move the text
 * cursor to the middle of the PIN and make the next digit land in the wrong
 * place.
 *
 * @param pin current value (digits only)
 * @param onPinChange called with the filtered value (digits, max [boxCount])
 * @param boxCount number of boxes; PIN length may be shorter (e.g. 4 of 6)
 */
@Composable
fun PinBoxesInput(
    pin: String,
    onPinChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    boxCount: Int = 6,
    isError: Boolean = false,
    enabled: Boolean = true,
    autoFocus: Boolean = false
) {
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(Unit) {
        if (autoFocus && enabled) focusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        // Text field (underneath): transparent, only drives the keyboard.
        BasicTextField(
            value = pin,
            onValueChange = { new ->
                if (new.length <= boxCount && new.all { it.isDigit() }) {
                    onPinChange(new)
                }
            },
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .focusRequester(focusRequester),
            interactionSource = interactionSource,
            textStyle = TextStyle(color = Color.Transparent, fontSize = 18.sp),
            cursorBrush = SolidColor(Color.Transparent),
            visualTransformation = VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            decorationBox = { innerTextField -> innerTextField() }
        )

        // Digit boxes (on top): consume taps, never expose the text cursor.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .pointerInput(enabled) {
                    detectTapGestures {
                        if (enabled) focusRequester.requestFocus()
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(boxCount) { i ->
                val digit = pin.getOrNull(i)?.toString() ?: ""
                val isActive = enabled && focused && i == pin.length
                val borderColor = when {
                    isError -> ErrorCrimson
                    isActive -> PrimaryCyan
                    else -> SurfaceContainerHigh
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .border(
                            width = if (isActive) 2.dp else 1.5.dp,
                            color = borderColor,
                            shape = MaterialTheme.shapes.medium
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = digit,
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextHighEmphasis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
