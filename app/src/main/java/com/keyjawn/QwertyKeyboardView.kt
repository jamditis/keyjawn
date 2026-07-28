package com.keyjawn

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import kotlin.math.roundToInt

/**
 * One drawing and touch surface for the QWERTY area.
 *
 * Key bounds are retained until the view size or row weights change. Display-only
 * updates, including lower/upper case changes, mutate the retained cells and dirty
 * only the affected rectangles.
 */
class QwertyKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    internal data class RenderedKey(
        val key: Key,
        val label: String,
        val backgroundColor: Int = DEFAULT_KEY_BG,
        val textColor: Int = DEFAULT_KEY_TEXT,
        val pressedColor: Int = DEFAULT_KEY_PRESSED,
        val hint: String? = null,
        val hintColor: Int = DEFAULT_KEY_HINT,
        val textSizeSp: Float = 18f,
        val accessibilityLabel: String = defaultAccessibilityLabel(key, label)
    )

    internal class KeyCell internal constructor(
        val rowIndex: Int,
        val colIndex: Int,
        internal var rendered: RenderedKey,
        val bounds: RectF = RectF()
    ) {
        val key: Key
            get() = rendered.key
    }

    internal data class PointerSample(
        val pointerId: Int,
        val action: Int,
        val x: Float,
        val y: Float,
        val downTime: Long,
        val eventTime: Long
    )

    internal data class TracePoint(
        val pointerId: Int,
        val action: Int,
        val x: Float,
        val y: Float,
        val downTime: Long,
        val eventTime: Long,
        val rowIndex: Int?,
        val colIndex: Int?
    )

    internal data class TouchTrace(
        val pointerId: Int,
        val points: List<TracePoint>,
        val crossedKeyBounds: Boolean
    )

    /**
     * Passive observation only: callbacks cannot consume or reclassify a touch.
     * Later glide phases can inspect this stream without entering the tap,
     * long-press, alt-slide, cursor-drag, or flick dispatch path.
     */
    internal interface TraceObserver {
        fun onTracePoint(point: TracePoint, crossedKeyBounds: Boolean)

        fun onTraceFinished(trace: TouchTrace, cancelled: Boolean)
    }

    internal interface Listener {
        fun onKeyPointer(cell: KeyCell, sample: PointerSample): Boolean

        fun onGapTouch(view: QwertyKeyboardView, event: MotionEvent): Boolean = false

        fun onVirtualClick(cell: KeyCell): Boolean

        fun onVirtualLongClick(cell: KeyCell): Boolean = false
    }

    private val density = resources.displayMetrics.density
    private val spScale = density * resources.configuration.fontScale
    private val rowPadding = dp(1f)
    private val keyMargin = dp(1f)
    private val keyHeight = dp(48f)
    private val rowHeight = rowPadding * 2f + keyMargin * 2f + keyHeight
    private val cornerRadius = dp(6f)
    private val shadowOffset = dp(1f)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.MONOSPACE
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        textSize = 9f * spScale
    }
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x40000000
    }
    private val dirtyRect = Rect()

    private var cells: List<List<KeyCell>> = emptyList()
    private val pressedPointers = HashMap<Int, KeyCell>()
    private val visuallyPressedPointers = HashSet<Int>()
    private val activeTraces = HashMap<Int, ActiveTrace>()
    private var gapPointerId = MotionEvent.INVALID_POINTER_ID
    private val accessibilityHelper = KeyAccessibilityHelper(this)

    internal var listener: Listener? = null
    internal var traceObserver: TraceObserver? = null

    internal var geometryGeneration: Int = 0
        private set

    val suggestedKeyboardHeight: Int
        get() = (rowHeight * EXPECTED_ROWS).roundToInt()

    init {
        isClickable = true
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        ViewCompat.setAccessibilityDelegate(this, accessibilityHelper)
    }

    internal fun submit(rows: List<List<RenderedKey>>) {
        val geometryChanged = !geometryMatches(rows)

        if (geometryChanged) {
            cells = rows.mapIndexed { rowIndex, row ->
                row.mapIndexed { colIndex, rendered ->
                    KeyCell(rowIndex, colIndex, rendered)
                }
            }
            recomputeGeometry()
            invalidate()
        } else {
            for (rowIndex in rows.indices) {
                for (colIndex in rows[rowIndex].indices) {
                    val cell = cells[rowIndex][colIndex]
                    val rendered = rows[rowIndex][colIndex]
                    if (cell.rendered != rendered) {
                        cell.rendered = rendered
                        invalidateCell(cell)
                    }
                }
            }
        }
        accessibilityHelper.invalidateRoot()
    }

    private fun geometryMatches(rows: List<List<RenderedKey>>): Boolean {
        if (cells.size != rows.size) return false
        for (rowIndex in rows.indices) {
            val oldRow = cells[rowIndex]
            val newRow = rows[rowIndex]
            if (oldRow.size != newRow.size) return false
            for (colIndex in newRow.indices) {
                if (oldRow[colIndex].key.weight != newRow[colIndex].key.weight) {
                    return false
                }
            }
        }
        return true
    }

    internal fun keyBounds(rowIndex: Int, colIndex: Int): RectF? =
        cells.getOrNull(rowIndex)?.getOrNull(colIndex)?.bounds

    internal fun keyAt(x: Float, y: Float): KeyCell? {
        for (row in cells) {
            for (cell in row) {
                if (cell.bounds.contains(x, y)) return cell
            }
        }
        return null
    }

    internal fun renderedKey(rowIndex: Int, colIndex: Int): RenderedKey? =
        cells.getOrNull(rowIndex)?.getOrNull(colIndex)?.rendered

    internal fun allCells(): List<KeyCell> = cells.flatten()

    internal fun accessibilityNode(
        rowIndex: Int,
        colIndex: Int
    ): AccessibilityNodeInfoCompat? {
        val cell = cells.getOrNull(rowIndex)?.getOrNull(colIndex) ?: return null
        return accessibilityHelper
            .getAccessibilityNodeProvider(this)
            ?.createAccessibilityNodeInfo(virtualId(cell))
    }

    internal fun performAccessibilityAction(
        rowIndex: Int,
        colIndex: Int,
        action: Int
    ): Boolean {
        val cell = cells.getOrNull(rowIndex)?.getOrNull(colIndex) ?: return false
        return accessibilityHelper
            .getAccessibilityNodeProvider(this)
            ?.performAction(virtualId(cell), action, null)
            ?: false
    }

    internal fun setPointerPressed(pointerId: Int, pressed: Boolean) {
        val cell = pressedPointers[pointerId] ?: return
        if (pressed) {
            visuallyPressedPointers.add(pointerId)
        } else {
            visuallyPressedPointers.remove(pointerId)
        }
        invalidateCell(cell)
        accessibilityHelper.invalidateVirtualView(virtualId(cell))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = suggestedMinimumWidth
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(suggestedKeyboardHeight, heightMeasureSpec)
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw || h != oldh) recomputeGeometry()
    }

    private fun recomputeGeometry() {
        if (width <= 0 || cells.isEmpty()) return

        for ((rowIndex, row) in cells.withIndex()) {
            val totalWeight = row.sumOf { it.key.weight.toDouble() }.toFloat()
            val availableWidth =
                width - rowPadding * 2f - keyMargin * 2f * row.size
            var x = rowPadding
            val top = rowIndex * rowHeight + rowPadding + keyMargin

            for (cell in row) {
                val allocatedWidth = availableWidth * cell.key.weight / totalWeight
                val left = x + keyMargin
                cell.bounds.set(left, top, left + allocatedWidth, top + keyHeight)
                x = left + allocatedWidth + keyMargin
            }
        }
        geometryGeneration++
        accessibilityHelper.invalidateRoot()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (row in cells) {
            for (cell in row) drawKey(canvas, cell)
        }
    }

    private fun drawKey(canvas: Canvas, cell: KeyCell) {
        val rendered = cell.rendered
        val bounds = cell.bounds
        val isPressed = pressedPointers.any { (pointerId, pointerCell) ->
            pointerCell === cell && pointerId in visuallyPressedPointers
        }

        val surfaceBottom = bounds.bottom - shadowOffset
        canvas.drawRoundRect(
            bounds.left,
            bounds.top + shadowOffset,
            bounds.right,
            bounds.bottom + shadowOffset,
            cornerRadius,
            cornerRadius,
            shadowPaint
        )
        keyPaint.color = if (isPressed) rendered.pressedColor else rendered.backgroundColor
        canvas.drawRoundRect(
            bounds.left,
            bounds.top,
            bounds.right,
            surfaceBottom,
            cornerRadius,
            cornerRadius,
            keyPaint
        )

        textPaint.color = rendered.textColor
        textPaint.textSize = rendered.textSizeSp * spScale
        textPaint.typeface = if (isSpecial(rendered.key.output)) {
            Typeface.create("sans-serif-medium", Typeface.NORMAL)
        } else {
            Typeface.MONOSPACE
        }
        val metrics = textPaint.fontMetrics
        val centerY = (bounds.top + surfaceBottom) / 2f
        val baseline = centerY - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(rendered.label, bounds.centerX(), baseline, textPaint)

        rendered.hint?.let { hint ->
            hintPaint.color = rendered.hintColor
            canvas.drawText(
                hint,
                bounds.right - dp(3f),
                bounds.top + dp(11f),
                hintPaint
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        observeTrace(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                val pointerId = event.getPointerId(index)
                val cell = keyAt(event.getX(index), event.getY(index))
                if (cell != null) {
                    pressedPointers[pointerId] = cell
                    visuallyPressedPointers.add(pointerId)
                    invalidateCell(cell)
                    accessibilityHelper.invalidateVirtualView(virtualId(cell))
                    listener?.onKeyPointer(
                        cell,
                        sample(event, index, MotionEvent.ACTION_DOWN)
                    )
                    return true
                }
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    gapPointerId = pointerId
                    listener?.onGapTouch(this, event)
                    // SwipeGestureDetector deliberately returns false on DOWN
                    // because it has not classified a gesture yet. The surface
                    // still has to claim the stream so Android delivers MOVE/UP.
                    return true
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (gapPointerId != MotionEvent.INVALID_POINTER_ID) {
                    listener?.onGapTouch(this, event)
                }
                for (index in 0 until event.pointerCount) {
                    val pointerId = event.getPointerId(index)
                    val cell = pressedPointers[pointerId] ?: continue
                    listener?.onKeyPointer(cell, sample(event, index, MotionEvent.ACTION_MOVE))
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                val pointerId = event.getPointerId(index)
                if (pointerId == gapPointerId) {
                    listener?.onGapTouch(this, event)
                    gapPointerId = MotionEvent.INVALID_POINTER_ID
                    return true
                }
                val cell = pressedPointers[pointerId]
                if (cell != null) {
                    listener?.onKeyPointer(cell, sample(event, index, MotionEvent.ACTION_UP))
                    if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                    pressedPointers.remove(pointerId)
                    visuallyPressedPointers.remove(pointerId)
                    invalidateCell(cell)
                    accessibilityHelper.invalidateVirtualView(virtualId(cell))
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                if (gapPointerId != MotionEvent.INVALID_POINTER_ID) {
                    listener?.onGapTouch(this, event)
                    gapPointerId = MotionEvent.INVALID_POINTER_ID
                }
                for ((pointerId, cell) in pressedPointers.toMap()) {
                    val index = event.findPointerIndex(pointerId).takeIf { it >= 0 } ?: 0
                    listener?.onKeyPointer(cell, sample(event, index, MotionEvent.ACTION_CANCEL))
                    invalidateCell(cell)
                    accessibilityHelper.invalidateVirtualView(virtualId(cell))
                }
                pressedPointers.clear()
                visuallyPressedPointers.clear()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun observeTrace(event: MotionEvent) {
        val observer = traceObserver
        if (observer == null) {
            activeTraces.clear()
            return
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                    appendCoPointerMoves(observer, event, index)
                }
                val pointerId = event.getPointerId(index)
                val trace = ActiveTrace(pointerId)
                activeTraces[pointerId] = trace
                emitTracePoint(
                    observer,
                    trace,
                    tracePoint(
                        event,
                        index,
                        MotionEvent.ACTION_DOWN,
                        event.getX(index),
                        event.getY(index),
                        event.eventTime
                    )
                )
            }

            MotionEvent.ACTION_MOVE -> {
                for (index in 0 until event.pointerCount) {
                    val pointerId = event.getPointerId(index)
                    val trace = activeTraces[pointerId] ?: continue
                    appendHistorical(observer, trace, event, index)
                    emitTracePoint(
                        observer,
                        trace,
                        tracePoint(
                            event,
                            index,
                            MotionEvent.ACTION_MOVE,
                            event.getX(index),
                            event.getY(index),
                            event.eventTime
                        )
                    )
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                if (event.actionMasked == MotionEvent.ACTION_POINTER_UP) {
                    appendCoPointerMoves(observer, event, index)
                }
                val pointerId = event.getPointerId(index)
                val trace = activeTraces.remove(pointerId) ?: return
                appendHistorical(observer, trace, event, index)
                emitTracePoint(
                    observer,
                    trace,
                    tracePoint(
                        event,
                        index,
                        MotionEvent.ACTION_UP,
                        event.getX(index),
                        event.getY(index),
                        event.eventTime
                    )
                )
                val snapshot = trace.snapshot()
                observer.onTraceFinished(snapshot, cancelled = false)
            }

            MotionEvent.ACTION_CANCEL -> {
                for ((pointerId, trace) in activeTraces.toMap()) {
                    val index = event.findPointerIndex(pointerId)
                    if (index >= 0) {
                        appendHistorical(observer, trace, event, index)
                        emitTracePoint(
                            observer,
                            trace,
                            tracePoint(
                                event,
                                index,
                                MotionEvent.ACTION_CANCEL,
                                event.getX(index),
                                event.getY(index),
                                event.eventTime
                            )
                        )
                    }
                    val snapshot = trace.snapshot()
                    observer.onTraceFinished(snapshot, cancelled = true)
                }
                activeTraces.clear()
            }
        }
    }

    private fun appendCoPointerMoves(
        observer: TraceObserver,
        event: MotionEvent,
        actionIndex: Int
    ) {
        for (index in 0 until event.pointerCount) {
            if (index == actionIndex) continue
            val pointerId = event.getPointerId(index)
            val trace = activeTraces[pointerId] ?: continue
            appendHistorical(observer, trace, event, index)
            emitTracePoint(
                observer,
                trace,
                tracePoint(
                    event,
                    index,
                    MotionEvent.ACTION_MOVE,
                    event.getX(index),
                    event.getY(index),
                    event.eventTime
                )
            )
        }
    }

    private fun appendHistorical(
        observer: TraceObserver,
        trace: ActiveTrace,
        event: MotionEvent,
        pointerIndex: Int
    ) {
        for (historyIndex in 0 until event.historySize) {
            emitTracePoint(
                observer,
                trace,
                tracePoint(
                    event,
                    pointerIndex,
                    MotionEvent.ACTION_MOVE,
                    event.getHistoricalX(pointerIndex, historyIndex),
                    event.getHistoricalY(pointerIndex, historyIndex),
                    event.getHistoricalEventTime(historyIndex)
                )
            )
        }
    }

    private fun emitTracePoint(
        observer: TraceObserver,
        trace: ActiveTrace,
        point: TracePoint
    ) {
        trace.add(point)
        observer.onTracePoint(point, trace.crossedKeyBounds)
    }

    private fun tracePoint(
        event: MotionEvent,
        pointerIndex: Int,
        action: Int,
        x: Float,
        y: Float,
        eventTime: Long
    ): TracePoint {
        val cell = keyAt(x, y)
        return TracePoint(
            pointerId = event.getPointerId(pointerIndex),
            action = action,
            x = x,
            y = y,
            downTime = event.downTime,
            eventTime = eventTime,
            rowIndex = cell?.rowIndex,
            colIndex = cell?.colIndex
        )
    }

    private class ActiveTrace(
        private val pointerId: Int
    ) {
        private val points = ArrayList<TracePoint>()
        private var firstRow: Int? = null
        private var firstCol: Int? = null
        private var firstPointSeen = false
        var crossedKeyBounds = false
            private set

        fun add(point: TracePoint) {
            points += point
            if (!firstPointSeen) {
                firstPointSeen = true
                firstRow = point.rowIndex
                firstCol = point.colIndex
                return
            }
            if (firstRow == null || firstCol == null) {
                // A trace may begin in a row margin. Use the first key it enters
                // as the origin for later cross-key detection.
                if (point.rowIndex != null && point.colIndex != null) {
                    firstRow = point.rowIndex
                    firstCol = point.colIndex
                }
            } else if (point.rowIndex != firstRow || point.colIndex != firstCol) {
                crossedKeyBounds = true
            }
        }

        fun snapshot(): TouchTrace =
            TouchTrace(
                pointerId = pointerId,
                points = points.toList(),
                crossedKeyBounds = crossedKeyBounds
            )
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean =
        accessibilityHelper.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun sample(event: MotionEvent, index: Int, action: Int): PointerSample =
        PointerSample(
            pointerId = event.getPointerId(index),
            action = action,
            x = event.getX(index),
            y = event.getY(index),
            downTime = event.downTime,
            eventTime = event.eventTime
        )

    private fun invalidateCell(cell: KeyCell) {
        val inset = dp(2f).roundToInt()
        dirtyRect.set(
            cell.bounds.left.roundToInt() - inset,
            cell.bounds.top.roundToInt() - inset,
            cell.bounds.right.roundToInt() + inset,
            cell.bounds.bottom.roundToInt() + inset
        )
        @Suppress("DEPRECATION")
        invalidate(dirtyRect)
    }

    private fun virtualId(cell: KeyCell): Int = cell.rowIndex * VIRTUAL_ROW_STRIDE + cell.colIndex

    private fun cellForVirtualId(id: Int): KeyCell? =
        cells.getOrNull(id / VIRTUAL_ROW_STRIDE)?.getOrNull(id % VIRTUAL_ROW_STRIDE)

    private inner class KeyAccessibilityHelper(host: View) : ExploreByTouchHelper(host) {
        override fun getVirtualViewAt(x: Float, y: Float): Int =
            keyAt(x, y)?.let(::virtualId) ?: INVALID_ID

        override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
            for (row in cells) {
                for (cell in row) virtualViewIds.add(virtualId(cell))
            }
        }

        override fun onPopulateEventForVirtualView(
            virtualViewId: Int,
            event: AccessibilityEvent
        ) {
            cellForVirtualId(virtualViewId)?.let { cell ->
                event.contentDescription = cell.rendered.accessibilityLabel
                event.className = android.widget.Button::class.java.name
            }
        }

        override fun onPopulateNodeForVirtualView(
            virtualViewId: Int,
            node: AccessibilityNodeInfoCompat
        ) {
            val cell = cellForVirtualId(virtualViewId) ?: return
            val rendered = cell.rendered
            val bounds = Rect(
                cell.bounds.left.roundToInt(),
                cell.bounds.top.roundToInt(),
                cell.bounds.right.roundToInt(),
                cell.bounds.bottom.roundToInt()
            )
            node.setBoundsInParent(bounds)
            node.text = rendered.label
            node.contentDescription = rendered.accessibilityLabel
            node.className = android.widget.Button::class.java.name
            node.isClickable = true
            node.isLongClickable = supportsLongClick(rendered.key)
            val pressed = pressedPointers.any { (pointerId, pointerCell) ->
                pointerCell === cell && pointerId in visuallyPressedPointers
            }
            node.isSelected = pressed
            if (pressed) node.stateDescription = "Pressed"
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
            if (node.isLongClickable) {
                node.addAction(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK)
            }
        }

        override fun onPerformActionForVirtualView(
            virtualViewId: Int,
            action: Int,
            arguments: Bundle?
        ): Boolean {
            val cell = cellForVirtualId(virtualViewId) ?: return false
            return when (action) {
                AccessibilityNodeInfoCompat.ACTION_CLICK ->
                    listener?.onVirtualClick(cell) ?: false
                AccessibilityNodeInfoCompat.ACTION_LONG_CLICK ->
                    listener?.onVirtualLongClick(cell) ?: false
                else -> false
            }
        }
    }

    private fun dp(value: Float): Float = value * density

    companion object {
        private const val EXPECTED_ROWS = 4
        private const val VIRTUAL_ROW_STRIDE = 100
        private const val DEFAULT_KEY_BG = 0xFF2B2B30.toInt()
        private const val DEFAULT_KEY_TEXT = 0xFFE8E8EC.toInt()
        private const val DEFAULT_KEY_PRESSED = 0xFF3A3A40.toInt()
        private const val DEFAULT_KEY_HINT = 0xFF6E6E78.toInt()

        private fun isSpecial(output: KeyOutput): Boolean =
            output !is KeyOutput.Character && output !is KeyOutput.QuickKey

        private fun supportsLongClick(key: Key): Boolean =
            key.output is KeyOutput.QuickKey ||
                key.output is KeyOutput.Space ||
                key.output is KeyOutput.Backspace ||
                (key.output is KeyOutput.Character &&
                    AltKeyMappings.getAlts(key.label) != null)

        private fun defaultAccessibilityLabel(key: Key, displayLabel: String): String =
            when (key.output) {
                is KeyOutput.Space -> "Space"
                is KeyOutput.Backspace -> "Delete"
                is KeyOutput.Shift -> "Shift"
                is KeyOutput.Enter -> displayLabel
                else -> displayLabel
            }
    }
}
