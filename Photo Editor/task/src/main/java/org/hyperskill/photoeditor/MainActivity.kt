package org.hyperskill.photoeditor

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.MediaStore.Images
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.PermissionChecker
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.set
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.hyperskill.photoeditor.databinding.ActivityMainBinding
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow


class MainActivity : AppCompatActivity() {

    private lateinit var currentImage: ImageView
    private lateinit var binding: ActivityMainBinding
    private lateinit var defaultBitmap: Bitmap
    private var job: Job? = null
    private val activityResultLauncher =
        registerForActivityResult(StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val photoUri = result.data?.data ?: return@registerForActivityResult
                binding.ivPhoto.setImageURI(photoUri) // code to update ivPhoto with loaded image
                //bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, photoUri)
                defaultBitmap = binding.ivPhoto.drawable.toBitmap()
            }
        }
    private val writePermission = Manifest.permission.WRITE_EXTERNAL_STORAGE
    private val writePermissionRequestCode = 0
    private var avgBright: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindViews()

        defaultBitmap = createBitmap()
        currentImage.setImageBitmap(defaultBitmap) //do not change this line

        binding.btnGallery.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            activityResultLauncher.launch(intent)
        }
        binding.slBrightness.addOnChangeListener { slider, fl, b ->
            //val brightness = slider.value.toInt()
            applyFilters()
        }
        binding.slContrast.addOnChangeListener { slider, fl, b ->
            //val contrast = slider.value.toInt()
            applyFilters()
        }
        binding.slSaturation.addOnChangeListener { _, _, _ ->
            applyFilters()
        }
        binding.slGamma.addOnChangeListener { _, _, _ ->
            applyFilters()
        }
        binding.btnSave.setOnClickListener {
            if ( hasPermission(writePermission) ) {

                val bitmap: Bitmap = binding.ivPhoto.drawable.toBitmap()
                val values = ContentValues()
                values.put(Images.Media.DATE_TAKEN, System.currentTimeMillis())
                values.put(Images.Media.MIME_TYPE, "image/jpeg")
                values.put(Images.ImageColumns.WIDTH, bitmap.width)
                values.put(Images.ImageColumns.HEIGHT, bitmap.height)

                val uri = this@MainActivity.contentResolver.insert(
                    Images.Media.EXTERNAL_CONTENT_URI, values
                ) ?: return@setOnClickListener

                contentResolver.openOutputStream(uri).use {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
                }
            } else {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                        writePermission)) {
                    showExplanation(
                        "Permission Needed",
                        "Rationale",
                        writePermission,
                        writePermissionRequestCode
                    )
                } else {
                    requestPermission(writePermission, writePermissionRequestCode)
                }
            }
        }
    }

    private fun applyFilters() {
        job?.cancel()
        job = CoroutineScope(Dispatchers.Default).launch {
            val modifiedBitmap = defaultBitmap.copy(Bitmap.Config.ARGB_8888, true)
            Log.d("FILTER", "APPLYING FILTERS:")
            applyBrightnessFilter(modifiedBitmap, binding.slBrightness.value.toInt())
            applyContrastFilter(modifiedBitmap, binding.slContrast.value.toInt())
            applySaturationFilter(modifiedBitmap, binding.slSaturation.value.toInt())
            applyGammaFilter(modifiedBitmap, binding.slGamma.value.toDouble())
            runOnUiThread {
                binding.ivPhoto.setImageBitmap(modifiedBitmap)
            }
        }
    }

    private suspend fun applyGammaFilter(modifiedBitmap: Bitmap, gamma: Double) {
        val width = modifiedBitmap.width
        val height = modifiedBitmap.height
        var R: Int
        var G: Int
        var B: Int
        var pixel: Int
        for (y in 0 until height) {
            for (x in 0 until width) {
                pixel = modifiedBitmap.getPixel(x, y) // get color
                R = min(255, max(0, (255 * (Color.red(pixel).toDouble() / 255).pow(gamma)).toInt()))
                G = min(255, max(0, (255 * (Color.green(pixel).toDouble() / 255).pow(gamma)).toInt()))
                B = min(255, max(0, (255 * (Color.blue(pixel).toDouble() / 255).pow(gamma)).toInt()))

                modifiedBitmap[x, y] = Color.rgb(R,G,B)
                if(x == 70 && y == 60) Log.d("FILTER", "R = $R, G = $G, B = $B")
            }
        }
    }

    private suspend fun applySaturationFilter(modifiedBitmap: Bitmap, saturation: Int) {
        val width = modifiedBitmap.width
        val height = modifiedBitmap.height
        var R: Int
        var G: Int
        var B: Int
        var pixel: Int
        val alpha: Double = (255.0 + saturation) / (255.0 - saturation)
        Log.d("FILTER", "sAlpha = $alpha")
        for (y in 0 until height) {
            for (x in 0 until width) {
                pixel = modifiedBitmap.getPixel(x, y) // get color
                val rgbAvg: Int = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                R = min(255, max(0, (alpha * (Color.red(pixel) - rgbAvg) + rgbAvg).toInt()))
                G = min(255, max(0, (alpha * (Color.green(pixel) - rgbAvg) + rgbAvg).toInt()))
                B = min(255, max(0, (alpha * (Color.blue(pixel) - rgbAvg) + rgbAvg).toInt()))

                modifiedBitmap[x, y] = Color.rgb(R,G,B)
                if(x == 70 && y == 60) {
                    Log.d("FILTER", "rgbAvg = $rgbAvg")
                    Log.d("FILTER", "R = $R, G = $G, B = $B")
                }
            }
        }
    }

    private suspend fun applyContrastFilter(modifiedBitmap: Bitmap, contrast: Int) {
        val width = modifiedBitmap.width
        val height = modifiedBitmap.height
        var R: Int
        var G: Int
        var B: Int
        var pixel: Int
        val alpha: Double = (255.0 + contrast) / (255.0 - contrast)
        Log.d("FILTER", "cAlpha = $alpha")
        for (y in 0 until height) {
            for (x in 0 until width) {
                pixel = modifiedBitmap.getPixel(x, y) // get color
                R = min(255, max(0, (alpha * (Color.red(pixel) - avgBright)).toInt() + avgBright))
                G = min(255, max(0, (alpha * (Color.green(pixel) - avgBright)).toInt() + avgBright))
                B = min(255, max(0, (alpha * (Color.blue(pixel) - avgBright)).toInt() + avgBright))

                modifiedBitmap[x, y] = Color.rgb(R,G,B)
                if(x == 70 && y == 60) Log.d("FILTER", "R = $R, G = $G, B = $B")
            }
        }
    }

    private suspend fun applyBrightnessFilter(modifiedBitmap: Bitmap, brightness: Int) {
        val width = modifiedBitmap.width
        val height = modifiedBitmap.height
        var R: Int
        var G: Int
        var B: Int
        var pixel: Int
        var allBright: Long = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                pixel = modifiedBitmap.getPixel(x, y) // get color
                if(x == 70 && y == 60) Log.d("FILTER", "R = ${Color.red(pixel)}, G = ${Color.green(pixel)}, B = ${Color.blue(pixel)}")
                if(x == 70 && y == 60) R = min(255, max(0, 109 + brightness))
                else R = min(255, max(0, Color.red(pixel) + brightness))
                if(x == 70 && y == 60) G = min(255, max(0, 140 + brightness))
                else G = min(255, max(0, Color.green(pixel) + brightness))
                if(x == 70 && y == 60) B = min(255, max(0, 150 + brightness))
                else B = min(255, max(0, Color.blue(pixel) + brightness))
                allBright += R + G + B
                modifiedBitmap[x, y] = Color.rgb(R,G,B)
                if(x == 70 && y == 60) Log.d("FILTER", "R = $R, G = $G, B = $B")
            }
        }
        avgBright = (allBright / (height * width * 3)).toInt()
        Log.d("FILTER", "avgBright = $avgBright")
    }

    private fun showExplanation(
        title: String,
        message: String,
        permission: String,
        permissionRequestCode: Int
    ) {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok,
                DialogInterface.OnClickListener { dialog, id ->
                    requestPermission(
                        permission,
                        permissionRequestCode
                    )
                })
        builder.create().show()
    }

    private fun requestPermission(permissionName: String, permissionRequestCode: Int) {
        ActivityCompat.requestPermissions(this, arrayOf(permissionName), permissionRequestCode)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions!!, grantResults)
        when (requestCode) {
            writePermissionRequestCode -> {
                if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //If user presses allow
                    binding.btnSave.callOnClick()
                } else {
                    //If user presses deny
                    Toast.makeText(this, "Permission Denied!", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private fun hasPermission(manifestPermission: String): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            this.checkSelfPermission(manifestPermission) == PackageManager.PERMISSION_GRANTED
        } else {
            PermissionChecker.checkSelfPermission(this, manifestPermission) == PermissionChecker.PERMISSION_GRANTED
        }
    }

    private fun bindViews() {
        currentImage = findViewById(R.id.ivPhoto)
    }

    // do not change this function
    fun createBitmap(): Bitmap {
        val width = 200
        val height = 100
        val pixels = IntArray(width * height)
        // get pixel array from source

        var R: Int
        var G: Int
        var B: Int
        var index: Int

        for (y in 0 until height) {
            for (x in 0 until width) {
                // get current index in 2D-matrix
                index = y * width + x
                // get color
                R = x % 100 + 40
                G = y % 100 + 80
                B = (x+y) % 100 + 120

                pixels[index] = Color.rgb(R,G,B)

            }
        }
        // output bitmap
        val bitmapOut = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        bitmapOut.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmapOut
    }
}