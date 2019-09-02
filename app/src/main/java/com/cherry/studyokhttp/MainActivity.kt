package com.cherry.studyokhttp

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.*
import java.io.IOException

class MainActivity : AppCompatActivity() ,Callback{

    val okHttpClient:OkHttpClient = OkHttpClient.Builder().build()

    val mediaType: MediaType? = MediaType.parse("application/json; charset=utf-8")
    val json:String = "{\n" +
            "\"books\":\"\",\n" +
            "\"num\":10,\n" +
            "\"page\":1\n" +
            "}"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

//        val formBody:FormBody = FormBody.Builder()
//            .add("books","")
//            .add("num","10")
//            .add("page","1")
//            .build()

//        val requestBody:RequestBody = MultipartBody.Builder()
//            .setType(MultipartBody.FORM)
//            .addFormDataPart("books","")
//            .addFormDataPart("num","10")
//            .addFormDataPart("page","1")
//            .build()

        val requestBody:RequestBody = RequestBody.create(mediaType,json)

        val request:Request = Request.Builder()
            .url("http://132.232.23.157:5000/getbooks")
            .post(requestBody)
            .build()

        button.setOnClickListener {
            run {
                Log.e("TAG", "---")
                okHttpClient.newCall(request).enqueue(this)
            }
        }
    }

    override fun onFailure(call: Call, e: IOException) {
        runOnUiThread { runOnUiThread({
            content.text = e.message.toString()
        }) }
    }

    override fun onResponse(call: Call, response: Response) {
        runOnUiThread { content.text = response.body()?.string() }
    }

}
