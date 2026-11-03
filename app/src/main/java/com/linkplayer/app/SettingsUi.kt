package com.linkplayer.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsList(settings: AppSettings, textColor: Color, subColor: Color) {
    Column(Modifier.fillMaxWidth()) {
        SETTING_OPTIONS.forEach { (key, title, sub) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { settings.set(key, !settings[key]) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = textColor, fontSize = 16.sp)
                    Text(sub, color = subColor, fontSize = 12.sp)
                }
                Switch(checked = settings[key], onCheckedChange = { settings.set(key, it) })
            }
        }
        Text(
            "Note: screen ke top, bottom aur side edges par gesture kaam nahi karte, taaki status bar / navigation swipe se volume-brightness na badle.",
            color = subColor, fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
        )
    }
}
