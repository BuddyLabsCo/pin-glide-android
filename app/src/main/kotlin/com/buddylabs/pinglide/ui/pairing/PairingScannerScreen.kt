package com.buddylabs.pinglide.ui.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.pairing.PairingEntryProblem
import com.buddylabs.pinglide.domain.pairing.PairingEntryState
import com.buddylabs.pinglide.platform.scanner.PairingCamera
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun PairingScannerScreen(
    state: PairingEntryState,
    model: PairingEntryViewModel,
    resumed: Boolean
) {
    val title = stringResource(R.string.pairing_scan_title)
    val cancel = stringResource(R.string.pairing_cancel)
    val insets = WindowInsets.safeDrawing
    val colors = MaterialTheme.colorScheme
    val showInvalidQrNotice = state.problem == PairingEntryProblem.InvalidQr
    val density = LocalDensity.current
    val cancelBottomInset = with(density) { insets.getBottom(this).toDp() }
    val viewfinderRequester = remember { BringIntoViewRequester() }
    Surface(Modifier.fillMaxSize()) {
        BoxWithConstraints(
            Modifier.fillMaxSize().scannerBackdrop(colors, insets).windowInsetsPadding(insets),
            contentAlignment = Alignment.TopCenter
        ) {
            val noticeGap = (maxHeight - 300.dp).coerceIn(0.dp, 52.dp)
            val cancelBottom = (maxHeight - 200.dp).coerceIn(0.dp, 28.dp)
            val cancelOffset = minOf(cancelBottom, cancelBottomInset)
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxSize()
                    .testTag("pairing-scanner").padding(horizontal = 20.dp)
                    .semantics { paneTitle = title }
            ) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val viewportHeight = maxHeight
                    LaunchedEffect(showInvalidQrNotice, viewportHeight) {
                        if (showInvalidQrNotice) viewfinderRequester.bringIntoView()
                    }
                    ScannerContent(
                        title,
                        cancel,
                        state,
                        model,
                        resumed,
                        showInvalidQrNotice,
                        Modifier.verticalScroll(rememberScrollState())
                            .heightIn(min = viewportHeight)
                            .padding(
                                top = 18.dp,
                                bottom = if (showInvalidQrNotice) 0.dp else 28.dp
                            ),
                        Modifier.padding(horizontal = 4.dp)
                            .widthIn(max = minOf(264.dp, viewportHeight))
                            .bringIntoViewRequester(viewfinderRequester)
                    )
                }
                if (showInvalidQrNotice) {
                    InvalidQrNotice()
                    Spacer(Modifier.height(noticeGap + cancelOffset))
                    ScannerCancel(cancel, model::close)
                    Spacer(Modifier.height(cancelBottom - cancelOffset))
                }
            }
        }
    }
}

@Composable
private fun ScannerContent(
    title: String,
    cancel: String,
    state: PairingEntryState,
    model: PairingEntryViewModel,
    resumed: Boolean,
    showInvalidQrNotice: Boolean,
    modifier: Modifier,
    viewfinderModifier: Modifier
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(model::close, Modifier.size(48.dp)) {
                Icon(
                    painterResource(R.drawable.ic_close),
                    contentDescription = cancel,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                title,
                Modifier.weight(1f).semantics { heading() },
                fontSize = 18.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight(740),
                letterSpacing = (-0.63).sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.width(48.dp))
        }
        Spacer(Modifier.height(38.dp))
        Text(
            stringResource(R.string.pairing_scan_instruction),
            Modifier.semantics { heading() },
            fontSize = 26.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight(750),
            letterSpacing = (-1.04).sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(9.dp))
        Text(
            stringResource(R.string.pairing_scan_description),
            Modifier.widthIn(max = 286.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(40.dp))
        ScannerViewfinder(state, model, resumed, viewfinderModifier)
        Spacer(Modifier.height(30.dp))
        if (state.problem != null && !showInvalidQrNotice) {
            PairingProblem(state.problem, model::freshScan)
        }
        if (state.pending) Text(stringResource(R.string.pairing_validating))
        if (!showInvalidQrNotice) {
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(4.dp))
            ScannerCancel(cancel, model::close)
        }
    }
}

@Composable
private fun ScannerCancel(label: String, onCancel: () -> Unit) {
    TextButton(onCancel, Modifier.fillMaxWidth().padding(horizontal = 4.dp).heightIn(min = 48.dp)) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.14.sp)
    }
}

@Composable
private fun InvalidQrNotice() {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .dropShadow(
                shape,
                Shadow(
                    radius = 24.dp,
                    color = if (isSystemInDarkTheme()) {
                        Color.Black.copy(alpha = 0.38f)
                    } else {
                        Color(0xFF0A1210).copy(alpha = 0.15f)
                    },
                    offset = DpOffset(0.dp, 8.dp)
                )
            )
            .background(colors.onSurface, shape)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painterResource(R.drawable.ic_error_filled),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = colors.surface
        )
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.pairing_scanner_invalid_qr),
            color = colors.surface,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight(650),
            letterSpacing = 0.sp
        )
    }
}

@Composable
private fun ScannerViewfinder(
    state: PairingEntryState,
    model: PairingEntryViewModel,
    resumed: Boolean,
    modifier: Modifier
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(28.dp)
    val outline = colors.onSurface.copy(alpha = if (isSystemInDarkTheme()) 0.16f else 0.17f)
    val showPreview = !state.pending &&
        (state.problem == null || state.problem == PairingEntryProblem.InvalidQr)
    val defaultAlias = stringResource(R.string.pairing_default_alias)
    Box(
        modifier.fillMaxWidth().aspectRatio(1f).testTag("pairing-viewfinder"),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.matchParentSize().background(
                colors.surfaceContainerLow.copy(alpha = 0.72f),
                shape
            )
                .border(1.dp, outline, shape)
                .clip(shape)
        ) {
            if (resumed && showPreview) {
                PairingCamera(
                    Modifier.matchParentSize(),
                    onCode = { model.scan(it, defaultAlias) },
                    onFailure = model::cameraFailed
                )
            }
        }
        Spacer(Modifier.matchParentSize().viewfinderGuides(colors.primary, showPreview))
        if (state.pending) CircularProgressIndicator()
    }
}

private fun Modifier.viewfinderGuides(color: Color, showLine: Boolean): Modifier = drawWithCache {
    val stroke = 3.dp.toPx()
    val inset = stroke / 2f
    val length = minOf(38.dp.toPx(), size.minDimension / 2f)
    val radius = minOf(18.dp.toPx(), length)
    val corner = Path().apply {
        moveTo(inset, length)
        arcTo(Rect(inset, inset, 2 * radius - inset, 2 * radius - inset), 180f, 90f, false)
        lineTo(length, inset)
    }
    val lineInset = minOf(16.dp.toPx(), size.width / 2f)
    val line = Brush.horizontalGradient(
        colors = listOf(Color.Transparent, color, Color.Transparent),
        startX = lineInset,
        endX = size.width - lineInset
    )
    onDrawBehind {
        repeat(4) { quadrant ->
            rotate(90f * quadrant) { drawPath(corner, color, style = Stroke(stroke)) }
        }
        if (showLine) {
            drawRect(
                line,
                topLeft = Offset(lineInset, center.y),
                size = Size(size.width - 2 * lineInset, 1.dp.toPx())
            )
        }
    }
}

private fun Modifier.scannerBackdrop(colors: ColorScheme, insets: WindowInsets): Modifier =
    drawWithCache {
        val angle = Math.toRadians(155.0)
        val direction = Offset(sin(angle).toFloat(), -cos(angle).toFloat())
        val gradientCenter = Offset(size.width / 2, size.height / 2)
        val gradientExtent = direction * (size.width * direction.x + size.height * direction.y) / 2f
        val background = Brush.linearGradient(
            0f to colors.surface,
            0.58f to colors.surfaceContainerLow,
            1f to colors.surfaceContainerHigh,
            start = gradientCenter - gradientExtent,
            end = gradientCenter + gradientExtent
        )
        val gridTop = insets.getTop(this).toFloat()
        val fadeHeight = (size.height - gridTop - insets.getBottom(this)).coerceAtLeast(0f) * 0.86f
        val gridLeft = insets.getLeft(this, layoutDirection).toFloat()
        val gridRight = size.width - insets.getRight(this, layoutDirection)
        val step = 32.dp.toPx()
        val stroke = 1.dp.toPx()
        val grid = colors.onSurface.copy(alpha = 0.04f)
        val verticalGrid = Brush.verticalGradient(
            colors = listOf(grid, Color.Transparent),
            startY = gridTop,
            endY = gridTop + fadeHeight
        )
        onDrawBehind {
            drawRect(background)
            var column = gridLeft
            while (column < gridRight) {
                drawRect(verticalGrid, Offset(column, gridTop), Size(stroke, fadeHeight))
                column += step
            }
            var row = 0f
            while (row < fadeHeight) {
                drawRect(
                    grid.copy(alpha = grid.alpha * (1f - row / fadeHeight)),
                    Offset(gridLeft, gridTop + row),
                    Size(gridRight - gridLeft, stroke)
                )
                row += step
            }
        }
    }
