// AugmentedFacesActivity.kt
package com.google.ar.core.examples.kotlin.augmentedfaces

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.ArCoreApk
import com.google.ar.core.ArCoreApk.InstallStatus
import com.google.ar.core.AugmentedFace
import com.google.ar.core.CameraConfig
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.Config
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

    private lateinit var frameObject: ObjectRenderer
    private lateinit var lensesObject: ObjectRenderer
    private lateinit var armsObject: ObjectRenderer

    private lateinit var frame: String
    private lateinit var lens: String
    private lateinit var arms: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        surfaceView = findViewById(R.id.surfaceview)
        displayRotationHelper = DisplayRotationHelper(this)

        frame = intent.getStringExtra("FRAME_PATH") ?: "models/lensesFrame.obj"
        lens = intent.getStringExtra("LENSES_PATH") ?: "models/lenses.obj"
        arms = intent.getStringExtra("ARMS_PATH") ?: "models/arms.obj"


        val blackButton: Button = findViewById(R.id.black_button)
        val blueButton: Button = findViewById(R.id.blue_button)
        val greyButton: Button = findViewById(R.id.grey_button)

        blackButton.setOnClickListener {
            updateFrameTexture("models/black.png")
            updateArmsTexture("models/black.png")
        }

        blueButton.setOnClickListener {
            updateFrameTexture("models/blue.png")
            updateArmsTexture("models/blue.png")
        }

        greyButton.setOnClickListener {
            updateFrameTexture("models/grey.png")
            updateArmsTexture("models/grey.png")
        }



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

    private fun updateFrameTexture(texturePath: String) {
        surfaceView?.queueEvent {
            try {
                // Load the new texture for the frame object
                frameObject.createOnGlThread(this, frame, texturePath)
                frameObject.setMaterialProperties(0.0f, 1.0f, 0.1f, 6.0f)
                frameObject.setBlendMode(ObjectRenderer.BlendMode.AlphaBlending)
            } catch (e: IOException) {
                Log.e(TAG, "Failed to load texture", e)
            }
        }
    }

    private fun updateArmsTexture(texturePath: String) {
        surfaceView?.queueEvent {
            try {
                // Load the new texture for the frame object
                armsObject.createOnGlThread(this, arms, texturePath)
                armsObject.setMaterialProperties(0.0f, 1.0f, 0.1f, 6.0f)
                armsObject.setBlendMode(ObjectRenderer.BlendMode.AlphaBlending)
            } catch (e: IOException) {
                Log.e(TAG, "Failed to load texture", e)
            }
        }
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

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)

        try {
            backgroundRenderer.createOnGlThread(this)
            augmentedFaceRenderer.createOnGlThread(this, "models/transparent.png")
            augmentedFaceRenderer.setMaterialProperties(0.0f, 1.0f, 0.1f, 6.0f)

            // Load the frame object
            val frameObject = ObjectRenderer()
            frameObject.createOnGlThread(this, frame, "models/black.png")
            frameObject.setMaterialProperties(0.0f, 1.0f, 0.1f, 6.0f)
            frameObject.setBlendMode(ObjectRenderer.BlendMode.AlphaBlending)

            // Load the lenses object
            val lensesObject = ObjectRenderer()
            lensesObject.createOnGlThread(this, lens, "models/transparent.png")
            lensesObject.setMaterialProperties(0.0f, 0.0f, 0.0f, 0.2f)
            lensesObject.setBlendMode(ObjectRenderer.BlendMode.AlphaBlending)

            val armsObject = ObjectRenderer()
            armsObject.createOnGlThread(this, arms, "models/black.png")
            armsObject.setMaterialProperties(0.0f, 0.0f, 0.0f, 0.2f)
            armsObject.setBlendMode(ObjectRenderer.BlendMode.AlphaBlending)

            // Assign to class variables
            this.frameObject = frameObject
            this.lensesObject = lensesObject
            this.armsObject = armsObject

            val error = GLES20.glGetError()
            if (error != GLES20.GL_NO_ERROR) {
                Log.e(TAG, "OpenGL Error: $error")
            }

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

                // Draw the frame
                frameObject.updateModelMatrix(glassesMatrix, scaleFactor)
                frameObject.draw(viewMatrix, projectionMatrix, colorCorrectionRgba, DEFAULT_COLOR)

                // Draw the lenses
                lensesObject.updateModelMatrix(glassesMatrix, scaleFactor)
                lensesObject.draw(viewMatrix, projectionMatrix, colorCorrectionRgba, DEFAULT_COLOR)

                armsObject.updateModelMatrix(glassesMatrix, scaleFactor)
                armsObject.draw(viewMatrix, projectionMatrix, colorCorrectionRgba, DEFAULT_COLOR)
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