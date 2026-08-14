package com.paperweight.os.pairing

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paperweight.os.MainActivity
import com.paperweight.os.ui.theme.PaperweightOSTheme

class PairingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PaperweightOSTheme {
                val viewModel: PairingViewModel = viewModel()
                PairingScreen(
                    viewModel = viewModel,
                    onPaired = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    },
                )
            }
        }
    }
}
