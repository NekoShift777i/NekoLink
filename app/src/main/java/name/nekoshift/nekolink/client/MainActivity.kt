package name.nekoshift.nekolink.client

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat

import name.nekoshift.nekolink.client.ui.theme.NekoLinkTheme
import name.nekoshift.nekolink.client.vpn.NekoLinkVpnService
import name.nekoshift.nekolink.client.vpn.VpnState
import name.nekoshift.nekolink.client.vpn.VpnStateManager

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
    val vpnState by VpnStateManager.state.collectAsState()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.i("NekoLink", "Notification permission granted: $granted")
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!permissionGranted) {
                notificationPermissionLauncher.launch(
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }
        }
    }

    fun startVpnService() {
        Log.i("NekoLink", "Starting VPN service")

        val serviceIntent = Intent(
            context,
            NekoLinkVpnService::class.java
        )

       context.startForegroundService(serviceIntent)

    }

    fun stopVpnService() {
        Log.i("NekoLink", "Sending VPN disconnect command")

        VpnStateManager.updateState(VpnState.DISCONNECTING)

        val serviceIntent = Intent(
            context,
            NekoLinkVpnService::class.java
        ).apply {
            action = NekoLinkVpnService.ACTION_DISCONNECT
        }

        context.startService(serviceIntent)
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
                when (vpnState) {
                    VpnState.DISCONNECTED, VpnState.ERROR -> {
                        val permissionIntent = VpnService.prepare(context)

                        if (permissionIntent != null) {
                            vpnPermissionLauncher.launch(permissionIntent)
                        } else {
                            startVpnService()
                        }
                    }

                    VpnState.CONNECTING,
                    VpnState.CONNECTED,
                    VpnState.DISCONNECT_ERROR -> {
                        stopVpnService()
                    }

                    VpnState.DISCONNECTING -> Unit
                }
            },
            enabled = vpnState != VpnState.DISCONNECTING
        ) {
            Text(
                when (vpnState) {
                    VpnState.DISCONNECTED -> "Подключиться"
                    VpnState.CONNECTING -> "Отменить подключение"
                    VpnState.CONNECTED -> "Отключиться"
                    VpnState.DISCONNECTING -> "Отключение..."
                    VpnState.ERROR -> "Повторить подключение"
                    VpnState.DISCONNECT_ERROR -> "Повторить отключение"
                }
            )
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