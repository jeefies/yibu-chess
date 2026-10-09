package cn.yibu.chess.diagnostics

import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.Protocol
import okhttp3.Response
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy

/** Aggregate retries; connect includes TLS, so the phase fields must not be summed. */
internal class HttpTimingListener(private val trace: AnalysisTiming?) : EventListener() {
    private val starts = mutableMapOf<String, Long>()
    private val sums = mutableMapOf<String, Double>()
    private var connects = 0
    private fun begin(name: String) { trace?.let { starts[name] = it.now() } }
    private fun end(name: String) { trace?.let { timing -> starts.remove(name)?.let { start ->
        sums[name] = (sums[name] ?: 0.0) + (timing.now() - start).coerceAtLeast(0) / 1_000_000.0
        timing.put(name, sums[name])
    } } }
    override fun callStart(call: Call) { begin("http_call_ms") }
    override fun callEnd(call: Call) { end("http_call_ms") }
    override fun callFailed(call: Call, ioe: IOException) { end("http_call_ms") }
    override fun dnsStart(call: Call, domainName: String) { begin("dns_ms") }
    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<java.net.InetAddress>) { end("dns_ms") }
    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        connects++; begin("connect_ms"); trace?.put("connect_attempts", connects)
    }
    override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?) { end("connect_ms") }
    override fun connectFailed(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?, ioe: IOException) { end("connect_ms") }
    override fun secureConnectStart(call: Call) { begin("tls_ms") }
    override fun secureConnectEnd(call: Call, handshake: Handshake?) { end("tls_ms") }
    override fun connectionAcquired(call: Call, connection: Connection) {
        trace?.put("connection_reused", connects == 0)
        trace?.put("protocol", connection.protocol().toString())
        trace?.put("ip_family", when (connection.route().socketAddress.address?.address?.size) { 4 -> "ipv4"; 16 -> "ipv6"; else -> "unknown" })
    }
    override fun requestBodyEnd(call: Call, byteCount: Long) { begin("response_headers_wait_ms") }
    override fun responseHeadersEnd(call: Call, response: Response) { end("response_headers_wait_ms") }
    override fun responseBodyStart(call: Call) { begin("download_ms") }
    override fun responseBodyEnd(call: Call, byteCount: Long) { end("download_ms"); trace?.put("response_bytes", byteCount) }
}
