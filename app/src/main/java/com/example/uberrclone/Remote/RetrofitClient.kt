package com.example.uberrclone.Remote

import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory

object RetrofitClient {
    val instance:Retrofit?=null
    get() = if(field==null) Retrofit.Builder()
        .baseUrl("https://maps.googleapis.com/")
        .addConverterFactory(ScalarsConverterFactory.create())
        .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
        .build() else field
}