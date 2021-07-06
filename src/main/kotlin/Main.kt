import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.websocket.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import origami.Origami
import service.DatabaseFactory
import service.OrigamiService
import service.WidgetService
import util.JsonMapper
import web.index
import web.origami
import web.widget
import java.time.Duration

fun Application.module() {
    install(DefaultHeaders)
    install(CallLogging)
//    install(WebSockets) {
//        pingPeriod = Duration.ofSeconds(15)
//        timeout = Duration.ofSeconds(15)
//        maxFrameSize = Long.MAX_VALUE
//        masking = false
//    }

    install(ContentNegotiation) {
        json(JsonMapper.defaultMapper)
    }

    DatabaseFactory.connectAndMigrate()

    install(Routing) {
        index()
//        widget(WidgetService())
        origami(OrigamiService())
    }

}

fun main(args: Array<String>) {
    Origami.init()
    embeddedServer(Netty, commandLineEnvironment(args)).start(wait = true)
}