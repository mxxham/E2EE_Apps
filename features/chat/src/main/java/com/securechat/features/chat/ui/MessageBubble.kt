package com.securechat.features.chat.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securechat.features.chat.ChatMessage
import com.securechat.features.chat.DeliveryStatus

/**
 * MessageBubble renders individual message bubbles, styling depending on sender,
 * and including visual indicator checkmarks matching the current delivery status.
 */
@Composable
fun MessageBubble(
    message: ChatMessage,
    isCurrentUser: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = if (isCurrentUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isCurrentUser) 16.dp else 0.dp,
                bottomEnd = if (isCurrentUser) 0.dp else 16.dp
            ),
            color = if (isCurrentUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isCurrentUser) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = message.text,
                    fontSize = 16.sp
                )
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "10:24 AM", // Format message.timestamp
                        fontSize = 10.sp,
                        color = if (isCurrentUser) Color.LightGray else Color.Gray
                    )
                    if (isCurrentUser) {
                        Spacer(modifier = Modifier.width(4.dp))
                        DeliveryStatusIndicator(status = message.deliveryStatus)
                    }
                }
            }
        }
    }
}

/**
 * Custom checkmark visual indicator corresponding to SENT, DELIVERED, and READ states.
 */
@Composable
fun DeliveryStatusIndicator(status: DeliveryStatus) {
    val (iconRes, tint) = when (status) {
        DeliveryStatus.PENDING -> Pair(android.R.drawable.presence_offline, Color.Gray)
        DeliveryStatus.SENT -> Pair(android.R.drawable.checkbox_off_background, Color.LightGray)
        DeliveryStatus.DELIVERED -> Pair(android.R.drawable.checkbox_on_background, Color.LightGray)
        DeliveryStatus.READ -> Pair(android.R.drawable.checkbox_on_background, Color.Cyan) // Cyan double checks
    }
    Icon(
        painter = painterResource(id = iconRes),
        contentDescription = status.name,
        tint = tint,
        modifier = Modifier.width(14.dp)
    )
}
