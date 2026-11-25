package name.nekoshift.nekolink.client.vpn

enum class VpnState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    DISCONNECT_ERROR,
    ERROR
}