package name.nekoshift.nekolink.client

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview

import name.nekoshift.nekolink.client.ui.theme.NekoLinkTheme
import name.nekoshift.nekolink.client.vpn.NekoLinkVpnService

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            NekoLinkTheme {
                VpnPermissionScreen()
            }
        }
    }
}

@Composable
fun VpnPermissionScreen() {
    val context = LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.i("NekoLink", "Notification permission granted: $granted")
    }

    fun startVpnService() {
        Log.i("NekoLink", "Starting VPN service")

        val serviceIntent = Intent(
            context,
            NekoLinkVpnService::class.java
        )

       context.startForegroundService(serviceIntent)

    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startVpnService()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = {
                val permissionIntent = VpnService.prepare(context)

                if (permissionIntent != null) {
                    vpnPermissionLauncher.launch(permissionIntent)
                } else {
                    startVpnService()
                }
            }
        ) {
            Text("Подключиться")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun VpnPermissionScreenPreview() {
    NekoLinkTheme {
        VpnPermissionScreen()
    }
}