package com.securechat.features.chat

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.securechat.core.model.DeliveryStatus
import com.securechat.core.model.Message
import com.securechat.core.model.MessageType
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    conversationTitle: String,
    onNavigateBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val inputText by viewModel.messageInput.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Auto-scroll to bottom when new message arrives
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // ── Media pickers ────────────────────────────────────────────────────────
    val photoVideoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri)
            val fileName = queryFileName(context.contentResolver, uri)
            viewModel.sendMedia(uri, mimeType, fileName)
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri)
            val fileName = queryFileName(context.contentResolver, uri)
            viewModel.sendMedia(uri, mimeType, fileName)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = viewModel.conversationTitle.take(1).uppercase(),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = viewModel.conversationTitle,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Messages List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(message = message)
                }
            }

            // Input Area
            ChatInputBar(
                text = inputText,
                onTextChanged = viewModel::onMessageInputChanged,
                onSend = {
                    viewModel.sendMessage()
                    coroutineScope.launch {
                        if (messages.isNotEmpty()) {
                            listState.animateScrollToItem(messages.size)
                        }
                    }
                },
                onPickPhotoVideo = {
                    photoVideoPicker.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageAndVideo,
                        ),
                    )
                },
                onPickFile = {
                    filePicker.launch(arrayOf("*/*"))
                },
            )
        }
    }
}

/** Looks up a content Uri's display filename, falling back to the last path segment. */
private fun queryFileName(resolver: ContentResolver, uri: Uri): String? {
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) {
            return cursor.getString(nameIndex)
        }
    }
    return uri.lastPathSegment
}

/** Opens a locally-decrypted media file with the system's default viewer via FileProvider. */
private fun openLocalMediaFile(context: android.content.Context, path: String, mimeType: String) {
    val file = File(path)
    if (!file.exists()) return
    val contentUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(contentUri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

private fun guessMimeTypeFromFileName(fileName: String?): String {
    val ext = fileName?.substringAfterLast('.', "")?.lowercase()
    return when (ext) {
        "pdf"          -> "application/pdf"
        "mp4", "mov"   -> "video/*"
        "mp3", "m4a"   -> "audio/*"
        "doc", "docx"  -> "application/msword"
        "png", "jpg", "jpeg", "webp" -> "image/*"
        else           -> "*/*"
    }
}

@Composable
private fun MessageBubble(message: Message) {
    val isMine = message.isMine
    val context = LocalContext.current

    val bubbleColor = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val alignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
    val shape = if (isMine) {
        RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Column(
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
        ) {
            when (message.type) {
                MessageType.IMAGE -> {
                    Box(
                        modifier = Modifier
                            .clip(shape)
                            .widthIn(max = 240.dp)
                            .heightIn(max = 320.dp)
                            .clickable(enabled = message.localMediaPath != null) {
                                message.localMediaPath?.let { openLocalMediaFile(context, it, "image/*") }
                            },
                    ) {
                        AsyncImage(
                            model = message.localMediaPath ?: message.mediaUrl,
                            contentDescription = message.mediaThumbnail ?: "Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .background(bubbleColor)
                                .fillMaxWidth()
                                .heightIn(min = 120.dp, max = 320.dp),
                        )
                    }
                }
                MessageType.VIDEO, MessageType.AUDIO, MessageType.FILE -> {
                    val icon = when (message.type) {
                        MessageType.VIDEO -> Icons.Default.Movie
                        MessageType.AUDIO -> Icons.Default.MusicNote
                        else               -> Icons.Default.InsertDriveFile
                    }
                    Row(
                        modifier = Modifier
                            .clip(shape)
                            .background(bubbleColor)
                            .clickable(enabled = message.localMediaPath != null) {
                                message.localMediaPath?.let {
                                    openLocalMediaFile(
                                        context,
                                        it,
                                        guessMimeTypeFromFileName(message.mediaThumbnail),
                                    )
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .widthIn(max = 260.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(textColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (message.type == MessageType.VIDEO) Icons.Default.PlayArrow else icon,
                                contentDescription = null,
                                tint = textColor,
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = message.mediaThumbnail ?: message.type.name.lowercase().replaceFirstChar { it.uppercase() },
                            color = textColor,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                        )
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .clip(shape)
                            .background(bubbleColor)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .widthIn(max = 280.dp)
                    ) {
                        Text(
                            text = message.body,
                            color = textColor,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = formatMessageTime(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )

                if (isMine) {
                    val statusText = when (message.deliveryStatus) {
                        DeliveryStatus.PENDING -> "•"
                        DeliveryStatus.SENT -> "✓"
                        DeliveryStatus.DELIVERED -> "✓✓"
                        DeliveryStatus.READ -> "✓✓ (Read)"
                        DeliveryStatus.FAILED -> "!"
                    }
                    val statusColor = if (message.deliveryStatus == DeliveryStatus.READ)
                        MaterialTheme.colorScheme.secondary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    text: String,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onPickPhotoVideo: () -> Unit,
    onPickFile: () -> Unit,
) {
    var showAttachMenu by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 8.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                IconButton(onClick = { showAttachMenu = true }) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Attach",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                DropdownMenu(
                    expanded = showAttachMenu,
                    onDismissRequest = { showAttachMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Photo or video") },
                        leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                        onClick = {
                            showAttachMenu = false
                            onPickPhotoVideo()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("File") },
                        leadingIcon = { Icon(Icons.Default.InsertDriveFile, contentDescription = null) },
                        onClick = {
                            showAttachMenu = false
                            onPickFile()
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            OutlinedTextField(
                value = text,
                onValueChange = onTextChanged,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message") },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                maxLines = 4
            )

            Spacer(modifier = Modifier.width(4.dp))

            AnimatedVisibility(
                visible = text.isNotBlank(),
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                IconButton(
                    onClick = onSend,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private fun formatMessageTime(timestamp: Long): String {
    val date = Date(timestamp)
    val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
    return fmt.format(date)
}
