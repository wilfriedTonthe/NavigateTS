package ca.ets.navigatets.ui.navigate

import android.content.Context
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.max
import kotlin.math.min

class DraggableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private var matrix = Matrix()
    private var savedMatrix = Matrix()
    private var mode = Mode.NONE
    private var startPoint = FloatArray(2)
    private var midPoint = FloatArray(2)
    private var oldDist = 1f
    private var scaleFactor = 1f
    private val MIN_SCALE = 0.5f
    private val MAX_SCALE = 3f

    private enum class Mode {
        NONE,
        DRAG,
        ZOOM
    }

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor *= detector.scaleFactor
                // Don't let the object get too small or too large
                scaleFactor = max(MIN_SCALE, min(scaleFactor, MAX_SCALE))

                matrix.getValues(matrixValues)
                val currentScale = matrixValues[Matrix.MSCALE_X]
                
                if (currentScale * detector.scaleFactor > MAX_SCALE) {
                    val scaleTo = MAX_SCALE / currentScale
                    matrix.postScale(scaleTo, scaleTo, detector.focusX, detector.focusY)
                } else if (currentScale * detector.scaleFactor < MIN_SCALE) {
                    val scaleTo = MIN_SCALE / currentScale
                    matrix.postScale(scaleTo, scaleTo, detector.focusX, detector.focusY)
                } else {
                    matrix.postScale(detector.scaleFactor, detector.scaleFactor, 
                        detector.focusX, detector.focusY)
                }
                
                imageMatrix = matrix
                invalidate()
                return true
            }
    })

    private val matrixValues = FloatArray(9)

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (drawable != null) {
            fitToScreen()
        }
    }

    override fun setImageResource(resId: Int) {
        super.setImageResource(resId)
        fitToScreen()
    }

    private fun fitToScreen() {
        if (drawable == null || width == 0 || height == 0) return

        // Reset the matrix
        matrix.reset()

        val imageWidth = drawable.intrinsicWidth.toFloat()
        val imageHeight = drawable.intrinsicHeight.toFloat()
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        // Calculate the scale needed to fit the image
        val scaleX = viewWidth / imageWidth
        val scaleY = viewHeight / imageHeight
        val scale = min(scaleX, scaleY)

        // Scale the image
        matrix.postScale(scale, scale)

        // Center the image
        val redundantXSpace = viewWidth - (scale * imageWidth)
        val redundantYSpace = viewHeight - (scale * imageHeight)
        matrix.postTranslate(redundantXSpace / 2, redundantYSpace / 2)

        // Save the initial matrix for reset
        savedMatrix.set(matrix)
        imageMatrix = matrix
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                savedMatrix.set(matrix)
                startPoint[0] = event.x
                startPoint[1] = event.y
                mode = Mode.DRAG
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                oldDist = spacing(event)
                if (oldDist > 10f) {
                    savedMatrix.set(matrix)
                    midPoint(midPoint, event)
                    mode = Mode.ZOOM
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == Mode.DRAG) {
                    matrix.set(savedMatrix)
                    val dx = event.x - startPoint[0]
                    val dy = event.y - startPoint[1]
                    matrix.postTranslate(dx, dy)
                } else if (mode == Mode.ZOOM) {
                    val newDist = spacing(event)
                    if (newDist > 10f) {
                        matrix.set(savedMatrix)
                        val scale = newDist / oldDist
                        matrix.postScale(scale, scale, midPoint[0], midPoint[1])
                    }
                }
                imageMatrix = matrix
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                mode = Mode.NONE
            }
        }

        return true
    }

    private fun spacing(event: MotionEvent): Float {
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return kotlin.math.sqrt(x * x + y * y)
    }

    private fun midPoint(point: FloatArray, event: MotionEvent) {
        val x = event.getX(0) + event.getX(1)
        val y = event.getY(0) + event.getY(1)
        point[0] = x / 2
        point[1] = y / 2
    }

    // Reset image position and scale
    fun resetPosition() {
        matrix.set(savedMatrix)
        scaleFactor = 1f
        imageMatrix = matrix
        invalidate()
    }
}
