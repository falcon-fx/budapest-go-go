package com.example.myapplication.ui

class Event<out T>(private val content: T) {
    private var alreadyHandled = false
    fun getContentIfNotHandled(): T? {
        return if(alreadyHandled) null
        else {
            alreadyHandled = true
            content
        }
    }

    fun peekContent(): T = content
}