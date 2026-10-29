import com.example.buddy.RelayCrypto
fun main() {
    val rk = "BwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwc"
    val k = RelayCrypto.derive(rk)!!
    check(k.room == "78784798c20935f3f4fc9508fe4c913e") { "room ${k.room}" }
    val frame = "1.CQkJCQkJCQkJCQkJFFQ87KNh2qE6USTF5xKN8eAcTFnZa10K8XNIEeMk7Bn8ZBPFnbFEXeQ1KNcbgg2LSSp1_qJxPf4Hwuf0uSj3JQH_GZr-vxlJF5HKGKhe7eu7YNcrFW4"
    val p = RelayCrypto.open(k.key, "b2p", frame)!!
    check(p.t == 1700000000000L && p.s == 1L && p.b == "aabbccddeeff" && p.line == "{\"type\":\"pong\"}") { "plain" }
    check(RelayCrypto.open(k.key, "p2b", frame) == null) { "dir" }
    val mine = RelayCrypto.seal(k.key, "b2p", 1700000000000L, 1L, "aabbccddeeff", "{\"type\":\"pong\"}", false, ByteArray(12) { 9 })
    check(mine == frame) { "seal differs: $mine" }
    println("KOTLIN RELAY VECTOR OK")
}
