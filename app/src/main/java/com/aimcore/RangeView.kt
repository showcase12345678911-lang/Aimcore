package com.aimcore

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.MotionEvent
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** OpenGL ES 2.0 first-person practice range. It never connects to other games. */
class RangeSurfaceView(context: Context) : GLSurfaceView(context) {
    private val rangeRenderer = RangeRenderer()
    private var previousX = 0f
    private var previousY = 0f
    private var dragging = false

    init {
        setEGLContextClientVersion(2)
        setRenderer(rangeRenderer)
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY)
        setPreserveEGLContextOnPause(true)
    }

    fun setSensitivityDegreesPerDp(value: Double) {
        queueEvent { rangeRenderer.sensitivityDegreesPerDp = value.coerceIn(0.005, 2.0) }
    }

    fun setScenario(value: String) { queueEvent { rangeRenderer.scenario = value } }

    fun beginRound() { queueEvent { rangeRenderer.beginRound() } }

    fun fire() { queueEvent { rangeRenderer.fire() } }

    fun cancelRound() { queueEvent { rangeRenderer.finishRound() } }

    suspend fun endRound(): RoundMetrics = suspendCancellableCoroutine { continuation ->
        queueEvent {
            val metrics = rangeRenderer.finishRound()
            post {
                if (continuation.isActive) continuation.resume(metrics)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                previousX = event.x
                previousY = event.y
                dragging = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging && width > 0 && height > 0) {
                    val dxDp = (event.x - previousX) / resources.displayMetrics.density
                    val dyDp = (event.y - previousY) / resources.displayMetrics.density
                    queueEvent { rangeRenderer.dragLook(dxDp, dyDp) }
                    previousX = event.x
                    previousY = event.y
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                return true
            }
        }
        return true
    }
}

private data class BotTarget(
    var baseX: Float,
    val y: Float,
    val z: Float,
    val radius: Float,
    val phase: Float,
    val amplitude: Float,
    val speed: Float
) {
    fun x(time: Float): Float = baseX + sin(time * speed + phase) * amplitude
}

private class Mesh(vertices: FloatArray, indices: ShortArray) {
    val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(vertices); position(0) }
    val indexBuffer: ShortBuffer = ByteBuffer.allocateDirect(indices.size * 2)
        .order(ByteOrder.nativeOrder()).asShortBuffer().apply { put(indices); position(0) }
    val indexCount = indices.size
}

private class RangeRenderer : GLSurfaceView.Renderer {
    @Volatile var sensitivityDegreesPerDp = 0.15
    private var program = 0
    private var positionHandle = 0
    private var matrixHandle = 0
    private var colorHandle = 0
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val viewProjection = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private lateinit var sphere: Mesh
    private lateinit var floor: Mesh
    private lateinit var wall: Mesh
    private lateinit var backWall: Mesh
    @Volatile var scenario: String = "MOVING TRACKING"
    private var width = 1
    private var height = 1
    private var yaw = 0.0
    private var pitch = 0.0
    private var lastDragYaw = 0.0
    private var lastDragPitch = 0.0
    private var lastFrameNanos = 0L
    private var elapsedSeconds = 0f
    private var roundStartedNanos = 0L
    private var roundRunning = false
    private var shots = 0
    private var hits = 0
    private var overshoots = 0
    private var undershoots = 0
    private var firstHitMs = 0L
    private var pathDistance = 0.0
    private var idealPathDistance = 0.0
    private var idealYaw = 0.0
    private var idealPitch = 0.0
    private var timeOnTargetMs = 0.0

    private val targets = listOf(
        BotTarget(-3.8f, 1.55f, -8.0f, 0.55f, 0.2f, 0.35f, 0.8f),
        BotTarget(1.2f, 1.45f, -11.0f, 0.48f, 1.3f, 0.65f, 1.0f),
        BotTarget(4.1f, 1.65f, -17.0f, 0.62f, 2.1f, 0.45f, 0.7f),
        BotTarget(-1.0f, 1.35f, -21.0f, 0.36f, 2.8f, 0.9f, 1.2f),
        BotTarget(2.5f, 1.6f, -27.0f, 0.42f, 0.8f, 0.7f, 0.9f)
    )

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.025f, 0.02f, 0.025f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER,
            "attribute vec3 aPosition; uniform mat4 uMvp; void main(){ gl_Position = uMvp * vec4(aPosition, 1.0); }")
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER,
            "precision mediump float; uniform vec4 uColor; void main(){ gl_FragColor = uColor; }")
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        matrixHandle = GLES20.glGetUniformLocation(program, "uMvp")
        colorHandle = GLES20.glGetUniformLocation(program, "uColor")
        sphere = createSphere()
        floor = createQuad(
            floatArrayOf(-12f, 0f, 0f, 12f, 0f, 0f, -12f, 0f, -35f, 12f, 0f, -35f)
        )
        wall = createQuad(
            floatArrayOf(-12f, 0f, 0f, -12f, 7f, 0f, -12f, 0f, -35f, -12f, 7f, -35f)
        )
        backWall = createQuad(floatArrayOf(-12f, 0f, -35f, 12f, 0f, -35f, -12f, 7f, -35f, 12f, 7f, -35f))
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        this.width = width.coerceAtLeast(1)
        this.height = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / height.coerceAtLeast(1).toFloat()
        val top = 0.1f * kotlin.math.tan((90.0 * PI / 360.0)).toFloat()
        Matrix.frustumM(projection, 0, -top * aspect, top * aspect, -top, top, 0.1f, 100f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val delta = if (lastFrameNanos == 0L) 0.016f else ((now - lastFrameNanos) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.05f)
        lastFrameNanos = now
        elapsedSeconds += delta
        if (roundRunning) updateTargetLock(delta)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val yawRad = yaw * PI / 180.0
        val pitchRad = pitch * PI / 180.0
        val dirX = (sin(yawRad) * cos(pitchRad)).toFloat()
        val dirY = sin(pitchRad).toFloat()
        val dirZ = (-cos(yawRad) * cos(pitchRad)).toFloat()
        Matrix.setLookAtM(view, 0, 0f, 1.6f, 0f, dirX, 1.6f + dirY, dirZ, 0f, 1f, 0f)
        Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0)
        drawObject(floor, 0f, 0f, 0f, 1f, 1f, 1f, floatArrayOf(0.16f, 0.15f, 0.16f, 1f))
        drawObject(wall, 0f, 0f, 0f, 1f, 1f, 1f, floatArrayOf(0.19f, 0.10f, 0.11f, 1f))
        drawObject(wall, 0f, 0f, 0f, -1f, 1f, 1f, floatArrayOf(0.19f, 0.10f, 0.11f, 1f))
        drawObject(backWall, 0f, 0f, 0f, 1f, 1f, 1f, floatArrayOf(0.13f, 0.08f, 0.09f, 1f))
        targets.forEachIndexed { index, target ->
            val x = targetX(target)
            val radiusScale = if (scenario == "LONG-RANGE MICRO-ADJUST") 0.58f else 1f
            val color = if (index % 2 == 0) floatArrayOf(0.95f, 0.18f, 0.20f, 1f) else floatArrayOf(1f, 0.55f, 0.25f, 1f)
            drawObject(sphere, x, target.y, target.z, target.radius * radiusScale, target.radius * radiusScale, target.radius * radiusScale, color)
        }
    }

    fun dragLook(dxDp: Float, dyDp: Float) {
        val changeYaw = dxDp * sensitivityDegreesPerDp
        val changePitch = -dyDp * sensitivityDegreesPerDp
        yaw = (yaw + changeYaw).coerceIn(-175.0, 175.0)
        pitch = (pitch + changePitch).coerceIn(-75.0, 75.0)
        lastDragYaw = changeYaw
        lastDragPitch = changePitch
        if (roundRunning) pathDistance += abs(changeYaw) + abs(changePitch)
    }

    fun beginRound() {
        roundRunning = true
        shots = 0; hits = 0; overshoots = 0; undershoots = 0
        firstHitMs = 0L; pathDistance = 0.0; idealPathDistance = 0.0
        timeOnTargetMs = 0.0
        roundStartedNanos = System.nanoTime()
        idealYaw = yaw; idealPitch = pitch
    }

    fun fire() {
        if (!roundRunning) return
        shots++
        val direction = cameraDirection()
        var nearestTarget: BotTarget? = null
        var nearestDistance = Float.MAX_VALUE
        var nearestYawError = 0.0
        var nearestPitchError = 0.0
        var closestAngularError = Double.MAX_VALUE
        for (target in targets) {
            val tx = targetX(target)
            val dx = tx
            val dy = target.y - 1.6f
            val dz = target.z
            val length = sqrt(dx * dx + dy * dy + dz * dz)
            val nx = dx / length; val ny = dy / length; val nz = dz / length
            val dot = (direction[0] * nx + direction[1] * ny + direction[2] * nz).coerceIn(-1f, 1f)
            val angle = kotlin.math.acos(dot.toDouble()).toFloat()
            if (angle.toDouble() < closestAngularError) {
                closestAngularError = angle.toDouble()
                nearestYawError = atan2(tx.toDouble(), -target.z.toDouble()) * 180.0 / PI - yaw
                nearestPitchError = atan2((target.y - 1.6f).toDouble(), sqrt((tx * tx + target.z * target.z).toDouble())) * 180.0 / PI - pitch
            }
            val radiusScale = if (scenario == "LONG-RANGE MICRO-ADJUST") 0.58f else 1f
            val rayDistance = raySphere(direction, tx, target.y, target.z, target.radius * radiusScale)
            if (rayDistance != null && rayDistance < nearestDistance) {
                nearestDistance = rayDistance
                nearestTarget = target
                nearestYawError = atan2(tx.toDouble(), -target.z.toDouble()) * 180.0 / PI - yaw
                nearestPitchError = atan2((target.y - 1.6f).toDouble(), sqrt((tx * tx + target.z * target.z).toDouble())) * 180.0 / PI - pitch
            }
        }
        if (nearestTarget != null) {
            hits++
            val elapsed = (System.nanoTime() - roundStartedNanos) / 1_000_000L
            if (firstHitMs == 0L) firstHitMs = elapsed.coerceAtLeast(1L)
            val target = nearestTarget
            val targetPositionX = targetX(target)
            val targetYaw = atan2(targetPositionX.toDouble(), -target.z.toDouble()) * 180.0 / PI
            val targetPitch = atan2((target.y - 1.6f).toDouble(), sqrt((targetPositionX * targetPositionX + target.z * target.z).toDouble())) * 180.0 / PI
            idealPathDistance += abs(targetYaw - idealYaw) + abs(targetPitch - idealPitch)
            idealYaw = targetYaw; idealPitch = targetPitch
            if (scenario == "TARGET SWITCHING") target.baseX = -target.baseX
        } else {
            if (nearestYawError * lastDragYaw < 0.0 || nearestPitchError * lastDragPitch < 0.0) overshoots++ else undershoots++
        }
    }

    fun finishRound(): RoundMetrics {
        roundRunning = false
        val duration = ((System.nanoTime() - roundStartedNanos) / 1_000_000L).coerceAtLeast(1L)
        val accuracy = if (shots == 0) 0.0 else hits * 100.0 / shots
        val timeToTarget = if (firstHitMs == 0L) duration else firstHitMs
        val efficiency = if (pathDistance <= 0.001) 0.0 else (idealPathDistance / pathDistance).coerceIn(0.0, 1.0)
        val tracking = (timeOnTargetMs / duration * 100.0).coerceIn(0.0, 100.0)
        return RoundMetrics(accuracy, hits, shots, timeToTarget, overshoots, undershoots, efficiency, tracking)
    }

    private fun updateTargetLock(deltaSeconds: Float) {
        val direction = cameraDirection()
        var locked = false
        for (target in targets) {
            val x = targetX(target)
            val dx = x; val dy = target.y - 1.6f; val dz = target.z
            val length = sqrt(dx * dx + dy * dy + dz * dz)
            val nx = dx / length; val ny = dy / length; val nz = dz / length
            val dot = (direction[0] * nx + direction[1] * ny + direction[2] * nz).coerceIn(-1f, 1f)
            val angle = kotlin.math.acos(dot.toDouble())
            val radiusScale = if (scenario == "LONG-RANGE MICRO-ADJUST") 0.58f else 1f
            if (angle <= kotlin.math.atan2((target.radius * radiusScale).toDouble(), length.toDouble())) { locked = true; break }
        }
        if (locked) timeOnTargetMs += deltaSeconds * 1000.0
    }

    private fun targetX(target: BotTarget): Float = if (scenario == "STATIC FLICK" || scenario == "TARGET SWITCHING") target.baseX else target.x(elapsedSeconds)

    private fun cameraDirection(): FloatArray {
        val y = yaw * PI / 180.0
        val p = pitch * PI / 180.0
        return floatArrayOf((sin(y) * cos(p)).toFloat(), sin(p).toFloat(), (-cos(y) * cos(p)).toFloat())
    }

    private fun raySphere(direction: FloatArray, cx: Float, cy: Float, cz: Float, radius: Float): Float? {
        val oy = 1.6f
        val b = -2f * (direction[0] * cx + direction[1] * (cy - oy) + direction[2] * cz)
        val c = cx * cx + (cy - oy) * (cy - oy) + cz * cz - radius * radius
        val discriminant = b * b - 4f * c
        if (discriminant < 0f) return null
        val root = sqrt(discriminant)
        val t1 = (-b - root) / 2f
        val t2 = (-b + root) / 2f
        return when { t1 > 0f -> t1; t2 > 0f -> t2; else -> null }
    }

    private fun drawObject(mesh: Mesh, x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, color: FloatArray) {
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, x, y, z)
        Matrix.scaleM(model, 0, sx, sy, sz)
        Matrix.multiplyMM(mvp, 0, viewProjection, 0, model, 0)
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(matrixHandle, 1, false, mvp, 0)
        GLES20.glUniform4fv(colorHandle, 1, color, 0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 12, mesh.vertexBuffer)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, mesh.indexCount, GLES20.GL_UNSIGNED_SHORT, mesh.indexBuffer)
        GLES20.glDisableVertexAttribArray(positionHandle)
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val message = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw IllegalStateException("OpenGL shader compile failed: $message")
        }
        return shader
    }

    private fun createQuad(v: FloatArray): Mesh = Mesh(v, shortArrayOf(0, 1, 2, 1, 3, 2))

    private fun createSphere(): Mesh {
        val rings = 14
        val segments = 20
        val vertices = FloatArray((rings + 1) * (segments + 1) * 3)
        var cursor = 0
        for (r in 0..rings) {
            val v = r.toFloat() / rings
            val phi = (v * PI).toFloat()
            for (s in 0..segments) {
                val u = s.toFloat() / segments
                val theta = (u * 2.0 * PI).toFloat()
                vertices[cursor++] = (sin(phi) * cos(theta))
                vertices[cursor++] = cos(phi)
                vertices[cursor++] = (sin(phi) * sin(theta))
            }
        }
        val indices = ShortArray(rings * segments * 6)
        cursor = 0
        for (r in 0 until rings) for (s in 0 until segments) {
            val first = (r * (segments + 1) + s).toShort()
            val second = (first.toInt() + segments + 1).toShort()
            indices[cursor++] = first; indices[cursor++] = second; indices[cursor++] = (first + 1).toShort()
            indices[cursor++] = second; indices[cursor++] = (second + 1).toShort(); indices[cursor++] = (first + 1).toShort()
        }
        return Mesh(vertices, indices)
    }
}
