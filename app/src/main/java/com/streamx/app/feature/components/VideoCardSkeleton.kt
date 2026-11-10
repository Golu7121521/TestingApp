package com.streamx.app.feature.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** Placeholder shown while a feed loads. Shape matches [VideoCard] so the layout does not jump. */
@Composable
fun VideoCardSkeleton(modifier: Modifier = Modifier) {
    val block = MaterialTheme.colorScheme.surface
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(block),
        ) {}
        Row(modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp)) {
            Column(modifier = Modifier.size(36.dp).clip(CircleShape).background(block)) {}
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Column(modifier = Modifier.width(260.dp).height(16.dp).clip(RoundedCornerShape(4.dp)).background(block)) {}
                Column(modifier = Modifier.padding(top = 8.dp).width(160.dp).height(12.dp).clip(RoundedCornerShape(4.dp)).background(block)) {}
            }
        }
    }
}
