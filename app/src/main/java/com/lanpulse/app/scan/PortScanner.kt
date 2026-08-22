package com.lanpulse.app.scan

import com.lanpulse.app.model.OpenPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

object PortCatalog {
    const val ALL = 65535

    val FINGERPRINT = listOf(
        22, 53, 80, 139, 443, 445, 548, 554, 631, 1883, 3306, 3389,
        5000, 5353, 5900, 8008, 8080, 8123, 8443, 9100, 32400, 62078,
    )

    val QUICK: IntArray by lazy {
        val extras = intArrayOf(1883, 5353, 8123, 25565, 32400, 51820)
        (expand(NMAP_TOP_1000) + extras.toList()).distinct().sorted().toIntArray()
    }

    fun service(port: Int): String = NAMES[port] ?: "tcp"

    private val NAMES = mapOf(
        21 to "FTP", 22 to "SSH", 23 to "Telnet", 25 to "SMTP", 53 to "DNS",
        80 to "HTTP", 110 to "POP3", 111 to "RPC", 135 to "MSRPC", 139 to "NetBIOS",
        143 to "IMAP", 161 to "SNMP", 389 to "LDAP", 443 to "HTTPS", 445 to "SMB",
        515 to "LPD", 548 to "AFP", 554 to "RTSP", 587 to "Submission", 631 to "IPP",
        993 to "IMAPS", 995 to "POP3S", 1433 to "MSSQL", 1521 to "Oracle",
        1883 to "MQTT", 2049 to "NFS", 3306 to "MySQL", 3389 to "RDP",
        5000 to "UPnP/DSM", 5001 to "DSM-SSL", 5353 to "mDNS", 5432 to "Postgres",
        5900 to "VNC", 6379 to "Redis", 8000 to "HTTP-alt", 8008 to "Cast",
        8009 to "Cast", 8080 to "HTTP-proxy", 8123 to "Home Assistant",
        8443 to "HTTPS-alt", 8888 to "HTTP-alt", 9000 to "HTTP-alt",
        9090 to "WebUI", 9100 to "JetDirect", 32400 to "Plex",
        25565 to "Minecraft", 27017 to "MongoDB", 62078 to "lockdownd",
        8291 to "Winbox", 51820 to "WireGuard", 5357 to "WSDAPI",
        5480 to "UniFi", 8530 to "WSUS", 9443 to "HTTPS-alt",
        2375 to "Docker", 2376 to "Docker-TLS", 6443 to "Kubernetes",
        10250 to "kubelet", 9092 to "Kafka", 9200 to "Elasticsearch",
        5601 to "Kibana", 3000 to "Dev HTTP", 3001 to "Dev HTTP",
        4172 to "Sunlogin", 5901 to "VNC-1", 5985 to "WinRM", 5986 to "WinRM-TLS",
        8292 to "Winbox-HTTP", 8728 to "RouterOS-API", 8729 to "RouterOS-API-SSL",
        1880 to "Node-RED", 8883 to "MQTT-TLS", 5683 to "CoAP",
        55443 to "UniFi-OS", 8081 to "HTTP-alt", 8082 to "HTTP-alt",
        8444 to "HTTPS-alt", 9001 to "Tor", 9418 to "Git",
        49152 to "UPnP", 5355 to "LLMNR",
    )

    private fun expand(spec: String): List<Int> {
        val out = ArrayList<Int>(1024)
        spec.split(',').forEach { token ->
            val dash = token.indexOf('-')
            if (dash < 0) {
                out += token.toInt()
            } else {
                val a = token.substring(0, dash).toInt()
                val b = token.substring(dash + 1).toInt()
                for (p in a..b) out += p
            }
        }
        return out
    }

    // nmap --top-ports 1000 default TCP set
    private const val NMAP_TOP_1000 =
        "1,3-4,6-7,9,13,17,19-26,30,32-33,37,42-43,49,53,70,79-85,88-90,99-100,106,109-111,113,119,125,135,139,143-144,146,161,163,179,199,211-212,222,254-256,259,264,280,301,306,311,340,366,389,406-407,416-417,425,427,443-445,458,464-465,481,497,500,512-515,524,541,543-545,548,554-555,563,587,593,616-617,625,631,636,646,648,666-668,683,687,691,700,705,711,714,720,722,726,749,765,777,783,787,800-801,808,843,873,880,888,898,900-903,911-912,981,987,990,992-993,995,999-1002,1007,1009-1011,1021-1100,1102,1104-1108,1110-1114,1117,1119,1121-1124,1126,1130-1132,1137-1138,1141,1145,1147-1149,1151-1152,1154,1163-1166,1169,1174-1175,1183,1185-1187,1192,1198-1199,1201,1213,1216-1218,1233-1234,1236,1244,1247-1248,1259,1271-1272,1277,1287,1296,1300-1301,1309-1311,1322,1328,1334,1352,1417,1433-1434,1443,1455,1461,1494,1500-1501,1503,1521,1524,1533,1556,1580,1583,1594,1600,1641,1658,1666,1687-1688,1700,1717-1721,1723,1755,1761,1782-1783,1801,1805,1812,1839-1840,1862-1864,1875,1900,1914,1935,1947,1971-1972,1974,1984,1998-2010,2013,2020-2022,2030,2033-2035,2038,2040-2043,2045-2049,2065,2068,2099-2100,2103,2105-2107,2111,2119,2121,2126,2135,2144,2160-2161,2170,2179,2190-2191,2196,2200,2222,2251,2260,2288,2301,2323,2366,2381-2383,2393-2394,2399,2401,2492,2500,2522,2525,2557,2601-2602,2604-2605,2607-2608,2638,2701-2702,2710,2717-2718,2725,2800,2809,2811,2869,2875,2909-2910,2920,2967-2968,2998,3000-3001,3003,3005-3007,3011,3013,3017,3030-3031,3052,3071,3077,3128,3168,3211,3221,3260-3261,3268-3269,3283,3300-3301,3306,3322-3325,3333,3351,3367,3369-3372,3389-3390,3404,3476,3493,3517,3527,3546,3551,3580,3659,3689-3690,3703,3737,3766,3784,3800-3801,3809,3814,3826-3828,3851,3869,3871,3878,3880,3889,3905,3914,3918,3920,3945,3971,3986,3995,3998,4000-4006,4045,4111,4125-4126,4129,4224,4242,4279,4321,4343,4443-4446,4449,4550,4567,4662,4848,4899-4900,4998,5000-5004,5009,5030,5033,5050-5051,5054,5060-5061,5080,5087,5100-5102,5120,5190,5200,5214,5221-5222,5225-5226,5269,5280,5298,5357,5405,5414,5431-5432,5440,5500,5510,5544,5550,5555,5560,5566,5631,5633,5666,5678-5679,5718,5730,5800-5802,5810-5811,5815,5822,5825,5850,5859,5862,5877,5900-5904,5906-5907,5910-5911,5915,5922,5925,5950,5952,5959-5963,5987-5989,5998-6007,6009,6025,6059,6100-6101,6106,6112,6123,6129,6156,6346,6389,6502,6510,6543,6547,6565-6567,6580,6646,6666-6669,6689,6692,6699,6779,6788-6789,6792,6839,6881,6901,6969,7000-7002,7004,7007,7019,7025,7070,7100,7103,7106,7200-7201,7402,7435,7443,7496,7512,7625,7627,7676,7741,7777-7778,7800,7911,7920-7921,7937-7938,7999-8002,8007-8011,8021-8022,8031,8042,8045,8080-8090,8093,8099-8100,8180-8181,8192-8194,8200,8222,8254,8290-8292,8300,8333,8383,8400,8402,8443,8500,8600,8649,8651-8652,8654,8701,8800,8873,8888,8899,8994,9000-9003,9009-9011,9040,9050,9071,9080-9081,9090-9091,9099-9103,9110-9111,9200,9207,9220,9290,9415,9418,9485,9500,9502-9503,9535,9575,9593-9595,9618,9666,9876-9878,9898,9900,9917,9929,9943-9944,9968,9998-10004,10009-10010,10012,10024-10025,10082,10180,10215,10243,10566,10616-10617,10621,10626,10628-10629,10778,11110-11111,11967,12000,12174,12265,12345,13456,13722,13782-13783,14000,14238,14441-14442,15000,15002-15004,15660,15742,16000-16001,16012,16016,16018,16080,16113,16992-16993,17877,17988,18040,18101,18988,19101,19283,19315,19350,19780,19801,19842,20000,20005,20031,20221-20222,20828,21571,22939,23502,24444,24800,25734-25735,26214,27000,27352-27353,27355-27356,27715,28201,30000,30718,30951,31038,31337,32768-32785,33354,33899,34571-34573,35500,38292,40193,40911,41511,42510,44176,44442-44443,44501,45100,48080,49152-49161,49163,49165,49167,49175-49176,49400,49999-50003,50006,50300,50389,50500,50636,50800,51103,51493,52673,52822,52848,52869,54045,54328,55055-55056,55555,55600,56737-56738,57294,57797,58080,60020,60443,61532,61900,62078,63331,64623,64680,65000,65129,65389"
}

object PortScanner {
    suspend fun probeOpen(ip: String, ports: List<Int>, timeoutMs: Int = 180): List<OpenPort> =
        coroutineScope {
            val sem = Semaphore(32)
            ports.map { port ->
                async(Dispatchers.IO) {
                    sem.withPermit { connect(ip, port, timeoutMs) }
                }
            }.awaitAll().filterNotNull()
        }

    fun scan(ip: String, full: Boolean): Flow<Pair<Int, OpenPort?>> = channelFlow {
        val ports = if (full) IntArray(PortCatalog.ALL) { it + 1 } else PortCatalog.QUICK
        val total = ports.size
        val workers = if (full) 96 else 64
        val timeout = if (full) 140 else 180
        val next = AtomicInteger(0)
        val scanned = AtomicInteger(0)
        coroutineScope {
            repeat(workers) {
                launch(Dispatchers.IO) {
                    while (true) {
                        val i = next.getAndIncrement()
                        if (i >= total) break
                        val hit = connect(ip, ports[i], timeout)
                        val n = scanned.incrementAndGet()
                        if (hit != null || n % 64 == 0 || n == total) {
                            send(n to hit)
                        }
                    }
                }
            }
        }
    }

    private fun connect(ip: String, port: Int, timeoutMs: Int): OpenPort? {
        val t0 = System.nanoTime()
        return try {
            Socket().use { sock ->
                sock.connect(InetSocketAddress(ip, port), timeoutMs)
                sock.soTimeout = 160
                val rtt = ((System.nanoTime() - t0) / 1_000_000L).toInt()
                val banner = runCatching {
                    if (sock.getInputStream().available() > 0) {
                        val buf = ByteArray(64)
                        val n = sock.getInputStream().read(buf)
                        if (n > 0) String(buf, 0, n).trim().take(48) else null
                    } else null
                }.getOrNull()
                OpenPort(port, PortCatalog.service(port), banner, rtt)
            }
        } catch (_: Exception) {
            null
        }
    }
}
