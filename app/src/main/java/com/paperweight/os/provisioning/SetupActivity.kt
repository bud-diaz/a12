package com.paperweight.os.provisioning

import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.paperweight.os.MainActivity
import com.paperweight.os.R
import com.paperweight.os.ui.theme.PaperweightOSTheme

class SetupActivity : ComponentActivity() {

    private val devicePolicyManager by lazy {
        getSystemService(DevicePolicyManager::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (devicePolicyManager.isDeviceOwnerApp(packageName)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContent {
            PaperweightOSTheme {
                SetupPendingScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (devicePolicyManager.isDeviceOwnerApp(packageName)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}

@Composable
private fun SetupPendingScreen() {
    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.setup_pending_message),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp)
        )
    }
}
