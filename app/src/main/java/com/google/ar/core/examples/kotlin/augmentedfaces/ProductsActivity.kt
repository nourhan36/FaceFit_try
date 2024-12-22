package com.google.ar.core.examples.kotlin.augmentedfaces

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.examples.java.augmentedfaces.R

class ProductsActivity : AppCompatActivity() {
    private val TAG = "ProductsActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_products)

        try {
            val tryOnButtons = listOf(
                findViewById<Button>(R.id.try_on_button1),
                findViewById<Button>(R.id.try_on_button2),
                findViewById<Button>(R.id.try_on_button3),
                findViewById<Button>(R.id.try_on_button4),
                findViewById<Button>(R.id.try_on_button5)
            )

            val productData = listOf(
                Triple("models/lensesFrame.obj", "models/lenses.obj", "models/arms.obj"),
                Triple("models/frameModel2.obj", "models/lensesModel2.obj", "models/armsModel2.obj"),
                Triple("models/frameModel4.obj", "models/lensesModel4.obj", "models/armsModel4.obj"),
                Triple("models/frameModel5.obj", "models/lensesModel5.obj", "models/armsModel5.obj"),
                Triple("models/frameModel6.obj", "models/lensesModel6.obj", "models/armsModel6.obj")
            )

            for ((index, button) in tryOnButtons.withIndex()) {
                button.setOnClickListener {
                    val intent = Intent(this, AugmentedFacesActivity::class.java).apply {
                        putExtra("FRAME_PATH", productData[index].first)
                        putExtra("LENSES_PATH", productData[index].second)
                        putExtra("ARMS_PATH", productData[index].third)
                    }
                    startActivity(intent)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up buttons", e)
        }
    }
}