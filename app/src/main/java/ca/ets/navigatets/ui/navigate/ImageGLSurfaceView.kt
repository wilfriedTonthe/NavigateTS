package ca.ets.navigatets.ui.navigate

import android.content.Context
import android.opengl.GLSurfaceView
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import kotlin.math.max
import kotlin.math.min

class ImageGLSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    private val renderer: ImageGLRenderer
    private var previousX: Float = 0f
    private var previousY: Float = 0f
    private var isDragging: Boolean = false
    private val TOUCH_SCALE_FACTOR = 0.003f  // Reduced for smoother movement
    private val MIN_SCALE = 0.5f
    private val MAX_SCALE = 3.0f
    private val SPRING_STIFFNESS = 1f // Base stiffness value
    private val MAX_TRANSLATE = 10f // Maximum translation distance
    private val MIN_TRANSLATE = -10f // Minimum translation distance
    private val ANIMATION_STIFFNESS = 5f // Extremely low stiffness for very slow movement
    private var springForceX = SpringForce(0f)
    private var springForceY = SpringForce(0f)
    private var springForceScale = SpringForce(1f)

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                var newScale = renderer.scale * detector.scaleFactor
                newScale = max(MIN_SCALE, min(newScale, MAX_SCALE))
                renderer.scale = newScale
                requestRender()
                return true
            }
        })

    init {
        setEGLContextClientVersion(2)
        renderer = ImageGLRenderer(context)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        // Handle scaling first
        scaleDetector.onTouchEvent(e)
        
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                previousX = e.x
                previousY = e.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging && !scaleDetector.isInProgress) {
                    // Calculate distance moved
                    val dx = (e.x - previousX) * TOUCH_SCALE_FACTOR * renderer.scale
                    val dy = (e.y - previousY) * TOUCH_SCALE_FACTOR * renderer.scale
                    
                    // Update translation
                    renderer.translateX += dx
                    renderer.translateY -= dy  // Inverted because OpenGL Y is inverted
                    
                    // Store current position for next move event
                    previousX = e.x
                    previousY = e.y
                    
                    requestRender()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                animateToCenter()
            }
        }
        return true
    }

    private fun setupSpringAnimations() {
        // Configure spring forces with extremely low stiffness for very slow movement
        springForceX = SpringForce(0f).apply {
            stiffness = ANIMATION_STIFFNESS  // Extremely low stiffness for very slow movement
            dampingRatio = 1f  // Critical damping for smooth movement without oscillation
            finalPosition = 0f
        }
        
        springForceY = SpringForce(0f).apply {
            stiffness = ANIMATION_STIFFNESS  // Extremely low stiffness for very slow movement
            dampingRatio = 1f  // Critical damping for smooth movement without oscillation
            finalPosition = 0f
        }
        
        springForceScale = SpringForce(1f).apply {
            stiffness = ANIMATION_STIFFNESS  // Extremely low stiffness for very slow movement
            dampingRatio = 1f  // Critical damping for smooth movement without oscillation
            finalPosition = 1f
        }
    }

    private fun animateToCenter() {
        val startX = renderer.translateX
        val startY = renderer.translateY
        
        android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 250  // 2.5 seconds for a very slow return
            interpolator = android.view.animation.LinearInterpolator()  // Constant speed
            
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                // Linear interpolation for straight-line movement
                renderer.translateX = startX * (1 - progress)
                renderer.translateY = startY * (1 - progress)
                requestRender()
            }
            start()
        }
        
        // Set scale immediately to avoid scale animation
        renderer.scale = 1f
        requestRender()
    }


    fun setImage(resourceId: Int) {
        renderer.setImage(resourceId, context)
        requestRender()
    }

    fun resetView() {
        renderer.scale = 1.0f
        renderer.translateX = 0.0f
        renderer.translateY = 0.0f
        requestRender()
    }
}
