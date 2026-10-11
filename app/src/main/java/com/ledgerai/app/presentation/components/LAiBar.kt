package com.ledgerai.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * The AI-first way to add anything on a list screen. Type or say one sentence; Home turns it into a
 * card to confirm. [hint] shows an example for this screen, such as "Dentist Friday at 3".
 * Manual forms stay on each screen as the second choice.
 */
@Composable
fun LAiBar(
    hint: String,
    onSend: (String) -> Unit,
    onMic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val canSend = text.isNotBlank()
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(L.Box)
            .border(1.dp, L.Line, shape)
            .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
            if (text.isEmpty()) Text(hint, style = MaterialTheme.typography.bodyLarge, color = L.InkMuted, maxLines = 1)
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = L.Ink),
                cursorBrush = SolidColor(L.Primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) { onSend(text.trim()); text = "" } }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (canSend) L.Primary else L.BoxDeep)
                .semantics { role = Role.Button; contentDescription = if (canSend) "Send" else "Speak" }
                .clickable { if (canSend) { onSend(text.trim()); text = "" } else onMic() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (canSend) Icons.Filled.Send else Icons.Filled.Mic,
                contentDescription = null,
                tint = if (canSend) L.OnPrimary else L.Primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
