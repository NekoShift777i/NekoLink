package name.nekoshift.nekolink.client.xray

object XrayTestConfig {

    fun createTunConfig(tunFd: Int, mtu: Int = 1500): String {
        return """
            {
                "env": {
                    "xray.tun.fd": "$tunFd"
                },
                "log": {
                    "loglevel": "debug"
                },
                "inbounds": [
                    {
                        "tag": "tun-in",
                        "protocol": "tun",
                        "settings": {
                            "name": "nekolink0",
                            "mtu": $mtu
                        }
                    }
                ],
                "outbounds": [
                    {
                        "tag": "direct",
                        "protocol": "freedom",
                        "settings": {}
                    }
                ]
            }
        """.trimIndent()
    }

    val config: String = """
        {
            "log": {
                "loglevel": "warning"
            },
            "inbounds": [],
            "outbounds": [
                {
                    "tag": "direct",
                    "protocol": "freedom",
                    "settings": {}
                }
            ]
        }
    """.trimIndent()
}