package name.nekoshift.nekolink.client.xray

object XrayTestConfig {

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