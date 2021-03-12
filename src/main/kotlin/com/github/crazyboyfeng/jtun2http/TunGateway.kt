/*
 * Copyright 2021 CrazyBoyFeng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.crazyboyfeng.jtun2http

import kotlinx.coroutines.*
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.net.SocketAddress
import java.util.logging.Logger

class TunGateway @Throws(TunInterfaceInvalidException::class) constructor(
    tunInterface: FileDescriptor,
    remoteProxy: SocketAddress
) : Runnable {
    class TunInterfaceInvalidException : FileNotFoundException()

    private val log = Logger.getGlobal()

    init {
        if (!tunInterface.valid()) {
            log.severe("Tunnel interface is invalid!")
            throw TunInterfaceInvalidException()
        }
    }

    private val httpProxyClient = HttpProxyClient(remoteProxy)
    private val fromLan = FileInputStream(tunInterface)
    private val toLan = FileOutputStream(tunInterface)
    private var mainJob: Job? = null

    @Throws(SecurityException::class)
    /*
       kotlin不显式声明异常这点并不好，只有实际使用时遇到了才发现还有权限不足异常。
       要是idea智能点也好，提示一下可能有的异常，但是什么提示都没有。捕捉异常全靠猜和坑。
       */
    override fun run() = runBlocking {//使用线程内调用阻塞协程是保活需要。非阻塞协程不保活。
        //todo 测试http代理连接
        //todo 连接失败抛出异常
        mainJob = launch { listen() }
    }

    fun stop() {
        mainJob?.cancel()
    }

    private suspend fun listen() = withContext(Dispatchers.Unconfined) {//当前线程运行协程
        //其实kotlin协程底层就是线程池。比如此处可以翻译为：在当前线程中执行本方法。
        try {
            while (isActive) {//没有被打断
                receive()
                delay(100)
            }
        } finally {
            close()
        }
    }

    private suspend fun receive() = withContext(Dispatchers.IO) {
        //todo 读取并处理
    }

    private suspend fun close() {
        withContext(Dispatchers.IO) { fromLan.close() }
        withContext(Dispatchers.IO) { toLan.close() }
        //TODO 关闭proxy连接？
    }
}