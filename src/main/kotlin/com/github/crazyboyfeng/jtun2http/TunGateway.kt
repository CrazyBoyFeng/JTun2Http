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
import kotlinx.coroutines.Dispatchers.IO
import java.io.*
import java.net.SocketAddress
import java.util.logging.Logger

class TunGateway @Throws(TunInterfaceInvalidException::class) constructor(
    private val tunInterface: FileDescriptor,
    private val remoteProxy: SocketAddress,
) : Thread() {
    class TunInterfaceInvalidException(msg: String) : FileNotFoundException(msg)

    private val log = Logger.getGlobal()

    init {
        if (!tunInterface.valid()) {
            val msg = "Tunnel interface is invalid!"
            log.severe(msg)
            throw TunInterfaceInvalidException(msg)
        }
    }

    private var mainJob: Job? = null

    @Throws(
        InterruptedException::class,
        IOException::class,
        SecurityException::class,//kotlin不显式声明异常这点并不好，只有实际使用时遇到了才发现还会有权限不足异常。
    )//要是idea智能点也好，提示一下可能有的异常，但是什么提示都没有。捕捉异常全靠猜和坑。
    override fun run() = runBlocking {
        //使用线程内调用阻塞协程是保活需要。非阻塞协程不保活。
        val httpProxyClient = HttpProxyClient(remoteProxy)
        //todo 测试http代理连接
        //todo 连接失败抛出异常
        val fromLan = async(IO) {
            FileInputStream(tunInterface)
        }
        val toLan = async(IO) {
            FileOutputStream(tunInterface)
        }
        try {
            mainJob = launch {
                //当前线程运行协程
                while (isActive) {//没有被打断
                    receive()
                    delay(100)
                }
            }
            mainJob!!.join()
            val msg = "Tunnel interface is closed!"
            log.info(msg)
            throw InterruptedException(msg)
        } finally {//我尝试了use()发现很丑陋，需要在外面套上try{}，没有java简洁。不过它们都不能并发操作。
            close(httpProxyClient, fromLan.await(), toLan.await())
        }
    }

    override fun interrupt() {
        mainJob?.cancel()
    }

    private suspend fun receive() = withContext(Dispatchers.IO) {
        //todo 读取并处理
    }

    private suspend fun close(vararg resources: Closeable) = withContext(NonCancellable) {
        resources.forEach {
            launch(IO) {
                it.close()
            }
        }
    }
}