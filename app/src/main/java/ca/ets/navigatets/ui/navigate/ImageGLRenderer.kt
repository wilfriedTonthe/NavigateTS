package ca.ets.navigatets.ui.navigate

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ImageGLRenderer(private val context: Context) : GLSurfaceView.Renderer {
    companion object {
        private const val VERTEX_SHADER = """
            precision mediump float;
            uniform mat4 uMVPMatrix;
            attribute vec4 vPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = uMVPMatrix * vPosition;
                vTexCoord = aTexCoord;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D uTexture;
            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord);
            }
        """
    }

    private var textureId = 0
    private var program = 0
    private var positionHandle = 0
    private var textureHandle = 0
    private var mvpMatrixHandle = 0
    private var textureCoordsHandle = 0
    private var imageResourceId = 0

    // Transform properties
    var scale: Float = 1.0f
        set(value) {
            field = value
            updateMVPMatrix()
        }
    var translateX: Float = 0.0f
        set(value) {
            field = value
            updateMVPMatrix()
        }
    var translateY: Float = 0.0f
        set(value) {
            field = value
            updateMVPMatrix()
        }

    fun setImageResource(resourceId: Int) {
        this.imageResourceId = resourceId
    }

    // Transformation matrices
    private val mvpMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)

    // Vertex and texture coordinates
    private val vertexBuffer: FloatBuffer
    private val textureCoordsBuffer: FloatBuffer

    private fun updateMVPMatrix() {
        // Reset matrices
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.setIdentityM(viewMatrix, 0)
        
        // Apply transformations to model matrix
        Matrix.translateM(modelMatrix, 0, translateX, translateY, 0f)
        Matrix.scaleM(modelMatrix, 0, scale, scale, 1f)
        
        // Calculate MVP matrix
        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvpMatrix, 0)
    }

    init {
        // Initialize vertex coordinates
        val vertexCoords = floatArrayOf(
            -1.0f, 1.0f, 0.0f,    // top left
            -1.0f, -1.0f, 0.0f,   // bottom left
            1.0f, 1.0f, 0.0f,     // top right
            1.0f, -1.0f, 0.0f     // bottom right
        )

        // Initialize texture coordinates
        val textureCoords = floatArrayOf(
            0.0f, 0.0f,    // top left
            0.0f, 1.0f,    // bottom left
            1.0f, 0.0f,    // top right
            1.0f, 1.0f     // bottom right
        )

        // Initialize buffers
        vertexBuffer = ByteBuffer.allocateDirect(vertexCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertexCoords)
        vertexBuffer.position(0)

        textureCoordsBuffer = ByteBuffer.allocateDirect(textureCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(textureCoords)
        textureCoordsBuffer.position(0)

        Matrix.setIdentityM(modelMatrix, 0)
    }

    private var weakContext: WeakReference<Context>? = null
    
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f)  // White background

        // Create and compile shaders
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)

        // Create program
        program = GLES20.glCreateProgram().also { program ->
            GLES20.glAttachShader(program, vertexShader)
            GLES20.glAttachShader(program, fragmentShader)
            GLES20.glLinkProgram(program)
        }

        // Get handles
        positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        textureHandle = GLES20.glGetUniformLocation(program, "uTexture")
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        textureCoordsHandle = GLES20.glGetAttribLocation(program, "aTexCoord")

        // Enable texture and blending
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        // If we have a pending image to load, load it now
        if (imageResourceId != 0) {
            setupTexture()
        }
    }

    private fun setupTexture() {
        // Delete existing texture if any
        if (textureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
        }

        // Generate new texture
        val textureIds = IntArray(1)
        GLES20.glGenTextures(1, textureIds, 0)
        textureId = textureIds[0]

        // Bind and set texture parameters
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        // Load the bitmap
        val options = BitmapFactory.Options()
        options.inScaled = false
        weakContext?.get()?.let { ctx ->
            val bitmap = BitmapFactory.decodeResource(ctx.resources, imageResourceId, options)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            bitmap.recycle()
        }
    }

    fun setImage(resourceId: Int, context: Context) {
        weakContext = WeakReference(context)
        imageResourceId = resourceId
        if (program != 0) {  // Only setup texture if GL context is ready
            setupTexture()
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)

        val ratio = width.toFloat() / height.toFloat()
        if (ratio > 1) {
            Matrix.orthoM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, -1f, 1f)
        } else {
            Matrix.orthoM(projectionMatrix, 0, -1f, 1f, -1f/ratio, 1f/ratio, -1f, 1f)
        }
        
        updateMVPMatrix()  // Update the MVP matrix after setting projection
        Matrix.orthoM(projectionMatrix, 0, -1f, 1f, -1/ratio, 1/ratio, -1f, 1f)

        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1.0f, 0.0f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)

        // Set vertex attributes
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(positionHandle)

        GLES20.glVertexAttribPointer(textureCoordsHandle, 2, GLES20.GL_FLOAT, false, 0, textureCoordsBuffer)
        GLES20.glEnableVertexAttribArray(textureCoordsHandle)

        // Set uniforms
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(textureHandle, 0)
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

        // Draw
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        // Cleanup
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(textureCoordsHandle)

        // Set texture parameters
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        // Load bitmap
        val options = BitmapFactory.Options()
        options.inScaled = false
        val bitmap = BitmapFactory.decodeResource(context.resources, imageResourceId, options)
        
        // Load bitmap into texture
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmap.recycle()
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
            
            // Check compilation status
            val compiled = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                val error = GLES20.glGetShaderInfoLog(shader)
                GLES20.glDeleteShader(shader)
                throw RuntimeException("Shader compilation error: $error")
            }
        }
    }
}
