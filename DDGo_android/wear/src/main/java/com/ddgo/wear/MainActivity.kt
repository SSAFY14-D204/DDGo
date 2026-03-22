package com.ddgo.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ddgo.wear.data.RecordingStateStore
import com.ddgo.wear.data.RecordingStateSyncProcessor
import com.ddgo.wear.data.WearRecordingSyncSnapshot
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private lateinit var recordingStateStore: RecordingStateStore

    private val dataClient by lazy { Wearable.getDataClient(this) }
    private val messageClient by lazy { Wearable.getMessageClient(this) }

    private val dataChangedListener: DataClient.OnDataChangedListener by lazy {
        RecordingStateSyncProcessor.createDataChangedListener(applicationContext)
    }
    private val messageReceivedListener: MessageClient.OnMessageReceivedListener by lazy {
        RecordingStateSyncProcessor.createMessageReceivedListener(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recordingStateStore = RecordingStateStore.get(applicationContext)

        setContent {
            WearApp(recordingStateStore = recordingStateStore)
        }
    }

    override fun onResume() {
        super.onResume()
        dataClient.addListener(dataChangedListener)
        messageClient.addListener(messageReceivedListener)
        RecordingStateSyncProcessor.refreshLatestRecordingState(applicationContext)
    }

    override fun onPause() {
        dataClient.removeListener(dataChangedListener)
        messageClient.removeListener(messageReceivedListener)
        super.onPause()
    }
}

@Composable
private fun WearApp(recordingStateStore: RecordingStateStore) {
    val snapshot by recordingStateStore.snapshot.collectAsState()

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF0D1B1E),
                                Color(0xFF15343A),
                                Color(0xFF2E5E57)
                            )
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 16.dp)
            ) {
                SyncDashboard(snapshot = snapshot)
            }
        }
    }
}

@Composable
private fun SyncDashboard(snapshot: WearRecordingSyncSnapshot) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "DDGo Watch Sync",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "폰에서 녹화 시작/종료하면 상태가 바로 갱신됩니다.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFD8E9E5)
        )
        StatusPill(isRecording = snapshot.isRecording)
        InfoCard(
            title = "Session",
            value = snapshot.recordingState?.sessionId?.take(8) ?: "-"
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                InfoCard(
                    title = "Updated",
                    value = snapshot.recordingState?.updatedAt.toReadableTime()
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                InfoCard(
                    title = "Source",
                    value = snapshot.lastEventSource.name
                )
            }
        }
        InfoCard(
            title = "Ignored",
            value = snapshot.ignoredEventCount.toString(),
            subtitle = "중복 또는 늦게 도착한 이벤트 수"
        )
        InfoCard(
            title = "Applied",
            value = snapshot.lastAppliedAt.toReadableTime(),
            subtitle = if (snapshot.recordingState == null) {
                "아직 워치로 동기화된 녹화 상태가 없습니다."
            } else if (snapshot.isRecording) {
                "현재 워치가 recording start 상태를 보유 중입니다."
            } else {
                "현재 워치가 recording stop 상태를 보유 중입니다."
            }
        )
    }
}

@Composable
private fun StatusPill(isRecording: Boolean) {
    val background = if (isRecording) {
        Color(0xFFB7F5D0)
    } else {
        Color(0xFFE5EFE9)
    }
    val content = if (isRecording) {
        Color(0xFF0F4A2D)
    } else {
        Color(0xFF27413A)
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = background)
    ) {
        Text(
            text = if (isRecording) "Recording ON" else "Recording IDLE",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            color = content,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun InfoCard(
    title: String,
    value: String,
    subtitle: String? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FBF9))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF50625C)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF13221D),
                fontWeight = FontWeight.SemiBold
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF5F736C)
                )
            }
        }
    }
}

private fun Long?.toReadableTime(): String {
    val value = this ?: return "-"
    return Instant.ofEpochMilli(value)
        .atZone(ZoneId.systemDefault())
        .format(TIME_FORMATTER)
}

private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
