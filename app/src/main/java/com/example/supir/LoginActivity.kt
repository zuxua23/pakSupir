package com.example.supir

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class LoginActivity : AppCompatActivity() {

    private lateinit var etPhone: TextInputEditText
    private lateinit var etPin: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var btnTogglePin: MaterialButton
    private var pinVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // block screenshoot
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContentView(R.layout.activity_login)

        etPhone = findViewById(R.id.etPhone)
        etPin = findViewById(R.id.etPin)
        btnLogin = findViewById(R.id.btnLogin)
        btnTogglePin = findViewById(R.id.btnTogglePin)

        // Toggle show/hide PIN
        btnTogglePin.setOnClickListener {
            pinVisible = !pinVisible
            etPin.inputType = if (pinVisible)
                InputType.TYPE_CLASS_NUMBER
            else
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            btnTogglePin.text = getString(
                if (pinVisible) R.string.action_hide else R.string.action_show
            )
            etPin.setSelection(etPin.text?.length ?: 0)
        }

        btnLogin.setOnClickListener {
            val phone = etPhone.text?.toString()?.trim().orEmpty()
            val pin = etPin.text?.toString()?.trim().orEmpty()

            if (phone.length < 10) {
                etPhone.error = "phone number invalid"
                return@setOnClickListener
            }
            if (pin.length != 6) {
                etPin.error = "PIN must be 6 digits"
                return@setOnClickListener
            }

            // Data statis untuk testing
            val validPhone = "08123456789"
            val validPin   = "123456"

            if (phone != validPhone || pin != validPin) {
                etPin.error = "Nomor HP atau PIN salah"
                return@setOnClickListener
            }

            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
        }
    }
}
