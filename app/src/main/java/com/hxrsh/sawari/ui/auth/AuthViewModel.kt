package com.hxrsh.sawari.ui.auth

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hxrsh.sawari.domain.AppResult
import com.hxrsh.sawari.domain.userMessage
import com.hxrsh.sawari.domain.usecase.LoginUseCase
import com.hxrsh.sawari.domain.usecase.RegisterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class AuthState(
    val registering: Boolean = false,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val login: LoginUseCase,
    private val register: RegisterUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    fun onNameChange(v: String) = _state.update { it.copy(name = v, error = null) }
    fun onEmailChange(v: String) = _state.update { it.copy(email = v, error = null) }
    fun onPasswordChange(v: String) = _state.update { it.copy(password = v, error = null) }
    fun toggleMode() = _state.update { it.copy(registering = !it.registering, error = null) }

    fun submit() {
        val s = _state.value
        if (s.isLoading) return
        validate(s)?.let { msg -> _state.update { it.copy(error = msg) }; return }
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = if (s.registering) register(s.name, s.email, s.password) else login(s.email, s.password)
            // On success the session flow flips and the UI leaves this screen; only failures need state.
            _state.update {
                it.copy(isLoading = false, error = (result as? AppResult.Failure)?.error?.userMessage())
            }
        }
    }

    internal fun validate(s: AuthState): String? = when {
        s.registering && s.name.isBlank() -> "Enter your name"
        !EMAIL.matches(s.email.trim()) -> "Enter a valid email address"
        s.password.length < 6 -> "Password must be at least 6 characters"
        else -> null
    }

    private companion object {
        val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}
