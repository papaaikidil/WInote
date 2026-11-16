package id.hananote.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.Flow

/**
 * Sumber interaksi tanpa sorotan "melayang" dan fokus. Pena yang melayang di atas tombol lalu pindah ke
 * kanvas kadang tidak memberi tahu bahwa ia sudah pergi, sehingga tombolnya tetap tampak tersorot seperti
 * aktif. Tombol di bilah alat memakai ini supaya hanya tampil aktif bila memang dipilih.
 */
internal class NoHoverInteractionSource : MutableInteractionSource {
    private val inner = MutableInteractionSource()
    override val interactions: Flow<Interaction> get() = inner.interactions

    override suspend fun emit(interaction: Interaction) {
        if (!ignored(interaction)) inner.emit(interaction)
    }

    override fun tryEmit(interaction: Interaction): Boolean = if (ignored(interaction)) true else inner.tryEmit(interaction)

    private fun ignored(i: Interaction) = i is HoverInteraction || i is FocusInteraction
}

/** FilterChip tanpa sorotan melayang/fokus. */
@Composable
internal fun ToolChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        interactionSource = remember { NoHoverInteractionSource() },
    )
}

/** Seperti clickable, tapi tanpa sorotan melayang/fokus yang bisa tertinggal. */
@Composable
internal fun Modifier.toolClick(onClickLabel: String? = null, onClick: () -> Unit): Modifier =
    clickable(
        interactionSource = remember { NoHoverInteractionSource() },
        indication = ripple(),
        onClickLabel = onClickLabel,
        onClick = onClick,
    )
