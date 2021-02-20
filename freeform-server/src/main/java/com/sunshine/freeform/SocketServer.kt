package com.sunshine.freeform

import android.os.SystemClock
import android.view.MotionEvent
import java.io.IOException
import java.io.ObjectInputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

class SocketServer {
    private lateinit var inputManager: InputManager
    private var motionEvent: MotionEvent? = null
    private var displayId = -1

    private var touchServerSocket: ServerSocket? = null
    private val TOUCH_PORT = 10259

    //ConnectThread运行状态，让while停止用
    private var serviceState = true
    //是否重启服务
    private var restartService = false

    fun startService() {

        Thread {
            try {
                var socket: Socket? = null
                println("waiting...")

                //等待连接，每建立一个连接，就新建一个线程
                socket = touchServerSocket!!.accept() //等待一个客户端的连接，在连接之前，此方法是阻塞的
                println("connect to" + socket.inetAddress + ":" + socket.localPort)
                ConnectThread(socket).start()
            } catch (e: IOException) {
                println("IOException $e")
                e.printStackTrace()
            }
        }.start()

        //无限循环等待注入，这个为false后，就会去执行下一个while
        while (serviceState) {
            //休眠1ms不影响问题但是会降低cpu占用很多
            try {
                TimeUnit.MILLISECONDS.sleep(1)
            }catch (e: Exception) {
                println("TimeUnit $e")
            }
            if (displayId != -1 && motionEvent != null) {
                InputManager.setDisplayId(motionEvent!!, displayId)
                inputManager.injectInputEvent(motionEvent, 0)
                println(motionEvent)
                motionEvent = null
            }
        }

        while (true) {
            try {
                TimeUnit.MILLISECONDS.sleep(1)
            }catch (e: Exception) {
                println("TimeUnit $e")
            }
            if (restartService) {
                touchServerSocket = ServerSocket(TOUCH_PORT)
                serviceState = true
                startService()
                restartService = false
            }
        }
    }

    //向客户端发送信息
    inner class ConnectThread(private val socket: Socket?) : Thread() {
        override fun run() {
            try {
                val ois = ObjectInputStream(socket!!.getInputStream())
                while (true) {
                    val eventData = ois.readObject() as EventData
                    val count = eventData.xArray.size
                    val pointerProperties: Array<MotionEvent.PointerProperties?> = arrayOfNulls(count)
                    val pointerCoords: Array<MotionEvent.PointerCoords?> = arrayOfNulls(count)
                    for (i in 0 until count) {
                        pointerProperties[i] = MotionEvent.PointerProperties()
                        pointerProperties[i]!!.id = i
                        pointerProperties[i]!!.toolType = MotionEvent.TOOL_TYPE_FINGER

                        pointerCoords[i] = MotionEvent.PointerCoords()
                        pointerCoords[i]!!.apply {
                            orientation = 0f
                            pressure = 1f
                            size = 1f
                            x = eventData.xArray[i]
                            y = eventData.yArray[i]
                        }
                    }
                    /**
                     * 为什么要采用每次事件都发送displayId，这不是浪费资源吗
                     * 并不是
                     * 因为如果支持创建多个小窗的话，那么每次点击的就可能不是一个id
                     * 所以采用这种方式可以处理多个小窗
                     */
                    displayId = eventData.displayId
                    motionEvent = MotionEvent.obtain(
                            SystemClock.uptimeMillis(),
                            SystemClock.uptimeMillis(),
                            eventData.action,
                            count,
                            pointerProperties,
                            pointerCoords,
                            0,
                            0,
                            1.0f,
                            1.0f,
                            eventData.deviceId,
                            0,
                            eventData.source,
                            eventData.flags
                    )
                }
            }catch (e: Exception) {
                println("connectThread $e")
                //表示这个连接出了问题，所以应该关闭这个线程，开启一个新的线程
                serviceState = false
                touchServerSocket!!.close()

                //重启服务
                restartService = true
                interrupt()
            }
        }
    }

    init {
        try {
            touchServerSocket = ServerSocket(TOUCH_PORT)
            val serviceManager = ServiceManager()
            inputManager = serviceManager.inputManager
        } catch (e: IOException) {
            e.printStackTrace()
            println("SocketServer init $e")
        }
    }
}