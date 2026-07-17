package eu.kanade.translation.presentation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.ui.reader.TranslationEditState
import eu.kanade.translation.model.TranslationBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationEditView(
    editState: TranslationEditState,
    onSave: (eu.kanade.translation.model.PageTranslation) -> Unit,
    onCancel: () -> Unit,
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(editState.page) {
        withContext(Dispatchers.IO) {
            try {
                var tempWidth = 0
                var tempHeight = 0
                editState.page.stream?.invoke()?.use { input ->
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeStream(input, null, options)
                    tempWidth = options.outWidth
                    tempHeight = options.outHeight
                }

                if (tempWidth > 0 && tempHeight > 0) {
                    val maxDim = 2048
                    var sampleSize = 1
                    while (tempWidth / sampleSize > maxDim || tempHeight / sampleSize > maxDim) {
                        sampleSize *= 2
                    }

                    editState.page.stream?.invoke()?.use { input ->
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = sampleSize
                        }
                        bitmap = BitmapFactory.decodeStream(input, null, options)
                    }
                }
            } catch (e: Exception) {
                // Ignore
            } finally {
                isLoading = false
            }
        }
    }

    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val blocks = remember { mutableStateListOf<TranslationBlock>().apply { addAll(editState.translation.blocks) } }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f)),
    ) {
        // Main Editor Area (Image + Overlay blocks)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (selectedIndex != null) 260.dp else 80.dp),
        ) {
            val containerWidth = maxWidth
            val containerHeight = maxHeight

            val containerWidthPx = with(LocalDensity.current) { containerWidth.toPx() }
            val containerHeightPx = with(LocalDensity.current) { containerHeight.toPx() }

            if (bitmap != null) {
                val bmpWidth = bitmap!!.width.toFloat()
                val bmpHeight = bitmap!!.height.toFloat()

                val scaleX = containerWidthPx / bmpWidth
                val scaleY = containerHeightPx / bmpHeight
                val scale = minOf(scaleX, scaleY)

                val fitWidth = bmpWidth * scale
                val fitHeight = bmpHeight * scale

                val fitWidthDp = with(LocalDensity.current) { fitWidth.toDp() }
                val fitHeightDp = with(LocalDensity.current) { fitHeight.toDp() }

                val offsetX = (containerWidthPx - fitWidth) / 2
                val offsetY = (containerHeightPx - fitHeight) / 2

                val offsetXDp = with(LocalDensity.current) { offsetX.toDp() }
                val offsetYDp = with(LocalDensity.current) { offsetY.toDp() }

                Box(
                    modifier = Modifier
                        .offset(offsetXDp, offsetYDp)
                        .size(fitWidthDp, fitHeightDp),
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )

                    blocks.forEachIndexed { index, block ->
                        val isSelected = selectedIndex == index
                        val xPx = block.x * scale
                        val yPx = block.y * scale
                        val wPx = block.width * scale
                        val hPx = block.height * scale

                        val xDp = with(LocalDensity.current) { xPx.toDp() }
                        val yDp = with(LocalDensity.current) { yPx.toDp() }
                        val wDp = with(LocalDensity.current) { wPx.toDp() }
                        val hDp = with(LocalDensity.current) { hPx.toDp() }

                        Box(
                            modifier = Modifier
                                .offset(xDp, yDp)
                                .size(wDp, hDp)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f),
                                    shape = if (block.isBubble) CircleShape else RoundedCornerShape(4.dp),
                                )
                                .background(
                                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.25f),
                                    shape = if (block.isBubble) CircleShape else RoundedCornerShape(4.dp),
                                )
                                .pointerInput(index) {
                                    detectDragGestures(
                                        onDragStart = {
                                            selectedIndex = index
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            val oldBlock = blocks[index]
                                            blocks[index] = oldBlock.copy(
                                                x = maxOf(0f, oldBlock.x + dragAmount.x / scale),
                                                y = maxOf(0f, oldBlock.y + dragAmount.y / scale),
                                            )
                                        },
                                    )
                                }
                                .clickable {
                                    selectedIndex = index
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = block.translation,
                                color = Color.White,
                                fontSize = 11.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(4.dp),
                            )

                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .offset(4.dp, 4.dp)
                                        .size(18.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                        .border(1.dp, Color.White, CircleShape)
                                        .pointerInput(index) {
                                            detectDragGestures(
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    val oldBlock = blocks[index]
                                                    val newW = maxOf(20f, oldBlock.width + dragAmount.x / scale)
                                                    val newH = maxOf(20f, oldBlock.height + dragAmount.y / scale)
                                                    blocks[index] = oldBlock.copy(
                                                        width = newW,
                                                        height = newH,
                                                    )
                                                },
                                            )
                                        },
                                )
                            }
                        }
                    }
                }
            }
        }

        // Top Actions Bar (Save / Cancel / Add Block)
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding(),
            color = Color.Black.copy(alpha = 0.8f),
            tonalElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
                }

                Text(
                    text = "Edit Translations",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                )

                IconButton(
                    onClick = {
                        val bmpW = bitmap?.width?.toFloat() ?: 800f
                        val bmpH = bitmap?.height?.toFloat() ?: 1200f
                        val newBlock = TranslationBlock(
                            text = "",
                            translation = "New Translation",
                            width = 160f,
                            height = 80f,
                            x = (bmpW - 160f) / 2f,
                            y = (bmpH - 80f) / 2f,
                            symWidth = 12f,
                            symHeight = 12f,
                            angle = 0f,
                            isBubble = true,
                        )
                        blocks.add(newBlock)
                        selectedIndex = blocks.size - 1
                    },
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Block", tint = Color.White)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        val finalTranslation = editState.translation.copy(blocks = blocks.toMutableList())
                        onSave(finalTranslation)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Save", tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save", color = Color.White)
                }
            }
        }

        // Bottom Edit Sheet (only visible when a block is selected)
        if (selectedIndex != null && selectedIndex!! in blocks.indices) {
            val selectedBlock = blocks[selectedIndex!!]

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(250.dp),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                tonalElevation = 16.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Block Text",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )

                        Text(
                            text = "Bubble",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Switch(
                            checked = selectedBlock.isBubble,
                            onCheckedChange = { isBubble ->
                                blocks[selectedIndex!!] = selectedBlock.copy(isBubble = isBubble)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                            ),
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        IconButton(
                            onClick = {
                                blocks.removeAt(selectedIndex!!)
                                selectedIndex = null
                            },
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete Block",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = selectedBlock.translation,
                        onValueChange = { text ->
                            blocks[selectedIndex!!] = selectedBlock.copy(translation = text)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Translation") },
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Width: ${selectedBlock.width.roundToInt()} px",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )

                        Button(
                            onClick = {
                                val newW = maxOf(20f, selectedBlock.width - 10f)
                                blocks[selectedIndex!!] = selectedBlock.copy(width = newW)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(end = 4.dp),
                        ) {
                            Text("-")
                        }

                        Button(
                            onClick = {
                                val newW = selectedBlock.width + 10f
                                blocks[selectedIndex!!] = selectedBlock.copy(width = newW)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text("+")
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Text(
                            text = "Height: ${selectedBlock.height.roundToInt()} px",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )

                        Button(
                            onClick = {
                                val newH = maxOf(20f, selectedBlock.height - 10f)
                                blocks[selectedIndex!!] = selectedBlock.copy(height = newH)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(end = 4.dp),
                        ) {
                            Text("-")
                        }

                        Button(
                            onClick = {
                                val newH = selectedBlock.height + 10f
                                blocks[selectedIndex!!] = selectedBlock.copy(height = newH)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text("+")
                        }
                    }
                }
            }
        }
    }
}
