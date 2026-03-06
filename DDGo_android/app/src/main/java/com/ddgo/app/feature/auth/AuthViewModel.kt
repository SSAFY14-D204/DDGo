package com.ddgo.app.feature.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel



class AuthViewModel : ViewModel(){
    var emailInput by mutableStateOf("")
    var passwordInput by mutableStateOf("")
}