package com.example.appinvest

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.rabbitmq.client.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.chromium.base.Promise
import org.json.JSONObject
import retrofit2.Retrofit
import java.nio.charset.StandardCharsets
import java.security.AccessController.getContext


class MainActivity : AppCompatActivity() {
    private val client = OkHttpClient()
    var tipoInvestimento: Spinner? = null
    var rendabilidade: Spinner? = null
    var aplicacaoMinima: Spinner? = null
    var periodo: Spinner? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //GET TEXT INPUT

        tipoInvestimento = findViewById<Spinner>(R.id.spinner1) as Spinner
        rendabilidade = findViewById<Spinner>(R.id.spinner2) as Spinner
        aplicacaoMinima = findViewById<Spinner>(R.id.spinner3) as Spinner
        periodo = findViewById<Spinner>(R.id.spinner4) as Spinner

        val itemTipoInvestimento = resources.getStringArray(R.array.tipo_investimento)
        val itemRentabilidade = resources.getStringArray(R.array.rentabilidade)
        val itemAplicacaoMinima = resources.getStringArray(R.array.aplicacao)
        val itemPeriodo = resources.getStringArray(R.array.periodo)


        if (tipoInvestimento != null) {
            val adapter = ArrayAdapter(this,
                android.R.layout.simple_spinner_item, itemTipoInvestimento)
            tipoInvestimento?.adapter = adapter
        }

        if (rendabilidade != null) {
            val adapter = ArrayAdapter(this,
                android.R.layout.simple_spinner_item, itemRentabilidade)
            rendabilidade?.adapter = adapter
        }

        if (aplicacaoMinima != null) {
            val adapter = ArrayAdapter(this,
                android.R.layout.simple_spinner_item, itemAplicacaoMinima)
            aplicacaoMinima?.adapter = adapter
        }

        if (periodo != null) {
            val adapter = ArrayAdapter(this,
                android.R.layout.simple_spinner_item, itemPeriodo)
            periodo?.adapter = adapter
        }

    }

    fun rawJSON(queue: String) : Promise<Boolean> {

       var isCreateQueue: Boolean = false

        // Create Retrofit
        val retrofit = Retrofit.Builder()
            .baseUrl("http://172.20.32.1:3000/")
            .build()

        // Create Service
        val service = retrofit.create(APIService::class.java)

        // Create JSON using JSONObject
        val jsonObject = JSONObject()
        jsonObject.put("fila", queue)


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
                    isCreateQueue = true
                    Log.d("Pretty Printed JSON :", prettyJson)


                } else {

                    Log.e("RETROFIT_ERROR", response.code().toString())
                    isCreateQueue = false
                }
            }
        }

       return  Promise.fulfilled(true)

    }

    fun sendMessage(view: View) {
        var fila : String = "${tipoInvestimento?.selectedItem.toString()}/${rendabilidade?.selectedItem.toString()}/${aplicacaoMinima?.selectedItem.toString()}/${periodo?.selectedItem.toString()}"

        rawJSON(fila).then { queue ->
            if(queue) subscribe(fila)
        }

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