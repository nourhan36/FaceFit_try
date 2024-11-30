// AugmentedFacesActivity.kt
package com.google.ar.core.examples.kotlin.augmentedfaces

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.ArCoreApk
import com.google.ar.core.ArCoreApk.InstallStatus
import com.google.ar.core.AugmentedFace
import com.google.ar.core.CameraConfig
import com.google.ar.core.CameraConfigFilter
import com.googlde.ar.core.Config
import com.google.ar.core.Config.AugmentedFaceMode
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.examples.java.augmentedfaces.R
import com.google.ar.core.examples.kotlin.common.helpers.CameraPermissionHelper.hasCameraPermission
import com.google.ar.core.examples.kotlin.common.helpers.CameraPermissionHelper.launchPermissionSettings
import com.google.ar.core.examples.kotlin.common.helpers.CameraPermissionHelper.requestCameraPermission
import com.google.ar.core.examples.kotlin.common.helpers.CameraPermissionHelper.shouldShowRequestPermissionRationale
import com.google.ar.core.examples.kotlin.common.helpers.DisplayRotationHelper
import com.google.ar.core.examples.kotlin.common.helpers.FullScreenHelper.setFullScreenOnWindowFocusChanged
import com.google.ar.core.examples.kotlin.common.helpers.SnackbarHelper
import com.google.ar.core.examples.kotlin.common.helpers.TrackingStateHelper
import com.google.ar.core.examples.kotlin.common.rendering.BackgroundRenderer
import com.google.ar.core.examples.kotlin.common.rendering.ObjectRenderer
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import java.io.IOException
import java.util.EnumSet
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import android.opengl.Matrix

class AugmentedFacesActivity : AppCompatActivity(), GLSurfaceView.Renderer {

    private var surfaceView: GLSurfaceView? = null
    private var installRequested = false
    private var session: Session? = null
    private val messageSnackbarHelper = SnackbarHelper()
    private var displayRotationHelper: DisplayRotationHelper? = null
    private val trackingStateHelper = TrackingStateHelper(this)

    private val backgroundRenderer = BackgroundRenderer()
    private val augmentedFaceRenderer = AugmentedFaceRenderer()
    private lateinit var glassesObject: ObjectRenderer

    private val glassesMatrix = FloatArray(16)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        surfaceView = findViewById(R.id.surfaceview)
        displayRotationHelper = DisplayRotationHelper(this)

        surfaceView?.let {
            it.preserveEGLContextOnPause = true
            it.setEGLContextClientVersion(2)
            it.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            it.setRenderer(this)
            it.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            it.setWillNotDraw(false)
        }

        installRequested = false
    }

    override fun onDestroy() {
        session?.close()
        session = null
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()

        if (session == null) {
            var exception: Exception? = null
            var message: String? = null
            try {
                when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    InstallStatus.INSTALL_REQUESTED -> {
                        installRequested = true
                        return
                    }
                    InstallStatus.INSTALLED -> {}
                    else -> {}
                }

                if (!hasCameraPermission(this)) {
                    requestCameraPermission(this)
                    return
                }

                session = Session(this, EnumSet.noneOf(Session.Feature::class.java))
                val cameraConfigFilter = CameraConfigFilter(session)
                cameraConfigFilter.setFacingDirection(CameraConfig.FacingDirection.FRONT)
                val cameraConfigs = session!!.getSupportedCameraConfigs(cameraConfigFilter)
                if (cameraConfigs.isNotEmpty()) {
                    session!!.cameraConfig = cameraConfigs[0]
                } else {
                    message = "This device does not have a front-facing (selfie) camera"
                    exception = UnavailableDeviceNotCompatibleException(message)
                }
                configureSession()
            } catch (e: UnavailableArcoreNotInstalledException) {
                message = "Please install ARCore"
                exception = e
            } catch (e: UnavailableUserDeclinedInstallationException) {
                message = "Please install ARCore"
                exception = e
            } catch (e: UnavailableApkTooOldException) {
                message = "Please update ARCore"
                exception = e
            } catch (e: UnavailableSdkTooOldException) {
                message = "Please update this app"
                exception = e
            } catch (e: UnavailableDeviceNotCompatibleException) {
                message = "This device does not support AR"
                exception = e
            } catch (e: Exception) {
                message = "Failed to create AR session"
                exception = e
            }

            if (message != null) {
                messageSnackbarHelper.showError(this, message)
                Log.e(TAG, "Exception creating session", exception)
                return
            }
        }

        try {
            session!!.resume()
        } catch (e: CameraNotAvailableException) {
            messageSnackbarHelper.showError(this, "Camera not available. Try restarting the app.")
            session = null
            return
        }

        surfaceView!!.onResume()
        displayRotationHelper!!.onResume()
    }

    public override fun onPause() {
        super.onPause()
        if (session != null) {
            displayRotationHelper!!.onPause()
            surfaceView!!.onPause()
            session!!.pause()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (!hasCameraPermission(this)) {
            Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG).show()
            if (!shouldShowRequestPermissionRationale(this)) {
                launchPermissionSettings(this)
            }
            finish()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        setFullScreenOnWindowFocusChanged(this, hasFocus)
    }

    override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)

        try {
            backgroundRenderer.createOnGlThread(this)
            augmentedFaceRenderer.createOnGlThread(this, "models/te.png")
            augmentedFaceRenderer.setMaterialProperties(0.0f, 1.0f, 0.1f, 6.0f)

            glassesObject = ObjectRenderer()
            glassesObject.createOnGlThread(this, "models/Glasses.obj", "models/R.png")
            glassesObject.setMaterialProperties(0.0f, 1.0f, 0.1f, 6.0f)
            glassesObject.setBlendMode(ObjectRenderer.BlendMode.AlphaBlending)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to read an asset file", e)
        }
    }

    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        displayRotationHelper!!.onSurfaceChanged(width, height)
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        if (session == null) {
            return
        }

        displayRotationHelper!!.updateSessionIfNeeded(session!!)

        try {
            session!!.setCameraTextureName(backgroundRenderer.textureId)
            val frame = session!!.update()
            val camera = frame.camera

            val projectionMatrix = FloatArray(16)
            camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f)

            val viewMatrix = FloatArray(16)
            camera.getViewMatrix(viewMatrix, 0)

            val colorCorrectionRgba = FloatArray(4)
            frame.lightEstimate.getColorCorrection(colorCorrectionRgba, 0)

            backgroundRenderer.draw(frame)
            trackingStateHelper.updateKeepScreenOnFlag(camera.trackingState)

            val faces = session!!.getAllTrackables(AugmentedFace::class.java)
            for (face in faces) {
                if (face.trackingState != TrackingState.TRACKING) {
                    break
                }

                val scaleFactor = 1.0f
                GLES20.glDepthMask(false)

                val modelMatrix = FloatArray(16)
                face.centerPose.toMatrix(modelMatrix, 0)
                augmentedFaceRenderer.draw(projectionMatrix, viewMatrix, modelMatrix, colorCorrectionRgba, face)

                face.getRegionPose(AugmentedFace.RegionType.NOSE_TIP).toMatrix(glassesMatrix, 0)

                // Apply translation to move the glasses up
                Matrix.translateM(glassesMatrix, 0, 0.0f, 0.05f, 0.0f) // Adjust the second parameter to move up

                glassesObject.updateModelMatrix(glassesMatrix, scaleFactor)
                glassesObject.draw(viewMatrix, projectionMatrix, colorCorrectionRgba, DEFAULT_COLOR)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Exception on the OpenGL thread", t)
        } finally {
            GLES20.glDepthMask(true)
        }
    }

    private fun configureSession() {
        val config = Config(session)
        config.setAugmentedFaceMode(AugmentedFaceMode.MESH3D)
        session!!.configure(config)
    }

    companion object {
        private val TAG: String = AugmentedFacesActivity::class.java.simpleName
        private val DEFAULT_COLOR = floatArrayOf(0f, 0f, 0f, 0f)
    }
}