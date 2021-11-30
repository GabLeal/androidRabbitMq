package com.example.appinvest

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import android.view.View
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.rabbitmq.client.*
import java.lang.Exception
import java.nio.charset.StandardCharsets
import com.rabbitmq.client.DeliverCallback

import com.rabbitmq.client.ConnectionFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.Retrofit
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        subscribe("tesouro/11,81%/R\$ 633,36")
    }

    fun rawJSON() {

        // Create Retrofit
        val retrofit = Retrofit.Builder()
            .baseUrl("http://172.20.32.1:3000/")
            .build()

        // Create Service
        val service = retrofit.create(APIService::class.java)

        // Create JSON using JSONObject
        val jsonObject = JSONObject()
        jsonObject.put("fila", "tesouro/11,81%/R\$ 633,36")


        // Convert JSONObject to String
        val jsonObjectString = jsonObject.toString()

        // Create RequestBody ( We're not using any converter, like GsonConverter, MoshiConverter e.t.c, that's why we use RequestBody )
        val requestBody = jsonObjectString.toRequestBody("application/json".toMediaTypeOrNull())

        CoroutineScope(Dispatchers.IO).launch {
            // Do the POST request and get response
            val response = service.createEmployee(requestBody = requestBody)

            withContext(Dispatchers.Main) {
                if (response.isSuccessful) {

                    // Convert raw JSON to pretty JSON using GSON library
                    val gson = GsonBuilder().setPrettyPrinting().create()
                    val prettyJson = gson.toJson(
                        JsonParser.parseString(
                            response.body()
                                ?.string() // About this thread blocking annotation : https://github.com/square/retrofit/issues/3255
                        )
                    )

                    Log.d("Pretty Printed JSON :", prettyJson)
                    subscribe(prettyJson)
                } else {

                    Log.e("RETROFIT_ERROR", response.code().toString())

                }
            }
        }
    }




    fun sendMessage(view: View) {
        Log.d("TESTE", "CLICOOOOOU")
        rawJSON()
    }

    fun run(url: String) {
        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) = println(response.body?.string())
        })
    }

    private fun subscribe(queu: String) {

        var subscribeThread = Thread {
            while (true) {
                try {

                    val factory = ConnectionFactory()
                    factory.host = "172.20.32.1".toString()

                    val connection = factory.newConnection("amqp://guest:guest@172.20.32.1:5672/")
                    val channel = connection.createChannel()

                    val deliverCallback = DeliverCallback { consumerTag: String?, delivery: Delivery ->
                        val message = String(delivery.body, StandardCharsets.UTF_8)
                        println("[$consumerTag] Received message: '$message'")



                    }
                    val cancelCallback = CancelCallback { consumerTag: String? ->
                        println("[$consumerTag] was canceled")
                    }
                    println(queu)
                   while (true) {
                    channel.basicConsume(queu, true, deliverCallback, cancelCallback)

                   }
                } catch (e: InterruptedException) {
                    break
                } catch (e1: Exception) {
                    Log.d("", "Connection broken: " + e1.message)
                    try {
                        Thread.sleep(5000) //sleep and then try again
                    } catch (e: InterruptedException) {
                        break
                    }
                }
            }
        }
        subscribeThread.start()
    }
}