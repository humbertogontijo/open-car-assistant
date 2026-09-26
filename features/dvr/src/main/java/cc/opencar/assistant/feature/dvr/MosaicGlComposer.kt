package cc.opencar.assistant.feature.dvr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * GLES compositor: camera OES textures → mosaic grid on an encoder [Surface],
 * with a burned-in timestamp (live + DVR share the same pixels).
 */
class MosaicGlComposer(
    private val width: Int,
    private val height: Int,
) {
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var programOes = 0
    private var program2d = 0
    private var aPosOes = 0
    private var aTexOes = 0
    private var uTexMatrix = 0
    private var uSamplerOes = 0
    private var aPos2d = 0
    private var aTex2d = 0
    private var uSampler2d = 0
    private val texIds = IntArray(4)
    private var overlayTex = 0
    private val texMatrix = FloatArray(16)
    private var quad: FloatBuffer? = null
    private var overlayBmp: Bitmap? = null
    private var overlayCanvas: Canvas? = null
    private var lastStampSec = -1L
    private var overlayW = 1
    private var overlayH = 1
    private val stampFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).also {
        it.timeZone = TimeZone.getDefault()
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.LEFT
    }
    private val bgPaint = Paint().apply {
        color = Color.argb(140, 0, 0, 0)
    }
    var lastError: String? = null
        private set

    fun init(encoderSurface: Surface): Boolean {
        return try {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val ver = IntArray(2)
            check(EGL14.eglInitialize(eglDisplay, ver, 0, ver, 1)) { "eglInitialize" }
            val attrib = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, 0x0004,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                0x3142 /* EGL_RECORDABLE_ANDROID */, 1,
                EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val num = IntArray(1)
            check(
                EGL14.eglChooseConfig(eglDisplay, attrib, 0, configs, 0, 1, num, 0),
            ) { "eglChooseConfig" }
            val ctxAttrib = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            eglContext = EGL14.eglCreateContext(
                eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttrib, 0,
            )
            val surfAttrib = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreateWindowSurface(
                eglDisplay, configs[0], encoderSurface, surfAttrib, 0,
            )
            check(
                EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext),
            ) { "eglMakeCurrent" }
            programOes = buildProgram(VERT, FRAG_OES)
            aPosOes = GLES20.glGetAttribLocation(programOes, "aPosition")
            aTexOes = GLES20.glGetAttribLocation(programOes, "aTexCoord")
            uTexMatrix = GLES20.glGetUniformLocation(programOes, "uTexMatrix")
            uSamplerOes = GLES20.glGetUniformLocation(programOes, "sTexture")
            program2d = buildProgram(VERT_2D, FRAG_2D)
            aPos2d = GLES20.glGetAttribLocation(program2d, "aPosition")
            aTex2d = GLES20.glGetAttribLocation(program2d, "aTexCoord")
            uSampler2d = GLES20.glGetUniformLocation(program2d, "sTexture")
            GLES20.glGenTextures(4, texIds, 0)
            for (i in 0 until 4) {
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texIds[i])
                GLES20.glTexParameteri(
                    GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_MIN_FILTER,
                    GLES20.GL_LINEAR,
                )
                GLES20.glTexParameteri(
                    GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_MAG_FILTER,
                    GLES20.GL_LINEAR,
                )
                GLES20.glTexParameteri(
                    GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_WRAP_S,
                    GLES20.GL_CLAMP_TO_EDGE,
                )
                GLES20.glTexParameteri(
                    GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_WRAP_T,
                    GLES20.GL_CLAMP_TO_EDGE,
                )
            }
            val overlayIds = IntArray(1)
            GLES20.glGenTextures(1, overlayIds, 0)
            overlayTex = overlayIds[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTex)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            textPaint.textSize = (height / 28f).coerceIn(18f, 36f)
            val pad = (textPaint.textSize * 0.35f).toInt().coerceAtLeast(6)
            val sample = "0000-00-00 00:00:00"
            overlayW = (textPaint.measureText(sample) + pad * 2).toInt().coerceAtLeast(8)
            overlayH = (textPaint.textSize + pad * 2).toInt().coerceAtLeast(8)
            overlayBmp = Bitmap.createBitmap(overlayW, overlayH, Bitmap.Config.ARGB_8888)
            overlayCanvas = Canvas(overlayBmp!!)
            quad = byteBufferOf(
                -1f, -1f, 0f, 0f,
                1f, -1f, 1f, 0f,
                -1f, 1f, 0f, 1f,
                1f, 1f, 1f, 1f,
            )
            EGL14.eglMakeCurrent(
                eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT,
            )
            lastError = null
            true
        } catch (t: Throwable) {
            lastError = t.message
            Log.e(TAG, "GL init failed", t)
            release()
            false
        }
    }

    fun textureIds(): IntArray = texIds.copyOf()

    fun createBoundTextures(count: Int): List<SurfaceTexture> {
        makeCurrent()
        val n = count.coerceIn(1, 4)
        return (0 until n).map { i ->
            SurfaceTexture(texIds[i]).also {
                it.setDefaultBufferSize(640, 480)
            }
        }
    }

    fun drawFrame(textures: List<SurfaceTexture>, orderCount: Int): Boolean {
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) return false
        return try {
            makeCurrent()
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glUseProgram(programOes)
            val n = orderCount.coerceAtLeast(1).coerceAtMost(4)
            val cols = if (n <= 1) 1 else if (n <= 4) 2 else 3
            val rows = (n + cols - 1) / cols
            val cellW = width / cols
            val cellH = height / rows
            val q = quad ?: return false
            for (i in 0 until n) {
                val st = textures.getOrNull(i) ?: continue
                runCatching { st.updateTexImage() }
                st.getTransformMatrix(texMatrix)
                val col = i % cols
                val row = i / cols
                GLES20.glViewport(col * cellW, (rows - 1 - row) * cellH, cellW, cellH)
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texIds[i])
                GLES20.glUniform1i(uSamplerOes, 0)
                GLES20.glUniformMatrix4fv(uTexMatrix, 1, false, texMatrix, 0)
                q.position(0)
                GLES20.glEnableVertexAttribArray(aPosOes)
                GLES20.glVertexAttribPointer(aPosOes, 2, GLES20.GL_FLOAT, false, 16, q)
                q.position(2)
                GLES20.glEnableVertexAttribArray(aTexOes)
                GLES20.glVertexAttribPointer(aTexOes, 2, GLES20.GL_FLOAT, false, 16, q)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            }
            drawTimestampOverlay(q)
            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, System.nanoTime())
            if (!EGL14.eglSwapBuffers(eglDisplay, eglSurface)) {
                lastError = "eglSwapBuffers failed err=0x${Integer.toHexString(EGL14.eglGetError())}"
                Log.w(TAG, lastError!!)
                return false
            }
            true
        } catch (t: Throwable) {
            lastError = t.message
            Log.w(TAG, "drawFrame: ${t.message}")
            false
        }
    }

    private fun drawTimestampOverlay(q: FloatBuffer) {
        val bmp = overlayBmp ?: return
        val canvas = overlayCanvas ?: return
        val nowSec = System.currentTimeMillis() / 1000L
        if (nowSec != lastStampSec) {
            lastStampSec = nowSec
            val text = stampFmt.format(Date(nowSec * 1000L))
            canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
            canvas.drawRect(0f, 0f, overlayW.toFloat(), overlayH.toFloat(), bgPaint)
            val pad = (textPaint.textSize * 0.35f)
            val y = overlayH - pad - textPaint.descent()
            canvas.drawText(text, pad, y, textPaint)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTex)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        }
        val margin = (height * 0.015f).toInt().coerceAtLeast(8)
        val x = width - overlayW - margin
        val yGl = height - overlayH - margin // bottom-left origin for glViewport
        GLES20.glViewport(x.coerceAtLeast(0), yGl.coerceAtLeast(0), overlayW, overlayH)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUseProgram(program2d)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTex)
        GLES20.glUniform1i(uSampler2d, 0)
        q.position(0)
        GLES20.glEnableVertexAttribArray(aPos2d)
        GLES20.glVertexAttribPointer(aPos2d, 2, GLES20.GL_FLOAT, false, 16, q)
        q.position(2)
        GLES20.glEnableVertexAttribArray(aTex2d)
        GLES20.glVertexAttribPointer(aTex2d, 2, GLES20.GL_FLOAT, false, 16, q)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    fun release() {
        runCatching {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(
                    eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT,
                )
                if (eglSurface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(eglDisplay, eglSurface)
                }
                if (eglContext != EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(eglDisplay, eglContext)
                }
                EGL14.eglTerminate(eglDisplay)
            }
        }
        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
        if (programOes != 0) {
            runCatching { GLES20.glDeleteProgram(programOes) }
            programOes = 0
        }
        if (program2d != 0) {
            runCatching { GLES20.glDeleteProgram(program2d) }
            program2d = 0
        }
        if (overlayTex != 0) {
            runCatching { GLES20.glDeleteTextures(1, intArrayOf(overlayTex), 0) }
            overlayTex = 0
        }
        overlayBmp?.recycle()
        overlayBmp = null
        overlayCanvas = null
    }

    private fun makeCurrent() {
        check(
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext),
        ) { "eglMakeCurrent err=0x${Integer.toHexString(EGL14.eglGetError())}" }
    }

    private fun buildProgram(vert: String, frag: String): Int {
        val vs = loadShader(GLES20.GL_VERTEX_SHADER, vert)
        val fs = loadShader(GLES20.GL_FRAGMENT_SHADER, frag)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        val link = IntArray(1)
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, link, 0)
        check(link[0] != 0) { "link: ${GLES20.glGetProgramInfoLog(prog)}" }
        return prog
    }

    private fun loadShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        check(compiled[0] != 0) { "shader: ${GLES20.glGetShaderInfoLog(shader)}" }
        return shader
    }

    private fun byteBufferOf(vararg values: Float): FloatBuffer {
        val bb = ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder())
        val fb = bb.asFloatBuffer()
        fb.put(values)
        fb.position(0)
        return fb
    }

    companion object {
        private const val TAG = "OaaMosaicGl"
        private const val VERT = """
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            uniform mat4 uTexMatrix;
            varying vec2 vTexCoord;
            void main() {
              gl_Position = aPosition;
              vTexCoord = (uTexMatrix * aTexCoord).xy;
            }
        """
        private const val FRAG_OES = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTexCoord;
            uniform samplerExternalOES sTexture;
            void main() {
              gl_FragColor = texture2D(sTexture, vTexCoord);
            }
        """
        private const val VERT_2D = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
              gl_Position = aPosition;
              // Canvas/Bitmap row 0 is top; GLUtils uploads it at v=0 → flip for upright text.
              vTexCoord = vec2(aTexCoord.x, 1.0 - aTexCoord.y);
            }
        """
        private const val FRAG_2D = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D sTexture;
            void main() {
              gl_FragColor = texture2D(sTexture, vTexCoord);
            }
        """
    }
}
