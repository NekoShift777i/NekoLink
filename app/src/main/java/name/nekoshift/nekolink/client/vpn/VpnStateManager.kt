package name.nekoshift.nekolink.client.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object VpnStateManager {

    private val _state = MutableStateFlow(VpnState.DISCONNECTED)

    val state: StateFlow<VpnState> = _state.asStateFlow()

    internal fun updateState(newState: VpnState) {
        _state.value = newState
    }
}