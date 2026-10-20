package com.triposr.mobile

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Minimal OpenGL ES 3 viewer: drag to rotate, pinch to zoom. */
class MeshView(context: Context) : GLSurfaceView(context) {
    private val renderer = MeshRenderer()
    private var lastX = 0f
    private var lastY = 0f
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            renderer.distance = (renderer.distance / d.scaleFactor).coerceIn(0.8f, 6f)
            requestRender()
            return true
        }
    })

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun setMesh(mesh: Mesh?) {
        queueEvent { renderer.upload(mesh) }
        requestRender()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastX = e.x; lastY = e.y }
            MotionEvent.ACTION_MOVE -> if (e.pointerCount == 1 && !scaleDetector.isInProgress) {
                renderer.yaw += (e.x - lastX) * 0.4f
                renderer.pitch = (renderer.pitch + (e.y - lastY) * 0.4f).coerceIn(-89f, 89f)
                lastX = e.x; lastY = e.y
                requestRender()
            }
        }
        return true
    }
}

private class MeshRenderer : GLSurfaceView.Renderer {
    @Volatile var yaw = 30f
    @Volatile var pitch = 15f
    @Volatile var distance = 2.2f
    private var program = 0
    private val vbo = IntArray(4)
    private var indexCount = 0
    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)

    private var current: Mesh? = null

    fun upload(mesh: Mesh?) {
        current = mesh
        indexCount = 0
        if (mesh == null || program == 0) return
        fun buf(a: FloatArray) = ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(a).position(0)
        for ((slot, data) in listOf(mesh.positions, mesh.normals, mesh.colors).withIndex()) {
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[slot])
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, buf(data), GLES30.GL_STATIC_DRAW)
        }
        val ib = ByteBuffer.allocateDirect(mesh.indices.size * 4).order(ByteOrder.nativeOrder()).asIntBuffer().put(mesh.indices).position(0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, vbo[3])
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, mesh.indices.size * 4, ib, GLES30.GL_STATIC_DRAW)
        indexCount = mesh.indices.size
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.12f, 0.12f, 0.14f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        program = link(
            """#version 300 es
            layout(location=0) in vec3 aPos;
            layout(location=1) in vec3 aNormal;
            layout(location=2) in vec3 aColor;
            uniform mat4 uMvp; uniform mat4 uModel;
            out vec3 vNormal; out vec3 vColor;
            void main() {
                vNormal = mat3(uModel) * aNormal;
                vColor = aColor;
                gl_Position = uMvp * vec4(aPos, 1.0);
            }""",
            """#version 300 es
            precision mediump float;
            in vec3 vNormal; in vec3 vColor;
            out vec4 fragColor;
            void main() {
                vec3 n = normalize(vNormal);
                if (!gl_FrontFacing) n = -n;
                float d = max(dot(n, normalize(vec3(0.4, 0.8, 1.0))), 0.0);
                fragColor = vec4(vColor * (0.45 + 0.65 * d), 1.0);
            }""")
        GLES30.glGenBuffers(4, vbo, 0)
        upload(current)  // the GL context is new, re-upload the mesh if we already have one
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        Matrix.perspectiveM(proj, 0, 40f, width.toFloat() / height, 0.05f, 50f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        if (indexCount == 0) return
        Matrix.setLookAtM(view, 0, 0f, 0f, distance, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.setIdentityM(model, 0)
        Matrix.rotateM(model, 0, pitch, 1f, 0f, 0f)
        Matrix.rotateM(model, 0, yaw, 0f, 1f, 0f)
        Matrix.multiplyMM(mvp, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, proj, 0, mvp.copyOf(), 0)

        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(program, "uMvp"), 1, false, mvp, 0)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(program, "uModel"), 1, false, model, 0)
        for (slot in 0..2) {
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[slot])
            GLES30.glEnableVertexAttribArray(slot)
            GLES30.glVertexAttribPointer(slot, 3, GLES30.GL_FLOAT, false, 0, 0)
        }
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, vbo[3])
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_INT, 0)
    }

    private fun link(vs: String, fs: String): Int {
        fun compile(type: Int, src: String): Int {
            val s = GLES30.glCreateShader(type)
            GLES30.glShaderSource(s, src)
            GLES30.glCompileShader(s)
            return s
        }
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, compile(GLES30.GL_VERTEX_SHADER, vs))
        GLES30.glAttachShader(p, compile(GLES30.GL_FRAGMENT_SHADER, fs))
        GLES30.glLinkProgram(p)
        return p
    }
}
