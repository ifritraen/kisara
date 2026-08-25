package eu.kanade.presentation.entries.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

sealed class ItemCover {
    companion object {
        val Book = CoverRatio(2f / 3f)
        val Square = CoverRatio(1f)
        val Thumb = CoverRatio(16f / 9f)

        @Composable
        fun Book(
            data: Any?,
            modifier: Modifier = Modifier,
            contentDescription: String? = null,
            onClick: (() -> Unit)? = null,
        ) {
            val clickMod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            AsyncImage(
                model = data,
                contentDescription = contentDescription,
                modifier = modifier
                    .aspectRatio(Book.ratio)
                    .clip(RoundedCornerShape(4.dp))
                    .then(clickMod),
                contentScale = ContentScale.Crop,
            )
        }

        @Composable
        fun Square(
            data: Any?,
            modifier: Modifier = Modifier,
            contentDescription: String? = null,
            onClick: (() -> Unit)? = null,
        ) {
            val clickMod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            AsyncImage(
                model = data,
                contentDescription = contentDescription,
                modifier = modifier
                    .aspectRatio(Square.ratio)
                    .clip(RoundedCornerShape(4.dp))
                    .then(clickMod),
                contentScale = ContentScale.Crop,
            )
        }

        @Composable
        fun Thumb(
            data: Any?,
            modifier: Modifier = Modifier,
            contentDescription: String? = null,
            onClick: (() -> Unit)? = null,
        ) {
            val clickMod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            AsyncImage(
                model = data,
                contentDescription = contentDescription,
                modifier = modifier
                    .aspectRatio(Thumb.ratio)
                    .clip(RoundedCornerShape(4.dp))
                    .then(clickMod),
                contentScale = ContentScale.Crop,
            )
        }
    }

    data class CoverRatio(val ratio: Float)
}
