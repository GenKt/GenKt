package io.modelcontentprotocol.kotlin.sdk.server

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.util.collections.*
import io.ktor.utils.io.KtorDsl


@KtorDsl
public fun Routing.mcp(path: String, block: () -> Server) {
    route(path) {
        mcp(block)
    }
}

/**
 * Configures the Ktor Application to handle Model Context Protocol (MCP) over Server-Sent Events (SSE).
 */
@KtorDsl
public fun Routing.mcp(block: () -> Server) {
    val transports = ConcurrentMap<String, SseServerTransport>()

    sse {
        mcpSseEndpoint("", transports, block)
    }

    post {
        mcpPostEndpoint(transports)
    }
}

@Suppress("FunctionName")
@Deprecated("Use mcp() instead", ReplaceWith("mcp(block)"), DeprecationLevel.WARNING)
public fun Application.MCP(block: () -> Server) {
    mcp(block)
}

@KtorDsl
public fun Application.mcp(block: () -> Server) {
    val transports = ConcurrentMap<String, SseServerTransport>()

    install(SSE)

    routing {
        sse("/sse") {
            mcpSseEndpoint("/message", transports, block)
        }

        post("/message") {
            mcpPostEndpoint(transports)
        }
    }
}

private suspend fun ServerSSESession.mcpSseEndpoint(
    postEndpoint: String,
    transports: ConcurrentMap<String, SseServerTransport>,
    block: () -> Server,
) {
    val transport =  mcpSseTransport(postEndpoint, transports)

    val server = block()

    server.onClose {
        transports.remove(transport.sessionId)
    }

    server.connect(transport)
}

//TODO: internal
public fun ServerSSESession.mcpSseTransport(
    postEndpoint: String,
    transports: ConcurrentMap<String, SseServerTransport>,
): SseServerTransport {
    val transport = SseServerTransport(postEndpoint, this)
    transports[transport.sessionId] = transport


    return transport
}

//TODO: internal
public suspend fun RoutingContext.mcpPostEndpoint(
    transports: ConcurrentMap<String, SseServerTransport>,
) {
    val sessionId: String = call.request.queryParameters["sessionId"]
        ?: run {
            call.respond(HttpStatusCode.BadRequest, "sessionId query parameter is not provided")
            return
        }


    val transport = transports[sessionId]
    if (transport == null) {
        call.respond(HttpStatusCode.NotFound, "Session not found")
        return
    }

    transport.handlePostMessage(call)
}
