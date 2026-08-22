package com.lanpulse.app.ssh

import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import java.io.File
import java.io.OutputStream
import java.util.Properties

class SshShell(knownHosts: File) {
    private val jsch = JSch().also {
        if (!knownHosts.exists()) knownHosts.createNewFile()
        runCatching { it.setKnownHosts(knownHosts.absolutePath) }
    }

    private var session: Session? = null
    private var channel: ChannelShell? = null
    private var stdin: OutputStream? = null
    private val writeLock = Any()

    @Synchronized
    fun connect(host: String, port: Int, user: String, password: String) {
        close()
        val session = jsch.getSession(user, host, port)
        session.setPassword(password)
        session.userInfo = object : UserInfo {
            override fun getPassphrase(): String? = null
            override fun getPassword(): String = password
            override fun promptPassword(message: String?) = true
            override fun promptPassphrase(message: String?) = false
            override fun promptYesNo(message: String?) = true
            override fun showMessage(message: String?) {}
        }
        val cfg = Properties()
        cfg["StrictHostKeyChecking"] = "no"
        cfg["PreferredAuthentications"] = "password,keyboard-interactive"
        session.setConfig(cfg)
        runCatching {
            session.setConfig(
                "server_host_key",
                session.getConfig("server_host_key") + ",ssh-rsa,rsa-sha2-256,rsa-sha2-512",
            )
        }
        session.connect(10_000)
        val channel = session.openChannel("shell") as ChannelShell
        channel.setPtyType("xterm-256color")
        channel.setPtySize(100, 32, 800, 480)
        channel.connect(8_000)
        this.session = session
        this.channel = channel
        this.stdin = channel.outputStream
    }

    fun inputStream() = channel?.inputStream

    fun write(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        synchronized(writeLock) {
            val out = stdin ?: return
            out.write(bytes)
            out.flush()
        }
    }

    fun connected(): Boolean = session?.isConnected == true && channel?.isConnected == true

    @Synchronized
    fun close() {
        runCatching { channel?.disconnect() }
        runCatching { session?.disconnect() }
        channel = null
        session = null
        stdin = null
    }

    companion object {
        fun stripAnsi(raw: String): String {
            return raw
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace(Regex("\u001B\\[[0-9;?=]*[A-Za-z]"), "")
                .replace(Regex("\u001B\\].*?(?:\u0007|\u001B\\\\)"), "")
                .replace(Regex("\u001B[()][AB012]"), "")
                .replace(Regex("\u001B."), "")
                .replace("\u0007", "")
        }
    }
}
