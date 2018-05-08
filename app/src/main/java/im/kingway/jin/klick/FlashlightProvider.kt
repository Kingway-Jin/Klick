package im.kingway.jin.klick

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager

class FlashlightProvider(private val context: Context) {
    private var camManager: CameraManager? = null

    public fun turnFlashlightOn() {
        try {
            camManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            var cameraId: String? = null // Usually front camera is at 0 position.
            if (camManager != null) {
                cameraId = camManager!!.cameraIdList[0]
                camManager!!.setTorchMode(cameraId!!, true)
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    public fun turnFlashlightOff() {
        try {
            val cameraId: String
            camManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            if (camManager != null) {
                cameraId = camManager!!.cameraIdList[0] // Usually front camera is at 0 position.
                camManager!!.setTorchMode(cameraId, false)
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    companion object {
        private val TAG = FlashlightProvider::class.java.simpleName
    }
}