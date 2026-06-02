package com.example.mangareader.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Numeric keypad + PIN dot display, mirroring the Flutter PinKeypad widget.
 * Stateless: the parent owns the entered digits and reacts via [onDigit] / [onBackspace].
 */
@Composable
fun PinKeypad(
    enteredCount: Int,
    total: Int,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        PinDots(entered = enteredCount, total = total)
        Spacer(modifier = Modifier.height(32.dp))
        KeypadGrid(onDigit = onDigit, onBackspace = onBackspace)
    }
}

@Composable
fun PinDots(entered: Int, total: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        repeat(total) { index ->
            val filled = index < entered
            val color by animateColorAsState(
                targetValue = if (filled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                animationSpec = tween(durationMillis = 150),
                label = "pin_dot"
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(16.dp)
                    .background(color = color, shape = CircleShape)
            )
        }
    }
}

@Composable
private fun KeypadGrid(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit
) {
    val rows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
        listOf(null, '0', BACKSPACE)
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.Center) {
                row.forEach { key ->
                    if (key == null) {
                        Spacer(modifier = Modifier.size(80.dp))
                    } else {
                        KeyButton(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            label = if (key == BACKSPACE) null else key.toString(),
                            icon = if (key == BACKSPACE) Icons.AutoMirrored.Filled.Backspace else null,
                            onClick = if (key == BACKSPACE) onBackspace else ({ onDigit(key) })
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyButton(
    label: String?,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.size(80.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                icon != null -> Icon(
                    imageVector = icon,
                    contentDescription = "Backspace",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
                label != null -> Text(
                    text = label,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private const val BACKSPACE = '\u0008'
