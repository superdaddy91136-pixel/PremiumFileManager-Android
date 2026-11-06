package com.aurora.filemanager.ui.screens.viewers

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.WrapText
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aurora.filemanager.data.model.FileItem
import com.aurora.filemanager.ui.components.GlassSurface
import com.aurora.filemanager.ui.theme.AuroraPalette
import com.aurora.filemanager.ui.theme.AuroraRadius
import com.aurora.filemanager.ui.theme.AuroraTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Safe, Read-Only Text & Source Code Previewer:
 * - Caps read buffer at 256 KB so multi-megabyte log files never cause OutOfMemoryError.
 * - Detects UTF-8 BOM, UTF-16 LE/BE BOM, and gracefully falls back to ISO-8859-1 replacement decoding.
 * - Strictly read-only and never executes scripts or code contents.
 * - Supports line numbers and optional soft line wrapping.
 */
@Composable
fun TextViewerScreen(
    item: FileItem,
    onShare: (FileItem) -> Unit,
    onBack: () -> Unit
) {
    val colors = AuroraTheme.colors
    var lines by remember(item.path) { mutableStateOf<List<String>>(emptyList()) }
    var detectedEncoding by remember(item.path) { mutableStateOf("UTF-8") }
    var isTruncatedPreview by remember(item.path) { mutableStateOf(false) }
    var isLoading by remember(item.path) { mutableStateOf(true) }
    var wrapLines by remember { mutableStateOf(true) }

    LaunchedEffect(item.path) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val file = File(item.path)
            val maxPreviewBytes = 256 * 1024 // 256 KB safe ceiling
            if (!file.exists() || !file.canRead()) {
                lines = listOf("Unable to read file contents.")
                isLoading = false
                return@withContext
            }

            val length = file.length()
            val bytesToRead = length.coerceAtMost(maxPreviewBytes.toLong()).toInt()
            isTruncatedPreview = length > maxPreviewBytes

            val buffer = ByteArray(bytesToRead)
            val actualRead = FileInputStream(file).use { it.read(buffer) }.coerceAtLeast(0)
            val slice = if (actualRead == buffer.size) buffer else buffer.copyOf(actualRead)

            val (charset, label) = detectCharset(slice)
            detectedEncoding = label
            val decoded = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
                .decode(ByteBuffer.wrap(slice))
                .toString()

            lines = decoded.lines()
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundPrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            shape = AuroraRadius.Medium,
            elevated = true,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${lines.size} lines • $detectedEncoding • Read-only",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
                IconButton(onClick = { wrapLines = !wrapLines }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.WrapText,
                        contentDescription = "Toggle Line Wrap",
                        tint = if (wrapLines) colors.accentSecondary else colors.textSecondary
                    )
                }
                IconButton(onClick = { onShare(item) }) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "Share file",
                        tint = colors.textPrimary
                    )
                }
            }
        }

        if (isTruncatedPreview) {
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                shape = AuroraRadius.Small,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Showing first 256 KB of large file (${item.formattedSize}) to keep memory usage low.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AuroraPalette.WarningAmber
                )
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.accentSecondary)
            }
        } else {
            val horizontalScroll = rememberScrollState()
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .then(if (!wrapLines) Modifier.horizontalScroll(horizontalScroll) else Modifier),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                itemsIndexed(lines) { index, line ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp)
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted,
                            modifier = Modifier
                                .width(44.dp)
                                .padding(end = 8.dp)
                        )
                        Text(
                            text = line.ifEmpty { " " },
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = colors.textPrimary,
                            softWrap = wrapLines
                        )
                    }
                }
            }
        }
    }
}

private fun detectCharset(bytes: ByteArray): Pair<Charset, String> {
    if (bytes.size >= 3 &&
        bytes[0] == 0xEF.toByte() &&
        bytes[1] == 0xBB.toByte() &&
        bytes[2] == 0xBF.toByte()
    ) {
        return StandardCharsets.UTF_8 to "UTF-8 (BOM)"
    }
    if (bytes.size >= 2) {
        if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return StandardCharsets.UTF_16BE to "UTF-16BE"
        }
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return StandardCharsets.UTF_16LE to "UTF-16LE"
        }
    }
    // Check if valid UTF-8
    return try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
        StandardCharsets.UTF_8 to "UTF-8"
    } catch (_: Throwable) {
        StandardCharsets.ISO_8859_1 to "ISO-8859-1"
    }
}
