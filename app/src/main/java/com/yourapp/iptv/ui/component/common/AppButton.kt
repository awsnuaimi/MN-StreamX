package com.yourapp.iptv.ui.component.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.ui.theme.NeonBlue

@Composable
fun AppButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    var focused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp).onFocusChanged { focused = it.isFocused },
        border = BorderStroke(if (focused) 3.dp else 1.dp, if (focused) Color.White else NeonBlue),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (focused) NeonBlue else Color(0xFF172443),
            contentColor = if (focused) Color.Black else Color.White
        )
    ) { Text(text) }
}