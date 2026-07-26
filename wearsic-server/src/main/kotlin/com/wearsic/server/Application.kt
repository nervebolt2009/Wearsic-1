package com.wearsic.server

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val duration: String,
    val streamUrl: String
)

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        install(ContentNegotiation) {
            json()
        }
        install(CORS) {
            anyHost()
        }
        install(Compression) {
            gzip()
        }
        
        routing {
            get("/api/tracks") {
                val funnelUrl = "https://your-tailscale-node.ts.net"
                val tracks = listOf(
                    Track("1", "Synthwave Sunset", "Retro Wave", "3:45", "$funnelUrl/api/stream/track1.mp3"),
                    Track("2", "Cyberpunk City", "Neon Runner", "4:12", "$funnelUrl/api/stream/track2.mp3")
                )
                call.respond(tracks)
            }
            
            staticFiles("/api/stream", File("audio"))
        }
    }.start(wait = true)
}
